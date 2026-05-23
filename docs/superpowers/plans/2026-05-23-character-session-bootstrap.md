# 角色系统 + Session + 游戏启动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement character creation/selection with session management, world bootstrap (map loading), and UI data handlers so the game is playable end-to-end.

**Architecture:** Session token identifies a player's save slots. SnapshotController routes by session state (no token → new session, no active character → character select, active character → in-game). World bootstrap and UI rendering are JS handlers in base-rules module. ScriptRuntime gains `engine.loadYaml()` for file access.

**Tech Stack:** Java 21, Spring Boot 3.4, GraalJS, H2, SnakeYAML, Vue 3

---

## File Structure

```
backend/src/main/java/com/epic/engine/
├── session/
│   ├── SessionService.java          (NEW — token management, timeout, active character)
│   └── SessionData.java             (NEW — record: ownerId, activeCharacterId, lastActivity)
├── snapshot/
│   ├── SnapshotController.java      (MODIFY — add session token handling, phase routing)
│   ├── SnapshotService.java         (MODIFY — add character_select/create phases)
│   └── WorldSnapshot.java           (MODIFY — add phase field, form/characters fields)
├── script/
│   └── ScriptRuntime.java           (MODIFY — add loadYaml to EngineApi, track module context)
├── module/
│   ├── Schema.java                  (MODIFY — add required_subs, category, label, description, base_components, modifiers)
│   └── SchemaRegistry.java          (MODIFY — add getByCategory query)
├── core/
│   └── EngineBootstrap.java         (MODIFY — fire world.init after loading, load persistent entities)
└── persistence/
    └── PersistenceService.java      (MODIFY — add findByOwner query)

backend/src/main/java/com/epic/engine/persistence/
    └── EntityDataRepository.java    (MODIFY — add findByOwnerTag custom query)

frontend/src/
├── App.vue                          (MODIFY — session token, phase routing)
├── api/client.js                    (MODIFY — add token header)
├── components/
│   ├── CharacterSelect.vue          (NEW — card layout, select/create)
│   └── CharacterCreate.vue          (NEW — dynamic form from snapshot)

mods/base-rules/
├── schemas/
│   ├── main/
│   │   └── character.schema.yaml    (NEW)
│   └── sub/
│       ├── class_warrior.schema.yaml (NEW)
│       └── class_mage.schema.yaml   (NEW)
├── entities/
│   └── maps/
│       └── world_map.yaml           (NEW)
├── handlers/
│   ├── world/
│   │   └── bootstrap.js            (NEW — load map on world.init)
│   ├── character/
│   │   └── select.js               (NEW — character CRUD actions)
│   └── ui/
│       ├── status_bars.js           (NEW — render HP/MP bars)
│       └── actions.js               (NEW — render movement actions)
```

---

## Task 1: Session Service

**Files:**
- Create: `backend/src/main/java/com/epic/engine/session/SessionData.java`
- Create: `backend/src/main/java/com/epic/engine/session/SessionService.java`
- Test: `backend/src/test/java/com/epic/engine/session/SessionServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SessionServiceTest {

    SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(30, 5);
    }

    @Test
    void createSession_returnsToken() {
        String token = service.createSession();
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void getSession_withValidToken_returnsData() {
        String token = service.createSession();
        SessionData data = service.getSession(token);
        assertThat(data).isNotNull();
        assertThat(data.activeCharacterId()).isNull();
    }

    @Test
    void getSession_withInvalidToken_returnsNull() {
        assertThat(service.getSession("bogus")).isNull();
    }

    @Test
    void setActiveCharacter_updatesSession() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        SessionData data = service.getSession(token);
        assertThat(data.activeCharacterId()).isEqualTo("char_1");
    }

    @Test
    void clearActiveCharacter_removesCharacter() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        service.clearActiveCharacter(token);
        SessionData data = service.getSession(token);
        assertThat(data.activeCharacterId()).isNull();
    }

    @Test
    void isTimedOut_afterTimeout_returnsTrue() {
        SessionService shortTimeout = new SessionService(0, 5);
        String token = shortTimeout.createSession();
        shortTimeout.setActiveCharacter(token, "char_1");
        // With 0 minute timeout, should immediately be timed out
        assertThat(shortTimeout.isTimedOut(token)).isTrue();
    }

    @Test
    void touchSession_resetsTimeout() {
        String token = service.createSession();
        service.setActiveCharacter(token, "char_1");
        service.touchSession(token);
        assertThat(service.isTimedOut(token)).isFalse();
    }

    @Test
    void getMaxSlots_returnsConfigured() {
        assertThat(service.getMaxSlots()).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test to verify compilation failure**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SessionServiceTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement SessionData**

```java
package com.epic.engine.session;

import java.time.Instant;

