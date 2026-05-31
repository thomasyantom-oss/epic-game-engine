# Feature #2 · 属性表扩展 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把一维 `attack` 标量换成「5 基础属性 → 5 二级属性」的两层属性表,技能经 `computeDamage` 的 `scaling` 吃二级属性;玩家与敌人同走一套派生管道。

**Architecture:** 纯 mod 改动(JS handler + YAML)——组件是通用 `engine.newComponent`,无需 Java 类。新增 `PrimaryStats`(力/敏/智/体/意 + weaponAttr)与 `DerivedStats`(物理/法术/精神强度)两个组件;一个新 `derived` 类型的 ModifierChain modifier 在所有属性来源之后跑,把基础属性换算成二级属性 + maxHp + 先攻 + 占位 attack。技能 spec 声明 `scaling:{二级属性:系数}`,`computeDamage` 读 `DerivedStats` 累加。

**Tech Stack:** Java 21 + Spring Boot(测试 JUnit5/AssertJ,`mvn -f backend test`)· GraalJS mod handlers · SnakeYAML 数据 · 现有 ModifierChain / SkillFidelity golden 网。

---

## ⚠️ 实现决策(执行前请 review —— 对 spec 的两处数值微调)

1. **maxHp 公式 `50 + 体质×10` → `体质×10`(base 0)。** 原公式让任何敌人下限 50 HP,与「20 血哥布林也由属性构成」冲突。改为纯 `体质×10`,玩家 `体质` 相应翻倍(战士体质 15→150,法师 10→100…)。语义不变(体质=血),只挪了系数。
2. **占位 attack 归一化常数 `占位武器基础 = 10`。** `普攻/物理 attack = ⌈10 ×(1 + 物理强度%)⌉`。这是 #2 没有武器时的占位,会把全部物理攻击压到 ~10–15 区间(物理强度 0 → attack 10 下限)。**Ch2 真武器到位后替换**。敌人 encounter 的属性值是首版近似,combat 能跑、不追求精确平衡。
3. **三强度 scaling 系数**:法术/精神技能本轮统一用 `0.5`(火球 = 8 + ⌈法强×0.5⌉)。
4. 其余全部遵循 spec `docs/superpowers/specs/2026-05-31-feature2-attribute-table-design.md`。

**派生公式(定稿,全程 `Math.ceil`):**
```
weaponMult(attr) = (attr === "力量") ? 2 : 1
物理强度 = PrimaryStats[weaponAttr] × weaponMult(weaponAttr)
法术强度 = 智力
精神强度 = 意志
maxHp    = 体质 × 10
先攻(speed) = 敏捷
attack(占位最终武器伤害) = ⌈10 ×(1 + 物理强度/100)⌉
defense  = 不派生,保留 base 3(Ch2/#3 接手)
```

**5 职业 L1 属性(base PrimaryStats 全 3,class modifier 加非负 delta):**
| 职业 | weaponAttr | 力/敏/智/体/意 | class delta(力/敏/智/体/意) | L1 派生 |
|---|---|---|---|---|
| 战士 | 力量 | 14/5/3/15/3 | +11/+2/0/+12/0 | 物理28·attack13·HP150·先攻5 |
| 法师 | 智力 | 3/5/14/10/6 | 0/+2/+11/+7/+3 | 法强14·物理14·attack12·HP100·精神6 |
| 盗贼 | 敏捷 | 5/14/3/11/3 | +2/+11/0/+8/0 | 物理14·attack12·HP110·先攻14 |
| 德鲁伊 | 意志 | 3/5/3/12/13 | 0/+2/0/+9/+10 | 精神13·物理13·attack12·HP120 |
| 护卫 | 体质 | 5/3/3/20/6 | +2/0/0/+17/+3 | 物理20·attack12·HP200·精神6 |

**每级成长模板(+5 属性点/级):** 战士 3力2体 · 法师 3智1敏1体 · 盗贼 3敏1力1体 · 德鲁伊 3意1体1敏 · 护卫 3体1力1意。

---

## 文件结构

