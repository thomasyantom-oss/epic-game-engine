# Feature #5 — 被动技能(框架)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给「技能」补上被动子类与一条技能 spec 合并管道——被动通过管道 patch 别的技能、通过常驻 modifier 改属性、通过战斗事件 JS 做特殊效果;只搭框架不做内容。

**Architecture:** 主动/被动共用一份技能 spec(`kind` 判别),实例统一住 `Skillbook.known`(`{base,node,level,equipped}`)。施放时 `02_dispatch.js` 新增 `Skill.resolveSpec` 阶段,按 base→level→node→被动→装备→专精 顺序合并(后三者本期空槽),对无 Skillbook 施法者 passthrough 保回归。属性被动注册常驻 modifier;特殊效果被动走新 `passives/` 目录的 JS loader。

**Tech Stack:** GraalJS mod handlers(`mods/base-rules`)、Java 21 + Spring Boot(`ModuleLoader`/`ScriptRuntime`/`SnapshotService`/`WorldSnapshot`)、Vue 3(`SkillbookPanel.vue`)。测试:JUnit JS-mod runtime 测试(仿 `CombatBugfixTest`)+ REST 快照测试(仿 `SkillbookSnapshotTest`)。

**Spec:** `docs/superpowers/specs/2026-06-04-feature5-passive-skills-design.md`

---

## ⚠️ Review 修订(Codex `bq07m0x5k`,实现时必须覆盖原文)

下列修订**优先级高于下方 task 正文**——正文写法若与此冲突,以本段为准。完整 review 见 `docs/superpowers/codex-runs/feature5/review-response.md`。

**R1(阻塞)· `rt.eval` 不存在。** `ScriptRuntime` 只暴露 `execute(String script, String sourceName)`,**没有 `eval`**。所有「`rt.eval("...表达式...")` → 断言返回值」的测试一律改成:**`rt.execute(脚本)` 把结果写进一个 `Probe` 组件,再用 Java 从 `store` 读断言**。模板:
```java
rt.execute(
    "var e = engine.createEntity('probe');" +
    "var c = engine.newComponent('Probe');" +
    "c.set('ok', <被测布尔表达式>);" +
    "e.addComponent(c); store.add(e);", "probe.js");
assertThat(store.get("probe").getComponent("Probe").get("ok")).isEqualTo(true);
```
适用于 Task 1/2/3/4/11 所有原 `rt.eval` 测试。需要造 caster 实体的测试,把造实体脚本并入同一个 `execute`。

**R2(阻塞)· bespoke 技能绕过 resolveSpec。** `02_dispatch.js` 只覆盖 data-driven(`effect:` 声明的)技能;`mods/base-rules/skills/cleave.js`、`cross_blast.js`、`piercing_ray.js`、`defend.js`、`heal.js`、`flee.js` 是 bespoke、自己读 raw spec、不经 dispatch 的 effect 分支。**本 feature 要让被动 patch 也作用于 bespoke 技能** → 在每个 bespoke skill 内,把 `var spec = Skill._toJs(rawSpec);` 改为 `var spec = Skill.resolveSpec(Skill.context(event), "<skillId>", Skill._toJs(rawSpec));`(`flee/defend` 无伤害可不接,但接了也 passthrough 无害,统一接更省心)。**这是 Task 3 之外新增的一步,并入 Task 3**:改完后加一个测试,证明持有 `lingering_burn` 不影响 cleave(match 不中)、且对一个新加的 `match:{skill:cleave}` demo patch 生效。

**R3(阻塞)· 属性被动测试装配缺 ModifierChain。** `PassiveStatModTest`(Task 7)用 `new ScriptRuntime(bus, store)` 时 `addModifier/removeModifier/recalculate/setBase` 全 no-op。改按 `DerivedStatsTest` 四参装配:
```java
ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
typeReg.loadFromModPath(Path.of("../mods/base-rules"));
ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
ScriptRuntime rt = new ScriptRuntime(bus, store, chainService, typeReg);
```
`engine.recalculate(entityId)` 真名确认存在;`setBase/addModifier/removeModifier` 签名与 plan 一致。(参照 `backend/.../DerivedStatsTest.java` 实际装配。)

**R4(阻塞)· 升级入口名。** Task 11 的「level_up / ready」不准。实际:升级走 `action.gain_xp`,升级后 fire **`entity.level_up`**;持久化重载走 **`entity.loaded`**(见 `handlers/character/leveling.js`、`recalculate_hooks.js`)。`applySkillLevelCurve(entityId)` 应在 `entity.level_up` handler(或 `action.gain_xp` 升级分支)调用,并在 `entity.loaded` 重放一次;新建角色在 `select.js` 建完 Skillbook 后调一次。**属性被动注册(Task 7 的 `Passive.registerStatMods`)也挂这三个点**(新建 / level_up / loaded),保证幂等。

**R5(应修)· `combat.unit_death` 字段。** 该事件带 `deadId / killerId / combatId`,**无 `attackerId`**(`attackerId` 只在 `combat.damage_dealt`;`death_check.js` 把它转成 `entity.hp_zero.killerId` 再 fire `unit_death`)。Task 8 的 `lifesteal_on_kill.js` 读 `event.get("killerId")`,**不要**写 `attackerId` fallback 声称其存在。

**R6(应修)· `SkillbookActionsTest` 骨架。** Task 6 用 `ui.render_actions` 事件验证。`setUp` 的 `known` 里加一个被动条目(`iron_skin`,无 equipped),测试体:
```java
@Test
void passive_doesNotRenderAsCombatAction_evenIfKnown() {
    store.get("player1").addTag("combat:c1");
    GameEvent event = new GameEvent("ui.render_actions");
    event.set("entityId", "player1");
    event.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
    bus.fire("ui.render_actions", event);
    List<WorldSnapshot.ActionOption> actions = event.get("actions");
    assertThat(actions.stream().map(a -> String.valueOf(a.params().get("command"))))
        .doesNotContain("iron_skin");
}
```
(核 `SkillbookActionsTest` 现有 `skill(base, equipped)` helper 与 `setUp`,沿用之。)

**R7(应修)· 防御性 kind 校验。** `Passive.owns(entityId, passiveId)` 除比对 base,还要 `Skill.loadSpecAny(passiveId)` 的 `kind === "passive"` 才算数,防主动同名/坏数据误触发。`actions.js` 生成 command 前显式确认 `skills/<base>.yaml` 存在且 `kind != passive`;`03_skillbook.js` equip/unequip 找到 target 后加载 spec,非 active 直接 return。

