# Ch1 内容设计轮 · 数值骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **本项目实际执行走团队工作流(Codex headless 实现)** —— 见末尾「执行交接」。

**Goal:** 为 Ch1 已搭好的成长系统填入跨职业、内容无关的数值骨架:中央调参板 `progression.yaml` + 指数 XP 曲线 + 技能等级派生曲线(`applySkillLevelCurve`)+ 球成本默认值,不填任何具体职业内容。

**Architecture:** 新建 `progression.yaml`(根目录数据表)+ `Progression` 全局 JS 库(懒加载缓存 + 访问器)。所有消费方(leveling / 技能等级曲线 / 进化扣球)改读 `Progression`。技能等级**不存储、每次 recalc 由 `tier`+当前人物级派生**写回 `Skillbook.known[i].level`,下游 `applyLevelScaling` 不动。

**Tech Stack:** Java 21 + Spring Boot + GraalJS;游戏逻辑在 `mods/base-rules/` JS handler + YAML;测试 = JUnit 启动 `ScriptRuntime` 跑 JS + Probe 组件断言(idiom 见 `backend/src/test/java/com/epic/engine/character/TalentTreeTest.java`)。

---

## 五要素(团队工作流必含)

**验收(含不做什么):**
- 做:`progression.yaml` 五块常量;`Progression` 库;`xpForLevel` 指数化;`gain_xp` 多级+cap;`debug.set_level` cap clamp + 触发曲线;`applySkillLevelCurve` 填真(主动+被动全 known);技能 `tier`/`level_curve` 约定 + 1 个 demo `level_scaling` 样例;球成本三处读默认 + demo 删显式 count。
- **不做:** 不填任何职业内容(专精树/天赋树节点/真被动/真技能数值表);不做球真供给(Ch3)/装备 patch(Ch2)/洗点收费(Ch5)/球技能(backlog);`passive_budget` 仅写进 yaml,**不接引擎**。

**修改边界:** 仅 `mods/base-rules/progression.yaml`(新)、`handlers/character/progression_lib.js`(新)、`handlers/character/leveling.js`、`handlers/character/recalculate_hooks.js`、`handlers/character/talent.js`、`handlers/ui/talent_tree.js`、`talents/elementalist.yaml`、`skills/fireball.yaml`(demo 样例),以及 `backend/src/test/.../ProgressionBackboneTest.java`(新)。不动引擎 Java、不动其它职业内容文件。

**验证方式:** `cd backend && mvn test`(全绿,新增 `ProgressionBackboneTest`);`npm run build`(前端回归,Claude 沙箱外跑);手动 `debug.set_level` 跳级观察技能 level 按 tier 爬升 + 满级封顶,`grant_orb`+进化扣 1 球。

**隐含产品偏好:** 等级=尺子/节奏器(升级偏快,长线功率在天赋/球/装);骨架是参数化框架非平衡值(真平衡 = 带内容 playtest + sim,本轮不做);常量占位但自洽(怪物成长本身脚手架)。

**现有代码约束:**
- 技能等级写回必须**早于** `Passive.registerStatMods`/`applySpec`(被动也吃 `known[i].level`,见 `04_passive_lib.js:38`)。loaded 路径现序已正确(`recalculate_hooks.js:99` 曲线 → `:110/112` 被动),保持。
- `Skillbook.known` 条目是 `java.util.Map`,用 `.put(k,v)` / `.get(k)`(参照 `talent.js:499`)。
- 读技能定义用 `Skill.loadSpecAny(base)` + `Skill._toJs`(参照 `04_passive_lib.js:36`)。
- `engine.loadYaml("progression.yaml")` 按需读,非 ModuleLoader 自动加载;HotReloader 重放脚本时 `Progression` 重声明→缓存自清。
- `Skillbook.known` 不增字段(`learnedAt` 已撤回);`level` 语义变派生值。
- **无 JS 日志 facility**(全代码无 `engine.log`)。spec 提的「未知 tier warn」无落点 → `resolveCurve` 对未知 tier **静默 fallback 到 default**(与现有 loader 容错惯例一致;tier 由作者控的 dev 内容,坏值在测试期即暴露)。不为此引入新日志通道。

---

## 文件职责

