# Engine Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundational vertical slice — mod loading, scene rendering with colored text, clickable actions, multi-panel UI, and player settings — proving the full architecture end-to-end.

**Architecture:** Java Spring Boot backend loads mods from filesystem, exposes REST APIs returning structured JSON with color-annotated text and action links. Vue frontend renders panels independently, refreshing based on backend directives. All game content comes from a "base mod" — the engine itself has zero hardcoded content.

**Tech Stack:** Java 21, Spring Boot 3, Maven, H2 (embedded), Vue 3 (Composition API), Vite, CSS variables

---

## File Structure

```
epic/
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/epic/engine/
│   │   ├── EpicApplication.java              — Spring Boot entry point
│   │   ├── config/
│   │   │   └── CorsConfig.java               — CORS for local dev
│   │   ├── mod/
│   │   │   ├── ModDescriptor.java            — Mod metadata record
│   │   │   ├── ModLoader.java                — Discovers and loads mods
│   │   │   └── ModRegistry.java              — Holds loaded mod data, provides queries
│   │   ├── scene/
│   │   │   ├── Scene.java                    — Scene data model
│   │   │   ├── SceneAction.java              — Clickable action in a scene
│   │   │   ├── TextSegment.java              — A piece of text with color
│   │   │   ├── SceneService.java             — Resolves current scene for player
│   │   │   └── SceneController.java          — REST endpoint /api/scene
│   │   ├── panel/
│   │   │   ├── PanelRefresh.java             — Enum of panel types
│   │   │   └── ActionResponse.java           — Response DTO: result + panels to refresh
│   │   ├── action/
│   │   │   ├── ActionHandler.java            — Interface for handling player actions
│   │   │   ├── MoveActionHandler.java        — Handles "move" actions
│   │   │   └── ActionController.java         — REST endpoint /api/action
│   │   └── save/
│   │       ├── PlayerState.java              — Player state entity
│   │       ├── PlayerStateRepository.java    — JPA repository
│   │       └── SaveController.java           — REST endpoints /api/save
│   └── src/test/java/com/epic/engine/
│       ├── mod/
│       │   ├── ModLoaderTest.java
│       │   └── ModRegistryTest.java
│       ├── scene/
│       │   └── SceneServiceTest.java
│       └── action/
│           └── MoveActionHandlerTest.java
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── src/
│   │   ├── main.js                           — Vue app entry
│   │   ├── App.vue                           — Root layout, panel container
│   │   ├── api/
│   │   │   └── client.js                     — API client (fetch wrapper)
│   │   ├── components/
│   │   │   ├── ScenePanel.vue                — Main scene display
│   │   │   ├── TextRenderer.vue              — Renders colored text segments
│   │   │   ├── ActionLink.vue                — Single clickable action
│   │   │   ├── StatusPanel.vue               — Player status display
│   │   │   └── SettingsPanel.vue             — UI customization
│   │   ├── composables/
│   │   │   ├── useGameState.js               — Reactive game state
│   │   │   └── usePanelRefresh.js            — Panel refresh orchestration
│   │   └── styles/
│   │       ├── variables.css                 — CSS custom properties
│   │       └── base.css                      — Base styles
│   └── tests/                                — (future: Vitest component tests)
└── mods/
    └── base/
        ├── mod.yaml                          — Mod descriptor
        ├── scenes/
        │   ├── village_square.yaml           — Starting scene
        │   ├── tavern.yaml                   — Tavern scene
        │   └── forest_entrance.yaml          — Forest entrance scene
        └── data/
            └── (future: items, classes, etc.)
```

---

### Task 1: Project Scaffolding — Backend

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/epic/engine/EpicApplication.java`
- Create: `backend/src/main/resources/application.yaml`
- Create: `backend/src/main/java/com/epic/engine/config/CorsConfig.java`

- [ ] **Step 1: Create Maven project with Spring Boot dependencies**

```xml
<!-- backend/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>

    <groupId>com.epic</groupId>
    <artifactId>epic-engine</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>Epic Engine</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create application entry point**

```java
// backend/src/main/java/com/epic/engine/EpicApplication.java
package com.epic.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EpicApplication {
    public static void main(String[] args) {
        SpringApplication.run(EpicApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application config**

```yaml
# backend/src/main/resources/application.yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/epic
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

epic:
  mods-path: ../mods
```

- [ ] **Step 4: Create CORS config for local dev**

```java
// backend/src/main/java/com/epic/engine/config/CorsConfig.java
package com.epic.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
```

- [ ] **Step 5: Verify backend starts**

Run: `cd backend && mvn spring-boot:run`
Expected: Application starts on port 8080 without errors. Stop it with Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add backend/
git commit -m "feat: scaffold Spring Boot backend with H2 and CORS config"
```