**R8(应修,plan 漏的回归)· `CharacterFlowTest` 会被打破。** 该测试断言「`known` 全部 `equipped==true`」;Task 5 给法师加 `starting_passives` 后被动条目无 equipped → 断言失败。**Task 5 必须同时改 `CharacterFlowTest`**:断言改为「只检查 active 条目 equipped」或按 base 分支(被动忽略 equipped)。grep `getComponent("Skillbook")` / `.get("known")` 确认没有其它遍历被打破。

**R9(建议)· 行号锚点更正。** `ModuleLoader` 的 `SCRIPT_DIRS` 在 **:42**(非 :46);`actions.js` 完整改动范围 **72-79**(非 73-77)。其余锚点(`WorldSnapshot:61`/`ScriptRuntime:161`/`SnapshotService:116`/`select:108-126`)准确。

**R10(建议)· typeId 优先级。** `passive base_priority=70` 当前可行(链:buff10<equipment50<passive70<level90<class180<derived300,升序应用,70 早于 derived ✓;现有 class schema 全 `+N` 不覆盖)。**保留 70**;留一条注释:未来若 class/race 出现 set 语义可能覆盖被动,届时再议(本期不动)。

**R11(建议)· 跨 task 暂红标注。** 这些测试在本 task 阶段预期**暂红,直到后续 task 才整体绿**,plan 执行时按此预期、勿误判:
- Task 5 被动进 snapshot → 等 **Task 9**(`ui/skillbook.js` 加载 `passives/`)。
- Task 7 属性被动 → 依赖 Task 1 的 `passive` typeId + Task 2/3 的 `loadSpecAny`。
- Task 11 hook 测试 → 必须加载**定义 hook 的 character 文件**,光加载 `00_skill_lib.js` 看不到。

---

## 文件结构(改动边界)

**新建:**
- `mods/base-rules/passives/iron_skin.yaml` — demo 属性被动(stat_mod)
- `mods/base-rules/passives/lingering_burn.yaml` — demo 技能 patch 被动(skill_patch)
- `mods/base-rules/passives/lifesteal_on_kill.yaml` + `.js` — demo 特殊效果被动(handler)
- `mods/base-rules/handlers/skill/04_passive_lib.js` — 被动 helper(loadSpecAny / ownedPassives / stat_mod 注册 / handler loader 注册口)
- `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java` — 管道回归 + skill_patch + level 缩放
- `backend/src/test/java/com/epic/engine/skill/PassiveStatModTest.java` — 属性被动
- `backend/src/test/java/com/epic/engine/skill/PassiveHandlerTest.java` — 特殊效果被动

**修改:**
- `backend/src/main/java/com/epic/engine/module/ModuleLoader.java:42` — `SCRIPT_DIRS` 加 `"passives"`（R9:在 42 行）
- `mods/base-rules/modifier_types.yaml` — 加 `passive` typeId
- `mods/base-rules/handlers/skill/00_skill_lib.js` — 加 `resolveSpec` + `loadSpecAny`
- `mods/base-rules/handlers/skill/02_dispatch.js` — 插入 `resolveSpec` 调用
- `mods/base-rules/skills/{cleave,cross_blast,piercing_ray}.js` — R2:bespoke 技能也接 `resolveSpec`
- `backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java` — R8:known 全 equipped 断言改只检查 active
- `mods/base-rules/handlers/character/select.js:108-126` — 实例加 `level`、播种 `starting_passives`
- `mods/base-rules/handlers/character/derived_stats.js` — 或新文件:属性被动 modifier 注册接入 recalc
- `mods/base-rules/handlers/ui/actions.js:73-77` — 指令生成只取主动
- `mods/base-rules/handlers/skill/03_skillbook.js` — equip/unequip 拒绝被动
- `mods/base-rules/handlers/ui/skillbook.js` — 快照吐 `kind`/`level`、被动从 `passives/` 加载
- `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java:61` — `SkillEntry` 加 `level`/`kind`
- `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:161` — `newSkillEntry` 加 `level`/`kind`
- `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java:116` — `equippedCount` 只数主动
- `mods/base-rules/schemas/sub/class_mage.schema.yaml` — 加 demo `starting_passives`
- `frontend/src/components/SkillbookPanel.vue` — 被动 tab 渲染
- `mods/base-rules/handlers/character/recalculate_hooks.js` — 人物升级→技能等级接缝(hook)

---

## Task 1: 被动目录与 loader 基建

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/module/ModuleLoader.java:46`
- Modify: `mods/base-rules/modifier_types.yaml`
- Create: `mods/base-rules/passives/iron_skin.yaml`
- Test: `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

- [ ] **Step 1: 写失败测试** — 验证 passive YAML 能被 `engine.loadYaml("passives/...")` 读到

Create `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`:

```java
package com.epic.engine.skill;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class ResolveSpecTest {

    ScriptRuntime newRuntime() {
        EventBus bus = new EventBus();
        EntityStore store = new EntityStore();
        ScriptRuntime rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        return rt;
    }

    @Test
    void passiveYaml_loadable() {
        ScriptRuntime rt = newRuntime();
        Object spec = rt.eval("engine.loadYaml('passives/iron_skin.yaml')");
        assertThat(spec).isNotNull();
    }
}
```

> 注:`ScriptRuntime` 已有 `eval(String)`(GraalJS 求值)。若方法名不同,核 `ScriptRuntime.java` 暴露的求值入口并对齐。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#passiveYaml_loadable`
Expected: FAIL（文件不存在 → loadYaml 返回 null）

- [ ] **Step 3: 加被动目录到 loader + 建 demo spec + 加 typeId**

`ModuleLoader.java:46` 改:
```java
private static final String[] SCRIPT_DIRS = {"handlers", "skills", "buffs", "passives"};
```

Create `mods/base-rules/passives/iron_skin.yaml`:
```yaml
id: iron_skin
kind: passive
name: "铁壁"
description: "力量提升（demo 属性被动）"
icon: "壁"
effect: stat_mod
modifiers: { 力量: 10 }
```

`modifier_types.yaml` 末尾加（priority < 300，使派生属性读到加成后的基础属性）:
```yaml
  - id: passive
    label: 被动
    stack_rule: additive
    base_priority: 70
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#passiveYaml_loadable`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/module/ModuleLoader.java \
  mods/base-rules/modifier_types.yaml mods/base-rules/passives/iron_skin.yaml \
  backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java
