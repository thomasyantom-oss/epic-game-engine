# 事件驱动微内核引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded game engine with an event-driven microkernel where all game rules are JS modules, entities are ECS objects with modifier chains, and the frontend is a stateless snapshot renderer.

**Architecture:** Engine core = Event Bus + Entity Store + GraalJS Script Runtime. All game logic lives in `mods/base-rules/handlers/*.js`. Entities = Main Component + Sub Components with modifier chain for computed state. Frontend posts actions, receives world snapshots, renders them.

**Tech Stack:** Java 21, Spring Boot 3.4, GraalJS (org.graalvm.js), H2, SnakeYAML, Vue 3, Vite 6

---

## Phase Overview

This refactor is broken into 6 phases. Each phase produces working software and can be tested independently:

1. **Engine Core** — Event Bus + Entity Store + Script Runtime (no game rules yet)
2. **Module System** — Load mods, parse schemas, register JS handlers, hot-reload
3. **Base Rules: Combat** — Rewrite combat as JS modules on the new engine
4. **Base Rules: Map & Scene** — Rewrite map/scene as JS modules
5. **Frontend Rewrite** — Stateless snapshot renderer
6. **Persistence & Generator** — Save/load persistent entities, schema-driven generation

---

## File Structure (New)

```
backend/src/main/java/com/epic/engine/
├── EpicApplication.java            (keep, minor changes)
├── config/CorsConfig.java          (keep)
├── core/
│   ├── EventBus.java               (NEW — fire, on, cancel)
│   ├── GameEvent.java              (NEW — event data carrier)
│   ├── HandlerRegistration.java    (NEW — priority + callback ref)
│   ├── EntityStore.java            (NEW — ECS entity management)
│   ├── Entity.java                 (NEW — component bag + tags + modifiers)
│   ├── Component.java              (NEW — base component interface)
│   ├── Modifier.java               (NEW — priority + source + apply fn)
│   ├── ModifierChain.java          (NEW — ordered apply + recalculate)
│   └── TagIndex.java               (NEW — tag→entity fast lookup)
├── script/
│   ├── ScriptRuntime.java          (NEW — GraalJS engine + sandbox)
│   ├── ScriptApiBinding.java       (NEW — Java API exposed to JS)
│   └── HotReloader.java            (NEW — file watcher for handler reload)
├── module/
│   ├── ModuleLoader.java           (NEW — replaces ModLoader, loads JS handlers)
│   ├── ModuleDescriptor.java       (NEW — replaces ModDescriptor, adds handler paths)
│   ├── SchemaRegistry.java         (NEW — loads/validates schemas)
│   └── Schema.java                 (NEW — parsed schema: fields, compatible_with)
├── snapshot/
│   ├── SnapshotService.java        (NEW — builds world snapshot for frontend)
│   ├── WorldSnapshot.java          (NEW — DTO: status_bars, map, combat, actions)
│   └── SnapshotController.java     (NEW — single /api/action endpoint)
├── persistence/
│   ├── PersistenceService.java     (NEW — save/load persistent entities)
│   └── EntityData.java             (NEW — JPA entity for serialized component data)
├── generator/
│   ├── GeneratorService.java       (NEW — schema-driven object assembly)
│   └── GeneratorRequest.java       (NEW — main + sub + level params)
└── debug/
    ├── DebugController.java        (keep, adapt to new entity model)
    └── GameEventLog.java           (keep)

frontend/src/
├── App.vue                         (REWRITE — stateless snapshot renderer)
├── main.js                         (keep)
├── api/client.js                   (REWRITE — single action + snapshot fetch)
├── components/
│   ├── SnapshotRenderer.vue        (NEW — top-level dispatcher)
│   ├── StatusBars.vue              (NEW — data-driven bars with priority/fold)
│   ├── MapView.vue                 (NEW — replaces MapGrid, data-driven)
│   ├── CombatView.vue              (REWRITE — reads from snapshot, no composable)
│   ├── ActionPanel.vue             (NEW — replaces NavigationPanel)
│   ├── GameLog.vue                 (REWRITE — reads log from snapshot)
│   ├── TabPanel.vue                (keep)
│   └── TextRenderer.vue            (keep)
├── composables/
│   └── useSettings.js              (keep — font/theme is client-only)
└── styles/
    ├── base.css                    (keep)
    └── variables.css               (keep)

mods/
├── base-rules/                     (NEW — replaces mods/base/)
│   ├── mod.yaml
│   ├── schemas/
│   │   ├── main/
│   │   │   ├── character.schema.yaml
│   │   │   ├── creature.schema.yaml
│   │   │   ├── equipment.schema.yaml
│   │   │   └── item.schema.yaml
│   │   └── sub/
│   │       ├── class.schema.yaml
│   │       └── mutation.schema.yaml
│   ├── handlers/
│   │   ├── combat/
│   │   │   ├── combat_flow.js
│   │   │   ├── initiative.js
│   │   │   ├── damage_calc.js
│   │   │   ├── skill_cost.js
│   │   │   └── death_check.js
│   │   ├── map/
│   │   │   ├── movement.js
│   │   │   └── pathfinding.js
│   │   └── ui/
│   │       └── status_bars.js
│   └── entities/
│       ├── player_template.yaml
│       └── monsters/
│           └── goblin.yaml
└── base/                           (OLD — will be removed after migration)
```

---

## Phase 1: Engine Core

### Task 1.1: Add GraalJS Dependency

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Add GraalJS dependency to pom.xml**

```xml
<!-- Add inside <dependencies> section, after snakeyaml -->
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.1.1</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.1.1</version>
    <type>pom</type>
</dependency>
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Volumes/workplace/epic/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/pom.xml
git commit -m "$(cat <<'EOF'
feat: add GraalJS dependency for script runtime
EOF
)"
```

---

