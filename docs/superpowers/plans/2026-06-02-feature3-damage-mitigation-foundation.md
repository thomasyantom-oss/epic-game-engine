# Feature #3 切片一:减伤地基 / 空跑框架 — Implementation Plan

> **For agentic workers (Codex):** 按 task 顺序实现,每个 task 走 TDD。完成后请求 Senior SDE(Claude)review;**仅 Claude APPROVE 方可 merge**。步骤用 `- [ ]`。
> Spec:`docs/superpowers/specs/2026-06-01-feature3-damage-mitigation-foundation-design.md`。脑暴记录:`docs/feature3-damage-types-running-notes.md`。

**Goal:** 把"减伤"建成唯一收口 `Skill.mitigate`,所有伤害(普攻/技能/DoT)经过它;新防御轴(三抗/元素)空跑(值全 0、行为≈不变),用 golden 兜底安全落地。

**Architecture:** 出伤(`computeDamage`,raw)与减伤(`Skill.mitigate`,守方)彻底分离。投放方式分流:**普攻→护甲(flat,=现行)**;**技能→三抗% + 元素乘区**(空跑=不减)。`basic_attack` 从 `via_damage_calc` 改成走统一收口,数值逐位不变。

**Tech Stack:** GraalJS mod handlers(`mods/base-rules`),Java 引擎(`backend`,JUnit5 + AssertJ),YAML 数据,SkillFidelity golden 测试。

**全局纪律:** 每 task 后 `cd backend && mvn test` 全绿;凡碰 golden 的 task,验收 = **golden 不变**(空跑),若变必须逐项可解释并在 commit 注明,**不得无脑重生成**。

---

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `mods/base-rules/schemas/main/character.schema.yaml` | 加 `Resistances` 到 base_components | Modify |
| `mods/base-rules/handlers/skill/00_skill_lib.js` | 新增 `Skill.mitigate`;`computeDamage` 不再负责减伤 | Modify |
| `mods/base-rules/handlers/skill/01_effects.js` | `damage_only`/`damage_with_debuff` 在 applyDamage 前调 mitigate | Modify |
| `mods/base-rules/skills/basic_attack.yaml` | 改 `damage: {base: attack}` + `delivery: 普攻`,删 `via_damage_calc` | Modify |
| `mods/base-rules/skills/{cleave,cross_blast,piercing_ray}.js` | 接入 mitigate | Modify |
| `mods/base-rules/skills/{fireball,ice_beam,light_field,...}.yaml` | 补 `damage.type` | Modify |
| `mods/base-rules/buffs/{burning,poison}.js` | DoT tick 过 mitigate | Modify |
| `mods/base-rules/handlers/combat/start_combat.js` | 敌人 spawn 加 Resistances(默认 0,可读 encounter `resistances:`) | Modify |
| `mods/base-rules/handlers/combat/damage_calc.js` | **本轮保留**(basic_attack 不再触发它 → 变 inert;多处测试 harness 仍 load 它,删除会断 setUp)。彻底删除 → backlog | 保留 |
| `backend/src/test/java/com/epic/engine/combat/MitigationTest.java` | mitigate 单测 | Create |
| `backend/.../content/ContentAuthoringValidationTest.java` | 扩展校验 | Modify |

---

## Task 1: `Resistances` 组件(玩家 + 敌人,空跑默认 0,只来自装备/encounter)

**Files:**
- Modify: `mods/base-rules/schemas/main/character.schema.yaml`(玩家)
- Modify: `mods/base-rules/handlers/combat/start_combat.js`(敌人 spawn 加 Resistances)
- Test: `backend/src/test/java/com/epic/engine/combat/MitigationTest.java`(本 task 建文件,后续 task 续用)

- [ ] **Step 1: 写失败测试 —— Resistances 进 base、装备能加、derived 不写**

新建 `MitigationTest.java`,harness 仿照 `ReloadInflationBugTest`(SchemaRegistry + ScriptRuntime + entity.loaded)。先写:

```java
@Test
void resistances_defaultZero_equipmentAdds_derivedNeverWrites() {
    // 复用 ReloadInflationBugTest 的 setUp 风格:typeReg/chainService/rt,
    // 绑 schemas+persistence,执行 derived_stats.js / recalculate_hooks.js / equip.js,
    // world.init 载入 items(本 task 先加一件含 Resistances 的测试装备到 items.yaml 或就地造 ItemStats)。
    Entity w = warriorWithResistGear("w");           // 装一件 "Resistances.法术": 20 的饰品
    js("var ev=engine.newEvent('entity.loaded'); ev.set('entity', store.get('w')); engine.fire('entity.loaded', ev);");
    Component res = store.get("w").getComponent("Resistances");
    assertThat(res).as("Resistances component exists").isNotNull();
    assertThat(res.getInt("物理")).isEqualTo(0);
    assertThat(res.getInt("精神")).isEqualTo(0);
    assertThat(res.getInt("法术")).as("equipment adds 法抗").isEqualTo(20);
    // 再 load 一次(重启)→ 不应翻倍(沿用 base 复位 + addModifier 幂等)
    js("var ev=engine.newEvent('entity.loaded'); ev.set('entity', store.get('w')); engine.fire('entity.loaded', ev);");
    assertThat(store.get("w").getComponent("Resistances").getInt("法术")).as("no inflation on reload").isEqualTo(20);
}
```

- [ ] **Step 2: 跑测试确认失败**

`cd backend && mvn test -Dtest=MitigationTest#resistances_defaultZero_equipmentAdds_derivedNeverWrites`
Expected: FAIL(Resistances 组件不存在 / 为 null)。

- [ ] **Step 3: schema 加 Resistances**

`character.schema.yaml` 的 `base_components` 末尾加一行:
```yaml
  Resistances: { 物理: 0, 法术: 0, 精神: 0 }
```
(`registerEquipmentModifier` 已能解析 `"Resistances.法术": 20` 全限定 key,因为组件存在且 `has("法术")`;`derived_stats.js` 不要加任何 Resistances 写入——保持不写。`entity.loaded` 的 base 复位循环会自动把它复位成 schema 默认 0,装备 modifier 再叠。)

**敌人 spawn 也要给 Resistances**(否则敌我不同轴、技能减伤测不了)。`start_combat.js` 的 `combat.start_encounter` 里组装敌人时加:
```javascript
var resComp = engine.newComponent("Resistances");
var rdef = enemyDef.get("resistances");           // encounter YAML 可选,缺省全 0
var rkeys = ["物理","法术","精神"];
for (var rk = 0; rk < rkeys.length; rk++) {
    resComp.set(rkeys[rk], (rdef !== null && rdef.get(rkeys[rk]) !== null) ? rdef.get(rkeys[rk]) : 0);
}
enemy.addComponent(resComp);
```
> 敌人不进 character schema/base 复位流程(它们 `engine.setBase(enemyId)` 自己快照),所以 Resistances 直接装在 spawn 上即可,无需 derived。
> Step 1 的测试相应补一条:encounter 配 `resistances: {法术: 50}` 的敌人 spawn 后 `Resistances.法术 == 50`,未配的敌人 == 0。

- [ ] **Step 4: 跑测试确认通过**

`mvn test -Dtest=MitigationTest#resistances_defaultZero_equipmentAdds_derivedNeverWrites` → PASS。
再 `mvn test` 全量确认无回归(尤其 `CharacterStatsTest`/`ReloadInflationBugTest`)。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/schemas/main/character.schema.yaml backend/src/test/java/com/epic/engine/combat/MitigationTest.java
git commit -m "feat(combat): 加 Resistances 组件(物/法/精,默认0,只来自装备)"
```

---

## Task 2: `Skill.mitigate` 纯函数 + 单测

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Test: `MitigationTest.java`

- [ ] **Step 1: 写失败测试(覆盖各分支)**

在 `MitigationTest` 加(harness 只需 `00_skill_lib.js`;造 target 实体带 CombatStats.defense + Resistances + 可选 Buff_defending):

```java
@Test
void mitigate_branches() {
    mkTarget("t", /*defense*/5, /*物*/0, /*法*/0, /*精*/0, /*defending*/false);
    // 普攻:flat 护甲 = max(1, raw-defense)
    assertThat(mit("t", 20, "{delivery:'普攻'}")).isEqualTo(15);
    assertThat(mit("t", 3,  "{delivery:'普攻'}")).isEqualTo(1);   // 下限
    // 技能:三抗 0 → 不减
    assertThat(mit("t", 20, "{delivery:'技能', type:'法术'}")).isEqualTo(20);

    mkTarget("r", 5, 0, 50, 0, false);                          // 法抗 50%
    assertThat(mit("r", 20, "{delivery:'技能', type:'法术'}")).isEqualTo(10);
    assertThat(mit("r", 20, "{delivery:'技能', type:'物理'}")).isEqualTo(20); // 法抗不挡物理
    assertThat(mit("r", 20, "{delivery:'普攻'}")).isEqualTo(15);             // 护甲挡普攻,与法抗无关

    mkTarget("n", 0, -100, 0, 0, false);                         // 物抗 -100 = 易伤
    assertThat(mit("n", 10, "{delivery:'技能', type:'物理'}")).isEqualTo(20);

    mkTarget("d", 5, 0, 0, 0, true);                             // 防御姿态
    assertThat(mit("d", 20, "{delivery:'普攻'}")).isEqualTo(7);   // max(1, floor((20-5)*0.5))
}
// 辅助:mit() 调 Skill.mitigate 并把结果写到 probe 组件读回
private int mit(String tid, int raw, String optsJs) {
    js("store.get('probe').getComponent('P').set('v', Skill.mitigate(store.get('"+tid+"'), "+raw+", "+optsJs+"));");
    return store.get("probe").getComponent("P").getInt("v");
}
```

- [ ] **Step 2: 跑测试确认失败**

`mvn test -Dtest=MitigationTest#mitigate_branches` → FAIL(`Skill.mitigate is not a function`)。

