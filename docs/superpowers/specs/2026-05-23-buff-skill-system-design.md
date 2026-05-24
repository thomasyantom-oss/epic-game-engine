# Buff/状态效果系统 + 技能系统设计

## 概述

两个紧密关联的框架系统。Buff 系统是技能系统的前置（技能施加 buff），但各自独立运作。

---

## Buff/状态效果系统

### 架构

**全局 handler + 过滤 + 生命周期事件**

- 每种 buff 类型 = 一对文件（YAML 元数据 + JS 效果逻辑）
- buff 数据存在实体的 component 上（`Buff_<id>`），携带参数（伤害值、层数、来源等）
- 添加/移除走统一 API：`engine.applyBuff(targetId, buffId, data)` / `engine.removeBuff(targetId, buffId)`
- API 不可绕过——裸操作 component 不触发生命周期事件，免疫/叠加/UI 不生效
- buff 的 JS handler 在 mod 加载时全局注册，运行时通过检查实体有无对应 component 来过滤

### 生命周期事件

| 事件 | 触发时机 | 用途 |
|------|---------|------|
| `buff.apply` | applyBuff 调用时 | 可被 cancel（实现免疫） |
| `buff.applied` | buff 实际添加到实体后 | 通知 UI 播放动画、其他系统响应 |
| `buff.remove` | removeBuff 调用时 | 可被 cancel（实现"不可驱散"） |
| `buff.removed` | buff 实际移除后 | on-removal 效果（爆炸/反弹） |

### 叠加策略

由 buff 的 YAML 定义 `stacking` 字段，`engine.applyBuff` 内部执行对应逻辑：

| 策略 | 行为 |
|------|------|
| `refresh` | 同 ID 只能存在一个，重复施加刷新数据（如重置持续时间） |
| `stack` | 同 ID 叠加层数（component 里 stacks +1），上限由 `max_stacks` 控制 |
| `replace` | 同 ID 新的覆盖旧的（先 remove 旧的再 apply 新的） |
| `independent` | 每次施加都是独立实例（component key 加后缀：`Buff_poison_1`） |

### 过期机制

纯事件驱动——buff 的 JS handler 自己决定何时调用 `engine.removeBuff` 移除自己：

```javascript
// buffs/poison.js
engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var poison = entity.getComponent("Buff_poison");
        if (poison === null) continue;

        // 执行效果：每层每回合扣伤害
        var stacks = poison.getInt("stacks");
        var dmgPerStack = poison.getInt("damage");
        // ... 扣血逻辑 ...

        // 递减计数，到 0 移除
        var remaining = poison.getInt("remaining") - 1;
        if (remaining <= 0) {
            engine.removeBuff(entity.getId(), "poison");
        } else {
            poison.set("remaining", remaining);
        }
    }
});
```

不同 buff 可以响应不同事件来过期——回合结束、被攻击时、施放技能时，完全由 handler 决定。

### Buff 定义格式

```yaml
# mods/base-rules/buffs/poison.yaml
id: poison
name: "中毒"
stacking: stack
max_stacks: 5
indicator:
  corner: bottom-left
  color: "#9c27b0"
animation:
  apply:
    - type: debuff_down
      target: target
      color: "#9c27b0"
      intensity: normal
  remove: []
```

```javascript
// mods/base-rules/buffs/poison.js
// 回合结束扣血
engine.on("combat.round_end", 50, function(event) { ... });

// 被施加时的额外逻辑（如果需要）
engine.on("buff.applied", 50, function(event) {
    if (event.get("buffId") !== "poison") return;
    // ...
});
```

### 引擎 API（暴露给 JS）

```javascript
engine.applyBuff(targetId, buffId, data)   // data = {stacks, damage, remaining, source, ...}
engine.removeBuff(targetId, buffId)
engine.hasBuff(targetId, buffId)           // 快捷检查
engine.getBuffData(targetId, buffId)       // 获取 buff component 数据
```

---

## 技能系统

### 架构

**静态定义（YAML + JS）+ 角色运行时数据 + 纯 JS 执行**

- 技能定义 = YAML（元数据/动画）+ JS（条件/目标选择/效果执行）
- 角色身上存 `Skills` 组件，记录拥有的技能 ID + 运行时状态
- 战斗指令 type = 技能 ID（`combat_command: { type: "fireball", targets: [...] }`）
- "普通攻击"/"防御"也是技能，默认所有角色拥有
- 引擎只提供"给角色添加/移除技能"的 API，获取方式由 mod 定义

### UI 分类

| 类别 | 属性 | 说明 |
|------|------|------|
| `skill` | 可被沉默 | 主动技能，受控制 debuff 影响 |
| `action` | 不可被沉默 | 道具/逃跑/移动，系统级操作 |

前端渲染指令列表时平级展示，但 `skill` 类在角色被沉默时置灰不可用。

### 角色的 Skills 组件

```json
{
  "type": "Skills",
  "data": {
    "list": [
      { "id": "basic_attack", "level": 1, "cooldown": 0, "mods": {} },
      { "id": "fireball", "level": 3, "cooldown": 0, "mods": { "damage_bonus": 5 } },
      { "id": "defend", "level": 1, "cooldown": 0, "mods": {} }
    ]
  }
}
```

### 技能执行流程

```
玩家选择技能 → 前端请求目标选择步骤 → 前端依次让玩家选目标 → 发送完整指令到后端 → handler 执行效果
```

