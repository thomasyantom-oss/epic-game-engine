package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoadYamlTest {

    @TempDir Path tempDir;
    ScriptRuntime runtime;
    EventBus bus;
    EntityStore store;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path entitiesDir = tempDir.resolve("entities/maps");
        Files.createDirectories(entitiesDir);
        Files.writeString(entitiesDir.resolve("test_map.yaml"), """
            id: test_map
            width: 5
            height: 5
            name: "测试地图"
            """);
    }

    @AfterEach
    void tearDown() { runtime.close(); }

    @Test
    void loadYaml_returnsMapData() {
        runtime.setModuleContext(tempDir);

        String script = """
            engine.on("test.load", 100, function(event) {
                var data = engine.loadYaml("entities/maps/test_map.yaml");
                event.set("mapId", data.get("id"));
                event.set("width", data.get("width"));
            });
            """;
        runtime.execute(script, "test.js");

        GameEvent event = new GameEvent("test.load");
        bus.fire("test.load", event);

        assertThat((String) event.get("mapId")).isEqualTo("test_map");
        assertThat(((Number) event.get("width")).intValue()).isEqualTo(5);
    }
}