---

### Task 2: Project Scaffolding — Frontend

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/styles/variables.css`
- Create: `frontend/src/styles/base.css`

- [ ] **Step 1: Initialize Vue project with Vite**

```json
// frontend/package.json
{
  "name": "epic-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.5.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.0",
    "vite": "^6.0.0"
  }
}
```

- [ ] **Step 2: Create Vite config**

```javascript
// frontend/vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

- [ ] **Step 3: Create index.html**

```html
<!-- frontend/index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Epic</title>
</head>
<body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
</body>
</html>
```

- [ ] **Step 4: Create CSS variables and base styles**

```css
/* frontend/src/styles/variables.css */
:root {
    --bg-color: #1a1a2e;
    --text-color: #e0e0e0;
    --panel-bg: #16213e;
    --panel-border-color: #0f3460;
    --panel-border-width: 1px;
    --panel-border-style: solid;
    --link-color: #e94560;
    --link-hover-color: #ff6b6b;
    --font-size: 16px;
    --font-family: "LXGW WenKai", "Noto Serif SC", serif;
}
```

```css
/* frontend/src/styles/base.css */
@import './variables.css';

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    background-color: var(--bg-color);
    color: var(--text-color);
    font-size: var(--font-size);
    font-family: var(--font-family);
    min-height: 100vh;
}

a, .action-link {
    color: var(--link-color);
    cursor: pointer;
    text-decoration: none;
}

a:hover, .action-link:hover {
    color: var(--link-hover-color);
    text-decoration: underline;
}
```

- [ ] **Step 5: Create Vue app entry and root component**

```javascript
// frontend/src/main.js
import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'

createApp(App).mount('#app')
```

```vue
<!-- frontend/src/App.vue -->
<template>
  <div class="app-container">
    <p>Epic Engine — 引擎启动成功</p>
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-container {
    padding: 2rem;
}
</style>
```

- [ ] **Step 6: Install dependencies and verify frontend starts**

Run: `cd frontend && npm install && npm run dev`
Expected: Vite dev server starts on port 5173. Browser shows "Epic Engine — 引擎启动成功".

- [ ] **Step 7: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold Vue 3 frontend with Vite and CSS variables"
```

---

### Task 3: Mod Loading System

**Files:**
- Create: `backend/src/main/java/com/epic/engine/mod/ModDescriptor.java`
- Create: `backend/src/main/java/com/epic/engine/mod/ModLoader.java`
- Create: `backend/src/main/java/com/epic/engine/mod/ModRegistry.java`
- Create: `backend/src/test/java/com/epic/engine/mod/ModLoaderTest.java`
- Create: `backend/src/test/java/com/epic/engine/mod/ModRegistryTest.java`
- Create: `mods/base/mod.yaml`

- [ ] **Step 1: Create base mod descriptor file**

```yaml
# mods/base/mod.yaml
id: base
name: "基础内容包"
version: "0.1.0"
description: "引擎默认内容包"
load-order: 0
dependencies: []
```

- [ ] **Step 2: Write failing test for ModLoader**

```java
// backend/src/test/java/com/epic/engine/mod/ModLoaderTest.java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=ModLoaderTest`
Expected: Compilation error — ModLoader and ModDescriptor don't exist yet.

- [ ] **Step 4: Implement ModDescriptor**

```java
// backend/src/main/java/com/epic/engine/mod/ModDescriptor.java
package com.epic.engine.mod;

import java.nio.file.Path;
import java.util.List;

public record ModDescriptor(
        String id,
        String name,
        String version,
        String description,
        int loadOrder,
        List<String> dependencies,
        Path path
) {}
```

- [ ] **Step 5: Implement ModLoader**

```java
// backend/src/main/java/com/epic/engine/mod/ModLoader.java
package com.epic.engine.mod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ModLoader {

    private final Path modsPath;

    public ModLoader(@Value("${epic.mods-path:../mods}") String modsPath) {
        this.modsPath = Path.of(modsPath).toAbsolutePath();
    }

    public ModLoader(Path modsPath) {
        this.modsPath = modsPath;
    }

    public List<ModDescriptor> discoverMods() throws IOException {
        List<ModDescriptor> mods = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path modFile = entry.resolve("mod.yaml");
                    if (Files.exists(modFile)) {
                        mods.add(parseModDescriptor(modFile, entry));
                    }
                }
            }
        }
        mods.sort(Comparator.comparingInt(ModDescriptor::loadOrder));
        return mods;
    }

    @SuppressWarnings("unchecked")
    private ModDescriptor parseModDescriptor(Path modFile, Path modDir) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(modFile)) {
            Map<String, Object> data = yaml.load(is);
            return new ModDescriptor(
                    (String) data.get("id"),
                    (String) data.get("name"),
                    (String) data.get("version"),
                    (String) data.get("description"),
                    data.containsKey("load-order") ? (int) data.get("load-order") : 0,
                    data.containsKey("dependencies") ? (List<String>) data.get("dependencies") : List.of(),
                    modDir
            );
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=ModLoaderTest`
Expected: 2 tests PASS.