public record SessionData(
        String token,
        String activeCharacterId,
        Instant lastActivity
) {
    public SessionData withActiveCharacter(String characterId) {
        return new SessionData(token, characterId, Instant.now());
    }

    public SessionData withTouch() {
        return new SessionData(token, activeCharacterId, Instant.now());
    }

    public SessionData withClearedCharacter() {
        return new SessionData(token, null, lastActivity);
    }
}
```

- [ ] **Step 4: Implement SessionService**

```java
package com.epic.engine.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final int timeoutMinutes;
    private final int maxSlots;

    public SessionService(
            @Value("${epic.session-timeout-minutes:30}") int timeoutMinutes,
            @Value("${epic.max-character-slots:5}") int maxSlots) {
        this.timeoutMinutes = timeoutMinutes;
        this.maxSlots = maxSlots;
    }

    public String createSession() {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionData(token, null, Instant.now()));
        return token;
    }

    public SessionData getSession(String token) {
        return sessions.get(token);
    }

    public void setActiveCharacter(String token, String characterId) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withActiveCharacter(characterId));
        }
    }

    public void clearActiveCharacter(String token) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withClearedCharacter());
        }
    }

    public boolean isTimedOut(String token) {
        SessionData data = sessions.get(token);
        if (data == null || data.activeCharacterId() == null) return false;
        Duration elapsed = Duration.between(data.lastActivity(), Instant.now());
        return elapsed.toMinutes() >= timeoutMinutes;
    }

    public void touchSession(String token) {
        SessionData data = sessions.get(token);
        if (data != null) {
            sessions.put(token, data.withTouch());
        }
    }

    public int getMaxSlots() {
        return maxSlots;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SessionServiceTest`
Expected: 8 tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/epic/engine/session/ backend/src/test/java/com/epic/engine/session/
git commit -m "feat: implement SessionService - token management with configurable timeout"
```

---

## Task 2: WorldSnapshot Phase Support + SnapshotController Session Routing

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotController.java`
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`
- Test: `backend/src/test/java/com/epic/engine/snapshot/SessionRoutingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.snapshot;

import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionRoutingTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;

    @Test
    void noToken_returnsNewSessionWithCharacterSelect() {
        ResponseEntity<Map> response = rest.getForEntity("/api/snapshot", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = response.getBody();
        assertThat(body.get("phase")).isEqualTo("character_select");
        assertThat(body.get("sessionToken")).isNotNull();
    }

    @Test
    void withToken_noActiveCharacter_returnsCharacterSelect() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        ResponseEntity<Map> response = rest.exchange(
                "/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map body = response.getBody();
        assertThat(body.get("phase")).isEqualTo("character_select");
    }

    @Test
    void action_withToken_passesTokenToEvent() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of("type", "test_ping", "params", Map.of());
        ResponseEntity<Map> response = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(request, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("phase")).isEqualTo("character_select");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SessionRoutingTest -pl . 2>&1 | tail -5`
Expected: Compilation failure or 404

- [ ] **Step 3: Rewrite WorldSnapshot to support phases**

Replace `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`:

```java
package com.epic.engine.snapshot;

import java.util.List;
import java.util.Map;

public record WorldSnapshot(
        String phase,
        String sessionToken,
        String playerId,
        ActionResult result,
        List<CharacterInfo> characters,
        Integer maxSlots,
        FormData form,
        List<StatusBar> statusBars,
        List<BuffEntry> buffs,
        MapSnapshot map,
        CombatSnapshot combat,
        List<ActionOption> actions,
        List<LogEntry> log
) {
    public record ActionResult(boolean success, String message) {}
    public record CharacterInfo(String id, String name, int level, String classId, String classLabel) {}
    public record FormData(List<FormField> fields) {}
    public record FormField(String name, String label, String type, boolean required, List<FormOption> options) {}
    public record FormOption(String value, String label, String description) {}
    public record StatusBar(String id, String label, int current, int max, String color, int priority) {}
    public record BuffEntry(String id, String name, String remaining, int priority) {}
    public record MapSnapshot(String mapId, int playerX, int playerY, int width, int height) {}
    public record CombatSnapshot(String combatId, String phase, int round, List<CombatantInfo> combatants) {}
    public record CombatantInfo(String id, String name, String side, int hp, int maxHp, boolean alive) {}
    public record ActionOption(String type, String label, Map<String, Object> params) {}
    public record LogEntry(List<TextSegment> segments) {}
    public record TextSegment(String text, String color) {}

    public static WorldSnapshot characterSelect(String sessionToken, List<CharacterInfo> characters, int maxSlots) {
        return new WorldSnapshot("character_select", sessionToken, null,
                new ActionResult(true, "ok"), characters, maxSlots, null,
                null, null, null, null, null, null);
    }

    public static WorldSnapshot characterCreate(String sessionToken, FormData form) {
        return new WorldSnapshot("character_create", sessionToken, null,
                new ActionResult(true, "ok"), null, null, form,
                null, null, null, null, null, null);
    }

    public static WorldSnapshot inGame(String sessionToken, String playerId, ActionResult result,
                                        List<StatusBar> statusBars, List<BuffEntry> buffs,
                                        MapSnapshot map, CombatSnapshot combat,
                                        List<ActionOption> actions, List<LogEntry> log) {
        return new WorldSnapshot("in_game", sessionToken, playerId, result,
                null, null, null, statusBars, buffs, map, combat, actions, log);
    }
}
```

- [ ] **Step 4: Rewrite SnapshotController with session routing**

Replace `backend/src/main/java/com/epic/engine/snapshot/SnapshotController.java`:

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.session.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final EventBus eventBus;
    private final SnapshotService snapshotService;
    private final SessionService sessionService;

    public SnapshotController(EventBus eventBus, SnapshotService snapshotService, SessionService sessionService) {
        this.eventBus = eventBus;
        this.snapshotService = snapshotService;
        this.sessionService = sessionService;
    }

    @PostMapping("/action")
    public WorldSnapshot performAction(
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @RequestBody Map<String, Object> request) {

        token = ensureToken(token);
        sessionService.touchSession(token);

        String type = (String) request.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        GameEvent actionEvent = new GameEvent("action." + type);
        actionEvent.set("sessionToken", token);
        actionEvent.set("type", type);
        params.forEach(actionEvent::set);

        var session = sessionService.getSession(token);
        if (session.activeCharacterId() != null) {
            actionEvent.set("playerId", session.activeCharacterId());
        }

        eventBus.fire("action." + type, actionEvent);

        return snapshotService.buildSnapshot(token);
    }

    @GetMapping("/snapshot")
    public WorldSnapshot getSnapshot(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        token = ensureToken(token);
        sessionService.touchSession(token);
        return snapshotService.buildSnapshot(token);
    }

    private String ensureToken(String token) {
        if (token == null || sessionService.getSession(token) == null) {
            return sessionService.createSession();
        }
        return token;
    }
}
```

- [ ] **Step 5: Rewrite SnapshotService with phase logic**

Replace `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`:

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionData;
import com.epic.engine.session.SessionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SnapshotService {

    private final EventBus eventBus;
    private final EntityStore entityStore;
    private final SessionService sessionService;

    public SnapshotService(EventBus eventBus, EntityStore entityStore, SessionService sessionService) {
        this.eventBus = eventBus;
        this.entityStore = entityStore;
        this.sessionService = sessionService;
    }

    public WorldSnapshot buildSnapshot(String token) {
        SessionData session = sessionService.getSession(token);
        if (session == null) {
            return WorldSnapshot.characterSelect(token, List.of(), sessionService.getMaxSlots());
        }

        // Check if active character is timed out
        if (session.activeCharacterId() != null && sessionService.isTimedOut(token)) {
            sessionService.clearActiveCharacter(token);
            session = sessionService.getSession(token);
        }

        // No active character → character select
        if (session.activeCharacterId() == null) {
            return buildCharacterSelectSnapshot(token);
        }

        // Active character → in-game
        return buildInGameSnapshot(token, session.activeCharacterId());
    }

    private WorldSnapshot buildCharacterSelectSnapshot(String token) {
        // Fire event to get character list for this session
        GameEvent event = new GameEvent("session.list_characters");
        event.set("sessionToken", token);
        event.set("characters", new ArrayList<WorldSnapshot.CharacterInfo>());
        eventBus.fire("session.list_characters", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.CharacterInfo> characters = event.get("characters");

        return WorldSnapshot.characterSelect(token, characters != null ? characters : List.of(),
                sessionService.getMaxSlots());
    }

    private WorldSnapshot buildInGameSnapshot(String token, String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) {
            sessionService.clearActiveCharacter(token);
            return buildCharacterSelectSnapshot(token);
        }

        GameEvent uiEvent = new GameEvent("ui.render_status");
        uiEvent.set("entityId", playerId);
        uiEvent.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        uiEvent.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        eventBus.fire("ui.render_status", uiEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = uiEvent.get("bars");
        @SuppressWarnings("unchecked")
        List<WorldSnapshot.BuffEntry> buffs = uiEvent.get("buffs");

        GameEvent actionsEvent = new GameEvent("ui.render_actions");
        actionsEvent.set("entityId", playerId);
        actionsEvent.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        eventBus.fire("ui.render_actions", actionsEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = actionsEvent.get("actions");

        return WorldSnapshot.inGame(token, playerId,
                new WorldSnapshot.ActionResult(true, "ok"),
                bars != null ? bars : List.of(),
                buffs != null ? buffs : List.of(),
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions != null ? actions : List.of(),
                List.of());
    }

    private WorldSnapshot.MapSnapshot buildMapSnapshot(Entity player) {
        if (!player.hasComponent("Position")) return null;
        Component pos = player.getComponent("Position");
        String mapId = pos.getString("map");
        Entity map = entityStore.get(mapId);
        if (map == null || !map.hasComponent("MapData")) return null;
        Component mapData = map.getComponent("MapData");
        return new WorldSnapshot.MapSnapshot(
                mapId, pos.getInt("x"), pos.getInt("y"),
                mapData.getInt("width"), mapData.getInt("height"));
    }

    private WorldSnapshot.CombatSnapshot buildCombatSnapshot(String playerId) {
        Entity player = entityStore.get(playerId);
        if (player == null) return null;
        for (String tag : player.getTags()) {
            if (tag.startsWith("combat:")) {
                String combatId = tag.substring(7);
                Entity combat = entityStore.get(combatId);
                if (combat == null || !combat.hasComponent("CombatState")) continue;
                Component state = combat.getComponent("CombatState");
                List<WorldSnapshot.CombatantInfo> combatants = new ArrayList<>();
                for (Entity c : entityStore.getByTag("combat:" + combatId)) {
                    if (!c.hasComponent("Health")) continue;
                    Component health = c.getComponent("Health");
                    String name = c.hasComponent("Name") ? c.getComponent("Name").getString("value") : c.getId();
                    String side = c.hasTag("player") ? "PLAYER" : "ENEMY";
                    combatants.add(new WorldSnapshot.CombatantInfo(
                            c.getId(), name, side,
                            health.getInt("hp"), health.getInt("maxHp"),
                            health.getInt("hp") > 0));
                }
                return new WorldSnapshot.CombatSnapshot(combatId, state.getString("phase"),
                        state.getInt("round"), combatants);
            }
        }
        return null;
    }
}
```

