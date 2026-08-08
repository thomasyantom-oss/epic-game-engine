# 属性/装备/等级 前端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现属性/装备/背包/属性点分配前端 UI，严格按照 spec（`docs/superpowers/specs/2026-05-26-attribute-equipment-level-design.md`）第八节视觉规范。

**Architecture:** 后端新增 `pendingPoints` + `EquipmentData` 字段到 `WorldSnapshot`，前端新增三个组件（`CharacterStatsTab`、`EquipmentBagPanel`、`AttributeAllocPanel`）和全局 tooltip 基础设施，`SnapshotRenderer` 统一接线。

**Tech Stack:** Java 21 records, Vue 3.5 Composition API, HTML5 drag & drop, CSS grid, `position:fixed` + `Teleport`

---

## 视觉规范（开发时对照）

- **配色**：深蓝暗色系 `#1a1a2e` / `#16213e`，沿用现有 CSS 变量
- **边框**：全部 2px+，禁止 1px 细边框
- **字体**：霞鹜文楷，沿用现有

---

## 文件结构

**新建文件**
| 文件 | 职责 |
|------|------|
| `frontend/src/composables/useTooltip.js` | 全局 tooltip 单例状态（位置 + 内容） |
| `frontend/src/components/ItemTooltip.vue` | `Teleport to body`，fixed 定位，统一渲染物品/属性 breakdown tooltip |
| `frontend/src/components/CharacterStatsTab.vue` | 右上角角色 Tab：HP/MP 条 + 属性行 + pendingPoints 横幅 + tooltip 触发 |
| `frontend/src/components/EquipmentBagPanel.vue` | 左侧新 Tab：SVG 人形 + 6 装备槽 + 背包网格 + 拖拽装备 |
| `frontend/src/components/AttributeAllocPanel.vue` | 内嵌于 CharacterStatsTab：属性点分配 UI |

**修改文件**
| 文件 | 变化 |
|------|------|
| `mods/base-rules/entities/items.yaml` | 扩充为 6 件物品（3 种稀有度，含武器/护甲/饰品） |
| `mods/base-rules/handlers/equipment/equip.js` | `world.init` 时加载 `item_rarity.yaml`，把 `rarityColor` 写入 `ItemMeta` |
| `mods/base-rules/handlers/character/select.js` | 创角时给初始背包装入 `iron_sword` + `leather_armor`（供测试） |
| `backend/.../snapshot/WorldSnapshot.java` | 新增 `Integer pendingPoints`、`EquipmentData equipment` 字段和嵌套 record |
| `backend/.../snapshot/SnapshotService.java` | `buildInGameSnapshot` 填入两个新字段 |
| `frontend/src/api/client.js` | 添加 `getCharacterStats()` |
| `frontend/src/components/SnapshotRenderer.vue` | 新增装备 Tab（左侧）、换用 CharacterStatsTab（右侧）、挂载全局 ItemTooltip |

---

## Task 1: 测试物品内容扩充

**Files:**
- Modify: `mods/base-rules/entities/items.yaml`
- Modify: `mods/base-rules/handlers/equipment/equip.js`
- Modify: `mods/base-rules/handlers/character/select.js`

- [ ] **Step 1: 替换 items.yaml（扩充为 6 件物品）**

```yaml
items:
  - id: iron_sword
    meta:
      name: 铁剑
      type: weapon
      rarity: common
    stats:
      attack: 8

  - id: steel_sword
    meta:
      name: 钢剑
      type: weapon
      rarity: uncommon
    stats:
      attack: 15

  - id: fire_staff
    meta:
      name: 火焰法杖
      type: weapon
      rarity: rare
    stats:
      attack: 10

  - id: leather_armor
    meta:
      name: 皮甲
      type: armor
      rarity: common
    stats:
      defense: 5

  - id: chain_mail
    meta:
      name: 锁子甲
      type: armor
      rarity: uncommon
    stats:
      defense: 12

  - id: speed_ring
    meta:
      name: 疾速戒指
      type: accessory
      rarity: rare
    stats:
      speed: 5
```

- [ ] **Step 2: 修改 equip.js — world.init 加载稀有度颜色并写入 ItemMeta**

把 `equip.js` 中 `engine.on("world.init", 80, ...)` 整个替换为：

```javascript
engine.on("world.init", 80, function(event) {
    // 加载稀有度颜色映射
    var rarityData = engine.loadYaml("item_rarity.yaml");
    var rarityList = rarityData.get("item_rarities");
    var rarityColors = engine.newMap();
    for (var r = 0; r < rarityList.size(); r++) {
        var rar = rarityList.get(r);
        rarityColors.put(rar.get("id"), rar.get("color"));
    }

    // 加载物品数据
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
        var rarity = meta.get("rarity");
        var metaComp = engine.newComponent("ItemMeta");
        metaComp.set("name", meta.get("name"));
        metaComp.set("type", meta.get("type"));
        metaComp.set("rarity", rarity);
        metaComp.set("rarityColor", rarityColors.get(rarity) !== null ? rarityColors.get(rarity) : "#ffffff");
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
```

- [ ] **Step 3: 修改 select.js — 创角时给初始背包（测试用）**

在 `select.js` 里找到：
```javascript
    var invComp = engine.newComponent("Inventory");
    invComp.set("items", engine.newList());
    entity.addComponent(invComp);
```

替换为：
```javascript
    var invComp = engine.newComponent("Inventory");
    var startingItems = engine.newList();
    startingItems.add("iron_sword");
    startingItems.add("leather_armor");
    startingItems.add("speed_ring");
    if (classId === "mage") {
        startingItems.add("fire_staff");
    } else {
        startingItems.add("steel_sword");
    }
    invComp.set("items", startingItems);
    entity.addComponent(invComp);
```

- [ ] **Step 4: 给 select.js 添加初始 pendingPoints（测试属性点分配 UI 用）**

找到：
```javascript
    var expComp = engine.newComponent("Experience");
    expComp.set("xp", 0);
    expComp.set("level", 1);
    expComp.set("pendingPoints", 0);
    entity.addComponent(expComp);
```

改为：
```javascript
    var expComp = engine.newComponent("Experience");
    expComp.set("xp", 0);
    expComp.set("level", 1);
    expComp.set("pendingPoints", 3);
    entity.addComponent(expComp);
```

- [ ] **Step 5: 运行全套后端测试确认无回归**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/entities/items.yaml
git add mods/base-rules/handlers/equipment/equip.js
git add mods/base-rules/handlers/character/select.js
git commit -m "feat: 扩充测试物品 + 稀有度颜色写入 ItemMeta + 创角给初始背包"
```

---

## Task 2: WorldSnapshot 扩展

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`

- [ ] **Step 1: 写失败测试**

在 `backend/src/test/java/com/epic/engine/snapshot/CharacterStatsTest.java` 末尾追加：

