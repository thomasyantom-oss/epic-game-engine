package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CombatTimeoutTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.bindService("buffs", new BuffService(bus, store));
        runtime.execute("var persistence = { save: function(){} };", "persistence_stub.js");

        Path combat = Path.of("../mods/base-rules/handlers/combat");
        runtime.execute(Files.readString(combat.resolve("initiative.js")), "initiative.js");
        runtime.execute(Files.readString(combat.resolve("death_check.js")), "death_check.js");
        runtime.execute(Files.readString(combat.resolve("combat_flow.js")), "combat_flow.js");

        Path skill = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        runtime.execute(Files.readString(skill.resolve("01_effects.js")), "01_effects.js");
        runtime.execute(Files.readString(skill.resolve("02_dispatch.js")), "02_dispatch.js");
        runtime.execute(Files.readString(combat.resolve("start_combat.js")), "start_combat.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void staleCommandRound_autoResolvesAndAdvancesRound() {
        setupCombat(System.currentTimeMillis() - 31_000, System.currentTimeMillis());

        GameEvent event = new GameEvent("combat.check_timeouts");
        event.set("playerId", "player1");
        bus.fire("combat.check_timeouts", event);

        Entity combat = store.get("battle1");
        assertThat(combat.getComponent("CombatState").getString("phase")).isEqualTo("COMMAND");
        assertThat(combat.getComponent("CombatState").getInt("round")).isEqualTo(2);
        assertThat(store.get("goblin1").getComponent("Health").getInt("hp")).isLessThan(20);
        assertThat(store.get("player1").getComponent("Health").getInt("hp")).isLessThan(100);
    }

    @Test
    void longInactiveCombat_fleesAndClearsCombatTag() {
        setupCombat(System.currentTimeMillis(), System.currentTimeMillis() - 16 * 60_000);

        GameEvent event = new GameEvent("combat.check_timeouts");
        event.set("playerId", "player1");
        bus.fire("combat.check_timeouts", event);

        Entity player = store.get("player1");
        assertThat(player.getTags()).doesNotContain("combat:battle1");
        assertThat(store.get("battle1")).isNull();
    }

    private void setupCombat(long roundStartTime, long lastInteractionTime) {
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 10);
        mana.set("maxMp", 10);
        player.addComponent(mana);
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        stats.set("defense", 5);
        stats.set("speed", 6);
        player.addComponent(stats);
        Component res = new Component("Resistances");
        res.set("物理", 0);
        res.set("法术", 0);
        res.set("精神", 0);
        player.addComponent(res);
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
        Component gRes = new Component("Resistances");
        gRes.set("物理", 0);
        gRes.set("法术", 0);
        gRes.set("精神", 0);
        goblin.addComponent(gRes);
        goblin.addTag("enemy");
        goblin.addTag("combat:battle1");
        store.add(goblin);

        Entity combat = new Entity("battle1");
        Component state = new Component("CombatState");
        state.set("round", 1);
        state.set("phase", "COMMAND");
        state.set("turnTimer", 30);
        state.set("roundStartTime", roundStartTime);
        state.set("lastInteractionTime", lastInteractionTime);
        combat.addComponent(state);
        store.add(combat);
    }
}