git commit -m "feat(ch1-f5): 被动目录入 loader + passive typeId + demo spec"
```

---

## Task 2: resolveSpec 骨架（passthrough，回归安全）

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Modify: `mods/base-rules/handlers/skill/02_dispatch.js`
- Test: `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

- [ ] **Step 1: 写失败测试** — 无 Skillbook 施法者时 `resolveSpec` 返回原 spec（逐字段一致）

加方法到 `ResolveSpecTest`（在 `newRuntime` 后加载 skill lib）:

```java
ScriptRuntime withSkillLib() throws Exception {
    ScriptRuntime rt = newRuntime();
    java.nio.file.Path d = Path.of("../mods/base-rules/handlers/skill");
    rt.execute(java.nio.file.Files.readString(d.resolve("00_skill_lib.js")), "00_skill_lib.js");
    return rt;
}

@Test
void resolveSpec_passthrough_whenNoSkillbook() throws Exception {
    ScriptRuntime rt = withSkillLib();
    // caster 没有 Skillbook 时，resolveSpec 不改 spec
    Object same = rt.eval(
        "var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));" +
        "var ctx = { caster: null, actorId: 'ghost' };" +
        "var out = Skill.resolveSpec(ctx, 'fireball', raw);" +
        "JSON.stringify(out) === JSON.stringify(raw)");
    assertThat(same).isEqualTo(true);
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#resolveSpec_passthrough_whenNoSkillbook`
Expected: FAIL（`Skill.resolveSpec` 未定义）

- [ ] **Step 3: 实现 resolveSpec 骨架**

`00_skill_lib.js` 在 `Skill` 对象内（`loadSpec` 之后）加:

```js
  // 同 id 在 skills/ 或 passives/ 找 spec（缓存）。先主动后被动。
  loadSpecAny: function(id) {
    var s = this.loadSpec(id);
    if (s !== null && s !== undefined) return s;
    var key = "passive:" + id;
    if (this._specs[key] === undefined) {
      this._specs[key] = engine.loadYaml("passives/" + id + ".yaml");
    }
    return this._specs[key];
  },

  // 取施法者持有的 base 实例（node/level）。无 Skillbook/无条目 → {node:null, level:1}。
  ownerInstance: function(ctx, baseId) {
    var c = ctx ? ctx.caster : null;
    if (c === null || c === undefined || !c.hasComponent("Skillbook")) return { node: null, level: 1 };
    var known = c.getComponent("Skillbook").get("known");
    if (known === null) return { node: null, level: 1 };
    for (var i = 0; i < known.size(); i++) {
      var k = known.get(i);
      if (String(k.get("base")) === String(baseId)) {
        return { node: (k.get("node") !== null ? String(k.get("node")) : null),
                 level: (k.has("level") ? k.getInt("level") : 1) };
      }
    }
    return { node: null, level: 1 };
  },

  // 合并管道：base → level → node → 被动 → 装备 → 专精。后三槽本期空实现。
  resolveSpec: function(ctx, baseId, baseSpec) {
    var spec = JSON.parse(JSON.stringify(baseSpec));   // clone，避免污染 _specs 缓存
    var inst = this.ownerInstance(ctx, baseId);
    spec = this.applyLevelScaling(spec, inst.level);
    spec = this.applyNode(spec, inst.node);            // #7 槽
    spec = this.applyPassivePatches(ctx, baseId, spec);
    // applyEquipmentPatches(spec)  // Ch2 槽
    // applySpecPatches(spec)       // #6 槽
    return spec;
  },

  applyLevelScaling: function(spec, level) { return spec; },          // Task 4 填
  applyNode: function(spec, node) { return spec; },                   // #7 空实现
  applyPassivePatches: function(ctx, baseId, spec) { return spec; },  // Task 3 填
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#resolveSpec_passthrough_whenNoSkillbook`
Expected: PASS

- [ ] **Step 5: 接入 dispatch**

`02_dispatch.js` 把:
```js
  var spec = Skill._toJs(rawSpec);
  var ctx = Skill.context(event);
  var results = Skill.resolveTargets(ctx, spec);
```
改为:
```js
  var spec = Skill._toJs(rawSpec);
  var ctx = Skill.context(event);
  spec = Skill.resolveSpec(ctx, type, spec);
  var results = Skill.resolveTargets(ctx, spec);
```

- [ ] **Step 6: 全量回归**（dispatch 改动后，现有技能行为必须零变化）

Run: `cd backend && mvn test`
Expected: PASS（含 `CombatBugfixTest`、`SkillbookSnapshotTest` 等全绿）

- [ ] **Step 7: 提交**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js mods/base-rules/handlers/skill/02_dispatch.js \
  backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java
git commit -m "feat(ch1-f5): resolveSpec 合并管道骨架(passthrough)接入 dispatch"
```

---

## Task 3: skill_patch 被动（applyPassivePatches）

**Files:**
- Create: `mods/base-rules/passives/lingering_burn.yaml`
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Test: `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

- [ ] **Step 1: 建 demo 被动 spec**

Create `mods/base-rules/passives/lingering_burn.yaml`:
```yaml
id: lingering_burn
kind: passive
name: "余烬"
description: "火球的灼烧多持续一回合（demo 技能 patch 被动）"
icon: "烬"
effect: skill_patch
match: { skill: fireball }
patch: { "debuff.data.remaining": +1 }
```

- [ ] **Step 2: 写失败测试** — 持有 lingering_burn 的施法者放火球，`resolveSpec` 后 `debuff.data.remaining` = 3（base 2 + 1）

加到 `ResolveSpecTest`:
```java
@Test
void skillPatchPassive_extendsBurn() throws Exception {
    ScriptRuntime rt = withSkillLib();
    Boolean ok = (Boolean) rt.eval(
        // 造一个带 Skillbook、持有 lingering_burn 被动的实体
        "var e = engine.createEntity('hero');" +
        "var sb = engine.newComponent('Skillbook');" +
        "var known = engine.newList();" +
        "var p = engine.newMap(); p.put('base','lingering_burn'); p.put('node',null); p.put('level',1); known.add(p);" +
        "sb.set('known', known); e.addComponent(sb); store.add(e);" +
        "var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));" +
        "var ctx = { caster: e, actorId: 'hero' };" +
        "var out = Skill.resolveSpec(ctx, 'fireball', raw);" +
        "out.debuff.data.remaining === 3");
    assertThat(ok).isTrue();
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#skillPatchPassive_extendsBurn`
Expected: FAIL（remaining 仍是 2）

- [ ] **Step 4: 实现 applyPassivePatches + match/patch**

