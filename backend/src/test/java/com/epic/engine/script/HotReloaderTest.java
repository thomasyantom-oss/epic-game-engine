package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
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
}
