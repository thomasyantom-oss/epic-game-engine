# 属性/装备/等级系统设计文档

**日期**：2026-05-26  
**状态**：待审阅

---

## 概述

为 Epic RPG 引擎添加统一的属性计算管道，支持装备系统和等级成长系统。核心原则：引擎提供基础设施，mod 定义所有游戏规则。

---

## 设计原则

- **引擎约定 UI 契约和事件协议，mod 负责所有游戏逻辑**
- **ModifierChain 是唯一的属性计算管道**，所有来源（职业/种族/装备/Buff 等）统一接入
- **Modifier 类型和实例分离**：类型在 YAML 声明规则，实例在 JS 注册计算
- **生死规则完全 mod 定义**，引擎只 fire 事件

---

## 一、属性计算管道（ModifierChain 激活）

### 1.1 核心概念

ModifierChain 已在引擎中存在但未被使用。本设计将其激活为唯一的属性计算管道。

**recalculate() 流程：**
1. Fire `entity.before_recalculate` 事件（mod 保存运行时值，如 hp）
2. 将 Entity 所有 Component 恢复到 baseState
3. 按 priority 从高到低依次调用每个 Modifier 的 `apply(entity)`
4. 记录每个 Modifier 执行前后的 diff（用于属性明细展示）
5. Fire `entity.after_recalculate` 事件（mod 恢复运行时值并做截断处理）

**关键设计**：后执行的 Modifier 读到的是前面已累积的值，天然支持"衍生自衍生"（如等级成长读取职业已加成后的力量值，再计算 maxHp）。

### 1.2 运行时值 vs 派生值

| 类型 | 示例 | 特点 |
|------|------|------|
| 派生值 | maxHp、attack、defense、speed | 由 Modifier 计算，recalculate 重算 |
| 运行时值 | hp、mp（当前值） | 战斗中直接修改，recalculate 不覆盖 |

Mod 通过 `before/after_recalculate` 事件自己管理运行时值的保存与恢复。两个事件共享同一个 `event.scratch` 临时对象用于传值：

```javascript
engine.on("entity.before_recalculate", function(event) {
  var h = event.entity.getComponent("Health");
  if (h) event.scratch.hp = h.getInt("hp"); // 保存到 scratch
});

engine.on("entity.after_recalculate", function(event) {
  var h = event.entity.getComponent("Health");
  if (h && event.scratch.hp !== undefined) {
    h.set("hp", Math.min(event.scratch.hp, h.getInt("maxHp")));
  }
});
```

**什么字段是"运行时值"完全由 mod 决定**。城墙 mod 可以用 `Durability.current`，角色 mod 用 `Health.hp`，引擎不感知。

### 1.3 Modifier 持久化策略

Modifier 是带 JS 函数的对象，无法序列化。引擎重启后，Entity Component 数据从 H2 恢复，Modifier 需要重新注册。

**策略**：引擎加载 Entity 后 fire `entity.loaded` 事件，各 handler 根据 Entity 当前 Component 状态重新注册 Modifier。

```javascript
engine.on("entity.loaded", function(event) {
  var entity = event.entity;
  // 如果有装备，重新注册装备 Modifier
  var slots = entity.getComponent("EquipmentSlots");
  if (slots && slots.get("weapon")) {
    registerEquipmentModifier(entity, slots.get("weapon"));
  }
  // 根据等级重新注册成长 Modifier
  var exp = entity.getComponent("Experience");
  if (exp) {
    registerLevelGrowthModifier(entity, exp.getInt("level"));
  }
});
```

---

## 二、ModifierType 系统

### 2.1 两层设计

**类型层（YAML）**：定义 Modifier 的规则和显示元数据，全局唯一。

```yaml
# mods/base-rules/modifier_types.yaml
modifier_types:
  - id: race
    label: 种族
    display_section: character_sheet
    display_order: 1
    stack_rule: exclusive        # 同时只能有一个
    base_priority: 200

  - id: class
    label: 职业
    display_section: character_sheet
    display_order: 2
    stack_rule: exclusive
    base_priority: 180

  - id: subclass
    label: 子职业
    display_section: character_sheet
    display_order: 3
    stack_rule: exclusive
    base_priority: 160

  - id: level
    label: 等级成长
    display_section: character_sheet
    display_order: 4
    stack_rule: exclusive
    base_priority: 90

  - id: equipment
    label: 装备
    display_section: character_sheet
    display_order: 5
    stack_rule: additive         # 多件装备叠加
    base_priority: 50

  - id: buff
    label: 增益/减益
    display_section: status_bar
    display_order: 1
    stack_rule: independent      # 每个独立实例
    base_priority: 10
```