```java
@Test
@SuppressWarnings("unchecked")
void snapshot_hasEquipmentAndPendingPoints() {
    String token = sessionService.createSession();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Session-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);

    // 创建角色
    rest.exchange("/api/action", HttpMethod.POST,
            new HttpEntity<>(Map.of("type", "confirm_character",
                    "params", Map.of("name", "测试员", "class", "warrior")), headers), Map.class);

    // 获取 snapshot
    ResponseEntity<Map> resp = rest.exchange(
            "/api/snapshot", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    var body = resp.getBody();
    assertThat(body).containsKey("equipment");
    assertThat(body).containsKey("pendingPoints");

    var equipment = (Map<String, Object>) body.get("equipment");
    assertThat(equipment).containsKey("slots");
    assertThat(equipment).containsKey("inventory");
}
```

- [ ] **Step 2: 确认测试失败**

```
cd backend && mvn test -Dtest=CharacterStatsTest#snapshot_hasEquipmentAndPendingPoints -q
```
期望：FAIL（snapshot 没有 equipment / pendingPoints 字段）

- [ ] **Step 3: 更新 WorldSnapshot.java**

完整替换为：

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
        List<LogEntry> log,
        Map<String, String> colors,
        Integer pendingPoints,
        EquipmentData equipment
) {
    public record ActionResult(boolean success, String message) {}
    public record CharacterInfo(String id, String name, int level, String classId, String classLabel) {}
    public record FormData(List<FormField> fields) {}
    public record FormField(String name, String label, String type, boolean required, List<FormOption> options) {}
    public record FormOption(String value, String label, String description) {}
    public record StatusBar(String id, String label, int current, int max, String color, int priority) {}
    public record BuffEntry(String id, String name, String remaining, int priority) {}
    public record MapSnapshot(String mapId, String mapName, int playerX, int playerY, int width, int height,
                               List<String> terrain, Map<String, TerrainInfo> terrains,
                               String currentTerrain, String currentTerrainName,
                               List<PoiInfo> pois) {}
    public record TerrainInfo(String color, String textColor, List<String> requires, double moveCost) {}
    public record PoiInfo(String id, int x, int y, String type, String target, String label) {}
    public record CombatSnapshot(String combatId, String phase, int round, int turnTimer,
                                     List<CombatantInfo> combatants, List<CombatEvent> events) {}
    public record CombatantInfo(String id, String name, String side, int hp, int maxHp, int mp, int maxMp, boolean alive, List<BuffInfo> buffs, String row, int slot, String hpColor, String mpColor) {}
    public record BuffInfo(String id, int stacks, String color, boolean positive, int remaining) {}
    public record CombatEvent(List<TextSegment> segments, List<Effect> effects, List<Map<String, Object>> animation, int logCount) {}
    public record Effect(String target, String type, Map<String, Object> data) {}
    public record ActionOption(String type, String label, Map<String, Object> params, String color, String style) {
        public ActionOption(String type, String label, Map<String, Object> params) {
            this(type, label, params, null, null);
        }
    }
    public record LogEntry(List<TextSegment> segments, Integer round) {
        public LogEntry(List<TextSegment> segments) { this(segments, null); }
    }
    public record TextSegment(String text, String color) {}

    public record ItemInfo(String id, String name, String type, String rarity, String rarityColor,
                           Map<String, Integer> stats) {}
    public record EquipmentData(Map<String, ItemInfo> slots, List<ItemInfo> inventory) {}

    public static WorldSnapshot characterSelect(String sessionToken, List<CharacterInfo> characters, int maxSlots, Map<String, String> colors) {
        return new WorldSnapshot("character_select", sessionToken, null,
                new ActionResult(true, "ok"), characters, maxSlots, null,
                null, null, null, null, null, null, colors, null, null);
    }

    public static WorldSnapshot characterCreate(String sessionToken, FormData form, Map<String, String> colors) {
        return new WorldSnapshot("character_create", sessionToken, null,
                new ActionResult(true, "ok"), null, null, form,
                null, null, null, null, null, null, colors, null, null);
    }

    public static WorldSnapshot inGame(String sessionToken, String playerId, ActionResult result,
                                        List<StatusBar> statusBars, List<BuffEntry> buffs,
                                        MapSnapshot map, CombatSnapshot combat,
                                        List<ActionOption> actions, List<LogEntry> log,
                                        Map<String, String> colors,
                                        Integer pendingPoints, EquipmentData equipment) {
        return new WorldSnapshot("in_game", sessionToken, playerId, result,
                null, null, null, statusBars, buffs, map, combat, actions, log, colors,
                pendingPoints, equipment);
    }
}
```

- [ ] **Step 4: 运行测试（此时仍 FAIL，因为 SnapshotService 还没更新）**

```
cd backend && mvn test -q 2>&1 | grep -E "Tests run:|BUILD|FAIL"
```
期望：编译通过，但测试仍 FAIL（equipment 为 null）

- [ ] **Step 5: Commit（只提交 WorldSnapshot，分离职责）**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java
git commit -m "feat: WorldSnapshot 新增 pendingPoints + EquipmentData + ItemInfo 字段"
```

---

## Task 3: SnapshotService 构建装备数据

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`

- [ ] **Step 1: 在 SnapshotService 中添加三个 private 方法**

在 `buildColorMap()` 方法后追加：

```java
private Integer buildPendingPoints(String playerId) {
    Entity player = entityStore.get(playerId);
    if (player == null || !player.hasComponent("Experience")) return null;
    Component exp = player.getComponent("Experience");
    if (!exp.has("pendingPoints")) return null;
    int pts = exp.getInt("pendingPoints");
    return pts > 0 ? pts : null;
}

private WorldSnapshot.EquipmentData buildEquipmentData(String playerId) {
    Entity player = entityStore.get(playerId);
    if (player == null) return null;

    Map<String, WorldSnapshot.ItemInfo> slots = new java.util.LinkedHashMap<>();
    Component slotsComp = player.getComponent("EquipmentSlots");
    if (slotsComp != null) {
        for (String slotName : new String[]{"weapon", "armor", "accessory"}) {
            Object itemId = slotsComp.get(slotName);
            slots.put(slotName, itemId != null ? buildItemInfo(itemId.toString()) : null);
        }
    }

    List<WorldSnapshot.ItemInfo> inventory = new ArrayList<>();
    Component invComp = player.getComponent("Inventory");
    if (invComp != null) {
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) invComp.get("items");
        if (items != null) {
            for (Object itemId : items) {
                WorldSnapshot.ItemInfo info = buildItemInfo(itemId.toString());
                if (info != null) inventory.add(info);
            }
        }
    }

    return new WorldSnapshot.EquipmentData(slots, inventory);
}

