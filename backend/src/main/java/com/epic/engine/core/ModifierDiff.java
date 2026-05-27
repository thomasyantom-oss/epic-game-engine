package com.epic.engine.core;

import java.util.Map;

public record ModifierDiff(
        String modifierId,
        String typeId,
        String label,
        Map<String, Map<String, Long>> componentDeltas
) {}