- [ ] **Step 7: Write failing test for ModRegistry**

```java
// backend/src/test/java/com/epic/engine/mod/ModRegistryTest.java
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
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=ModRegistryTest`
Expected: Compilation error — ModRegistry doesn't exist yet.

- [ ] **Step 9: Implement ModRegistry**

```java
// backend/src/main/java/com/epic/engine/mod/ModRegistry.java
package com.epic.engine.mod;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class ModRegistry {

    private final ModLoader modLoader;
    private final Map<String, Map<String, Object>> scenes = new LinkedHashMap<>();

    public ModRegistry(ModLoader modLoader) {
        this.modLoader = modLoader;
    }

    @PostConstruct
    public void initialize() throws IOException {
        List<ModDescriptor> mods = modLoader.discoverMods();
        for (ModDescriptor mod : mods) {
            loadModContent(mod);
        }
    }

    public Optional<Map<String, Object>> getScene(String sceneId) {
        return Optional.ofNullable(scenes.get(sceneId));
    }

    public Collection<String> getAllSceneIds() {
        return Collections.unmodifiableSet(scenes.keySet());
    }

    @SuppressWarnings("unchecked")
    private void loadModContent(ModDescriptor mod) throws IOException {
        Path scenesDir = mod.path().resolve("scenes");
        if (Files.isDirectory(scenesDir)) {
            Yaml yaml = new Yaml();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scenesDir, "*.yaml")) {
                for (Path sceneFile : stream) {
                    try (InputStream is = Files.newInputStream(sceneFile)) {
                        Map<String, Object> sceneData = yaml.load(is);
                        String id = (String) sceneData.get("id");
                        scenes.put(id, sceneData);
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=ModRegistryTest`
Expected: 2 tests PASS.

- [ ] **Step 11: Commit**

```bash
git add backend/src/ mods/
git commit -m "feat: implement mod loading system with load-order and override support"
```

---

### Task 4: Scene API and Action Handling

**Files:**
- Create: `backend/src/main/java/com/epic/engine/scene/TextSegment.java`
- Create: `backend/src/main/java/com/epic/engine/scene/SceneAction.java`
- Create: `backend/src/main/java/com/epic/engine/scene/Scene.java`
- Create: `backend/src/main/java/com/epic/engine/scene/SceneService.java`
- Create: `backend/src/main/java/com/epic/engine/scene/SceneController.java`
- Create: `backend/src/main/java/com/epic/engine/panel/PanelRefresh.java`
- Create: `backend/src/main/java/com/epic/engine/action/ActionResponse.java`
- Create: `backend/src/main/java/com/epic/engine/action/ActionHandler.java`
- Create: `backend/src/main/java/com/epic/engine/action/MoveActionHandler.java`
- Create: `backend/src/main/java/com/epic/engine/action/ActionController.java`
- Create: `backend/src/test/java/com/epic/engine/scene/SceneServiceTest.java`
- Create: `backend/src/test/java/com/epic/engine/action/MoveActionHandlerTest.java`
- Create: `mods/base/scenes/village_square.yaml`
- Create: `mods/base/scenes/tavern.yaml`
- Create: `mods/base/scenes/forest_entrance.yaml`

- [ ] **Step 1: Create scene content files for base mod**

```yaml
# mods/base/scenes/village_square.yaml
id: village_square
description:
  - text: "你站在"
    color: "#e0e0e0"
  - text: "村庄广场"
    color: "#4ecdc4"
  - text: "的中央。周围是低矮的石砌房屋，空气中弥漫着炊烟的气息。"
    color: "#e0e0e0"
  - text: "\n\n"
    color: "#e0e0e0"
  - text: "北面"
    color: "#ffd93d"
  - text: "是一间挂着木牌的"
    color: "#e0e0e0"
  - text: "酒馆"
    color: "#ff6b35"
  - text: "，南面的小路通往"
    color: "#e0e0e0"
  - text: "幽暗森林"
    color: "#2d6a4f"
  - text: "。"
    color: "#e0e0e0"
actions:
  - id: go-tavern
    label: "走进酒馆"
    type: move
    target: tavern
  - id: go-forest
    label: "前往森林"
    type: move
    target: forest_entrance
```