**实例层（JS）**：注册在具体 Entity 上，负责实际计算。

```javascript
engine.addModifier(entityId, {
  typeId: "race",         // 引用类型，继承 stack_rule、display、base_priority
  id: "race_elf",         // 唯一实例 ID
  label: "精灵",           // 显示名（可覆盖 type label）
  priority: 200,          // 可省略，省略则用 type.base_priority；需要特例时可覆盖
  apply: function(entity) {
    var s = entity.getComponent("CombatStats");
    s.set("speed", s.getInt("speed") + 2);
  }
});
```

### 2.2 stack_rule 行为

| 规则 | 行为 |
|------|------|
| `exclusive` | 注册新 Modifier 时自动移除同 typeId 的旧 Modifier |
| `additive` | 同 typeId 多个 Modifier 共存（如多件装备） |
| `independent` | 同 id 也可以有多个实例（不同来源的同一 Buff） |

### 2.3 apply 函数的计算能力

`apply` 是任意 JS 函数，支持：

```javascript
// 平加
s.set("attack", s.getInt("attack") + 5);

// 百分比加成（读前面 Modifier 已算好的值）
s.set("attack", Math.floor(s.getInt("attack") * 1.2));

// 衍生计算（读其他属性）
var str = s.getInt("strength");
entity.getComponent("Health").set("maxHp",
  entity.getComponent("Health").getInt("maxHp") + str * 10);

// 条件加成
var hp = entity.getComponent("Health");
if (hp.getInt("hp") < hp.getInt("maxHp") * 0.5) {
  s.set("attack", s.getInt("attack") + 10); // 低血爆发
}
```

---

## 三、装备系统

### 3.1 物品作为 Entity

每件物品是 EntityStore 中的一个 Entity，带 `item` 标签：

```yaml
# 物品 Entity 的 Components
ItemMeta:  { name: "铁剑", type: "weapon", rarity: "common" }
ItemStats: { attack: 8 }   # 具体字段由 mod 自由定义
```

装备时，equip handler 读取 ItemStats 的内容来动态构建 Modifier 的 apply 函数，不需要额外存储 Modifier 描述。

### 3.2 角色 Entity 新增组件

```
EquipmentSlots { weapon: "iron_sword_001", armor: null, accessory: null }
Inventory      { items: ["iron_sword_001", "hp_potion_002"] }
```

装备槽位（weapon/armor/accessory 等）由角色 schema 声明，引擎不硬编码。

### 3.3 装备/卸下流程

```javascript
// 装备
engine.on("action.equip", function(event) {
  var slots = player.getComponent("EquipmentSlots");
  var oldItemId = slots.get(slotName);
  if (oldItemId) unregisterEquipmentModifier(playerId, oldItemId); // 先卸旧的
  slots.set(slotName, itemId);
  registerEquipmentModifier(playerId, itemId); // 注册新 Modifier → 自动 recalculate
});

// 卸下
engine.on("action.unequip", function(event) {
  engine.removeModifier(playerId, "equip_" + itemId);
  // engine 在 addModifier/removeModifier 后自动调用 recalculate
});
```

---

## 四、等级成长系统

### 4.1 Experience 组件

```
Experience { xp: 340, level: 5, pendingPoints: 0 }
```

### 4.2 两种成长模式（均由 mod 定义）

**自动成长**（适合怪物、NPC）：升级时由 schema 配置的成长公式自动注册 Modifier。

```javascript
engine.on("entity.level_up", function(event) {
  var level = event.entity.getComponent("Experience").getInt("level");
  // 更新等级成长 Modifier（exclusive → 自动替换旧的）
  engine.addModifier(event.entity.getId(), {
    typeId: "level", id: "level_growth", label: level + "级成长",
    apply: function(entity) {
      var s = entity.getComponent("CombatStats");
      s.set("attack", s.getInt("attack") + level * 1);
      // 成长公式完全由 mod 定义，可读 schema 或硬编码
      entity.getComponent("Health")
        .set("maxHp", entity.getComponent("Health").getInt("maxHp") + level * 5);
    }
  });
});
```

**手动分配**（适合玩家）：升级给属性点，玩家在角色面板分配。

