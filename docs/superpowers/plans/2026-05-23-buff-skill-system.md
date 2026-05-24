# Buff/状态效果 + 技能系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现通用 buff 框架（全局 handler + 生命周期事件 + 叠加策略）和技能系统（静态定义 + 角色 Skills 组件 + 纯 JS 执行），将硬编码的 ATTACK/DEFEND/FLEE 改造为数据驱动的技能。

**Architecture:** 引擎层新增 BuffService（Java，暴露 applyBuff/removeBuff API 给 JS，触发生命周期事件），技能系统纯 mod 层实现（JS handler + YAML 定义）。前端改造 BattleGrid 指令区支持动态技能列表。

**Tech Stack:** Java 21 + Spring Boot (BuffService), GraalJS (mod handlers), Vue 3 (frontend)

---

## File Structure

| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/epic/engine/buff/BuffService.java` | 新建：applyBuff/removeBuff API，叠加逻辑，触发生命周期事件 |
| `backend/src/main/java/com/epic/engine/core/EngineBootstrap.java` | 修改：绑定 BuffService 到 JS |
| `backend/src/main/java/com/epic/engine/core/EngineConfig.java` | 修改：注册 BuffService Bean |
| `mods/base-rules/buffs/defending.yaml` | 新建：防御 buff 元数据 |
| `mods/base-rules/buffs/defending.js` | 新建：防御 buff 效果（减伤） |
| `mods/base-rules/buffs/poison.yaml` | 新建：中毒 buff 元数据 |
| `mods/base-rules/buffs/poison.js` | 新建：中毒 buff 效果（每回合扣血） |
| `mods/base-rules/buffs/burning.yaml` | 新建：灼烧 buff 元数据 |
| `mods/base-rules/buffs/burning.js` | 新建：灼烧 buff 效果 |
| `mods/base-rules/skills/basic_attack.yaml` | 新建：普通攻击技能定义 |
| `mods/base-rules/skills/basic_attack.js` | 新建：普通攻击效果 |
| `mods/base-rules/skills/defend.yaml` | 新建：防御技能定义 |
| `mods/base-rules/skills/defend.js` | 新建：防御效果（施加 defending buff） |
| `mods/base-rules/skills/flee.yaml` | 新建：逃跑定义 |
| `mods/base-rules/skills/flee.js` | 新建：逃跑效果 |
| `mods/base-rules/skills/fireball.yaml` | 新建：火球术定义 |
| `mods/base-rules/skills/fireball.js` | 新建：火球术效果 |
| `mods/base-rules/handlers/combat/skill_loader.js` | 新建：加载技能定义，注册 canUse/getTargetSteps |
| `mods/base-rules/handlers/ui/actions.js` | 修改：战斗指令从 Skills 组件动态生成 |
| `mods/base-rules/handlers/combat/death_check.js` | 修改：ATTACK/DEFEND 逻辑迁移到技能 JS |
| `mods/base-rules/handlers/combat/combat_flow.js` | 修改：不再手动清 Defending component |
| `mods/base-rules/handlers/combat/damage_calc.js` | 修改：检查 Buff_defending 而非 Defending |
| `mods/base-rules/handlers/combat/start_combat.js` | 修改：command type 改为技能 ID |
| `mods/base-rules/handlers/character/select.js` | 修改：创建角色时添加 Skills 组件 |
| `frontend/src/components/combat/BattleGrid.vue` | 修改：支持多步目标选择 |

---

### Task 1: BuffService — Java 层 API

**Files:**
- Create: `backend/src/main/java/com/epic/engine/buff/BuffService.java`
- Modify: `backend/src/main/java/com/epic/engine/core/EngineConfig.java`
- Modify: `backend/src/main/java/com/epic/engine/core/EngineBootstrap.java`

- [ ] **Step 1: 创建 BuffService 类**

```java
package com.epic.engine.buff;

import com.epic.engine.core.*;
import org.graalvm.polyglot.HostAccess;
import java.util.Map;

