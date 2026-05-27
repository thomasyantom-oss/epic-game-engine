# 属性/装备/等级系统 后端实现计划（Plan A）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 激活 ModifierChain 作为统一属性计算管道，添加 ModifierType 注册表（含 stack_rule 执行）、ModifierChainService、装备/等级/LifeState mod 层、以及 `/api/character/stats` 属性明细端点。

**Architecture:** 新建 `ModifierChainService`（Spring @Service）集中管理所有实体的 ModifierChain，在 recalculate 前后 fire `entity.before/after_recalculate` 事件；ScriptRuntime 通过 `EngineApi` 把操作暴露给 JS；PersistenceService 保存 base state 值而非 post-modifier 值；装备/等级/生死规则完全由 mod JS 实现。

**Tech Stack:** Java 21 records, Spring Boot @Service, GraalJS Value, AssertJ + JUnit 5

---

## 文件结构

**新建文件**
| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/epic/engine/core/ModifierType.java` | ModifierType record (id, label, stackRule, basePriority) |
| `backend/src/main/java/com/epic/engine/core/ModifierDiff.java` | per-modifier 属性 diff record |
| `backend/src/main/java/com/epic/engine/core/ModifierTypeRegistry.java` | @Service，从 YAML 加载类型定义 |
| `backend/src/main/java/com/epic/engine/core/ModifierChainService.java` | @Service，管理所有实体的 ModifierChain |
| `backend/src/main/java/com/epic/engine/snapshot/CharacterStatsController.java` | GET /api/character/stats |
| `backend/src/test/java/com/epic/engine/core/ModifierTypeRegistryTest.java` | 单元测试 |
| `backend/src/test/java/com/epic/engine/core/ModifierChainServiceTest.java` | 单元测试 |
| `backend/src/test/java/com/epic/engine/snapshot/CharacterStatsTest.java` | 集成测试 |
| `mods/base-rules/modifier_types.yaml` | ModifierType 数据 |
| `mods/base-rules/item_rarity.yaml` | 稀有度颜色数据 |
| `mods/base-rules/entities/items.yaml` | 示例物品数据 |
| `mods/base-rules/handlers/character/recalculate_hooks.js` | before/after_recalculate 及 entity.loaded |
| `mods/base-rules/handlers/equipment/equip.js` | 装备/卸装/背包动作 |
| `mods/base-rules/handlers/character/leveling.js` | 经验值/升级系统 |

**修改文件**
| 文件 | 变化 |
|------|------|
| `backend/src/main/java/com/epic/engine/core/Modifier.java` | 添加 typeId、label 字段，兼容旧构造器 |
| `backend/src/main/java/com/epic/engine/core/ModifierChain.java` | 选择性 restore、recalculateWithTracking、setBaseSelective |
| `backend/src/main/java/com/epic/engine/core/Component.java` | 添加 getBoolean() |
| `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java` | 新增 EngineApi 方法 |
| `backend/src/main/java/com/epic/engine/core/EngineConfig.java` | 新增 bean |
| `backend/src/main/java/com/epic/engine/core/EngineBootstrap.java` | 加载类型定义，fire entity.loaded |
| `backend/src/main/java/com/epic/engine/persistence/PersistenceService.java` | 保存 base state 值 |
| `mods/base-rules/handlers/character/select.js` | 改用 ModifierChain 注册职业加成 |
| `mods/base-rules/handlers/combat/death_check.js` | 迁移到 LifeState |

**不需修改**
- `GeneratorService.java` — 仅用于生成战斗遭遇实体，不受影响
- `BuffService.java` — 现有 buff（defending/poison）不修改属性，不需迁移

---

## 持久化设计（关键约定）

`setBase()` 仅在以下场景调用，且必须在任何 Modifier 注册之前：
1. **角色创建**：添加完 stat 组件（Health/Mana/CombatStats）后、注册职业 Modifier 前
2. **游戏重启 / entity.loaded**：重新加载后、重新注册 Modifier 前

`PersistenceService.save()` 保存规则：若实体有 ModifierChain，保存 chain.baseState 中的组件值（不保存 post-modifier 值）+ 其他组件（EquipmentSlots、Inventory 等）的当前值。

`ModifierChain.recalculate()` 只恢复/重置 baseState 中的组件，**不触及** baseState 之外的组件（EquipmentSlots、Inventory、Character、Name、Skills 等）。

---

## Task 1: 扩展 Modifier record

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/core/Modifier.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/epic/engine/core/ModifierChainTest.java` 末尾添加：

```java
@Test
void modifier_hasTypeIdAndLabel() {
    Modifier m = new Modifier("id1", "equipment", "铁剑", "source1", 50,
            e -> e.getComponent("Health").set("hp", 10));
    assertThat(m.typeId()).isEqualTo("equipment");
    assertThat(m.label()).isEqualTo("铁剑");
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=ModifierChainTest#modifier_hasTypeIdAndLabel -q
```
期望：FAIL (编译错误，Modifier 没有 typeId/label)

- [ ] **Step 3: 修改 Modifier.java**

```java
package com.epic.engine.core;

import java.util.function.Consumer;

public record Modifier(
        String id,
        String typeId,
        String label,
        String source,
        int priority,
        Consumer<Entity> apply
) implements Comparable<Modifier> {

    public Modifier(String id, String source, int priority, Consumer<Entity> apply) {
        this(id, null, null, source, priority, apply);
    }

    @Override
    public int compareTo(Modifier other) {
        return Integer.compare(this.priority, other.priority);
    }
}
```

- [ ] **Step 4: 确认测试通过**

```
cd backend && mvn test -Dtest=ModifierChainTest -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/Modifier.java
git add backend/src/test/java/com/epic/engine/core/ModifierChainTest.java
git commit -m "feat: Modifier record 添加 typeId 和 label 字段"
```

---

## Task 2: Component.getBoolean() + ModifierType record

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/core/Component.java`
- Create: `backend/src/main/java/com/epic/engine/core/ModifierType.java`

- [ ] **Step 1: 添加 getBoolean() 到 Component.java**

在 `getString()` 方法后插入：

```java
public boolean getBoolean(String key) { return Boolean.TRUE.equals(data.get(key)); }
```

- [ ] **Step 2: 创建 ModifierType.java**

```java
package com.epic.engine.core;

public record ModifierType(
        String id,
        String label,
        String stackRule,
        int basePriority
) {
    public static final String EXCLUSIVE = "exclusive";
    public static final String ADDITIVE = "additive";
    public static final String INDEPENDENT = "independent";
}
```

- [ ] **Step 3: 运行测试确认无回归**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/Component.java
git add backend/src/main/java/com/epic/engine/core/ModifierType.java
git commit -m "feat: 添加 Component.getBoolean() 和 ModifierType record"
```

---

