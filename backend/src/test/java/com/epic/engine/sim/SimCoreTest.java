package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimCoreTest {
    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        Path combat = Path.of("../mods/base-rules/handlers/combat");
        for (String file : List.of("initiative.js", "death_check.js", "combat_flow.js")) {
            runtime.execute(Files.readString(combat.resolve(file)), file);
        }
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        for (String file : List.of("00_skill_lib.js", "01_effects.js", "02_dispatch.js")) {
            runtime.execute(Files.readString(skill.resolve(file)), file);
        }
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void fightRunnerRunsRealRoundsAndTraceCapturesDamage() {
        setupCombat();
        FightRunner runner = new FightRunner(store, bus);
        DamageTap tap = new DamageTap(bus, runner::round);
        Policy policy = (unit, combatId, entityStore) -> {
            Entity target = HeuristicPolicy.lowestHpEnemy(unit, combatId, entityStore);
            if (target == null) return null;
            Map<String, Object> command = new HashMap<>();
            command.put("type", "basic_attack");
            command.put("targetId", target.getId());
            return command;
        };

        FightResult result = runner.run("battle1", List.of("player1"), List.of("goblin1"),
                Map.of("player1", policy, "goblin1", policy), 50, tap);

        assertThat(result.outcome()).isEqualTo(FightOutcome.WIN);
        assertThat(result.rounds()).isEqualTo(3);
        assertThat(result.trace().damageBySkill().get("basic_attack")).isGreaterThan(0L);
        assertThat(result.trace().mitigationSavedByTarget().get("goblin1")).isEqualTo(3L);
    }

    @Test
    void batchMetricsKeepsTimeoutSeparateFromLoss() {
        BatchMetrics metrics = BatchMetrics.of(List.of(
                new FightResult(FightOutcome.WIN, 3, 0.5, 0.0),
                new FightResult(FightOutcome.LOSS, 5, 0.0, 0.2),
                new FightResult(FightOutcome.TIMEOUT, 10, 0.4, 0.4)
        ));

        assertThat(metrics.winRate()).isEqualTo(1.0 / 3.0);
        assertThat(metrics.lossRate()).isEqualTo(1.0 / 3.0);
        assertThat(metrics.timeoutRate()).isEqualTo(1.0 / 3.0);
        assertThat(metrics.winRate() + metrics.lossRate() + metrics.timeoutRate()).isEqualTo(1.0);
        assertThat(metrics.enemyTtkMedian()).isEqualTo(5.0);
    }

    @Test
    void armorCurveMathMatchesExpectedMitigation() {
        setupCombat();
        store.get("goblin1").getComponent("Health").set("hp", 100);
        store.get("goblin1").getComponent("Health").set("maxHp", 100);
        store.get("player1").getComponent("CombatStats").set("attack", 30);
        store.get("goblin1").getComponent("CombatStats").set("defense", 10);
        runOnePlayerAction("basic_attack");
        assertThat(store.get("goblin1").getComponent("Health").getInt("hp")).isEqualTo(77);

        store.get("goblin1").getComponent("Health").set("hp", 100);
        store.get("goblin1").getComponent("Health").set("maxHp", 100);
        store.get("player1").getComponent("CombatStats").set("attack", 10);
        store.get("goblin1").getComponent("CombatStats").set("defense", 50);
        runOnePlayerAction("basic_attack");
        assertThat(store.get("goblin1").getComponent("Health").getInt("hp")).isEqualTo(98);

        store.get("goblin1").getComponent("Health").set("hp", 100);
        store.get("player1").getComponent("CombatStats").set("attack", 100);
        runOnePlayerAction("basic_attack");
        assertThat(store.get("goblin1").getComponent("Health").getInt("hp")).isEqualTo(33);
    }

    @Test
    void resistCapMathMatchesExpectedMitigation() {
        setupCombat();
        store.get("goblin1").getComponent("Health").set("hp", 100);
        store.get("goblin1").getComponent("Health").set("maxHp", 100);
        CombatTuning tuning = new CombatTuning();
        tuning.setResistBounds(50, -50);
        runtime.bindService("tuning", tuning);
        Component resistances = new Component("Resistances");
        resistances.set("物理", 0);
        resistances.set("法术", 90);
        resistances.set("精神", 0);
        store.get("goblin1").addComponent(resistances);

        runOnePlayerAction("fireball");

        assertThat(store.get("goblin1").getComponent("Health").getInt("hp")).isEqualTo(95);
    }

    @Test
    void damageVarianceIsIdentityWhenZeroAndSeedReproducibleWhenEnabled() {
        CombatTuning identity = new CombatTuning();
        identity.setVariance(0.0, 1L);
        assertThat(identity.rollVariance()).isEqualTo(1.0);
        assertThat(identity.rollVariance()).isEqualTo(1.0);

        CombatTuning a = new CombatTuning();
        CombatTuning b = new CombatTuning();
        a.setVariance(0.20, 42L);
        b.setVariance(0.20, 42L);

        double first = a.rollVariance();
        assertThat(first).isBetween(0.8, 1.2);
        assertThat(b.rollVariance()).isEqualTo(first);
        assertThat(b.rollVariance()).isEqualTo(a.rollVariance());
    }

    private void setupCombat() {
        Entity player = new Entity("player1");
        Component playerHealth = new Component("Health");
        playerHealth.set("hp", 100);
        playerHealth.set("maxHp", 100);
        player.addComponent(playerHealth);
        Component playerStats = new Component("CombatStats");
        playerStats.set("attack", 10);
        playerStats.set("defense", 5);
        playerStats.set("speed", 6);
        player.addComponent(playerStats);
        player.addTag("player");
        player.addTag("combat:battle1");
        store.add(player);

        Entity enemy = new Entity("goblin1");
        Component enemyHealth = new Component("Health");
        enemyHealth.set("hp", 20);
        enemyHealth.set("maxHp", 20);
        enemy.addComponent(enemyHealth);
        Component enemyStats = new Component("CombatStats");
        enemyStats.set("attack", 5);
        enemyStats.set("defense", 2);
        enemyStats.set("speed", 3);
        enemy.addComponent(enemyStats);
        enemy.addTag("enemy");
        enemy.addTag("combat:battle1");
        store.add(enemy);

        Entity combat = new Entity("battle1");
        Component state = new Component("CombatState");
        state.set("round", 0);
        state.set("phase", "COMMAND");
        combat.addComponent(state);
        store.add(combat);
    }

    private void runOnePlayerAction(String skill) {
        Map<String, Object> command = new HashMap<>();
        command.put("type", skill);
        command.put("targetId", "goblin1");
        com.epic.engine.core.GameEvent event = new com.epic.engine.core.GameEvent("combat.unit_action");
        event.set("actorId", "player1");
        event.set("combatId", "battle1");
        event.set("command", command);
        bus.fire("combat.unit_action", event);
    }

    private void loadCombatScripts() {
        try {
            Path combat = Path.of("../mods/base-rules/handlers/combat");
            for (String file : List.of("initiative.js", "death_check.js", "combat_flow.js")) {
                runtime.execute(Files.readString(combat.resolve(file)), file);
            }
            Path skill = Path.of("../mods/base-rules/handlers/skill");
            for (String file : List.of("00_skill_lib.js", "01_effects.js", "02_dispatch.js")) {
                runtime.execute(Files.readString(skill.resolve(file)), file);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
