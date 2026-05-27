package com.epic.engine.core;

public record ModifierType(
        String id,
        String label,
        String stackRule,
        int basePriority
) {
    public static final String EXCLUSIVE = "exclusive";
    public static final String ADDITIVE = "additive";
    public static final String INDEPENDENT = "independent";
}