- [ ] **Step 6: Update application.yaml with session config**

Add to `backend/src/main/resources/application.yaml`:
```yaml
epic:
  mods-path: ../mods
  session-timeout-minutes: 30
  max-character-slots: 5
```

- [ ] **Step 7: Fix existing SnapshotControllerTest**

The old test used `/api/snapshot/player1` — update it to use the new `/api/snapshot` endpoint with headers. Replace test file or fix compilation.

- [ ] **Step 8: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SessionRoutingTest`
Expected: 3 tests PASS

Run: `cd /Volumes/workplace/epic/backend && mvn test`
Expected: All pass (fix broken old tests if needed)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/ backend/src/main/resources/application.yaml backend/src/test/java/com/epic/engine/snapshot/
git commit -m "feat: snapshot phase routing - character_select/create/in_game based on session state"
```

---

## Task 3: ScriptRuntime loadYaml API + Module Context

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java`
- Modify: `backend/src/main/java/com/epic/engine/module/ModuleLoader.java`
- Test: `backend/src/test/java/com/epic/engine/script/LoadYamlTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoadYamlTest {

    @TempDir Path tempDir;
    ScriptRuntime runtime;
    EventBus bus;
    EntityStore store;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        // Create a test YAML file
        Path entitiesDir = tempDir.resolve("entities/maps");
        Files.createDirectories(entitiesDir);
        Files.writeString(entitiesDir.resolve("test_map.yaml"), """
            id: test_map
            width: 5
            height: 5
            name: "测试地图"
            """);
    }

    @AfterEach
    void tearDown() { runtime.close(); }

    @Test
    void loadYaml_returnsMapData() {
        runtime.setModuleContext(tempDir);

        String script = """
            engine.on("test.load", 100, function(event) {
                var data = engine.loadYaml("entities/maps/test_map.yaml");
                event.set("mapId", data.get("id"));
                event.set("width", data.get("width"));
            });
            """;
        runtime.execute(script, "test.js");

        GameEvent event = new GameEvent("test.load");
        bus.fire("test.load", event);

        assertThat((String) event.get("mapId")).isEqualTo("test_map");
        assertThat(((Number) event.get("width")).intValue()).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=LoadYamlTest -pl . 2>&1 | tail -5`
Expected: Compilation failure (no setModuleContext or loadYaml method)

- [ ] **Step 3: Add loadYaml and module context to ScriptRuntime**

Modify `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java` — add `moduleContext` field and `loadYaml` method to EngineApi:

```java
package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ScriptRuntime implements AutoCloseable {

    private final Context context;
    private final EventBus bus;
    private final EntityStore store;
    private Path moduleContext;

    public ScriptRuntime(EventBus bus, EntityStore store) {
        this.bus = bus;
        this.store = store;
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> false)
                .build();
        bindApi();
    }

    private void bindApi() {
        Value bindings = context.getBindings("js");
        bindings.putMember("engine", new EngineApi());
        bindings.putMember("store", store);
    }

    public void setModuleContext(Path modulePath) {
        this.moduleContext = modulePath;
    }

    public Path getModuleContext() {
        return moduleContext;
    }

    public void execute(String script, String sourceName) {
        try {
            Source source = Source.newBuilder("js", script, sourceName).build();
            context.eval(source);
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute script: " + sourceName, e);
        }
    }

    @Override
    public void close() {
        context.close();
    }

    public class EngineApi {
        @HostAccess.Export
        public void on(String eventType, int priority, Value handler) {
            bus.on(eventType, priority, event -> handler.execute(event));
        }

        @HostAccess.Export
        public void fire(String eventType, GameEvent event) {
            bus.fire(eventType, event);
        }

        @HostAccess.Export
        public GameEvent newEvent(String type) {
            return new GameEvent(type);
        }

        @HostAccess.Export
        public Entity createEntity(String id) {
            return new Entity(id);
        }

        @HostAccess.Export
        public Component newComponent(String type) {
            return new Component(type);
        }

        @HostAccess.Export
        public Map<String, Object> loadYaml(String relativePath) {
            if (moduleContext == null) {
                throw new RuntimeException("No module context set — cannot load YAML");
            }
            Path yamlPath = moduleContext.resolve(relativePath);
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(yamlPath)) {
                return yaml.load(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load YAML: " + yamlPath, e);
            }
        }
    }
}
```

- [ ] **Step 4: Update ModuleLoader to set module context before loading handlers**

In `ModuleLoader.java`, modify `loadModule` method to set context:

```java
private void loadModule(ModuleDescriptor mod) throws IOException {
    Path handlersDir = mod.path().resolve("handlers");
    if (!Files.isDirectory(handlersDir)) return;

    runtime.setModuleContext(mod.path());

    try (Stream<Path> files = Files.walk(handlersDir)) {
        files.filter(p -> p.toString().endsWith(".js"))
             .sorted()
             .forEach(jsFile -> {
                 try {
                     String script = Files.readString(jsFile);
                     runtime.execute(script, mod.id() + "/" + modsPath.relativize(jsFile));
                 } catch (IOException e) {
                     throw new RuntimeException("Failed to load handler: " + jsFile, e);
                 }
             });
    }
}
```

- [ ] **Step 5: Run test**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=LoadYamlTest`
Expected: 1 test PASS

