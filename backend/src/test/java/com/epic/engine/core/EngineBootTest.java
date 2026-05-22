package com.epic.engine.core;

import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EngineBootTest {

    @Autowired EventBus eventBus;
    @Autowired EntityStore entityStore;
    @Autowired ScriptRuntime scriptRuntime;
    @Autowired SchemaRegistry schemaRegistry;

    @Test
    void engineCoreBeansAreWired() {
        assertThat(eventBus).isNotNull();
        assertThat(entityStore).isNotNull();
        assertThat(scriptRuntime).isNotNull();
        assertThat(schemaRegistry).isNotNull();
    }

    @Test
    void eventBus_isOperational() {
        var results = new java.util.ArrayList<String>();
        eventBus.on("boot.test", 100, e -> results.add("ok"));
        eventBus.fire("boot.test", new GameEvent("boot.test"));
        assertThat(results).containsExactly("ok");
    }
}
