package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SnapshotService {

    private final EventBus eventBus;
    private final EntityStore entityStore;

    public SnapshotService(EventBus eventBus, EntityStore entityStore) {
        this.eventBus = eventBus;
        this.entityStore = entityStore;
    }

    public WorldSnapshot buildSnapshot(String playerId, WorldSnapshot.ActionResult result) {
        Entity player = entityStore.get(playerId);
        if (player == null) {
            return new WorldSnapshot(playerId, result, List.of(), List.of(), null, null, List.of(), List.of());
        }

        // Fire ui.render_status event to let modules contribute bars/buffs
        GameEvent uiEvent = new GameEvent("ui.render_status");
        uiEvent.set("entityId", playerId);
        uiEvent.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        uiEvent.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        eventBus.fire("ui.render_status", uiEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = uiEvent.get("bars");
        @SuppressWarnings("unchecked")
        List<WorldSnapshot.BuffEntry> buffs = uiEvent.get("buffs");

        // Fire ui.render_actions event
        GameEvent actionsEvent = new GameEvent("ui.render_actions");
        actionsEvent.set("entityId", playerId);
        actionsEvent.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        eventBus.fire("ui.render_actions", actionsEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = actionsEvent.get("actions");

        return new WorldSnapshot(
                playerId, result,
                bars != null ? bars : List.of(),
                buffs != null ? buffs : List.of(),
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions != null ? actions : List.of(),
                List.of()
        );
    }

    private WorldSnapshot.MapSnapshot buildMapSnapshot(Entity player) {
        if (!player.hasComponent("Position")) return null;
        Component pos = player.getComponent("Position");
        String mapId = pos.getString("map");
        Entity map = entityStore.get(mapId);
        if (map == null || !map.hasComponent("MapData")) return null;

        Component mapData = map.getComponent("MapData");
        return new WorldSnapshot.MapSnapshot(
                mapId, pos.getInt("x"), pos.getInt("y"),
                mapData.getInt("width"), mapData.getInt("height")
        );
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
                            health.getInt("hp") > 0
                    ));
                }

                return new WorldSnapshot.CombatSnapshot(
                        combatId, state.getString("phase"),
                        state.getInt("round"), combatants
                );
            }
        }
        return null;
    }
}