- [ ] **Step 3: 实现 `Skill.mitigate`**

在 `00_skill_lib.js` 的 `Skill` 对象里加(放在 `dealDamage`/`applyDamage` 附近):

```javascript
// 唯一减伤收口。delivery: "普攻"→护甲(flat,=现行;曲线 parked) | "技能"→三抗%+元素乘区。
// opts = { delivery, type, element, elementAmp, ignoreDefend }
// ignoreDefend=true 用于 DoT tick:防御姿态不影响持续伤害(见 Task4 生命周期决策)。
mitigate: function(target, raw, opts) {
  opts = opts || {};
  var defending = target.hasComponent("Buff_defending") && !opts.ignoreDefend;
  if (opts.delivery === "普攻") {
    var armor = target.hasComponent("CombatStats") ? target.getComponent("CombatStats").getInt("defense") : 0;
    var d = Math.max(1, raw - armor);                       // 逐位对齐现行 damage_calc
    if (defending) d = Math.max(1, Math.floor(d * 0.5));
    return d;
  }
  var res = target.hasComponent("Resistances") ? target.getComponent("Resistances") : null;
  var type = opts.type || "物理";
  var typeR = (res !== null && res.has(type)) ? res.getInt(type) : 0;
  var elem = opts.element || null;
  var elemR = (elem && res !== null && res.has(elem)) ? res.getInt(elem) : 0;
  var amp = opts.elementAmp || 0;
  var dmg = raw * (1 + amp / 100) * (1 - typeR / 100) * (1 - elemR / 100);
  if (defending) dmg = dmg * 0.5;
  return Math.max(1, Math.ceil(dmg));
}
```

- [ ] **Step 4: 跑测试确认通过**

`mvn test -Dtest=MitigationTest#mitigate_branches` → PASS。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/combat/MitigationTest.java
git commit -m "feat(combat): Skill.mitigate 减伤收口(普攻护甲 / 技能三抗+元素乘区)"
```

---

## Task 3: `basic_attack` 改走收口,数值逐位不变

**Files:**
- Modify: `mods/base-rules/skills/basic_attack.yaml`
- Modify: `mods/base-rules/handlers/skill/01_effects.js`(`damage_only` 接入 mitigate)
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`(`computeDamage` 的 `via_damage_calc` 分支**保留不动**,只是 basic_attack 不再用)
- **保留不删** `mods/base-rules/handlers/combat/damage_calc.js`(basic_attack 改走收口后它 inert;但多处测试 harness 仍 load 它,删除会断 setUp。彻底删除/退役 → backlog)
- Test: `backend/.../combat/CombatBugfixTest`/`SkillCombatIntegrationTest` 现有普攻用例;新增等价回归

- [ ] **Step 1: 写/锁定回归测试 —— 普攻数值不变**

新增 `MitigationTest#basicAttack_numbersUnchanged`:战士 attack=14、敌 defense=4 → 普攻 = max(1,14-4)=10;敌防御姿态 → max(1,floor(10*0.5))=5。走真实 `action.combat_command` 普攻路径断言伤害=10/5。
(若 `SkillFidelityTest` 有 basic_attack golden,它也必须不变。)