```yaml
# mods/base/scenes/tavern.yaml
id: tavern
description:
  - text: "你推开"
    color: "#e0e0e0"
  - text: "酒馆"
    color: "#ff6b35"
  - text: "的木门。里面烛光摇曳，几个冒险者围坐在角落的桌旁低声交谈。"
    color: "#e0e0e0"
  - text: "\n\n"
    color: "#e0e0e0"
  - text: "酒保"
    color: "#ffd93d"
  - text: "正在擦拭杯子，抬头看了你一眼。"
    color: "#e0e0e0"
actions:
  - id: go-square
    label: "离开酒馆"
    type: move
    target: village_square
```

```yaml
# mods/base/scenes/forest_entrance.yaml
id: forest_entrance
description:
  - text: "你来到"
    color: "#e0e0e0"
  - text: "幽暗森林"
    color: "#2d6a4f"
  - text: "的入口。高大的古树遮天蔽日，只有零星的"
    color: "#e0e0e0"
  - text: "光线"
    color: "#ffd93d"
  - text: "透过树冠洒下。远处传来不明生物的"
    color: "#e0e0e0"
  - text: "低吼"
    color: "#e94560"
  - text: "。"
    color: "#e0e0e0"
actions:
  - id: go-square
    label: "返回村庄"
    type: move
    target: village_square
```

- [ ] **Step 2: Write failing test for SceneService**

```java
// backend/src/test/java/com/epic/engine/scene/SceneServiceTest.java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=SceneServiceTest`
Expected: Compilation error — Scene, TextSegment, SceneAction, SceneService don't exist.

- [ ] **Step 4: Implement scene data models**

```java
// backend/src/main/java/com/epic/engine/scene/TextSegment.java
package com.epic.engine.scene;

public record TextSegment(String text, String color) {}
```

```java
// backend/src/main/java/com/epic/engine/scene/SceneAction.java
package com.epic.engine.scene;

import java.util.Map;

public record SceneAction(String id, String label, String type, Map<String, String> params) {}
```

```java
// backend/src/main/java/com/epic/engine/scene/Scene.java
package com.epic.engine.scene;

import java.util.List;

public record Scene(String id, List<TextSegment> description, List<SceneAction> actions) {}
```

- [ ] **Step 5: Implement SceneService**

```java
// backend/src/main/java/com/epic/engine/scene/SceneService.java
package com.epic.engine.scene;

import com.epic.engine.mod.ModRegistry;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SceneService {

    private final ModRegistry modRegistry;

    public SceneService(ModRegistry modRegistry) {
        this.modRegistry = modRegistry;
    }

    @SuppressWarnings("unchecked")
    public Scene getScene(String sceneId) {
        Optional<Map<String, Object>> raw = modRegistry.getScene(sceneId);
        if (raw.isEmpty()) {
            return null;
        }
        Map<String, Object> data = raw.get();
        String id = (String) data.get("id");

        List<Map<String, String>> descRaw = (List<Map<String, String>>) data.get("description");
        List<TextSegment> description = descRaw.stream()
                .map(seg -> new TextSegment(seg.get("text"), seg.get("color")))
                .toList();

        List<Map<String, Object>> actionsRaw = (List<Map<String, Object>>) data.getOrDefault("actions", Collections.emptyList());
        List<SceneAction> actions = actionsRaw.stream()
                .map(a -> new SceneAction(
                        (String) a.get("id"),
                        (String) a.get("label"),
                        (String) a.get("type"),
                        Map.of("target", (String) a.getOrDefault("target", ""))
                ))
                .toList();

        return new Scene(id, description, actions);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=SceneServiceTest`
Expected: 2 tests PASS.

- [ ] **Step 7: Implement panel and action types**

```java
// backend/src/main/java/com/epic/engine/panel/PanelRefresh.java
package com.epic.engine.panel;

public enum PanelRefresh {
    SCENE,
    STATUS,
    INVENTORY,
    MAP
}
```

```java
// backend/src/main/java/com/epic/engine/action/ActionResponse.java
package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;

import java.util.List;

public record ActionResponse(boolean success, String message, List<PanelRefresh> refreshPanels) {}
```

```java
// backend/src/main/java/com/epic/engine/action/ActionHandler.java
package com.epic.engine.action;

import java.util.Map;

public interface ActionHandler {
    String getType();
    ActionResponse handle(String playerId, Map<String, String> params);
}
```

- [ ] **Step 8: Write failing test for MoveActionHandler**