1. **条件检查**：技能 JS 导出 `canUse(caster)` 判断能不能用（CD/资源/沉默等），不满足前端置灰
2. **目标选择**：技能 JS 导出 `getTargetSteps(caster, combatants)` 返回选择步骤列表
3. **效果执行**：收到完整指令后，技能 handler 监听 `combat.unit_action`，执行效果逻辑

### 目标选择步骤

技能返回一个步骤数组，前端按序执行每步：

```javascript
// skills/chain_lightning.js 中
function getTargetSteps(caster, combatants) {
    return [
        { prompt: "选择主目标", filter: "enemy", count: 1 },
        { prompt: "选择跳跃目标", filter: "enemy", count: 2, exclude_previous: true }
    ];
}
```

前端根据每步的 filter/count 限制玩家的选择，收集完所有步骤的结果后发送。

常见 filter 值：`enemy` / `ally` / `self` / `any` / `dead_ally`（复活用）

### 技能定义格式

```yaml
# mods/base-rules/skills/fireball.yaml
id: fireball
name: "火球术"
category: skill
silenceable: true
description: "向目标发射一颗火球，造成火焰伤害并附加灼烧"
animation:
  - type: projectile
    shape: circle
    color: "#ff4400"
    trail: glow
    speed: fast
    from: actor
    to: target
  - type: impact
    target: target
    color: "#ff4400"
  - type: shake
    target: target
    intensity: normal
  - type: damage_number
    target: target
    color: damage
```

```javascript
// mods/base-rules/skills/fireball.js

// 条件：MP >= 10
skill.canUse = function(caster) {
    var mana = caster.getComponent("Mana");
    if (mana === null) return false;
    return mana.getInt("mp") >= 10;
};

// 目标选择：单个敌方
skill.getTargetSteps = function(caster, combatants) {
    return [{ prompt: "选择目标", filter: "enemy", count: 1 }];
};

// 效果执行
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "fireball") return;

    var casterId = event.get("actorId");
    var targetId = cmd.get("targets").get(0);
    var caster = store.get(casterId);
    var target = store.get(targetId);

    // 消耗 MP
    var mana = caster.getComponent("Mana");
    mana.set("mp", mana.getInt("mp") - 10);

    // 读取角色技能数据（等级加成等）
    var skillData = engine.getSkillData(casterId, "fireball");
    var baseDamage = 15 + (skillData.level - 1) * 3;
    var bonus = skillData.mods.damage_bonus || 0;
    var totalDamage = baseDamage + bonus;

    // 触发伤害计算（让 buff 能介入）
    var calcEvent = engine.newEvent("combat.damage_calc");
    calcEvent.set("attackerId", casterId);
    calcEvent.set("targetId", targetId);
    calcEvent.set("baseDamage", totalDamage);
    calcEvent.set("combatId", event.get("combatId"));
    engine.fire("combat.damage_calc", calcEvent);

    var finalDamage = calcEvent.get("damage");

    // 扣血
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - finalDamage));

    // 触发伤害事件（动画/日志/死亡检测）
    var dmgEvent = engine.newEvent("combat.damage_dealt");
    dmgEvent.set("attackerId", casterId);
    dmgEvent.set("targetId", targetId);
    dmgEvent.set("damage", finalDamage);
    dmgEvent.set("combatId", event.get("combatId"));
    engine.fire("combat.damage_dealt", dmgEvent);

    // 施加灼烧 buff
    engine.applyBuff(targetId, "burning", { damage: 5, remaining: 3, source: casterId });
});
```

### 引擎 API（暴露给 JS）

```javascript
engine.grantSkill(entityId, skillId, data)    // 给角色添加技能
engine.removeSkill(entityId, skillId)          // 移除技能
engine.getSkillData(entityId, skillId)         // 获取角色的技能运行时数据（level/cd/mods）
engine.setSkillData(entityId, skillId, data)   // 更新技能运行时数据
```

### 前端变更

- 战斗指令不再硬编码 ATTACK/DEFEND/FLEE
- 后端 actions handler 根据角色 Skills 组件动态生成可用技能列表
- 每个技能 action 带 `category`（skill/action）和 `targetSteps`
- 前端收到 targetSteps 后进入多步选择流程
- 沉默状态下 `category: "skill"` 的选项置灰

---

## 两个系统的协作

```
技能执行 → 触发 combat.damage_calc → buff handler 介入修改伤害
技能执行 → 调用 engine.applyBuff → 触发 buff.apply → 免疫检查 → buff.applied → UI 动画
buff handler → 每回合触发效果 → 调用 engine.removeBuff → buff.removed → UI 更新
```

---

## 实现顺序

1. **Buff 框架**：engine API（applyBuff/removeBuff）+ 生命周期事件 + 叠加逻辑
2. **Buff 示例**：实现 poison / burning / defending 三个 buff 验证框架
3. **技能框架**：engine API（grantSkill/getSkillData）+ Skills 组件 + 执行管道
4. **技能示例**：将现有 ATTACK/DEFEND/FLEE 改造为技能 + 新增 fireball 验证
5. **前端改造**：动态技能列表 + 多步目标选择 + 沉默置灰
6. **动画对接**：技能 YAML 的 animation 字段接入已有的 AnimationLayer

---

## 约束

- Buff 添加/移除必须走 `engine.applyBuff` / `engine.removeBuff`，不得裸操作 component
- 所有 buff 效果逻辑在 JS handler 里，YAML 只定义元数据
- 技能效果逻辑在 JS handler 里，YAML 定义元数据 + 动画序列
- 技能获取方式完全由 mod 定义，引擎只提供 API
- `category: "action"` 的技能不受沉默/封印影响
