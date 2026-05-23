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
        assertThat(schema.baseComponents()).containsKeys("Health", "Mana", "CombatStats", "Position");
    }

    @Test
    void classSchemas_loaded() {
        List<Schema> classes = schemaRegistry.getByCategory("class");
        assertThat(classes).hasSize(2);
        assertThat(classes.stream().map(Schema::id).toList())
                .containsExactlyInAnyOrder("warrior", "mage");
    }

    @Test
    void warriorSchema_hasModifiers() {
        Schema warrior = schemaRegistry.get("warrior");
        assertThat(warrior.label()).isEqualTo("战士");
        assertThat(warrior.modifiers()).isNotEmpty();
        assertThat(warrior.modifiers().get(0).field()).isEqualTo("Health.maxHp");
    }
}