| 文件 | 职责 |
|---|---|
| `mods/base-rules/progression.yaml` | 五块可调常量(等级/技能等级/缩放预算/球成本/被动预算) |
| `mods/base-rules/handlers/character/progression_lib.js` | `Progression` 全局:懒加载缓存 + `xpForLevel`/`cap`/`skillLevelFor`/`orbCost` 访问器 |
| `handlers/character/leveling.js` | `xpForLevel` 改读 Progression;`gain_xp` 多级+cap;`debug.set_level` cap clamp + 调曲线 |
| `handlers/character/recalculate_hooks.js` | `applySkillLevelCurve` 填真 |
| `handlers/character/talent.js` | 进化扣球 + summary 文本读默认 count |
| `handlers/ui/talent_tree.js` | 快照进化成本读默认 count |
| `talents/elementalist.yaml` | demo:删进化节点显式 `count` |
| `skills/fireball.yaml` | demo:加 `tier` + 1 条 `level_scaling` 接缝样例 |
| `backend/src/test/.../ProgressionBackboneTest.java` | 新测试类 |

---

## Task 1: `progression.yaml` + `Progression` 库

**Files:**
- Create: `mods/base-rules/progression.yaml`
- Create: `mods/base-rules/handlers/character/progression_lib.js`
- Test: `backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java`

- [ ] **Step 1: 写 progression.yaml**

Create `mods/base-rules/progression.yaml`:

```yaml
# progression.yaml — Ch1 数值骨架调参板（内容无关，跨职业共享）
level:
  cap: 100
  xp_curve:
    base: 100
    exp: 1.5
skill_level:
  cap: 10
  default: smooth
  tiers:
    smooth:   { start: 1,  per: 2 }
    standard: { start: 20, per: 3 }
    chunky:   { start: 50, per: 5 }
scaling_budget:
  baseline_multiplier: 5.0
orb:
  cost_per_evolution: 1
passive_budget:
  unit_primary_stat: 10
  tiers: { minor: 1, normal: 2, major: 4 }
```

- [ ] **Step 2: 写 Progression 库**

Create `mods/base-rules/handlers/character/progression_lib.js`:

```javascript
// Ch1 数值骨架：progression.yaml 的懒加载访问层。所有成长/技能等级/球成本消费方读这里。
var Progression = {
  _cfg: null,
  cfg: function() {
    if (this._cfg === null) {
      var raw = engine.loadYaml("progression.yaml");
      this._cfg = (typeof Skill !== "undefined" && Skill && Skill._toJs) ? Skill._toJs(raw) : raw;
    }
    return this._cfg;
  },
  reset: function() { this._cfg = null; },
  cap: function() { return parseInt(this.cfg().level.cap); },
  xpForLevel: function(level) {
    var x = this.cfg().level.xp_curve;
    return Math.round(parseFloat(x.base) * Math.pow(level, parseFloat(x.exp)));
  },
  skillCap: function() { return parseInt(this.cfg().skill_level.cap); },
  // 解析一个技能 spec 的曲线参数：level_curve(对象覆盖/字符串选档) > tier > default
  resolveCurve: function(spec) {
    var sl = this.cfg().skill_level;
    var def = sl.tiers[sl.default];
    var lc = spec ? spec.level_curve : null;
    if (lc && typeof lc === "object") {
      return {
        start: parseInt(lc.start != null ? lc.start : def.start),
        per:   parseInt(lc.per   != null ? lc.per   : def.per)
      };
    }
    var name = (lc && typeof lc === "string") ? lc
             : (spec && spec.tier ? String(spec.tier) : sl.default);
    var t = sl.tiers[name] || def;
    return { start: parseInt(t.start), per: parseInt(t.per) };
  },
  skillLevelFor: function(charLevel, spec) {
    var p = this.resolveCurve(spec);
    var lv = 1 + Math.floor((charLevel - p.start) / p.per);
    var cap = this.skillCap();
    if (lv < 1) lv = 1;
    if (lv > cap) lv = cap;
    return lv;
  },
  orbCost: function() { return parseInt(this.cfg().orb.cost_per_evolution); }
};
```

- [ ] **Step 3: 写失败测试(已用 Codex 核实的真实 API:`engine.createEntity`/`newComponent`/`newList`/`newMap`/`newEvent`;无 `runtime.evalInt`;Map 取值要 cast)**

Create `backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java`:

```java
package com.epic.engine.progression;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionBackboneTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
        runtime = new ScriptRuntime(bus, store, chainService, typeReg);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        Path ch = Path.of("../mods/base-rules/handlers/character");
        runtime.execute(Files.readString(ch.resolve("progression_lib.js")), "progression_lib.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    /** 执行返回数字的 JS：写进临时实体的 Probe 组件，Java 端读回。 */
    long probe(String expr) {
        runtime.execute(
            "var __p = engine.newComponent('Probe'); __p.set('v', (" + expr + ")); " +
            "var __e = engine.createEntity('probe_holder'); __e.addComponent(__p); store.add(__e);",
            "probe.js");
        long v = store.get("probe_holder").getComponent("Probe").getInt("v");
        store.remove("probe_holder");
        return v;
    }

    @Test
    void xpCurve_isExponential() {
        assertThat(probe("Progression.xpForLevel(1)")).isEqualTo(100L);
        assertThat(probe("Progression.xpForLevel(4)")).isEqualTo(800L);
        assertThat(probe("Progression.xpForLevel(10)")).isEqualTo(3162L);
    }

    @Test
    void cap_is100() {
        assertThat(probe("Progression.cap()")).isEqualTo(100L);
    }
}
```