- [ ] **Step 2: 跑现状确认(基线)**

`mvn test -Dtest=MitigationTest#basicAttack_numbersUnchanged` 当前应能反映现值;记录现有 basic_attack golden 的数字。

- [ ] **Step 3: 改 basic_attack 走 raw + 普攻分流**

`basic_attack.yaml`:
```yaml
damage:
  base: attack          # computeDamage 返回 attacker 的 CombatStats.attack(raw)
delivery: 普攻
```
删除 `via_damage_calc: true`。

`00_skill_lib.js` `computeDamage`:`via_damage_calc` 分支**原样保留**(向后兼容);确认 `base:"attack"` 路径返回 `CombatStats.attack`。`damage_calc.js` 不动、不改成调 mitigate(避免测试 harness 只 load 它不 load 00_skill_lib 的加载顺序问题)。basic_attack 改用 `base:attack`+`delivery:普攻`后,`combat.damage_calc` 不再被触发,damage_calc.js 自然 inert。

`01_effects.js` `damage_only`:**两条路径都必须、且只 mitigate 一次**——在 `computeDamage` 之后立刻算出 mitigated 值,custom(present)与非 custom(dealDamage)分支都用这个值,不得二次减伤:
```javascript
var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
dmg = Skill.mitigate(results[i].entity, dmg, {
  delivery: spec.delivery || "技能",
  type: (spec.damage && spec.damage.type) || "物理",
  element: spec.damage && spec.damage.element,
  elementAmp: 0
});
// custom:  Skill.applyDamage(target, dmg) → present → fireDamageDealt
// 非custom: Skill.dealDamage(ctx, target, dmg, spec.id, false)
// 两者都用同一个已 mitigate 的 dmg;dealDamage 内部不再二次 mitigate。
```

- [ ] **Step 4: 跑测试 + golden**

`mvn test -Dtest=MitigationTest#basicAttack_numbersUnchanged` → PASS(10/5)。
`mvn test -Dtest=SkillFidelityTest` → **basic_attack golden 不变**。全量 `mvn test` 绿。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/skills/basic_attack.yaml mods/base-rules/handlers/skill/00_skill_lib.js mods/base-rules/handlers/skill/01_effects.js backend/src/test/java/com/epic/engine/combat/MitigationTest.java
git commit -m "refactor(combat): 普攻改走 Skill.mitigate 统一收口,数值逐位不变(damage_calc.js 保留 inert)"
```

---

## Task 4: 技能链路 + DoT 接入收口;补 `damage.type`

**Files:**
- Modify: `mods/base-rules/handlers/skill/01_effects.js`(`damage_with_debuff` present 路径)
- Modify: `mods/base-rules/skills/{cleave,cross_blast,piercing_ray}.js`
- Modify: `mods/base-rules/buffs/{burning,poison}.js`(tick 伤害)
- Modify: `mods/base-rules/skills/{fireball,ice_beam,light_field}.yaml`(`damage.type: 法术`);物理系技能不写(默认物理)
- Test: `MitigationTest` 新增技能减伤用例;`SkillFidelityTest` golden 稳定

- [ ] **Step 1: 写失败测试 —— 技能吃对应抗、golden 空跑不变**

`MitigationTest#skillsRespectResist`:给敌配 `Resistances.法术=50`,法师放 fireball,断言落地伤害=裸伤的 50%(raw 由 computeDamage 决定,用敏感数值)。
并断言:同敌物抗=0 时 cleave(物理技能)伤害=裸伤(空跑不减,但走了收口)。

`MitigationTest#dotIgnoresDefending`(生命周期固定):给目标挂 burning(已知 tick 值),分别在**有/无** `Buff_defending` 下跑一个 `combat.round_end`,断言**两次 DoT 掉血完全相同**(defending 不影响 DoT);顺带断言 DoT 仍吃对应类型抗(给目标 50% 对应抗 → tick 减半)。这把 DoT×defending 的 round_end 顺序依赖钉死。

- [ ] **Step 2: 跑确认失败**

`mvn test -Dtest=MitigationTest#skillsRespectResist` → FAIL(技能当前不减伤)。

- [ ] **Step 3: 接入 mitigate(三处)**

