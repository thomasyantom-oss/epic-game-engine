package com.epic.engine.script;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HotReloaderTest {

    @TempDir
    Path tempDir;

    ScriptRuntime runtime;
    HotReloader reloader;

    @AfterEach
    void tearDown() {
        if (reloader != null) reloader.stop();
        if (runtime != null) runtime.close();
    }

    @Test
    void reloadScript_updatesHandler() throws Exception {
        EventBus bus = new EventBus();
        EntityStore store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path handlerFile = tempDir.resolve("test.js");
        Files.writeString(handlerFile, """
            engine.on("test.value", 100, function(event) {
                event.set("val", 1);
            });
            """);
        runtime.execute(Files.readString(handlerFile), "test.js");

        // Verify initial
        GameEvent event1 = new GameEvent("test.value");
        bus.fire("test.value", event1);
        assertThat((int) event1.get("val")).isEqualTo(1);

        // Simulate hot reload with new content
        reloader = new HotReloader(runtime, bus);
        String newScript = """
            engine.on("test.value", 100, function(event) {
                event.set("val", 2);
            });
            """;
        reloader.reload("test.js", newScript);

        // Verify updated
        GameEvent event2 = new GameEvent("test.value");
        bus.fire("test.value", event2);
        assertThat((int) event2.get("val")).isEqualTo(2);
    }

    @Test
    void reloadScript_rebindsExistingEntityModifiersWithoutInflation() {
        EventBus bus = new EventBus();
        EntityStore store = new EntityStore();
        ModifierTypeRegistry typeRegistry = new ModifierTypeRegistry();
        ModifierChainService chains = new ModifierChainService(bus, store, typeRegistry);
        runtime = new ScriptRuntime(bus, store, chains, typeRegistry);

        Entity hero = new Entity("hero");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        hero.addComponent(stats);
        store.add(hero);
        chains.setBase("hero");

        reloader = new HotReloader(runtime, bus, store);
        reloader.reload("modifier.js", """
            engine.on("entity.loaded", 100, function(event) {
                var e = event.get("entity");
                if (e.getId() !== "hero") return;
                engine.addModifier(e.getId(), {
                    typeId: "test",
                    id: "hot_reload_attack",
                    label: "hot",
                    apply: function(ent) {
                        var s = ent.getComponent("CombatStats");
                        s.set("attack", s.getInt("attack") + 5);
                    }
                });
                engine.recalculate(e.getId());
            });
            """);
        assertThat(hero.getComponent("CombatStats").getInt("attack")).isEqualTo(15);

        reloader.reload("modifier.js", """
            engine.on("entity.loaded", 100, function(event) {
                var e = event.get("entity");
                if (e.getId() !== "hero") return;
                engine.addModifier(e.getId(), {
                    typeId: "test",
                    id: "hot_reload_attack",
                    label: "hot",
                    apply: function(ent) {
                        var s = ent.getComponent("CombatStats");
                        s.set("attack", s.getInt("attack") + 7);
                    }
                });
                engine.recalculate(e.getId());
            });
            """);

        assertThat(hero.getComponent("CombatStats").getInt("attack")).isEqualTo(17);
    }
}
