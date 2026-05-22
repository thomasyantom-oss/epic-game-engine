# 事件驱动微内核引擎重构

## 概述

将现有硬编码引擎重构为事件驱动微内核架构。引擎只做调度（事件总线 + 实体存储 + 脚本运行时），所有游戏规则由模块实现。模块可改变任何游戏机制，不受引擎限制。

## 核心架构

```
┌───────────────────────────────────────────────┐
│                ENGINE CORE                     │
│  Event Bus + Entity Store + Script Runtime    │
└───────────────────────────────────────────────┘
        │ fire/listen       │ read/write       │ execute
        ▼                   ▼                  ▼
┌────────────┐  ┌────────────┐  ┌────────────────┐
│ base-rules │  │ blood-magic│  │ necromancer    │
│  (默认规则) │  │ (血魔法)   │  │ (亡灵术士)    │
└────────────┘  └────────────┘  └────────────────┘
```

### 引擎核心职责

1. **Event Bus** — fire(event), on(event, priority, handler), cancel()。模块自由注册/触发事件，无预定义事件列表。
2. **Entity Store** — ECS 组件结构管理，tag 索引，modifier 链计算。
3. **Script Runtime** — GraalJS 沙箱，执行模块 JS 脚本，暴露引擎 API。

引擎不含任何游戏规则。"攻击扣血"、"速度决定先攻"都是 base-rules 模块实现的。

## 实体模型 (ECS)

### Object 结构

所有游戏对象（玩家、怪物、物品、技能）统一结构：

```
Object = Main Component + Sub Components[]
```

- **Main Component**：定义"是什么" + 作用域/生命周期
- **Sub Component**：定义"有什么效果" + compatible_with 限制合法载体

示例：

```
Vampire Sword =
  ├── Main: Equipment(sword, level 1)         → 作用域: while_equipped
  └── Sub:  EpicModifier(blood_magic, level 2) → 效果: replace_resource(mp, hp)

Blood Magic Potion =
  ├── Main: Item(potion, level 1, effect: 50%) → 作用域: 5_rounds
  └── Sub:  EpicModifier(blood_magic, level 2) → 效果: replace_resource(mp, hp)

Player =
  ├── Main: Character(human, level 5)          → 作用域: permanent
  ├── Sub:  Class(warrior, level 3)            → 可换（转职）
  ├── Sub:  Talent(berserker_tree)             → 可加可移除
  └── Sub:  Curse(blood_pact)                  → 剧情强制挂上

Monster =
  ├── Main: Creature(goblin, level 2)
  ├── Sub:  Mutation(berserker)                → 变异
  └── Sub:  Buff(leader_aura_buff)             → 被光环影响
```

众生平等——玩家和怪物结构相同，区别在于是否有 Persistence 标记。

### Schema

定义组件类型的约束，供编辑器/生成器/校验使用：

```yaml
# schemas/sub/blood_magic.schema.yaml
id: blood_magic
type: epic_modifier
compatible_with: [equipment, potion, skill]
level_range: [1, 5]
fields:
  - name: effect_type
    type: enum
    values: [replace_resource, convert_resource]
  - name: source_resource
    type: string
    default: "mp"
  - name: target_resource
    type: string
    default: "hp"
```

Schema 限制组合合法性（swords_blade 不能挂在 staff 上），但不限制运行时行为。

### Modifier 链

管理效果叠加和撤销：

- 每个生效的 Sub Component 注册一个 modifier
- Modifier 带 priority，按顺序执行
- 后面的 modifier 能读到前面叠完的结果
- 移除来源（卸装备/buff 到期）→ 按 source 移除对应 modifier → 从基础状态重算

```
基础状态: Health{hp:100}, Mana{mp:50}

modifier 1 (ring_A, pri 100): mp += 20           → mp=70
modifier 2 (blood_staff, pri 200): hp += mp, 删mp → hp=170, 无mp

脱 ring_A → 移除 modifier 1 → 重算:
  基础 → modifier 2: hp += 50, 删mp → hp=150, 无mp
```

### Modifier 作用域

- Modifier 只能改自身实体
- 跨实体效果走事件系统：fire 事件 → handler 给目标挂 Buff 组件 → Buff 在目标的 modifier 链里生效

### Tag 系统

贯穿所有层级（实体、组件、物品），用于查询和逻辑判断：

```javascript
entity.hasTag("blood_magic")
engine.getEntitiesWith("Burning")  // 查有 Burning 组件的实体
item.tags.includes("hp")           // 装备 tag 用于数值计算
```

### 持久化

- **Persistence 组件**标记是否存盘
- 有标记的实体（玩家、宠物、剧情 Boss）：所有组件和状态存盘，包括临时效果剩余时间
- 无标记的实体（野怪、环境 NPC）：重启后按模板重新生成
- 存盘时机：关键操作时（装备变更、战斗结束、场景切换）
- 存储方式：数据库（H2 或后续升级）

## 事件系统

### 设计原则

- 模块自由定义和触发事件，引擎无预定义列表
- 命名约定：`领域.动作`（如 `combat.damage_calc`）
- Handler 按 priority 数字排序执行
- Handler 可 cancel 事件（阻止后续低优先级 handler）
- 同步执行，一口气跑完不中断