```javascript
// 升级时给属性点
exp.set("pendingPoints", exp.getInt("pendingPoints") + 3);

// 玩家预览分配效果（临时 Modifier）
engine.on("action.preview_point_allocation", function(event) {
  engine.addModifier(playerId, {
    typeId: "point_preview", id: "preview_temp", priority: 1,
    apply: function(entity) { /* 预览加点 */ }
  });
  // 返回 snapshot → 前端显示预览值
  // 用户确认后移除 preview 注册真实 Modifier
});
```

---

## 五、生死系统

### 5.1 LifeState 组件

```
LifeState { state: "alive", deathCount: 0 }
```

state 的取值（alive/downed/dead/absent 等）完全由 mod 定义，引擎不硬编码。

### 5.2 引擎提供的事件

| 事件 | 触发方 | 触发时机 |
|------|--------|----------|
| `entity.hp_zero` | 引擎（伤害 handler 写入 hp 后检测） | hp 降到 ≤ 0 |
| `entity.state_change` | 引擎（LifeState component 写入时检测） | LifeState.state 变化 |
| `combat.check_end` | 引擎（entity.state_change 后自动触发） | 任何实体状态变化后 |
| `combat.victory` | mod | mod 判断胜利条件成立时 |
| `combat.defeat` | mod | mod 判断失败条件成立时 |

### 5.3 典型 Mod 规则示例

```javascript
// 可复活单位（第一次倒地，第二次消失）
engine.on("entity.hp_zero", function(event) {
  var ls = event.entity.getComponent("LifeState");
  if (ls.getInt("deathCount") == 0) {
    ls.set("state", "downed");
    ls.set("deathCount", 1);
  } else {
    ls.set("state", "dead");
  }
});

// 玩家死亡 → 战斗失败
engine.on("entity.state_change", function(event) {
  if (event.entity.hasTag("player") && event.newState == "downed") {
    engine.fire("combat.defeat", {});
  }
});

// 宠物死亡 → 场外1血等待，战斗继续
engine.on("entity.hp_zero", function(event) {
  if (event.entity.hasTag("companion")) {
    var ls = event.entity.getComponent("LifeState");
    ls.set("state", "absent");
    event.entity.getComponent("Health").set("hp", 1);
    engine.fire("combat.remove_participant", { id: event.entity.getId() });
    event.cancel(); // 阻止默认死亡逻辑
  }
});

// 战斗胜利后宠物满血复活
engine.on("combat.victory", function(event) {
  store.query(["companion", "absent"]).forEach(function(c) {
    var h = c.getComponent("Health");
    h.set("hp", h.getInt("maxHp"));
    c.getComponent("LifeState").set("state", "alive");
  });
});

// 战斗结束条件
engine.on("combat.check_end", function(event) {
  var allDead = store.query(["enemy", "combat_participant"])
    .every(function(e) {
      return e.getComponent("LifeState").get("state") == "dead";
    });
  if (allDead) engine.fire("combat.victory", {});
});
```

---

## 六、Snapshot 变化

### 6.1 主 Snapshot（不变大）

`GET /api/snapshot` 只返回最终属性值，与现在大小基本一致。

### 6.2 新增属性详情端点

`GET /api/character/stats`，仅在角色面板 Tab 打开时调用：

```json
{
  "attack": {
    "final": 21,
    "breakdown": [
      { "label": "基础",     "value": 5,  "type": "base" },
      { "label": "精灵种族", "value": 0,  "type": "race" },
      { "label": "战士职业", "value": 3,  "type": "class" },
      { "label": "5级成长",  "value": 5,  "type": "level" },
      { "label": "铁剑",     "value": 8,  "type": "equipment" }
    ]
  },
  "maxHp": {
    "final": 150,
    "breakdown": [
      { "label": "基础",     "value": 100, "type": "base" },
      { "label": "战士职业", "value": 50,  "type": "class" }
    ]
  }
}
```

---

## 七、引擎 vs Mod 职责边界

### 引擎提供（固定契约）

- ModifierChain 基础设施（addModifier / removeBySource / recalculate）
- ModifierType 注册表（加载 `modifier_types.yaml`，管理 stack_rule）
- JS API：`engine.addModifier()` / `engine.removeModifier()`
- 事件：`entity.before_recalculate` / `entity.after_recalculate` / `entity.loaded`
- 事件：`entity.hp_zero` / `entity.state_change` / `combat.check_end`
- `GET /api/character/stats` 端点（读 recalculate 时记录的 diff）