public class BuffService {

    private final EventBus bus;
    private final EntityStore store;

    public BuffService(EventBus bus, EntityStore store) {
        this.bus = bus;
        this.store = store;
    }

    @HostAccess.Export
    public boolean applyBuff(String targetId, String buffId, Map<String, Object> data) {
        Entity target = store.get(targetId);
        if (target == null) return false;

        GameEvent applyEvent = new GameEvent("buff.apply");
        applyEvent.set("targetId", targetId);
        applyEvent.set("buffId", buffId);
        applyEvent.set("data", data);
        bus.fire("buff.apply", applyEvent);

        if (applyEvent.isCancelled()) return false;

        String compName = "Buff_" + buffId;
        Component existing = target.getComponent(compName);

        String stacking = data != null && data.containsKey("stacking")
                ? (String) data.get("stacking") : "replace";

        if (existing != null) {
            switch (stacking) {
                case "stack" -> {
                    int stacks = existing.has("stacks") ? existing.getInt("stacks") : 1;
                    int maxStacks = existing.has("maxStacks") ? existing.getInt("maxStacks") : 99;
                    existing.set("stacks", Math.min(stacks + 1, maxStacks));
                    if (data != null) data.forEach((k, v) -> {
                        if (!k.equals("stacking") && !k.equals("stacks")) existing.set(k, v);
                    });
                }
                case "refresh" -> {
                    if (data != null) data.forEach((k, v) -> {
                        if (!k.equals("stacking")) existing.set(k, v);
                    });
                }
                case "independent" -> {
                    String indepName = compName + "_" + System.currentTimeMillis();
                    Component comp = new Component(indepName);
                    comp.set("buffId", buffId);
                    if (data != null) data.forEach(comp::set);
                    target.addComponent(comp);
                }
                default -> {
                    target.removeComponent(compName);
                    Component comp = new Component(compName);
                    if (data != null) data.forEach(comp::set);
                    target.addComponent(comp);
                }
            }
        } else {
            Component comp = new Component(compName);
            if (data != null) data.forEach(comp::set);
            if (!comp.has("stacks")) comp.set("stacks", 1);
            target.addComponent(comp);
        }

        GameEvent appliedEvent = new GameEvent("buff.applied");
        appliedEvent.set("targetId", targetId);
        appliedEvent.set("buffId", buffId);
        bus.fire("buff.applied", appliedEvent);

        return true;
    }

    @HostAccess.Export
    public boolean removeBuff(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return false;

        String compName = "Buff_" + buffId;
        if (!target.hasComponent(compName)) return false;

        GameEvent removeEvent = new GameEvent("buff.remove");
        removeEvent.set("targetId", targetId);
        removeEvent.set("buffId", buffId);
        bus.fire("buff.remove", removeEvent);

        if (removeEvent.isCancelled()) return false;

        target.removeComponent(compName);

        GameEvent removedEvent = new GameEvent("buff.removed");
        removedEvent.set("targetId", targetId);
        removedEvent.set("buffId", buffId);
        bus.fire("buff.removed", removedEvent);

        return true;
    }

    @HostAccess.Export
    public boolean hasBuff(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return false;
        return target.hasComponent("Buff_" + buffId);
    }

    @HostAccess.Export
    public Component getBuffData(String targetId, String buffId) {
        Entity target = store.get(targetId);
        if (target == null) return null;
        return target.getComponent("Buff_" + buffId);
    }
}
```

- [ ] **Step 2: 注册 BuffService Bean**

在 `EngineConfig.java` 中添加：

```java
import com.epic.engine.buff.BuffService;

