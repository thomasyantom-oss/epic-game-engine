package com.epic.engine.module;

import java.util.List;
import java.util.Map;

public record Schema(
        String id,
        String type,
        List<String> compatibleWith,
        List<SchemaField> fields
) {
    public record SchemaField(
            String name,
            String fieldType,
            boolean required,
            Object defaultValue
    ) {}

    @SuppressWarnings("unchecked")
    public static Schema fromYaml(Map<String, Object> data) {
        String id = (String) data.get("id");
        String type = (String) data.get("type");

        List<String> compatible = data.containsKey("compatible_with")
                ? (List<String>) data.get("compatible_with")
                : List.of();

        List<Map<String, Object>> fieldDefs = data.containsKey("fields")
                ? (List<Map<String, Object>>) data.get("fields")
                : List.of();

        List<SchemaField> fields = fieldDefs.stream().map(f -> new SchemaField(
                (String) f.get("name"),
                (String) f.get("type"),
                Boolean.TRUE.equals(f.get("required")),
                f.get("default")
        )).toList();

        return new Schema(id, type, compatible, fields);
    }
}