(a) `01_effects.js` `damage_with_debuff`:与 Task3 的 `damage_only` 同样,在 `applyDamage` 前 `dmg = Skill.mitigate(target, dmg, {delivery:"技能", type, element, elementAmp:0})`。
(b) `cleave.js`/`cross_blast.js`/`piercing_ray.js`:在各自 `Skill.applyDamage(...)` 之前对每个目标 `var dmg = Skill.mitigate(results[i].entity, damage, {delivery:"技能", type:(spec.damage&&spec.damage.type)||"物理"})`,后续表现/扣血用 `dmg`。**保持上轮已修的顺序(applyDamage→emit→fireDamageDealt)。**
(c) `burning.js`/`poison.js` tick:tick 伤害扣血前过 `Skill.mitigate(target, tickDmg, {delivery:"技能", type:<burning=法术 / poison=物理>, ignoreDefend:true})`。**`ignoreDefend:true` 是生命周期决策:防御姿态不影响 DoT**(defending 是对"敌人当回合行动"的格挡,不针对回合末持续伤害)。这样 DoT 与 defending 都在 round_end 也不存在"谁先注册决定 DoT 是否减半"的隐性 bug——DoT 结果与 defending 在场与否无关。空跑下三抗 0 → DoT 数值不变。
(d) YAML:`fireball.yaml`/`ice_beam.yaml`/`light_field.yaml` 在 `damage:` 下加 `type: 法术`;物理技能(cleave/cross_blast/backstab/crescent_slash/poison_dart/pulse_wave)不写=默认物理。

- [ ] **Step 4: 跑测试 + golden**

