package com.epic.engine.module;

import java.util.List;
import java.util.Map;

public record Schema(
        String id,
        String type,
        List<String> compatibleWith,
        List<SchemaField> fields,
        List<String> requiredSubs,
        String category,
        String label,
        String description,
        Map<String, Map<String, Object>> baseComponents,
        List<SchemaModifier> modifiers
) {
    public record SchemaField(
            String name,
            String fieldType,
            boolean required,
            Object defaultValue
    ) {}

    public record SchemaModifier(
            String field,
            String value
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

        List<String> requiredSubs = data.containsKey("required_subs")
                ? (List<String>) data.get("required_subs")
                : List.of();

        String category = (String) data.get("category");
        String label = (String) data.get("label");
        String description = (String) data.get("description");

        Map<String, Map<String, Object>> baseComponents = data.containsKey("base_components")
                ? (Map<String, Map<String, Object>>) data.get("base_components")
                : Map.of();

        List<Map<String, Object>> modDefs = data.containsKey("modifiers")
                ? (List<Map<String, Object>>) data.get("modifiers")
                : List.of();
        List<SchemaModifier> modifiers = modDefs.stream().map(m -> new SchemaModifier(
                (String) m.get("field"),
                (String) m.get("value")
        )).toList();

        return new Schema(id, type, compatible, fields, requiredSubs, category, label, description, baseComponents, modifiers);
    }
}