private WorldSnapshot.ItemInfo buildItemInfo(String itemId) {
    Entity item = entityStore.get(itemId);
    if (item == null || !item.hasComponent("ItemMeta")) return null;
    Component meta = item.getComponent("ItemMeta");

    Map<String, Integer> statsMap = new java.util.LinkedHashMap<>();
    Component stats = item.getComponent("ItemStats");
    if (stats != null) {
        stats.getAll().forEach((k, v) -> {
            if (v instanceof Number n) statsMap.put(k, n.intValue());
        });
    }

    return new WorldSnapshot.ItemInfo(
            itemId,
            meta.getString("name"),
            meta.getString("type"),
            meta.getString("rarity"),
            meta.getString("rarityColor"),
            statsMap
    );
}
```

- [ ] **Step 2: 更新 buildInGameSnapshot 调用**

找到：
```java
        return WorldSnapshot.inGame(token, playerId,
                new WorldSnapshot.ActionResult(true, "ok"),
                bars != null ? bars : List.of(),
                buffs != null ? buffs : List.of(),
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions != null ? actions : List.of(),
                buildCombatLog(playerId),
                buildColorMap());
```

替换为：
```java
        return WorldSnapshot.inGame(token, playerId,
                new WorldSnapshot.ActionResult(true, "ok"),
                bars != null ? bars : List.of(),
                buffs != null ? buffs : List.of(),
                buildMapSnapshot(player),
                buildCombatSnapshot(playerId),
                actions != null ? actions : List.of(),
                buildCombatLog(playerId),
                buildColorMap(),
                buildPendingPoints(playerId),
                buildEquipmentData(playerId));
```

- [ ] **Step 3: 确认测试通过**

```
cd backend && mvn test -Dtest=CharacterStatsTest -q
```
期望：全部 PASS（包含新增的 `snapshot_hasEquipmentAndPendingPoints`）

- [ ] **Step 4: 运行全套测试**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java
git commit -m "feat: SnapshotService 填充 pendingPoints + EquipmentData 到 snapshot"
```

---

## Task 4: API client 扩展

**Files:**
- Modify: `frontend/src/api/client.js`

- [ ] **Step 1: 在 client.js 末尾追加 getCharacterStats()**

```javascript
export async function getCharacterStats() {
    try {
        const response = await fetch(`${BASE_URL}/character/stats`, {
            headers: getHeaders()
        })
        if (!response.ok) return null
        return await response.json()
    } catch (e) {
        return null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/client.js
git commit -m "feat: client.js 添加 getCharacterStats() 端点调用"
```

---

## Task 5: useTooltip.js 全局单例

**Files:**
- Create: `frontend/src/composables/useTooltip.js`

- [ ] **Step 1: 创建文件**

```javascript
import { ref, reactive } from 'vue'

const visible = ref(false)
const position = reactive({ x: 0, y: 0 })
const content = ref(null)

export function useTooltip() {
  function showTooltip(data, event) {
    content.value = data
    // Keep tooltip within viewport
    const x = Math.min(event.clientX + 14, window.innerWidth - 220)
    const y = Math.min(event.clientY + 14, window.innerHeight - 200)
    position.x = x
    position.y = y
    visible.value = true
  }

  function moveTooltip(event) {
    if (!visible.value) return
    const x = Math.min(event.clientX + 14, window.innerWidth - 220)
    const y = Math.min(event.clientY + 14, window.innerHeight - 200)
    position.x = x
    position.y = y
  }

  function hideTooltip() {
    visible.value = false
    content.value = null
  }

  return { visible, position, content, showTooltip, moveTooltip, hideTooltip }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/composables/useTooltip.js
git commit -m "feat: useTooltip.js 全局单例 tooltip 状态管理"
```

---

## Task 6: ItemTooltip.vue 全局浮层

**Files:**
- Create: `frontend/src/components/ItemTooltip.vue`

tooltip 内容格式（`content.value` 数据结构）：
```js
{
  title: "铁剑",               // string
  titleColor: "#ffffff",      // string，稀有度颜色或属性颜色
  rows: [                     // 每行展示一个 label + value
    { label: "攻击", value: "+8", valueColor: "#4ade80" },
    { label: "──────", value: "", valueColor: null },   // 分割线用特殊 label
    { label: "优质 · 武器", value: "", valueColor: "#888" }
  ],
  footer: null                // 可选，string
}
```

- [ ] **Step 1: 创建文件**

```vue
<template>
  <Teleport to="body">
    <div
      v-if="visible && content"
      class="item-tooltip"
      :style="{ left: position.x + 'px', top: position.y + 'px' }"
    >
      <div class="tt-title" :style="{ color: content.titleColor }">{{ content.title }}</div>
      <template v-for="(row, i) in content.rows" :key="i">
        <div v-if="row.label.startsWith('──')" class="tt-divider"></div>
        <div v-else class="tt-row">
          <span class="tt-label">{{ row.label }}</span>
          <span class="tt-value" :style="{ color: row.valueColor || 'inherit' }">{{ row.value }}</span>
        </div>
      </template>
      <div v-if="content.footer" class="tt-footer">{{ content.footer }}</div>
    </div>
  </Teleport>
</template>

<script setup>
import { useTooltip } from '../composables/useTooltip.js'
const { visible, position, content } = useTooltip()
</script>

<style scoped>
.item-tooltip {
  position: fixed;
  z-index: 9999;
  min-width: 160px;
  max-width: 240px;
  background: #0d1117;
  border: 2px solid #30363d;
  border-radius: 4px;
  padding: 0.5rem 0.65rem;
  pointer-events: none;
  font-family: var(--font-family, inherit);
  font-size: 0.85rem;
  color: var(--text-color, #e6edf3);
  box-shadow: 0 4px 16px rgba(0,0,0,0.6);
}
.tt-title {
  font-weight: bold;
  font-size: 0.95rem;
  margin-bottom: 0.35rem;
}
.tt-divider {
  border-top: 1px solid #30363d;
  margin: 0.3rem 0;
}
.tt-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  line-height: 1.7;
}
.tt-label { color: #8b949e; }
.tt-value { font-weight: bold; }
.tt-footer {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: #8b949e;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/ItemTooltip.vue
git commit -m "feat: ItemTooltip.vue 全局 fixed 浮层，支持物品和属性 breakdown"
```

---

## Task 7: CharacterStatsTab.vue

**Files:**
- Create: `frontend/src/components/CharacterStatsTab.vue`

**布局**（从上到下）：
1. 角色名 + 职业行（名字/等级/职业）
2. 属性点横幅（仅 `pendingPoints > 0`）：`✦ N 属性点待分配 [分配]`
3. HP 进度条（红）
4. MP 进度条（蓝）
5. 分割线
6. 属性行：攻击 / 防御 / 速度（hover 显示 breakdown tooltip）
7. Buff 列表（已有逻辑保留）
8. `AttributeAllocPanel`（当 `showAlloc` 为 true 时展开）

从 `statusBars` prop 里取数据（id 对应 `name`/`class`/`hp`/`mp`/`atk`/`def`/`spd`）。

- [ ] **Step 1: 创建文件**

