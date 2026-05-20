package com.epic.engine.mod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsModDescriptorFromDirectory() throws IOException {
        Path modDir = tempDir.resolve("test-mod");
        Files.createDirectories(modDir);
        Files.writeString(modDir.resolve("mod.yaml"), """
                id: test-mod
                name: "Test Mod"
                version: "1.0.0"
                description: "A test mod"
                load-order: 5
                dependencies: []
                """);

        ModLoader loader = new ModLoader(tempDir);
        List<ModDescriptor> mods = loader.discoverMods();

        assertThat(mods).hasSize(1);
        assertThat(mods.get(0).id()).isEqualTo("test-mod");
        assertThat(mods.get(0).name()).isEqualTo("Test Mod");
        assertThat(mods.get(0).loadOrder()).isEqualTo(5);
    }

    @Test
    void sortsModsByLoadOrder() throws IOException {
        Path modA = tempDir.resolve("mod-a");
        Path modB = tempDir.resolve("mod-b");
        Files.createDirectories(modA);
        Files.createDirectories(modB);
        Files.writeString(modA.resolve("mod.yaml"), """
                id: mod-a
                name: "Mod A"
                version: "1.0.0"
                description: "First"
                load-order: 10
                dependencies: []
                """);
        Files.writeString(modB.resolve("mod.yaml"), """
                id: mod-b
                name: "Mod B"
                version: "1.0.0"
                description: "Second"
                load-order: 1
                dependencies: []
                """);

        ModLoader loader = new ModLoader(tempDir);
        List<ModDescriptor> mods = loader.discoverMods();

        assertThat(mods).hasSize(2);
        assertThat(mods.get(0).id()).isEqualTo("mod-b");
        assertThat(mods.get(1).id()).isEqualTo("mod-a");
    }
}