### Mod 定义（完全可扩展）

- ModifierType 定义（有哪些类别，priority，display，stack_rule）
- 装备槽位（schema 声明）
- 等级成长公式（自动 or 手动分配，由 schema + JS handler 实现）
- 哪些字段是"运行时值"（通过 before/after_recalculate 事件自己管理）
- 生死规则（LifeState 状态机，combat.check_end 逻辑）
- 战斗结束条件和胜负判定

---

## 八、前端设计

### 8.1 设计规范

沿用现有视觉语言：深蓝暗色系（`#1a1a2e` / `#16213e`）、霞鹜文楷字体、2px 粗边框（禁止 1px 细边框）。

### 8.2 角色属性 Tab（右上角）

**平时**：属性值全部白色，悬停显示 tooltip 来源明细。

**战斗中**：值与入战前的值比较实时染色：
- 绿色 = 高于入战前（有增益 buff）
- 红色 = 低于入战前（有减益 debuff）
- 白色 = 与入战前相同

装备/职业/种族/等级带来的加成**不染色**，只有战斗中的临时属性变化才染色。

Tooltip 格式（悬停显示，`position:fixed` 浮于面板外，不受滚动限制）：
```
铁剑           ← 物品名（稀有度颜色）
攻击  +8       ← 绿色
耐久  18/20
──────────
优质 · 单手剑  ← footer
```

战斗中 tooltip 仅显示"入战前值 + 临时变化"，不展开完整 modifier 链。

升级后在 HP/MP 条上方出现横幅："✦ N 属性点待分配 [分配]"，点击进入分配界面。

### 8.3 装备 / 背包面板（左上角，覆盖地图区域）

地图区 Tab 新增"装备 / 背包"Tab，点击后地图切换为装备面板。

**布局**：左右两栏
- **左栏**：角色图片（实际图片由 mod/资源提供，现为占位）+ 6 个方形装备槽围绕在图片对应部位（头/武器/护甲/副手/饰品/靴子）
- **右栏**：背包格子（N×4 网格）+ 整理按钮

**装备槽**：纯方形空框，装备名在框下方。已装备时边框和名称文字颜色 = 物品稀有度颜色（由 mod 的 `rarity_colors` 定义）。

**背包格子**：方形，边框染稀有度颜色（半透明）。悬停显示 tooltip。支持拖拽穿戴（拖到装备槽）。

**整理按钮**：按稀有度从高到低排序，已装备物品优先排在前，有淡入动画。

**稀有度颜色**（由 mod `item_rarity.yaml` 定义，snapshot 带 `rarityColor` 字段）：
| 稀有度 | 颜色 |
|--------|------|
| 普通 | `#ffffff` |
| 优质 | `#4ade80` |
| 稀有 | `#60a5fa` |
| 史诗 | `#a78bfa` |
| 传说 | `#ffd93d` |

### 8.4 属性点分配界面

升级后在角色 Tab 内出现"✦ 分配属性点"Tab（或弹层）：
- 显示每条属性的当前值 → 预览值 + 差值
- +/- 按钮逐点分配，剩余点数实时更新
- 确认/重置按钮
- 确认后 Tab 消失，属性点归零

### 8.5 新增 Vue 组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `CharacterStatsTab.vue` | 右上角 character tab | 属性列表 + 战斗染色 + tooltip |
| `EquipmentBagPanel.vue` | 左上角新 tab | 角色图 + 装备槽 + 背包格子 + 整理 |
| `AttributeAllocPanel.vue` | character tab 内 | 升级属性点分配界面 |
| `ItemTooltip.vue` | 全局 fixed | 统一 tooltip，浮于所有面板外 |

---

## 九、需要迁移的现有代码

| 现有代码 | 变化 |
|----------|------|
| `select.js` 手动计算职业加成 | 改为注册 ModifierType + Modifier |
| `buff/*.js` 直接修改 Component | 改为注册 Buff Modifier，tick 时更新 Modifier → recalculate |
| `damage_calc.js` | 不需要改，继续读最终属性值 |
| `SnapshotService` | 新增读取 `/api/character/stats` 的 diff 数据路径 |
| `GeneratorService` | 激活 baseComponents 应用逻辑，创建 Entity 并完成所有初始 Modifier 注册后调用 `setBase()`，此后 recalculate 以这个状态为基准 |