@Bean
public BuffService buffService(EventBus eventBus, EntityStore entityStore) {
    return new BuffService(eventBus, entityStore);
}
```

修改 `engineBootstrap` Bean 参数添加 BuffService 并传入。

- [ ] **Step 3: 绑定到 JS Runtime**

在 `EngineBootstrap.java` 的 `boot()` 方法中，在 `scriptRuntime.bindService("sessions", sessionService)` 之后添加：

```java
scriptRuntime.bindService("buffs", buffService);
```

- [ ] **Step 4: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: 无输出（编译成功）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/epic/engine/buff/BuffService.java backend/src/main/java/com/epic/engine/core/EngineConfig.java backend/src/main/java/com/epic/engine/core/EngineBootstrap.java
git commit -m "feat: BuffService — applyBuff/removeBuff API + 生命周期事件 + 叠加策略"
```

---

### Task 2: Defending buff 改造

**Files:**
- Create: `mods/base-rules/buffs/defending.js`
- Modify: `mods/base-rules/handlers/combat/damage_calc.js`
- Modify: `mods/base-rules/handlers/combat/combat_flow.js`
- Modify: `mods/base-rules/handlers/combat/death_check.js`

- [ ] **Step 1: 创建 defending buff handler**

```javascript
// mods/base-rules/buffs/defending.js
// 防御状态：减伤 50%，回合开始时自动移除

engine.on("combat.resolve_round", 98, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasComponent("Buff_defending")) {
            buffs.removeBuff(entity.getId(), "defending");
        }
    }
});
```

- [ ] **Step 2: 修改 damage_calc.js 检查 Buff_defending**

将 `var isDefending = target.hasComponent("Defending");` 改为：

```javascript
var isDefending = target.hasComponent("Buff_defending");
```

- [ ] **Step 3: 修改 combat_flow.js 移除旧的 Defending 清理**

删除 combat_flow.js 中清除 Defending 的代码块：

```javascript
// 删除这段：
// Clear defending status from previous round
var combatants = store.getByTagAsList("combat:" + combatId);
for (var i = 0; i < combatants.size(); i++) {
    combatants.get(i).removeComponent("Defending");
}
```

- [ ] **Step 4: 修改 death_check.js 中 DEFEND 逻辑**

将 `} else if (cmdTypeStr === "DEFEND") { var actor = store.get(actorId); actor.addComponent(engine.newComponent("Defending")); }` 改为：

```javascript
} else if (cmdTypeStr === "DEFEND") {
    buffs.applyBuff(actorId, "defending", engine.newMap());
}
```

- [ ] **Step 5: 验证编译 + 启动**

Run: `cd backend && mvn compile -q`

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/buffs/defending.js mods/base-rules/handlers/combat/damage_calc.js mods/base-rules/handlers/combat/combat_flow.js mods/base-rules/handlers/combat/death_check.js
git commit -m "feat: 防御改造为 buff — 走 BuffService API，回合开始自动移除"
```

---

### Task 3: Poison buff 示例

**Files:**
- Create: `mods/base-rules/buffs/poison.yaml`
- Create: `mods/base-rules/buffs/poison.js`

- [ ] **Step 1: 创建 poison.yaml**

```yaml
id: poison
name: "中毒"
stacking: stack
max_stacks: 5
indicator:
  corner: bottom-left
  color: "#9c27b0"
```

- [ ] **Step 2: 创建 poison.js**

```javascript
// mods/base-rules/buffs/poison.js
// 中毒：每回合结束时每层扣 damage 点 HP，递减 remaining，到 0 移除

engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var poison = entity.getComponent("Buff_poison");
        if (poison === null) continue;

        var stacks = poison.has("stacks") ? poison.getInt("stacks") : 1;
        var dmgPerStack = poison.has("damage") ? poison.getInt("damage") : 3;
        var totalDmg = stacks * dmgPerStack;

        var health = entity.getComponent("Health");
        if (health !== null) {
            health.set("hp", Math.max(0, health.getInt("hp") - totalDmg));
        }

        var remaining = poison.has("remaining") ? poison.getInt("remaining") : 3;
        remaining--;
        if (remaining <= 0) {
            buffs.removeBuff(entity.getId(), "poison");
        } else {
            poison.set("remaining", remaining);
        }
    }
});
```

- [ ] **Step 3: Commit**

```bash
git add mods/base-rules/buffs/poison.yaml mods/base-rules/buffs/poison.js
git commit -m "feat: 中毒 buff — 每回合每层扣血，到期自动移除"
```

---

### Task 4: Skills 组件 + 角色创建时赋予技能

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js`
- Modify: `mods/base-rules/schemas/sub/class_warrior.schema.yaml`
- Modify: `mods/base-rules/schemas/sub/class_mage.schema.yaml`

