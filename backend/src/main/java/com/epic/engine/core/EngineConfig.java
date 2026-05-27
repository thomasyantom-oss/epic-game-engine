package com.epic.engine.core;

import com.epic.engine.buff.BuffService;
import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.persistence.PersistenceService;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.session.SessionService;
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
    public ScriptRuntime scriptRuntime(EventBus eventBus, EntityStore entityStore,
                                        ModifierChainService modifierChainService,
                                        ModifierTypeRegistry modifierTypeRegistry) {
        return new ScriptRuntime(eventBus, entityStore, modifierChainService, modifierTypeRegistry);
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
    public BuffService buffService(EventBus eventBus, EntityStore entityStore) {
        return new BuffService(eventBus, entityStore);
    }

    @Bean
    public EngineBootstrap engineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry,
                                           EventBus eventBus, EntityStore entityStore,
                                           ScriptRuntime scriptRuntime, PersistenceService persistenceService,
                                           SessionService sessionService, BuffService buffService,
                                           ModifierTypeRegistry modifierTypeRegistry,
                                           ModifierChainService modifierChainService) {
        persistenceService.setModifierChainService(modifierChainService);
        return new EngineBootstrap(moduleLoader, schemaRegistry, eventBus, entityStore, Path.of(modsPath),
                scriptRuntime, persistenceService, sessionService, buffService, modifierTypeRegistry);
    }
}