> API 已 Codex 核实:`engine.createEntity(id)`(只 `new Entity`,不自动入 store,故需 `store.add`)、`engine.newComponent`、`engine.newList`、`engine.newMap`、`engine.newEvent`、`store.remove(id)` 均存在;`Component.getInt` 走 `((Number)..).intValue()`。**Probe 组件值用 `getInt` 读没问题;但 `Skillbook.known` 是 `List<Map>`,Map 无 `getInt`,读 level 要显式 cast(见 Task 3 `skillLevelOf`)。**

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS(`xpCurve_isExponential`、`cap_is100`)。
xpForLevel 校验:`round(100*1^1.5)=100`、`round(100*4^1.5)=round(800)=800`、`round(100*10^1.5)=round(3162.27)=3162`。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/progression.yaml mods/base-rules/handlers/character/progression_lib.js backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java
git commit -m "feat(ch1-backbone): progression.yaml 调参板 + Progression 访问库"
```

---

## Task 2: `xpForLevel` 指数化 + `gain_xp` 多级升级 + cap

**Files:**
- Modify: `mods/base-rules/handlers/character/leveling.js:1-3`(xpForLevel)、`:60-93`(gain_xp)
- Test: `backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java`

- [ ] **Step 1: 写失败测试(gain_xp 多级 + cap)**

加进 `ProgressionBackboneTest`(setUp 末尾追加加载 leveling.js,并 stub 掉重依赖):

在 `setUp()` 末尾追加:

```java
        runtime.execute(Files.readString(ch.resolve("leveling.js")), "leveling.js");
        // stub 掉成长 modifier 与持久化，隔离 gain_xp 的 XP/等级数学。
        // 这些单测 runtime 未 bindService('persistence')，故直接在全局定义即可（每个测试新建 runtime，互不影响）。
        // registerLevelGrowthModifier 是 leveling.js 里的 function 声明，可被全局重新赋值覆盖。
        runtime.execute(
            "registerLevelGrowthModifier = function(){}; " +
            "var persistence = { save: function(){} };",
            "stubs.js");