## Task 3: ModifierTypeRegistry

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/ModifierTypeRegistry.java`
- Create: `backend/src/test/java/com/epic/engine/core/ModifierTypeRegistryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierTypeRegistryTest {

    @Test
    void register_andGet_byId() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        reg.register(new ModifierType("equipment", "装备", "additive", 50));
        ModifierType t = reg.get("equipment");
        assertThat(t).isNotNull();
        assertThat(t.stackRule()).isEqualTo("additive");
    }

    @Test
    void loadFromList_populatesRegistry() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        reg.loadFromList(List.of(
            Map.of("id", "race", "label", "种族", "stack_rule", "exclusive", "base_priority", 200),
            Map.of("id", "equipment", "label", "装备", "stack_rule", "additive", "base_priority", 50)
        ));
        assertThat(reg.get("race").basePriority()).isEqualTo(200);
        assertThat(reg.get("equipment").stackRule()).isEqualTo("additive");
    }

    @Test
    void getStackRule_unknownType_returnsAdditive() {
        ModifierTypeRegistry reg = new ModifierTypeRegistry();
        assertThat(reg.getStackRule("unknown")).isEqualTo("additive");
    }
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=ModifierTypeRegistryTest -q
```
期望：FAIL (class not found)

- [ ] **Step 3: 创建 ModifierTypeRegistry.java**

```java
package com.epic.engine.core;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModifierTypeRegistry {

    private final Map<String, ModifierType> types = new ConcurrentHashMap<>();

    public void register(ModifierType type) {
        types.put(type.id(), type);
    }

    @SuppressWarnings("unchecked")
    public void loadFromList(List<Map<String, Object>> list) {
        for (Map<String, Object> entry : list) {
            String id = (String) entry.get("id");
            String label = (String) entry.getOrDefault("label", id);
            String stackRule = (String) entry.getOrDefault("stack_rule", "additive");
            int basePriority = entry.containsKey("base_priority")
                    ? ((Number) entry.get("base_priority")).intValue() : 0;
            types.put(id, new ModifierType(id, label, stackRule, basePriority));
        }
    }

    @SuppressWarnings("unchecked")
    public void loadFromModPath(Path modPath) {
        Path yamlPath = modPath.resolve("modifier_types.yaml");
        if (!Files.exists(yamlPath)) return;
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yaml.load(is);
            if (data != null && data.containsKey("modifier_types")) {
                loadFromList((List<Map<String, Object>>) data.get("modifier_types"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load modifier_types.yaml: " + yamlPath, e);
        }
    }

    public ModifierType get(String id) {
        return types.get(id);
    }

    public String getStackRule(String typeId) {
        if (typeId == null) return ModifierType.ADDITIVE;
        ModifierType t = types.get(typeId);
        return t != null ? t.stackRule() : ModifierType.ADDITIVE;
    }

    public int getBasePriority(String typeId) {
        if (typeId == null) return 0;
        ModifierType t = types.get(typeId);
        return t != null ? t.basePriority() : 0;
    }
}
```

- [ ] **Step 4: 确认测试通过**

```
cd backend && mvn test -Dtest=ModifierTypeRegistryTest -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/ModifierTypeRegistry.java
git add backend/src/test/java/com/epic/engine/core/ModifierTypeRegistryTest.java
git commit -m "feat: 添加 ModifierTypeRegistry，支持从 YAML 加载 stack_rule"
```

---

## Task 4: ModifierDiff record + ModifierChain 增强

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/ModifierDiff.java`
- Modify: `backend/src/main/java/com/epic/engine/core/ModifierChain.java`

- [ ] **Step 1: 写失败测试**

在 `ModifierChainTest.java` 末尾添加：

```java
@Test
void recalculateWithTracking_recordsDeltas() {
    Entity entity = new Entity("p1");
    Component stats = new Component("CombatStats");
    stats.set("attack", 10);
    entity.addComponent(stats);

    ModifierChain chain = new ModifierChain(entity);
    chain.addModifier(new Modifier("class_warrior", "warrior", "战士", "class", 180,
            e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 3)));

    List<ModifierDiff> diffs = chain.recalculateWithTracking();

    assertThat(diffs).hasSize(1);
    assertThat(diffs.get(0).modifierId()).isEqualTo("class_warrior");
    assertThat(diffs.get(0).componentDeltas().get("CombatStats").get("attack")).isEqualTo(3L);
}

@Test
void recalculate_onlyRestoresBaseStateComponents() {
    Entity entity = new Entity("p2");
    Component stats = new Component("CombatStats");
    stats.set("attack", 10);
    entity.addComponent(stats);

    ModifierChain chain = new ModifierChain(entity);

    // 添加非-base 组件（setBase 之后）
    Component slots = new Component("EquipmentSlots");
    slots.set("weapon", "iron_sword");
    entity.addComponent(slots);

    chain.addModifier(new Modifier("cls", "class", 180,
            e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 5)));
    chain.recalculate();

    // EquipmentSlots 不受 recalculate 影响
    assertThat(entity.hasComponent("EquipmentSlots")).isTrue();
    assertThat(entity.getComponent("EquipmentSlots").getString("weapon")).isEqualTo("iron_sword");
    assertThat(entity.getComponent("CombatStats").getInt("attack")).isEqualTo(15);
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=ModifierChainTest -q
```
期望：FAIL (ModifierDiff not found, selective restore not implemented)

- [ ] **Step 3: 创建 ModifierDiff.java**

```java
package com.epic.engine.core;

import java.util.Map;

public record ModifierDiff(
        String modifierId,
        String typeId,
        String label,
        Map<String, Map<String, Long>> componentDeltas
) {}
```

- [ ] **Step 4: 替换 ModifierChain.java**

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
        snapshotAll();
    }

    ModifierChain(Entity entity, boolean autoSnapshot) {
        this.entity = entity;
        if (autoSnapshot) snapshotAll();
    }

    private void snapshotAll() {
        baseState.clear();
        for (Component c : entity.getAllComponents()) {
            baseState.put(c.getType(), c.copy());
        }
        baseTags.clear();
        baseTags.addAll(entity.getTags());
    }

    public void setBase() {
        snapshotAll();
    }

    public void setBaseSelective(List<String> componentTypes) {
        baseState.clear();
        baseTags.clear();
        baseTags.addAll(entity.getTags());
        for (String type : componentTypes) {
            Component comp = entity.getComponent(type);
            if (comp != null) baseState.put(type, comp.copy());
        }
    }

    public Map<String, Component> getBaseState() {
        return Collections.unmodifiableMap(baseState);
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

    public void removeByTypeId(String typeId) {
        modifiers.removeIf(m -> typeId.equals(m.typeId()));
    }

    private void restoreBaseState() {
        // Only remove/restore components that are in base state
        for (String type : baseState.keySet()) {
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
    }

    public void recalculate() {
        restoreBaseState();
        for (Modifier mod : modifiers) {
            mod.apply().accept(entity);
        }
    }

    public List<ModifierDiff> recalculateWithTracking() {
        restoreBaseState();
        List<ModifierDiff> diffs = new ArrayList<>();
        for (Modifier mod : modifiers) {
            Map<String, Map<String, Long>> before = snapshotLongs();
            mod.apply().accept(entity);
            Map<String, Map<String, Long>> after = snapshotLongs();
            diffs.add(new ModifierDiff(mod.id(), mod.typeId(), mod.label(), computeDelta(before, after)));
        }
        return diffs;
    }

    private Map<String, Map<String, Long>> snapshotLongs() {
        Map<String, Map<String, Long>> snap = new LinkedHashMap<>();
        for (Component c : entity.getAllComponents()) {
            Map<String, Long> fields = new LinkedHashMap<>();
            c.getAll().forEach((k, v) -> {
                if (v instanceof Number n) fields.put(k, n.longValue());
            });
            if (!fields.isEmpty()) snap.put(c.getType(), fields);
        }
        return snap;
    }

    private Map<String, Map<String, Long>> computeDelta(
            Map<String, Map<String, Long>> before,
            Map<String, Map<String, Long>> after) {
        Map<String, Map<String, Long>> delta = new LinkedHashMap<>();
        after.forEach((comp, fields) -> {
            Map<String, Long> compDelta = new LinkedHashMap<>();
            fields.forEach((field, afterVal) -> {
                long beforeVal = before.getOrDefault(comp, Map.of()).getOrDefault(field, 0L);
                long diff = afterVal - beforeVal;
                if (diff != 0) compDelta.put(field, diff);
            });
            if (!compDelta.isEmpty()) delta.put(comp, compDelta);
        });
        return delta;
    }

    public List<Modifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
```

- [ ] **Step 5: 确认测试通过**

```
cd backend && mvn test -Dtest=ModifierChainTest -q
```
期望：全部 PASS（包括已有的 4 个测试 + 新加的 2 个）

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/ModifierDiff.java
git add backend/src/main/java/com/epic/engine/core/ModifierChain.java
git commit -m "feat: ModifierChain 支持选择性 restore 和 diff 追踪"
```

---

## Task 5: ModifierChainService

**Files:**
- Create: `backend/src/main/java/com/epic/engine/core/ModifierChainService.java`
- Create: `backend/src/test/java/com/epic/engine/core/ModifierChainServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierChainServiceTest {

    EventBus bus;
    EntityStore store;
    ModifierTypeRegistry typeRegistry;
    ModifierChainService service;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        typeRegistry = new ModifierTypeRegistry();
        typeRegistry.loadFromList(List.of(
            java.util.Map.of("id", "class", "label", "职业", "stack_rule", "exclusive", "base_priority", 180),
            java.util.Map.of("id", "equipment", "label", "装备", "stack_rule", "additive", "base_priority", 50)
        ));
        service = new ModifierChainService(bus, store, typeRegistry);
    }

    @Test
    void addModifier_exclusive_removesOldSameType() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("class_warrior", "class", "战士", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));
        service.addModifier("p1", new Modifier("class_mage", "class", "法师", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 1)));

        // exclusive → old warrior removed, only mage applies
        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(11);
    }

    @Test
    void addModifier_additive_keepsAll() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("sword", "equipment", "铁剑", "equip_sword", 50,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 8)));
        service.addModifier("p1", new Modifier("ring", "equipment", "攻击戒", "equip_ring", 50,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));

        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(21);
    }

    @Test
    void recalculate_firesBeforeAfterEvents() {
        Entity e = new Entity("p1");
        Component health = new Component("Health");
        health.set("hp", 80);
        health.set("maxHp", 100);
        e.addComponent(health);
        store.add(e);
        service.setBase("p1");

        List<String> fired = new ArrayList<>();
        bus.on("entity.before_recalculate", 100, ev -> fired.add("before"));
        bus.on("entity.after_recalculate", 100, ev -> fired.add("after"));

        service.recalculate("p1");

        assertThat(fired).containsExactly("before", "after");
    }

    @Test
    void getContributions_returnsPerModifierDeltas() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("class_warrior", "class", "战士", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));

        List<ModifierDiff> diffs = service.getContributions("p1");
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).componentDeltas().get("CombatStats").get("attack")).isEqualTo(3L);
    }
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=ModifierChainServiceTest -q
```
期望：FAIL (class not found)

- [ ] **Step 3: 创建 ModifierChainService.java**

```java
package com.epic.engine.core;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModifierChainService {

    private final EventBus bus;
    private final EntityStore store;
    private final ModifierTypeRegistry typeRegistry;

    private final Map<String, ModifierChain> chains = new ConcurrentHashMap<>();
    private final Map<String, List<ModifierDiff>> contributions = new ConcurrentHashMap<>();

    public ModifierChainService(EventBus bus, EntityStore store, ModifierTypeRegistry typeRegistry) {
        this.bus = bus;
        this.store = store;
        this.typeRegistry = typeRegistry;
    }

    private ModifierChain getOrCreate(String entityId) {
        return chains.computeIfAbsent(entityId, k -> {
            Entity entity = store.get(k);
            if (entity == null) throw new IllegalArgumentException("Entity not found: " + k);
            return new ModifierChain(entity, false);
        });
    }

    public void setBase(String entityId) {
        Entity entity = store.get(entityId);
        if (entity == null) return;
        getOrCreate(entityId).setBase();
    }

    public void setBaseSelective(String entityId, List<String> componentTypes) {
        Entity entity = store.get(entityId);
        if (entity == null) return;
        getOrCreate(entityId).setBaseSelective(componentTypes);
    }

    public void addModifier(String entityId, Modifier modifier) {
        ModifierChain chain = getOrCreate(entityId);
        String stackRule = typeRegistry.getStackRule(modifier.typeId());
        if (ModifierType.EXCLUSIVE.equals(stackRule)) {
            chain.removeByTypeId(modifier.typeId());
        }
        chain.addModifier(modifier);
        recalculate(entityId);
    }

    public void removeModifier(String entityId, String modifierId) {
        ModifierChain chain = chains.get(entityId);
        if (chain == null) return;
        chain.removeById(modifierId);
        recalculate(entityId);
    }

    public void removeBySource(String entityId, String source) {
        ModifierChain chain = chains.get(entityId);
        if (chain == null) return;
        chain.removeBySource(source);
        recalculate(entityId);
    }

    public void recalculate(String entityId) {
        Entity entity = store.get(entityId);
        ModifierChain chain = chains.get(entityId);
        if (entity == null || chain == null) return;

        Map<String, Object> scratch = new HashMap<>();
        GameEvent before = new GameEvent("entity.before_recalculate");
        before.set("entity", entity);
        before.set("scratch", scratch);
        bus.fire("entity.before_recalculate", before);

        List<ModifierDiff> diffs = chain.recalculateWithTracking();
        contributions.put(entityId, diffs);

        GameEvent after = new GameEvent("entity.after_recalculate");
        after.set("entity", entity);
        after.set("scratch", scratch);
        bus.fire("entity.after_recalculate", after);
    }

    public List<ModifierDiff> getContributions(String entityId) {
        return contributions.getOrDefault(entityId, List.of());
    }

    public Map<String, Component> getBaseState(String entityId) {
        ModifierChain chain = chains.get(entityId);
        return chain != null ? chain.getBaseState() : null;
    }

    public void clearChain(String entityId) {
        chains.remove(entityId);
        contributions.remove(entityId);
    }
}
```

- [ ] **Step 4: 确认测试通过**

```
cd backend && mvn test -Dtest=ModifierChainServiceTest -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/ModifierChainService.java
git add backend/src/test/java/com/epic/engine/core/ModifierChainServiceTest.java
git commit -m "feat: 添加 ModifierChainService，支持 stack_rule 和 before/after_recalculate 事件"
```

---

## Task 6: ScriptRuntime EngineApi 扩展

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java`

- [ ] **Step 1: 写失败测试**

在 `ScriptRuntimeTest.java` 末尾添加：

```java
@Test
void script_addModifier_recalculatesEntity() {
    ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
    ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
    runtime = new ScriptRuntime(bus, store, chainService, typeReg);

    Entity player = new Entity("p1");
    Component stats = new Component("CombatStats");
    stats.set("attack", 10);
    player.addComponent(stats);
    store.add(player);

    String script = """
        engine.setBase("p1");
        engine.addModifier("p1", {
            id: "class_warrior",
            typeId: "class",
            label: "战士",
            priority: 180,
            apply: function(entity) {
                var s = entity.getComponent("CombatStats");
                s.set("attack", s.getInt("attack") + 3);
            }
        });
        """;
    runtime.execute(script, "test.js");

    assertThat(player.getComponent("CombatStats").getInt("attack")).isEqualTo(13);
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=ScriptRuntimeTest -q
```
期望：FAIL (ScriptRuntime 没有 3-arg 构造器)

- [ ] **Step 3: 修改 ScriptRuntime.java 构造器**

把当前构造器：
```java
public ScriptRuntime(EventBus bus, EntityStore store) {
    this.bus = bus;
    this.store = store;
    ...
    bindApi();
}
```
改为：
```java
private ModifierChainService modifierChainService;
private ModifierTypeRegistry modifierTypeRegistry;

public ScriptRuntime(EventBus bus, EntityStore store) {
    this(bus, store, null, null);
}

public ScriptRuntime(EventBus bus, EntityStore store,
                     ModifierChainService modifierChainService,
                     ModifierTypeRegistry modifierTypeRegistry) {
    this.bus = bus;
    this.store = store;
    this.modifierChainService = modifierChainService;
    this.modifierTypeRegistry = modifierTypeRegistry;
    this.context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> false)
            .build();
    bindApi();
}
```

- [ ] **Step 4: 在 EngineApi 内部添加 5 个新方法**

在 EngineApi class 内（现有方法后）添加：

```java
@HostAccess.Export
public void setBase(String entityId) {
    if (modifierChainService != null) {
        modifierChainService.setBase(entityId);
    }
}

@HostAccess.Export
public void setBaseSelective(String entityId, Value componentTypes) {
    if (modifierChainService == null) return;
    List<String> types = new java.util.ArrayList<>();
    for (int i = 0; i < componentTypes.getArraySize(); i++) {
        types.add(componentTypes.getArrayElement(i).asString());
    }
    modifierChainService.setBaseSelective(entityId, types);
}

@HostAccess.Export
public void addModifier(String entityId, Value config) {
    if (modifierChainService == null) return;
    String id = config.getMember("id").asString();
    String typeId = config.hasMember("typeId") ? config.getMember("typeId").asString() : null;
    String label = config.hasMember("label") ? config.getMember("label").asString() : id;
    Value applyFn = config.getMember("apply");

    int priority;
    if (config.hasMember("priority")) {
        priority = config.getMember("priority").asInt();
    } else if (typeId != null && modifierTypeRegistry != null) {
        priority = modifierTypeRegistry.getBasePriority(typeId);
    } else {
        priority = 0;
    }

    String source = typeId != null ? typeId + "_" + id : id;
    Modifier modifier = new Modifier(id, typeId, label, source, priority, entity -> {
        synchronized (ScriptRuntime.this) {
            applyFn.execute(entity);
        }
    });
    modifierChainService.addModifier(entityId, modifier);
}

@HostAccess.Export
public void removeModifier(String entityId, String modifierId) {
    if (modifierChainService != null) {
        modifierChainService.removeModifier(entityId, modifierId);
    }
}

@HostAccess.Export
public void recalculate(String entityId) {
    if (modifierChainService != null) {
        modifierChainService.recalculate(entityId);
    }
}
```

- [ ] **Step 5: 确认测试通过**

```
cd backend && mvn test -Dtest=ScriptRuntimeTest -q
```
期望：全部 PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/epic/engine/script/ScriptRuntime.java
git commit -m "feat: ScriptRuntime 暴露 addModifier/removeModifier/setBase/recalculate JS API"
```

---

## Task 7: EngineConfig + EngineBootstrap 接线

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/core/EngineConfig.java`
- Modify: `backend/src/main/java/com/epic/engine/core/EngineBootstrap.java`

- [ ] **Step 1: 修改 EngineConfig.java**

把 `scriptRuntime` bean 替换：
```java
@Bean
public ScriptRuntime scriptRuntime(EventBus eventBus, EntityStore entityStore,
                                    ModifierChainService modifierChainService,
                                    ModifierTypeRegistry modifierTypeRegistry) {
    return new ScriptRuntime(eventBus, entityStore, modifierChainService, modifierTypeRegistry);
}
```

更新 `engineBootstrap` bean，添加 `ModifierTypeRegistry` 参数：
```java
@Bean
public EngineBootstrap engineBootstrap(ModuleLoader moduleLoader, SchemaRegistry schemaRegistry,
                                       EventBus eventBus, EntityStore entityStore,
                                       ScriptRuntime scriptRuntime, PersistenceService persistenceService,
                                       SessionService sessionService, BuffService buffService,
                                       ModifierTypeRegistry modifierTypeRegistry) {
    return new EngineBootstrap(moduleLoader, schemaRegistry, eventBus, entityStore, Path.of(modsPath),
            scriptRuntime, persistenceService, sessionService, buffService, modifierTypeRegistry);
}
```

- [ ] **Step 2: 修改 EngineBootstrap.java**

构造器添加 `ModifierTypeRegistry modifierTypeRegistry` 参数：
```java
private final ModifierTypeRegistry modifierTypeRegistry;

public EngineBootstrap(..., ModifierTypeRegistry modifierTypeRegistry) {
    ...
    this.modifierTypeRegistry = modifierTypeRegistry;
}
```

在 `boot()` 方法的 schema 加载循环后添加类型加载：
```java
for (ModuleDescriptor mod : modules) {
    schemaRegistry.loadFromModPath(mod.path());
    modifierTypeRegistry.loadFromModPath(mod.path());  // 新增
}
```

在 `persistenceService.loadAllPersistent()` 后添加 entity.loaded 事件：
```java
persistenceService.loadAllPersistent();

// Fire entity.loaded for each persistent entity so handlers can re-register Modifiers
for (Entity entity : entityStore.all()) {
    GameEvent loadedEvent = new GameEvent("entity.loaded");
    loadedEvent.set("entity", entity);
    eventBus.fire("entity.loaded", loadedEvent);
}
```

- [ ] **Step 3: 运行完整测试**

```
cd backend && mvn test -q
```
期望：全部 PASS（新 bean 接线正确）

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/epic/engine/core/EngineConfig.java
git add backend/src/main/java/com/epic/engine/core/EngineBootstrap.java
git commit -m "feat: 接线 ModifierChainService 和 ModifierTypeRegistry，启动时 fire entity.loaded"
```

---

## Task 8: PersistenceService 保存 base state 值

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/persistence/PersistenceService.java`

- [ ] **Step 1: 写失败测试**

在 `PersistenceServiceTest.java` 末尾添加：

```java
@Test
void save_withChain_savesBaseStateValues() {
    ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
    EventBus bus = new EventBus();
    ModifierChainService chainService = new ModifierChainService(bus, entityStore, typeReg);

    Entity e = new Entity("char_test");
    Component stats = new Component("CombatStats");
    stats.set("attack", 10);
    e.addComponent(stats);
    Component slots = new Component("EquipmentSlots");
    slots.set("weapon", "iron_sword");
    e.addComponent(slots);
    e.addTag("persistent");
    entityStore.add(e);

    // setBase with only stat components
    chainService.setBaseSelective("char_test", List.of("CombatStats"));
    // Add modifier that inflates attack
    chainService.addModifier("char_test", new Modifier("sword", "equipment", "铁剑", "equipment_sword", 50,
            ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 8)));

    // Current state: attack=18, EquipmentSlots.weapon="iron_sword"
    assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(18);

    // Inject chainService and save
    persistenceService.setModifierChainService(chainService);
    persistenceService.save(e);

    // Load and verify: attack should be BASE value (10), not 18
    Entity loaded = persistenceService.load("char_test");
    assertThat(loaded.getComponent("CombatStats").getInt("attack")).isEqualTo(10);
    // Non-stat component preserved as-is
    assertThat(loaded.getComponent("EquipmentSlots").getString("weapon")).isEqualTo("iron_sword");
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=PersistenceServiceTest#save_withChain_savesBaseStateValues -q
```
期望：FAIL

- [ ] **Step 3: 修改 PersistenceService.java**

在类顶部添加字段和 setter：
```java
private ModifierChainService modifierChainService;

public void setModifierChainService(ModifierChainService modifierChainService) {
    this.modifierChainService = modifierChainService;
}
```

在 `EngineConfig.java` 的 `engineBootstrap` bean 创建后，添加：
```java
// 在 EngineConfig 的 engineBootstrap bean 方法里，注入 ModifierChainService 到 PersistenceService
// 见后续 Task — 这里先通过 EngineBootstrap 传入
```

替换 `PersistenceService.save()` 方法：
```java
public void save(Entity entity) {
    if (!entity.hasTag("persistent")) return;

    try {
        Map<String, Map<String, Object>> components = new LinkedHashMap<>();

        if (modifierChainService != null) {
            Map<String, Component> baseState = modifierChainService.getBaseState(entity.getId());
            if (baseState != null) {
                // Save base values for base-state components
                for (Map.Entry<String, Component> entry : baseState.entrySet()) {
                    components.put(entry.getKey(), entry.getValue().getAll());
                }
                // Save current values for non-base components (EquipmentSlots, Inventory, etc.)
                for (Component c : entity.getAllComponents()) {
                    if (!baseState.containsKey(c.getType())) {
                        components.put(c.getType(), c.getAll());
                    }
                }
            } else {
                for (Component c : entity.getAllComponents()) {
                    components.put(c.getType(), c.getAll());
                }
            }
        } else {
            for (Component c : entity.getAllComponents()) {
                components.put(c.getType(), c.getAll());
            }
        }

        String componentsJson = mapper.writeValueAsString(components);
        String tagsJson = mapper.writeValueAsString(entity.getTags());
        repository.save(new EntityData(entity.getId(), componentsJson, tagsJson));
    } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize entity: " + entity.getId(), e);
    }
}
```

在 `EngineConfig.java` 添加 `ModifierChainService` 注入到 `PersistenceService`。将 `EngineBootstrap` bean 更新为：
```java
@Bean
public EngineBootstrap engineBootstrap(..., ModifierChainService modifierChainService,
                                       PersistenceService persistenceService, ...) {
    persistenceService.setModifierChainService(modifierChainService);  // 注入
    return new EngineBootstrap(...);
}
```

- [ ] **Step 4: 确认测试通过**

```
cd backend && mvn test -Dtest=PersistenceServiceTest -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/persistence/PersistenceService.java
git add backend/src/main/java/com/epic/engine/core/EngineConfig.java
git commit -m "feat: PersistenceService 保存 ModifierChain base state 值而非 post-modifier 值"
```

---

## Task 9: modifier_types.yaml

**Files:**
- Create: `mods/base-rules/modifier_types.yaml`

- [ ] **Step 1: 创建文件**

```yaml
modifier_types:
  - id: race
    label: 种族
    stack_rule: exclusive
    base_priority: 200

  - id: class
    label: 职业
    stack_rule: exclusive
    base_priority: 180

  - id: subclass
    label: 子职业
    stack_rule: exclusive
    base_priority: 160

  - id: level
    label: 等级成长
    stack_rule: exclusive
    base_priority: 90

  - id: equipment
    label: 装备
    stack_rule: additive
    base_priority: 50

  - id: buff
    label: 增益/减益
    stack_rule: independent
    base_priority: 10
```

- [ ] **Step 2: 运行测试（验证引擎启动能加载）**

```
cd backend && mvn test -Dtest=EngineBootTest -q
```
期望：PASS

- [ ] **Step 3: Commit**

```bash
git add mods/base-rules/modifier_types.yaml
git commit -m "feat: 添加 modifier_types.yaml 定义职业/装备/等级/buff 类型"
```

---

## Task 10: 更新 select.js 使用 ModifierChain

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js`

核心变化：在 `action.confirm_character` handler 中：
1. 添加 stat 组件后调用 `engine.setBase(charId)`
2. 用 `engine.addModifier()` 替换手动的 `classSchema.modifiers()` 应用逻辑

- [ ] **Step 1: 在 `action.confirm_character` handler 中定位并替换职业修正值代码**

找到注释 `// 应用职业修正值` 开始的块（第 80-99 行），整个替换为：

```javascript
    // Snapshot base state（此时只有 stat 组件）
    engine.setBase(charId);

    // 添加非 stat 组件（Character、Name、Skills 等）
```

（注：addBase 调用移到 Name/Character 组件添加之前）

具体：把 select.js 中 `action.confirm_character` handler 的关键顺序调整如下：

```javascript
engine.on("action.confirm_character", 100, function(event) {
    var token = event.get("sessionToken");
    var name = event.get("name");
    var classId = event.get("class");

    var charId = "char_" + engine.now();

    var charSchema = schemas.get("character");
    var classSchema = schemas.get(classId);

    var entity = engine.createEntity(charId);

    // 1. 添加 base stat 组件
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

    // 2. 添加标签和到 store（setBase 需要实体在 store 中）
    entity.addTag("persistent");
    entity.addTag("player");
    entity.addTag("session:" + token);
    store.add(entity);

    // 3. Snapshot base state（仅包含 stat 组件）
    engine.setBase(charId);

    // 4. 添加非 stat 组件
    var charComp = engine.newComponent("Character");
    charComp.set("name", name);
    charComp.set("level", 1);
    charComp.set("xp", 0);
    charComp.set("classId", classId);
    charComp.set("classLabel", classSchema !== null ? classSchema.label() : classId);
    entity.addComponent(charComp);

    var nameComp = engine.newComponent("Name");
    nameComp.set("value", name);
    entity.addComponent(nameComp);

    // Skills 组件
    var skills = engine.newComponent("Skills");
    var skillList = engine.newList();
    var atkSkill = engine.newMap();
    atkSkill.put("id", "basic_attack"); atkSkill.put("level", 1); atkSkill.put("cooldown", 0);
    skillList.add(atkSkill);
    var defSkill = engine.newMap();
    defSkill.put("id", "defend"); defSkill.put("level", 1); defSkill.put("cooldown", 0);
    skillList.add(defSkill);
    var fleeSkill = engine.newMap();
    fleeSkill.put("id", "flee"); fleeSkill.put("level", 1); fleeSkill.put("cooldown", 0);
    skillList.add(fleeSkill);
    if (classId === "mage") {
        var mageSkills = ["fireball", "light_field"];
        for (var m = 0; m < mageSkills.length; m++) {
            var ms = engine.newMap();
            ms.put("id", mageSkills[m]); ms.put("level", 1); ms.put("cooldown", 0);
            skillList.add(ms);
        }
    }
    skills.set("list", skillList);
    entity.addComponent(skills);

    // EquipmentSlots 和 Inventory
    var slotsComp = engine.newComponent("EquipmentSlots");
    slotsComp.set("weapon", null);
    slotsComp.set("armor", null);
    slotsComp.set("accessory", null);
    entity.addComponent(slotsComp);

    var invComp = engine.newComponent("Inventory");
    invComp.set("items", engine.newList());
    entity.addComponent(invComp);

    // Experience 组件
    var expComp = engine.newComponent("Experience");
    expComp.set("xp", 0);
    expComp.set("level", 1);
    expComp.set("pendingPoints", 0);
    entity.addComponent(expComp);

    // 5. 注册职业 Modifier（exclusive，替换旧职业）
    var capturedClassSchema = classSchema;
    var capturedClassId = classId;
    engine.addModifier(charId, {
        typeId: "class",
        id: "class_" + classId,
        label: classSchema !== null ? classSchema.label() : classId,
        apply: function(ent) {
            if (capturedClassSchema === null || capturedClassSchema.modifiers() === null) return;
            var mods = capturedClassSchema.modifiers();
            for (var i = 0; i < mods.size(); i++) {
                var mod = mods.get(i);
                var dotIdx = mod.field().indexOf(".");
                var compName = mod.field().substring(0, dotIdx);
                var fieldName = mod.field().substring(dotIdx + 1);
                var valueStr = mod.value();
                var comp = ent.getComponent(compName);
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
    });

    persistence.save(entity);
    sessions.setActiveCharacter(token, charId);
});
```

- [ ] **Step 2: 运行角色创建相关测试**

```
cd backend && mvn test -Dtest=CharacterFlowTest -q
```
期望：PASS（角色创建和选择流程正常）

- [ ] **Step 3: 运行全部测试**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 4: Commit**

```bash
git add mods/base-rules/handlers/character/select.js
git commit -m "feat: 角色创建改用 ModifierChain 注册职业加成"
```

---

## Task 11: recalculate_hooks.js（hp/mp 保存 + entity.loaded）

**Files:**
- Create: `mods/base-rules/handlers/character/recalculate_hooks.js`

- [ ] **Step 1: 创建文件**

```javascript
// 在 recalculate 前保存运行时值（hp/mp）
engine.on("entity.before_recalculate", 100, function(event) {
    var entity = event.get("entity");
    var scratch = event.get("scratch");
    var health = entity.getComponent("Health");
    if (health !== null) scratch.put("hp", health.getInt("hp"));
    var mana = entity.getComponent("Mana");
    if (mana !== null) scratch.put("mp", mana.getInt("mp"));
});

// 在 recalculate 后恢复运行时值，截断到新的 max
engine.on("entity.after_recalculate", 100, function(event) {
    var entity = event.get("entity");
    var scratch = event.get("scratch");
    var health = entity.getComponent("Health");
    if (health !== null && scratch.containsKey("hp")) {
        health.set("hp", Math.min(scratch.get("hp"), health.getInt("maxHp")));
    }
    var mana = entity.getComponent("Mana");
    if (mana !== null && scratch.containsKey("mp")) {
        mana.set("mp", Math.min(scratch.get("mp"), mana.getInt("maxMp")));
    }
});

// 游戏重启后重新注册 Modifier（entity.loaded 事件）
engine.on("entity.loaded", 100, function(event) {
    var entity = event.get("entity");
    var charComp = entity.getComponent("Character");
    if (charComp === null) return;

    var classId = charComp.getString("classId");
    var classSchema = schemas.get(classId);

    // 重建 base state（只包含 stat 组件）
    var statCompTypes = engine.newList();
    var baseKeys = schemas.get("character").baseComponents().keySet().iterator();
    while (baseKeys.hasNext()) {
        statCompTypes.add(baseKeys.next());
    }
    engine.setBaseSelective(entity.getId(), statCompTypes);

    // 重新注册职业 Modifier
    var capturedClassSchema = classSchema;
    engine.addModifier(entity.getId(), {
        typeId: "class",
        id: "class_" + classId,
        label: classSchema !== null ? classSchema.label() : classId,
        apply: function(ent) {
            if (capturedClassSchema === null || capturedClassSchema.modifiers() === null) return;
            var mods = capturedClassSchema.modifiers();
            for (var i = 0; i < mods.size(); i++) {
                var mod = mods.get(i);
                var dotIdx = mod.field().indexOf(".");
                var compName = mod.field().substring(0, dotIdx);
                var fieldName = mod.field().substring(dotIdx + 1);
                var valueStr = mod.value();
                var comp = ent.getComponent(compName);
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
    });

    // 重新注册装备 Modifier（如有装备）
    var slots = entity.getComponent("EquipmentSlots");
    if (slots !== null) {
        var slotNames = ["weapon", "armor", "accessory"];
        for (var s = 0; s < slotNames.length; s++) {
            var itemId = slots.get(slotNames[s]);
            if (itemId !== null) {
                registerEquipmentModifier(entity.getId(), itemId);
            }
        }
    }

    // 重新注册等级成长 Modifier（如有 Experience 组件）
    var exp = entity.getComponent("Experience");
    if (exp !== null && exp.getInt("level") > 1) {
        registerLevelGrowthModifier(entity.getId(), exp.getInt("level"));
    }
});
```

注意：`registerEquipmentModifier` 和 `registerLevelGrowthModifier` 函数在后续 Task 12 和 Task 13 中定义。

- [ ] **Step 2: 运行测试**

```
cd backend && mvn test -Dtest=CharacterFlowTest -q
```
期望：PASS

- [ ] **Step 3: Commit**

```bash
git add mods/base-rules/handlers/character/recalculate_hooks.js
git commit -m "feat: 添加 recalculate_hooks.js 保存/恢复 hp/mp 并在重启后重注册 Modifier"
```

---

## Task 12: 装备系统

**Files:**
- Create: `mods/base-rules/item_rarity.yaml`
- Create: `mods/base-rules/entities/items.yaml`
- Create: `mods/base-rules/handlers/equipment/equip.js`

- [ ] **Step 1: 创建 item_rarity.yaml**

```yaml
item_rarities:
  - id: common
    label: 普通
    color: "#ffffff"
  - id: uncommon
    label: 优质
    color: "#4ade80"
  - id: rare
    label: 稀有
    color: "#60a5fa"
  - id: epic
    label: 史诗
    color: "#a78bfa"
  - id: legendary
    label: 传说
    color: "#ffd93d"
```

- [ ] **Step 2: 创建 items.yaml**

```yaml
items:
  - id: iron_sword
    meta:
      name: 铁剑
      type: weapon
      rarity: common
    stats:
      attack: 8

  - id: leather_armor
    meta:
      name: 皮甲
      type: armor
      rarity: common
    stats:
      defense: 5
```

- [ ] **Step 3: 创建 equip.js**

```javascript
// 在 world.init 时加载物品数据到 EntityStore
engine.on("world.init", 80, function(event) {
    var itemsData = engine.loadYaml("entities/items.yaml");
    var items = itemsData.get("items");
    for (var i = 0; i < items.size(); i++) {
        var itemDef = items.get(i);
        var id = itemDef.get("id");
        var item = store.get(id);
        if (item === null) {
            item = engine.createEntity(id);
            item.addTag("item");
            store.add(item);
        }
        var meta = itemDef.get("meta");
        var metaComp = engine.newComponent("ItemMeta");
        metaComp.set("name", meta.get("name"));
        metaComp.set("type", meta.get("type"));
        metaComp.set("rarity", meta.get("rarity"));
        item.addComponent(metaComp);

        var statsMap = itemDef.get("stats");
        var statsComp = engine.newComponent("ItemStats");
        var statsKeys = statsMap.keySet().iterator();
        while (statsKeys.hasNext()) {
            var key = statsKeys.next();
            statsComp.set(key, statsMap.get(key));
        }
        item.addComponent(statsComp);
    }
});

// 注册装备 Modifier 的辅助函数（供 entity.loaded 重用）
function registerEquipmentModifier(entityId, itemId) {
    var item = store.get(itemId);
    if (item === null || !item.hasComponent("ItemStats")) return;
    var stats = item.getComponent("ItemStats");
    var statsCopy = engine.newMap();
    var statsKeys = stats.getAll().keySet().iterator();
    while (statsKeys.hasNext()) {
        var k = statsKeys.next();
        statsCopy.put(k, stats.getInt(k));
    }
    engine.addModifier(entityId, {
        typeId: "equipment",
        id: "equip_" + itemId,
        label: item.getComponent("ItemMeta").getString("name"),
        apply: function(entity) {
            var combatStats = entity.getComponent("CombatStats");
            if (combatStats === null) return;
            var keys = statsCopy.keySet().iterator();
            while (keys.hasNext()) {
                var k = keys.next();
                if (combatStats.has(k)) {
                    combatStats.set(k, combatStats.getInt(k) + statsCopy.get(k));
                }
            }
        }
    });
}

// 装备动作
engine.on("action.equip", 100, function(event) {
    var playerId = event.get("playerId");
    var itemId = event.get("itemId");
    var player = store.get(playerId);
    if (player === null) return;

    var item = store.get(itemId);
    if (item === null || !item.hasComponent("ItemMeta")) return;
    var slotName = item.getComponent("ItemMeta").getString("type");

    var slots = player.getComponent("EquipmentSlots");
    if (slots === null) return;

    // 卸下已装备的旧物品
    var oldItemId = slots.get(slotName);
    if (oldItemId !== null) {
        engine.removeModifier(playerId, "equip_" + oldItemId);
    }

    // 更新装备槽
    slots.set(slotName, itemId);

    // 注册新 Modifier（内部触发 recalculate）
    registerEquipmentModifier(playerId, itemId);

    persistence.save(player);
});

// 卸装动作
engine.on("action.unequip", 100, function(event) {
    var playerId = event.get("playerId");
    var slotName = event.get("slot");
    var player = store.get(playerId);
    if (player === null) return;

    var slots = player.getComponent("EquipmentSlots");
    if (slots === null) return;

    var itemId = slots.get(slotName);
    if (itemId === null) return;

    engine.removeModifier(playerId, "equip_" + itemId);
    slots.set(slotName, null);
    persistence.save(player);
});
```

- [ ] **Step 4: 运行测试**

```
cd backend && mvn test -Dtest=EngineBootTest -q
```
期望：PASS（引擎启动加载物品数据不报错）

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/item_rarity.yaml
git add mods/base-rules/entities/items.yaml
git add mods/base-rules/handlers/equipment/equip.js
git commit -m "feat: 装备系统 — equip/unequip 动作 + 装备 Modifier 注册"
```

---

## Task 13: 等级成长系统

**Files:**
- Create: `mods/base-rules/handlers/character/leveling.js`

- [ ] **Step 1: 创建 leveling.js**

```javascript
// XP 阈值计算（简单线性：level * 100）
function xpForLevel(level) {
    return level * 100;
}

// 注册等级成长 Modifier 的辅助函数（供 entity.loaded 重用）
function registerLevelGrowthModifier(entityId, level) {
    var capturedLevel = level;
    engine.addModifier(entityId, {
        typeId: "level",
        id: "level_growth",
        label: level + "级成长",
        apply: function(entity) {
            var health = entity.getComponent("Health");
            if (health !== null) {
                health.set("maxHp", health.getInt("maxHp") + capturedLevel * 5);
            }
            var stats = entity.getComponent("CombatStats");
            if (stats !== null) {
                stats.set("attack", stats.getInt("attack") + capturedLevel);
            }
        }
    });
}

// 获得经验值
engine.on("action.gain_xp", 100, function(event) {
    var playerId = event.get("playerId");
    var amount = event.get("amount");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null) return;

    var currentXp = exp.getInt("xp") + amount;
    var currentLevel = exp.getInt("level");
    var threshold = xpForLevel(currentLevel);

    if (currentXp >= threshold) {
        // 升级
        currentXp -= threshold;
        currentLevel++;
        exp.set("level", currentLevel);
        exp.set("xp", currentXp);
        exp.set("pendingPoints", exp.getInt("pendingPoints") + 3);

        // 同步到 Character 组件
        var charComp = player.getComponent("Character");
        if (charComp !== null) charComp.set("level", currentLevel);

        // 更新等级成长 Modifier（exclusive → 自动替换旧的）
        registerLevelGrowthModifier(playerId, currentLevel);

        var levelUpEvent = engine.newEvent("entity.level_up");
        levelUpEvent.set("entity", player);
        levelUpEvent.set("level", currentLevel);
        engine.fire("entity.level_up", levelUpEvent);
    } else {
        exp.set("xp", currentXp);
    }

    persistence.save(player);
});

// 分配属性点
engine.on("action.allocate_point", 100, function(event) {
    var playerId = event.get("playerId");
    var stat = event.get("stat");   // e.g. "attack" or "maxHp"
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null || exp.getInt("pendingPoints") <= 0) return;

    exp.set("pendingPoints", exp.getInt("pendingPoints") - 1);

    // 直接更新 base state 中的对应属性
    // 方法：找到包含该属性的 base 组件并更新
    var targetComp = player.getComponent("CombatStats");
    var isHealthStat = (stat === "maxHp");
    if (isHealthStat) targetComp = player.getComponent("Health");

    if (targetComp !== null && targetComp.has(stat)) {
        // 更新 base state 中的值（通过 recalculate_hooks 中相同 ID 的 level_growth modifier 会保持一致）
        // 暂时通过直接更新 base state 实现
        engine.addModifier(playerId, {
            typeId: "level",
            id: "level_growth",
            label: exp.getInt("level") + "级成长（含加点）",
            apply: function(entity) {
                var level = entity.getComponent("Experience").getInt("level");
                var health = entity.getComponent("Health");
                if (health !== null) {
                    health.set("maxHp", health.getInt("maxHp") + level * 5);
                }
                var stats2 = entity.getComponent("CombatStats");
                if (stats2 !== null) {
                    stats2.set("attack", stats2.getInt("attack") + level);
                }
                // 手动加点
                var allocComp = entity.getComponent(isHealthStat ? "Health" : "CombatStats");
                if (allocComp !== null && allocComp.has(stat)) {
                    allocComp.set(stat, allocComp.getInt(stat) + 1);
                }
            }
        });
    }

    persistence.save(player);
});
```

- [ ] **Step 2: 运行测试**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 3: Commit**

```bash
git add mods/base-rules/handlers/character/leveling.js
git commit -m "feat: 添加经验值/升级/属性点分配系统"
```

---

## Task 14: CharacterStatsController（GET /api/character/stats）

**Files:**
- Create: `backend/src/main/java/com/epic/engine/snapshot/CharacterStatsController.java`
- Create: `backend/src/test/java/com/epic/engine/snapshot/CharacterStatsTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.*;
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
class CharacterStatsTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;

    @Test
    @SuppressWarnings("unchecked")
    void getStats_returnsBreakdown() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建角色
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "confirm_character",
                        "params", Map.of("name", "测试", "class", "warrior")), headers), Map.class);

        // 获取属性明细
        ResponseEntity<Map> resp = rest.exchange(
                "/api/character/stats", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();

        // 应有 CombatStats.attack 的 breakdown
        // 结构: { "CombatStats.attack": { "final": 13, "breakdown": [...] } }
        var statsMap = resp.getBody();
        assertThat(statsMap).containsKey("CombatStats.attack");
        var attackStat = (Map<String, Object>) statsMap.get("CombatStats.attack");
        assertThat(attackStat).containsKey("final");
        assertThat(attackStat).containsKey("breakdown");
    }
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=CharacterStatsTest -q
```
期望：FAIL (404, controller not found)

- [ ] **Step 3: 创建 CharacterStatsController.java**

```java
package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/character")
public class CharacterStatsController {

