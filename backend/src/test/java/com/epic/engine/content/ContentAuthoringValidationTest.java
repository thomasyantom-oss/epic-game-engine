package com.epic.engine.content;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAuthoringValidationTest {

    private static final Path MOD = Path.of("../mods/base-rules");
    private static final Set<String> PRIMARY_STATS = Set.of("力量", "敏捷", "智力", "体质", "意志");
    private static final Set<String> DERIVED_STATS = Set.of("物理强度", "法术强度", "精神强度");
    private static final Set<String> DAMAGE_TYPES = Set.of("物理", "法术", "精神");
    private static final Set<String> DELIVERIES = Set.of("普攻", "技能");
    private static final Set<String> RESISTANCE_KEYS = Set.of("物理", "法术", "精神");
    private static final Set<String> SKILL_CATEGORIES = Set.of("action", "skill");
    private static final Set<String> TARGETING_MODES = Set.of("pattern", "group", "self");
    private static final Set<String> TARGETING_FIELDS = Set.of("enemy", "ally", "self", "any");
    private static final Set<String> SKILL_TIERS = Set.of("smooth", "standard", "chunky");
    private static final Set<String> EFFECTS_REQUIRING_DAMAGE = Set.of("damage_only", "damage_with_debuff");
    private static final Set<String> EFFECTS_REQUIRING_BUFF = Set.of("buff_only");
    private static final Set<String> EFFECTS_REQUIRING_DEBUFF = Set.of("damage_with_debuff", "debuff_only");
    private static final Set<String> ALLOWED_SCALING_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(PRIMARY_STATS);
        keys.addAll(DERIVED_STATS);
        ALLOWED_SCALING_KEYS = Collections.unmodifiableSet(keys);
    }

    @Test
    void skillDefinitions_followCurrentAuthoringContract() throws Exception {
        Set<String> registeredEffects = registeredEffects();
        Set<String> buffIds = buffIds();
        List<String> errors = new ArrayList<>();

        try (Stream<Path> files = Files.list(MOD.resolve("skills"))) {
            for (Path skillFile : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                Map<String, Object> skill = yaml(skillFile);
                String id = string(skill.get("id"));
                String stem = stripYamlSuffix(skillFile.getFileName().toString());

                require(errors, id != null && id.equals(stem), skillFile, "id must match filename");
                requireKeys(errors, skillFile, skill, "id", "name", "category", "silenceable", "targeting");
                require(errors, nonBlank(skill.get("name")), skillFile, "name must be non-empty text");
                require(errors, nonBlank(skill.get("description")), skillFile, "description must be non-empty text");
                require(errors, SKILL_CATEGORIES.contains(string(skill.get("category"))), skillFile,
                        "category must be one of " + SKILL_CATEGORIES + ": " + string(skill.get("category")));
                require(errors, skill.get("silenceable") instanceof Boolean, skillFile, "silenceable must be boolean");
                validateTargeting(errors, skillFile, map(skill.get("targeting")));

                String effect = string(skill.get("effect"));
                Path bespokeJs = MOD.resolve("skills").resolve(stem + ".js");
                if (effect != null) {
                    require(errors, registeredEffects.contains(effect), skillFile, "effect is not registered: " + effect);
                    require(errors, nonEmptyList(skill.get("animation")), skillFile,
                            "data-driven skill must declare non-empty runtime animation");
                    if (EFFECTS_REQUIRING_DAMAGE.contains(effect)) {
                        require(errors, map(skill.get("damage")) != null, skillFile, effect + " requires damage block");
                    }
                    if (EFFECTS_REQUIRING_BUFF.contains(effect)) {
                        require(errors, map(skill.get("buff")) != null, skillFile, effect + " requires buff block");
                    }
                    if (EFFECTS_REQUIRING_DEBUFF.contains(effect)) {
                        require(errors, map(skill.get("debuff")) != null, skillFile, effect + " requires debuff block");
                    }
                } else if (!"flee".equals(stem)) {
                    require(errors, Files.exists(bespokeJs), skillFile, "skill without effect must have same-name bespoke JS");
                }

                String tier = string(skill.get("tier"));
                require(errors, tier == null || SKILL_TIERS.contains(tier), skillFile,
                        "tier must be one of " + SKILL_TIERS + ": " + tier);
                validateLevelScaling(errors, skillFile, map(skill.get("level_scaling")));
                validateScaling(errors, skillFile, map(skill.get("damage")), "damage.scaling");
                validateDamageMetadata(errors, skillFile, map(skill.get("damage")));
                String delivery = string(skill.get("delivery"));
                require(errors, delivery == null || DELIVERIES.contains(delivery), skillFile,
                        "delivery must be one of " + DELIVERIES + ": " + delivery);
                validateBuffSpec(errors, skillFile, map(skill.get("buff")), "buff", buffIds);
                validateBuffSpec(errors, skillFile, map(skill.get("debuff")), "debuff", buffIds);
            }
        }

        assertThat(errors).isEmpty();
    }

    @Test
    void itemDefinitions_followCurrentEquipmentContract() throws Exception {
        Map<String, Object> root = yaml(MOD.resolve("entities/items.yaml"));
        List<Map<String, Object>> items = list(root.get("items"));
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String id = string(item.get("id"));
            Map<String, Object> meta = map(item.get("meta"));
            Map<String, Object> stats = map(item.get("stats"));
            require(errors, id != null, MOD.resolve("entities/items.yaml"), "item id is required");
            require(errors, meta != null, MOD.resolve("entities/items.yaml"), id + ": meta is required");
            require(errors, stats != null, MOD.resolve("entities/items.yaml"), id + ": stats is required");
            if (meta == null || stats == null) continue;

            String type = string(meta.get("type"));
            if ("weapon".equals(type)) {
                String weaponAttr = string(stats.get("weaponAttr"));
                require(errors, PRIMARY_STATS.contains(weaponAttr), MOD.resolve("entities/items.yaml"),
                        id + ": weaponAttr must be one of " + PRIMARY_STATS);
                require(errors, stats.get("base") instanceof Number, MOD.resolve("entities/items.yaml"),
                        id + ": weapon base must be numeric");
            } else {
                for (Map.Entry<String, Object> entry : stats.entrySet()) {
                    String key = entry.getKey();
                    require(errors, key.contains(".") && key.indexOf('.') > 0 && key.indexOf('.') < key.length() - 1,
                            MOD.resolve("entities/items.yaml"), id + ": non-weapon stat must be Component.field: " + key);
                    require(errors, entry.getValue() instanceof Number, MOD.resolve("entities/items.yaml"),
                            id + ": stat value must be numeric: " + key);
                    validateEquipmentStatKey(errors, MOD.resolve("entities/items.yaml"), id, key);
                }
            }
        }

        assertThat(errors).isEmpty();
    }

    private static void validateDamageMetadata(List<String> errors, Path file, Map<String, Object> damage) {
        if (damage == null) return;
        String type = string(damage.get("type"));
        require(errors, type == null || DAMAGE_TYPES.contains(type), file,
                "damage.type must be one of " + DAMAGE_TYPES + ": " + type);
        Object add = damage.get("add");
        require(errors, add == null || add instanceof Number, file, "damage.add must be numeric when present");
    }

    private static void validateTargeting(List<String> errors, Path file, Map<String, Object> targeting) {
        require(errors, targeting != null, file, "targeting must be a map");
        if (targeting == null) return;
        require(errors, targeting.get("steps") instanceof List<?>, file, "targeting.steps must be a list");
        String mode = string(targeting.get("mode"));
        require(errors, mode == null || TARGETING_MODES.contains(mode), file,
                "targeting.mode must be one of " + TARGETING_MODES + ": " + mode);
        String field = string(targeting.get("field"));
        require(errors, field == null || TARGETING_FIELDS.contains(field), file,
                "targeting.field must be one of " + TARGETING_FIELDS + ": " + field);
        Object pattern = targeting.get("pattern");
        require(errors, pattern == null || pattern instanceof List<?>, file, "targeting.pattern must be a list when present");
        Object allowEmpty = targeting.get("allow_empty");
        require(errors, allowEmpty == null || allowEmpty instanceof Boolean, file, "targeting.allow_empty must be boolean");
    }

    private static void validateLevelScaling(List<String> errors, Path file, Map<String, Object> scaling) {
        if (scaling == null) return;
        for (Map.Entry<String, Object> entry : scaling.entrySet()) {
            require(errors, entry.getKey() != null && entry.getKey().contains(".") && !entry.getKey().startsWith(".")
                            && !entry.getKey().endsWith("."),
                    file, "level_scaling key must be a dotted path: " + entry.getKey());
            require(errors, entry.getValue() instanceof Number, file,
                    "level_scaling value must be numeric: " + entry.getKey());
        }
    }

    private static void validateEquipmentStatKey(List<String> errors, Path file, String itemId, String key) {
        int dotIdx = key.indexOf('.');
        if (dotIdx < 0) return;
        String component = key.substring(0, dotIdx);
        String field = key.substring(dotIdx + 1);
        if ("Resistances".equals(component)) {
            require(errors, RESISTANCE_KEYS.contains(field), file,
                    itemId + ": Resistances key must be one of " + RESISTANCE_KEYS + ": " + field);
        }
    }

    private static void validateBuffSpec(List<String> errors, Path file, Map<String, Object> spec,
                                         String field, Set<String> buffIds) {
        if (spec == null) return;
        String id = string(spec.get("id"));
        require(errors, buffIds.contains(id), file, field + ".id has no matching buff file: " + id);
        Map<String, Object> scaling = map(spec.get("scaling"));
        if (scaling == null) return;
        for (Map.Entry<String, Object> entry : scaling.entrySet()) {
            validateScalingKeys(errors, file, map(entry.getValue()), field + ".scaling." + entry.getKey());
        }
    }

    private static void validateScaling(List<String> errors, Path file, Map<String, Object> damage, String field) {
        if (damage == null) return;
        validateScalingKeys(errors, file, map(damage.get("scaling")), field);
    }

    private static void validateScalingKeys(List<String> errors, Path file, Map<String, Object> scaling, String field) {
        if (scaling == null) return;
        for (String key : scaling.keySet()) {
            require(errors, ALLOWED_SCALING_KEYS.contains(key), file,
                    field + " key must be a known PrimaryStats or DerivedStats field: " + key);
        }
    }

    private static Set<String> registeredEffects() throws IOException {
        String script = Files.readString(MOD.resolve("handlers/skill/01_effects.js"));
        Matcher matcher = Pattern.compile("Skill\\.registerEffect\\(\"([^\"]+)\"").matcher(script);
        Set<String> effects = new LinkedHashSet<>();
        while (matcher.find()) effects.add(matcher.group(1));
        return effects;
    }

    private static Set<String> buffIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(MOD.resolve("buffs"))) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.endsWith(".js")) ids.add(name.substring(0, name.length() - 3));
                if (name.endsWith(".yaml")) ids.add(name.substring(0, name.length() - 5));
            });
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(is);
            return loaded instanceof Map<?, ?> ? (Map<String, Object>) loaded : Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }

    private static boolean nonEmptyList(Object value) {
        return value instanceof List<?> list && !list.isEmpty();
    }

    private static boolean nonBlank(Object value) {
        return value instanceof String s && !s.isBlank();
    }

    private static String string(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static String stripYamlSuffix(String name) {
        return name.endsWith(".yaml") ? name.substring(0, name.length() - 5) : name;
    }

    private static void requireKeys(List<String> errors, Path file, Map<String, Object> map, String... keys) {
        for (String key : keys) {
            require(errors, map.containsKey(key), file, "missing required key: " + key);
        }
    }

    private static void require(List<String> errors, boolean ok, Path file, String message) {
        if (!ok) errors.add(file + ": " + message);
    }
}
