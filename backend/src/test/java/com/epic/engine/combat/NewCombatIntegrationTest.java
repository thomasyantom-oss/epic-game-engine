package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewCombatIntegrationTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));

        Path baseDir = Path.of("../mods/base-rules/handlers/combat");
        runtime.execute(Files.readString(baseDir.resolve("initiative.js")), "initiative.js");
        runtime.execute(Files.readString(baseDir.resolve("death_check.js")), "death_check.js");
        runtime.execute(Files.readString(baseDir.resolve("combat_flow.js")), "combat_flow.js");

        // Load skill lib + dispatcher (replaces the old bespoke basic_attack.js)
        Path sklDir = Path.of("../mods/base-rules/handlers/skill");
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.execute(Files.readString(sklDir.resolve("00_skill_lib.js")), "00_skill_lib.js");
        runtime.execute(Files.readString(sklDir.resolve("01_effects.js")), "01_effects.js");
        runtime.execute(Files.readString(sklDir.resolve("02_dispatch.js")), "02_dispatch.js");

        Path skillsDir = Path.of("../mods/base-rules/skills");
        runtime.execute(Files.readString(skillsDir.resolve("defend.js")), "defend.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void combatRound_attackDealsDamage() {
        setupCombat();

        Map<String, Object> playerCmd = new HashMap<>();
        playerCmd.put("type", "basic_attack");
        playerCmd.put("targetId", "goblin1");
        Map<String, Object> enemyCmd = new HashMap<>();
        enemyCmd.put("type", "basic_attack");
        enemyCmd.put("targetId", "player1");
        Map<String, Object> commands = new HashMap<>();
        commands.put("player1", playerCmd);
        commands.put("goblin1", enemyCmd);

        GameEvent resolveEvent = new GameEvent("combat.resolve_round");
        resolveEvent.set("combatId", "battle1");
        resolveEvent.set("commands", commands);
        bus.fire("combat.resolve_round", resolveEvent);

        Entity goblin = store.get("goblin1");
        Entity player = store.get("player1");
        // Player attacks goblin: ceil(10^2/(2+10)) = 9 damage -> goblin hp = 11
        assertThat(goblin.getComponent("Health").getInt("hp")).isEqualTo(11);
        // Goblin attacks player: ceil(5^2/(5+5)) = 3 damage -> player hp = 97
        assertThat(player.getComponent("Health").getInt("hp")).isEqualTo(97);
    }

    @Test
    void combatRound_defendReducesDamage() {
        setupCombat();
        // Give goblin high attack so defend effect is visible
        store.get("goblin1").getComponent("CombatStats").set("attack", 15);

        Map<String, Object> playerCmd = new HashMap<>();
        playerCmd.put("type", "defend");
        Map<String, Object> enemyCmd = new HashMap<>();
        enemyCmd.put("type", "basic_attack");
        enemyCmd.put("targetId", "player1");
        Map<String, Object> commands = new HashMap<>();
        commands.put("player1", playerCmd);
        commands.put("goblin1", enemyCmd);

        GameEvent resolveEvent = new GameEvent("combat.resolve_round");
        resolveEvent.set("combatId", "battle1");
        resolveEvent.set("commands", commands);
        bus.fire("combat.resolve_round", resolveEvent);

        Entity player = store.get("player1");
        // Without defend: ceil(15^2/(5+15)) = 12 damage
        // With defend: floor(12 * 0.5) = 6 damage -> player hp = 94
        assertThat(player.getComponent("Health").getInt("hp")).isEqualTo(94);
    }

    @Test
    void combatRound_detectsVictory() {
        setupCombat();
        // Set goblin to 1 hp so it dies in one hit
        store.get("goblin1").getComponent("Health").set("hp", 1);

        Map<String, Object> playerCmd = new HashMap<>();
        playerCmd.put("type", "basic_attack");
        playerCmd.put("targetId", "goblin1");
        Map<String, Object> enemyCmd = new HashMap<>();
        enemyCmd.put("type", "basic_attack");
        enemyCmd.put("targetId", "player1");
        Map<String, Object> commands = new HashMap<>();
        commands.put("player1", playerCmd);
        commands.put("goblin1", enemyCmd);

        GameEvent resolveEvent = new GameEvent("combat.resolve_round");
        resolveEvent.set("combatId", "battle1");
        resolveEvent.set("commands", commands);
        bus.fire("combat.resolve_round", resolveEvent);

        Entity combat = store.get("battle1");
        assertThat(combat.getComponent("CombatState").getString("phase")).isEqualTo("VICTORY");
    }

    private void setupCombat() {
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        stats.set("defense", 5);
        stats.set("speed", 6);
        player.addComponent(stats);
        player.addTag("player");
        player.addTag("combat:battle1");
        store.add(player);

        Entity goblin = new Entity("goblin1");
        Component gHealth = new Component("Health");
        gHealth.set("hp", 20);
        gHealth.set("maxHp", 20);
        goblin.addComponent(gHealth);
        Component gStats = new Component("CombatStats");
        gStats.set("attack", 5);
        gStats.set("defense", 2);
        gStats.set("speed", 3);
        goblin.addComponent(gStats);
        goblin.addTag("enemy");
        goblin.addTag("combat:battle1");
        store.add(goblin);

        Entity combat = new Entity("battle1");
        Component combatState = new Component("CombatState");
        combatState.set("round", 0);
        combatState.set("phase", "COMMAND");
        combat.addComponent(combatState);
        store.add(combat);
    }
}