| 文件 | 创建/改 | 职责 |
|---|---|---|
| `mods/base-rules/modifier_types.yaml` | 改 | 加 `derived` 类型(base_priority 300,排最后跑) |
| `mods/base-rules/handlers/character/derived_stats.js` | 创建 | `weaponMult` + `registerDerivedModifier(entityId)` 共享派生逻辑 |
| `mods/base-rules/handlers/skill/00_skill_lib.js` | 改 | `computeDamage` 加 `scaling` 项 |
| `mods/base-rules/skills/fireball.yaml` / `ice_beam.yaml` | 改 | 迁到 `scaling:{法术强度:0.5}` |
| `mods/base-rules/schemas/main/character.schema.yaml` | 改 | base_components 加 PrimaryStats / DerivedStats |
| `mods/base-rules/schemas/sub/class_warrior.schema.yaml` / `class_mage...` | 改 | 改为 PrimaryStats delta + weapon_attr + growth |
| `mods/base-rules/schemas/sub/class_rogue/druid/guardian.schema.yaml` | 创建 | 新 3 职业 |
| `mods/base-rules/handlers/character/select.js` | 改 | confirm_character 注册派生 modifier + weaponAttr + hp 填满 |
| `mods/base-rules/handlers/character/recalculate_hooks.js` | 改 | entity.loaded 注册派生 modifier;setBaseSelective 含新组件 |
| `mods/base-rules/handlers/character/leveling.js` | 改 | 等级成长改往 PrimaryStats 加点 |
| `mods/base-rules/handlers/combat/start_combat.js` | 改 | 敌人 spawn 建 PrimaryStats + 注册派生 + recalc |
| `mods/base-rules/entities/encounters/*.yaml` | 改 | 敌人改写 PrimaryStats |
| `frontend/src/components/CharacterSelect.vue`(及传 maxSlots 处) | 改 | maxSlots 5→9 |
| `backend/src/test/java/com/epic/engine/combat/DerivedStatsTest.java` | 创建 | 派生公式 + 衍生自衍生顺序测试 |
| `backend/.../SkillLibTest.java` | 改 | computeDamage scaling 测试 |
| `backend/.../SkillFidelityHarness.java` | 改 | scene 加 DerivedStats(供 fireball/ice_beam scaling) |
| `golden/fireball.json` / `ice_beam.json` | 改 | 重生成 |

---

## Task 1: `derived` modifier 类型 + 派生公式(含衍生自衍生顺序验证)

**Files:**
- Modify: `mods/base-rules/modifier_types.yaml`
- Create: `mods/base-rules/handlers/character/derived_stats.js`
- Test: `backend/src/test/java/com/epic/engine/combat/DerivedStatsTest.java`

- [ ] **Step 1: 写失败测试 `DerivedStatsTest`**

```java
package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class DerivedStatsTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;

    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
    }
    @AfterEach void tearDown() { rt.close(); }

    Entity warrior() {
        Entity e = new Entity("w1");
        Component p = new Component("PrimaryStats");
        p.set("力量", 14); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 15); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 3); c.set("speed", 0); e.addComponent(c);
        store.add(e);
        return e;
    }

    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    @Test void derives_warrior_stats() {
        warrior();
        rt.getEngineApi().setBase("w1");                 // snapshot base
        js("registerDerivedModifier('w1');");            // registers + recalculates
        Entity e = store.get("w1");
        Component d = e.getComponent("DerivedStats");
        assertThat(d.getInt("物理强度")).isEqualTo(28);   // 力量14 × 2
        assertThat(d.getInt("法术强度")).isEqualTo(3);    // 智力3
        assertThat(d.getInt("精神强度")).isEqualTo(3);    // 意志3
        assertThat(e.getComponent("Health").getInt("maxHp")).isEqualTo(150); // 体质15×10
        assertThat(e.getComponent("CombatStats").getInt("speed")).isEqualTo(5); // 敏捷
        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(13); // ⌈10×1.28⌉
    }

    @Test void derived_reads_post_class_primary_value() {   // 衍生自衍生顺序
        warrior();
        rt.getEngineApi().setBase("w1");
        // 一个 priority 180(class 段)的 modifier 给力量 +6,模拟职业/等级先跑
        js("engine.addModifier('w1', { typeId:'class', id:'fake_class', priority:180," +
           " apply:function(ent){ var p=ent.getComponent('PrimaryStats');" +
           " p.set('力量', p.getInt('力量')+6); } });");
        js("registerDerivedModifier('w1');");            // derived priority 300 → 必须最后跑
        assertThat(store.get("w1").getComponent("DerivedStats").getInt("物理强度"))
            .isEqualTo(40);   // (14+6)×2 — 证明 derived 读到累加后的力量
    }
}
```

