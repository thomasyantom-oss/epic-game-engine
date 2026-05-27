package com.epic.engine.persistence;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.ModifierChainService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PersistenceService {

    private final EntityDataRepository repository;
    private final EntityStore entityStore;
    private final ObjectMapper mapper = new ObjectMapper();

    private ModifierChainService modifierChainService;

    public void setModifierChainService(ModifierChainService modifierChainService) {
        this.modifierChainService = modifierChainService;
    }

    public PersistenceService(EntityDataRepository repository, EntityStore entityStore) {
        this.repository = repository;
        this.entityStore = entityStore;
    }

    public void save(Entity entity) {
        if (!entity.hasTag("persistent")) return;

        try {
            Map<String, Map<String, Object>> components = new LinkedHashMap<>();

            if (modifierChainService != null) {
                Map<String, Component> baseState = modifierChainService.getBaseState(entity.getId());
                if (baseState != null) {
                    // Save base values for base-state components
                    for (Map.Entry<String, Component> entry : baseState.entrySet()) {
                        components.put(entry.getKey(), entry.getValue().getAll());
                    }
                    // Save current values for non-base components (EquipmentSlots, Inventory, etc.)
                    for (Component c : entity.getAllComponents()) {
                        if (!baseState.containsKey(c.getType())) {
                            components.put(c.getType(), c.getAll());
                        }
                    }
                } else {
                    for (Component c : entity.getAllComponents()) {
                        components.put(c.getType(), c.getAll());
                    }
                }
            } else {
                for (Component c : entity.getAllComponents()) {
                    components.put(c.getType(), c.getAll());
                }
            }

            String componentsJson = mapper.writeValueAsString(components);
            String tagsJson = mapper.writeValueAsString(entity.getTags());
            repository.save(new EntityData(entity.getId(), componentsJson, tagsJson));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize entity: " + entity.getId(), e);
        }
    }

    public Entity load(String entityId) {
        return repository.findById(entityId).map(this::deserialize).orElse(null);
    }

    public List<Entity> findByTag(String tag) {
        return repository.findByTagContaining(tag).stream()
                .map(this::deserialize)
                .toList();
    }

    public void delete(String entityId) {
        repository.deleteById(entityId);
        entityStore.remove(entityId);
    }

    public void loadAllPersistent() {
        repository.findAll().forEach(data -> {
            Entity entity = deserialize(data);
            entityStore.add(entity);
        });
    }

    @SuppressWarnings("unchecked")
    private Entity deserialize(EntityData data) {
        try {
            Entity entity = new Entity(data.getEntityId());

            Map<String, Map<String, Object>> components = mapper.readValue(
                    data.getComponentsJson(), Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : components.entrySet()) {
                Component c = new Component(entry.getKey());
                entry.getValue().forEach(c::set);
                entity.addComponent(c);
            }

            List<String> tags = mapper.readValue(data.getTagsJson(), List.class);
            tags.forEach(entity::addTag);

            return entity;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize entity: " + data.getEntityId(), e);
        }
    }
}