- [ ] **Step 6: Run full suite**

Run: `cd /Volumes/workplace/epic/backend && mvn test`
Expected: All pass

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/epic/engine/script/ScriptRuntime.java backend/src/main/java/com/epic/engine/module/ModuleLoader.java backend/src/test/java/com/epic/engine/script/LoadYamlTest.java
git commit -m "feat: add engine.loadYaml() API - JS handlers can read mod YAML files"
```

---

## Task 4: World Bootstrap Handler + Map Template

**Files:**
- Create: `mods/base-rules/entities/maps/world_map.yaml`
- Create: `mods/base-rules/handlers/world/bootstrap.js`
- Modify: `backend/src/main/java/com/epic/engine/core/EngineBootstrap.java`
- Test: `backend/src/test/java/com/epic/engine/world/WorldBootstrapTest.java`

- [ ] **Step 1: Create world_map.yaml**

Create `mods/base-rules/entities/maps/world_map.yaml`:
```yaml
id: world_map
name: "世界地图"
width: 10
height: 10
terrain:
  - "林林林草草草草山山山"
  - "林林草草路路草草山山"
  - "林草草路路路路草草山"
  - "草草路路城路路路草草"
  - "草路路路路路路路草沙"
  - "草草路路路路水水水沙"
  - "草草草路路水水水沙沙"
  - "草草草草路草水沙沙沙"
  - "林林草草路草草沙沙沙"
  - "林林林草路草草草沙沙"
spawn:
  x: 4
  y: 3
```

- [ ] **Step 2: Create bootstrap.js**

Create `mods/base-rules/handlers/world/bootstrap.js`:
```javascript
engine.on("world.init", 100, function(event) {
    var mapData = engine.loadYaml("entities/maps/world_map.yaml");

    var mapEntity = engine.createEntity(mapData.get("id"));
    var mapComponent = engine.newComponent("MapData");
    mapComponent.set("width", mapData.get("width"));
    mapComponent.set("height", mapData.get("height"));
    mapComponent.set("name", mapData.get("name"));
    mapEntity.addComponent(mapComponent);
    mapEntity.addTag("map");
    store.add(mapEntity);
});
```

- [ ] **Step 3: Modify EngineBootstrap to fire world.init after loading modules**

Add to `EngineBootstrap.boot()` after `moduleLoader.loadAll()`:

```java
@PostConstruct
public void boot() throws Exception {
    List<ModuleDescriptor> modules = moduleLoader.discoverModules();
    log.info("发现 {} 个模块，开始加载…", modules.size());
    for (ModuleDescriptor mod : modules) {
        schemaRegistry.loadFromModPath(mod.path());
    }
    moduleLoader.loadAll();
    log.info("引擎启动完成，已加载 {} 个 schema", schemaRegistry.getAll().size());

    // Fire world.init to let modules bootstrap world state
    eventBus.fire("world.init", new GameEvent("world.init"));
    log.info("世界初始化完成，实体数: {}", entityStore.all().size());
}
```

This requires injecting `EventBus` and `EntityStore` into EngineBootstrap. Update the constructor and EngineConfig accordingly.

- [ ] **Step 4: Write integration test**

```java
package com.epic.engine.world;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorldBootstrapTest {

    @Autowired EntityStore entityStore;

    @Test
    void worldMap_loadedOnStartup() {
        Entity map = entityStore.get("world_map");
        assertThat(map).isNotNull();
        assertThat(map.hasComponent("MapData")).isTrue();
        assertThat(map.getComponent("MapData").getInt("width")).isEqualTo(10);
        assertThat(map.getComponent("MapData").getInt("height")).isEqualTo(10);
        assertThat(map.hasTag("map")).isTrue();
    }
}
```

- [ ] **Step 5: Run test**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=WorldBootstrapTest`
Expected: 1 test PASS

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/entities/ mods/base-rules/handlers/world/ backend/src/main/java/com/epic/engine/core/EngineBootstrap.java backend/src/main/java/com/epic/engine/core/EngineConfig.java backend/src/test/java/com/epic/engine/world/
git commit -m "feat: world bootstrap - map loaded from YAML on engine start via JS handler"
```

---

## Task 5: Character Schemas

**Files:**
- Create: `mods/base-rules/schemas/main/character.schema.yaml`
- Create: `mods/base-rules/schemas/sub/class_warrior.schema.yaml`
- Create: `mods/base-rules/schemas/sub/class_mage.schema.yaml`
- Modify: `backend/src/main/java/com/epic/engine/module/Schema.java`
- Modify: `backend/src/main/java/com/epic/engine/module/SchemaRegistry.java`
- Test: `backend/src/test/java/com/epic/engine/module/CharacterSchemaTest.java`

- [ ] **Step 1: Create character.schema.yaml**

Create `mods/base-rules/schemas/main/character.schema.yaml`:
```yaml
id: character
type: main
required_subs: [class]
fields:
  - name: name
    type: string
    required: true
  - name: level
    type: int
    default: 1
base_components:
  Health: { hp: 100, maxHp: 100 }
  Mana: { mp: 50, maxMp: 50 }
  CombatStats: { attack: 5, defense: 3, speed: 5 }
  Position: { map: "world_map", x: 4, y: 3 }
```

- [ ] **Step 2: Create class_warrior.schema.yaml**

Create `mods/base-rules/schemas/sub/class_warrior.schema.yaml`:
```yaml
id: warrior
type: sub
category: class
compatible_with: [character]
label: "战士"
description: "近战物理攻击者"
modifiers:
  - field: "Health.maxHp"
    value: "+50"
  - field: "Health.hp"
    value: "+50"
  - field: "CombatStats.attack"
    value: "+3"
  - field: "CombatStats.defense"
    value: "+2"