```vue
<template>
  <div class="stats-tab" @mousemove="moveTooltip">
    <!-- 角色信息行 -->
    <div class="char-header" v-if="nameBar">
      <span class="char-name" :style="{ color: nameBar.color }">{{ nameBar.label }}</span>
      <span class="char-level">Lv.{{ nameBar.current }}</span>
      <span class="char-class" v-if="classBar" :style="{ color: classBar.color }">{{ classBar.label }}</span>
    </div>

    <!-- 属性点横幅 -->
    <div v-if="pendingPoints" class="pending-banner">
      <span>✦ {{ pendingPoints }} 属性点待分配</span>
      <span class="alloc-link" @click="showAlloc = !showAlloc">
        {{ showAlloc ? '[收起]' : '[分配]' }}
      </span>
    </div>

    <!-- HP 条 -->
    <div class="bar-row" v-if="hpBar">
      <span class="bar-label" :style="{ color: hpBar.color }">{{ hpBar.label }}</span>
      <div class="bar-track">
        <div class="bar-fill hp-fill"
             :style="{ width: (hpBar.max > 0 ? hpBar.current / hpBar.max * 100 : 0) + '%' }"></div>
      </div>
      <span class="bar-nums">{{ hpBar.current }}/{{ hpBar.max }}</span>
    </div>

    <!-- MP 条 -->
    <div class="bar-row" v-if="mpBar">
      <span class="bar-label" :style="{ color: mpBar.color }">{{ mpBar.label }}</span>
      <div class="bar-track">
        <div class="bar-fill mp-fill"
             :style="{ width: (mpBar.max > 0 ? mpBar.current / mpBar.max * 100 : 0) + '%' }"></div>
      </div>
      <span class="bar-nums">{{ mpBar.current }}/{{ mpBar.max }}</span>
    </div>

    <div class="divider"></div>

    <!-- 属性行（hover tooltip） -->
    <div
      v-for="stat in statBars"
      :key="stat.id"
      class="stat-row"
      @mouseenter="onStatHover(stat, $event)"
      @mouseleave="hideTooltip"
    >
      <span class="stat-label">{{ stat.label }}</span>
      <span class="stat-value" :style="{ color: stat.color }">{{ stat.current }}</span>
    </div>

    <!-- Buff 列表 -->
    <div class="buff-list" v-if="buffs && buffs.length">
      <div class="divider"></div>
      <div v-for="buff in buffs" :key="buff.id" class="buff-item">
        {{ buff.name }} ({{ buff.remaining }})
      </div>
    </div>

    <!-- 属性点分配面板 -->
    <AttributeAllocPanel
      v-if="showAlloc && pendingPoints"
      :pending-points="pendingPoints"
      :stat-bars="statBars"
      :player-id="playerId"
      @action="$emit('action', $event)"
      @done="showAlloc = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useTooltip } from '../composables/useTooltip.js'
import { getCharacterStats } from '../api/client.js'
import AttributeAllocPanel from './AttributeAllocPanel.vue'

const props = defineProps({
  bars: Array,
  buffs: Array,
  pendingPoints: Number,
  playerId: String
})
const emit = defineEmits(['action'])

const { showTooltip, moveTooltip, hideTooltip } = useTooltip()
const showAlloc = ref(false)
const statsBreakdown = ref(null)

const barById = computed(() => {
  const m = {}
  for (const b of (props.bars || [])) m[b.id] = b
  return m
})

const nameBar = computed(() => barById.value['name'])
const classBar = computed(() => barById.value['class'])
const hpBar = computed(() => barById.value['hp'])
const mpBar = computed(() => barById.value['mp'])
const statBars = computed(() =>
  (props.bars || []).filter(b => !['name','class','hp','mp'].includes(b.id))
    .sort((a, b) => a.priority - b.priority)
)

// 懒加载 breakdown（每次 playerId 变化时重置，首次 hover 时拉取）
watch(() => props.playerId, () => { statsBreakdown.value = null })

async function onStatHover(stat, event) {
  if (!statsBreakdown.value) {
    statsBreakdown.value = await getCharacterStats()
  }
  const key = statIdToKey(stat.id)
  const data = statsBreakdown.value?.[key]
  if (!data) {
    showTooltip({
      title: stat.label,
      titleColor: stat.color,
      rows: [{ label: '暂无明细', value: '', valueColor: '#888' }]
    }, event)
    return
  }
  const rows = data.breakdown.map(b => ({
    label: b.label,
    value: (b.value > 0 && b.type !== 'base' ? '+' : '') + b.value,
    valueColor: b.type === 'base' ? null : (b.value > 0 ? '#4ade80' : '#f87171')
  }))
  rows.push({ label: '──────', value: '', valueColor: null })
  rows.push({ label: '合计', value: String(data.final), valueColor: stat.color })
  showTooltip({ title: stat.label, titleColor: stat.color, rows }, event)
}

function statIdToKey(id) {
  // 把 statusBar id 映射到 /api/character/stats 的 key
  // e.g. "atk" → "CombatStats.attack", "def" → "CombatStats.defense"
  const map = {
    atk: 'CombatStats.attack',
    def: 'CombatStats.defense',
    spd: 'CombatStats.speed',
    hp: 'Health.maxHp',
    mp: 'Mana.maxMp'
  }
  return map[id] || null
}
</script>

<style scoped>
.stats-tab { padding: 0.4rem 0.5rem; }
.char-header { display: flex; align-items: baseline; gap: 0.4rem; margin-bottom: 0.4rem; flex-wrap: wrap; }
.char-name { font-weight: bold; font-size: 1.05rem; }
.char-level { font-size: 0.85rem; opacity: 0.7; }
.char-class { font-size: 0.85rem; margin-left: auto; }

.pending-banner {
  display: flex; justify-content: space-between; align-items: center;
  background: rgba(255, 217, 61, 0.12);
  border: 2px solid rgba(255, 217, 61, 0.4);
  border-radius: 3px;
  padding: 0.2rem 0.5rem;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
  color: #ffd93d;
}
.alloc-link { cursor: pointer; text-decoration: underline; }
.alloc-link:hover { opacity: 0.8; }

.bar-row {
  display: flex; align-items: center; gap: 0.4rem;
  margin-bottom: 0.25rem;
}
.bar-label { width: 2rem; font-size: 0.8rem; font-weight: bold; flex-shrink: 0; }
.bar-track {
  flex: 1; height: 8px;
  background: rgba(255,255,255,0.1);
  border: 2px solid rgba(255,255,255,0.15);
  border-radius: 2px;
  overflow: hidden;
}
.bar-fill { height: 100%; border-radius: 0; transition: width 0.3s; }
.hp-fill { background: #e74c3c; }
.mp-fill { background: #3498db; }
.bar-nums { font-size: 0.78rem; color: var(--text-color); opacity: 0.8; min-width: 3rem; text-align: right; }

.divider { border-top: 2px solid var(--panel-border-color, #333); margin: 0.4rem 0; }

.stat-row {
  display: flex; justify-content: space-between;
  padding: 0.1rem 0.2rem;
  border-radius: 2px;
  cursor: default;
  margin-bottom: 0.1rem;
}
.stat-row:hover { background: rgba(255,255,255,0.06); }
.stat-label { font-size: 0.85rem; opacity: 0.8; }
.stat-value { font-weight: bold; font-size: 0.9rem; }

.buff-list { font-size: 0.82rem; }
.buff-item { margin: 0.1rem 0; opacity: 0.8; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/CharacterStatsTab.vue
git commit -m "feat: CharacterStatsTab.vue — HP/MP 进度条 + 属性行 + tooltip + pendingPoints 横幅"
```

