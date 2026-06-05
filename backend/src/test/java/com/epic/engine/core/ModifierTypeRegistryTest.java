package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierTypeRegistryTest {

    @Test
    void register_andGet_byId() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        reg.register(new ModifierType("equipment", "装备", "additive", 50));
        ModifierType t = reg.get("equipment");
        assertThat(t).isNotNull();
        assertThat(t.stackRule()).isEqualTo("additive");
    }

    @Test
    void loadFromList_populatesRegistry() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        reg.loadFromList(List.of(
            Map.of("id", "race", "label", "种族", "stack_rule", "exclusive", "base_priority", 200),
            Map.of("id", "equipment", "label", "装备", "stack_rule", "additive", "base_priority", 50)
        ));
        assertThat(reg.get("race").basePriority()).isEqualTo(200);
        assertThat(reg.get("equipment").stackRule()).isEqualTo("additive");
    }

    @Test
    void getStackRule_unknownType_returnsAdditive() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        assertThat(reg.getStackRule("unknown")).isEqualTo("additive");
    }

    @Test
    void baseRules_registersSpecModifierAtPriority220() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        reg.loadFromModPath(Path.of("../mods/base-rules"));
        ModifierType spec = reg.get("spec");
        assertThat(spec).isNotNull();
        assertThat(spec.stackRule()).isEqualTo("exclusive");
        assertThat(spec.basePriority()).isEqualTo(220);
    }
}