- [ ] **Step 1: 角色创建时添加 Skills 组件**

在 `select.js` 的 `action.confirm_character` handler 中，在 `entity.addTag("persistent")` 之前添加：

```javascript
// 添加 Skills 组件 — 所有角色默认拥有普攻/防御/逃跑
var skills = engine.newComponent("Skills");
var skillList = engine.newList();

var atkSkill = engine.newMap();
atkSkill.put("id", "basic_attack");
atkSkill.put("level", 1);
atkSkill.put("cooldown", 0);
skillList.add(atkSkill);

var defSkill = engine.newMap();
defSkill.put("id", "defend");
defSkill.put("level", 1);
defSkill.put("cooldown", 0);
skillList.add(defSkill);

var fleeSkill = engine.newMap();
fleeSkill.put("id", "flee");
fleeSkill.put("level", 1);
fleeSkill.put("cooldown", 0);
skillList.add(fleeSkill);

// 职业额外技能
if (classId === "mage") {
    var fbSkill = engine.newMap();
    fbSkill.put("id", "fireball");
    fbSkill.put("level", 1);
    fbSkill.put("cooldown", 0);
    skillList.add(fbSkill);
}

skills.set("list", skillList);
entity.addComponent(skills);
```

- [ ] **Step 2: Commit**

```bash
git add mods/base-rules/handlers/character/select.js
git commit -m "feat: 角色创建时添加 Skills 组件，默认普攻/防御/逃跑 + 职业技能"
```

---

### Task 5: 技能定义文件 — basic_attack / defend / flee / fireball

**Files:**
- Create: `mods/base-rules/skills/basic_attack.yaml`
- Create: `mods/base-rules/skills/basic_attack.js`
- Create: `mods/base-rules/skills/defend.yaml`
- Create: `mods/base-rules/skills/defend.js`
- Create: `mods/base-rules/skills/flee.yaml`
- Create: `mods/base-rules/skills/flee.js`
- Create: `mods/base-rules/skills/fireball.yaml`
- Create: `mods/base-rules/skills/fireball.js`

- [ ] **Step 1: basic_attack.yaml**

```yaml
id: basic_attack
name: "攻击"
category: skill
silenceable: true
targeting:
  steps:
    - prompt: "选择目标"
      filter: enemy
      count: 1
animation:
  - type: lunge
    target: actor
    side: actor_side
  - type: impact
    target: target
  - type: shake
    target: target
    intensity: normal
  - type: damage_number
    target: target
    color: damage
```

- [ ] **Step 2: basic_attack.js**

```javascript
// mods/base-rules/skills/basic_attack.js
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "basic_attack") return;

    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (targetId === null || targetId === undefined) return;

    var target = store.get(targetId);
    if (target === null || !target.hasComponent("Health") || target.getComponent("Health").getInt("hp") <= 0) return;

    var calcEvent = engine.newEvent("combat.damage_calc");
    calcEvent.set("attackerId", actorId);
    calcEvent.set("targetId", targetId);
    calcEvent.set("combatId", combatId);
    engine.fire("combat.damage_calc", calcEvent);

    var damage = calcEvent.get("damage");
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    engine.fire("combat.damage_dealt", dealEvent);
});
```

- [ ] **Step 3: defend.yaml**