```

- [ ] **Step 3: Create class_mage.schema.yaml**

Create `mods/base-rules/schemas/sub/class_mage.schema.yaml`:
```yaml
id: mage
type: sub
category: class
compatible_with: [character]
label: "法师"
description: "远程魔法攻击者"
modifiers:
  - field: "Mana.maxMp"
    value: "+30"
  - field: "Mana.mp"
    value: "+30"
  - field: "CombatStats.attack"
    value: "+5"
  - field: "CombatStats.speed"
    value: "+2"
```

- [ ] **Step 4: Extend Schema record to support new fields**

Replace `backend/src/main/java/com/epic/engine/module/Schema.java`:

```java
package com.epic.engine.module;

import java.util.List;
import java.util.Map;

public record Schema(
        String id,
        String type,
        List<String> compatibleWith,
        List<SchemaField> fields,
        List<String> requiredSubs,
        String category,
        String label,
        String description,
        Map<String, Map<String, Object>> baseComponents,
        List<SchemaModifier> modifiers
) {
    public record SchemaField(
            String name,
            String fieldType,
            boolean required,
            Object defaultValue
    ) {}

    public record SchemaModifier(
            String field,
            String value
    ) {}

    @SuppressWarnings("unchecked")
    public static Schema fromYaml(Map<String, Object> data) {
        String id = (String) data.get("id");
        String type = (String) data.get("type");

        List<String> compatible = data.containsKey("compatible_with")
                ? (List<String>) data.get("compatible_with")
                : List.of();

        List<Map<String, Object>> fieldDefs = data.containsKey("fields")
                ? (List<Map<String, Object>>) data.get("fields")
                : List.of();

        List<SchemaField> fields = fieldDefs.stream().map(f -> new SchemaField(
                (String) f.get("name"),
                (String) f.get("type"),
                Boolean.TRUE.equals(f.get("required")),
                f.get("default")
        )).toList();

        List<String> requiredSubs = data.containsKey("required_subs")
                ? (List<String>) data.get("required_subs")
                : List.of();

        String category = (String) data.get("category");
        String label = (String) data.get("label");
        String description = (String) data.get("description");

        Map<String, Map<String, Object>> baseComponents = data.containsKey("base_components")
                ? (Map<String, Map<String, Object>>) data.get("base_components")
                : Map.of();

        List<Map<String, Object>> modDefs = data.containsKey("modifiers")
                ? (List<Map<String, Object>>) data.get("modifiers")
                : List.of();
        List<SchemaModifier> modifiers = modDefs.stream().map(m -> new SchemaModifier(
                (String) m.get("field"),
                (String) m.get("value")
        )).toList();

        return new Schema(id, type, compatible, fields, requiredSubs, category, label, description, baseComponents, modifiers);
    }
}
```

- [ ] **Step 5: Add getByCategory to SchemaRegistry**

Add method to `SchemaRegistry.java`:

```java
public List<Schema> getByCategory(String category) {
    return schemas.values().stream()
            .filter(s -> category.equals(s.category()))
            .toList();
}
```

- [ ] **Step 6: Write test**

```java
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
```

- [ ] **Step 7: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=CharacterSchemaTest`
Expected: 3 tests PASS

- [ ] **Step 8: Commit**

```bash
git add mods/base-rules/schemas/ backend/src/main/java/com/epic/engine/module/Schema.java backend/src/main/java/com/epic/engine/module/SchemaRegistry.java backend/src/test/java/com/epic/engine/module/CharacterSchemaTest.java
git commit -m "feat: character + class schemas with required_subs, modifiers, base_components"
```

---

## Task 6: Character Select/Create JS Handler

**Files:**
- Create: `mods/base-rules/handlers/character/select.js`
- Modify: `backend/src/main/java/com/epic/engine/persistence/PersistenceService.java`
- Modify: `backend/src/main/java/com/epic/engine/persistence/EntityDataRepository.java`
- Test: `backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java`

- [ ] **Step 1: Add findByTag query to persistence layer**

Add to `EntityDataRepository.java`:
```java
@Query("SELECT e FROM EntityData e WHERE e.tagsJson LIKE %:tag%")
List<EntityData> findByTagContaining(@Param("tag") String tag);
```

Add to `PersistenceService.java`:
```java
public List<Entity> findByTag(String tag) {
    return repository.findByTagContaining(tag).stream()
            .map(this::deserialize)
            .toList();
}
```

- [ ] **Step 2: Expose PersistenceService and SchemaRegistry to JS**

Modify `ScriptRuntime.java` constructor to also accept and bind these:

Add a new binding approach — expose a `services` object:

In `EngineConfig.java`, after creating ScriptRuntime, bind additional services. Or simpler: add them to ScriptRuntime bindings.

Modify ScriptRuntime to accept additional bindings via a method:
```java
public void bindService(String name, Object service) {
    context.getBindings("js").putMember(name, service);
}
```

In `EngineBootstrap.boot()` or `EngineConfig`, bind:
```java
scriptRuntime.bindService("persistence", persistenceService);
scriptRuntime.bindService("schemas", schemaRegistry);
scriptRuntime.bindService("sessions", sessionService);
```

- [ ] **Step 3: Create select.js handler**

Create `mods/base-rules/handlers/character/select.js`:

