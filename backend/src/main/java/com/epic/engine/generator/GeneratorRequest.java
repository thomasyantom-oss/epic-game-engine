package com.epic.engine.generator;

import java.util.List;

public record GeneratorRequest(
        String mainSchemaId,
        List<String> subSchemaIds,
        int level
) {}
