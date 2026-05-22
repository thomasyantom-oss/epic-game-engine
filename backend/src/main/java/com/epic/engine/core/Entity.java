package com.epic.engine.core;

import java.util.*;

public class Entity {

    private final String id;
    private final Map<String, Component> components = new LinkedHashMap<>();
    private final Set<String> tags = new LinkedHashSet<>();

    public Entity(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public void addComponent(Component component) {
        components.put(component.getType(), component);
    }

    public void removeComponent(String type) {
        components.remove(type);
    }

    public Component getComponent(String type) {
        return components.get(type);
    }

    public boolean hasComponent(String type) {
        return components.containsKey(type);
    }

    public Collection<Component> getAllComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    public void addTag(String tag) { tags.add(tag); }

    public void removeTag(String tag) { tags.remove(tag); }

    public boolean hasTag(String tag) { return tags.contains(tag); }

    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
}