> 注:`rt.getEngineApi()` 暴露 `setBase`/`addModifier`/`recalculate`。若 `ScriptRuntime` 未公开该 getter,改用 js() 调 `engine.setBase('w1')`(`engine` 在脚本作用域内已绑定),功能等价。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -f backend test -Dtest=DerivedStatsTest`
Expected: FAIL —— `derived_stats.js` 不存在 / `registerDerivedModifier is not defined`。

- [ ] **Step 3: 加 `derived` modifier 类型**

`mods/base-rules/modifier_types.yaml` 末尾追加:
```yaml
  - id: derived
    label: 派生属性
    stack_rule: exclusive
    base_priority: 300        # 高于 race(200) → ModifierChain 升序应用时最后跑,读到累加后的基础属性
```

- [ ] **Step 4: 写 `derived_stats.js`**

```javascript
// 武器对应属性的倍率:力量 ×2(纯输出补偿),其余 ×1。
function weaponMult(attr) { return attr === "力量" ? 2 : 1; }

var PLACEHOLDER_WEAPON_BASE = 10;   // #2 占位武器基础;Ch2 真武器替换

// 共享:给实体注册「派生」modifier(exclusive,priority 300 → 最后跑)。
// 读 PrimaryStats → 写 DerivedStats(三强度)+ Health.maxHp + CombatStats.speed/attack。
function registerDerivedModifier(entityId) {
    engine.addModifier(entityId, {
        typeId: "derived",
        id: "derived_stats",
        label: "派生属性",
        apply: function(ent) {
            var p = ent.getComponent("PrimaryStats");
            if (p === null) return;
            var d = ent.getComponent("DerivedStats");
            var wAttr = p.has("weaponAttr") ? p.get("weaponAttr") : "力量";
            var phys = Math.ceil(p.getInt(wAttr) * weaponMult(wAttr));
            if (d !== null) {
                d.set("物理强度", phys);
                d.set("法术强度", p.getInt("智力"));
                d.set("精神强度", p.getInt("意志"));
            }
            var h = ent.getComponent("Health");
            if (h !== null) h.set("maxHp", p.getInt("体质") * 10);
            var c = ent.getComponent("CombatStats");
            if (c !== null) {
                c.set("speed", p.getInt("敏捷"));
                c.set("attack", Math.ceil(PLACEHOLDER_WEAPON_BASE * (1 + phys / 100)));
            }
        }
    });
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `mvn -f backend test -Dtest=DerivedStatsTest`
Expected: PASS(2 tests)。若 `derived_reads_post_class_primary_value` 失败,说明 ModifierChain 实际是**降序**应用 → 把 `derived` 的 `base_priority` 改成低于 buff(如 5)再跑。

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/modifier_types.yaml mods/base-rules/handlers/character/derived_stats.js backend/src/test/java/com/epic/engine/combat/DerivedStatsTest.java
git commit -m "feat(attr): derived-stats modifier — 基础属性→二级属性派生 + 衍生自衍生顺序"
```

---

## Task 2: `computeDamage` 接 `scaling`(吃三强度)

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js:40-54`
- Test: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`

- [ ] **Step 1: 在 `SkillLibTest` 加失败测试**

在 `SkillLibTest` 的 `mkUnit` 里,给单位补一个 DerivedStats(否则 scaling 读不到)。在 `mkUnit` 的 `store.add(e)` 之前插入:
```java
        Component ds = new Component("DerivedStats");
        ds.set("物理强度", 0); ds.set("法术强度", 14); ds.set("精神强度", 0);
        e.addComponent(ds);
```
新增测试方法:
```java
    @Test
    void computeDamage_scalingSpellPower() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        // add 8 + ⌈法术强度14 × 0.5⌉ = 8 + 7 = 15
        js("var s = {add:8, scaling:{法术强度:0.5}};" +
           "var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), s);" +
           "if (dmg !== 15) throw 'dmg='+dmg;");
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -f backend test -Dtest=SkillLibTest#computeDamage_scalingSpellPower`
Expected: FAIL —— 现在 `computeDamage` 不认 `scaling`,返回 8。

- [ ] **Step 3: 改 `computeDamage`**

把 `00_skill_lib.js` 的 `computeDamage` 主体(line 49–53)替换为:
```javascript
    var statName = dmgSpec.base;
    var base = (statName && caster.hasComponent("CombatStats"))
        ? caster.getComponent("CombatStats").getInt(statName) : 0;
    var total = base + (dmgSpec.add || 0);
    if (dmgSpec.scaling) {                                  // Feature #2:吃三强度
        var ds = caster.getComponent("DerivedStats");
        if (ds !== null) {
            var keys = Object.keys(dmgSpec.scaling);
            for (var i = 0; i < keys.length; i++) {
                total += Math.ceil(ds.getInt(keys[i]) * dmgSpec.scaling[keys[i]]);
            }
        }
    }
    return total;
```
(保留上方 `via_damage_calc` 分支不动;删掉旧的 `// Feature #2 将在此处追加` 注释行。注意 `dmgSpec.base` 现在没有默认 `"attack"` —— 不声明 base 的纯 scaling 技能 base=0。)

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -f backend test -Dtest=SkillLibTest`
Expected: PASS(含旧的 `computeDamage_flatAdd`:`{base:'attack',add:8}` 仍 = 18,因 mkUnit attack 10 未变)。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skill): computeDamage 支持 scaling — 技能吃三强度(向上取整)"
```

---

## Task 3: 火球 / 冰霜射线迁到法术强度 + 重生成 golden

**Files:**
- Modify: `mods/base-rules/skills/fireball.yaml:7-9`, `mods/base-rules/skills/ice_beam.yaml:7-9`
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java:75-97`
- Modify: `backend/src/test/resources/golden/fireball.json`, `ice_beam.json`

- [ ] **Step 1: 改两个技能 spec 的 damage 块**

`fireball.yaml`:
```yaml
damage:
  add: 8
  scaling: { 法术强度: 0.5 }
```
`ice_beam.yaml`:
```yaml
damage:
  add: 8
  scaling: { 法术强度: 0.5 }
```
(均删除 `base: attack`。)

- [ ] **Step 2: 给 fidelity harness 的单位补 DerivedStats**

`SkillFidelityHarness.addUnit`,在 `e.addComponent(s);`(CombatStats)之后插入:
```java
        Component ds = new Component("DerivedStats");
        ds.set("物理强度", 0);
        ds.set("法术强度", atk);   // 复用 atk 参数当法强:mage(10)→火球 8+⌈10×0.5⌉=13
        ds.set("精神强度", 0);
        e.addComponent(ds);
```

- [ ] **Step 3: 跑 fidelity 确认 fireball/ice_beam 失败、其余通过**

Run: `mvn -f backend test -Dtest=SkillFidelityTest`
Expected: `fireball`、`ice_beam` 两个 FAIL(伤害 20/18 → 13);其余 12 个 golden PASS(它们走 `base:attack`,attack 未变)。

- [ ] **Step 4: 重生成两个 golden**

确认 harness 已有重生成开关(看 `SkillFidelityTest` 是否支持 `-Dgolden.regen=true` 或类似;若无,临时把失败 case 的实际输出写入对应 json)。重生成后:
```bash
git diff --stat backend/src/test/resources/golden/   # 应只有 fireball.json / ice_beam.json 变化
```

- [ ] **Step 5: 跑 fidelity 全绿**

Run: `mvn -f backend test -Dtest=SkillFidelityTest`
Expected: PASS(14/14)。

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/skills/fireball.yaml mods/base-rules/skills/ice_beam.yaml backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java backend/src/test/resources/golden/fireball.json backend/src/test/resources/golden/ice_beam.json
git commit -m "feat(skill): 火球/冰霜射线迁到法术强度 scaling + golden 重生成"
```

---

## Task 4: 角色 schema(主 + 5 职业)改为属性驱动

**Files:**
- Modify: `mods/base-rules/schemas/main/character.schema.yaml`
- Modify: `mods/base-rules/schemas/sub/class_warrior.schema.yaml`, `class_mage.schema.yaml`
- Create: `class_rogue.schema.yaml`, `class_druid.schema.yaml`, `class_guardian.schema.yaml`

- [ ] **Step 1: 主 schema 加基础/派生组件**

`character.schema.yaml` 的 `base_components` 改为:
```yaml
base_components:
  Health: { hp: 30, maxHp: 30 }
  Mana: { mp: 50, maxMp: 50 }
  CombatStats: { attack: 10, defense: 3, speed: 3 }
  PrimaryStats: { 力量: 3, 敏捷: 3, 智力: 3, 体质: 3, 意志: 3, weaponAttr: "力量" }
  DerivedStats: { 物理强度: 0, 法术强度: 0, 精神强度: 0 }
  Position: { map: "world_map", x: 4, y: 3 }
```
(Health 初值随意,派生会覆盖 maxHp;weaponAttr 在创建时按职业覆盖。)

- [ ] **Step 2: 改战士 / 法师 schema**

`class_warrior.schema.yaml`:
```yaml
id: warrior
type: sub
category: class
compatible_with: [character]
label: "战士"
description: "近战物理输出,力量主属性"
weapon_attr: "力量"
growth: { 力量: 3, 体质: 2 }
modifiers:
  - { field: "PrimaryStats.力量", value: "+11" }
  - { field: "PrimaryStats.敏捷", value: "+2" }
  - { field: "PrimaryStats.体质", value: "+12" }
```
`class_mage.schema.yaml`:
```yaml
id: mage
type: sub
category: class
compatible_with: [character]
label: "法师"
description: "远程法术输出,智力主属性"
weapon_attr: "智力"
growth: { 智力: 3, 敏捷: 1, 体质: 1 }
modifiers:
  - { field: "PrimaryStats.敏捷", value: "+2" }
  - { field: "PrimaryStats.智力", value: "+11" }
  - { field: "PrimaryStats.体质", value: "+7" }
  - { field: "PrimaryStats.意志", value: "+3" }
```

- [ ] **Step 3: 建 3 个新职业 schema**

`class_rogue.schema.yaml`:
```yaml
id: rogue
type: sub
category: class
compatible_with: [character]
label: "盗贼"
description: "敏捷物理输出,高先攻"
weapon_attr: "敏捷"
growth: { 敏捷: 3, 力量: 1, 体质: 1 }
modifiers:
  - { field: "PrimaryStats.力量", value: "+2" }
  - { field: "PrimaryStats.敏捷", value: "+11" }
  - { field: "PrimaryStats.体质", value: "+8" }
```
`class_druid.schema.yaml`:
```yaml
id: druid
type: sub
category: class
compatible_with: [character]
label: "德鲁伊"
description: "意志施法者,精神强度驱动"
weapon_attr: "意志"
growth: { 意志: 3, 体质: 1, 敏捷: 1 }
modifiers:
  - { field: "PrimaryStats.敏捷", value: "+2" }
  - { field: "PrimaryStats.体质", value: "+9" }
  - { field: "PrimaryStats.意志", value: "+10" }
```
`class_guardian.schema.yaml`:
```yaml
id: guardian
type: sub
category: class
compatible_with: [character]
label: "护卫"
description: "体质坦克,高生命"
weapon_attr: "体质"
growth: { 体质: 3, 力量: 1, 意志: 1 }
modifiers:
  - { field: "PrimaryStats.力量", value: "+2" }
  - { field: "PrimaryStats.体质", value: "+17" }
  - { field: "PrimaryStats.意志", value: "+3" }
```

- [ ] **Step 4: 确认 schema 加载不报错**

Run: `mvn -f backend test -Dtest=SchemaRegistryTest`(若无此测试则 `mvn -f backend test -Dtest=DerivedStatsTest` 跑一遍确保编译/加载链路无碍)。
Expected: PASS / 无 YAML 解析异常。
> `weapon_attr` / `growth` 是 schema 顶层自定义字段;若 `SchemaRegistry` 严格校验未知字段会报错 —— 那样需在读取侧用宽松取值(下一 Task 用 `classSchema.raw().get("weapon_attr")` 风格读取),schema 加载本身不应因多字段失败。执行时先验证这一点。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/schemas/
git commit -m "feat(attr): 角色 schema 属性驱动 — 主 schema 加 PrimaryStats/DerivedStats + 5 职业"
```

---

## Task 5: 角色创建/加载注册派生 modifier

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js:79-178`
- Modify: `mods/base-rules/handlers/character/recalculate_hooks.js:23-80`

- [ ] **Step 1: `recalculate_hooks.js` —— setBaseSelective 含新组件 + entity.loaded 注册派生**

把 `entity.loaded` 里构建 `statCompTypes` 的循环保持(它从 character schema 的 baseComponents 取,已含 PrimaryStats/DerivedStats)。在该 handler 末尾(注册完 class/equipment/level modifier 之后)追加:
```javascript
    if (typeof registerDerivedModifier !== 'undefined' && entity.getComponent("PrimaryStats") !== null) {
        registerDerivedModifier(entity.getId());
    }
```

- [ ] **Step 2: `select.js confirm_character` —— 设 weaponAttr + 注册派生 + 填血**

在 `confirm_character` 里,注册 class modifier(line 151 那段)**之后**、`persistence.save` 之前,插入:
```javascript
    // 按职业设置武器属性(决定物理强度吃哪条基础属性)
    var primary = entity.getComponent("PrimaryStats");
    if (primary !== null && classSchema !== null) {
        var wAttr = classSchema.raw().get("weapon_attr");   // schema 顶层自定义字段
        if (wAttr !== null) primary.set("weaponAttr", wAttr);
    }
    // 注册派生 modifier(→ 自动 recalculate,算出二级属性 + maxHp)
    if (typeof registerDerivedModifier !== 'undefined') {
        registerDerivedModifier(charId);
    }
    // 新建角色满血
    var hh = entity.getComponent("Health");
    if (hh !== null) hh.set("hp", hh.getInt("maxHp"));
```
> 若 `classSchema.raw()` 不存在,用 `schemas.getRaw(classId)` 或在 Task 4 Step 4 确认的读取方式。weaponAttr 读不到时默认保持 base 的 "力量"。

- [ ] **Step 3: 手动冒烟(无自动化,起后端跑一次创建)**

Run: 启动后端 `mvn -f backend spring-boot:run`,另开终端:
```bash
# 经 /api 创建一个战士,然后查 state
curl http://localhost:8080/api/debug/health
```
经前端创建战士 → `GET /api/debug/state/{token}` 确认该角色有 `PrimaryStats{力量:14...}`、`DerivedStats{物理强度:28}`、`Health.maxHp:150`、`hp:150`。
Expected: 数值符合 L1 战士表。

- [ ] **Step 4: Commit**

```bash
git add mods/base-rules/handlers/character/select.js mods/base-rules/handlers/character/recalculate_hooks.js
git commit -m "feat(attr): 角色创建/加载注册派生 modifier + 职业武器属性 + 满血"
```

---

## Task 6: 等级成长改往 PrimaryStats 加点

**Files:**
- Modify: `mods/base-rules/handlers/character/leveling.js:1-58`

- [ ] **Step 1: 改写 `registerLevelGrowthModifier`**

把 `leveling.js` 顶部的 `registerLevelGrowthModifier` 替换为读职业 growth 模板、往 PrimaryStats 加点:
```javascript
function registerLevelGrowthModifier(entityId, level) {
    var capturedLevel = level;
    engine.addModifier(entityId, {
        typeId: "level",
        id: "level_growth",
        label: level + "级成长",
        apply: function(entity) {
            var ch = entity.getComponent("Character");
            var p = entity.getComponent("PrimaryStats");
            if (ch === null || p === null) return;
            var classSchema = schemas.get(ch.getString("classId"));
            if (classSchema === null) return;
            var growth = classSchema.raw().get("growth");
            if (growth === null) return;
            var gained = capturedLevel - 1;   // L1 不加,每升一级加一份模板
            var keys = growth.keySet().iterator();
            while (keys.hasNext()) {
                var stat = keys.next();
                p.set(stat, p.getInt(stat) + growth.get(stat) * gained);
            }
        }
    });
}
```
(level modifier priority 90 < derived 300 → 先加点,派生后跑。`gain_xp` handler 里去掉 `exp.set("pendingPoints", ...+3)` 那行 —— §1.8 不再手动加点;保留 `action.allocate_point` handler 不删,但它已无新点可分。)

- [ ] **Step 2: 验证升级派生(扩 DerivedStatsTest)**

在 `DerivedStatsTest` 加:
```java
    @Test void level_growth_feeds_derived() {
        Entity e = warrior();                       // 力量14
        e.getComponent("Character"); // 需要 Character 组件:在 warrior() 里补 classId
        // —— 见下方说明:此测试需要 leveling.js + 一个带 classId 的 Character + schemas
    }
```
> **简化**:leveling 依赖 `schemas`/`Character`,单元测起来重。改为在 Step 3 的手动冒烟覆盖升级成长,本 Task 不强加单测(派生公式本身已被 Task 1 锁)。删掉上面的占位测试桩。

- [ ] **Step 3: 手动冒烟升级**

后端运行中,对战士发 `action.gain_xp`(经 debug 或打一场胜),升到 2 级 → `GET /api/debug/state` 确认 `力量` = 14+3 = 17、`体质` = 15+2 = 17、`物理强度` = 34、maxHp = 170。
Expected: 升级走属性、二级自动重算。

- [ ] **Step 4: 跑全部测试确保未回归**

Run: `mvn -f backend test`
Expected: 全绿(leveling 改动不被现有测试直接覆盖,但不应破坏编译/加载)。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/character/leveling.js backend/src/test/java/com/epic/engine/combat/DerivedStatsTest.java
git commit -m "feat(attr): 等级成长改为按职业模板自动加基础属性点"
```

---

## Task 7: 敌人 spawn 走属性派生 + encounter 迁移

**Files:**
- Modify: `mods/base-rules/handlers/combat/start_combat.js:44-73`
- Modify: `mods/base-rules/entities/encounters/forest_goblin.yaml` 等 8 个 encounter

- [ ] **Step 1: 改 `start_combat.js` 敌人构建**

把 enemy 循环里构建 CombatStats 的段(line 50–59)替换为:从 enemyDef 读 PrimaryStats,建 DerivedStats,defense 保留直读:
```javascript
        var primary = engine.newComponent("PrimaryStats");
        var attrs = ["力量","敏捷","智力","体质","意志"];
        for (var a = 0; a < attrs.length; a++) {
            primary.set(attrs[a], enemyDef.get(attrs[a]) !== null ? enemyDef.get(attrs[a]) : 0);
        }
        primary.set("weaponAttr", enemyDef.get("weaponAttr") !== null ? enemyDef.get("weaponAttr") : "敏捷");
        enemy.addComponent(primary);

        enemy.addComponent(engine.newComponent("DerivedStats"));

        var health = engine.newComponent("Health");
        health.set("hp", 1); health.set("maxHp", 1);   // 派生覆盖 maxHp
        enemy.addComponent(health);

        var stats = engine.newComponent("CombatStats");
        stats.set("attack", 0);   // 派生覆盖
        stats.set("defense", enemyDef.get("defense") !== null ? enemyDef.get("defense") : 0);
        stats.set("speed", 0);    // 派生覆盖(= 敏捷)
        enemy.addComponent(stats);
```
在该敌人 `store.add(enemy);` 之后追加:
```javascript
        engine.setBase(enemyId);
        if (typeof registerDerivedModifier !== 'undefined') registerDerivedModifier(enemyId);
        var eh = enemy.getComponent("Health");
        eh.set("hp", eh.getInt("maxHp"));   // 满血
```
> `start_combat.js` 需能调到 `registerDerivedModifier`(它在 `derived_stats.js`,与本文件同 mod,引擎全局加载;若作用域不可见,确认 ModuleLoader 把 character/ 与 combat/ 的 handler 加载到同一 GraalJS 上下文 —— 现状是,跨文件函数互相可见,如 `registerEquipmentModifier`)。

- [ ] **Step 2: 迁移 encounter YAML(8 个)**

每个敌人从 `hp/attack/defense/speed` → `PrimaryStats + defense`。换算:`体质 = hp/10`,`敏捷 = 原 attack`(weaponAttr 默认敏捷 ×1 → attack ≈ ⌈10×(1+敏捷/100)⌉,见下)。**注意占位 attack 公式让攻击落在 ~10–15**,首版近似即可。

`forest_goblin.yaml`:
```yaml
id: forest_goblin
name: "森林哥布林遭遇战"
enemies:
  - id: goblin1
    name: "哥布林"
    row: FRONT
    slot: 0
    defense: 2
    力量: 0
    敏捷: 5
    智力: 0
    体质: 2          # → hp 20
    意志: 0
    weaponAttr: "敏捷"
  - id: goblin2
    name: "哥布林弓手"
    row: BACK
    slot: 0
    defense: 1
    力量: 0
    敏捷: 7
    智力: 0
    体质: 2          # → hp 20
    意志: 0
    weaponAttr: "敏捷"
```
对其余 7 个 encounter(`desert_scorpion` / `lake_slime` / `mountain_bandit` / `training_dummy` / `training_dummy_3` / `training_dummy_9` / `training_dummy_cross`)做同样迁移:
- `体质 = ⌈原 hp / 10⌉`,`敏捷 = 原 attack`,`defense = 原 defense`,其余基础属性 0,`weaponAttr: "敏捷"`。
- 训练假人(attack 0)→ `敏捷: 0`、`体质 = ⌈hp/10⌉`、`weaponAttr: "敏捷"`,攻击占位会变成 `⌈10×1⌉=10`,但假人不行动(AI 不攻击)故无影响;若希望假人真 0 攻击,给它 `攻击占位` 不触发即可(假人本就不出手)。

- [ ] **Step 3: 现有战斗集成测试回归**

Run: `mvn -f backend test -Dtest=NewCombatIntegrationTest,SkillCombatIntegrationTest,CombatBugfixTest`
Expected: PASS。这些测试若自建实体(非走 start_combat 真 spawn)则不受影响;若走真 encounter 需确认敌人现在有 DerivedStats、攻击非 0。**若测试里硬编码了旧 enemy 数值断言,按新占位值更新断言。**

- [ ] **Step 4: 手动冒烟一场战斗**

后端 + 前端起,打森林哥布林:确认哥布林有血(20)、会攻击(伤害 ~10-defense),战斗可正常结束。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/combat/start_combat.js mods/base-rules/entities/encounters/
git commit -m "feat(attr): 敌人统一走属性派生 — encounter 改写 PrimaryStats + spawn 注册派生"
```

---

## Task 8: 存档扩展 5 → 9

**Files:**
- Modify: `frontend/src/components/CharacterSelect.vue:60`(及传 `maxSlots` 的父组件)

- [ ] **Step 1: 找 maxSlots 传入点**

Run: `grep -rn "maxSlots" frontend/src`
找到 `CharacterSelect` 被使用处传的 `:max-slots` 值(或默认 5)。

- [ ] **Step 2: 改默认为 9**

`CharacterSelect.vue:60` 的兜底默认 `props.maxSlots || 5` → `props.maxSlots || 9`;若父组件显式传了数字,把那个数字改成 9。

- [ ] **Step 3: 手动验证**

前端角色页:空槽位显示 9 格(现有角色 + 空位 = 9)。
Expected: 最多 9 个存档位。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/CharacterSelect.vue
git commit -m "feat(ui): 角色存档位 5 → 9"
```

---

## Task 9: 全量回归 + 清理

**Files:** 无新增,验证为主。

- [ ] **Step 1: 后端全测试**

Run: `mvn -f backend test`
Expected: 全绿(DerivedStatsTest / SkillLibTest / SkillFidelityTest 14/14 / 现有战斗集成 / CombatBugfixTest)。

- [ ] **Step 2: 检查 §1.9 refinement 注释落实**

确认 `00_skill_lib.js computeDamage` 顶部有一行注释说明「scaling 吃三强度(DerivedStats),非裸基础属性 —— §1.9 演进」。无则补。

- [ ] **Step 3: 手动全链路冒烟**

创建每个职业各一个(验证 5 职业起始属性表)→ 各打一场 → 升一级看属性成长 → 确认存档 9 位。

- [ ] **Step 4: 更新 `prompt.md` 给下一轮(Feature #3)**

按记忆约定,把 `prompt.md` 增量更新为 Feature #3「伤害类型/抗性」的开局 prompt:点名读母文档 §1.9(伤害类型层)+ 本 spec 的「延后」表(精神免控、减伤结算、最终武器伤害真公式都在 #3/Ch2)+ Feature #2 留的接缝(computeDamage 三强度、占位 attack 待 Ch2 武器替换)。

- [ ] **Step 5: Commit**

```bash
git add prompt.md
git commit -m "docs: prompt.md 更新至 Feature #3 伤害类型/抗性"
```

---

## Self-Review 覆盖核对(spec → task)

- §2 属性模型 / §2.3 派生系数 → Task 1(公式)+ 顶部决策表。
- §3.1 PrimaryStats / DerivedStats 组件 → Task 4 Step 1 + Task 1 测试。
- §3.2 derived modifier priority(衍生自衍生)→ Task 1 Step 3 + `derived_reads_post_class_primary_value` 测试。
- §4 五职业 → Task 4。
- §5 成长(自动加属性,手动搁置)→ Task 6。
- §6 computeDamage scaling + 火球迁移 → Task 2 + Task 3。
- §6.1 敌人同轴派生 → Task 7。
- §6.2 数值量级占位归一化 → 顶部决策 #2 + Task 7 换算。
- §7 存档 9 → Task 8。
- §8 延后项 → 不实现,Task 9 Step 4 写进 prompt 交棒 #3。
- §9 测试网 → Task 1/2/3 单测 + golden + Task 9 全回归。

**已知执行期需现场确认的点**(非 placeholder,是依赖实测的分支):
1. ModifierChain 升/降序(Task 1 Step 5)——测试会立刻暴露,改一个数字。
2. `classSchema.raw()` / 读 schema 顶层自定义字段的 API 名(Task 4 Step 4 验证后,Task 5/6 沿用)。
3. 跨 handler 文件函数可见性(`registerDerivedModifier` 在 combat/ 里可调)——现状跨文件可见(`registerEquipmentModifier` 先例),Task 7 Step 1 复核。
