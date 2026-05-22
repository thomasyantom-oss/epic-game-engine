package com.epic.engine.module;

import java.nio.file.Path;
import java.util.List;

public record ModuleDescriptor(
        String id,
        String name,
        String version,
        int loadOrder,
        List<String> dependencies,
        Path path
) {}
