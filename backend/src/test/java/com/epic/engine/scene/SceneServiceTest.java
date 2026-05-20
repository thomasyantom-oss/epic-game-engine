package com.epic.engine.scene;

import com.epic.engine.mod.ModLoader;
import com.epic.engine.mod.ModRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SceneServiceTest {

    @TempDir
    Path tempDir;
    SceneService sceneService;

    @BeforeEach
    void setUp() throws IOException {
        Path modDir = tempDir.resolve("base");
        Path scenesDir = modDir.resolve("scenes");
        Files.createDirectories(scenesDir);
        Files.writeString(modDir.resolve("mod.yaml"), """
                id: base
                name: "Base"
                version: "1.0.0"
                description: "Base"
                load-order: 0
                dependencies: []
                """);
        Files.writeString(scenesDir.resolve("start.yaml"), """
                id: start
                description:
                  - text: "起点"
                    color: "#ffffff"
                actions:
                  - id: go-next
                    label: "前进"
                    type: move
                    target: next
                """);
        Files.writeString(scenesDir.resolve("next.yaml"), """
                id: next
                description:
                  - text: "下一个场景"
                    color: "#00ff00"
                actions: []
                """);

        ModLoader loader = new ModLoader(tempDir);
        ModRegistry registry = new ModRegistry(loader);
        registry.initialize();
        sceneService = new SceneService(registry);
    }

    @Test
    void returnsSceneWithTextSegmentsAndActions() {
        Scene scene = sceneService.getScene("start");

        assertThat(scene).isNotNull();
        assertThat(scene.id()).isEqualTo("start");
        assertThat(scene.description()).hasSize(1);
        assertThat(scene.description().get(0).text()).isEqualTo("起点");
        assertThat(scene.description().get(0).color()).isEqualTo("#ffffff");
        assertThat(scene.actions()).hasSize(1);
        assertThat(scene.actions().get(0).label()).isEqualTo("前进");
    }

    @Test
    void returnsNullForUnknownScene() {
        Scene scene = sceneService.getScene("nonexistent");
        assertThat(scene).isNull();
    }
}
