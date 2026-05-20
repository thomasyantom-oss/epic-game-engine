package com.epic.engine.mod;

import java.nio.file.Path;
import java.util.List;

public record ModDescriptor(
        String id,
        String name,
        String version,
        String description,
        int loadOrder,
        List<String> dependencies,
        Path path
) {}
