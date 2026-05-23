package com.epic.engine.module;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SchemaRegistry {

    private final Map<String, Schema> schemas = new LinkedHashMap<>();

    public void loadFromModPath(Path modPath) throws IOException {
        Path schemasDir = modPath.resolve("schemas");
        if (!Files.isDirectory(schemasDir)) return;

        Yaml yaml = new Yaml();
        try (Stream<Path> files = Files.walk(schemasDir)) {
            files.filter(p -> p.toString().endsWith(".schema.yaml"))
                 .forEach(schemaFile -> {
                     try (InputStream is = Files.newInputStream(schemaFile)) {
                         @SuppressWarnings("unchecked")
                         Map<String, Object> data = yaml.load(is);
                         Schema schema = Schema.fromYaml(data);
                         schemas.put(schema.id(), schema);
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to load schema: " + schemaFile, e);
                     }
                 });
        }
    }

    public Schema get(String id) {
        return schemas.get(id);
    }

    public boolean isCompatible(String subSchemaId, String mainSchemaId) {
        Schema sub = schemas.get(subSchemaId);
        if (sub == null) return false;
        return sub.compatibleWith().contains(mainSchemaId);
    }

    public List<Schema> getByCategory(String category) {
        return schemas.values().stream()
                .filter(s -> category.equals(s.category()))
                .toList();
    }

    public Map<String, Schema> getAll() {
        return Map.copyOf(schemas);
    }
}