```java
// backend/src/test/java/com/epic/engine/action/MoveActionHandlerTest.java
package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MoveActionHandlerTest {

    PlayerStateRepository repository;
    MoveActionHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(PlayerStateRepository.class);
        handler = new MoveActionHandler(repository);
    }

    @Test
    void movesPlayerToTargetScene() {
        PlayerState state = new PlayerState();
        state.setPlayerId("player1");
        state.setCurrentScene("village_square");
        when(repository.findByPlayerId("player1")).thenReturn(Optional.of(state));

        ActionResponse response = handler.handle("player1", Map.of("target", "tavern"));

        assertThat(response.success()).isTrue();
        assertThat(state.getCurrentScene()).isEqualTo("tavern");
        verify(repository).save(state);
        assertThat(response.refreshPanels()).contains(PanelRefresh.SCENE);
    }

    @Test
    void failsWhenNoTarget() {
        ActionResponse response = handler.handle("player1", Map.of());
        assertThat(response.success()).isFalse();
    }
}
```

- [ ] **Step 9: Run test to verify it fails**

Run: `cd backend && mvn test -pl . -Dtest=MoveActionHandlerTest`
Expected: Compilation error — MoveActionHandler, PlayerState, PlayerStateRepository don't exist.

- [ ] **Step 10: Implement PlayerState and repository**

```java
// backend/src/main/java/com/epic/engine/save/PlayerState.java
package com.epic.engine.save;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PlayerState {

    @Id
    private String playerId;
    private String currentScene;

    public PlayerState() {}

    public PlayerState(String playerId, String currentScene) {
        this.playerId = playerId;
        this.currentScene = currentScene;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getCurrentScene() { return currentScene; }
    public void setCurrentScene(String currentScene) { this.currentScene = currentScene; }
}
```

```java
// backend/src/main/java/com/epic/engine/save/PlayerStateRepository.java
package com.epic.engine.save;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerStateRepository extends JpaRepository<PlayerState, String> {
    Optional<PlayerState> findByPlayerId(String playerId);
}
```

- [ ] **Step 11: Implement MoveActionHandler**

```java
// backend/src/main/java/com/epic/engine/action/MoveActionHandler.java
package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MoveActionHandler implements ActionHandler {

    private final PlayerStateRepository repository;

    public MoveActionHandler(PlayerStateRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getType() {
        return "move";
    }

    @Override
    public ActionResponse handle(String playerId, Map<String, String> params) {
        String target = params.get("target");
        if (target == null || target.isBlank()) {
            return new ActionResponse(false, "无效的移动目标", List.of());
        }

        Optional<PlayerState> stateOpt = repository.findByPlayerId(playerId);
        PlayerState state = stateOpt.orElseGet(() -> new PlayerState(playerId, "village_square"));
        state.setCurrentScene(target);
        repository.save(state);

        return new ActionResponse(true, null, List.of(PanelRefresh.SCENE));
    }
}
```

- [ ] **Step 12: Run test to verify it passes**

Run: `cd backend && mvn test -pl . -Dtest=MoveActionHandlerTest`
Expected: 2 tests PASS.

- [ ] **Step 13: Implement REST controllers**

```java
// backend/src/main/java/com/epic/engine/scene/SceneController.java
package com.epic.engine.scene;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scene")
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping("/{sceneId}")
    public ResponseEntity<Scene> getScene(@PathVariable String sceneId) {
        Scene scene = sceneService.getScene(sceneId);
        if (scene == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scene);
    }
}
```

```java
// backend/src/main/java/com/epic/engine/action/ActionController.java
package com.epic.engine.action;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/action")
public class ActionController {

    private final Map<String, ActionHandler> handlers;

    public ActionController(List<ActionHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(java.util.stream.Collectors.toMap(ActionHandler::getType, h -> h));
    }

    @PostMapping
    public ActionResponse performAction(@RequestBody ActionRequest request) {
        ActionHandler handler = handlers.get(request.type());
        if (handler == null) {
            return new ActionResponse(false, "未知的操作类型: " + request.type(), List.of());
        }
        return handler.handle(request.playerId(), request.params());
    }

    public record ActionRequest(String playerId, String type, Map<String, String> params) {}
}
```

- [ ] **Step 14: Run all tests**

Run: `cd backend && mvn test`
Expected: All tests PASS.

- [ ] **Step 15: Commit**

```bash
git add backend/src/ mods/base/scenes/
git commit -m "feat: implement scene API, move action handler, and base mod scenes"
```

---

### Task 5: Save System API

**Files:**
- Create: `backend/src/main/java/com/epic/engine/save/SaveController.java`

- [ ] **Step 1: Implement SaveController**

