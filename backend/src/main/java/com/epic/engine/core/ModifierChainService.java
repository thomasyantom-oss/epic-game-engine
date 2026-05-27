package com.epic.engine.core;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModifierChainService {

    private final EventBus bus;
    private final EntityStore store;
    private final ModifierTypeRegistry typeRegistry;

    private final Map<String, ModifierChain> chains = new ConcurrentHashMap<>();
    private final Map<String, List<ModifierDiff>> contributions = new ConcurrentHashMap<>();

    public ModifierChainService(EventBus bus, EntityStore store, ModifierTypeRegistry typeRegistry) {
        this.bus = bus;
        this.store = store;
        this.typeRegistry = typeRegistry;
    }

    private ModifierChain getOrCreate(String entityId) {
        return chains.computeIfAbsent(entityId, k -> {
            Entity entity = store.get(k);
            if (entity == null) throw new IllegalArgumentException("Entity not found: " + k);
            return new ModifierChain(entity, false);
        });
    }

    public void setBase(String entityId) {
        Entity entity = store.get(entityId);
        if (entity == null) return;
        getOrCreate(entityId).setBase();
    }

    public void setBaseSelective(String entityId, List<String> componentTypes) {
        Entity entity = store.get(entityId);
        if (entity == null) return;
        getOrCreate(entityId).setBaseSelective(componentTypes);
    }

    public void addModifier(String entityId, Modifier modifier) {
        ModifierChain chain = getOrCreate(entityId);
        String stackRule = typeRegistry.getStackRule(modifier.typeId());
        if (ModifierType.EXCLUSIVE.equals(stackRule)) {
            chain.removeByTypeId(modifier.typeId());
        }
        chain.addModifier(modifier);
        recalculate(entityId);
    }

    public void removeModifier(String entityId, String modifierId) {
        ModifierChain chain = chains.get(entityId);
        if (chain == null) return;
        chain.removeById(modifierId);
        recalculate(entityId);
    }

    public void removeBySource(String entityId, String source) {
        ModifierChain chain = chains.get(entityId);
        if (chain == null) return;
        chain.removeBySource(source);
        recalculate(entityId);
    }

    public void recalculate(String entityId) {
        Entity entity = store.get(entityId);
        ModifierChain chain = chains.get(entityId);
        if (entity == null || chain == null) return;

        Map<String, Object> scratch = new HashMap<>();
        GameEvent before = new GameEvent("entity.before_recalculate");
        before.set("entity", entity);
        before.set("scratch", scratch);
        bus.fire("entity.before_recalculate", before);

        List<ModifierDiff> diffs = chain.recalculateWithTracking();
        contributions.put(entityId, diffs);

        GameEvent after = new GameEvent("entity.after_recalculate");
        after.set("entity", entity);
        after.set("scratch", scratch);
        bus.fire("entity.after_recalculate", after);
    }

    public List<ModifierDiff> getContributions(String entityId) {
        return contributions.getOrDefault(entityId, List.of());
    }

    public Map<String, Component> getBaseState(String entityId) {
        ModifierChain chain = chains.get(entityId);
        return chain != null ? chain.getBaseState() : null;
    }

    public void clearChain(String entityId) {
        chains.remove(entityId);
        contributions.remove(entityId);
    }
}