`00_skill_lib.js` 替换 `applyPassivePatches` 空实现，并加私有 helpers:
```js
  applyPassivePatches: function(ctx, baseId, spec) {
    var passives = this.ownedPassives(ctx);
    for (var i = 0; i < passives.length; i++) {
      var ps = passives[i];
      if (ps.effect !== "skill_patch") continue;
      if (!this._matchSkill(ps.match, baseId, spec)) continue;
      this._applyPatch(spec, ps.patch);
    }
    return spec;
  },

  // 施法者持有的、kind==passive 的 spec 列表。
  ownedPassives: function(ctx) {
    var out = [];
    var c = ctx ? ctx.caster : null;
    if (c === null || c === undefined || !c.hasComponent("Skillbook")) return out;
    var known = c.getComponent("Skillbook").get("known");
    if (known === null) return out;
    for (var i = 0; i < known.size(); i++) {
      var base = String(known.get(i).get("base"));
      var raw = this.loadSpecAny(base);
      if (raw === null || raw === undefined) continue;
      var kind = (typeof raw.get === "function") ? raw.get("kind") : raw.kind;
      if (String(kind) !== "passive") continue;
      out.push(this._toJs(raw));
    }
    return out;
  },

  // match: {skill:<id>} 比 baseId；{<字段路径>:<值>} 比 spec 对应字段。
  _matchSkill: function(match, baseId, spec) {
    if (!match) return false;
    var keys = Object.keys(match);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (k === "skill") { if (String(match[k]) !== String(baseId)) return false; }
      else { if (String(this._getPath(spec, k)) !== String(match[k])) return false; }
    }
    return true;
  },

  // patch: 字段路径 → 值。带 "+" 前缀的数值=加；否则=set。
  _applyPatch: function(spec, patch) {
    if (!patch) return;
    var keys = Object.keys(patch);
    for (var i = 0; i < keys.length; i++) {
      var v = patch[keys[i]];
      if (typeof v === "number") { this._setPath(spec, keys[i], (this._getPath(spec, keys[i]) || 0) + v); }
      else if (typeof v === "string" && v.charAt(0) === "+") { this._setPath(spec, keys[i], (this._getPath(spec, keys[i]) || 0) + parseFloat(v.substring(1))); }
      else { this._setPath(spec, keys[i], v); }
    }
  },

  _getPath: function(obj, path) {
    var parts = path.split("."); var cur = obj;
    for (var i = 0; i < parts.length; i++) { if (cur == null) return undefined; cur = cur[parts[i]]; }
    return cur;
  },

  _setPath: function(obj, path, val) {
    var parts = path.split("."); var cur = obj;
    for (var i = 0; i < parts.length - 1; i++) { if (cur[parts[i]] == null) cur[parts[i]] = {}; cur = cur[parts[i]]; }
    cur[parts[parts.length - 1]] = val;
  },
```

> 注:YAML `+1` 会被 SnakeYAML 解析成数字 `1`,故 `typeof v === "number"` 分支即「加」。若作者写 `"+1"`(字符串)走字符串分支。两者都按加处理,符合 spec §6.2。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#skillPatchPassive_extendsBurn`
Expected: PASS

- [ ] **Step 6: 回归**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest`
Expected: PASS（passthrough 测试仍绿——无被动者不受影响）

- [ ] **Step 7: 提交**

```bash
git add mods/base-rules/passives/lingering_burn.yaml mods/base-rules/handlers/skill/00_skill_lib.js \
  backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java
git commit -m "feat(ch1-f5): skill_patch 被动 + match/patch + demo 余烬"
```

---

## Task 4: level 缩放钩子（applyLevelScaling）

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Test: `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

- [ ] **Step 1: 写失败测试** — spec 声明 `level_scaling`，level 3 时按 (level-1) 倍加

加到 `ResolveSpecTest`（直接喂带 level_scaling 的内联 spec，level 来自实例）:
```java
@Test
void levelScaling_scalesDeclaredField() throws Exception {
    ScriptRuntime rt = withSkillLib();
    Boolean ok = (Boolean) rt.eval(
        "var e = engine.createEntity('lv');" +
        "var sb = engine.newComponent('Skillbook'); var known = engine.newList();" +
        "var p = engine.newMap(); p.put('base','fireball'); p.put('node',null); p.put('level',3); known.add(p);" +
        "sb.set('known', known); e.addComponent(sb); store.add(e);" +
        "var raw = { id:'fireball', effect:'damage_only', damage:{ add:10 }, level_scaling:{ 'damage.add':2 } };" +
        "var ctx = { caster: e, actorId:'lv' };" +
        "var out = Skill.resolveSpec(ctx, 'fireball', raw);" +
        // level 3 → (3-1)*2 = +4 → 14
        "out.damage.add === 14");
    assertThat(ok).isTrue();
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#levelScaling_scalesDeclaredField`
Expected: FAIL（damage.add 仍 10）

- [ ] **Step 3: 实现 applyLevelScaling**

`00_skill_lib.js` 替换 `applyLevelScaling` 空实现:
```js
  // 极简线性钩子：spec.level_scaling = { 字段路径: 每级增量 }；总增量 = (level-1)*增量。
  // 真曲线属内容（留给 Ch1 内容轮）；本期只提供「按 level 缩放声明字段」的机制。
  applyLevelScaling: function(spec, level) {
    var ls = spec.level_scaling;
    if (!ls || !level || level <= 1) return spec;
    var keys = Object.keys(ls);
    for (var i = 0; i < keys.length; i++) {
      var inc = ls[keys[i]] * (level - 1);
      this._setPath(spec, keys[i], (this._getPath(spec, keys[i]) || 0) + inc);
    }
    return spec;
  },
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#levelScaling_scalesDeclaredField`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java
git commit -m "feat(ch1-f5): level 缩放钩子(极简线性,曲线留内容轮)"
```

---

## Task 5: 实例 level 字段 + starting_passives 播种

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js:108-126`
- Modify: `mods/base-rules/schemas/sub/class_mage.schema.yaml`
- Test: `backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java`

- [ ] **Step 1: 给法师加 demo 起始被动**

`class_mage.schema.yaml` 在 `starting_skills: [fireball, light_field]` 下加:
```yaml
starting_passives: [iron_skin, lingering_burn]
```

- [ ] **Step 2: select.js 播种 level + 被动**