---

## Task 8: EquipmentBagPanel.vue

**Files:**
- Create: `frontend/src/components/EquipmentBagPanel.vue`

**布局（左右两栏）**：

```
┌──────────────────────────────────────────┐
│  [左栏 38%]          [右栏 62%]           │
│                                           │
│     [头]          背包 (4/12)  [整理]     │
│  [武器][图][副手]  ┌──┬──┬──┬──┐         │
│     [护甲]         │铁│皮│  │  │         │
│  [饰][  ][靴]      ├──┼──┼──┼──┤         │
│                    │  │  │  │  │         │
│                    └──┴──┴──┴──┘         │
└──────────────────────────────────────────┘
```

装备槽按 CSS grid 布局（3列×4行）在左栏，中间列中间行放人形 SVG。整理按钮只做本地展示动画（无后端 action）。

已装备：边框 + 名字颜色 = 稀有度颜色；未装备：默认灰色边框。

拖拽：从背包格拖到装备槽 → equip；点击背包格 → 直接 equip；点已装备槽 ✕ → unequip。

装备槽布局定义（用 `SLOT_LAYOUT` 数组代替 inline string-template 子组件）：
```
name       col  row  label   disabled
head        2    1   头盔    true
weapon      1    2   武器    false
figure      2    2   (SVG)   -
offhand     3    2   副手    true
armor       2    3   护甲    false
accessory   1    4   饰品    false
boots       3    4   靴子    true
```

- [ ] **Step 1: 创建文件**

```vue
<template>
  <div class="equip-panel">
    <!-- 左栏：人形 + 装备槽 -->
    <div class="figure-col">
      <div class="figure-grid">

        <!-- 按 SLOT_LAYOUT 渲染：跳过 figure 占位，SVG 单独渲染 -->
        <template v-for="def in SLOT_LAYOUT" :key="def.name">
          <!-- 人形 SVG -->
          <div v-if="def.name === 'figure'"
               class="figure-cell"
               :style="{ gridColumn: def.col, gridRow: def.row }">
            <svg viewBox="0 0 40 60" class="figure-svg">
              <ellipse cx="20" cy="8" rx="7" ry="7" fill="none" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="15" x2="20" y2="38" stroke="#8b949e" stroke-width="2"/>
              <line x1="8" y1="20" x2="32" y2="20" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="38" x2="12" y2="56" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="38" x2="28" y2="56" stroke="#8b949e" stroke-width="2"/>
            </svg>
          </div>

          <!-- 装备槽 -->
          <div v-else
               class="equip-slot"
               :class="{
                 equipped: !!slots[def.name],
                 disabled: def.disabled,
                 'drag-over': dragOverSlot === def.name
               }"
               :style="{
                 gridColumn: def.col,
                 gridRow: def.row,
                 borderColor: slots[def.name] ? slots[def.name].rarityColor : undefined
               }"
               @dragover.prevent="!def.disabled && (dragOverSlot = def.name)"
               @dragleave="dragOverSlot = null"
               @drop.prevent="!def.disabled && onDropToSlot(def.name, $event)"
               @mouseenter="slots[def.name] && showItemTooltip(slots[def.name], $event)"
               @mousemove="slots[def.name] && moveTooltip($event)"
               @mouseleave="hideTooltip"
          >
            <template v-if="slots[def.name]">
              <span class="slot-icon">{{ ICONS[slots[def.name].type] || '?' }}</span>
              <span class="slot-name" :style="{ color: slots[def.name].rarityColor }">
                {{ slots[def.name].name }}
              </span>
              <span v-if="!def.disabled" class="slot-remove"
                    @click.stop="emit('action', { type: 'unequip', params: { playerId, slot: def.name } })">
                ✕
              </span>
            </template>
            <template v-else>
              <span class="slot-label">{{ def.label }}</span>
            </template>
          </div>
        </template>

      </div>
    </div>

    <!-- 右栏：背包 -->
    <div class="bag-col">
      <div class="bag-header">
        <span class="bag-title">背包 ({{ inventory.length }}/{{ maxBag }})</span>
        <button class="sort-btn" @click="doSort">整理</button>
      </div>
      <div class="bag-grid">
        <div
          v-for="(item, i) in bagSlots"
          :key="i"
          class="bag-cell"
          :class="{ 'has-item': !!item, 'sorting': sortingAnim }"
          :style="item ? { borderColor: item.rarityColor + '99' } : {}"
          :draggable="!!item"
          @dragstart="item && onDragStart(item, $event)"
          @dragend="onDragEnd"
          @mouseenter="item && showItemTooltip(item, $event)"
          @mousemove="item && moveTooltip($event)"
          @mouseleave="hideTooltip"
          @click="item && onBagItemClick(item)"
        >
          <template v-if="item">
            <span class="item-icon">{{ ICONS[item.type] || '?' }}</span>
            <span class="item-name" :style="{ color: item.rarityColor }">{{ item.name }}</span>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useTooltip } from '../composables/useTooltip.js'

const ICONS = { weapon: '⚔', armor: '🛡', accessory: '💍', boots: '👢', head: '⛑', offhand: '🔰' }

const SLOT_LAYOUT = [
  { name: 'head',      col: 2, row: 1, label: '头盔', disabled: true  },
  { name: 'weapon',    col: 1, row: 2, label: '武器', disabled: false },
  { name: 'figure',    col: 2, row: 2, label: '',     disabled: true  },
  { name: 'offhand',   col: 3, row: 2, label: '副手', disabled: true  },
  { name: 'armor',     col: 2, row: 3, label: '护甲', disabled: false },
  { name: 'accessory', col: 1, row: 4, label: '饰品', disabled: false },
  { name: 'boots',     col: 3, row: 4, label: '靴子', disabled: true  },
]

const props = defineProps({ equipment: Object, playerId: String })
const emit = defineEmits(['action'])
const { showTooltip, moveTooltip, hideTooltip } = useTooltip()

const maxBag = 16
const sortingAnim = ref(false)
const dragOverSlot = ref(null)

const slots = computed(() => props.equipment?.slots || {})
const inventory = computed(() => props.equipment?.inventory || [])

const bagSlots = computed(() => {
  const arr = [...inventory.value]
  while (arr.length < maxBag) arr.push(null)
  return arr
})

function onDragStart(item, event) {
  event.dataTransfer.setData('itemId', item.id)
  event.dataTransfer.effectAllowed = 'move'
}

function onDragEnd() {
  dragOverSlot.value = null
}

function onDropToSlot(slotName, event) {
  dragOverSlot.value = null
  const itemId = event.dataTransfer.getData('itemId')
  if (itemId) emit('action', { type: 'equip', params: { playerId: props.playerId, itemId } })
}

function onBagItemClick(item) {
  emit('action', { type: 'equip', params: { playerId: props.playerId, itemId: item.id } })
}

function doSort() {
  // 仅本地动画，背包排序由 snapshot 刷新时自动反映
  sortingAnim.value = true
  setTimeout(() => { sortingAnim.value = false }, 400)
}

function showItemTooltip(item, event) {
  const rows = Object.entries(item.stats || {}).map(([k, v]) => ({
    label: STAT_LABELS[k] || k,
    value: '+' + v,
    valueColor: '#4ade80'
  }))
  rows.push({ label: '──────', value: '', valueColor: null })
  rows.push({
    label: (RARITY_LABELS[item.rarity] || item.rarity) + ' · ' + (TYPE_LABELS[item.type] || item.type),
    value: '', valueColor: '#8b949e'
  })
  showTooltip({ title: item.name, titleColor: item.rarityColor || '#fff', rows }, event)
}

const STAT_LABELS = { attack: '攻击', defense: '防御', speed: '速度', magic: '魔法' }
const RARITY_LABELS = { common: '普通', uncommon: '优质', rare: '稀有', epic: '史诗', legendary: '传说' }
const TYPE_LABELS = { weapon: '武器', armor: '护甲', accessory: '饰品' }
</script>

<style scoped>
.equip-panel {
  display: flex;
  height: 100%;
  gap: 0.5rem;
  padding: 0.5rem;
  overflow: hidden;
}

.figure-col {
  width: 38%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.figure-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: auto auto auto auto;
  gap: 0.3rem;
  width: 100%;
}

.figure-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.figure-svg { width: 100%; max-width: 60px; opacity: 0.5; }

.equip-slot {
  width: 52px;
  height: 52px;
  border: 2px solid #30363d;
  border-radius: 3px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  position: relative;
  cursor: default;
  background: rgba(255,255,255,0.03);
  transition: border-color 0.2s;
  overflow: hidden;
  user-select: none;
  justify-self: center;
}
.equip-slot.equipped { background: rgba(255,255,255,0.06); }
.equip-slot.disabled { opacity: 0.3; }
.equip-slot.drag-over { border-color: #60a5fa !important; background: rgba(96,165,250,0.1); }
.slot-icon { font-size: 1.2rem; line-height: 1; }
.slot-name { font-size: 0.6rem; text-align: center; line-height: 1.1; margin-top: 2px; max-width: 50px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slot-label { font-size: 0.65rem; color: #444; }
.slot-remove {
  position: absolute; top: 1px; right: 2px;
  font-size: 0.6rem; color: #888; cursor: pointer; line-height: 1;
}
.slot-remove:hover { color: #f87171; }

.bag-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.bag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.4rem;
  flex-shrink: 0;
}
.bag-title { font-size: 0.85rem; opacity: 0.7; }
.sort-btn {
  font-size: 0.75rem;
  background: rgba(255,255,255,0.08);
  border: 2px solid rgba(255,255,255,0.2);
  color: var(--text-color);
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
  cursor: pointer;
}
.sort-btn:hover { background: rgba(255,255,255,0.14); }

.bag-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.25rem;
  overflow-y: auto;
  flex: 1;
}
.bag-cell {
  aspect-ratio: 1;
  border: 2px solid rgba(255,255,255,0.1);
  border-radius: 3px;
  background: rgba(255,255,255,0.03);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  cursor: default;
  overflow: hidden;
  user-select: none;
  transition: border-color 0.2s, opacity 0.2s;
}
.bag-cell.has-item { cursor: grab; }
.bag-cell.has-item:hover { background: rgba(255,255,255,0.08); }
.bag-cell.sorting { opacity: 0.5; }
.item-icon { font-size: 1.1rem; line-height: 1; }
.item-name { font-size: 0.6rem; text-align: center; line-height: 1.2; overflow: hidden; text-overflow: ellipsis; width: 100%; padding: 0 2px; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/EquipmentBagPanel.vue
git commit -m "feat: EquipmentBagPanel.vue — 装备槽 + 背包网格 + 拖拽装备"
```

