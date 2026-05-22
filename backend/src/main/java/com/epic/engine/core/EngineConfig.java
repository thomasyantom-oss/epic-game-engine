package com.epic.engine.core;

import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EngineConfig {

    @Value("${epic.mods-path:../mods}")
    private String modsPath;

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    public EntityStore entityStore() {
        return new EntityStore();
    }

    @Bean
    public ScriptRuntime scriptRuntime(EventBus eventBus, EntityStore entityStore) {
        return new ScriptRuntime(eventBus, entityStore);
    }

    @Bean
    public SchemaRegistry schemaRegistry() {
        return new SchemaRegistry();
    }

    @Bean
    public ModuleLoader moduleLoader(ScriptRuntime scriptRuntime) {
        return new ModuleLoader(Path.of(modsPath), scriptRuntime);
    }

    @Bean
    public EngineBootstrap engineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry) {
        return new EngineBootstrap(moduleLoader, schemaRegistry, Path.of(modsPath));
    }
}
