package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagIndex {

    private final Map<String, Set<Entity>> tagToEntities = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> entityTags = new ConcurrentHashMap<>();

    public void register(Entity entity) {
        entityTags.put(entity.getId(), new HashSet<>(entity.getTags()));
        for (String tag : entity.getTags()) {
            tagToEntities.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(entity);
        }
    }

    public void unregister(Entity entity) {
        Set<String> oldTags = entityTags.remove(entity.getId());
        if (oldTags != null) {
            for (String tag : oldTags) {
                Set<Entity> set = tagToEntities.get(tag);
                if (set != null) {
                    set.remove(entity);
                    if (set.isEmpty()) tagToEntities.remove(tag);
                }
            }
        }
    }

    public void reindex(Entity entity) {
        unregister(entity);
        register(entity);
    }

    public Set<Entity> getByTag(String tag) {
        return Collections.unmodifiableSet(
                tagToEntities.getOrDefault(tag, Collections.emptySet()));
    }

    public void clear() {
        tagToEntities.clear();
        entityTags.clear();
    }
}