```java
// backend/src/main/java/com/epic/engine/save/SaveController.java
package com.epic.engine.save;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/save")
public class SaveController {

    private final PlayerStateRepository repository;

    public SaveController(PlayerStateRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerState> getState(@PathVariable String playerId) {
        return repository.findByPlayerId(playerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    PlayerState newState = new PlayerState(playerId, "village_square");
                    repository.save(newState);
                    return ResponseEntity.ok(newState);
                });
    }
}
```

- [ ] **Step 2: Verify backend starts and API responds**

Run: `cd backend && mvn spring-boot:run`

In another terminal:
```bash
curl http://localhost:8080/api/save/player1
curl http://localhost:8080/api/scene/village_square
```

Expected: First returns `{"playerId":"player1","currentScene":"village_square"}`. Second returns the scene JSON with text segments and actions.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/epic/engine/save/SaveController.java
git commit -m "feat: add save state API endpoint"
```

---

### Task 6: Frontend — API Client and Game State

**Files:**
- Create: `frontend/src/api/client.js`
- Create: `frontend/src/composables/useGameState.js`
- Create: `frontend/src/composables/usePanelRefresh.js`

- [ ] **Step 1: Create API client**

```javascript
// frontend/src/api/client.js
const BASE_URL = '/api'

export async function fetchScene(sceneId) {
    const response = await fetch(`${BASE_URL}/scene/${sceneId}`)
    if (!response.ok) return null
    return response.json()
}

