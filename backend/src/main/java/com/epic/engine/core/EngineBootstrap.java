package com.epic.engine.core;

import com.epic.engine.buff.BuffService;
import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.ModuleDescriptor;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.persistence.PersistenceService;
import com.epic.engine.script.HotReloader;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.session.SessionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class EngineBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EngineBootstrap.class);

    private final ModuleLoader moduleLoader;
    private final SchemaRegistry schemaRegistry;
    private final EventBus eventBus;
    private final EntityStore entityStore;
    private final Path modsPath;
    private final ScriptRuntime scriptRuntime;
    private final PersistenceService persistenceService;
    private final SessionService sessionService;
    private final BuffService buffService;
    private HotReloader hotReloader;

    public EngineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry,
                           EventBus eventBus, EntityStore entityStore, Path modsPath,
                           ScriptRuntime scriptRuntime, PersistenceService persistenceService,
                           SessionService sessionService, BuffService buffService) {
        this.moduleLoader = moduleLoader;
        this.schemaRegistry = schemaRegistry;
        this.eventBus = eventBus;
        this.entityStore = entityStore;
        this.modsPath = modsPath;
        this.scriptRuntime = scriptRuntime;
        this.persistenceService = persistenceService;
        this.sessionService = sessionService;
        this.buffService = buffService;
    }

    @PostConstruct
    public void boot() throws Exception {
        List<ModuleDescriptor> modules = moduleLoader.discoverModules();
        log.info("发现 {} 个模块，开始加载…", modules.size());
        for (ModuleDescriptor mod : modules) {
            schemaRegistry.loadFromModPath(mod.path());
        }

        // Bind services to JS runtime before loading handlers
        scriptRuntime.bindService("persistence", persistenceService);
        scriptRuntime.bindService("schemas", schemaRegistry);
        scriptRuntime.bindService("sessions", sessionService);
        scriptRuntime.bindService("buffs", buffService);

        moduleLoader.loadAll();
        log.info("引擎启动完成，已加载 {} 个 schema", schemaRegistry.getAll().size());

        // Load persistent entities from database (player characters etc.)
        persistenceService.loadAllPersistent();

        // Clean up stale combat tags from restarted sessions
        for (Entity entity : entityStore.all()) {
            var staleTags = entity.getTags().stream()
                    .filter(t -> t.startsWith("combat:"))
                    .toList();
            if (!staleTags.isEmpty()) {
                staleTags.forEach(entity::removeTag);
                entityStore.reindexTags(entity);
                persistenceService.save(entity);
            }
        }

        log.info("已从数据库恢复 {} 个持久实体", entityStore.all().size());

        eventBus.fire("world.init", new GameEvent("world.init"));
        log.info("世界初始化完成，实体数: {}", entityStore.all().size());

        // Start JS hot reloader
        hotReloader = new HotReloader(scriptRuntime, eventBus);
        hotReloader.startWatching(modsPath);
    }

    @PreDestroy
    public void shutdown() {
        if (hotReloader != null) {
            hotReloader.stop();
        }
    }
}
