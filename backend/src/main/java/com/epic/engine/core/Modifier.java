package com.epic.engine.core;

import java.util.function.Consumer;

public record Modifier(
        String id,
        String typeId,
        String label,
        String source,
        int priority,
        Consumer<Entity> apply
) implements Comparable<Modifier> {

    public Modifier(String id, String source, int priority, Consumer<Entity> apply) {
        this(id, null, null, source, priority, apply);
    }

    @Override
    public int compareTo(Modifier other) {
        return Integer.compare(this.priority, other.priority);
    }
}
