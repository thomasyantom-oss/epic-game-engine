package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionData;
import com.epic.engine.session.SessionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SnapshotService {

    private final EventBus eventBus;
    private final EntityStore entityStore;
    private final SessionService sessionService;

    public SnapshotService(EventBus eventBus, EntityStore entityStore, SessionService sessionService) {
        this.eventBus = eventBus;
        this.entityStore = entityStore;
        this.sessionService = sessionService;
    }

    public WorldSnapshot buildSnapshot(String token) {
        SessionData session = sessionService.getSession(token);
        if (session == null) {
            return WorldSnapshot.characterSelect(token, List.of(), sessionService.getMaxSlots(), buildColorMap());
        }

        if (session.activeCharacterId() != null && sessionService.isTimedOut(token)) {
            sessionService.clearActiveCharacter(token);
            session = sessionService.getSession(token);
        }

        if (session.activeCharacterId() == null) {
            return buildCharacterSelectSnapshot(token);
        }

        return buildInGameSnapshot(token, session.activeCharacterId());
    }

    private WorldSnapshot buildCharacterSelectSnapshot(String token) {
        GameEvent event = new GameEvent("session.list_characters");
        event.set("sessionToken", token);
        event.set("characters", new ArrayList<WorldSnapshot.CharacterInfo>());
        eventBus.fire("session.list_characters", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.CharacterInfo> characters = event.get("characters");

        return WorldSnapshot.characterSelect(token, characters != null ? characters : List.of(),
                sessionService.getMaxSlots(), buildColorMap());
    }

    private WorldSnapshot buildInGameSnapshot(String token, String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) {
            sessionService.clearActiveCharacter(token);
            return buildCharacterSelectSnapshot(token);
        }

        GameEvent uiEvent = new GameEvent("ui.render_status");
        uiEvent.set("entityId", playerId);
        uiEvent.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        uiEvent.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        eventBus.fire("ui.render_status", uiEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = uiEvent.get("bars");
        @SuppressWarnings("unchecked")
        List<WorldSnapshot.BuffEntry> buffs = uiEvent.get("buffs");

        GameEvent actionsEvent = new GameEvent("ui.render_actions");
        actionsEvent.set("entityId", playerId);
        actionsEvent.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        eventBus.fire("ui.render_actions", actionsEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = actionsEvent.get("actions");

        return WorldSnapshot.inGame(token, playerId,
                new WorldSnapshot.ActionResult(true, "ok"),
                bars != null ? bars : List.of(),
                buffs != null ? buffs : List.of(),
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions != null ? actions : List.of(),
                buildCombatLog(playerId),
                buildColorMap());
    }

    public Map<String, String> buildColorMap() {
        Entity config = entityStore.get("_config");
        if (config == null || !config.hasComponent("Colors")) return Map.of();
        Component colors = config.getComponent("Colors");
        Map<String, String> colorMap = new LinkedHashMap<>();
        colors.getAll().forEach((k, v) -> colorMap.put(k, v.toString()));
        return colorMap;
    }

    @SuppressWarnings("unchecked")
    private List<WorldSnapshot.LogEntry> buildCombatLog(String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) return List.of();

        // Active combat log
        for (String tag : player.getTags()) {
            if (tag.startsWith("combat:")) {
                String combatId = tag.substring(7);
                Entity combat = entityStore.get(combatId);
                if (combat == null || !combat.hasComponent("CombatLog")) continue;
                return parseLogEntries((List<Object>) combat.getComponent("CombatLog").get("entries"));
            }
        }

        // Last combat log (after battle ended)
        if (player.hasComponent("LastCombatLog")) {
            List<Object> entries = (List<Object>) player.getComponent("LastCombatLog").get("entries");
            return parseLogEntries(entries);
        }

        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<WorldSnapshot.LogEntry> parseLogEntries(List<Object> entries) {
        if (entries == null) return List.of();
        List<WorldSnapshot.LogEntry> log = new ArrayList<>();
        for (Object entryObj : entries) {
            List<Object> segments = (List<Object>) entryObj;
            List<WorldSnapshot.TextSegment> textSegments = new ArrayList<>();
            Integer round = null;
            for (Object segObj : segments) {
                Map<String, Object> seg = (Map<String, Object>) segObj;
                textSegments.add(new WorldSnapshot.TextSegment(
                        (String) seg.get("text"),
                        (String) seg.get("color")));
                if (seg.containsKey("round") && seg.get("round") != null) {
                    round = ((Number) seg.get("round")).intValue();
                }
            }
            if (!textSegments.isEmpty()) {
                log.add(new WorldSnapshot.LogEntry(textSegments, round));
            }
        }
        return log;
    }

    private WorldSnapshot.MapSnapshot buildMapSnapshot(Entity player) {
        if (!player.hasComponent("Position")) return null;
        Component pos = player.getComponent("Position");
        String mapId = pos.getString("map");
        Entity map = entityStore.get(mapId);
        if (map == null || !map.hasComponent("MapData")) return null;
        Component mapData = map.getComponent("MapData");

        String mapName = mapData.has("name") ? mapData.getString("name") : mapId;

        @SuppressWarnings("unchecked")
        List<String> terrain = (List<String>) mapData.get("terrain");

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> terrainsRaw = (Map<String, Map<String, Object>>) mapData.get("terrains");
        Map<String, WorldSnapshot.TerrainInfo> terrains = new LinkedHashMap<>();
        if (terrainsRaw != null) {
            terrainsRaw.forEach((ch, info) -> {
                @SuppressWarnings("unchecked")
                List<String> requires = info.get("requires") != null
                        ? ((List<?>) info.get("requires")).stream().map(Object::toString).toList()
                        : List.of();
                double moveCost = info.get("moveCost") != null
                        ? ((Number) info.get("moveCost")).doubleValue()
                        : 1.0;
                terrains.put(ch, new WorldSnapshot.TerrainInfo(
                        (String) info.get("color"), (String) info.get("textColor"),
                        requires, moveCost));
            });
        }

        // Current terrain info
        int px = pos.getInt("x");
        int py = pos.getInt("y");
        String currentTerrain = null;
        String currentTerrainName = null;
        if (terrain != null && py < terrain.size()) {
            String row = terrain.get(py);
            if (px < row.length()) {
                currentTerrain = String.valueOf(row.charAt(px));
                if (terrainsRaw != null && terrainsRaw.containsKey(currentTerrain)) {
                    Object idObj = terrainsRaw.get(currentTerrain).get("id");
                    if (idObj != null) currentTerrainName = idObj.toString();
                }
            }
        }

        // POIs
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> poisRaw = (List<Map<String, Object>>) mapData.get("pois");
        List<WorldSnapshot.PoiInfo> pois = new ArrayList<>();
        if (poisRaw != null) {
            for (Map<String, Object> poiMap : poisRaw) {
                pois.add(new WorldSnapshot.PoiInfo(
                        (String) poiMap.get("id"),
                        ((Number) poiMap.get("x")).intValue(),
                        ((Number) poiMap.get("y")).intValue(),
                        (String) poiMap.get("type"),
                        (String) poiMap.get("target"),
                        (String) poiMap.get("label")));
            }
        }

        return new WorldSnapshot.MapSnapshot(
                mapId, mapName, px, py,
                mapData.getInt("width"), mapData.getInt("height"),
                terrain, terrains, currentTerrain, currentTerrainName, pois);
    }

    @SuppressWarnings("unchecked")
    private WorldSnapshot.CombatSnapshot buildCombatSnapshot(String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) return null;
        for (String tag : player.getTags()) {
            if (tag.startsWith("combat:")) {
                String combatId = tag.substring(7);
                Entity combat = entityStore.get(combatId);
                if (combat == null || !combat.hasComponent("CombatState")) continue;
                Component state = combat.getComponent("CombatState");

                int turnTimer = state.has("turnTimer") ? state.getInt("turnTimer") : 30;

                List<WorldSnapshot.CombatantInfo> combatants = new ArrayList<>();
                for (Entity c : entityStore.getByTag("combat:" + combatId)) {
                    if (!c.hasComponent("Health")) continue;
                    Component health = c.getComponent("Health");
                    String name = c.hasComponent("Name") ? c.getComponent("Name").getString("value") : c.getId();
                    String side = c.hasTag("player") ? "PLAYER" : "ENEMY";
                    List<WorldSnapshot.BuffInfo> buffList = new ArrayList<>();
                    for (Component comp : c.getAllComponents()) {
                        if (comp.getType().startsWith("Buff_")) {
                            String buffId = comp.getType().substring(5);
                            int stacks = comp.has("stacks") ? comp.getInt("stacks") : 1;
                            String color = comp.has("color") ? (String) comp.get("color") : null;
                            buffList.add(new WorldSnapshot.BuffInfo(buffId, stacks, color));
                        }
                    }
                    String row = "FRONT";
                    int slot = 0;
                    if (c.hasComponent("CombatPosition")) {
                        Component pos = c.getComponent("CombatPosition");
                        row = pos.has("row") ? pos.getString("row") : "FRONT";
                        slot = pos.has("slot") ? pos.getInt("slot") : 0;
                    }
                    combatants.add(new WorldSnapshot.CombatantInfo(
                            c.getId(), name, side,
                            health.getInt("hp"), health.getInt("maxHp"),
                            health.getInt("hp") > 0, buffList, row, slot));
                }

                // Read combat events (pending playback)
                List<WorldSnapshot.CombatEvent> events = new ArrayList<>();
                if (combat.hasComponent("CombatEvents")) {
                    List<Object> rawEvents = (List<Object>) combat.getComponent("CombatEvents").get("queue");
                    if (rawEvents != null) {
                        for (Object evtObj : rawEvents) {
                            Map<String, Object> evt = (Map<String, Object>) evtObj;
                            List<WorldSnapshot.TextSegment> segments = new ArrayList<>();
                            List<Object> segs = (List<Object>) evt.get("segments");
                            if (segs != null) {
                                for (Object segObj : segs) {
                                    Map<String, Object> seg = (Map<String, Object>) segObj;
                                    segments.add(new WorldSnapshot.TextSegment(
                                            (String) seg.get("text"), (String) seg.get("color")));
                                }
                            }
                            List<WorldSnapshot.Effect> effects = new ArrayList<>();
                            List<Object> effs = (List<Object>) evt.get("effects");
                            if (effs != null) {
                                for (Object effObj : effs) {
                                    Map<String, Object> eff = (Map<String, Object>) effObj;
                                    effects.add(new WorldSnapshot.Effect(
                                            (String) eff.get("target"),
                                            (String) eff.get("type"),
                                            eff));
                                }
                            }
                            List<Map<String, Object>> animation = new ArrayList<>();
                            List<Object> anims = (List<Object>) evt.get("animation");
                            if (anims != null) {
                                for (Object animObj : anims) {
                                    animation.add((Map<String, Object>) animObj);
                                }
                            }
                            events.add(new WorldSnapshot.CombatEvent(segments, effects, animation));
                        }
                    }
                }

                return new WorldSnapshot.CombatSnapshot(combatId, state.getString("phase"),
                        state.getInt("round"), turnTimer, combatants, events);
            }
        }
        return null;
    }
}