`select.js` 主动播种循环里 `inst.put("equipped", true);` 后加 `inst.put("level", 1);`。在该循环之后、`skills.set("slots", ...)` 之前加被动播种:
```js
    if (classSchema !== null && classSchema.raw().get("starting_passives") !== null) {
        var startPassives = classSchema.raw().get("starting_passives");
        for (var pp = 0; pp < startPassives.size(); pp++) {
            var pinst = engine.newMap();
            pinst.put("base", String(startPassives.get(pp)));
            pinst.put("node", null);
            pinst.put("level", 1);
            // 被动无 equipped（拥有即生效）
            known.add(pinst);
        }
    }
```

- [ ] **Step 3: 写失败测试** — 法师 known 含被动 base，且 `equippedCount` 不被被动顶高

在 `SkillbookSnapshotTest` 加（沿用现有 REST 流程）:
```java
@Test
@SuppressWarnings("unchecked")
void snapshot_includesPassivesButCountsOnlyActives() {
    String token = sessionService.createSession();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Session-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    rest.exchange("/api/action", HttpMethod.POST, new HttpEntity<>(Map.of(
            "type", "confirm_character",
            "params", Map.of("name", "被动测试", "class", "mage")), headers), Map.class);
    ResponseEntity<Map> snap = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    Map<String, Object> skillbook = (Map<String, Object>) snap.getBody().get("skillbook");
    List<Map<String, Object>> known = (List<Map<String, Object>>) skillbook.get("known");
    List<String> bases = known.stream().map(s -> String.valueOf(s.get("base"))).toList();
    assertThat(bases).contains("iron_skin", "lingering_burn");
    // 被动不计入出战数（仍是 2 个主动）
    assertThat(((Number) skillbook.get("equippedCount")).intValue()).isEqualTo(2);
}
```

- [ ] **Step 4: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=SkillbookSnapshotTest#snapshot_includesPassivesButCountsOnlyActives`
Expected: FAIL（此时 `ui/skillbook.js` 只从 `skills/` 加载，被动 base 找不到 → 被跳过 → bases 不含被动；该项在 Task 8 修快照后才会真正包含。本 Task 仅播种,先让数据进 `known`）

> 该测试在 Task 8 完成后才整体转绿；本 Task 先确保 `equippedCount==2` 这半部分不被破坏。可暂时只断言 `equippedCount==2`，Task 8 再补 `bases.contains(...)`。

- [ ] **Step 5: 暂时收窄断言**，先验证播种不破坏出战计数

把 Step 3 测试体中 `assertThat(bases).contains(...)` 一行先注释，保留 `equippedCount==2`。

Run: `cd backend && mvn test -Dtest=SkillbookSnapshotTest`
Expected: PASS（含原 `snapshot_includesSkillbook`——被动被 `ui/skillbook.js` 跳过，不影响主动）

- [ ] **Step 6: 提交**

```bash
git add mods/base-rules/handlers/character/select.js mods/base-rules/schemas/sub/class_mage.schema.yaml \
  backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java
git commit -m "feat(ch1-f5): 实例加 level 字段 + 法师 starting_passives 播种"
```

---

## Task 6: 指令生成 kind 感知 + equip 拒绝被动

**Files:**
- Modify: `mods/base-rules/handlers/ui/actions.js:73-77`
- Modify: `mods/base-rules/handlers/skill/03_skillbook.js`
- Test: `backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java`

- [ ] **Step 1: 写失败测试** — 被动绝不出现在战斗指令列表；equip 被动无效

> 沿用 `SkillbookActionsTest` 现有 setup（核其加载 actions.js 的方式）。断言玩家可用战斗指令不含 `iron_skin`/`lingering_burn`，且对被动调 `skillbook_equip` 后该被动不会变 equipped。

```java
@Test
void passives_neverBecomeCombatCommands_andCannotEquip() {
    // 1) 生成可用指令（ui.render actions），断言不含被动 base
    // 2) 触发 action.skillbook_equip{base: iron_skin}，再读 known 中 iron_skin.equipped != true
    // （按本测试类既有 helper 构造 player + Skillbook，参照同文件其它用例）
}
```

> 实现者:按 `SkillbookActionsTest` 既有模式补全测试体（该文件已有构造玩家 Skillbook 与触发 action 的 helper）。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest`
Expected: FAIL

- [ ] **Step 3: actions.js 只取主动**

`actions.js` 遍历 `known` 生成指令处（line 74 起），在 `if (sk.get("equipped") !== true) continue;` 前加 kind 守卫:
```js
            var skDef = engine.loadYaml("skills/" + String(sk.get("base")) + ".yaml");
            if (skDef === null) continue;   // 被动 spec 不在 skills/ → 天然跳过；显式更稳
```
> `skills/` 里找不到的 base(被动)直接跳过——指令只可能来自主动。

- [ ] **Step 4: 03_skillbook.js 拒绝被动 equip**

`_setSkillbookEquipped` 找到 `target` 后、改 `equipped` 前加:
```js
    var def = engine.loadYaml("skills/" + String(base) + ".yaml");
    if (def === null) return;   // 被动（不在 skills/）不可 equip
```

- [ ] **Step 5: 跑测试确认通过 + 回归**

Run: `cd backend && mvn test -Dtest=SkillbookActionsTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add mods/base-rules/handlers/ui/actions.js mods/base-rules/handlers/skill/03_skillbook.js \
  backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java
git commit -m "feat(ch1-f5): 指令生成 kind 感知 + equip 拒绝被动"
```

---

## Task 7: 属性被动 → 常驻 modifier（stat_mod）

**Files:**
- Create: `mods/base-rules/handlers/skill/04_passive_lib.js`
- Modify: `mods/base-rules/handlers/character/recalculate_hooks.js`（接入 recalc 时注册）
- Test: `backend/src/test/java/com/epic/engine/skill/PassiveStatModTest.java`

- [ ] **Step 1: 写失败测试** — 持有 iron_skin 的实体，注册后 力量 +10、法术强度随之变（派生在后）

Create `PassiveStatModTest.java`（仿 `ResolveSpecTest` 的 runtime 装配，额外加载 derived_stats.js + 04_passive_lib.js）:
```java
package com.epic.engine.skill;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PassiveStatModTest {
    @Test
    void statModPassive_boostsPrimary() throws Exception {
        EventBus bus = new EventBus();
        EntityStore store = new EntityStore();
        ScriptRuntime rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        rt.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        rt.execute(Files.readString(skill.resolve("04_passive_lib.js")), "04_passive_lib.js");

        Boolean ok = (Boolean) rt.eval(
            "var e = engine.createEntity('m');" +
            "var ps = engine.newComponent('PrimaryStats'); ps.set('力量', 5); e.addComponent(ps);" +
            "var sb = engine.newComponent('Skillbook'); var known = engine.newList();" +
            "var p = engine.newMap(); p.put('base','iron_skin'); p.put('node',null); p.put('level',1); known.add(p);" +
            "sb.set('known', known); e.addComponent(sb); store.add(e);" +
            "engine.setBase('m');" +
            "Passive.registerStatMods('m');" +
            "engine.recalculate('m');" +   // 应用 modifier 链
            "e.getComponent('PrimaryStats').getInt('力量') === 15");
        assertThat(ok).isTrue();
    }
}
```
> 注:核 `ScriptRuntime`/`engine` 暴露的 `setBase`/`recalculate` 入口名（参照 `derived_stats.js` 与 `select.js` 用法）。若 recalc 在 Java 侧由 ModifierChain 驱动,调对应入口。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=PassiveStatModTest`
Expected: FAIL（`Passive` 未定义）