---

## Task 9: AttributeAllocPanel.vue

**Files:**
- Create: `frontend/src/components/AttributeAllocPanel.vue`

**布局**：
```
─── 分配属性点 ───
剩余点数: 2
攻击   13  →  [14]  [-][+]  (+1)
防御    8  →   [8]  [-][+]
速度   12  →  [12]  [-][+]
[确认]  [重置]
```

前端本地维护分配中间状态，确认后批量发送 `action.allocate_point`，每次一点。

- [ ] **Step 1: 创建文件**

```vue
<template>
  <div class="alloc-panel">
    <div class="alloc-header">
      <span>─── 分配属性点 ───</span>
      <span class="remaining" :class="{ warn: remaining === 0 }">剩余: {{ remaining }}</span>
    </div>

    <div v-for="stat in statBars" :key="stat.id" class="alloc-row">
      <span class="alloc-label">{{ stat.label }}</span>
      <span class="alloc-current">{{ stat.current }}</span>
      <span class="alloc-arrow" v-if="allocated[stat.id] > 0">→</span>
      <span class="alloc-preview" v-if="allocated[stat.id] > 0" :style="{ color: stat.color }">
        {{ stat.current + allocated[stat.id] }}
      </span>
      <span class="alloc-diff" v-if="allocated[stat.id] > 0">+{{ allocated[stat.id] }}</span>
      <div class="alloc-btns">
        <button @click="decr(stat.id)" :disabled="!allocated[stat.id]">-</button>
        <button @click="incr(stat.id)" :disabled="remaining === 0">+</button>
      </div>
    </div>

    <div class="alloc-actions">
      <button class="confirm-btn" @click="confirm" :disabled="totalAllocated === 0">确认</button>
      <button class="reset-btn" @click="reset">重置</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const props = defineProps({
  pendingPoints: Number,
  statBars: Array,
  playerId: String
})
const emit = defineEmits(['action', 'done'])

const allocated = reactive({})
for (const s of (props.statBars || [])) allocated[s.id] = 0

const totalAllocated = computed(() => Object.values(allocated).reduce((a, b) => a + b, 0))
const remaining = computed(() => props.pendingPoints - totalAllocated.value)

function incr(id) {
  if (remaining.value <= 0) return
  allocated[id] = (allocated[id] || 0) + 1
}

function decr(id) {
  if ((allocated[id] || 0) <= 0) return
  allocated[id]--
}

function reset() {
  for (const k of Object.keys(allocated)) allocated[k] = 0
}

// 映射 bar id → action.allocate_point 的 stat 参数
const statParamMap = {
  atk: 'attack',
  def: 'defense',
  spd: 'speed'
}

async function confirm() {
  for (const [id, pts] of Object.entries(allocated)) {
    const stat = statParamMap[id] || id
    for (let i = 0; i < pts; i++) {
      emit('action', {
        type: 'allocate_point',
        params: { playerId: props.playerId, stat }
      })
    }
  }
  emit('done')
}
</script>

<style scoped>
.alloc-panel {
  border-top: 2px solid var(--panel-border-color, #333);
  margin-top: 0.4rem;
  padding-top: 0.4rem;
}
.alloc-header {
  display: flex; justify-content: space-between;
  font-size: 0.8rem; margin-bottom: 0.4rem; opacity: 0.7;
}
.remaining.warn { color: #f87171; }

.alloc-row {
  display: flex; align-items: center; gap: 0.3rem;
  margin-bottom: 0.25rem; font-size: 0.85rem;
}
.alloc-label { width: 2rem; opacity: 0.8; }
.alloc-current { width: 2rem; text-align: right; }
.alloc-arrow { color: #60a5fa; }
.alloc-preview { width: 2rem; text-align: right; font-weight: bold; }
.alloc-diff { font-size: 0.75rem; color: #4ade80; width: 2rem; }
.alloc-btns { margin-left: auto; display: flex; gap: 0.2rem; }
.alloc-btns button {
  width: 1.4rem; height: 1.4rem;
  background: rgba(255,255,255,0.08);
  border: 2px solid rgba(255,255,255,0.2);
  color: var(--text-color);
  border-radius: 2px; cursor: pointer;
  font-size: 0.9rem; line-height: 1;
  display: flex; align-items: center; justify-content: center;
}
.alloc-btns button:hover:not(:disabled) { background: rgba(255,255,255,0.18); }
.alloc-btns button:disabled { opacity: 0.3; cursor: not-allowed; }

.alloc-actions {
  display: flex; gap: 0.4rem; margin-top: 0.5rem;
}
.confirm-btn, .reset-btn {
  flex: 1; padding: 0.2rem; font-size: 0.82rem;
  border-radius: 2px; cursor: pointer;
  border: 2px solid;
}
.confirm-btn {
  background: rgba(74, 222, 128, 0.12);
  border-color: rgba(74, 222, 128, 0.4);
  color: #4ade80;
}
.confirm-btn:hover:not(:disabled) { background: rgba(74, 222, 128, 0.22); }
.confirm-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.reset-btn {
  background: rgba(255,255,255,0.05);
  border-color: rgba(255,255,255,0.2);
  color: var(--text-color);
}
.reset-btn:hover { background: rgba(255,255,255,0.1); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/AttributeAllocPanel.vue
git commit -m "feat: AttributeAllocPanel.vue — 属性点逐项分配 + 预览 + 确认/重置"
```