    private final EntityStore entityStore;
    private final SessionService sessionService;
    private final ModifierChainService modifierChainService;

    public CharacterStatsController(EntityStore entityStore, SessionService sessionService,
                                     ModifierChainService modifierChainService) {
        this.entityStore = entityStore;
        this.sessionService = sessionService;
        this.modifierChainService = modifierChainService;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null) return Map.of();

        var session = sessionService.getSession(token);
        if (session == null || session.activeCharacterId() == null) return Map.of();

        String playerId = session.activeCharacterId();
        Entity player = entityStore.get(playerId);
        if (player == null) return Map.of();

        Map<String, Component> baseState = modifierChainService.getBaseState(playerId);
        List<ModifierDiff> contributions = modifierChainService.getContributions(playerId);

        Map<String, Object> result = new LinkedHashMap<>();

        // Collect all numeric fields that exist in base state or were modified
        Map<String, Long> baseValues = new LinkedHashMap<>();
        if (baseState != null) {
            baseState.forEach((compType, comp) ->
                comp.getAll().forEach((field, val) -> {
                    if (val instanceof Number n) {
                        baseValues.put(compType + "." + field, n.longValue());
                    }
                })
            );
        }

        // Collect all fields that were modified
        Set<String> allKeys = new LinkedHashSet<>(baseValues.keySet());
        for (ModifierDiff diff : contributions) {
            diff.componentDeltas().forEach((comp, fields) ->
                fields.keySet().forEach(f -> allKeys.add(comp + "." + f))
            );
        }

