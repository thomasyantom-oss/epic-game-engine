package com.epic.engine.generator;

import com.epic.engine.core.Entity;
import com.epic.engine.module.SchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorServiceTest {

    GeneratorService generator;

    @BeforeEach
    void setUp() throws Exception {
        SchemaRegistry registry = new SchemaRegistry();
        registry.loadFromModPath(Path.of("src/test/resources/test-mods/test-mod"));
        generator = new GeneratorService(registry);
    }

    @Test
    void generate_createsEntityFromMainSchema() {
        GeneratorRequest request = new GeneratorRequest("creature", List.of(), 3);

        Entity entity = generator.generate(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).startsWith("creature_");
        assertThat(entity.hasComponent("creature")).isTrue();
        assertThat(entity.getComponent("creature").has("hp")).isTrue();
        assertThat(entity.getComponent("creature").has("maxHp")).isTrue();
        assertThat(entity.hasTag("creature")).isTrue();
    }

    @Test
    void generate_addsCompatibleSubComponents() {
        GeneratorRequest request = new GeneratorRequest("creature", List.of("mutation"), 2);

        Entity entity = generator.generate(request);

        assertThat(entity.hasComponent("mutation")).isTrue();
        assertThat(entity.getComponent("mutation").has("mutation_type")).isTrue();
        assertThat(entity.hasTag("mutation")).isTrue();
    }

    @Test
    void generate_skipsIncompatibleSub() {
        GeneratorRequest request = new GeneratorRequest("creature", List.of("nonexistent"), 1);

        Entity entity = generator.generate(request);

        // Only main component, incompatible/nonexistent sub was skipped
        assertThat(entity.getAllComponents()).hasSize(1);
    }
}
