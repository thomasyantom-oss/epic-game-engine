package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EntityStore {

    private final Map<String, Entity> entities = new ConcurrentHashMap<>();
    private final TagIndex tagIndex = new TagIndex();

    public void add(Entity entity) {
        entities.put(entity.getId(), entity);
        tagIndex.register(entity);
    }

    public Entity get(String id) {
        return entities.get(id);
    }

    public void remove(String id) {
        Entity entity = entities.remove(id);
        if (entity != null) {
            tagIndex.unregister(entity);
        }
    }

    public Set<Entity> getByTag(String tag) {
        return tagIndex.getByTag(tag);
    }

    public List<Entity> getByTagAsList(String tag) {
        return new ArrayList<>(getByTag(tag));
    }

    public Set<Entity> getByComponent(String componentType) {
        return entities.values().stream()
                .filter(e -> e.hasComponent(componentType))
                .collect(Collectors.toSet());
    }

    public Collection<Entity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public void reindexTags(Entity entity) {
        tagIndex.reindex(entity);
    }

    public void clear() {
        entities.clear();
    }
}
