package com.epic.engine.core;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public boolean getBoolean(String key) { return Boolean.TRUE.equals(data.get(key)); }

    public boolean has(String key) { return data.containsKey(key); }

    public void remove(String key) { data.remove(key); }

    public Map<String, Object> getAll() { return Collections.unmodifiableMap(data); }

    public Component copy() {
        Component copy = new Component(type);
        this.data.forEach((k, v) -> copy.data.put(k, deepCopyValue(v)));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Component component) return component.copy();
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(k, deepCopyValue(v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(deepCopyValue(item));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : set) copy.add(deepCopyValue(item));
            return copy;
        }
        return value;
    }
}
