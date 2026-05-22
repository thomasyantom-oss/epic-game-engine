package com.epic.engine.core;

import java.util.HashMap;
import java.util.Map;

public class Component {

    private final String type;
    private final Map<String, Object> data = new HashMap<>();

    public Component(String type) {
        this.type = type;
    }

    public String getType() { return type; }

    public void set(String key, Object value) { data.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) data.get(key); }

    public int getInt(String key) { return ((Number) data.get(key)).intValue(); }

    public double getDouble(String key) { return ((Number) data.get(key)).doubleValue(); }

    public String getString(String key) { return (String) data.get(key); }

    public boolean has(String key) { return data.containsKey(key); }

    public void remove(String key) { data.remove(key); }

    public Map<String, Object> getAll() { return Map.copyOf(data); }

    public Component copy() {
        Component copy = new Component(type);
        copy.data.putAll(this.data);
        return copy;
    }
}
