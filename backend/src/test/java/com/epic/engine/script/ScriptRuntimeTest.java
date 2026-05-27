package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.core.ModifierChainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScriptRuntimeTest {

    ScriptRuntime runtime;
    EventBus bus;
    EntityStore store;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void executeScript_registersHandler() {
        String script = """
            engine.on("test.hello", 100, function(event) {
                event.set("greeting", "world");
            });
            """;
        runtime.execute(script, "test_handler.js");

        GameEvent event = new GameEvent("test.hello");
        bus.fire("test.hello", event);

        assertThat((String) event.get("greeting")).isEqualTo("world");
    }

    @Test
    void script_canAccessEntityStore() {
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        player.addComponent(health);
        store.add(player);

        String script = """
            engine.on("test.damage", 100, function(event) {
                var entity = store.get("player1");
                var hp = entity.getComponent("Health").getInt("hp");
                entity.getComponent("Health").set("hp", hp - 10);
            });
            """;
        runtime.execute(script, "damage.js");

        bus.fire("test.damage", new GameEvent("test.damage"));

        assertThat(player.getComponent("Health").getInt("hp")).isEqualTo(90);
    }

    @Test
    void script_canFireEvents() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.chained", 100, event -> results.add("chained"));

        String script = """
            engine.on("test.trigger", 100, function(event) {
                engine.fire("test.chained", engine.newEvent("test.chained"));
            });
            """;
        runtime.execute(script, "chain.js");

        bus.fire("test.trigger", new GameEvent("test.trigger"));

        assertThat(results).containsExactly("chained");
    }

    @Test
    void script_canCancelEvent() {
        var results = new java.util.ArrayList<String>();

        String script = """
            engine.on("test.cancel", 50, function(event) {
                event.cancel();
            });
            """;
        runtime.execute(script, "canceller.js");
        bus.on("test.cancel", 100, event -> results.add("should not run"));

        bus.fire("test.cancel", new GameEvent("test.cancel"));

        assertThat(results).isEmpty();
    }

    @Test
    void script_addModifier_recalculatesEntity() {
        ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
        ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
        runtime.close();
        runtime = new ScriptRuntime(bus, store, chainService, typeReg);

        Entity player = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        player.addComponent(stats);
        store.add(player);

        String script = """
            engine.setBase("p1");
            engine.addModifier("p1", {
                id: "class_warrior",
                typeId: "class",
                label: "战士",
                priority: 180,
                apply: function(entity) {
                    var s = entity.getComponent("CombatStats");
                    s.set("attack", s.getInt("attack") + 3);
                }
            });
            """;
        runtime.execute(script, "test.js");

        assertThat(player.getComponent("CombatStats").getInt("attack")).isEqualTo(13);
    }
}
