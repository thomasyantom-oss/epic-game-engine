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
            return WorldSnapshot.characterSelect(token, List.of(), sessionService.getMaxSlots());
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
                sessionService.getMaxSlots());
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
                List.of());
    }

    private WorldSnapshot.MapSnapshot buildMapSnapshot(Entity player) {
        if (!player.hasComponent("Position")) return null;
        Component pos = player.getComponent("Position");
        String mapId = pos.getString("map");
        Entity map = entityStore.get(mapId);
        if (map == null || !map.hasComponent("MapData")) return null;
        Component mapData = map.getComponent("MapData");

        @SuppressWarnings("unchecked")
        List<String> terrain = (List<String>) mapData.get("terrain");

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> terrainsRaw = (Map<String, Map<String, Object>>) mapData.get("terrains");
        Map<String, WorldSnapshot.TerrainInfo> terrains = new LinkedHashMap<>();
        if (terrainsRaw != null) {
            terrainsRaw.forEach((ch, info) -> terrains.put(ch,
                    new WorldSnapshot.TerrainInfo((String) info.get("color"), (String) info.get("textColor"))));
        }

        return new WorldSnapshot.MapSnapshot(
                mapId, pos.getInt("x"), pos.getInt("y"),
                mapData.getInt("width"), mapData.getInt("height"),
                terrain, terrains);
    }

    private WorldSnapshot.CombatSnapshot buildCombatSnapshot(String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) return null;
        for (String tag : player.getTags()) {
            if (tag.startsWith("combat:")) {
                String combatId = tag.substring(7);
                Entity combat = entityStore.get(combatId);
                if (combat == null || !combat.hasComponent("CombatState")) continue;
                Component state = combat.getComponent("CombatState");
                List<WorldSnapshot.CombatantInfo> combatants = new ArrayList<>();
                for (Entity c : entityStore.getByTag("combat:" + combatId)) {
                    if (!c.hasComponent("Health")) continue;
                    Component health = c.getComponent("Health");
                    String name = c.hasComponent("Name") ? c.getComponent("Name").getString("value") : c.getId();
                    String side = c.hasTag("player") ? "PLAYER" : "ENEMY";
                    combatants.add(new WorldSnapshot.CombatantInfo(
                            c.getId(), name, side,
                            health.getInt("hp"), health.getInt("maxHp"),
                            health.getInt("hp") > 0));
                }
                return new WorldSnapshot.CombatSnapshot(combatId, state.getString("phase"),
                        state.getInt("round"), combatants);
            }
        }
        return null;
    }
}