---

## Task 10: SnapshotRenderer.vue 接线

**Files:**
- Modify: `frontend/src/components/SnapshotRenderer.vue`

变化：
1. `mainTabs`：非战斗时增加 `{ id: 'equip', label: '装备' }` Tab
2. 左侧 `#equip` slot → `EquipmentBagPanel`
3. 右侧 `#status` slot → `CharacterStatsTab`（替换 StatusBars）
4. `<ItemTooltip />` 挂在模板根部
5. equip/unequip 动作通过 `$emit('action', ...)` 传给 App.vue

- [ ] **Step 1: 修改 SnapshotRenderer.vue**

把整个文件替换为：

```vue
<template>
  <div class="game-grid">
    <div class="panel-main">
      <TabPanel :tabs="mainTabs" :default-tab="snapshot.combat ? 'battle' : 'map'">
        <template #map>
          <div class="map-split">
            <MapGrid :map="snapshot.map" :map-size="settings.mapSize || 10"
                     :disabled="!!snapshot.combat"
                     @move="onMove" @moveTo="onMoveTo" @interrupt="onInterrupt" />
            <MapInfoPanel :map="snapshot.map" @poi-action="onPoiAction" />
          </div>
        </template>
        <template #equip v-if="!snapshot.combat">
          <EquipmentBagPanel
            :equipment="snapshot.equipment"
            :player-id="snapshot.playerId"
            @action="$emit('action', $event)"
          />
        </template>
        <template #battle v-if="snapshot.combat">
          <BattleGrid ref="battleGridRef" :combat="snapshot.combat" :player-id="snapshot.playerId"
                      :commands="combatActions" :animating="isAnimating"
                      :terrain-color="currentTerrainColor"
                      @command="$emit('action', $event)" />
        </template>
      </TabPanel>
    </div>

    <div class="panel-func">
      <TabPanel :tabs="funcTabs" default-tab="status">
        <template #status>
          <CharacterStatsTab
            :bars="snapshot.statusBars"
            :buffs="snapshot.buffs"
            :pending-points="snapshot.pendingPoints"
            :player-id="snapshot.playerId"
            @action="$emit('action', $event)"
          />
        </template>
        <template #settings>
          <SettingsPanel />
        </template>
      </TabPanel>
    </div>

    <div class="panel-log">
      <TabPanel :tabs="logTabs" :default-tab="hasLog ? 'combat-log' : 'events'">
        <template #events>
          <div class="log-scroll">
            <div v-if="!snapshot.log || snapshot.log.length === 0" class="empty-log">暂无事件</div>
          </div>
        </template>
        <template #combat-log v-if="hasLog">
          <div class="log-scroll" ref="logScrollRef">
            <div v-if="historyEntries.length > 0" class="history-toggle" @click="showHistory = !showHistory">
              {{ showHistory ? '▼ 收起历史' : '▶ 查看历史 (' + historyRounds + '回合)' }}
            </div>
            <div v-if="showHistory" class="combat-history">
              <div v-for="(entry, i) in historyEntries" :key="'h'+i" class="log-entry">
                <TextRenderer :segments="entry.segments" />
              </div>
            </div>
            <div v-for="(entry, i) in visibleCurrentEntries" :key="'c'+i" class="log-entry">
              <TextRenderer :segments="entry.segments" />
            </div>
            <div v-if="currentRoundEntries.length === 0 && historyEntries.length === 0" class="empty-log">等待指令...</div>
          </div>
        </template>
      </TabPanel>
    </div>

    <div class="panel-nav">
      <TabPanel :tabs="navTabs" default-tab="actions">
        <template #actions>
          <ActionPanel :actions="filteredActions" @action="$emit('action', $event)" />
        </template>
      </TabPanel>
    </div>

    <!-- 全局 Tooltip -->
    <ItemTooltip />
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import TabPanel from './TabPanel.vue'
import TextRenderer from './TextRenderer.vue'
import CharacterStatsTab from './CharacterStatsTab.vue'
import EquipmentBagPanel from './EquipmentBagPanel.vue'
import ItemTooltip from './ItemTooltip.vue'
import ActionPanel from './ActionPanel.vue'
import SettingsPanel from './SettingsPanel.vue'
import MapGrid from './MapGrid.vue'
import MapInfoPanel from './MapInfoPanel.vue'
import BattleGrid from './combat/BattleGrid.vue'
import { useSettings } from '../composables/useSettings.js'

const props = defineProps({ snapshot: Object })
const emit = defineEmits(['action'])
const { settings } = useSettings()
const showHistory = ref(false)
const logScrollRef = ref(null)

const mainTabs = computed(() => {
  if (props.snapshot?.combat) {
    return [{ id: 'battle', label: '战场' }, { id: 'map', label: '地图' }]
  }
  return [{ id: 'map', label: '地图' }, { id: 'equip', label: '装备' }]
})

const funcTabs = [{ id: 'status', label: '人物' }, { id: 'settings', label: '设置' }]

const hasLog = computed(() => {
  return (props.snapshot?.log && props.snapshot.log.length > 0) || props.snapshot?.combat
})

const logTabs = computed(() => {
  if (hasLog.value) {
    return [{ id: 'combat-log', label: '战斗' }, { id: 'events', label: '事件' }]
  }
  return [{ id: 'events', label: '事件' }]
})
const navTabs = [{ id: 'actions', label: '快捷' }]

// Split log into rounds: each round starts with a separator entry (round != null)
const rounds = computed(() => {
  const log = props.snapshot?.log || []
  const result = []
  let current = []
  for (const entry of log) {
    if (entry.round != null) {
      if (current.length > 0) result.push(current)
      current = [entry]
    } else {
      current.push(entry)
    }
  }
  if (current.length > 0) result.push(current)
  return result
})

const currentRoundEntries = computed(() => {
  const r = rounds.value
  if (r.length === 0) return []
  for (let i = r.length - 1; i >= 0; i--) {
    if (r[i].length > 1) return r[i]
  }
  return []
})

const historyEntries = computed(() => {
  const r = rounds.value
  if (r.length <= 1) return []
  const currentIdx = (() => {
    for (let i = r.length - 1; i >= 0; i--) {
      if (r[i].length > 1) return i
    }
    return r.length - 1
  })()
  const entries = []
  for (let i = 0; i < currentIdx; i++) {
    entries.push(...r[i])
  }
  return entries
})

const historyRounds = computed(() => {
  const r = rounds.value
  if (r.length <= 1) return 0
  const currentIdx = (() => {
    for (let i = r.length - 1; i >= 0; i--) {
      if (r[i].length > 1) return i
    }
    return r.length - 1
  })()
  return currentIdx
})

const isAnimating = ref(false)
const battleGridRef = ref(null)
const playedCount = ref(0)

watch(() => props.snapshot?.combat?.events, async (events) => {
  if (!events || events.length === 0) {
    isAnimating.value = false
    return
  }
  isAnimating.value = true
  playedCount.value = 0
  visibleLogCount.value = 0
  await nextTick()
  if (battleGridRef.value) {
    battleGridRef.value.updateCellPositions()
    await battleGridRef.value.play(events, (idx) => {
      playedCount.value = idx + 1
      const event = events[idx]
      const logCount = event.logCount || 1
      visibleLogCount.value += logCount
    })
    battleGridRef.value.animationDone()
  }
  playedCount.value = events.length
  visibleLogCount.value = 999
  isAnimating.value = false
}, { immediate: true })

const visibleLogCount = ref(0)

const visibleCurrentEntries = computed(() => {
  const entries = currentRoundEntries.value
  if (!isAnimating.value) return entries
  return entries.slice(0, visibleLogCount.value + 1)
})

watch(visibleCurrentEntries, () => {
  nextTick(() => {
    if (logScrollRef.value) {
      logScrollRef.value.scrollTop = logScrollRef.value.scrollHeight
    }
  })
})

const currentTerrainColor = computed(() => {
  const map = props.snapshot?.map
  if (!map || !map.currentTerrain || !map.terrains) return null
  const info = map.terrains[map.currentTerrain]
  return info?.color || null
})

const combatActions = computed(() => {
  return (props.snapshot?.actions || []).filter(a => a.type === 'combat_command')
})

const filteredActions = computed(() => {
  return (props.snapshot?.actions || []).filter(
    a => a.type !== 'map_move' && a.type !== 'poi_interact' && a.type !== 'combat_command'
  )
})

function onMove(direction) {
  emit('action', { type: 'map_move', params: { direction, entityId: props.snapshot?.playerId } })
}

function onMoveTo(x, y) {
  emit('action', { type: 'map_moveto', params: { targetX: x, targetY: y, entityId: props.snapshot?.playerId } })
}

function onInterrupt() {
  emit('action', { type: '_interrupt' })
}

function onPoiAction(poi) {
  emit('action', { type: 'poi_interact', params: { poiId: poi.id, poiType: poi.type, target: poi.target } })
}
</script>

<style scoped>
.game-grid {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}
.panel-main { grid-column: 1; grid-row: 1; min-height: 0; overflow: hidden; }
.map-split { display: flex; height: 100%; gap: 0.5rem; }
.map-split > :first-child { flex: 1; min-width: 0; }
.map-split > :last-child { width: 8rem; flex-shrink: 0; border-left: 1px solid var(--panel-border-color); }
.panel-func { grid-column: 2; grid-row: 1; min-height: 0; }
.panel-log { grid-column: 1; grid-row: 2; min-height: 0; }
.panel-nav { grid-column: 2; grid-row: 2; min-height: 0; }
.log-scroll { height: 100%; overflow-y: auto; line-height: 1.8; }
.empty-log { color: var(--text-color); opacity: 0.5; }
.history-toggle { cursor: pointer; color: var(--link-color); margin-bottom: 0.3rem; }
.combat-history { border-bottom: 1px solid var(--panel-border-color); margin-bottom: 0.3rem; padding-bottom: 0.3rem; opacity: 0.7; }
.log-entry { margin-bottom: 0.15rem; }
</style>
```

