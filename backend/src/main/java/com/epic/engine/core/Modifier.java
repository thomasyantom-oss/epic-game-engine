package com.epic.engine.core;

import java.util.function.Consumer;

public record Modifier(
        String id,
        String source,
        int priority,
        Consumer<Entity> apply
) implements Comparable<Modifier> {

    @Override
    public int compareTo(Modifier other) {
        return Integer.compare(this.priority, other.priority);
    }
}