        // Build per-stat breakdown
        for (String key : allKeys) {
            List<Map<String, Object>> breakdown = new ArrayList<>();
            long base = baseValues.getOrDefault(key, 0L);
            breakdown.add(Map.of("label", "基础", "value", base, "type", "base"));

            for (ModifierDiff diff : contributions) {
                String[] parts = key.split("\\.", 2);
                String compType = parts[0];
                String fieldName = parts[1];
                Map<String, Long> compDelta = diff.componentDeltas().getOrDefault(compType, Map.of());
                Long delta = compDelta.get(fieldName);
                if (delta != null && delta != 0) {
                    breakdown.add(Map.of(
                        "label", diff.label() != null ? diff.label() : diff.modifierId(),
                        "value", delta,
                        "type", diff.typeId() != null ? diff.typeId() : "modifier"
                    ));
                }
            }

            // Get final value from entity
            String[] parts = key.split("\\.", 2);
            Component comp = player.getComponent(parts[0]);
            long finalVal = comp != null && comp.has(parts[1])
                    ? ((Number) comp.get(parts[1])).longValue() : base;

            result.put(key, Map.of("final", finalVal, "breakdown", breakdown));
        }

        return result;
    }
}
```

- [ ] **Step 4: 确认测试通过**

```
cd backend && mvn test -Dtest=CharacterStatsTest -q
```
期望：PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/CharacterStatsController.java
git add backend/src/test/java/com/epic/engine/snapshot/CharacterStatsTest.java
git commit -m "feat: 添加 GET /api/character/stats 属性明细端点"
```

