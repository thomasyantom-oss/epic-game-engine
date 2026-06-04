package com.epic.engine.sim;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds simulator combatants through the same script events used by the real game. */
public class CombatantBuilder {
    private final EventBus bus;
    private final EntityStore store;
    private final SessionService sessions;

    public CombatantBuilder(EventBus bus, EntityStore store, SessionService sessions) {
        this.bus = bus;
        this.store = store;
        this.sessions = sessions;
    }

    public String buildPlayer(String classId, int level, List<Map<String, Object>> sources) {
        String token = sessions.createSession();
        GameEvent create = new GameEvent("action.confirm_character");
        create.set("characterId", "sim_char_" + classId + "_" + level + "_" + System.nanoTime());
        create.set("sessionToken", token);
        create.set("name", "sim_" + classId + "_" + level + "_" + System.nanoTime());
        create.set("class", classId);
        bus.fire("action.confirm_character", create);
        String playerId = sessions.getSession(token).activeCharacterId();
        Entity player = store.get(playerId);
        if (player == null) {
            throw new IllegalStateException("Character creation did not produce a player");
        }
        if (level > 1) {
            player.getComponent("Experience").set("level", level);
            Component character = player.getComponent("Character");
            if (character != null) character.set("level", level);
        }
        GameEvent reset = new GameEvent("sim.reset_player_modifiers");
        reset.set("playerId", playerId);
        bus.fire("sim.reset_player_modifiers", reset);

        if (sources != null) {
            for (int i = 0; i < sources.size(); i++) {
                Map<String, Object> source = sources.get(i);
                GameEvent event = new GameEvent("sim.apply_source");
                event.set("playerId", playerId);
                event.set("source", source);
                event.set("sourceIndex", i);
                event.set("sourceId", "sim_src_" + System.nanoTime() + "_" + i);
                bus.fire("sim.apply_source", event);
            }
        }
        GameEvent weapon = new GameEvent("sim.apply_level_weapon");
        weapon.set("playerId", playerId);
        weapon.set("level", level);
        bus.fire("sim.apply_level_weapon", weapon);
        return playerId;
    }

    public record Spawn(String combatId, List<String> enemyIds) {}

    public Spawn spawnEncounter(String playerId, String encounterId) {
        GameEvent before = new GameEvent("sim.before_start_encounter");
        before.set("encounterId", encounterId);
        bus.fire("sim.before_start_encounter", before);

        GameEvent event = new GameEvent("combat.start_encounter");
        event.set("playerId", playerId);
        event.set("encounterId", encounterId);
        bus.fire("combat.start_encounter", event);

        GameEvent scale = new GameEvent("sim.scale_encounter");
        scale.set("encounterId", encounterId);
        scale.set("level", playerLevel(playerId));
        bus.fire("sim.scale_encounter", scale);

        String combatId = combatIdForPlayer(playerId);
        List<String> enemyIds = new ArrayList<>();
        for (Entity entity : store.getByTagAsList("combat:" + combatId)) {
            if (entity.hasTag("enemy")) enemyIds.add(entity.getId());
        }
        return new Spawn(combatId, enemyIds);
    }

    public void cleanup(String playerId, Spawn spawn) {
        GameEvent cleanup = new GameEvent("sim.cleanup_combat");
        cleanup.set("playerId", playerId);
        cleanup.set("enemyIds", spawn.enemyIds());
        bus.fire("sim.cleanup_combat", cleanup);

        for (String enemyId : spawn.enemyIds()) {
            store.remove(enemyId);
        }
        store.remove(spawn.combatId());
        Entity player = store.get(playerId);
        if (player != null) {
            player.removeTag("combat:" + spawn.combatId());
            player.removeComponent("CombatPosition");
            store.reindexTags(player);
        }
    }

    public String makeCombat(String playerId, String enemyId) {
        String combatId = "sim_combat_" + System.nanoTime();
        Entity combat = new Entity(combatId);
        Component state = new Component("CombatState");
        state.set("round", 0);
        state.set("phase", "COMMAND");
        combat.addComponent(state);
        store.add(combat);
        tagForCombat(store.get(playerId), combatId);
        tagForCombat(store.get(enemyId), combatId);
        return combatId;
    }

    public String makeRef(String id, String kind) {
        Entity entity = new Entity(id);
        Component health = new Component("Health");
        health.set("hp", 1_000_000);
        health.set("maxHp", 1_000_000);
        entity.addComponent(health);
        Component combatStats = new Component("CombatStats");
        combatStats.set("attack", "attacker".equals(kind) ? 30 : 1);
        combatStats.set("defense", 0);
        combatStats.set("speed", "attacker".equals(kind) ? 99 : 1);
        entity.addComponent(combatStats);
        Component resistances = new Component("Resistances");
        resistances.set("物理", 0);
        resistances.set("法术", 0);
        resistances.set("精神", 0);
        entity.addComponent(resistances);
        entity.addTag("enemy");
        store.add(entity);
        return id;
    }

    private String combatIdForPlayer(String playerId) {
        Entity player = store.get(playerId);
        for (String tag : player.getTags()) {
            if (tag.startsWith("combat:")) return tag.substring(7);
        }
        throw new IllegalStateException("Player is not in combat: " + playerId);
    }

    private int playerLevel(String playerId) {
        Entity player = store.get(playerId);
        Component experience = player != null ? player.getComponent("Experience") : null;
        if (experience != null && experience.has("level")) return experience.getInt("level");
        Component character = player != null ? player.getComponent("Character") : null;
        if (character != null && character.has("level")) return character.getInt("level");
        return 1;
    }

    private void tagForCombat(Entity entity, String combatId) {
        entity.addTag("combat:" + combatId);
        store.reindexTags(entity);
    }
}