```yaml
id: defend
name: "防御"
category: skill
silenceable: true
targeting:
  steps: []
animation:
  - type: buff_up
    target: actor
    color: "#66bb6a"
    intensity: normal
```

- [ ] **Step 4: defend.js**

```javascript
// mods/base-rules/skills/defend.js
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "defend") return;

    var actorId = event.get("actorId");
    buffs.applyBuff(actorId, "defending", engine.newMap());
});
```

- [ ] **Step 5: flee.yaml**

```yaml
id: flee
name: "逃跑"
category: action
silenceable: false
targeting:
  steps: []
```

- [ ] **Step 6: flee.js**

```javascript
// mods/base-rules/skills/flee.js
// 逃跑处理仍在 start_combat.js 的 FLEE 分支，这里不重复处理
// 保留空文件作为技能定义的 JS 占位
```

- [ ] **Step 7: fireball.yaml**

```yaml
id: fireball
name: "火球术"
category: skill
silenceable: true
targeting:
  steps:
    - prompt: "选择目标"
      filter: enemy
      count: 1
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

- [ ] **Step 8: fireball.js**

```javascript
// mods/base-rules/skills/fireball.js
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "fireball") return;

    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (targetId === null || targetId === undefined) return;

    var caster = store.get(actorId);
    var target = store.get(targetId);
    if (target === null || !target.hasComponent("Health") || target.getComponent("Health").getInt("hp") <= 0) return;

    // 消耗 MP
    var mana = caster.getComponent("Mana");
    if (mana !== null) {
        mana.set("mp", Math.max(0, mana.getInt("mp") - 10));
    }

    // 伤害计算
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 10; // 火球基础额外伤害

    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    engine.fire("combat.damage_dealt", dealEvent);

    // 施加灼烧 buff
    var burnData = engine.newMap();
    burnData.put("damage", 3);
    burnData.put("remaining", 2);
    burnData.put("stacking", "refresh");
    burnData.put("source", actorId);
    buffs.applyBuff(targetId, "burning", burnData);
});
```

- [ ] **Step 9: 创建 burning buff**

创建 `mods/base-rules/buffs/burning.yaml`：
```yaml
id: burning
name: "灼烧"
stacking: refresh
indicator:
  corner: bottom-right
  color: "#ff4400"
```

创建 `mods/base-rules/buffs/burning.js`：
```javascript
// mods/base-rules/buffs/burning.js
engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var burning = entity.getComponent("Buff_burning");
        if (burning === null) continue;

        var dmg = burning.has("damage") ? burning.getInt("damage") : 3;
        var health = entity.getComponent("Health");
        if (health !== null) {
            health.set("hp", Math.max(0, health.getInt("hp") - dmg));
        }

        var remaining = burning.has("remaining") ? burning.getInt("remaining") : 2;
        remaining--;
        if (remaining <= 0) {
            buffs.removeBuff(entity.getId(), "burning");
        } else {
            burning.set("remaining", remaining);
        }
    }
});
```

- [ ] **Step 10: Commit**

```bash
git add mods/base-rules/skills/ mods/base-rules/buffs/burning.yaml mods/base-rules/buffs/burning.js
git commit -m "feat: 技能定义 — basic_attack/defend/flee/fireball + burning buff"
```

---

### Task 6: 战斗指令改造 — 从 Skills 组件动态生成

**Files:**
- Modify: `mods/base-rules/handlers/ui/actions.js`
- Modify: `mods/base-rules/handlers/combat/start_combat.js`
- Modify: `mods/base-rules/handlers/combat/death_check.js`

- [ ] **Step 1: 修改 actions.js 的战斗指令生成**

将 `if (inCombat) { ... }` 块中硬编码的 ATTACK/DEFEND/FLEE 替换为从 Skills 组件动态生成：

```javascript
if (inCombat) {
    var skills = entity.hasComponent("Skills") ? entity.getComponent("Skills").get("list") : null;
    if (skills !== null) {
        for (var j = 0; j < skills.size(); j++) {
            var sk = skills.get(j);
            var skillId = sk.get("id");
            var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
            if (skillDef === null) continue;

            var name = skillDef.get("name");
            var category = skillDef.get("category");
            var targeting = skillDef.get("targeting");
            var steps = targeting !== null ? targeting.get("steps") : null;
            var needsTarget = steps !== null && steps.size() > 0;

            var params = engine.newMap();
            params.put("command", skillId);
            var style = needsTarget ? "requires_target" : "instant";
            actions.add(engine.newActionOptionStyled("combat_command", name, params, null, style));
        }
    }
}
```

- [ ] **Step 2: 修改 start_combat.js 的 combat_command handler**

将 FLEE 检测从 `command === "FLEE"` 改为 `command === "flee"`（小写，匹配技能 ID）：

```javascript
if (command === "flee") {
    endCombat(player, combatId, "FLEE");
    return;
}
```

将 `playerCmd.put("type", command);` 保持不变（command 现在就是技能 ID 如 "basic_attack"）。

- [ ] **Step 3: 从 death_check.js 移除旧的 ATTACK/DEFEND 处理**

删除 `death_check.js` 中 `engine.on("combat.unit_action", 100, ...)` handler 的整个函数体——ATTACK 和 DEFEND 的效果现在由各自的技能 JS 文件处理。

将整个 `combat.unit_action` handler 替换为空（只保留死亡检测和 check_end 相关的 handler）：

```javascript
// death_check.js — 只保留 damage_dealt 死亡检测和 check_end
// ATTACK/DEFEND 逻辑已迁移到 skills/basic_attack.js 和 skills/defend.js

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