```javascript
// List characters for a session
engine.on("session.list_characters", 100, function(event) {
    var token = event.get("sessionToken");
    var characters = event.get("characters");
    var entities = persistence.findByTag("session:" + token);

    for (var i = 0; i < entities.size(); i++) {
        var entity = entities.get(i);
        if (!entity.hasComponent("Character")) continue;
        var charComp = entity.getComponent("Character");
        characters.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$CharacterInfo"))(
            entity.getId(),
            charComp.getString("name"),
            charComp.has("level") ? charComp.getInt("level") : 1,
            charComp.has("classId") ? charComp.getString("classId") : "",
            charComp.has("classLabel") ? charComp.getString("classLabel") : ""
        ));
    }
});

// Start character creation — build form from schema
engine.on("action.create_character", 100, function(event) {
    var token = event.get("sessionToken");
    var session = sessions.getSession(token);
    // Mark that we're in create mode by setting a flag on the event
    // The snapshot service will detect this and return character_create phase
    event.set("phase_override", "character_create");

    var charSchema = schemas.get("character");
    var fields = new (Java.type("java.util.ArrayList"))();

    // Add fields from character schema
    var schemaFields = charSchema.fields();
    for (var i = 0; i < schemaFields.size(); i++) {
        var f = schemaFields.get(i);
        fields.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$FormField"))(
            f.name(), f.name() === "name" ? "名字" : f.name(), f.fieldType(), f.required(), null
        ));
    }

    // Add required sub selections
    var requiredSubs = charSchema.requiredSubs();
    for (var i = 0; i < requiredSubs.size(); i++) {
        var category = requiredSubs.get(i);
        var subSchemas = schemas.getByCategory(category);
        var options = new (Java.type("java.util.ArrayList"))();
        for (var j = 0; j < subSchemas.size(); j++) {
            var sub = subSchemas.get(j);
            options.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$FormOption"))(
                sub.id(), sub.label(), sub.description()
            ));
        }
        fields.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$FormField"))(
            category, category === "class" ? "职业" : category, "select", true, options
        ));
    }

    event.set("form", new (Java.type("com.epic.engine.snapshot.WorldSnapshot$FormData"))(fields));
});

// Confirm character creation
engine.on("action.confirm_character", 100, function(event) {
    var token = event.get("sessionToken");
    var name = event.get("name");
    var classId = event.get("class");

    // Generate unique character ID
    var charId = "char_" + java.lang.System.currentTimeMillis();

    // Load schemas
    var charSchema = schemas.get("character");
    var classSchema = schemas.get(classId);

    // Create entity with base components from character schema
    var entity = engine.createEntity(charId);

    var baseComps = charSchema.baseComponents();
    var compTypes = baseComps.keySet().iterator();
    while (compTypes.hasNext()) {
        var compType = compTypes.next();
        var compData = baseComps.get(compType);
        var comp = engine.newComponent(compType);
        var keys = compData.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.next();
            comp.set(key, compData.get(key));
        }
        entity.addComponent(comp);
    }

    // Apply class modifiers
    if (classSchema !== null && classSchema.modifiers() !== null) {
        var mods = classSchema.modifiers();
        for (var i = 0; i < mods.size(); i++) {
            var mod = mods.get(i);
            var parts = mod.field().split("\\.");
            var compName = parts[0];
            var fieldName = parts[1];
            var valueStr = mod.value();
            var comp = entity.getComponent(compName);
            if (comp !== null) {
                var current = comp.getInt(fieldName);
                if (valueStr.startsWith("+")) {
                    comp.set(fieldName, current + parseInt(valueStr.substring(1)));
                } else {
                    comp.set(fieldName, parseInt(valueStr));
                }
            }
        }
    }

    // Add Character component with metadata
    var charComp = engine.newComponent("Character");
    charComp.set("name", name);
    charComp.set("level", 1);
    charComp.set("classId", classId);
    charComp.set("classLabel", classSchema !== null ? classSchema.label() : classId);
    entity.addComponent(charComp);

    // Tag for persistence and session ownership
    entity.addTag("persistent");
    entity.addTag("player");
    entity.addTag("session:" + token);

    // Add to store and save
    store.add(entity);
    persistence.save(entity);

    // Set as active character
    sessions.setActiveCharacter(token, charId);
});

// Select existing character
engine.on("action.select_character", 100, function(event) {
    var token = event.get("sessionToken");
    var characterId = event.get("characterId");

    // Load character into store if not already there
    var entity = store.get(characterId);
    if (entity === null) {
        entity = persistence.load(characterId);
        if (entity !== null) {
            store.add(entity);
        }
    }

    if (entity !== null && entity.hasTag("session:" + token)) {
        sessions.setActiveCharacter(token, characterId);
    }
});

// Logout — clear active character
engine.on("action.logout", 100, function(event) {
    var token = event.get("sessionToken");
    sessions.clearActiveCharacter(token);
});
```

- [ ] **Step 4: Update SnapshotService to handle phase_override from create_character action**

In `SnapshotService.buildSnapshot`, after building the snapshot, check if the last action set a phase override. This is tricky because the event is fired in the controller. Better approach: have the controller check the event for form data and return character_create snapshot directly.

Modify `SnapshotController.performAction()`:

```java
@PostMapping("/action")
public WorldSnapshot performAction(
        @RequestHeader(value = "X-Session-Token", required = false) String token,
        @RequestBody Map<String, Object> request) {

    token = ensureToken(token);
    sessionService.touchSession(token);

    String type = (String) request.get("type");
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

    GameEvent actionEvent = new GameEvent("action." + type);
    actionEvent.set("sessionToken", token);
    actionEvent.set("type", type);
    params.forEach(actionEvent::set);

    var session = sessionService.getSession(token);
    if (session.activeCharacterId() != null) {
        actionEvent.set("playerId", session.activeCharacterId());
    }

    eventBus.fire("action." + type, actionEvent);

    // Check if handler wants to show a form
    if (actionEvent.has("form")) {
        WorldSnapshot.FormData form = actionEvent.get("form");
        return WorldSnapshot.characterCreate(token, form);
    }

    return snapshotService.buildSnapshot(token);
}
```

- [ ] **Step 5: Write integration test**

```java
package com.epic.engine.character;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CharacterFlowTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;
    @Autowired EntityStore entityStore;

    @Test
    void createCharacter_fullFlow() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Request character creation form
        var createReq = Map.of("type", "create_character", "params", Map.of());
        ResponseEntity<Map> createResp = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(createReq, headers), Map.class);
        assertThat(createResp.getBody().get("phase")).isEqualTo("character_create");
        assertThat(createResp.getBody().get("form")).isNotNull();

        // 2. Confirm character creation
        var confirmReq = Map.of("type", "confirm_character",
                "params", Map.of("name", "测试勇者", "class", "warrior"));
        ResponseEntity<Map> confirmResp = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(confirmReq, headers), Map.class);
        assertThat(confirmResp.getBody().get("phase")).isEqualTo("in_game");
        assertThat(confirmResp.getBody().get("playerId")).isNotNull();
    }

    @Test
    void selectCharacter_afterCreation() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create a character first
        var confirmReq = Map.of("type", "confirm_character",
                "params", Map.of("name", "选择测试", "class", "mage"));
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(confirmReq, headers), Map.class);

        // Logout
        var logoutReq = Map.of("type", "logout", "params", Map.of());
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(logoutReq, headers), Map.class);

        // Get snapshot — should be character_select with our character
        ResponseEntity<Map> snapshot = rest.exchange(
                "/api/snapshot", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(snapshot.getBody().get("phase")).isEqualTo("character_select");
        List<Map> characters = (List<Map>) snapshot.getBody().get("characters");
        assertThat(characters).isNotEmpty();
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=CharacterFlowTest`
Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
git add mods/base-rules/handlers/character/ backend/src/main/java/com/epic/engine/persistence/ backend/src/main/java/com/epic/engine/snapshot/ backend/src/main/java/com/epic/engine/script/ScriptRuntime.java backend/src/main/java/com/epic/engine/core/EngineBootstrap.java backend/src/main/java/com/epic/engine/core/EngineConfig.java backend/src/test/java/com/epic/engine/character/
git commit -m "feat: character create/select flow - schema-driven form, persistence, session binding"
```

---

## Task 7: UI Handlers (status_bars + actions)

**Files:**
- Create: `mods/base-rules/handlers/ui/status_bars.js`
- Create: `mods/base-rules/handlers/ui/actions.js`
- Test: `backend/src/test/java/com/epic/engine/ui/UiHandlersTest.java`

- [ ] **Step 1: Create status_bars.js**

Create `mods/base-rules/handlers/ui/status_bars.js`:
```javascript
engine.on("ui.render_status", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var bars = event.get("bars");

    if (entity.hasComponent("Health")) {
        var health = entity.getComponent("Health");
        bars.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$StatusBar"))(
            "hp", "生命", health.getInt("hp"), health.getInt("maxHp"), "#e74c3c", 1
        ));
    }

    if (entity.hasComponent("Mana")) {
        var mana = entity.getComponent("Mana");
        bars.add(new (Java.type("com.epic.engine.snapshot.WorldSnapshot$StatusBar"))(
            "mp", "法力", mana.getInt("mp"), mana.getInt("maxMp"), "#3498db", 2
        ));
    }
});
```

- [ ] **Step 2: Create actions.js**

Create `mods/base-rules/handlers/ui/actions.js`:
```javascript
engine.on("ui.render_actions", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var actions = event.get("actions");
    var HashMap = Java.type("java.util.HashMap");
    var ActionOption = Java.type("com.epic.engine.snapshot.WorldSnapshot$ActionOption");

    // If player has Position component → show movement actions
    if (entity.hasComponent("Position")) {
        var directions = [
            { dir: "NORTH", label: "向北" },
            { dir: "SOUTH", label: "向南" },
            { dir: "WEST", label: "向西" },
            { dir: "EAST", label: "向东" }
        ];
        for (var i = 0; i < directions.length; i++) {
            var params = new HashMap();
            params.put("direction", directions[i].dir);
            params.put("entityId", entityId);
            actions.add(new ActionOption("map_move", directions[i].label, params));
        }
    }

    // Logout action always available
    actions.add(new ActionOption("logout", "退出角色", new HashMap()));
});

