package com.epic.engine.core;

import java.util.function.Consumer;

public record HandlerRegistration(
        String eventType,
        int priority,
        Consumer<GameEvent> handler
) implements Comparable<HandlerRegistration> {

    @Override
    public int compareTo(HandlerRegistration other) {
        return Integer.compare(this.priority, other.priority);
    }
}
