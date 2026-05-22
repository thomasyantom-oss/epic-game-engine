package com.epic.engine.core;

import java.util.HashMap;
import java.util.Map;

public class GameEvent {

    private final String type;
    private final Map<String, Object> data = new HashMap<>();
    private boolean cancelled = false;

    public GameEvent(String type) {
        this.type = type;
    }

    public String getType() { return type; }

    public void set(String key, Object value) { data.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) data.get(key); }

    public boolean has(String key) { return data.containsKey(key); }

    public void cancel() { cancelled = true; }

    public boolean isCancelled() { return cancelled; }
}