export async function performAction(playerId, type, params) {
    const response = await fetch(`${BASE_URL}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, type, params })
    })
    return response.json()
}

export async function getPlayerState(playerId) {
    const response = await fetch(`${BASE_URL}/save/${playerId}`)
    return response.json()
}
```

- [ ] **Step 2: Create game state composable**

```javascript
// frontend/src/composables/useGameState.js
import { reactive } from 'vue'
import { getPlayerState, fetchScene, performAction } from '../api/client.js'

const state = reactive({
    playerId: 'player1',
    currentScene: null,
    loading: false
})

export function useGameState() {
    async function initialize() {
        state.loading = true
        const playerState = await getPlayerState(state.playerId)
        const scene = await fetchScene(playerState.currentScene)
        state.currentScene = scene
        state.loading = false
    }

    async function doAction(type, params) {
        state.loading = true
        const response = await performAction(state.playerId, type, params)
        return response
    }

    async function refreshScene(sceneId) {
        const scene = await fetchScene(sceneId)
        state.currentScene = scene
    }

    return { state, initialize, doAction, refreshScene }
}
```

- [ ] **Step 3: Create panel refresh composable**

```javascript
// frontend/src/composables/usePanelRefresh.js
import { useGameState } from './useGameState.js'
import { getPlayerState } from '../api/client.js'

export function usePanelRefresh() {
    const { state, refreshScene } = useGameState()

    async function handleRefresh(refreshPanels) {
        for (const panel of refreshPanels) {
            if (panel === 'SCENE') {
                const playerState = await getPlayerState(state.playerId)
                await refreshScene(playerState.currentScene)
            }
        }
        state.loading = false
    }

    return { handleRefresh }
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/ frontend/src/composables/
git commit -m "feat: add API client and game state management composables"
```

---

### Task 7: Frontend — Scene Panel and Text Rendering

**Files:**
- Create: `frontend/src/components/TextRenderer.vue`
- Create: `frontend/src/components/ActionLink.vue`
- Create: `frontend/src/components/ScenePanel.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Create TextRenderer component**

```vue
<!-- frontend/src/components/TextRenderer.vue -->
<template>
  <span class="text-renderer">
    <span
      v-for="(segment, index) in segments"
      :key="index"
      :style="{ color: segment.color }"
      v-text="segment.text"
    ></span>
  </span>
</template>

<script setup>
defineProps({
    segments: {
        type: Array,
        required: true
    }
})
</script>

<style scoped>
.text-renderer {
    line-height: 1.8;
    white-space: pre-wrap;
}
</style>
```

- [ ] **Step 2: Create ActionLink component**

```vue
<!-- frontend/src/components/ActionLink.vue -->
<template>
  <span class="action-link" @click="$emit('execute')">
    {{ action.label }}
  </span>
</template>

<script setup>
defineProps({
    action: {
        type: Object,
        required: true
    }
})

defineEmits(['execute'])
</script>

<style scoped>
.action-link {
    display: inline-block;
    margin: 0.5rem 1rem 0.5rem 0;
    padding: 0.3rem 0.8rem;
    border: var(--panel-border-width) var(--panel-border-style) var(--link-color);
    border-radius: 4px;
    transition: background-color 0.2s;
}

.action-link:hover {
    background-color: rgba(233, 69, 96, 0.1);
}
</style>
```

- [ ] **Step 3: Create ScenePanel component**

```vue
<!-- frontend/src/components/ScenePanel.vue -->
<template>
  <div class="scene-panel" v-if="scene">
    <div class="scene-description">
      <TextRenderer :segments="scene.description" />
    </div>
    <div class="scene-actions">
      <ActionLink
        v-for="action in scene.actions"
        :key="action.id"
        :action="action"
        @execute="handleAction(action)"
      />
    </div>
  </div>
  <div class="scene-panel loading" v-else>
    <span style="color: #666">加载中...</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TextRenderer from './TextRenderer.vue'
import ActionLink from './ActionLink.vue'
import { useGameState } from '../composables/useGameState.js'
import { usePanelRefresh } from '../composables/usePanelRefresh.js'

const { state, doAction } = useGameState()
const { handleRefresh } = usePanelRefresh()

const scene = computed(() => state.currentScene)

async function handleAction(action) {
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        await handleRefresh(response.refreshPanels)
    }
}
</script>

<style scoped>
.scene-panel {
    background-color: var(--panel-bg);
    border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
    padding: 1.5rem;
    border-radius: 8px;
    max-width: 800px;
}

.scene-description {
    margin-bottom: 1.5rem;
}

.scene-actions {
    border-top: 1px solid var(--panel-border-color);
    padding-top: 1rem;
}
</style>
```

- [ ] **Step 4: Update App.vue to use ScenePanel**

```vue
<!-- frontend/src/App.vue -->
<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Epic</h1>
    </header>
    <main class="app-main">
      <ScenePanel />
    </main>
  </div>
</template>

<script setup>
import ScenePanel from './components/ScenePanel.vue'
import { useGameState } from './composables/useGameState.js'
import { onMounted } from 'vue'

const { initialize } = useGameState()

onMounted(() => {
    initialize()
})
</script>

<style scoped>
.app-container {
    padding: 2rem;
    max-width: 1000px;
    margin: 0 auto;
}

.app-header h1 {
    color: var(--link-color);
    margin-bottom: 2rem;
    font-size: 1.5rem;
}
</style>
```

- [ ] **Step 5: Start both backend and frontend, verify end-to-end**

Terminal 1: `cd backend && mvn spring-boot:run`
Terminal 2: `cd frontend && npm run dev`

Open http://localhost:5173 in browser.
Expected: Village square scene renders with colored text. Clicking "走进酒馆" navigates to tavern scene. Clicking "离开酒馆" returns to village square.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/ frontend/src/App.vue
git commit -m "feat: implement scene panel with colored text rendering and action links"
```

---

### Task 8: Frontend — Settings Panel (Player UI Customization)

**Files:**
- Create: `frontend/src/components/SettingsPanel.vue`
- Create: `frontend/src/composables/useSettings.js`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Create settings composable**

```javascript
// frontend/src/composables/useSettings.js
import { reactive, watch } from 'vue'

const STORAGE_KEY = 'epic-settings'

const defaults = {
    fontSize: '16px',
    bgColor: '#1a1a2e',
    textColor: '#e0e0e0',
    panelBg: '#16213e',
    panelBorderColor: '#0f3460',
    linkColor: '#e94560'
}

function loadSettings() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
        return { ...defaults, ...JSON.parse(saved) }
    }
    return { ...defaults }
}

const settings = reactive(loadSettings())

function applySettings() {
    const root = document.documentElement
    root.style.setProperty('--font-size', settings.fontSize)
    root.style.setProperty('--bg-color', settings.bgColor)
    root.style.setProperty('--text-color', settings.textColor)
    root.style.setProperty('--panel-bg', settings.panelBg)
    root.style.setProperty('--panel-border-color', settings.panelBorderColor)
    root.style.setProperty('--link-color', settings.linkColor)
}

watch(settings, () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    applySettings()
}, { deep: true })

export function useSettings() {
    applySettings()
    return { settings, defaults }
}
```

- [ ] **Step 2: Create SettingsPanel component**

```vue
<!-- frontend/src/components/SettingsPanel.vue -->
<template>
  <div class="settings-panel" v-if="visible">
    <h3>显示设置</h3>
    <div class="setting-row">
      <label>字体大小</label>
      <input type="range" min="12" max="24" step="1"
             :value="parseInt(settings.fontSize)"
             @input="settings.fontSize = $event.target.value + 'px'" />
      <span>{{ settings.fontSize }}</span>
    </div>
    <div class="setting-row">
      <label>背景颜色</label>
      <input type="color" v-model="settings.bgColor" />
    </div>
    <div class="setting-row">
      <label>文字颜色</label>
      <input type="color" v-model="settings.textColor" />
    </div>
    <div class="setting-row">
      <label>面板背景</label>
      <input type="color" v-model="settings.panelBg" />
    </div>
    <div class="setting-row">
      <label>边框颜色</label>
      <input type="color" v-model="settings.panelBorderColor" />
    </div>
    <div class="setting-row">
      <label>链接颜色</label>
      <input type="color" v-model="settings.linkColor" />
    </div>
    <button class="reset-btn" @click="resetDefaults">恢复默认</button>
  </div>
</template>

<script setup>
import { useSettings } from '../composables/useSettings.js'

defineProps({
    visible: {
        type: Boolean,
        default: false
    }
})

const { settings, defaults } = useSettings()

function resetDefaults() {
    Object.assign(settings, defaults)
}
</script>

<style scoped>
.settings-panel {
    background-color: var(--panel-bg);
    border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
    padding: 1.5rem;
    border-radius: 8px;
    max-width: 400px;
}

.settings-panel h3 {
    margin-bottom: 1rem;
    color: var(--link-color);
}

.setting-row {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 0.8rem;
}

.setting-row label {
    min-width: 80px;
}

.reset-btn {
    margin-top: 1rem;
    padding: 0.4rem 1rem;
    background: none;
    border: 1px solid var(--link-color);
    color: var(--link-color);
    border-radius: 4px;
    cursor: pointer;
}

.reset-btn:hover {
    background-color: rgba(233, 69, 96, 0.1);
}
</style>
```

- [ ] **Step 3: Update App.vue to include settings toggle**

```vue
<!-- frontend/src/App.vue -->
<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Epic</h1>
      <button class="settings-toggle" @click="showSettings = !showSettings">
        ⚙ 设置
      </button>
    </header>
    <main class="app-main">
      <SettingsPanel :visible="showSettings" />
      <ScenePanel />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ScenePanel from './components/ScenePanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import { useGameState } from './composables/useGameState.js'
import { useSettings } from './composables/useSettings.js'

const { initialize } = useGameState()
useSettings()

const showSettings = ref(false)

onMounted(() => {
    initialize()
})
</script>

<style scoped>
.app-container {
    padding: 2rem;
    max-width: 1000px;
    margin: 0 auto;
}

.app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
}

.app-header h1 {
    color: var(--link-color);
    font-size: 1.5rem;
}

.settings-toggle {
    background: none;
    border: 1px solid var(--panel-border-color);
    color: var(--text-color);
    padding: 0.4rem 0.8rem;
    border-radius: 4px;
    cursor: pointer;
}

.settings-toggle:hover {
    border-color: var(--link-color);
}
</style>
```

- [ ] **Step 4: Verify in browser**

Expected: Settings button in header. Clicking it shows settings panel. Changing colors/font size immediately reflects on the page. Refreshing the page preserves the settings.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/
git commit -m "feat: add player UI customization settings panel with localStorage persistence"
```

---

### Task 9: Integration Smoke Test

**Files:** None new — this is a verification task.

- [ ] **Step 1: Start backend**

Run: `cd backend && mvn spring-boot:run`

- [ ] **Step 2: Start frontend**

Run: `cd frontend && npm run dev`

- [ ] **Step 3: Verify full flow in browser at http://localhost:5173**

Checklist:
- [ ] Village square loads with colored Chinese text
- [ ] "走进酒馆" and "前往森林" are clickable links
- [ ] Clicking "走进酒馆" shows tavern scene with different colored text
- [ ] Clicking "离开酒馆" returns to village square
- [ ] Clicking "前往森林" shows forest entrance with colored text (green for 幽暗森林, red for 低吼)
- [ ] Settings panel opens/closes
- [ ] Changing font size updates all text
- [ ] Changing colors updates the theme
- [ ] Refreshing the page preserves settings and current scene

- [ ] **Step 4: Run all backend tests**

Run: `cd backend && mvn test`
Expected: All tests PASS.

- [ ] **Step 5: Final commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: integration test fixes"
```

---

## Summary

This plan delivers:
1. **Mod system** — discovers mods, loads content by load-order, supports override
2. **Scene rendering** — colored Chinese text via structured JSON
3. **Action system** — extensible handler pattern, currently supports movement
4. **Multi-panel UI** — independent panels with server-directed refresh
5. **Player customization** — CSS variable driven, localStorage persisted
6. **Player state** — H2 database, scene tracking

**Next plans (future):**
- Plan 2: Combat system (turn-based, extensible damage/effect calculation)
- Plan 3: Class/profession system (multi-class, specializations, synergies)
- Plan 4: Equipment system (slots, affixes, class interactions)
- Plan 5: World generation and map system
- Plan 6: Mod mechanism layer (plugin hooks, rule overrides)