```

> 注:Task 4 会让 `debug.set_level` 在 `applySpec` 前调 `applySkillLevelCurve`,而 `applySpec` 此处也未加载 → 在 setUp 同一 `stubs.js` 串里再追加 `applySpec = function(){};`(见 Task 4 Step 1)。`applySkillLevelCurve` 由 Task 3 加载 `recalculate_hooks.js` 提供。

新增测试方法:

```java
    /** 造一个带 Experience+Character 的实体，存进 store，返回 id。 */
    void makeHero(String id, int level, int xp) {
        runtime.execute(
            "var e = engine.createEntity('" + id + "'); " +
            "var exp = engine.newComponent('Experience'); exp.set('level', " + level + "); exp.set('xp', " + xp + "); e.addComponent(exp); " +
            "var ch = engine.newComponent('Character'); ch.set('level', " + level + "); ch.set('classId', 'mage'); e.addComponent(ch); " +
            "store.add(e);",
            "mkhero.js");
    }

    void fireGainXp(String id, int amount) {
        runtime.execute(
            "var ev = engine.newEvent('action.gain_xp'); ev.set('playerId', '" + id + "'); ev.set('amount', " + amount + "); " +
            "engine.fire('action.gain_xp', ev);",
            "gainxp.js");
    }

    @Test
    void gainXp_multiLevel_singleCall() {
        makeHero("h1", 1, 0);
        // L1→2 需 100, L2→3 需 round(100*2^1.5)=283, 共 383；给 400 应到 3 级
        fireGainXp("h1", 400);
        assertThat(store.get("h1").getComponent("Experience").getInt("level")).isEqualTo(3);
    }

    @Test
    void gainXp_clampsAtCap() {
        makeHero("h2", 99, 0);
        fireGainXp("h2", 999999999);
        assertThat(store.get("h2").getComponent("Experience").getInt("level")).isEqualTo(100);
        assertThat(store.get("h2").getComponent("Experience").getInt("xp")).isEqualTo(0);
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest#gainXp_multiLevel_singleCall+gainXp_clampsAtCap`
Expected: FAIL —— 现 `gain_xp` 是 `if` 单次升级,400 只升到 2 级;99 级给海量 XP 也只升到 100 但 xp 不归零/或溢出。

- [ ] **Step 3: 改 xpForLevel**

`leveling.js:1-3` 替换:

```javascript
function xpForLevel(level) {
    return (typeof Progression !== "undefined") ? Progression.xpForLevel(level) : level * 100;
}
```

- [ ] **Step 4: 改 gain_xp(`leveling.js:60-93` 整个 handler 体)**

把 `engine.on("action.gain_xp", 100, function(event){ ... })` 的函数体替换为:

```javascript
    var playerId = event.get("playerId");
    var amount = event.get("amount");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null) return;

    var currentXp = exp.getInt("xp") + amount;
    var currentLevel = exp.getInt("level");
    var cap = (typeof Progression !== "undefined") ? Progression.cap() : 100;

    var leveled = false;
    while (currentLevel < cap && currentXp >= xpForLevel(currentLevel)) {
        currentXp -= xpForLevel(currentLevel);
        currentLevel++;
        leveled = true;
    }
    if (currentLevel >= cap) { currentLevel = cap; currentXp = 0; }

    exp.set("level", currentLevel);
    exp.set("xp", currentXp);
    var charComp = player.getComponent("Character");
    if (charComp !== null) charComp.set("level", currentLevel);

    if (leveled) {
        registerLevelGrowthModifier(playerId, currentLevel);
        var levelUpEvent = engine.newEvent("entity.level_up");
        levelUpEvent.set("entity", player);
        levelUpEvent.set("level", currentLevel);
        engine.fire("entity.level_up", levelUpEvent);
    }

    persistence.save(player);
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS(全部)。

- [ ] **Step 6: Commit**

```bash
git add mods/base-rules/handlers/character/leveling.js backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java
git commit -m "feat(ch1-backbone): xpForLevel 指数化 + gain_xp 多级升级与 cap 封顶"
```

---

## Task 3: `applySkillLevelCurve` 填真

**Files:**
- Modify: `mods/base-rules/handlers/character/recalculate_hooks.js:123-125`
- Test: `ProgressionBackboneTest`

- [ ] **Step 1: 写失败测试**

setUp 末尾追加加载被动库(`applySkillLevelCurve` 不依赖它,但保持与运行时一致;同时 recalculate_hooks 里有 `Talent`/`Passive` 守卫,本测试只单独调函数,故只需 `00_skill_lib.js` 已加载):

加载 `recalculate_hooks.js`(需在 leveling 之后):

```java
        runtime.execute(Files.readString(ch.resolve("recalculate_hooks.js")), "recalculate_hooks.js");
```

新增测试:

```java
    void makeHeroWithSkill(String id, int charLevel, String base) {
        runtime.execute(
            "var e = engine.createEntity('" + id + "'); " +
            "var ch = engine.newComponent('Character'); ch.set('level', " + charLevel + "); ch.set('classId','mage'); e.addComponent(ch); " +
            "var sb = engine.newComponent('Skillbook'); " +
            "var known = engine.newList(); " +
            "var k = engine.newMap(); k.put('base','" + base + "'); k.put('level', 1); known.add(k); " +
            "sb.set('known', known); e.addComponent(sb); " +
            "store.add(e);",
            "mkheroskill.js");
    }

    @SuppressWarnings("unchecked")
    long skillLevelOf(String id) {
        List<Map<String, Object>> known =
            store.get(id).getComponent("Skillbook").get("known");
        return ((Number) known.get(0).get("level")).longValue();
    }

    // 用注入的合成 spec 解耦真实技能（避免依赖 fireball 的 tier，且确保「先失败」不假绿）
    @Test
    void curve_respectsTier_chunkyLateGame() {
        runtime.execute("Skill._specs['probe_chunky'] = { id:'probe_chunky', tier:'chunky' };", "inject.js");
        makeHeroWithSkill("c1", 60, "probe_chunky");
        runtime.execute("applySkillLevelCurve('c1');", "curve.js");
        // chunky(start50,per5)@60 = 1+floor((60-50)/5)=3（no-op 现状会得 1 → 失败）
        assertThat(skillLevelOf("c1")).isEqualTo(3L);
    }

    @Test
    void curve_respectsTier_smoothMid() {
        runtime.execute("Skill._specs['probe_smooth'] = { id:'probe_smooth', tier:'smooth' };", "inject.js");
        makeHeroWithSkill("c2", 10, "probe_smooth");
        runtime.execute("applySkillLevelCurve('c2');", "curve.js");
        // smooth(start1,per2)@10 = 1+floor(9/2)=5（no-op 现状会得 1 → 失败）
        assertThat(skillLevelOf("c2")).isEqualTo(5L);
    }
```

> **为何注入合成 spec:** `Skill.loadSpecAny` 先查 `Skill._specs` 缓存,注入即命中,返回我们指定 `tier` 的对象(`Skill._toJs` 对纯 JS 对象原样返回)。两个断言值(3、5)都≠初始 1,故 no-op 现状必失败,杜绝假绿;且与 fireball 后续改 `tier` 无耦合。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest#curve_respectsTier_chunkyLateGame+curve_respectsTier_smoothMid`
Expected: FAIL —— 现 `applySkillLevelCurve` 是 no-op,level 维持 1(≠3、≠5)。

- [ ] **Step 3: 填 applySkillLevelCurve(`recalculate_hooks.js:123-125`)**

替换:

```javascript
function applySkillLevelCurve(entityId) {
    var entity = store.get(entityId);
    if (entity === null) return;
    if (!entity.hasComponent("Skillbook")) return;
    var charLevel = 1;
    var ch = entity.getComponent("Character");
    if (ch !== null) charLevel = ch.getInt("level");
    var known = entity.getComponent("Skillbook").get("known");
    if (known === null) return;
    for (var i = 0; i < known.size(); i++) {
        var entry = known.get(i);
        var base = String(entry.get("base"));
        var raw = (typeof Skill !== "undefined") ? Skill.loadSpecAny(base) : null;
        var spec = (raw !== null && raw !== undefined) ? Skill._toJs(raw) : null;
        var lv = (typeof Progression !== "undefined") ? Progression.skillLevelFor(charLevel, spec) : 1;
        entry.put("level", lv);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS。`curve_respectsTier_chunkyLateGame`:`1+floor((60-50)/5)=3`;`curve_respectsTier_smoothMid`:`1+floor((10-1)/2)=5`。

> 两测试用注入合成 spec,与任何真实技能解耦,可独立通过,不依赖 Task 5。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/character/recalculate_hooks.js backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java
git commit -m "feat(ch1-backbone): applySkillLevelCurve 据 tier+人物级派生写 known level"
```

---

## Task 4: `debug.set_level` cap clamp + 触发曲线

**Files:**
- Modify: `mods/base-rules/handlers/character/leveling.js:39-58`
- Test: `ProgressionBackboneTest`

- [ ] **Step 1: 写失败测试**

```java
    @Test
    void debugSetLevel_clampsToCap() {
        makeHero("d1", 1, 0);
        runtime.execute(
            "var ev = engine.newEvent('debug.set_level'); ev.set('entityId','d1'); ev.set('level', 250); " +
            "engine.fire('debug.set_level', ev);",
            "setlevel.js");
        assertThat(store.get("d1").getComponent("Experience").getInt("level")).isEqualTo(100);
    }
```

> `debug.set_level` 内部调 `applySpec` 或 `registerLevelGrowthModifier`,后者需 `schemas`/`effectiveGrowth`。测试 setUp 已 stub `registerLevelGrowthModifier`;若 `applySpec` 存在且重,额外 stub:在 setUp stubs.js 追加 `applySpec = function(){};`。本测试只验 cap clamp 写进 Experience.level,不验属性成长。

setUp 的 `stubs.js` 追加一行:

```java
            "applySpec = function(id){ };",
```

(合进同一 `runtime.execute` 字符串)

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest#debugSetLevel_clampsToCap`
Expected: FAIL —— 现 `debug.set_level` 不 clamp,level=250。

- [ ] **Step 3: 改 debug.set_level(`leveling.js:39-58`)**

**关键时序(Codex 阻塞项):** 被动 modifier 在 `04_passive_lib.js:135` 把 `level` **烤进闭包** `deltas[k]=mods[k]*level` 后 `addModifier`,单纯 `engine.recalculate` 不会用新 level 重建。故 `applySkillLevelCurve` 必须在 `applySpec`(内部 `Passive.registerStatMods`)**之前**跑,让被动按新派生 level 注册。

当前 handler 体(`:42` 起):

```javascript
    var entity = store.get(entityId);
    if (entity === null || level < 1) { event.set("ok", false); return; }
    var exp = entity.getComponent("Experience");
    if (exp !== null) exp.set("level", level);
    var ch = entity.getComponent("Character");
    if (ch !== null) ch.set("level", level);
    if (typeof applySpec !== "undefined") {
        applySpec(entityId);
    } else if (typeof registerLevelGrowthModifier !== "undefined") {
        registerLevelGrowthModifier(entityId, level);
        engine.recalculate(entityId);
    }
```

整段替换为(加 cap clamp + 在 applySpec 前调曲线):

```javascript
    var entity = store.get(entityId);
    if (entity === null || level < 1) { event.set("ok", false); return; }
    var __cap = (typeof Progression !== "undefined") ? Progression.cap() : 100;
    if (level > __cap) level = __cap;
    var exp = entity.getComponent("Experience");
    if (exp !== null) exp.set("level", level);
    var ch = entity.getComponent("Character");
    if (ch !== null) ch.set("level", level);
    if (typeof applySkillLevelCurve !== "undefined") applySkillLevelCurve(entityId);
    if (typeof applySpec !== "undefined") {
        applySpec(entityId);
    } else if (typeof registerLevelGrowthModifier !== "undefined") {
        registerLevelGrowthModifier(entityId, level);
        engine.recalculate(entityId);
    }
```

handler 末尾(`var hp = ...`/`persistence.save`)**保持不动**。

> 生产 loaded 路径(`recalculate_hooks.js:99` 曲线 → `:110/112` 被动)本就此序,无需改。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/character/leveling.js backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java
git commit -m "feat(ch1-backbone): debug.set_level cap clamp + 跳级后刷新技能等级曲线"
```

---

## Task 5: 技能 `tier`/`level_curve` 约定 + demo `level_scaling` 样例

**Files:**
- Modify: `mods/base-rules/skills/fireball.yaml`
- Test: `ProgressionBackboneTest`

- [ ] **Step 1: 写失败测试(level_scaling 随 skillLevel 生效 + tier 选档)**

验证两点:(a) fireball 标 `tier: standard` 后,曲线按 standard 档而非默认 smooth 算;(b) `Skill.resolveSpec` 在 level=10 时把 `damage.add` 按 `level_scaling` 抬高。

```java
    @Test
    void fireball_tierStandard_usesStandardCurve() {
        // 用 level 30 区分:无 tier(fallback smooth)@30 = 1+floor(29/2)=10；
        // 标 standard(start20,per3)@30 = 1+floor(10/3)=4。改前得 10、改后得 4。
        makeHeroWithSkill("t1", 30, "fireball");
        runtime.execute("applySkillLevelCurve('t1');", "curve.js");
        assertThat(skillLevelOf("t1")).isEqualTo(4L);
    }

    @Test
    void fireball_levelScaling_raisesDamage() {
        // resolveSpec 读 known level → applyLevelScaling。standard@50=cap10；lv10 damage.add=10+5*9
        makeHeroWithSkill("t2", 50, "fireball");
        runtime.execute("applySkillLevelCurve('t2');", "curve.js");
        runtime.execute(
            "var caster = store.get('t2'); var ctx = { caster: caster }; " +
            "var base = Skill._toJs(Skill.loadSpecAny('fireball')); " +
            "var spec = Skill.resolveSpec(ctx, 'fireball', base); " +
            "var p = engine.newComponent('Probe'); p.set('v', Math.round(spec.damage.add)); " +
            "var pe = engine.createEntity('probe_holder'); pe.addComponent(p); store.add(pe);",
            "resolve.js");
        long dmg = store.get("probe_holder").getComponent("Probe").getInt("v");
        store.remove("probe_holder");
        // lv10、delta=5 → 10 + 5*9 = 55
        assertThat(dmg).isEqualTo(55L);
    }
```

> `resolveSpec(ctx, base, baseSpec)` 签名见 `00_skill_lib.js:171`;内部 `ownerInstance` 读 caster 的 Skillbook.known level。本测试 caster t2 的 fireball known level 已被曲线设为 10。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest#fireball_tierStandard_usesStandardCurve+fireball_levelScaling_raisesDamage`
Expected: FAIL —— 改前 fireball 无 `tier`(fallback smooth,@30 得 10≠4)、无 `level_scaling`(damage.add 维持 10≠55)。

- [ ] **Step 3: 改 fireball.yaml**

`skills/fireball.yaml` 在 `damage:` 块同级加 `tier` 与 `level_scaling`。现有:

```yaml
damage:
  type: 法术
  add: 10
  scaling: { 智力: 0.2 }
```

在文件顶部字段区(`category: skill` 之后)加一行:

```yaml
tier: standard
```

并在 `damage:` 块**之后**(同级)加:

```yaml
level_scaling:
  "damage.add": 5
```

> `level_scaling` 的 key 用点路径(见 `applyLevelScaling` → `_applyPatchValue`,`00_skill_lib.js:181`),`"damage.add"` 命中 `damage.add`。delta=5 → lv1→lv10 = 10→55(≈×5.5,落在 ×5 预算附近;精确值后续 playtest 调)。这是**接缝验证样例,非内容铺开**。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS。(a) standard@30 = `1+floor((30-20)/3)=4` ✓;(b) standard@50=cap10,damage.add lv10 = `10+5*(10-1)=55` ✓。

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/skills/fireball.yaml backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java
git commit -m "feat(ch1-backbone): 技能 tier/level_curve 约定 + fireball level_scaling 接缝样例"
```

---

## Task 6: 球进化成本读默认 + demo 删显式 count

**Files:**
- Modify: `mods/base-rules/handlers/character/talent.js:387`(summary)、`:484`(扣除)
- Modify: `mods/base-rules/handlers/ui/talent_tree.js:53`(快照成本)
- Modify: `mods/base-rules/talents/elementalist.yaml:19,37`
- Test: `ProgressionBackboneTest`(新断言)
- **回归测试同步(Codex 阻塞项):** `backend/src/test/java/com/epic/engine/character/TalentTreeTest.java:309,350`、`backend/src/test/java/com/epic/engine/snapshot/TalentTreeSnapshotTest.java:123` —— 默认成本由 2 变 1,这些既有断言必须同步。

- [ ] **Step 1: 写失败测试**

直接验解析规则:节点 orb 无 count 时取 `Progression.orbCost()`(=1),有 count 时取显式值。用一个小 JS 函数验证(实现里会抽成共享 helper `Talent.orbCount`,见 Step 3):

```java
    @Test
    void orbCost_defaultsWhenMissing() {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        long missing = probe("Talent.orbCount({ type: '爆破' })");
        long explicit = probe("Talent.orbCount({ type: '爆破', count: 3 })");
        assertThat(missing).isEqualTo(1L);
        assertThat(explicit).isEqualTo(3L);
    }
```

> 若 talent.js 加载需额外依赖(它 `engine.on(...)` 注册多个 handler,通常可独立加载),按报错补依赖。`probe(...)` helper 已在 Task 1 Step 3b 定义。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest#orbCost_defaultsWhenMissing`
Expected: FAIL —— `Talent.orbCount` 尚不存在。

- [ ] **Step 3: 在 talent.js 加共享 helper 并改三处读点**

在 `Talent` 对象内(任意方法旁)加:

```javascript
  orbCount: function(orb) {
    if (orb && orb.count != null) return parseInt(orb.count);
    return (typeof Progression !== "undefined") ? Progression.orbCost() : 1;
  },
```

`talent.js:387` 现为:

```javascript
      return "主动增强 " + s.skill + " -> " + s.evolution + "; 灵魂球 " + orb.type + " x" + orb.count;
```

改为:

```javascript
      return "主动增强 " + s.skill + " -> " + s.evolution + "; 灵魂球 " + orb.type + " x" + Talent.orbCount(orb);
```

`talent.js:484` 现为:

```javascript
  var count = parseInt(orb.count || 0);
```

改为:

```javascript
  var count = Talent.orbCount(orb);
```

- [ ] **Step 4: 改 talent_tree.js 快照成本(`:53`)**

`talent_tree.js:48-53` 现为(片段):

```javascript
      var orb = s.orb || {};
      ...
        parseInt(orb.count || 0),
```

把 `parseInt(orb.count || 0)` 改为 `(typeof Talent !== "undefined" ? Talent.orbCount(orb) : parseInt(orb.count || 1))`。

> talent_tree.js 与 talent.js 同 mod 同运行时,`Talent` 全局可用。

- [ ] **Step 5: 改 elementalist.yaml 删显式 count**

`talents/elementalist.yaml:19`:

```yaml
    skill_slot: { skill: fireball, orb: { type: 爆破, count: 2 }, evolution: pyroblast }
```

改为:

```yaml
    skill_slot: { skill: fireball, orb: { type: 爆破 }, evolution: pyroblast }
```

`talents/elementalist.yaml:37`:

```yaml
    skill_slot: { skill: light_field, orb: { type: 多重, count: 1 }, evolution: prism_field }
```

改为:

```yaml
    skill_slot: { skill: light_field, orb: { type: 多重 }, evolution: prism_field }
```

- [ ] **Step 6: 同步既有回归测试(默认成本 2→1)**

(a) `TalentTreeTest.java:309` 现为(grant 2 颗 → 花 cost 后断言剩 0):

```java
            store.get('hero').getComponent('OrbPouch').set('爆破', 2);
```

改为(grant 1 颗,保持「恰好花光 → 余 0」语义,`:350` 的 `orbLeft==0` 断言不变):

```java
            store.get('hero').getComponent('OrbPouch').set('爆破', 1);
```

(b) `TalentTreeSnapshotTest.java:123` 现为(火球槽**进化成本**展示):

```java
        assertThat(fireballSlot.path("slot").path("orbCount").asInt()).isEqualTo(2);
```

改为:

```java
        assertThat(fireballSlot.path("slot").path("orbCount").asInt()).isEqualTo(1);
```

> **只改「成本」断言,别动「背包存量」断言:** 同文件 `:91`/`:113` 的 `orbInventory ... count==2` 是玩家**持有**的球数(与成本无关),保持 2 不变。改前先确认该处确实是 `slot.orbCount`(进化成本)而非 inventory。

- [ ] **Step 7: 跑测试 + 全量回归**

Run: `cd backend && mvn test -Dtest=ProgressionBackboneTest`
Expected: PASS(`orbCost_defaultsWhenMissing`)。
Run: `cd backend && mvn test`
Expected: 全绿(`TalentTreeTest`/`TalentTreeSnapshotTest`/`SpecializationTest` 等无回归)。

- [ ] **Step 8: Commit**

```bash
git add mods/base-rules/handlers/character/talent.js mods/base-rules/handlers/ui/talent_tree.js mods/base-rules/talents/elementalist.yaml backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java backend/src/test/java/com/epic/engine/character/TalentTreeTest.java backend/src/test/java/com/epic/engine/snapshot/TalentTreeSnapshotTest.java
git commit -m "feat(ch1-backbone): 进化成本缺省读 orb.cost_per_evolution（修 missing→0 免费）"
```

---

## Task 7: 前端回归 + 收尾

- [ ] **Step 1: 前端 build(Claude 沙箱外)**

Run: `cd frontend && npm run build`
Expected: 通过(本轮无前端逻辑改动,纯回归;`talent_tree.js` 改的是后端快照字段,前端只消费)。

- [ ] **Step 2: 手动 verify 清单(交用户)**
- `debug.set_level` 跳到 5/20/50/100,看某技能 level 按其 tier 爬升、满级封顶。
- `grant_orb` 给 1 颗对应球 → 进化技能槽成功且扣 1 球;给 0 颗时进化被拒。
- 进化成本在天赋树 UI 显示为 1(elementalist 两个槽)。

- [ ] **Step 3: 全量绿确认后,更新 prompt.md(下一轮新 prompt,见 memory `feedback-update-prompt-md-on-wrapup`)**

---

## Self-Review

**Spec coverage:**
- §3.1 等级主轴 → Task 2(xpForLevel/gain_xp)+ Task 4(debug.set_level cap)✓
- §3.2 技能等级曲线 → Task 1(Progression.skillLevelFor)+ Task 3(applySkillLevelCurve)+ Task 4(debug 触发)+ Task 5(tier 约定)✓
- §3.3 缩放预算 → Task 5(level_scaling 样例 + ×5 校验)✓
- §3.4 球成本 → Task 6 ✓
- §3.5 被动预算(仅 yaml,不接引擎)→ Task 1(写进 progression.yaml)✓
- §4 接缝表全部覆盖;`debug.set_skill_level` 兼容(被覆写)= 设计声明,无需代码,已在 spec 注明,无 task ✓
- backlog(球技能)= 不做 ✓

**Placeholder scan:** 无 TBD/TODO;测试代码均给出具体断言值;实现代码均完整。对 ScriptRuntime/EntityStore 便捷 API 的不确定处,统一指向「按 TalentTreeTest 实际 idiom 调整」并给 Probe 版兜底 —— 这是对现有测试 API 的合理引用,非占位。

**Type consistency:** `Progression.skillLevelFor(charLevel, spec)`、`Progression.orbCost()`、`Talent.orbCount(orb)`、`applySkillLevelCurve(entityId)` 在定义与调用处签名一致;`known[i]` 用 `.get/.put`(Map)贯穿一致。

---

## 执行交接(团队工作流)

本项目不走 Claude 内 subagent/inline 执行,而是 **Claude orchestrate Codex**:
1. **Codex plan review**(本 plan)→ 纳阻塞项 → 提交。
2. **Codex exec 派实现**(`codex-runs/ch1-numeric-backbone/`),Claude review diff + 独立跑 `mvn test`/`npm run build`(不只信 Codex 报告)。
3. 报用户 verify → approve → Claude 圈文件提交。

下一步:对本 plan 跑 Codex plan review。
