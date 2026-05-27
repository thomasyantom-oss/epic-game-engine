package com.epic.engine.core;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModifierTypeRegistry {

    private final Map<String, ModifierType> types = new ConcurrentHashMap<>();

    public void register(ModifierType type) {
        types.put(type.id(), type);
    }

    @SuppressWarnings("unchecked")
    public void loadFromList(List<Map<String, Object>> list) {
        for (Map<String, Object> entry : list) {
            String id = (String) entry.get("id");
            String label = (String) entry.getOrDefault("label", id);
            String stackRule = (String) entry.getOrDefault("stack_rule", "additive");
            int basePriority = entry.containsKey("base_priority")
                    ? ((Number) entry.get("base_priority")).intValue() : 0;
            types.put(id, new ModifierType(id, label, stackRule, basePriority));
        }
    }

    @SuppressWarnings("unchecked")
    public void loadFromModPath(Path modPath) {
        Path yamlPath = modPath.resolve("modifier_types.yaml");
        if (!Files.exists(yamlPath)) return;
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data != null && data.containsKey("modifier_types")) {
                loadFromList((List<Map<String, Object>>) data.get("modifier_types"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load modifier_types.yaml: " + yamlPath, e);
        }
    }

    public ModifierType get(String id) {
        return types.get(id);
    }

    public String getStackRule(String typeId) {
        if (typeId == null) return ModifierType.ADDITIVE;
        ModifierType t = types.get(typeId);
        return t != null ? t.stackRule() : ModifierType.ADDITIVE;
    }

    public int getBasePriority(String typeId) {
        if (typeId == null) return 0;
        ModifierType t = types.get(typeId);
        return t != null ? t.basePriority() : 0;
    }
}