`mvn test -Dtest=MitigationTest#skillsRespectResist` → PASS。
`mvn test -Dtest=SkillFidelityTest` → **全 golden 不变**(空跑:fidelity 敌人无抗、不防御)。全量 `mvn test` 绿。
> 若某 golden 变了:大概率是把 mitigate 调错了(漏了空跑=不减)。排查,不要重生成。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/skill/01_effects.js mods/base-rules/skills/*.js mods/base-rules/skills/*.yaml mods/base-rules/buffs/burning.js mods/base-rules/buffs/poison.js backend/src/test/java/com/epic/engine/combat/MitigationTest.java
git commit -m "feat(combat): 技能/DoT 接入 Skill.mitigate,补 damage.type;空跑 golden 不变"
```

---

## Task 5: 扩展 ContentAuthoringValidationTest

**Files:**
- Modify: `backend/src/test/java/com/epic/engine/content/ContentAuthoringValidationTest.java`

- [ ] **Step 1: 加校验断言**

在现有 skill/item 扫描里加:
- `damage.type`(若声明)∈ `{物理, 法术, 精神}`。
- `delivery`(若声明)∈ `{普攻, 技能}`。
- `Resistances.<key>` 装备词缀:`<key>` ∈ `{物理, 法术, 精神}`,值为数字。**本轮明确只支持三类类型抗;元素抗 key(火/冰…)不进装备池、不在本轮 validation 允许集合**(元素抗稀有、来自特殊技能/怪种,且需要元素注册表——进 backlog)。如此 spec 的"sparse element key"与本轮 validation 不冲突:本轮就是不允许元素 key。

```java
private static final Set<String> DMG_TYPES = Set.of("物理", "法术", "精神");
// skill 扫描里:
String dtype = string(map(skill.get("damage")) == null ? null : map(skill.get("damage")).get("type"));
require(errors, dtype == null || DMG_TYPES.contains(dtype), skillFile, "damage.type 非法: " + dtype);
String delivery = string(skill.get("delivery"));
require(errors, delivery == null || Set.of("普攻","技能").contains(delivery), skillFile, "delivery 非法: " + delivery);
```

- [ ] **Step 2-4: 跑测试**

`mvn test -Dtest=ContentAuthoringValidationTest` → PASS(对当前内容全绿——它应只在未来写错时红)。全量绿。

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/epic/engine/content/ContentAuthoringValidationTest.java
git commit -m "test(content): 校验 damage.type/delivery/Resistances 词缀合法"
```

---

## Task 6(本轮 DEFER → 后续小任务 / backlog)

UI 抗性块 + 伤害数字上色**不在本切片**。理由(采纳 Codex review #7):后端减伤地基自身就是一个完整可测的 slice;UI 改动给快照/布局引入额外风险,且全 0 抗性面板没什么可看。

**留作后续小任务**:角色面板"抗性"块(物/法/精;全 0 折叠)+ 伤害数字事件带 `type` 字段(为按类型上色,走 colorMap)。等真有抗性数值/元素了一起做更有意义。本切片不碰前端、不碰 snapshot。

---

## Task 7: 全量回归 + golden 稳定性 + 请求 review

- [ ] **Step 1:** `cd backend && mvn test` → 全绿,记录 `Tests run` 数。
- [ ] **Step 2:** `git status backend/src/test/resources/golden/` → **无改动**(空跑)。若有改动,逐项解释或回退。
- [ ] **Step 3:** 本切片不动前端;如顺手验证可 `cd frontend && npm run build`,非必须。
- [ ] **Step 4:** 用 `requesting-code-review` 流程把改动发给 Claude(Senior SDE)。**仅 Claude APPROVE 方可 merge。**

---

## Codex review v1 — 已采纳(2026-06-02)

Codex 对本 plan 的 review,Senior SDE 核实后采纳:
1. **不删 `damage_calc.js`**:多处测试 harness 显式 load 它,删除断 setUp。本轮 basic_attack 不再触发它 → inert;彻底删除进 backlog。(消解 #1#2,也避免 damage_calc 调 mitigate 的加载顺序风险——我们不让它调。)
2. **DoT × defending 顺序**(最关键):决策 = **防御姿态不影响 DoT**(`mitigate` 加 `ignoreDefend`,DoT 传 true)。消除 round_end 注册顺序的隐性依赖。补 `dotIgnoresDefending` 生命周期测试。
3. **敌人 Resistances 落地**:`start_combat.js` spawn 时装 Resistances(默认 0,可读 encounter `resistances:`)。补敌人 spawn 测试。
4. **元素 key vs validation**:本轮 validation **只允许 物/法/精**;元素抗 key 不进装备池、进 backlog —— 与 spec 的 sparse element key 不冲突(本轮就是不支持)。
5. **damage_only 两路统一**:computeDamage 后**只 mitigate 一次**,custom/非 custom 两分支共用该值,杜绝漏算/二次减伤。
6. **UI 降级**:面板抗性块 + 伤害数字上色移出本切片(Task 6 DEFER),本切片纯后端。

> 一处需 PM 知会的玩法决策:**defending 不影响 DoT**(见 #2)。若你想要"防御也减 DoT",告诉我,改 `ignoreDefend` 默认值 + 改测试即可。

## Self-Review(plan vs spec)

- **Spec §1 范围**:Task1-4 建收口+三抗+元素框架(空跑),Task3 修"普攻统一收口"、Task4 修"技能绕过减伤"的结构。✓
- **§2 两轴**:delivery(Task3/4 的 `delivery` 字段)+ type(Task4 的 `damage.type`)+ element(mitigate 的 opts.element,空跑无配置)。✓
- **§3 组件/数据**:Resistances(Task1)、护甲=defense(Task2/3)、skill 字段 delivery/type(Task3/4)。✓
- **§4 mitigate 算法**:Task2 实现并逐分支测;普攻逐位对齐(Task3)。✓
- **§5 链路改造**:damage_calc 退役(Task3)、effects+bespoke+DoT 接入(Task3/4)。✓
- **§6 红线**:Task1 测 derived 不写 Resistances;Task5 校验词缀来源。✓ review 复查。
- **§7 测试网**:MitigationTest(各分支/敌我对称由同一函数保证)、golden 稳定(Task4/7)、ContentAuthoring 扩展(Task5)。✓
- **§9 风险(普攻数值)**:Task3 专门锁 basic_attack 逐位不变 + golden。✓
- **Placeholder 扫描**:无 TBD;UI task 的"颜色后补"是明确 parked,非占位。
- **类型一致**:`Skill.mitigate(target, raw, opts)` 签名与 opts 字段(delivery/type/element/elementAmp)在 Task2/3/4 一致。✓

---

## Execution Handoff

按当前团队工作流,**本计划交 Codex 实现**(非 subagent / 非本会话执行)。Codex 逐 task TDD、频繁提交;每完成关键节点用 `requesting-code-review` 发给 Claude(Senior SDE)review,**仅 Claude APPROVE 方可 merge**。Claude 不直接改 Codex 代码,只给署名 review。