// Handle map_move action
engine.on("action.map_move", 100, function(event) {
    var playerId = event.get("playerId");
    var direction = event.get("direction");
    var entityId = event.get("entityId") || playerId;

    var moveEvent = engine.newEvent("map.move");
    moveEvent.set("entityId", entityId);
    moveEvent.set("direction", direction);
    engine.fire("map.move", moveEvent);
});
```

- [ ] **Step 3: Write test**

```java
package com.epic.engine.ui;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.snapshot.WorldSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiHandlersTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path baseDir = Path.of("../mods/base-rules/handlers/ui");
        runtime.execute(Files.readString(baseDir.resolve("status_bars.js")), "status_bars.js");
        runtime.execute(Files.readString(baseDir.resolve("actions.js")), "actions.js");

        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 80);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 30);
        mana.set("maxMp", 50);
        player.addComponent(mana);
        Component pos = new Component("Position");
        pos.set("map", "world_map");
        pos.set("x", 4);
        pos.set("y", 3);
        player.addComponent(pos);
        store.add(player);
    }

    @AfterEach
    void tearDown() { runtime.close(); }

    @Test
    void statusBars_rendersHealthAndMana() {
        GameEvent event = new GameEvent("ui.render_status");
        event.set("entityId", "player1");
        event.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        event.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        bus.fire("ui.render_status", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = event.get("bars");
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).id()).isEqualTo("hp");
        assertThat(bars.get(0).current()).isEqualTo(80);
        assertThat(bars.get(1).id()).isEqualTo("mp");
    }

    @Test
    void actions_rendersMovementOptions() {
        GameEvent event = new GameEvent("ui.render_actions");
        event.set("entityId", "player1");
        event.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        bus.fire("ui.render_actions", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = event.get("actions");
        assertThat(actions).hasSizeGreaterThanOrEqualTo(4);
        assertThat(actions.stream().map(WorldSnapshot.ActionOption::type).toList())
                .contains("map_move");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=UiHandlersTest`
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/ui/ backend/src/test/java/com/epic/engine/ui/
git commit -m "feat: UI handlers - status bars (HP/MP) and movement actions from JS modules"
```

---

## Task 8: Frontend — Session Token + Phase Routing

**Files:**
- Modify: `frontend/src/api/client.js`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/components/CharacterSelect.vue`
- Create: `frontend/src/components/CharacterCreate.vue`

- [ ] **Step 1: Rewrite api/client.js with token management**

```javascript
const BASE_URL = '/api'

let sessionToken = localStorage.getItem('epic_session_token')

function getHeaders() {
    const headers = { 'Content-Type': 'application/json' }
    if (sessionToken) {
        headers['X-Session-Token'] = sessionToken
    }
    return headers
}

function saveToken(snapshot) {
    if (snapshot && snapshot.sessionToken) {
        sessionToken = snapshot.sessionToken
        localStorage.setItem('epic_session_token', sessionToken)
    }
}

export async function performAction(type, params = {}) {
    const response = await fetch(`${BASE_URL}/action`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ type, params })
    })
    const data = await response.json()
    saveToken(data)
    return data
}

export async function getSnapshot() {
    const response = await fetch(`${BASE_URL}/snapshot`, {
        headers: getHeaders()
    })
    const data = await response.json()
    saveToken(data)
    return data
}
```

- [ ] **Step 2: Create CharacterSelect.vue**

```vue
<template>
  <div class="character-select">
    <h2>选择角色</h2>
    <div class="card-grid">
      <div v-for="char in characters" :key="char.id"
           class="char-card" @click="$emit('select', char.id)">
        <div class="char-name">{{ char.name }}</div>
        <div class="char-info">Lv.{{ char.level }} {{ char.classLabel }}</div>
      </div>
      <div v-for="i in emptySlots" :key="'empty-'+i"
           class="char-card empty" @click="$emit('create')">
        <div class="plus">+</div>
        <div class="char-info">新建角色</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ characters: Array, maxSlots: Number })
defineEmits(['select', 'create'])

const emptySlots = computed(() => {
    const used = (props.characters || []).length
    const max = props.maxSlots || 5
    return Math.max(0, max - used)
})
</script>

