package com.epic.engine.mod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ModRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSceneFromMod() throws IOException {
        Path modDir = tempDir.resolve("base");
        Path scenesDir = modDir.resolve("scenes");
        Files.createDirectories(scenesDir);
        Files.writeString(modDir.resolve("mod.yaml"), """
                id: base
                name: "Base"
                version: "1.0.0"
                description: "Base mod"
                load-order: 0
                dependencies: []
                """);
        Files.writeString(scenesDir.resolve("town.yaml"), """
                id: town
                description:
                  - text: "你站在"
                    color: "#e0e0e0"
                  - text: "村庄广场"
                    color: "#4ecdc4"
                actions:
                  - id: go-tavern
                    label: "走向酒馆"
                    type: move
                    target: tavern
                """);

        ModLoader loader = new ModLoader(tempDir);
        ModRegistry registry = new ModRegistry(loader);
        registry.initialize();

        Optional<Map<String, Object>> scene = registry.getScene("town");
        assertThat(scene).isPresent();
        assertThat(scene.get().get("id")).isEqualTo("town");
    }

    @Test
    void laterModOverridesEarlierScene() throws IOException {
        Path baseMod = tempDir.resolve("base");
        Path overrideMod = tempDir.resolve("override");
        Files.createDirectories(baseMod.resolve("scenes"));
        Files.createDirectories(overrideMod.resolve("scenes"));

        Files.writeString(baseMod.resolve("mod.yaml"), """
                id: base
                name: "Base"
                version: "1.0.0"
                description: "Base"
                load-order: 0
                dependencies: []
                """);
        Files.writeString(overrideMod.resolve("mod.yaml"), """
                id: override
                name: "Override"
                version: "1.0.0"
                description: "Override mod"
                load-order: 10
                dependencies: [base]
                """);
        Files.writeString(baseMod.resolve("scenes/town.yaml"), """
                id: town
                description:
                  - text: "原版村庄"
                    color: "#e0e0e0"
                actions: []
                """);
        Files.writeString(overrideMod.resolve("scenes/town.yaml"), """
                id: town
                description:
                  - text: "Mod 覆盖的村庄"
                    color: "#ff0000"
                actions: []
                """);

        ModLoader loader = new ModLoader(tempDir);
        ModRegistry registry = new ModRegistry(loader);
        registry.initialize();

        Optional<Map<String, Object>> scene = registry.getScene("town");
        assertThat(scene).isPresent();
        @SuppressWarnings("unchecked")
        var desc = (java.util.List<Map<String, Object>>) scene.get().get("description");
        assertThat(desc.get(0).get("text")).isEqualTo("Mod 覆盖的村庄");
    }
}