- [ ] **Step 2: 运行后端测试确认无回归**

```
cd backend && mvn test -q
```
期望：全部 PASS

- [ ] **Step 3: 启动应用手工验证**

```
./start.ps1
```

验证清单：
- [ ] 新建角色后，左侧 Tab 出现「装备」
- [ ] 点「装备」Tab，显示人形图 + 6 个装备槽 + 右侧背包格子，背包里有物品
- [ ] 悬停背包物品，出现 tooltip（物品名稀有度颜色 + 属性行 + footer）
- [ ] 拖拽或点击背包物品，装备到对应槽位，槽位边框变为稀有度颜色
- [ ] 点击装备槽的 ✕，卸下装备，槽位恢复空状态
- [ ] 右上角「人物」Tab 显示新版 CharacterStatsTab：HP 条 / MP 条 / 属性行
- [ ] 属性行悬停，出现 tooltip（基础值 + 职业加成 + 合计）
- [ ] 顶部出现 `✦ 3 属性点待分配 [分配]` 横幅
- [ ] 点 `[分配]`，展开分配面板，+/- 正常，确认发送动作
- [ ] 进入战斗，「装备」Tab 消失（战斗时不显示）

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/SnapshotRenderer.vue
git commit -m "feat: SnapshotRenderer 接线装备/属性/Tooltip 新组件"
```

---

## 验证总结

后端：`cd backend && mvn test -q` → 全部 PASS（79+ 测试）

前端手动验证路径：
1. `./start.ps1`
2. 新建战士角色（自动获得初始背包物品 + 3 属性点）
3. 装备 Tab：拖拽装备铁剑/皮甲，悬停查看 tooltip
4. 人物 Tab：查看 HP 条/属性，悬停属性行看 breakdown，点 [分配] 加点
5. 进入战斗，确认「装备」Tab 消失，战斗结束后恢复
