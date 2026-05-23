package com.epic.engine.persistence;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PersistenceService {

    private final EntityDataRepository repository;
    private final EntityStore entityStore;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersistenceService(EntityDataRepository repository, EntityStore entityStore) {
        this.repository = repository;
        this.entityStore = entityStore;
    }

    public void save(Entity entity) {
        if (!entity.hasTag("persistent")) return;

        try {
            Map<String, Map<String, Object>> components = new LinkedHashMap<>();
            for (Component c : entity.getAllComponents()) {
                components.put(c.getType(), c.getAll());
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
