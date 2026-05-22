package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    private final Map<String, List<HandlerRegistration>> handlers = new ConcurrentHashMap<>();

    public void on(String eventType, int priority, Consumer<GameEvent> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new HandlerRegistration(eventType, priority, handler));
        handlers.get(eventType).sort(HandlerRegistration::compareTo);
    }

    public void fire(String eventType, GameEvent event) {
        List<HandlerRegistration> registrations = handlers.get(eventType);
        if (registrations == null) return;

        for (HandlerRegistration reg : registrations) {
            if (event.isCancelled()) break;
            reg.handler().accept(event);
        }
    }

    public void clear() {
        handlers.clear();
    }

    public void removeHandlersFor(String eventType) {
        handlers.remove(eventType);
    }
}
