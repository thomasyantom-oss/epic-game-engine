package com.epic.engine.generator;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.module.Schema;
import com.epic.engine.module.SchemaRegistry;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Service
public class GeneratorService {

    private final SchemaRegistry schemaRegistry;
    private final Random random = new Random();

    public GeneratorService(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public Entity generate(GeneratorRequest request) {
        Schema mainSchema = schemaRegistry.get(request.mainSchemaId());
        if (mainSchema == null) {
            throw new IllegalArgumentException("Unknown schema: " + request.mainSchemaId());
        }

        String id = request.mainSchemaId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        Entity entity = new Entity(id);

        // Create main component from schema fields
        Component main = new Component(mainSchema.id());
        for (Schema.SchemaField field : mainSchema.fields()) {
            Object value = generateFieldValue(field, request.level());
            main.set(field.name(), value);
        }
        entity.addComponent(main);
        entity.addTag(mainSchema.id());

        // Add sub components
        for (String subId : request.subSchemaIds()) {
            Schema subSchema = schemaRegistry.get(subId);
            if (subSchema == null) continue;
            if (!schemaRegistry.isCompatible(subId, request.mainSchemaId())) continue;

            Component sub = new Component(subSchema.id());
            for (Schema.SchemaField field : subSchema.fields()) {
                Object value = generateFieldValue(field, request.level());
                sub.set(field.name(), value);
            }
            entity.addComponent(sub);
            entity.addTag(subId);
        }

        return entity;
    }

    private Object generateFieldValue(Schema.SchemaField field, int level) {
        if (field.defaultValue() != null) return field.defaultValue();

        return switch (field.fieldType()) {
            case "int" -> 10 * level + random.nextInt(5 * level + 1);
            case "double" -> 1.0 + (level * 0.5) + random.nextDouble();
            case "string" -> field.name() + "_" + level;
            default -> null;
        };
    }
}