engine.on("combat.check_end", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);

    var playersAlive = false;
    var enemiesAlive = false;

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.getComponent("Health").getInt("hp") > 0) {
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
```

- [ ] **Step 4: 修改 combat_flow.js 移除 Defending 清理**

删除 combat_flow.js 中：
```javascript
// Clear defending status from previous round
var combatants = store.getByTagAsList("combat:" + combatId);
for (var i = 0; i < combatants.size(); i++) {
    combatants.get(i).removeComponent("Defending");
}
```

这段逻辑已经在 `buffs/defending.js` 中处理了。

- [ ] **Step 5: 验证编译并测试**

Run: `cd backend && mvn compile -q`
启动 `./start.sh`，创建角色进入战斗，确认攻击/防御/逃跑仍然正常工作。

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/handlers/ui/actions.js mods/base-rules/handlers/combat/start_combat.js mods/base-rules/handlers/combat/death_check.js mods/base-rules/handlers/combat/combat_flow.js
git commit -m "feat: 战斗指令从 Skills 组件动态生成，移除硬编码 ATTACK/DEFEND/FLEE"
```

---

### Task 7: 前端支持动态技能列表（无多步选择，保持现有交互）

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`

- [ ] **Step 1: 确认前端无需改动**

当前 BattleGrid 已经根据后端返回的 `commands` 数组渲染指令列表，`style: "requires_target"` 的会进入目标选择模式。后端改为从 Skills 动态生成后，前端无需修改——它只看 action 的 type/label/style/params。

验证：启动应用，确认战斗界面显示正确的技能名称（"攻击"/"防御"/"逃跑"，法师额外有"火球术"）。

- [ ] **Step 2: 如果法师的火球术显示正确，Commit（无文件变更，验证通过）**

```bash
git commit --allow-empty -m "verify: 前端动态技能列表验证通过，无需改动"
```

---

### Task 8: 火球术 canUse 条件（MP 不足时置灰）

**Files:**
- Modify: `mods/base-rules/handlers/ui/actions.js`

- [ ] **Step 1: 在 actions 生成时检查技能可用性**

在 actions.js 的战斗指令循环中，添加 canUse 检查。对于火球术需要 MP >= 10：

修改技能循环，在 `actions.add(...)` 之前添加条件检查：

```javascript
if (inCombat) {
    var skills = entity.hasComponent("Skills") ? entity.getComponent("Skills").get("list") : null;
    if (skills !== null) {
        for (var j = 0; j < skills.size(); j++) {
            var sk = skills.get(j);
            var skillId = sk.get("id");
            var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
            if (skillDef === null) continue;

            var name = skillDef.get("name");
            var targeting = skillDef.get("targeting");
            var steps = targeting !== null ? targeting.get("steps") : null;
            var needsTarget = steps !== null && steps.size() > 0;

            // canUse 检查：触发事件让技能 handler 判断
            var canUseEvent = engine.newEvent("skill.can_use");
            canUseEvent.set("entityId", entityId);
            canUseEvent.set("skillId", skillId);
            canUseEvent.set("usable", true);
            engine.fire("skill.can_use", canUseEvent);

            var usable = canUseEvent.get("usable");

            var params = engine.newMap();
            params.put("command", skillId);
            var style = needsTarget ? "requires_target" : "instant";
            if (!usable) {
                actions.add(engine.newActionOptionStyled("combat_command", name, params, "text", "disabled"));
            } else {
                actions.add(engine.newActionOptionStyled("combat_command", name, params, null, style));
            }
        }
    }
}
```

- [ ] **Step 2: 在 fireball.js 中添加 canUse handler**

在 `mods/base-rules/skills/fireball.js` 的顶部添加：

```javascript
// 条件检查：MP >= 10
engine.on("skill.can_use", 100, function(event) {
    if (event.get("skillId") !== "fireball") return;
    var entity = store.get(event.get("entityId"));
    if (entity === null) return;
    var mana = entity.getComponent("Mana");
    if (mana === null || mana.getInt("mp") < 10) {
        event.set("usable", false);
    }
});
```

- [ ] **Step 3: 前端处理 disabled 状态**

在 `frontend/src/components/combat/BattleGrid.vue` 中，`selectCommand` 函数开头添加检查：

```javascript
function selectCommand(action) {
  if (!currentActor.value) return
  if (action.style === 'disabled') return
  // ... 现有逻辑
}
```

在 BattleGrid 的 CSS 中添加：

```css
.cmd-actions :deep(.action-link.disabled) {
  opacity: 0.4;
  pointer-events: none;
}
```

- [ ] **Step 4: Commit**

```bash
git add mods/base-rules/handlers/ui/actions.js mods/base-rules/skills/fireball.js frontend/src/components/combat/BattleGrid.vue
git commit -m "feat: 技能 canUse 条件 — MP 不足时火球术置灰不可用"
```

---

### Task 9: combat_events.js 适配技能 ID

**Files:**
- Modify: `mods/base-rules/handlers/combat/combat_events.js`

- [ ] **Step 1: 修改 defend 事件记录检查**

在 combat_events.js 中，记录 DEFEND 事件的 handler 当前检查 `cmdTypeStr === "DEFEND"`，改为：

```javascript
if (cmdTypeStr === "defend") {
```

- [ ] **Step 2: Commit**

```bash
git add mods/base-rules/handlers/combat/combat_events.js
git commit -m "fix: combat_events 适配小写技能 ID"
```

---

### Task 10: 最终集成验证

- [ ] **Step 1: 启动完整应用**

Run: `./start.sh`

- [ ] **Step 2: 验证流程**

1. 创建战士角色 → 确认有 攻击/防御/逃跑 三个技能
2. 创建法师角色 → 确认额外有 火球术
3. 进入战斗 → 攻击正常造成伤害
4. 防御 → 下回合被攻击减伤
5. 火球术 → 造成伤害 + 目标获得灼烧（右下角标橙色）
6. 灼烧每回合扣血，2 回合后消失
7. MP 耗尽后火球术置灰
8. 逃跑 → 显示"逃跑成功"退出战斗

- [ ] **Step 3: Commit（如有修复）**

```bash
git commit -m "fix: 集成测试修复"
```
