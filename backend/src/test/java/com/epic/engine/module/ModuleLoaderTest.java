package com.epic.engine.module;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleLoaderTest {

    ModuleLoader loader;
    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        Path modsPath = Path.of("src/test/resources/test-mods");
        loader = new ModuleLoader(modsPath, runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void discoverAndLoadModules() throws Exception {
        List<ModuleDescriptor> mods = loader.discoverModules();

        assertThat(mods).hasSize(1);
        assertThat(mods.get(0).id()).isEqualTo("test-mod");
    }

    @Test
    void loadedHandlers_registerOnEventBus() throws Exception {
        loader.loadAll();

        GameEvent event = new GameEvent("test.hello");
        bus.fire("test.hello", event);

        assertThat((String) event.get("message")).isEqualTo("hello from module");
    }
}