- [ ] **Step 3: 实现 04_passive_lib.js（stat_mod 注册）**

Create `mods/base-rules/handlers/skill/04_passive_lib.js`:
```js
// 被动 helper：属性被动 → 常驻 modifier；handler 被动加载（Task 8）。
var Passive = {
  // 给实体注册其所有 stat_mod 被动为常驻 modifier（幂等：先撤同 id 再加）。
  // modifier 改 PrimaryStats，priority(typeId passive=70) 在 derived(300) 之前 → 派生读到加成后基础属性。
  registerStatMods: function(entityId) {
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return;
    for (var i = 0; i < known.size(); i++) {
      var base = String(known.get(i).get("base"));
      var raw = Skill.loadSpecAny(base);
      if (raw === null || raw === undefined) continue;
      var spec = Skill._toJs(raw);
      if (spec.kind !== "passive" || spec.effect !== "stat_mod") continue;
      var level = known.get(i).has("level") ? known.get(i).getInt("level") : 1;
      this._registerOne(entityId, spec, level);
    }
  },

  _registerOne: function(entityId, spec, level) {
    var modId = "passive_" + spec.id + "_" + entityId;
    engine.removeModifier(entityId, modId);
    var mods = spec.modifiers || {};
    var keys = Object.keys(mods);
    if (keys.length === 0) return;
    // 闭包捕获加成（level 缩放：每级 = 声明值；本期等同 base*level，曲线留内容轮）
    var deltas = {};
    for (var i = 0; i < keys.length; i++) deltas[keys[i]] = mods[keys[i]] * level;
    engine.addModifier(entityId, {
      typeId: "passive",
      id: modId,
      label: spec.name || spec.id,
      apply: function(ent) {
        var p = ent.getComponent("PrimaryStats");
        if (p === null) return;
        var dk = Object.keys(deltas);
        for (var j = 0; j < dk.length; j++) {
          if (p.has(dk[j])) p.set(dk[j], p.getInt(dk[j]) + deltas[dk[j]]);
        }
      }
    });
  }
};
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=PassiveStatModTest`
Expected: PASS

- [ ] **Step 5: 接入角色生命周期**

在 `recalculate_hooks.js`（或 `select.js` 创建角色后、持久化重载后的 recalc 入口）调用 `Passive.registerStatMods(entityId)`，与 `registerDerivedModifier` 并列。核现有「角色就绪 / 重载」事件处统一接入（确保新建 + 持久化重载都注册，且幂等）。

```js
// 例：在注册 derived modifier 的同一处之后
if (typeof Passive !== 'undefined') Passive.registerStatMods(entityId);
```

- [ ] **Step 6: 回归全量**

Run: `cd backend && mvn test`
Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add mods/base-rules/handlers/skill/04_passive_lib.js mods/base-rules/handlers/character/recalculate_hooks.js \
  backend/src/test/java/com/epic/engine/skill/PassiveStatModTest.java
git commit -m "feat(ch1-f5): 属性被动→常驻 modifier(幂等,派生前)"
```

---

## Task 8: 特殊效果被动（handler loader + demo）

**Files:**
- Create: `mods/base-rules/passives/lifesteal_on_kill.yaml` + `mods/base-rules/passives/lifesteal_on_kill.js`
- Test: `backend/src/test/java/com/epic/engine/skill/PassiveHandlerTest.java`

- [ ] **Step 1: 建 demo handler 被动**

Create `mods/base-rules/passives/lifesteal_on_kill.yaml`:
```yaml
id: lifesteal_on_kill
kind: passive
name: "嗜血"
description: "击杀目标时回复 5 点生命（demo 特殊效果被动）"
icon: "血"
effect: handler
```

Create `mods/base-rules/passives/lifesteal_on_kill.js`:
```js
// 特殊效果被动：击杀时施法者回血。守卫「击杀者是否持有本被动」。
engine.on("combat.unit_death", 60, function(event) {
    var killerId = event.has("killerId") ? event.get("killerId") : event.get("attackerId");
    if (killerId === null) return;
    var killer = store.get(killerId);
    if (killer === null || !killer.hasComponent("Skillbook")) return;
    if (!Passive.owns(killerId, "lifesteal_on_kill")) return;
    var h = killer.getComponent("Health");
    if (h === null) return;
    h.set("hp", Math.min(h.getInt("maxHp"), h.getInt("hp") + 5));
});
```
> 核 `combat.unit_death` 事件实际携带的击杀者字段名（看 `death_check.js` / `combat_events.js` fire 处）；若无 killer 字段,改挂 `combat.damage_dealt` 并在致死时判定。

- [ ] **Step 2: 加 Passive.owns 守卫 helper**

`04_passive_lib.js` 的 `Passive` 加:
```js
  owns: function(entityId, passiveId) {
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return false;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return false;
    for (var i = 0; i < known.size(); i++) {
      if (String(known.get(i).get("base")) === String(passiveId)) return true;
    }
    return false;
  },
