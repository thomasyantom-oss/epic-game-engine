package com.epic.engine.core;

import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.ModuleDescriptor;
import com.epic.engine.module.SchemaRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class EngineBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EngineBootstrap.class);

    private final ModuleLoader moduleLoader;
    private final SchemaRegistry schemaRegistry;
    private final Path modsPath;

    public EngineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry, Path modsPath) {
        this.moduleLoader = moduleLoader;
        this.schemaRegistry = schemaRegistry;
        this.modsPath = modsPath;
    }

    @PostConstruct
    public void boot() throws Exception {
        List<ModuleDescriptor> modules = moduleLoader.discoverModules();
        log.info("发现 {} 个模块，开始加载…", modules.size());
        for (ModuleDescriptor mod : modules) {
            schemaRegistry.loadFromModPath(mod.path());
        }
        moduleLoader.loadAll();
        log.info("引擎启动完成，已加载 {} 个 schema", schemaRegistry.getAll().size());
    }
}