### 战斗事件流（全体下令 → 统一结算）

```
指令阶段: 前端收集所有玩家指令（含条件决策如"击杀后吸收灵魂"）
结算阶段: 引擎 fire 事件链，纯计算，不中断

combat.round_start
  → combat.determine_order (先攻排序)
  → combat.unit_action (每个单位按序)
    → combat.skill_execute
      → combat.skill_cost (消耗计算)
      → combat.damage_calc (伤害计算)
      → combat.damage_dealt
        → combat.unit_death (若死亡)
  → combat.round_end
```

### 事件可覆盖示例

```javascript
// base-rules: 默认扣 MP
engine.on("combat.skill_cost", 100, (event) => {
    event.caster.mp -= event.skill.cost;
});

// blood-magic: 优先级高，拦截默认逻辑
engine.on("combat.skill_cost", 90, (event) => {
    if (event.caster.hasComponent("BloodMagic")) {
        event.caster.hp -= event.skill.cost * 0.8;
        event.cancel();
    }
});
```

## 模块结构

```
mods/base-rules/
├── mod.yaml                 # 元信息 + 加载顺序
├── schemas/                 # 组件 schema 定义
│   ├── main/
│   │   ├── character.schema.yaml
│   │   ├── creature.schema.yaml
│   │   ├── equipment.schema.yaml
│   │   └── item.schema.yaml
│   └── sub/
│       ├── class.schema.yaml
│       ├── mutation.schema.yaml
│       └── enchant.schema.yaml
├── handlers/                # JS 脚本（游戏规则）
│   ├── combat/
│   │   ├── combat_flow.js
│   │   ├── damage_calc.js
│   │   ├── skill_cost.js
│   │   └── death_check.js
│   ├── map/
│   │   ├── movement.js
│   │   └── pathfinding.js
│   └── buff/
│       ├── apply.js
│       ├── tick.js
│       └── expire.js
├── entities/                # 实体模板（数据）
│   ├── monsters/
│   ├── items/
│   └── skills/
└── generators/              # 生成规则（可选）
    └── random_monster.js
```

模块可以：
- 注册任意事件的 handler
- 定义新的 schema 类型
- 提供实体模板
- 触发新事件（创建新领域）
- 横切多个系统（血魔法同时影响战斗、地图、UI）

## 前端架构

### 无状态快照渲染器

前端不维护游戏状态，只渲染上次收到的快照。

通信模型：

```
前端操作 → POST /api/action { type, params }
后端执行事件链 → 返回 { result, snapshot }
前端用 snapshot 重绘全部 UI
```

"点了一个已经不存在的怪"是合法操作——后端返回"目标不存在"+ 最新快照，前端刷新画面。

不需要 WebSocket、不需要推送、不需要 refreshPanels 机制。

### UI 数据驱动

后端返回 UI 描述数据，前端只管渲染：

```json
{
  "status_bars": [
    { "id": "hp", "label": "生命", "current": 170, "max": 170, "color": "#e74c3c", "priority": 1 }
  ],
  "buffs": [
    { "id": "blood_potion", "name": "血魔药水", "remaining": "87回合", "priority": 3 }
  ],
  "combat": { ... },
  "map": { ... },
  "actions": [ ... ]
}
```

- 后端通过 `ui.render_*` 事件组装 UI 数据，模块可以增删改 UI 元素
- 每个 UI 元素带 priority，空间不够时前端按优先级裁剪/折叠
- 前端有通用排版规则（条超过N个→折叠），不含游戏知识

## 模块热加载

- Handler（JS 文件）：文件变化 → 自动重新加载，立即生效
- Schema / 实体定义变更：需要重启
- 开发体验：改逻辑脚本 → 保存 → 下次操作立即用新逻辑

## 生成器

读 schema → 选 Main → 选合法 Sub → 填数值 → 输出 Object。

两个入口：
- **Claude 对话**：读 schema 文件，按约束组合，写入 mods/ 目录
- **运行时随机生成**：模块监听 `entity.generate_request`，按 schema 规则 + level 缩放 + 权重随机组装

生成器本身也是模块，不同 mod 可提供不同生成策略。

Web 编辑器搁置（可能与打造系统复用，后续讨论）。

## 现有系统迁移

| 现有代码 | 重构后 |
|---|---|
| CombatEngine (硬编码结算) | base-rules/handlers/combat/ 模块 |
| CombatService (ConcurrentHashMap) | Entity Store 管理战斗实体 |
| SceneService | base-rules/handlers/scene/ 模块 |
| MapService + Pathfinder | base-rules/handlers/map/ 模块 |
| ActionController + ActionHandler | Event Bus dispatch |
| PlayerState (H2 JPA) | Entity Store + Persistence 组件 |
| ModLoader + ModRegistry | Engine Core 模块加载 |
| 前端 composables (useCombat, useMap...) | 统一快照渲染 |
| refreshPanels 机制 | 删除，每次返回完整快照 |

## 设计约束

- 结算同步执行，不中断等待玩家输入
- 所有决策在指令阶段完成（梦幻西游模式）
- 单人按多人架构设计（不堵死多人路），但不处理多人交互逻辑
- 玩家数据持久化，世界数据内存中按模板生成