### Task 1.2: Event Bus

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/GameEvent.java`
- Create: `backend/src/main/java/com/epic/engine/core/HandlerRegistration.java`
- Create: `backend/src/main/java/com/epic/engine/core/EventBus.java`
- Test: `backend/src/test/java/com/epic/engine/core/EventBusTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {

    EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
    }

    @Test
    void fireEvent_callsRegisteredHandler() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 100, event -> results.add("handled"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("handled");
    }

    @Test
    void handlers_executedInPriorityOrder_lowNumberFirst() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 200, event -> results.add("second"));
        bus.on("test.event", 100, event -> results.add("first"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("first", "second");
    }

    @Test
    void cancel_stopsSubsequentHandlers() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 100, event -> {
            results.add("first");
            event.cancel();
        });
        bus.on("test.event", 200, event -> results.add("should not run"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("first");
    }

    @Test
    void differentEvents_dontInterfere() {
        var results = new java.util.ArrayList<String>();
        bus.on("event.a", 100, event -> results.add("a"));
        bus.on("event.b", 100, event -> results.add("b"));

        bus.fire("event.a", new GameEvent("event.a"));

        assertThat(results).containsExactly("a");
    }

    @Test
    void eventData_accessibleInHandler() {
        var results = new java.util.ArrayList<Object>();
        bus.on("test.event", 100, event -> results.add(event.get("value")));

        GameEvent event = new GameEvent("test.event");
        event.set("value", 42);
        bus.fire("test.event", event);

        assertThat(results).containsExactly(42);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EventBusTest -pl . 2>&1 | tail -5`
Expected: Compilation failure (classes don't exist yet)

- [ ] **Step 3: Implement GameEvent**

```java
package com.epic.engine.core;

import java.util.HashMap;
import java.util.Map;

public class GameEvent {

    private final String type;
    private final Map<String, Object> data = new HashMap<>();
    private boolean cancelled = false;

    public GameEvent(String type) {
        this.type = type;
    }

    public String getType() { return type; }

    public void set(String key, Object value) { data.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) data.get(key); }

    public boolean has(String key) { return data.containsKey(key); }

    public void cancel() { cancelled = true; }

    public boolean isCancelled() { return cancelled; }
}
```

- [ ] **Step 4: Implement HandlerRegistration**

```java
package com.epic.engine.core;

import java.util.function.Consumer;

public record HandlerRegistration(
        String eventType,
        int priority,
        Consumer<GameEvent> handler
) implements Comparable<HandlerRegistration> {

    @Override
    public int compareTo(HandlerRegistration other) {
        return Integer.compare(this.priority, other.priority);
    }
}
```

- [ ] **Step 5: Implement EventBus**

```java
package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    private final Map<String, List<HandlerRegistration>> handlers = new ConcurrentHashMap<>();

    public void on(String eventType, int priority, Consumer<GameEvent> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new HandlerRegistration(eventType, priority, handler));
        handlers.get(eventType).sort(HandlerRegistration::compareTo);
    }

    public void fire(String eventType, GameEvent event) {
        List<HandlerRegistration> registrations = handlers.get(eventType);
        if (registrations == null) return;

        for (HandlerRegistration reg : registrations) {
            if (event.isCancelled()) break;
            reg.handler().accept(event);
        }
    }

    public void clear() {
        handlers.clear();
    }

    public void removeHandlersFor(String eventType) {
        handlers.remove(eventType);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EventBusTest -pl .`
Expected: 5 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/core/ backend/src/test/java/com/epic/engine/core/
git commit -m "$(cat <<'EOF'
feat: implement EventBus core - fire, on, cancel, priority ordering
EOF
)"
```

---

### Task 1.3: Entity + Component + Tags

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/Component.java`
- Create: `backend/src/main/java/com/epic/engine/core/Entity.java`
- Create: `backend/src/main/java/com/epic/engine/core/TagIndex.java`
- Test: `backend/src/test/java/com/epic/engine/core/EntityTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {

    @Test
    void addAndGetComponent() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);

        entity.addComponent(health);

        assertThat(entity.hasComponent("Health")).isTrue();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    @Test
    void removeComponent() {
        Entity entity = new Entity("player1");
        entity.addComponent(new Component("Mana"));

        entity.removeComponent("Mana");

        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void tagOperations() {
        Entity entity = new Entity("goblin1");
        entity.addTag("humanoid");
        entity.addTag("goblin");

        assertThat(entity.hasTag("humanoid")).isTrue();
        assertThat(entity.hasTag("undead")).isFalse();
        assertThat(entity.getTags()).containsExactlyInAnyOrder("humanoid", "goblin");
    }

    @Test
    void tagIndex_findByTag() {
        TagIndex index = new TagIndex();
        Entity e1 = new Entity("goblin1");
        e1.addTag("humanoid");
        Entity e2 = new Entity("skeleton1");
        e2.addTag("undead");
        Entity e3 = new Entity("goblin2");
        e3.addTag("humanoid");

        index.register(e1);
        index.register(e2);
        index.register(e3);

        assertThat(index.getByTag("humanoid")).containsExactlyInAnyOrder(e1, e3);
        assertThat(index.getByTag("undead")).containsExactly(e2);
    }

    @Test
    void tagIndex_updatesOnTagChange() {
        TagIndex index = new TagIndex();
        Entity e1 = new Entity("goblin1");
        e1.addTag("humanoid");
        index.register(e1);

        e1.removeTag("humanoid");
        index.reindex(e1);

        assertThat(index.getByTag("humanoid")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EntityTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement Component**

```java
package com.epic.engine.core;

import java.util.HashMap;
import java.util.Map;

public class Component {

    private final String type;
    private final Map<String, Object> data = new HashMap<>();

    public Component(String type) {
        this.type = type;
    }

    public String getType() { return type; }

    public void set(String key, Object value) { data.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) data.get(key); }

    public int getInt(String key) { return ((Number) data.get(key)).intValue(); }

    public double getDouble(String key) { return ((Number) data.get(key)).doubleValue(); }

    public String getString(String key) { return (String) data.get(key); }

    public boolean has(String key) { return data.containsKey(key); }

    public void remove(String key) { data.remove(key); }

    public Map<String, Object> getAll() { return Map.copyOf(data); }

    public Component copy() {
        Component copy = new Component(type);
        copy.data.putAll(this.data);
        return copy;
    }
}
```

- [ ] **Step 4: Implement Entity**

```java
package com.epic.engine.core;

import java.util.*;

public class Entity {

    private final String id;
    private final Map<String, Component> components = new LinkedHashMap<>();
    private final Set<String> tags = new LinkedHashSet<>();

    public Entity(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public void addComponent(Component component) {
        components.put(component.getType(), component);
    }

    public void removeComponent(String type) {
        components.remove(type);
    }

    public Component getComponent(String type) {
        return components.get(type);
    }

    public boolean hasComponent(String type) {
        return components.containsKey(type);
    }

    public Collection<Component> getAllComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    public void addTag(String tag) { tags.add(tag); }

    public void removeTag(String tag) { tags.remove(tag); }

    public boolean hasTag(String tag) { return tags.contains(tag); }

    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
}
```

- [ ] **Step 5: Implement TagIndex**

```java
package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagIndex {

    private final Map<String, Set<Entity>> tagToEntities = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> entityTags = new ConcurrentHashMap<>();

    public void register(Entity entity) {
        entityTags.put(entity.getId(), new HashSet<>(entity.getTags()));
        for (String tag : entity.getTags()) {
            tagToEntities.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(entity);
        }
    }

    public void unregister(Entity entity) {
        Set<String> oldTags = entityTags.remove(entity.getId());
        if (oldTags != null) {
            for (String tag : oldTags) {
                Set<Entity> set = tagToEntities.get(tag);
                if (set != null) {
                    set.remove(entity);
                    if (set.isEmpty()) tagToEntities.remove(tag);
                }
            }
        }
    }

    public void reindex(Entity entity) {
        unregister(entity);
        register(entity);
    }

    public Set<Entity> getByTag(String tag) {
        return Collections.unmodifiableSet(
                tagToEntities.getOrDefault(tag, Collections.emptySet()));
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EntityTest -pl .`
Expected: 5 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/core/Component.java backend/src/main/java/com/epic/engine/core/Entity.java backend/src/main/java/com/epic/engine/core/TagIndex.java backend/src/test/java/com/epic/engine/core/EntityTest.java
git commit -m "$(cat <<'EOF'
feat: implement Entity with ECS components and TagIndex
EOF
)"
```

---

### Task 1.4: Modifier Chain

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/Modifier.java`
- Create: `backend/src/main/java/com/epic/engine/core/ModifierChain.java`
- Test: `backend/src/test/java/com/epic/engine/core/ModifierChainTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierChainTest {

    @Test
    void singleModifier_appliesEffect() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        entity.addComponent(health);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("ring_hp", "ring_a", 100, e -> {
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + 20);
            e.getComponent("Health").set("maxHp", e.getComponent("Health").getInt("maxHp") + 20);
        }));
        chain.recalculate();

        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(120);
        assertThat(entity.getComponent("Health").getInt("maxHp")).isEqualTo(120);
    }

    @Test
    void modifiers_applyInPriorityOrder() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 50);
        entity.addComponent(mana);

        ModifierChain chain = new ModifierChain(entity);
        // Priority 100: mp +20
        chain.addModifier(new Modifier("ring_mp", "ring_a", 100, e -> {
            e.getComponent("Mana").set("mp", e.getComponent("Mana").getInt("mp") + 20);
        }));
        // Priority 200: convert mp to hp, remove mana
        chain.addModifier(new Modifier("blood_convert", "blood_staff", 200, e -> {
            int mp = e.getComponent("Mana").getInt("mp");
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + mp);
            e.removeComponent("Mana");
        }));
        chain.recalculate();

        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(170);
        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void removeModifierBySource_recalculates() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 50);
        entity.addComponent(mana);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("ring_mp", "ring_a", 100, e -> {
            e.getComponent("Mana").set("mp", e.getComponent("Mana").getInt("mp") + 20);
        }));
        chain.addModifier(new Modifier("blood_convert", "blood_staff", 200, e -> {
            int mp = e.getComponent("Mana").getInt("mp");
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + mp);
            e.removeComponent("Mana");
        }));
        chain.recalculate();

        // Now remove ring_a
        chain.removeBySource("ring_a");
        chain.recalculate();

        // blood_staff still converts, but no +20 from ring
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(150);
        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void recalculate_restoresBaseStateBetweenRuns() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("buff", "potion", 100, e -> {
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + 50);
        }));
        chain.recalculate();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(150);

        // Remove the buff, recalculate — should go back to base
        chain.removeBySource("potion");
        chain.recalculate();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(100);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ModifierChainTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement Modifier**

```java
package com.epic.engine.core;

import java.util.function.Consumer;

public record Modifier(
        String id,
        String source,
        int priority,
        Consumer<Entity> apply
) implements Comparable<Modifier> {

    @Override
    public int compareTo(Modifier other) {
        return Integer.compare(this.priority, other.priority);
    }
}
```

- [ ] **Step 4: Implement ModifierChain**

```java
package com.epic.engine.core;

import java.util.*;

public class ModifierChain {

    private final Entity entity;
    private final List<Modifier> modifiers = new ArrayList<>();
    private final Map<String, Component> baseState = new LinkedHashMap<>();
    private final Set<String> baseTags = new LinkedHashSet<>();

    public ModifierChain(Entity entity) {
        this.entity = entity;
        snapshotBase();
    }

    private void snapshotBase() {
        baseState.clear();
        for (Component c : entity.getAllComponents()) {
            baseState.put(c.getType(), c.copy());
        }
        baseTags.clear();
        baseTags.addAll(entity.getTags());
    }

    public void setBase() {
        snapshotBase();
    }

    public void addModifier(Modifier modifier) {
        modifiers.add(modifier);
        modifiers.sort(Modifier::compareTo);
    }

    public void removeBySource(String source) {
        modifiers.removeIf(m -> m.source().equals(source));
    }

    public void removeById(String id) {
        modifiers.removeIf(m -> m.id().equals(id));
    }

    public void recalculate() {
        // Restore base state
        List<String> currentTypes = new ArrayList<>();
        for (Component c : entity.getAllComponents()) {
            currentTypes.add(c.getType());
        }
        for (String type : currentTypes) {
            entity.removeComponent(type);
        }
        for (Map.Entry<String, Component> entry : baseState.entrySet()) {
            entity.addComponent(entry.getValue().copy());
        }
        // Restore base tags
        for (String tag : new ArrayList<>(entity.getTags())) {
            entity.removeTag(tag);
        }
        for (String tag : baseTags) {
            entity.addTag(tag);
        }

        // Apply modifiers in priority order
        for (Modifier mod : modifiers) {
            mod.apply().accept(entity);
        }
    }

    public List<Modifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ModifierChainTest -pl .`
Expected: 4 tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/core/Modifier.java backend/src/main/java/com/epic/engine/core/ModifierChain.java backend/src/test/java/com/epic/engine/core/ModifierChainTest.java
git commit -m "$(cat <<'EOF'
feat: implement ModifierChain - priority-ordered effects with base state restore
EOF
)"
```

---

### Task 1.5: Entity Store

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/EntityStore.java`
- Test: `backend/src/test/java/com/epic/engine/core/EntityStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntityStoreTest {

    EntityStore store;

    @BeforeEach
    void setUp() {
        store = new EntityStore();
    }

    @Test
    void addAndGet() {
        Entity entity = new Entity("player1");
        store.add(entity);

        assertThat(store.get("player1")).isSameAs(entity);
    }

    @Test
    void remove() {
        Entity entity = new Entity("player1");
        store.add(entity);
        store.remove("player1");

        assertThat(store.get("player1")).isNull();
    }

    @Test
    void getByTag() {
        Entity e1 = new Entity("goblin1");
        e1.addTag("enemy");
        Entity e2 = new Entity("player1");
        e2.addTag("player");
        Entity e3 = new Entity("goblin2");
        e3.addTag("enemy");

        store.add(e1);
        store.add(e2);
        store.add(e3);

        assertThat(store.getByTag("enemy")).containsExactlyInAnyOrder(e1, e3);
    }

    @Test
    void getByComponent() {
        Entity e1 = new Entity("player1");
        e1.addComponent(new Component("Health"));
        e1.addComponent(new Component("Mana"));
        Entity e2 = new Entity("goblin1");
        e2.addComponent(new Component("Health"));

        store.add(e1);
        store.add(e2);

        assertThat(store.getByComponent("Mana")).containsExactly(e1);
        assertThat(store.getByComponent("Health")).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void all_returnsAllEntities() {
        store.add(new Entity("a"));
        store.add(new Entity("b"));

        assertThat(store.all()).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EntityStoreTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement EntityStore**

```java
package com.epic.engine.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EntityStore {

    private final Map<String, Entity> entities = new ConcurrentHashMap<>();
    private final TagIndex tagIndex = new TagIndex();

    public void add(Entity entity) {
        entities.put(entity.getId(), entity);
        tagIndex.register(entity);
    }

    public Entity get(String id) {
        return entities.get(id);
    }

    public void remove(String id) {
        Entity entity = entities.remove(id);
        if (entity != null) {
            tagIndex.unregister(entity);
        }
    }

    public Set<Entity> getByTag(String tag) {
        return tagIndex.getByTag(tag);
    }

    public Set<Entity> getByComponent(String componentType) {
        return entities.values().stream()
                .filter(e -> e.hasComponent(componentType))
                .collect(Collectors.toSet());
    }

    public Collection<Entity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public void reindexTags(Entity entity) {
        tagIndex.reindex(entity);
    }

    public void clear() {
        entities.clear();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EntityStoreTest -pl .`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/core/EntityStore.java backend/src/test/java/com/epic/engine/core/EntityStoreTest.java
git commit -m "$(cat <<'EOF'
feat: implement EntityStore with tag and component queries
EOF
)"
```

---

### Task 1.6: Script Runtime (GraalJS)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java`
- Test: `backend/src/test/java/com/epic/engine/script/ScriptRuntimeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScriptRuntimeTest {

    ScriptRuntime runtime;
    EventBus bus;
    EntityStore store;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void executeScript_registersHandler() {
        String script = """
            engine.on("test.hello", 100, function(event) {
                event.set("greeting", "world");
            });
            """;
        runtime.execute(script, "test_handler.js");

        GameEvent event = new GameEvent("test.hello");
        bus.fire("test.hello", event);

        assertThat(event.get("greeting")).isEqualTo("world");
    }

    @Test
    void script_canAccessEntityStore() {
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        player.addComponent(health);
        store.add(player);

        String script = """
            engine.on("test.damage", 100, function(event) {
                var entity = store.get("player1");
                var hp = entity.getComponent("Health").getInt("hp");
                entity.getComponent("Health").set("hp", hp - 10);
            });
            """;
        runtime.execute(script, "damage.js");

        bus.fire("test.damage", new GameEvent("test.damage"));

        assertThat(player.getComponent("Health").getInt("hp")).isEqualTo(90);
    }

    @Test
    void script_canFireEvents() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.chained", 100, event -> results.add("chained"));

        String script = """
            engine.on("test.trigger", 100, function(event) {
                engine.fire("test.chained", engine.newEvent("test.chained"));
            });
            """;
        runtime.execute(script, "chain.js");

        bus.fire("test.trigger", new GameEvent("test.trigger"));

        assertThat(results).containsExactly("chained");
    }

    @Test
    void script_canCancelEvent() {
        var results = new java.util.ArrayList<String>();

        String script = """
            engine.on("test.cancel", 50, function(event) {
                event.cancel();
            });
            """;
        runtime.execute(script, "canceller.js");
        bus.on("test.cancel", 100, event -> results.add("should not run"));

        bus.fire("test.cancel", new GameEvent("test.cancel"));

        assertThat(results).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ScriptRuntimeTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement ScriptRuntime**

```java
package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

public class ScriptRuntime implements AutoCloseable {

    private final Context context;
    private final EventBus bus;
    private final EntityStore store;

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
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ScriptRuntimeTest -pl .`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/script/ backend/src/test/java/com/epic/engine/script/
git commit -m "$(cat <<'EOF'
feat: implement ScriptRuntime - GraalJS sandbox with engine/store API bindings
EOF
)"
```

---

## Phase 2: Module System

### Task 2.1: Module Loader (loads JS handlers from mods)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/module/ModuleDescriptor.java`
- Create: `backend/src/main/java/com/epic/engine/module/ModuleLoader.java`
- Test: `backend/src/test/java/com/epic/engine/module/ModuleLoaderTest.java`
- Create: test fixture `backend/src/test/resources/test-mods/test-mod/mod.yaml`
- Create: test fixture `backend/src/test/resources/test-mods/test-mod/handlers/hello.js`

- [ ] **Step 1: Create test mod fixture**

Create `backend/src/test/resources/test-mods/test-mod/mod.yaml`:
```yaml
id: test-mod
name: "Test Module"
version: "1.0.0"
load-order: 0
dependencies: []
```

Create `backend/src/test/resources/test-mods/test-mod/handlers/hello.js`:
```javascript
engine.on("test.hello", 100, function(event) {
    event.set("message", "hello from module");
});
```

- [ ] **Step 2: Write the failing test**

```java
package com.epic.engine.module;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleLoaderTest {

    ModuleLoader loader;
    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        Path modsPath = Path.of("src/test/resources/test-mods");
        loader = new ModuleLoader(modsPath, runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void discoverAndLoadModules() throws Exception {
        List<ModuleDescriptor> mods = loader.discoverModules();

        assertThat(mods).hasSize(1);
        assertThat(mods.get(0).id()).isEqualTo("test-mod");
    }

    @Test
    void loadedHandlers_registerOnEventBus() throws Exception {
        loader.loadAll();

        GameEvent event = new GameEvent("test.hello");
        bus.fire("test.hello", event);

        assertThat(event.get("message")).isEqualTo("hello from module");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ModuleLoaderTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 4: Implement ModuleDescriptor**

```java
package com.epic.engine.module;

import java.nio.file.Path;
import java.util.List;

public record ModuleDescriptor(
        String id,
        String name,
        String version,
        int loadOrder,
        List<String> dependencies,
        Path path
) {}
```

- [ ] **Step 5: Implement ModuleLoader**

```java
package com.epic.engine.module;

import com.epic.engine.script.ScriptRuntime;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class ModuleLoader {

    private final Path modsPath;
    private final ScriptRuntime runtime;

    public ModuleLoader(Path modsPath, ScriptRuntime runtime) {
        this.modsPath = modsPath.toAbsolutePath();
        this.runtime = runtime;
    }

    @SuppressWarnings("unchecked")
    public List<ModuleDescriptor> discoverModules() throws IOException {
        List<ModuleDescriptor> modules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path modFile = entry.resolve("mod.yaml");
                    if (Files.exists(modFile)) {
                        modules.add(parseDescriptor(modFile, entry));
                    }
                }
            }
        }
        modules.sort(Comparator.comparingInt(ModuleDescriptor::loadOrder));
        return modules;
    }

    public void loadAll() throws IOException {
        List<ModuleDescriptor> modules = discoverModules();
        for (ModuleDescriptor mod : modules) {
            loadModule(mod);
        }
    }

    private void loadModule(ModuleDescriptor mod) throws IOException {
        Path handlersDir = mod.path().resolve("handlers");
        if (!Files.isDirectory(handlersDir)) return;

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

    @SuppressWarnings("unchecked")
    private ModuleDescriptor parseDescriptor(Path modFile, Path modDir) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(modFile)) {
            Map<String, Object> data = yaml.load(is);
            return new ModuleDescriptor(
                    (String) data.get("id"),
                    (String) data.get("name"),
                    (String) data.get("version"),
                    data.containsKey("load-order") ? ((Number) data.get("load-order")).intValue() : 0,
                    data.containsKey("dependencies") ? (List<String>) data.get("dependencies") : List.of(),
                    modDir
            );
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=ModuleLoaderTest -pl .`
Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/module/ backend/src/test/java/com/epic/engine/module/ backend/src/test/resources/test-mods/
git commit -m "$(cat <<'EOF'
feat: implement ModuleLoader - discovers mods, loads JS handlers into ScriptRuntime
EOF
)"
```

---

### Task 2.2: Schema Registry

**Files:**
- Create: `backend/src/main/java/com/epic/engine/module/Schema.java`
- Create: `backend/src/main/java/com/epic/engine/module/SchemaRegistry.java`
- Test: `backend/src/test/java/com/epic/engine/module/SchemaRegistryTest.java`
- Create: test fixture `backend/src/test/resources/test-mods/test-mod/schemas/main/creature.schema.yaml`
- Create: test fixture `backend/src/test/resources/test-mods/test-mod/schemas/sub/mutation.schema.yaml`

- [ ] **Step 1: Create test schema fixtures**

Create `backend/src/test/resources/test-mods/test-mod/schemas/main/creature.schema.yaml`:
```yaml
id: creature
type: main
fields:
  - name: hp
    type: int
    required: true
  - name: maxHp
    type: int
    required: true
  - name: attack
    type: int
    default: 5
  - name: level
    type: int
    default: 1
```

Create `backend/src/test/resources/test-mods/test-mod/schemas/sub/mutation.schema.yaml`:
```yaml
id: mutation
type: sub
compatible_with: [creature]
fields:
  - name: mutation_type
    type: string
    required: true
  - name: stat_multiplier
    type: double
    default: 1.5
```

- [ ] **Step 2: Write the failing test**

```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SchemaRegistryTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 4: Implement Schema**

```java
package com.epic.engine.module;

import java.util.List;
import java.util.Map;

public record Schema(
        String id,
        String type,
        List<String> compatibleWith,
        List<SchemaField> fields
) {
    public record SchemaField(
            String name,
            String fieldType,
            boolean required,
            Object defaultValue
    ) {}

    public static Schema fromYaml(Map<String, Object> data) {
        String id = (String) data.get("id");
        String type = (String) data.get("type");

        @SuppressWarnings("unchecked")
        List<String> compatible = data.containsKey("compatible_with")
                ? (List<String>) data.get("compatible_with")
                : List.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldDefs = data.containsKey("fields")
                ? (List<Map<String, Object>>) data.get("fields")
                : List.of();

        List<SchemaField> fields = fieldDefs.stream().map(f -> new SchemaField(
                (String) f.get("name"),
                (String) f.get("type"),
                Boolean.TRUE.equals(f.get("required")),
                f.get("default")
        )).toList();

        return new Schema(id, type, compatible, fields);
    }
}
```

- [ ] **Step 5: Implement SchemaRegistry**

```java
package com.epic.engine.module;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class SchemaRegistry {

    private final Map<String, Schema> schemas = new LinkedHashMap<>();

    public void loadFromModPath(Path modPath) throws IOException {
        Path schemasDir = modPath.resolve("schemas");
        if (!Files.isDirectory(schemasDir)) return;

        Yaml yaml = new Yaml();
        try (Stream<Path> files = Files.walk(schemasDir)) {
            files.filter(p -> p.toString().endsWith(".schema.yaml"))
                 .forEach(schemaFile -> {
                     try (InputStream is = Files.newInputStream(schemaFile)) {
                         @SuppressWarnings("unchecked")
                         Map<String, Object> data = yaml.load(is);
                         Schema schema = Schema.fromYaml(data);
                         schemas.put(schema.id(), schema);
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to load schema: " + schemaFile, e);
                     }
                 });
        }
    }

    public Schema get(String id) {
        return schemas.get(id);
    }

    public boolean isCompatible(String subSchemaId, String mainSchemaId) {
        Schema sub = schemas.get(subSchemaId);
        if (sub == null) return false;
        return sub.compatibleWith().contains(mainSchemaId);
    }

    public Map<String, Schema> getAll() {
        return Map.copyOf(schemas);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SchemaRegistryTest -pl .`
Expected: 4 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/module/Schema.java backend/src/main/java/com/epic/engine/module/SchemaRegistry.java backend/src/test/java/com/epic/engine/module/SchemaRegistryTest.java backend/src/test/resources/test-mods/test-mod/schemas/
git commit -m "$(cat <<'EOF'
feat: implement SchemaRegistry - loads and validates main/sub schemas from mods
EOF
)"
```

---

### Task 2.3: Hot Reload (File Watcher)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/script/HotReloader.java`
- Test: `backend/src/test/java/com/epic/engine/script/HotReloaderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HotReloaderTest {

    @TempDir
    Path tempDir;

    ScriptRuntime runtime;
    HotReloader reloader;

    @AfterEach
    void tearDown() {
        if (reloader != null) reloader.stop();
        if (runtime != null) runtime.close();
    }

    @Test
    void reloadScript_updatesHandler() throws Exception {
        EventBus bus = new EventBus();
        EntityStore store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path handlerFile = tempDir.resolve("test.js");
        Files.writeString(handlerFile, """
            engine.on("test.value", 100, function(event) {
                event.set("val", 1);
            });
            """);
        runtime.execute(Files.readString(handlerFile), "test.js");

        // Verify initial
        GameEvent event1 = new GameEvent("test.value");
        bus.fire("test.value", event1);
        assertThat((int) event1.get("val")).isEqualTo(1);

        // Simulate hot reload with new content
        reloader = new HotReloader(runtime, bus);
        String newScript = """
            engine.on("test.value", 100, function(event) {
                event.set("val", 2);
            });
            """;
        reloader.reload("test.js", newScript);

        // Verify updated
        GameEvent event2 = new GameEvent("test.value");
        bus.fire("test.value", event2);
        assertThat((int) event2.get("val")).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=HotReloaderTest -pl . 2>&1 | tail -5`
Expected: Compilation failure

- [ ] **Step 3: Implement HotReloader**

```java
package com.epic.engine.script;

import com.epic.engine.core.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HotReloader {

    private final ScriptRuntime runtime;
    private final EventBus bus;
    private final Map<String, String> loadedScripts = new ConcurrentHashMap<>();

    public HotReloader(ScriptRuntime runtime, EventBus bus) {
        this.runtime = runtime;
        this.bus = bus;
    }

    public void reload(String sourceName, String newScript) {
        // Clear all handlers and re-register from scratch
        // (In production, we'd track which handlers came from which script.
        //  For now, clear the event type and re-execute all scripts.)
        bus.clear();
        loadedScripts.put(sourceName, newScript);

        // Re-execute all scripts
        for (Map.Entry<String, String> entry : loadedScripts.entrySet()) {
            runtime.execute(entry.getValue(), entry.getKey());
        }
    }

    public void trackScript(String sourceName, String script) {
        loadedScripts.put(sourceName, script);
    }

    public void stop() {
        // Placeholder for file watcher shutdown
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=HotReloaderTest -pl .`
Expected: 1 test PASS

- [ ] **Step 5: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/script/HotReloader.java backend/src/test/java/com/epic/engine/script/HotReloaderTest.java
git commit -m "$(cat <<'EOF'
feat: implement HotReloader - re-executes JS handlers on change
EOF
)"
```

---

### Task 2.4: Spring Boot Integration (Wire Engine Core as Beans)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/EngineConfig.java`
- Test: `backend/src/test/java/com/epic/engine/core/EngineBootTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.epic.engine.core;

import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EngineBootTest {

    @Autowired EventBus eventBus;
    @Autowired EntityStore entityStore;
    @Autowired ScriptRuntime scriptRuntime;
    @Autowired SchemaRegistry schemaRegistry;

    @Test
    void engineCoreBeansAreWired() {
        assertThat(eventBus).isNotNull();
        assertThat(entityStore).isNotNull();
        assertThat(scriptRuntime).isNotNull();
        assertThat(schemaRegistry).isNotNull();
    }

    @Test
    void eventBus_isOperational() {
        var results = new java.util.ArrayList<String>();
        eventBus.on("boot.test", 100, e -> results.add("ok"));
        eventBus.fire("boot.test", new GameEvent("boot.test"));
        assertThat(results).containsExactly("ok");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EngineBootTest -pl . 2>&1 | tail -10`
Expected: Failure (no beans defined yet)

- [ ] **Step 3: Implement EngineConfig**

```java
package com.epic.engine.core;

import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EngineConfig {

    @Value("${epic.mods-path:../mods}")
    private String modsPath;

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    public EntityStore entityStore() {
        return new EntityStore();
    }

    @Bean
    public ScriptRuntime scriptRuntime(EventBus eventBus, EntityStore entityStore) {
        return new ScriptRuntime(eventBus, entityStore);
    }

    @Bean
    public SchemaRegistry schemaRegistry() {
        return new SchemaRegistry();
    }

    @Bean
    public ModuleLoader moduleLoader(ScriptRuntime scriptRuntime) {
        return new ModuleLoader(Path.of(modsPath), scriptRuntime);
    }

    @Bean
    public EngineBootstrap engineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry) {
        return new EngineBootstrap(moduleLoader, schemaRegistry, Path.of(modsPath));
    }
}
```

- [ ] **Step 4: Create EngineBootstrap to load modules on startup**

```java
package com.epic.engine.core;

import com.epic.engine.module.ModuleLoader;
import com.epic.engine.module.ModuleDescriptor;
import com.epic.engine.module.SchemaRegistry;
import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.util.List;

public class EngineBootstrap {

    private final ModuleLoader moduleLoader;
    private final SchemaRegistry schemaRegistry;
    private final Path modsPath;

    public EngineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry, Path modsPath) {
        this.moduleLoader = moduleLoader;
        this.schemaRegistry = schemaRegistry;
        this.modsPath = modsPath;
    }

    @PostConstruct
    public void boot() throws Exception {
        List<ModuleDescriptor> modules = moduleLoader.discoverModules();
        for (ModuleDescriptor mod : modules) {
            schemaRegistry.loadFromModPath(mod.path());
        }
        moduleLoader.loadAll();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=EngineBootTest -pl .`
Expected: 2 tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/core/EngineConfig.java backend/src/main/java/com/epic/engine/core/EngineBootstrap.java backend/src/test/java/com/epic/engine/core/EngineBootTest.java
git commit -m "$(cat <<'EOF'
feat: wire Engine Core into Spring Boot - EventBus, EntityStore, ScriptRuntime as beans
EOF
)"
```

---

## Phase 3: Base Rules — Combat

### Task 3.1: Create base-rules Mod Structure + Combat Flow Handler

**Files:**
- Create: `mods/base-rules/mod.yaml`
- Create: `mods/base-rules/handlers/combat/combat_flow.js`
- Create: `mods/base-rules/handlers/combat/initiative.js`
- Create: `mods/base-rules/handlers/combat/damage_calc.js`
- Create: `mods/base-rules/handlers/combat/skill_cost.js`
- Create: `mods/base-rules/handlers/combat/death_check.js`
- Create: `mods/base-rules/entities/monsters/goblin.yaml`
- Test: `backend/src/test/java/com/epic/engine/combat/NewCombatIntegrationTest.java`

- [ ] **Step 1: Create mod.yaml**

Create `mods/base-rules/mod.yaml`:
```yaml
id: base-rules
name: "基础规则"
version: "1.0.0"
description: "默认游戏规则 - 战斗、移动、场景"
load-order: 0
dependencies: []
```

- [ ] **Step 2: Create combat_flow.js**

Create `mods/base-rules/handlers/combat/combat_flow.js`:
```javascript
engine.on("combat.start", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = event.get("combatants");

    // Store combat state as an entity
    var combat = store.get(combatId);
    if (combat === null) {
        combat = engine.createEntity(combatId);
        var state = engine.newComponent("CombatState");
        state.set("round", 0);
        state.set("phase", "COMMAND");
        combat.addComponent(state);
        store.add(combat);
    }
});

engine.on("combat.resolve_round", 100, function(event) {
    var combatId = event.get("combatId");
    var commands = event.get("commands");
    var combat = store.get(combatId);
    var state = combat.getComponent("CombatState");

    state.set("round", state.getInt("round") + 1);
    state.set("phase", "RESOLVE");

    // Fire initiative to get turn order
    var initEvent = engine.newEvent("combat.determine_order");
    initEvent.set("combatId", combatId);
    engine.fire("combat.determine_order", initEvent);
    var turnOrder = initEvent.get("turnOrder");

    // Execute each unit's action
    for (var i = 0; i < turnOrder.length; i++) {
        var unitId = turnOrder[i];
        var unit = store.get(unitId);
        if (unit === null || !unit.hasComponent("Health")) continue;
        if (unit.getComponent("Health").getInt("hp") <= 0) continue;

        var cmd = commands.get(unitId);
        if (cmd === null) continue;

        var actionEvent = engine.newEvent("combat.unit_action");
        actionEvent.set("combatId", combatId);
        actionEvent.set("actorId", unitId);
        actionEvent.set("command", cmd);
        engine.fire("combat.unit_action", actionEvent);
    }

    // Check combat end
    var endEvent = engine.newEvent("combat.check_end");
    endEvent.set("combatId", combatId);
    engine.fire("combat.check_end", endEvent);

    state.set("phase", endEvent.get("ended") ? endEvent.get("result") : "COMMAND");
});
```

- [ ] **Step 3: Create initiative.js**

Create `mods/base-rules/handlers/combat/initiative.js`:
```javascript
engine.on("combat.determine_order", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTag("combat:" + combatId);
    var sorted = [];

    combatants.forEach(function(entity) {
        if (entity.hasComponent("Health") && entity.getComponent("Health").getInt("hp") > 0) {
            sorted.push({
                id: entity.getId(),
                speed: entity.hasComponent("CombatStats") ? entity.getComponent("CombatStats").getInt("speed") : 0
            });
        }
    });

    sorted.sort(function(a, b) { return b.speed - a.speed; });
    event.set("turnOrder", sorted.map(function(s) { return s.id; }));
});
```

- [ ] **Step 4: Create damage_calc.js**

Create `mods/base-rules/handlers/combat/damage_calc.js`:
```javascript
engine.on("combat.damage_calc", 100, function(event) {
    var attacker = store.get(event.get("attackerId"));
    var target = store.get(event.get("targetId"));

    var attack = attacker.getComponent("CombatStats").getInt("attack");
    var defense = target.getComponent("CombatStats").getInt("defense");
    var isDefending = target.hasComponent("Defending");

    var damage = Math.max(1, attack - defense);
    if (isDefending) {
        damage = Math.max(1, Math.floor(damage * 0.5));
    }

    event.set("damage", damage);
});
```

- [ ] **Step 5: Create skill_cost.js**

Create `mods/base-rules/handlers/combat/skill_cost.js`:
```javascript
engine.on("combat.skill_cost", 100, function(event) {
    var caster = store.get(event.get("casterId"));
    var cost = event.get("cost");

    if (caster.hasComponent("Mana")) {
        var mana = caster.getComponent("Mana");
        mana.set("mp", mana.getInt("mp") - cost);
    }
});
```

- [ ] **Step 6: Create death_check.js**

Create `mods/base-rules/handlers/combat/death_check.js`:
```javascript
engine.on("combat.damage_dealt", 100, function(event) {
    var target = store.get(event.get("targetId"));
    var health = target.getComponent("Health");

    if (health.getInt("hp") <= 0) {
        var deathEvent = engine.newEvent("combat.unit_death");
        deathEvent.set("deadId", target.getId());
        deathEvent.set("killerId", event.get("attackerId"));
        deathEvent.set("combatId", event.get("combatId"));
        engine.fire("combat.unit_death", deathEvent);
    }
});

engine.on("combat.unit_action", 100, function(event) {
    var cmd = event.get("command");
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");

    if (cmd.type === "ATTACK") {
        // Calculate damage
        var calcEvent = engine.newEvent("combat.damage_calc");
        calcEvent.set("attackerId", actorId);
        calcEvent.set("targetId", cmd.targetId);
        calcEvent.set("combatId", combatId);
        engine.fire("combat.damage_calc", calcEvent);

        var damage = calcEvent.get("damage");

        // Apply damage
        var target = store.get(cmd.targetId);
        var health = target.getComponent("Health");
        health.set("hp", Math.max(0, health.getInt("hp") - damage));

        // Record result
        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", cmd.targetId);
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        engine.fire("combat.damage_dealt", dealEvent);
    } else if (cmd.type === "DEFEND") {
        var actor = store.get(actorId);
        actor.addComponent(engine.newComponent("Defending"));
    }
});

engine.on("combat.check_end", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTag("combat:" + combatId);

    var playersAlive = false;
    var enemiesAlive = false;

    combatants.forEach(function(entity) {
        if (entity.getComponent("Health").getInt("hp") > 0) {
            if (entity.hasTag("player")) playersAlive = true;
            if (entity.hasTag("enemy")) enemiesAlive = true;
        }
    });

    if (!enemiesAlive) {
        event.set("ended", true);
        event.set("result", "VICTORY");
    } else if (!playersAlive) {
        event.set("ended", true);
        event.set("result", "DEFEAT");
    } else {
        event.set("ended", false);
    }
});
```

- [ ] **Step 7: Create monster template**

Create `mods/base-rules/entities/monsters/goblin.yaml`:
```yaml
id: goblin
main:
  type: creature
  level: 2
components:
  Health: { hp: 20, maxHp: 20 }
  CombatStats: { attack: 5, defense: 2, speed: 3 }
tags: [humanoid, goblin]
```

- [ ] **Step 8: Write integration test**

```java
package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.module.ModuleLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewCombatIntegrationTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        // Need to add createEntity and newComponent to EngineApi
        ModuleLoader loader = new ModuleLoader(Path.of("../mods/base-rules").toAbsolutePath(), runtime);
        // Load just the combat handlers directly
        runtime.execute(java.nio.file.Files.readString(
                Path.of("../mods/base-rules/handlers/combat/initiative.js")), "initiative.js");
        runtime.execute(java.nio.file.Files.readString(
                Path.of("../mods/base-rules/handlers/combat/damage_calc.js")), "damage_calc.js");
        runtime.execute(java.nio.file.Files.readString(
                Path.of("../mods/base-rules/handlers/combat/death_check.js")), "death_check.js");
        runtime.execute(java.nio.file.Files.readString(
                Path.of("../mods/base-rules/handlers/combat/combat_flow.js")), "combat_flow.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void combatRound_attackDealsDamage() {
        // Setup player
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        stats.set("defense", 5);
        stats.set("speed", 6);
        player.addComponent(stats);
        player.addTag("player");
        player.addTag("combat:battle1");
        store.add(player);

        // Setup enemy
        Entity goblin = new Entity("goblin1");
        Component gHealth = new Component("Health");
        gHealth.set("hp", 20);
        gHealth.set("maxHp", 20);
        goblin.addComponent(gHealth);
        Component gStats = new Component("CombatStats");
        gStats.set("attack", 5);
        gStats.set("defense", 2);
        gStats.set("speed", 3);
        goblin.addComponent(gStats);
        goblin.addTag("enemy");
        goblin.addTag("combat:battle1");
        store.add(goblin);

        // Create combat entity
        Entity combat = new Entity("battle1");
        Component combatState = new Component("CombatState");
        combatState.set("round", 0);
        combatState.set("phase", "COMMAND");
        combat.addComponent(combatState);
        store.add(combat);

        // Submit commands
        Map<String, Object> playerCmd = Map.of("type", "ATTACK", "targetId", "goblin1");
        Map<String, Object> enemyCmd = Map.of("type", "ATTACK", "targetId", "player1");
        Map<String, Object> commands = new HashMap<>();
        commands.put("player1", playerCmd);
        commands.put("goblin1", enemyCmd);

        GameEvent resolveEvent = new GameEvent("combat.resolve_round");
        resolveEvent.set("combatId", "battle1");
        resolveEvent.set("commands", commands);
        bus.fire("combat.resolve_round", resolveEvent);

        // Player attacks goblin: 10 attack - 2 defense = 8 damage
        assertThat(goblin.getComponent("Health").getInt("hp")).isEqualTo(12);
        // Goblin attacks player: 5 attack - 5 defense = 1 damage (min 1)
        assertThat(player.getComponent("Health").getInt("hp")).isEqualTo(99);
    }

    @Test
    void combatRound_defendReducesDamage() {
        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        stats.set("defense", 5);
        stats.set("speed", 6);
        player.addComponent(stats);
        player.addTag("player");
        player.addTag("combat:battle1");
        store.add(player);

        Entity goblin = new Entity("goblin1");
        Component gHealth = new Component("Health");
        gHealth.set("hp", 20);
        gHealth.set("maxHp", 20);
        goblin.addComponent(gHealth);
        Component gStats = new Component("CombatStats");
        gStats.set("attack", 10);
        gStats.set("defense", 2);
        gStats.set("speed", 8);
        goblin.addComponent(gStats);
        goblin.addTag("enemy");
        goblin.addTag("combat:battle1");
        store.add(goblin);

        Entity combat = new Entity("battle1");
        Component combatState = new Component("CombatState");
        combatState.set("round", 0);
        combatState.set("phase", "COMMAND");
        combat.addComponent(combatState);
        store.add(combat);

        Map<String, Object> playerCmd = Map.of("type", "DEFEND");
        Map<String, Object> enemyCmd = Map.of("type", "ATTACK", "targetId", "player1");
        Map<String, Object> commands = new HashMap<>();
        commands.put("player1", playerCmd);
        commands.put("goblin1", enemyCmd);

        GameEvent resolveEvent = new GameEvent("combat.resolve_round");
        resolveEvent.set("combatId", "battle1");
        resolveEvent.set("commands", commands);
        bus.fire("combat.resolve_round", resolveEvent);

        // Goblin attacks player who is defending: 10 - 5 = 5, halved = 2
        assertThat(player.getComponent("Health").getInt("hp")).isLessThan(100);
        assertThat(player.hasComponent("Defending")).isTrue();
    }
}
```

- [ ] **Step 9: Run tests (will require ScriptRuntime API additions)**

Note: This test will likely require adding `createEntity` and `newComponent` methods to the EngineApi class in ScriptRuntime. Add them:

In `ScriptRuntime.java`'s `EngineApi` class, add:
```java
@HostAccess.Export
public Entity createEntity(String id) {
    return new Entity(id);
}

@HostAccess.Export
public Component newComponent(String type) {
    return new Component(type);
}
```

Also add to EntityStore:
```java
// Make getByTag return a list for JS iteration
public List<Entity> getByTagAsList(String tag) {
    return new ArrayList<>(getByTag(tag));
}
```

And expose `getByTagAsList` as `getByTag` in the JS bindings by adding to ScriptRuntime:
```java
bindings.putMember("store", new StoreApi());
```

With a StoreApi wrapper class that delegates to EntityStore but returns JS-friendly types.

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=NewCombatIntegrationTest -pl .`
Expected: 2 tests PASS (after API fixes)

- [ ] **Step 10: Commit**

```bash
cd /Volumes/workplace/epic && git add mods/base-rules/ backend/src/test/java/com/epic/engine/combat/NewCombatIntegrationTest.java backend/src/main/java/com/epic/engine/script/ScriptRuntime.java
git commit -m "$(cat <<'EOF'
feat: base-rules combat module - JS handlers for initiative, damage, death check
EOF
)"
```

---

## Phase 4: Base Rules — Map & Scene

### Task 4.1: Map Movement Handler

**Files:**
- Create: `mods/base-rules/handlers/map/movement.js`
- Create: `mods/base-rules/handlers/map/pathfinding.js`
- Test: `backend/src/test/java/com/epic/engine/map/NewMapIntegrationTest.java`

- [ ] **Step 1: Create movement.js**

Create `mods/base-rules/handlers/map/movement.js`:
```javascript
engine.on("map.move", 100, function(event) {
    var entityId = event.get("entityId");
    var direction = event.get("direction");
    var entity = store.get(entityId);
    var pos = entity.getComponent("Position");
    var mapId = pos.getString("map");
    var x = pos.getInt("x");
    var y = pos.getInt("y");

    var dx = 0, dy = 0;
    if (direction === "NORTH") dy = -1;
    else if (direction === "SOUTH") dy = 1;
    else if (direction === "WEST") dx = -1;
    else if (direction === "EAST") dx = 1;

    var newX = x + dx;
    var newY = y + dy;

    // Check bounds and passability via map entity
    var map = store.get(mapId);
    if (map === null) { event.set("success", false); return; }

    var mapData = map.getComponent("MapData");
    var width = mapData.getInt("width");
    var height = mapData.getInt("height");

    if (newX < 0 || newX >= width || newY < 0 || newY >= height) {
        event.set("success", false);
        return;
    }

    pos.set("x", newX);
    pos.set("y", newY);
    event.set("success", true);
    event.set("newX", newX);
    event.set("newY", newY);

    // Fire enter area event
    var enterEvent = engine.newEvent("map.enter_area");
    enterEvent.set("entityId", entityId);
    enterEvent.set("x", newX);
    enterEvent.set("y", newY);
    enterEvent.set("mapId", mapId);
    engine.fire("map.enter_area", enterEvent);
});
```

- [ ] **Step 2: Create pathfinding.js**

Create `mods/base-rules/handlers/map/pathfinding.js`:
```javascript
engine.on("map.pathfind", 100, function(event) {
    var entityId = event.get("entityId");
    var targetX = event.get("targetX");
    var targetY = event.get("targetY");
    var entity = store.get(entityId);
    var pos = entity.getComponent("Position");
    var mapId = pos.getString("map");
    var map = store.get(mapId);
    var mapData = map.getComponent("MapData");
    var width = mapData.getInt("width");
    var height = mapData.getInt("height");

    // Simple A* implementation
    var startX = pos.getInt("x");
    var startY = pos.getInt("y");

    var open = [{x: startX, y: startY, g: 0, f: 0, parent: null}];
    var closed = {};
    var key = function(x, y) { return x + "," + y; };

    while (open.length > 0) {
        open.sort(function(a, b) { return a.f - b.f; });
        var current = open.shift();

        if (current.x === targetX && current.y === targetY) {
            // Reconstruct path
            var path = [];
            var node = current;
            while (node.parent !== null) {
                path.unshift({x: node.x, y: node.y});
                node = node.parent;
            }
            event.set("path", path);
            event.set("found", true);
            return;
        }

        closed[key(current.x, current.y)] = true;

        var neighbors = [
            {x: current.x, y: current.y - 1},
            {x: current.x, y: current.y + 1},
            {x: current.x - 1, y: current.y},
            {x: current.x + 1, y: current.y}
        ];

        for (var i = 0; i < neighbors.length; i++) {
            var n = neighbors[i];
            if (n.x < 0 || n.x >= width || n.y < 0 || n.y >= height) continue;
            if (closed[key(n.x, n.y)]) continue;

            var g = current.g + 1;
            var h = Math.abs(n.x - targetX) + Math.abs(n.y - targetY);
            var f = g + h;

            var existing = open.find(function(o) { return o.x === n.x && o.y === n.y; });
            if (existing && existing.g <= g) continue;

            open.push({x: n.x, y: n.y, g: g, f: f, parent: current});
        }
    }

    event.set("found", false);
    event.set("path", []);
});
```

- [ ] **Step 3: Write integration test**

```java
package com.epic.engine.map;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NewMapIntegrationTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        runtime.execute(Files.readString(
                Path.of("../mods/base-rules/handlers/map/movement.js")), "movement.js");
        runtime.execute(Files.readString(
                Path.of("../mods/base-rules/handlers/map/pathfinding.js")), "pathfinding.js");

        // Setup map entity
        Entity map = new Entity("world_map");
        Component mapData = new Component("MapData");
        mapData.set("width", 10);
        mapData.set("height", 10);
        map.addComponent(mapData);
        store.add(map);

        // Setup player with position
        Entity player = new Entity("player1");
        Component pos = new Component("Position");
        pos.set("map", "world_map");
        pos.set("x", 4);
        pos.set("y", 3);
        player.addComponent(pos);
        store.add(player);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void moveNorth_updatesPosition() {
        GameEvent event = new GameEvent("map.move");
        event.set("entityId", "player1");
        event.set("direction", "NORTH");
        bus.fire("map.move", event);

        assertThat((boolean) event.get("success")).isTrue();
        Entity player = store.get("player1");
        assertThat(player.getComponent("Position").getInt("y")).isEqualTo(2);
    }

    @Test
    void moveOutOfBounds_fails() {
        Entity player = store.get("player1");
        player.getComponent("Position").set("y", 0);

        GameEvent event = new GameEvent("map.move");
        event.set("entityId", "player1");
        event.set("direction", "NORTH");
        bus.fire("map.move", event);

        assertThat((boolean) event.get("success")).isFalse();
    }

    @Test
    void pathfind_findsRoute() {
        GameEvent event = new GameEvent("map.pathfind");
        event.set("entityId", "player1");
        event.set("targetX", 6);
        event.set("targetY", 3);
        bus.fire("map.pathfind", event);

        assertThat((boolean) event.get("found")).isTrue();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=NewMapIntegrationTest -pl .`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Volumes/workplace/epic && git add mods/base-rules/handlers/map/ backend/src/test/java/com/epic/engine/map/NewMapIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat: base-rules map module - movement and pathfinding as JS handlers
EOF
)"
```

---

## Phase 5: Frontend Rewrite

### Task 5.1: Snapshot API Endpoint

**Files:**
- Create: `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`
- Create: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`
- Create: `backend/src/main/java/com/epic/engine/snapshot/SnapshotController.java`
- Test: `backend/src/test/java/com/epic/engine/snapshot/SnapshotControllerTest.java`

- [ ] **Step 1: Implement WorldSnapshot DTO**

```java
package com.epic.engine.snapshot;

import java.util.List;
import java.util.Map;

public record WorldSnapshot(
        String playerId,
        ActionResult result,
        List<StatusBar> statusBars,
        List<BuffEntry> buffs,
        MapSnapshot map,
        CombatSnapshot combat,
        List<ActionOption> actions,
        List<LogEntry> log
) {
    public record ActionResult(boolean success, String message) {}
    public record StatusBar(String id, String label, int current, int max, String color, int priority) {}
    public record BuffEntry(String id, String name, String remaining, int priority) {}
    public record MapSnapshot(String mapId, int playerX, int playerY, int width, int height, List<List<TileInfo>> visibleTiles) {}
    public record TileInfo(char terrain, String color, String textColor) {}
    public record CombatSnapshot(String combatId, String phase, int round, List<CombatantInfo> combatants) {}
    public record CombatantInfo(String id, String name, String side, int hp, int maxHp, boolean alive) {}
    public record ActionOption(String type, String label, Map<String, Object> params) {}
    public record LogEntry(List<TextSegment> segments) {}
    public record TextSegment(String text, String color) {}
}
```

- [ ] **Step 2: Implement SnapshotService**

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SnapshotService {

    private final EventBus eventBus;
    private final EntityStore entityStore;

    public SnapshotService(EventBus eventBus, EntityStore entityStore) {
        this.eventBus = eventBus;
        this.entityStore = entityStore;
    }

    public WorldSnapshot buildSnapshot(String playerId, WorldSnapshot.ActionResult result) {
        Entity player = entityStore.get(playerId);
        if (player == null) {
            return new WorldSnapshot(playerId, result, List.of(), List.of(), null, null, List.of(), List.of());
        }

        // Fire ui.render events to let modules contribute UI data
        GameEvent uiEvent = new GameEvent("ui.render_status");
        uiEvent.set("entityId", playerId);
        uiEvent.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        uiEvent.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        eventBus.fire("ui.render_status", uiEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = uiEvent.get("bars");
        @SuppressWarnings("unchecked")
        List<WorldSnapshot.BuffEntry> buffs = uiEvent.get("buffs");

        // Fire ui.render_actions to get available actions
        GameEvent actionsEvent = new GameEvent("ui.render_actions");
        actionsEvent.set("entityId", playerId);
        actionsEvent.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        eventBus.fire("ui.render_actions", actionsEvent);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = actionsEvent.get("actions");

        return new WorldSnapshot(
                playerId, result, bars, buffs,
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions, List.of()
        );
    }

    private WorldSnapshot.MapSnapshot buildMapSnapshot(Entity player) {
        if (!player.hasComponent("Position")) return null;
        Component pos = player.getComponent("Position");
        String mapId = pos.getString("map");
        Entity map = entityStore.get(mapId);
        if (map == null) return null;

        Component mapData = map.getComponent("MapData");
        return new WorldSnapshot.MapSnapshot(
                mapId, pos.getInt("x"), pos.getInt("y"),
                mapData.getInt("width"), mapData.getInt("height"),
                List.of()
        );
    }

    private WorldSnapshot.CombatSnapshot buildCombatSnapshot(String playerId) {
        // Find active combat for this player
        for (Entity entity : entityStore.all()) {
            if (entity.hasComponent("CombatState") && entity.hasTag("combat_player:" + playerId)) {
                Component state = entity.getComponent("CombatState");
                List<WorldSnapshot.CombatantInfo> combatants = new ArrayList<>();
                for (Entity c : entityStore.getByTag("combat:" + entity.getId())) {
                    Component health = c.getComponent("Health");
                    combatants.add(new WorldSnapshot.CombatantInfo(
                            c.getId(), c.hasComponent("Name") ? c.getComponent("Name").getString("value") : c.getId(),
                            c.hasTag("player") ? "PLAYER" : "ENEMY",
                            health.getInt("hp"), health.getInt("maxHp"),
                            health.getInt("hp") > 0
                    ));
                }
                return new WorldSnapshot.CombatSnapshot(
                        entity.getId(), state.getString("phase"),
                        state.getInt("round"), combatants
                );
            }
        }
        return null;
    }
}
```

- [ ] **Step 3: Implement SnapshotController**

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final EventBus eventBus;
    private final SnapshotService snapshotService;

    public SnapshotController(EventBus eventBus, SnapshotService snapshotService) {
        this.eventBus = eventBus;
        this.snapshotService = snapshotService;
    }

    @PostMapping("/action")
    public WorldSnapshot performAction(@RequestBody Map<String, Object> request) {
        String playerId = (String) request.get("playerId");
        String type = (String) request.get("type");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        // Fire the action event
        GameEvent actionEvent = new GameEvent("action." + type);
        actionEvent.set("playerId", playerId);
        actionEvent.set("type", type);
        params.forEach(actionEvent::set);
        eventBus.fire("action." + type, actionEvent);

        boolean success = !actionEvent.has("error");
        String message = actionEvent.has("error") ? actionEvent.get("error") : "ok";

        return snapshotService.buildSnapshot(playerId, new WorldSnapshot.ActionResult(success, message));
    }

    @GetMapping("/snapshot/{playerId}")
    public WorldSnapshot getSnapshot(@PathVariable String playerId) {
        return snapshotService.buildSnapshot(playerId, new WorldSnapshot.ActionResult(true, "ok"));
    }
}
```

- [ ] **Step 4: Write test**

```java
package com.epic.engine.snapshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SnapshotControllerTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void getSnapshot_returnsValidStructure() {
        WorldSnapshot snapshot = rest.getForObject("/api/snapshot/player1", WorldSnapshot.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.playerId()).isEqualTo("player1");
        assertThat(snapshot.result().success()).isTrue();
    }

    @Test
    void performAction_returnsSnapshot() {
        var request = Map.of("playerId", "player1", "type", "test", "params", Map.of());
        WorldSnapshot snapshot = rest.postForObject("/api/action", request, WorldSnapshot.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.playerId()).isEqualTo("player1");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=SnapshotControllerTest -pl .`
Expected: 2 tests PASS

- [ ] **Step 6: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/snapshot/ backend/src/test/java/com/epic/engine/snapshot/
git commit -m "$(cat <<'EOF'
feat: snapshot API - single /api/action endpoint returns full world state
EOF
)"
```

---

### Task 5.2: Frontend Stateless Renderer

**Files:**
- Rewrite: `frontend/src/api/client.js`
- Rewrite: `frontend/src/App.vue`
- Create: `frontend/src/components/SnapshotRenderer.vue`
- Create: `frontend/src/components/StatusBars.vue`
- Create: `frontend/src/components/ActionPanel.vue`

- [ ] **Step 1: Rewrite api/client.js**

```javascript
const BASE_URL = '/api'

export async function performAction(playerId, type, params = {}) {
    const response = await fetch(`${BASE_URL}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, type, params })
    })
    return response.json()
}

export async function getSnapshot(playerId) {
    const response = await fetch(`${BASE_URL}/snapshot/${playerId}`)
    return response.json()
}
```

- [ ] **Step 2: Create StatusBars.vue**

```vue
<template>
  <div class="status-bars">
    <div v-for="bar in visibleBars" :key="bar.id" class="bar-item">
      <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
      <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
    </div>
    <div v-if="hiddenCount > 0" class="bar-overflow" @click="expanded = !expanded">
      {{ expanded ? '收起' : `+${hiddenCount} 更多` }}
    </div>
    <div v-if="expanded">
      <div v-for="bar in hiddenBars" :key="bar.id" class="bar-item">
        <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
        <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({ bars: Array })
const expanded = ref(false)
const MAX_VISIBLE = 3

const sorted = computed(() =>
    [...(props.bars || [])].sort((a, b) => a.priority - b.priority)
)
const visibleBars = computed(() => sorted.value.slice(0, MAX_VISIBLE))
const hiddenBars = computed(() => sorted.value.slice(MAX_VISIBLE))
const hiddenCount = computed(() => hiddenBars.value.length)
</script>

<style scoped>
.bar-item { display: flex; justify-content: space-between; margin: 0.2rem 0; }
.bar-label { font-weight: bold; }
.bar-overflow { cursor: pointer; color: var(--link-color); font-size: 0.9em; }
</style>
```

- [ ] **Step 3: Create ActionPanel.vue**

```vue
<template>
  <div class="action-panel">
    <div v-for="action in actions" :key="action.type + JSON.stringify(action.params)"
         class="action-item" @click="$emit('action', action)">
      {{ action.label }}
    </div>
    <div v-if="!actions || actions.length === 0" class="no-actions">
      无可用操作
    </div>
  </div>
</template>

<script setup>
defineProps({ actions: Array })
defineEmits(['action'])
</script>

<style scoped>
.action-item { cursor: pointer; color: var(--link-color); margin: 0.3rem 0; }
.action-item:hover { text-decoration: underline; }
.no-actions { color: var(--text-color); opacity: 0.5; }
</style>
```

- [ ] **Step 4: Create SnapshotRenderer.vue**

```vue
<template>
  <div class="snapshot-renderer" v-if="snapshot">
    <div class="panel-main">
      <MapView v-if="snapshot.map" :map="snapshot.map" />
      <CombatView v-if="snapshot.combat" :combat="snapshot.combat" />
    </div>
    <div class="panel-status">
      <StatusBars :bars="snapshot.statusBars" />
      <div class="buffs" v-if="snapshot.buffs && snapshot.buffs.length">
        <div v-for="buff in snapshot.buffs" :key="buff.id" class="buff-item">
          {{ buff.name }} ({{ buff.remaining }})
        </div>
      </div>
    </div>
    <div class="panel-log">
      <div v-for="(entry, i) in snapshot.log" :key="i">
        <TextRenderer :segments="entry.segments" />
      </div>
    </div>
    <div class="panel-actions">
      <ActionPanel :actions="snapshot.actions" @action="$emit('action', $event)" />
    </div>
  </div>
</template>

<script setup>
import StatusBars from './StatusBars.vue'
import ActionPanel from './ActionPanel.vue'
import TextRenderer from './TextRenderer.vue'
import MapView from './MapView.vue'
import CombatView from './combat/CombatView.vue'

defineProps({ snapshot: Object })
defineEmits(['action'])
</script>

<style scoped>
.snapshot-renderer {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}
.panel-main { grid-column: 1; grid-row: 1; min-height: 0; overflow: hidden; }
.panel-status { grid-column: 2; grid-row: 1; min-height: 0; }
.panel-log { grid-column: 1; grid-row: 2; min-height: 0; overflow-y: auto; }
.panel-actions { grid-column: 2; grid-row: 2; min-height: 0; }
.buff-item { font-size: 0.9em; margin: 0.2rem 0; }
</style>
```

- [ ] **Step 5: Rewrite App.vue**

```vue
<template>
  <div class="app-container">
    <SnapshotRenderer :snapshot="snapshot" @action="handleAction" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SnapshotRenderer from './components/SnapshotRenderer.vue'
import { performAction, getSnapshot } from './api/client.js'

const PLAYER_ID = 'player1'
const snapshot = ref(null)

async function handleAction(action) {
    const result = await performAction(PLAYER_ID, action.type, action.params || {})
    snapshot.value = result
}

onMounted(async () => {
    snapshot.value = await getSnapshot(PLAYER_ID)
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}
</style>
```

- [ ] **Step 6: Start dev server and verify in browser**

Run: `cd /Volumes/workplace/epic && ./start.sh`
Expected: App loads, shows snapshot data (may be empty if no player entity exists yet)

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add frontend/src/
git commit -m "$(cat <<'EOF'
feat: frontend rewrite - stateless snapshot renderer, single action API
EOF
)"
```

---

## Phase 6: Persistence & Generator

### Task 6.1: Entity Persistence

**Files:**
- Create: `backend/src/main/java/com/epic/engine/persistence/EntityData.java`
- Create: `backend/src/main/java/com/epic/engine/persistence/EntityDataRepository.java`
- Create: `backend/src/main/java/com/epic/engine/persistence/PersistenceService.java`
- Test: `backend/src/test/java/com/epic/engine/persistence/PersistenceServiceTest.java`

- [ ] **Step 1: Implement EntityData JPA entity**

```java
package com.epic.engine.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class EntityData {

    @Id
    private String entityId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String componentsJson;

    @Column
    private String tagsJson;

    public EntityData() {}

    public EntityData(String entityId, String componentsJson, String tagsJson) {
        this.entityId = entityId;
        this.componentsJson = componentsJson;
        this.tagsJson = tagsJson;
    }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getComponentsJson() { return componentsJson; }
    public void setComponentsJson(String componentsJson) { this.componentsJson = componentsJson; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
}
```

- [ ] **Step 2: Implement Repository**

```java
package com.epic.engine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityDataRepository extends JpaRepository<EntityData, String> {
}
```

- [ ] **Step 3: Implement PersistenceService**

```java
package com.epic.engine.persistence;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PersistenceService {

    private final EntityDataRepository repository;
    private final EntityStore entityStore;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersistenceService(EntityDataRepository repository, EntityStore entityStore) {
        this.repository = repository;
        this.entityStore = entityStore;
    }

    public void save(Entity entity) {
        if (!entity.hasTag("persistent")) return;

        try {
            Map<String, Map<String, Object>> components = new LinkedHashMap<>();
            for (Component c : entity.getAllComponents()) {
                components.put(c.getType(), c.getAll());
            }
            String componentsJson = mapper.writeValueAsString(components);
            String tagsJson = mapper.writeValueAsString(entity.getTags());

            repository.save(new EntityData(entity.getId(), componentsJson, tagsJson));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize entity: " + entity.getId(), e);
        }
    }

    public Entity load(String entityId) {
        return repository.findById(entityId).map(this::deserialize).orElse(null);
    }

    public void loadAllPersistent() {
        repository.findAll().forEach(data -> {
            Entity entity = deserialize(data);
            entityStore.add(entity);
        });
    }

    @SuppressWarnings("unchecked")
    private Entity deserialize(EntityData data) {
        try {
            Entity entity = new Entity(data.getEntityId());

            Map<String, Map<String, Object>> components = mapper.readValue(
                    data.getComponentsJson(), Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : components.entrySet()) {
                Component c = new Component(entry.getKey());
                entry.getValue().forEach(c::set);
                entity.addComponent(c);
            }

            List<String> tags = mapper.readValue(data.getTagsJson(), List.class);
            tags.forEach(entity::addTag);

            return entity;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize entity: " + data.getEntityId(), e);
        }
    }
}
```

- [ ] **Step 4: Write test**

```java
package com.epic.engine.persistence;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PersistenceServiceTest {

    @Autowired PersistenceService persistenceService;
    @Autowired EntityStore entityStore;

    @Test
    void saveAndLoad_roundTrips() {
        Entity player = new Entity("persist_test_player");
        Component health = new Component("Health");
        health.set("hp", 85);
        health.set("maxHp", 100);
        player.addComponent(health);
        player.addTag("persistent");
        player.addTag("player");

        persistenceService.save(player);

        Entity loaded = persistenceService.load("persist_test_player");

        assertThat(loaded).isNotNull();
        assertThat(loaded.hasComponent("Health")).isTrue();
        assertThat(loaded.getComponent("Health").getInt("hp")).isEqualTo(85);
        assertThat(loaded.hasTag("player")).isTrue();
        assertThat(loaded.hasTag("persistent")).isTrue();
    }

    @Test
    void nonPersistent_notSaved() {
        Entity goblin = new Entity("temp_goblin");
        goblin.addComponent(new Component("Health"));
        // No "persistent" tag

        persistenceService.save(goblin);

        assertThat(persistenceService.load("temp_goblin")).isNull();
    }
}
```

- [ ] **Step 5: Add jackson-databind to pom.xml (if not already included via spring-boot-starter-web)**

Check: jackson-databind is already included transitively via `spring-boot-starter-web`. No change needed.

- [ ] **Step 6: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=PersistenceServiceTest -pl .`
Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/persistence/ backend/src/test/java/com/epic/engine/persistence/
git commit -m "$(cat <<'EOF'
feat: entity persistence - save/load entities with Persistence tag to H2
EOF
)"
```

---

### Task 6.2: Schema-Driven Generator

**Files:**
- Create: `backend/src/main/java/com/epic/engine/generator/GeneratorRequest.java`
- Create: `backend/src/main/java/com/epic/engine/generator/GeneratorService.java`
- Test: `backend/src/test/java/com/epic/engine/generator/GeneratorServiceTest.java`

- [ ] **Step 1: Implement GeneratorRequest**

```java
package com.epic.engine.generator;

import java.util.List;

public record GeneratorRequest(
        String mainSchemaId,
        List<String> subSchemaIds,
        int level
) {}
```

- [ ] **Step 2: Implement GeneratorService**

```java
package com.epic.engine.generator;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.module.Schema;
import com.epic.engine.module.SchemaRegistry;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Service
public class GeneratorService {

    private final SchemaRegistry schemaRegistry;
    private final Random random = new Random();

    public GeneratorService(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public Entity generate(GeneratorRequest request) {
        Schema mainSchema = schemaRegistry.get(request.mainSchemaId());
        if (mainSchema == null) {
            throw new IllegalArgumentException("Unknown schema: " + request.mainSchemaId());
        }

        String id = request.mainSchemaId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        Entity entity = new Entity(id);

        // Create main component from schema fields
        Component main = new Component(mainSchema.id());
        for (Schema.SchemaField field : mainSchema.fields()) {
            Object value = generateFieldValue(field, request.level());
            main.set(field.name(), value);
        }
        entity.addComponent(main);
        entity.addTag(mainSchema.id());

        // Add sub components
        for (String subId : request.subSchemaIds()) {
            Schema subSchema = schemaRegistry.get(subId);
            if (subSchema == null) continue;
            if (!schemaRegistry.isCompatible(subId, request.mainSchemaId())) continue;

            Component sub = new Component(subSchema.id());
            for (Schema.SchemaField field : subSchema.fields()) {
                Object value = generateFieldValue(field, request.level());
                sub.set(field.name(), value);
            }
            entity.addComponent(sub);
            entity.addTag(subId);
        }

        return entity;
    }

    private Object generateFieldValue(Schema.SchemaField field, int level) {
        if (field.defaultValue() != null) return field.defaultValue();

        return switch (field.fieldType()) {
            case "int" -> 10 * level + random.nextInt(5 * level);
            case "double" -> 1.0 + (level * 0.5) + random.nextDouble();
            case "string" -> field.name() + "_" + level;
            default -> null;
        };
    }
}
```

- [ ] **Step 3: Write test**

```java
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

        assertThat(entity.getAllComponents()).hasSize(1);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -Dtest=GeneratorServiceTest -pl .`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Volumes/workplace/epic && git add backend/src/main/java/com/epic/engine/generator/ backend/src/test/java/com/epic/engine/generator/
git commit -m "$(cat <<'EOF'
feat: schema-driven generator - creates entities from Main + Sub schemas
EOF
)"
```

---

### Task 6.3: Remove Old Code

**Files:**
- Delete: `backend/src/main/java/com/epic/engine/action/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/combat/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/map/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/scene/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/save/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/panel/` (entire directory)
- Delete: `backend/src/main/java/com/epic/engine/mod/` (entire directory)
- Delete: old test files that reference removed code
- Delete: `frontend/src/composables/useCombat.js`
- Delete: `frontend/src/composables/useGameState.js`
- Delete: `frontend/src/composables/useMap.js`
- Delete: `frontend/src/composables/usePanelRefresh.js`
- Delete: old frontend components replaced by snapshot renderer

- [ ] **Step 1: Remove old backend code**

```bash
cd /Volumes/workplace/epic/backend/src/main/java/com/epic/engine
rm -rf action/ combat/ map/ scene/ save/ panel/ mod/
```

- [ ] **Step 2: Remove old tests that depend on removed code**

```bash
cd /Volumes/workplace/epic/backend/src/test/java/com/epic/engine
rm -f IntegrationTest.java
rm -rf combat/CombatIntegrationTest.java
rm -rf action/
rm -rf map/MapServiceTest.java map/MapIntegrationTest.java map/PathfinderTest.java
rm -rf scene/
rm -rf mod/
```

- [ ] **Step 3: Remove old frontend composables and components**

```bash
cd /Volumes/workplace/epic/frontend/src
rm -f composables/useCombat.js composables/useGameState.js composables/useMap.js composables/usePanelRefresh.js
rm -f components/ScenePanel.vue components/NavigationPanel.vue components/StatusPanel.vue
rm -f components/MapGrid.vue components/MapInfoPanel.vue components/ActionLink.vue components/GameLog.vue
rm -rf components/combat/BattleGrid.vue
```

- [ ] **Step 4: Verify build still passes**

Run: `cd /Volumes/workplace/epic/backend && mvn compile -q`
Expected: BUILD SUCCESS

Run: `cd /Volumes/workplace/epic/frontend && npm run build 2>&1 | tail -5`
Expected: Build success (or known warnings only)

- [ ] **Step 5: Run remaining tests**

Run: `cd /Volumes/workplace/epic/backend && mvn test -pl .`
Expected: All new tests pass, no references to deleted code

- [ ] **Step 6: Commit**

```bash
cd /Volumes/workplace/epic && git add -A
git commit -m "$(cat <<'EOF'
refactor: remove old hardcoded engine code - replaced by event-driven microkernel
EOF
)"
```

---

### Task 6.4: Rename mods/base to mods/base-rules (data migration)

**Files:**
- Delete: `mods/base/` (old mod)
- The new `mods/base-rules/` already exists from Task 3.1

- [ ] **Step 1: Remove old mods/base directory**

```bash
rm -rf /Volumes/workplace/epic/mods/base
```

- [ ] **Step 2: Verify the app starts with new mod structure**

Run: `cd /Volumes/workplace/epic/backend && mvn spring-boot:run -q &` (start in background, verify no errors)
Expected: Application starts, loads base-rules module

- [ ] **Step 3: Commit**

```bash
cd /Volumes/workplace/epic && git add -A
git commit -m "$(cat <<'EOF'
chore: remove old mods/base, replaced by mods/base-rules
EOF
)"
```

---

## Notes for Implementation

1. **Task 3.1 is the hardest task** — the JS handlers need to work with GraalJS's polyglot interop. Java collections (`Set`, `List`) behave differently in JS context. You may need to add wrapper methods (like `getByTagAsList`) or use `ProxyArray`/`ProxyObject` adapters.

2. **GraalJS gotchas**: `HashMap` keys aren't directly iterable in JS. The `EntityStore.getByTag()` returns a `Set` which doesn't have `.forEach()` in JS the same way. Consider wrapping returns in `Value.asValue()` or providing JS-friendly API methods.

3. **The old tests in `combat/engine/CombatEngineTest.java` and `combat/model/TargetResolverTest.java`** should be deleted in Task 6.3 since they test removed code.

4. **The frontend MapView.vue** is referenced in SnapshotRenderer but not created here — it should be a simplified version of the current MapGrid that reads from snapshot data instead of composables. Create it as part of Task 5.2 or as a follow-up.

5. **application-test.yaml** may need updating to not reference old JPA entities. Verify after removing `PlayerState`.
