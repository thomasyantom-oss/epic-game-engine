package com.epic.engine.module;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CharacterSchemaTest {

    @Autowired SchemaRegistry schemaRegistry;

    @Test
    void characterSchema_loaded() {
        Schema schema = schemaRegistry.get("character");
        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("main");
        assertThat(schema.requiredSubs()).containsExactly("class");
        assertThat(schema.baseComponents()).containsKeys("Health", "Mana", "CombatStats", "PrimaryStats", "DerivedStats", "Position");
    }

    @Test
    void classSchemas_loaded() {
        List<Schema> classes = schemaRegistry.getByCategory("class");
        assertThat(classes).hasSize(5);
        assertThat(classes.stream().map(Schema::id).toList())
                .containsExactlyInAnyOrder("warrior", "mage", "rogue", "druid", "guardian");
    }

    @Test
    void warriorSchema_hasModifiers() {
        Schema warrior = schemaRegistry.get("warrior");
        assertThat(warrior.label()).isEqualTo("战士");
        assertThat(warrior.modifiers()).isNotEmpty();
        assertThat(warrior.modifiers().get(0).field()).isEqualTo("PrimaryStats.力量");
    }

    @Test
    void schema_raw_exposesCustomFields() {
        Schema warrior = schemaRegistry.get("warrior");
        assertThat(warrior.raw().get("weapon_attr")).isEqualTo("力量");
        Object growth = warrior.raw().get("growth");
        assertThat(growth).isInstanceOf(java.util.Map.class);
        assertThat(((java.util.Map<?, ?>) growth).get("力量")).isEqualTo(3);
    }
}