---

## Task 15: LifeState 迁移

**Files:**
- Modify: `mods/base-rules/handlers/combat/death_check.js`

将现有基于 `Health.hp` 直接判断的死亡逻辑迁移到 `LifeState` 组件。

- [ ] **Step 1: 更新 death_check.js**

```javascript
// 伤害后检测 hp <= 0，fire entity.hp_zero
engine.on("combat.damage_dealt", 100, function(event) {
    var target = store.get(event.get("targetId"));
    var health = target.getComponent("Health");
    if (health.getInt("hp") <= 0) {
        var hpZeroEvent = engine.newEvent("entity.hp_zero");
        hpZeroEvent.set("entity", target);
        hpZeroEvent.set("combatId", event.get("combatId"));
        hpZeroEvent.set("killerId", event.get("attackerId"));
        engine.fire("entity.hp_zero", hpZeroEvent);
    }
});

// 默认死亡处理（可被 cancel 取消，让 mod 自定义）
engine.on("entity.hp_zero", 100, function(event) {
    if (event.isCancelled()) return;
    var entity = event.get("entity");
    var ls = entity.getComponent("LifeState");
    if (ls === null) {
        // 没有 LifeState 组件时用旧逻辑
        var deathEvent = engine.newEvent("combat.unit_death");
        deathEvent.set("deadId", entity.getId());
        deathEvent.set("killerId", event.get("killerId"));
        deathEvent.set("combatId", event.get("combatId"));
        engine.fire("combat.unit_death", deathEvent);
        return;
    }
    var deathCount = ls.has("deathCount") ? ls.getInt("deathCount") : 0;
    if (deathCount === 0) {
        ls.set("state", "downed");
    } else {
        ls.set("state", "dead");
    }
    ls.set("deathCount", deathCount + 1);

    var stateEvent = engine.newEvent("entity.state_change");
    stateEvent.set("entity", entity);
    stateEvent.set("newState", ls.getString("state"));
    stateEvent.set("combatId", event.get("combatId"));
    engine.fire("entity.state_change", stateEvent);
});

// 状态变化后检查战斗是否结束
engine.on("entity.state_change", 50, function(event) {
    var combatId = event.get("combatId");
    if (combatId === null) return;
    var checkEvent = engine.newEvent("combat.check_end");
    checkEvent.set("combatId", combatId);
    engine.fire("combat.check_end", checkEvent);
});

// 战斗结束条件（兼容旧逻辑 + LifeState）
engine.on("combat.check_end", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);

    var playersAlive = false;
    var enemiesAlive = false;

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var ls = entity.getComponent("LifeState");
        var alive = ls !== null
            ? (ls.getString("state") === "alive")
            : (entity.getComponent("Health").getInt("hp") > 0);

        if (alive) {
            if (entity.hasTag("player")) playersAlive = true;
            if (entity.hasTag("enemy")) enemiesAlive = true;
        }
    }

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

// 兼容：unit_death 时清除非永久 buff（保持旧逻辑）
engine.on("combat.unit_death", 50, function(event) {
    var deadId = event.get("deadId");
    var entity = store.get(deadId);
    if (entity === null) return;
    var components = entity.getAllComponents();
    for (var i = 0; i < components.size(); i++) {
        var comp = components.get(i);
        if (!comp.getType().startsWith("Buff_")) continue;
        var permanent = comp.has("permanent") && comp.getBoolean("permanent");
        if (!permanent) {
            buffs.removeBuff(deadId, comp.getType().substring(5));
        }
    }
});
```