<style scoped>
.character-select { padding: 2rem; display: flex; flex-direction: column; align-items: center; height: 100%; }
h2 { color: var(--text-color); margin-bottom: 1.5rem; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 1rem; width: 100%; max-width: 600px; }
.char-card { border: 2px solid var(--panel-border-color); padding: 1.5rem 1rem; cursor: pointer; text-align: center; }
.char-card:hover { border-color: var(--link-color); }
.char-card.empty { border-style: dashed; opacity: 0.6; }
.char-card.empty:hover { opacity: 1; }
.char-name { color: var(--text-color); font-size: 1.1em; font-weight: bold; margin-bottom: 0.5rem; }
.char-info { color: var(--text-color); opacity: 0.7; font-size: 0.9em; }
.plus { font-size: 2rem; color: var(--link-color); }
</style>
```

- [ ] **Step 3: Create CharacterCreate.vue**

```vue
<template>
  <div class="character-create">
    <h2>创建角色</h2>
    <form @submit.prevent="submit" class="create-form">
      <div v-for="field in form.fields" :key="field.name" class="form-field">
        <label>{{ field.label }}</label>
        <input v-if="field.type === 'text'" v-model="values[field.name]"
               :required="field.required" class="field-input" />
        <div v-else-if="field.type === 'select'" class="select-options">
          <div v-for="opt in field.options" :key="opt.value"
               class="option-card"
               :class="{ selected: values[field.name] === opt.value }"
               @click="values[field.name] = opt.value">
            <div class="opt-label">{{ opt.label }}</div>
            <div class="opt-desc">{{ opt.description }}</div>
          </div>
        </div>
      </div>
      <div class="form-actions">
        <span class="btn" @click="$emit('cancel')">取消</span>
        <button type="submit" class="btn btn-primary" :disabled="!isValid">确认创建</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({ form: Object })
const emit = defineEmits(['confirm', 'cancel'])

const values = reactive({})

const isValid = computed(() => {
    if (!props.form) return false
    return props.form.fields.every(f => !f.required || values[f.name])
})

function submit() {
    if (isValid.value) {
        emit('confirm', { ...values })
    }
}
</script>

<style scoped>
.character-create { padding: 2rem; display: flex; flex-direction: column; align-items: center; height: 100%; }
h2 { color: var(--text-color); margin-bottom: 1.5rem; }
.create-form { width: 100%; max-width: 500px; }
.form-field { margin-bottom: 1.5rem; }
.form-field label { display: block; color: var(--text-color); margin-bottom: 0.5rem; }
.field-input { width: 100%; padding: 0.5rem; background: var(--bg-color); border: 1px solid var(--panel-border-color); color: var(--text-color); font-size: 1rem; }
.select-options { display: flex; gap: 1rem; flex-wrap: wrap; }
.option-card { border: 2px solid var(--panel-border-color); padding: 1rem; cursor: pointer; min-width: 120px; text-align: center; }
.option-card:hover { border-color: var(--link-color); }
.option-card.selected { border-color: var(--link-color); background: rgba(78, 205, 196, 0.1); }
.opt-label { color: var(--text-color); font-weight: bold; margin-bottom: 0.3rem; }
.opt-desc { color: var(--text-color); opacity: 0.7; font-size: 0.85em; }
.form-actions { display: flex; justify-content: space-between; margin-top: 2rem; }
.btn { cursor: pointer; color: var(--link-color); padding: 0.5rem 1rem; }
.btn-primary { background: none; border: 1px solid var(--link-color); color: var(--link-color); }
.btn-primary:disabled { opacity: 0.3; cursor: not-allowed; }
</style>
```

- [ ] **Step 4: Rewrite App.vue with phase routing**

```vue
<template>
  <div class="app-container">
    <CharacterSelect v-if="phase === 'character_select'"
      :characters="snapshot.characters"
      :max-slots="snapshot.maxSlots"
      @select="selectCharacter"
      @create="createCharacter" />
    <CharacterCreate v-else-if="phase === 'character_create'"
      :form="snapshot.form"
      @confirm="confirmCharacter"
      @cancel="cancelCreate" />
    <SnapshotRenderer v-else-if="phase === 'in_game'"
      :snapshot="snapshot"
      @action="handleAction" />
    <div v-else class="loading">加载中...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import CharacterSelect from './components/CharacterSelect.vue'
import CharacterCreate from './components/CharacterCreate.vue'
import SnapshotRenderer from './components/SnapshotRenderer.vue'
import { performAction, getSnapshot } from './api/client.js'

const snapshot = ref(null)
const phase = computed(() => snapshot.value?.phase || 'loading')

async function selectCharacter(characterId) {
    snapshot.value = await performAction('select_character', { characterId })
}

async function createCharacter() {
    snapshot.value = await performAction('create_character')
}

async function confirmCharacter(values) {
    snapshot.value = await performAction('confirm_character', values)
}

async function cancelCreate() {
    snapshot.value = await getSnapshot()
}

async function handleAction(action) {
    snapshot.value = await performAction(action.type, action.params || {})
}

onMounted(async () => {
    snapshot.value = await getSnapshot()
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}
.loading { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--text-color); }
</style>
```

- [ ] **Step 5: Build frontend**

Run: `cd /Volumes/workplace/epic/frontend && npm run build`
Expected: Build success

- [ ] **Step 6: Commit**

```bash
git add frontend/src/
git commit -m "feat: frontend session + character select/create UI with phase routing"
```

---

## Task 9: End-to-End Verification

- [ ] **Step 1: Start the app**

Run: `cd /Volumes/workplace/epic && ./start.sh`

- [ ] **Step 2: Open browser and verify flow**

1. Open `http://localhost:5173`
2. Should see character select (empty, with "新建角色" cards)
3. Click "新建角色" → form appears with name field + class select
4. Fill in name, pick a class, click 确认创建
5. Should transition to in_game view with HP/MP bars and movement buttons
6. Click a movement button → position should update in map info

- [ ] **Step 3: Verify persistence**

1. Refresh the page → should still be in-game (session token persists)
2. Click "退出角色" → should go back to character select with created character shown
3. Click the character card → should re-enter game

- [ ] **Step 4: Run full test suite**

Run: `cd /Volumes/workplace/epic/backend && mvn test`
Expected: All tests pass

- [ ] **Step 5: Commit any fixes**

```bash
git add -A
git commit -m "fix: end-to-end adjustments for character flow"
```

---

## Notes for Implementation

1. **Task 6 is the most complex** — the JS handler needs to construct Java record instances using `Java.type()`. This is GraalJS-specific syntax. If `Java.type()` fails with `allowHostClassLookup(className -> false)`, you'll need to change the ScriptRuntime to allow lookups for `com.epic.engine.snapshot.WorldSnapshot$*` classes, or provide factory methods on EngineApi instead.

2. **Alternative to Java.type in JS**: Instead of constructing records in JS, have EngineApi provide factory methods:
   ```java
   @HostAccess.Export
   public WorldSnapshot.StatusBar newStatusBar(String id, String label, int current, int max, String color, int priority) {
       return new WorldSnapshot.StatusBar(id, label, current, max, color, priority);
   }
   ```
   This avoids the class lookup problem entirely.

3. **PersistenceService.findByTag** uses a LIKE query on JSON string — this works for simple cases but isn't robust. For production you'd want a proper join table. Fine for now.

4. **The old `/api/snapshot/{playerId}` endpoint** is replaced by `/api/snapshot` (no path variable). The old `SnapshotControllerTest` needs updating.

5. **EngineBootstrap** needs `EventBus` and `EntityStore` injected. Update `EngineConfig.java` to pass them to the constructor.