```

- [ ] **Step 3: 写失败测试** — 持有 lifesteal 的击杀者击杀后 hp +5

Create `PassiveHandlerTest.java`（runtime 装配 + 加载 04_passive_lib.js + passives/lifesteal_on_kill.js，手动 fire `combat.unit_death`）:
```java
package com.epic.engine.skill;
// imports 同 PassiveStatModTest
class PassiveHandlerTest {
    @Test
    void lifesteal_healsOnKill() throws Exception {
        // 装配 runtime，加载 00_skill_lib.js、04_passive_lib.js、passives/lifesteal_on_kill.js
        // 造 killer：Health{hp:10,maxHp:30} + Skillbook 持有 lifesteal_on_kill
        // fire combat.unit_death{killerId: killer, ...}
        // 断言 killer hp == 15
    }
}
```
> 实现者按 `PassiveStatModTest` 装配模式补全；fire 事件用 `engine.newEvent` + `engine.fire`（参照其它 combat 测试）。

- [ ] **Step 4: 跑测试确认失败 → 实现 → 通过**

Run: `cd backend && mvn test -Dtest=PassiveHandlerTest`
（demo JS 已在 Step 1 写好；若字段名需调整按 Step 1 注解修正）
Expected: PASS

- [ ] **Step 5: 回归全量**

Run: `cd backend && mvn test`
Expected: PASS（`passives/*.js` 已由 Task 1 的 loader 自动加载，确认无加载顺序问题——`Passive`/`Skill` 在 handler 触发时已就绪）

- [ ] **Step 6: 提交**

```bash
git add mods/base-rules/passives/lifesteal_on_kill.yaml mods/base-rules/passives/lifesteal_on_kill.js \
  mods/base-rules/handlers/skill/04_passive_lib.js backend/src/test/java/com/epic/engine/skill/PassiveHandlerTest.java
git commit -m "feat(ch1-f5): 特殊效果被动 handler + 守卫 + demo 嗜血"
```

---

## Task 9: 快照吐 kind/level + 被动从 passives/ 加载

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java:61`
- Modify: `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:161`
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java:116`
- Modify: `mods/base-rules/handlers/ui/skillbook.js`
- Test: `backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java`

- [ ] **Step 1: 扩展 SkillEntry record**

`WorldSnapshot.java:61`:
```java
public record SkillEntry(String base, String name, String description, String icon,
                         boolean equipped, String node, int level, String kind) {}
```

- [ ] **Step 2: 扩展 newSkillEntry**

`ScriptRuntime.java:161`:
```java
public WorldSnapshot.SkillEntry newSkillEntry(String base, String name, String description,
        String icon, boolean equipped, String node, int level, String kind) {
    return new WorldSnapshot.SkillEntry(base, name, description, icon, equipped, node, level, kind);
}
```

- [ ] **Step 3: equippedCount 只数主动**

`SnapshotService.java:116`:
```java
int equipped = (int) known.stream()
        .filter(e -> "active".equals(e.kind()) && e.equipped()).count();
```

- [ ] **Step 4: ui/skillbook.js 加载主动+被动、吐 kind/level**

整体替换 `ui/skillbook.js` 遍历体:
```js
    for (var i = 0; i < known.size(); i++) {
        var skill = known.get(i);
        var base = String(skill.get("base"));
        var def = engine.loadYaml("skills/" + base + ".yaml");
        var kind = "active";
        if (def === null) { def = engine.loadYaml("passives/" + base + ".yaml"); kind = "passive"; }
        if (def === null) continue;
        var name = def.get("name") !== null ? String(def.get("name")) : base;
        var description = def.get("description") !== null ? String(def.get("description")) : "";
        var icon = def.get("icon") !== null ? String(def.get("icon")) : name.substring(0, 1);
        var node = skill.get("node") !== null ? String(skill.get("node")) : null;
        var equipped = skill.get("equipped") === true;
        var level = skill.has("level") ? skill.getInt("level") : 1;
        out.add(engine.newSkillEntry(base, name, description, icon, equipped, node, level, kind));
    }
```

- [ ] **Step 5: 解开 Task 5 收窄的断言 + 加 kind/level 断言**

`SkillbookSnapshotTest`：恢复 `snapshot_includesPassivesButCountsOnlyActives` 中被注释的 `assertThat(bases).contains("iron_skin", "lingering_burn");`，并在 `snapshot_includesSkillbook` 加:
```java
assertThat(((Number) fireball.get("level")).intValue()).isEqualTo(1);
assertThat(fireball.get("kind")).isEqualTo("active");
```
并断言被动 entry 的 kind:
```java
Map<String, Object> ironSkin = known.stream()
    .filter(s -> "iron_skin".equals(s.get("base"))).findFirst().orElseThrow();
assertThat(ironSkin.get("kind")).isEqualTo("passive");
```

- [ ] **Step 6: 跑测试 + 全量回归**

Run: `cd backend && mvn test`
Expected: PASS（所有引用 `newSkillEntry` 的 JS 已更新；record 新字段全链路通）

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java \
  backend/src/main/java/com/epic/engine/script/ScriptRuntime.java \
  backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java \
  mods/base-rules/handlers/ui/skillbook.js \
  backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java
git commit -m "feat(ch1-f5): 快照吐 kind/level + 被动从 passives/ 加载"
```

---

## Task 10: 前端被动 tab 渲染

**Files:**
- Modify: `frontend/src/components/SkillbookPanel.vue`

- [ ] **Step 1: 计算属性拆主动/被动**

`<script setup>` 内 `sortedKnown` 旁加:
```js
const activeKnown = computed(() =>
  [...known.value].filter(s => (s.kind ?? 'active') === 'active')
    .sort((a, b) => Number(b.equipped === true) - Number(a.equipped === true))
)
const passiveKnown = computed(() =>
  known.value.filter(s => s.kind === 'passive')
)
```
并把模板里主动 tab 的 `v-for="skill in sortedKnown"` 改为 `activeKnown`。

- [ ] **Step 2: 被动 tab 模板**

把 `<div v-else class="sb-empty">暂未开放</div>` 替换为:
```html
    <div v-else-if="tab === 'passive'" class="sb-list">
      <div v-for="p in passiveKnown" :key="p.base" class="sb-row">
        <div class="sb-icon">{{ p.icon }}</div>
        <div class="sb-info">
          <div class="sb-name">{{ p.name }} <span class="sb-lv">Lv{{ p.level ?? 1 }}</span></div>
          <div class="sb-desc">{{ p.description }}</div>
        </div>
      </div>
      <div v-if="passiveKnown.length === 0" class="sb-empty">暂无被动</div>
    </div>

    <div v-else class="sb-empty">暂未开放</div>
```

- [ ] **Step 3: 样式（2px 边框规范）**

`<style scoped>` 加:
```css
.sb-lv {
  font-size: 0.75rem;
  color: var(--color-highlight);
  border: 2px solid var(--color-highlight);
  border-radius: 3px;
  padding: 0 0.3rem;
  margin-left: 0.3rem;
}
```

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: 构建成功，无错误

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/SkillbookPanel.vue
git commit -m "feat(ch1-f5): 前端被动 tab 渲染(只读,Lv 标签)"
```

---

## Task 11: 人物升级 → 技能等级接缝（只留 hook）

**Files:**
- Modify: `mods/base-rules/handlers/character/recalculate_hooks.js`（或现有 level_up 事件处）
- Test: `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

- [ ] **Step 1: 核现有升级事件**

找角色涨级的事件/入口（grep `level_up`/`xp`/`level` in `mods/base-rules/handlers/character/*.js`）。本 Task 只在该处留一个**空实现 hook 函数** `applySkillLevelCurve(entityId)`,内部 TODO 注释指向「Ch1 内容轮填 charLevel→skillLevel 映射」,当前 no-op（level 维持 1/实例值）。

- [ ] **Step 2: 写测试** — hook 存在且 no-op（升级不改 level，仍读实例值）

加到 `ResolveSpecTest`:
```java
@Test
void skillLevelSeam_isNoOpForNow() throws Exception {
    ScriptRuntime rt = withSkillLib();
    // applySkillLevelCurve 定义即可，调用不抛错、不改实例 level
    Object defined = rt.eval("typeof applySkillLevelCurve === 'function'");
    assertThat(defined).isEqualTo(true);
}
```
> 若 hook 定义在 character 目录文件，测试装配里 execute 对应文件。

- [ ] **Step 3: 实现空 hook**

在升级处文件加:
```js
// 接缝：人物等级 → 技能等级。Ch1 内容设计轮在此填 charLevel→skillLevel 曲线（每技能不同）。
// 现为 no-op：技能 level 维持实例存储值（默认 1 / debug 设定）。
function applySkillLevelCurve(entityId) {
    // TODO(Ch1 内容轮): 遍历 Skillbook.known，按各技能曲线据人物 level 写 level 字段。
}
```
并在角色 level_up / ready 处调用 `applySkillLevelCurve(entityId)`（即便 no-op，把调用点焊死，内容轮只改函数体）。

- [ ] **Step 4: 跑测试 + 回归**

Run: `cd backend && mvn test -Dtest=ResolveSpecTest#skillLevelSeam_isNoOpForNow`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/character/recalculate_hooks.js backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java
git commit -m "feat(ch1-f5): 人物升级→技能等级接缝(空 hook,焊死调用点)"
```

---

## Task 12: debug 给被动 / 设 level

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/debug/DebugController.java`
- Modify: `mods/base-rules/handlers/...`（新增 `action.debug_grant_passive` / `action.debug_set_skill_level` handler，或 debug REST → 事件）

- [ ] **Step 1: 决定入口形式**

沿用现有 debug 风格:`DebugController` 现有 `@GetMapping("/state/{entityId}")`。加两个 `@PostMapping`:
- `POST /api/debug/passive/{token}?base=<id>` — 给玩家加被动实例
- `POST /api/debug/skill-level/{token}?base=<id>&level=<n>` — 设某技能 level

Controller 解析 token→playerId,fire 一个引擎事件（`debug.grant_passive` / `debug.set_skill_level`),由 mod handler 改 `Skillbook.known` + 触发 `Passive.registerStatMods` + recalc。

- [ ] **Step 2: 写测试** — POST 给被动后,snapshot known 含该被动

加到 `SkillbookSnapshotTest`:
```java
@Test
@SuppressWarnings("unchecked")
void debugGrantPassive_addsToKnown() {
    String token = sessionService.createSession();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Session-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    rest.exchange("/api/action", HttpMethod.POST, new HttpEntity<>(Map.of(
            "type", "confirm_character",
            "params", Map.of("name", "debug被动", "class", "warrior")), headers), Map.class);
    rest.exchange("/api/debug/passive/" + token + "?base=iron_skin",
            HttpMethod.POST, new HttpEntity<>(headers), Map.class);
    ResponseEntity<Map> snap = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    Map<String, Object> skillbook = (Map<String, Object>) snap.getBody().get("skillbook");
    List<Map<String, Object>> known = (List<Map<String, Object>>) skillbook.get("known");
    assertThat(known.stream().anyMatch(s -> "iron_skin".equals(s.get("base")))).isTrue();
}
```

- [ ] **Step 3: 跑测试确认失败 → 实现 controller + handler → 通过**

实现要点:幂等(已持有不重复加)、加完 `persistence.save` + `Passive.registerStatMods` + recalc。

Run: `cd backend && mvn test -Dtest=SkillbookSnapshotTest#debugGrantPassive_addsToKnown`
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/epic/engine/debug/DebugController.java mods/base-rules/handlers/ \
  backend/src/test/java/com/epic/engine/snapshot/SkillbookSnapshotTest.java
git commit -m "feat(ch1-f5): debug 给被动/设技能等级端点"
```

---

## Task 13: 收尾全量验收

- [ ] **Step 1: 后端全量绿**

Run: `cd backend && mvn test`
Expected: PASS（全部，含三条通路测试 + 回归 + 快照）

- [ ] **Step 2: 前端构建**

Run: `cd frontend && npm run build`
Expected: 成功

- [ ] **Step 3: 真人 verify 清单**（交 PM）
  - 法师角色:技能书被动 tab 显示「铁壁 Lv1」「余烬 Lv1」(只读、无 equip 按钮)。
  - 属性面板:力量含 iron_skin +10（派生强度随之）。
  - 战斗放火球:灼烧持续 3 回合(余烬 +1)。
  - debug 给 `lifesteal_on_kill` 后击杀回血。
  - 被动**不出现**在战斗指令栏；出战计数仍只数主动。

- [ ] **Step 4: 更新 prompt.md**（下一轮交接,见 memory `feedback_update_prompt_md_on_wrapup`）：标 #5 完成、指向 #6 专精。

---

## 自检覆盖（spec → task 映射）

- spec §3 数据模型 → Task 2/5/9（实例 level、kind、统一 known）
- spec §4 被动 spec 格式三 archetype → Task 1/3/7/8（demo + 各通路）
- spec §5 合并管道 → Task 2/3/4（resolveSpec + 顺序 + passthrough 回归）
- spec §6.1 stat_mod → Task 7;§6.2 skill_patch → Task 3;§6.3 handler → Task 8
- spec §7 level 轴 + 接缝 → Task 4(缩放)/5(字段)/11(人物升级 hook)
- spec §8 前端 → Task 10
- spec §9 demo 夹具 → Task 1/3/8
- spec §10 验收 → Task 13;§11 边界(kind 感知/槽空实现) → Task 6/2
- spec §1 OUT(node/装备/专精空槽、怪物不碰) → Task 2 空实现 + 全程不加怪物被动内容