- [ ] **Step 2: 在角色创建 (select.js) 中添加 LifeState 组件**

在 select.js 的 `action.confirm_character` handler 中，Experience 组件添加后追加：

```javascript
    var lifeState = engine.newComponent("LifeState");
    lifeState.set("state", "alive");
    lifeState.set("deathCount", 0);
    entity.addComponent(lifeState);
```

- [ ] **Step 3: 运行战斗相关测试**

```
cd backend && mvn test -Dtest=NewCombatIntegrationTest -q
```
期望：PASS

- [ ] **Step 4: 运行全部测试**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/combat/death_check.js
git add mods/base-rules/handlers/character/select.js
git commit -m "feat: 迁移死亡检测到 LifeState 组件和 entity.hp_zero 事件"
```

---

## 验证

### 基础功能验证

1. 启动应用：`./start.ps1`（Windows）

2. 创建角色，验证属性明细：
   ```
   curl -H "X-Session-Token: TOKEN" http://localhost:8080/api/character/stats
   ```
   期望返回：
   ```json
   {
     "CombatStats.attack": {
       "final": 13,
       "breakdown": [
         {"label": "基础", "value": 10, "type": "base"},
         {"label": "战士", "value": 3, "type": "class"}
       ]
     }
   }
   ```

3. 重启应用，选择已有角色，验证属性值正确（重启后 modifier 重新注册，值不变）

### 测试套件

```bash
cd backend && mvn test -q
```
所有测试应通过，包括：
- `ModifierChainTest` — 5 个场景
- `ModifierTypeRegistryTest` — 3 个场景
- `ModifierChainServiceTest` — 4 个场景
- `CharacterStatsTest` — 1 个集成场景
- `CharacterFlowTest` — 2 个集成场景
- `NewCombatIntegrationTest` — 战斗流程不回归
