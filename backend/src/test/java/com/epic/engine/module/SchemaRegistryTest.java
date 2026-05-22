package com.epic.engine.module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaRegistryTest {

    SchemaRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SchemaRegistry();
        registry.loadFromModPath(Path.of("src/test/resources/test-mods/test-mod"));
    }

    @Test
    void loadsMainSchema() {
        Schema schema = registry.get("creature");

        assertThat(schema).isNotNull();
        assertThat(schema.id()).isEqualTo("creature");
        assertThat(schema.type()).isEqualTo("main");
        assertThat(schema.fields()).hasSize(4);
    }

    @Test
    void loadsSubSchema_withCompatibleWith() {
        Schema schema = registry.get("mutation");

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("sub");
        assertThat(schema.compatibleWith()).containsExactly("creature");
    }

    @Test
    void isCompatible_checksCorrectly() {
        assertThat(registry.isCompatible("mutation", "creature")).isTrue();
        assertThat(registry.isCompatible("mutation", "equipment")).isFalse();
    }

    @Test
    void unknownSchema_returnsNull() {
        assertThat(registry.get("nonexistent")).isNull();
    }
}
