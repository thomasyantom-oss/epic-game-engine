package com.epic.engine.core;

import java.util.*;

public class ModifierChain {

    private final Entity entity;
    private final List<Modifier> modifiers = new ArrayList<>();
    private final Map<String, Component> baseState = new LinkedHashMap<>();
    private final Set<String> baseTags = new LinkedHashSet<>();

    public ModifierChain(Entity entity) {
        this.entity = entity;
        snapshotBase();
    }

    private void snapshotBase() {
        baseState.clear();
        for (Component c : entity.getAllComponents()) {
            baseState.put(c.getType(), c.copy());
        }
        baseTags.clear();
        baseTags.addAll(entity.getTags());
    }

    public void setBase() {
        snapshotBase();
    }

    public void addModifier(Modifier modifier) {
        modifiers.add(modifier);
        modifiers.sort(Modifier::compareTo);
    }

    public void removeBySource(String source) {
        modifiers.removeIf(m -> m.source().equals(source));
    }

    public void removeById(String id) {
        modifiers.removeIf(m -> m.id().equals(id));
    }

    public void recalculate() {
        // Restore base state: remove all current components
        List<String> currentTypes = new ArrayList<>();
        for (Component c : entity.getAllComponents()) {
            currentTypes.add(c.getType());
        }
        for (String type : currentTypes) {
            entity.removeComponent(type);
        }
        // Re-add base components (deep copies)
        for (Map.Entry<String, Component> entry : baseState.entrySet()) {
            entity.addComponent(entry.getValue().copy());
        }
        // Restore base tags
        for (String tag : new ArrayList<>(entity.getTags())) {
            entity.removeTag(tag);
        }
        for (String tag : baseTags) {
            entity.addTag(tag);
        }

        // Apply modifiers in priority order (already sorted)
        for (Modifier mod : modifiers) {
            mod.apply().accept(entity);
        }
    }

    public List<Modifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
