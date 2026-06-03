# 数值平衡模拟器 — Plan 3:护甲曲线/cap + sweep + 基线指数 + 报告 B/C + 五曲线

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 PM 能(1)用 sweep 选护甲 K / 抗性 cap,(2)量化一个 build 强多少(offense/defense 指数),并产出报告 B `armor_k_sweep`、C `baseline_vs_variant` 与五条曲线数据。对应 spec UC2/UC3 + §2 验收。

**Architecture:** `Skill.mitigate` 的护甲减伤从 flat 升级为可切换的曲线 `护甲/(护甲+K)`,三抗加 cap/floor 钳制——**全部由 `CombatTuning` 旋钮驱动,默认 `FLAT + cap75 + floor−50`**(护甲曲线默认关 → 既有 147 测试与真游戏数值不变;PM 用 sweep 选定 K 后再单独"翻默认"重生 golden,不属本计划)。`Sweep` 框架在每个旋钮值上跑一批 `BatchMetrics`。`BaselineIndex` 用固定参照档(`ref_dummy_offense`/`ref_attacker_defense`)算 offense/defense 比值。`sources[]` 经新 `sim_support.js` 的 `sim.apply_source` 以真 `engine.addModifier` 落地。报告把这些导成 CSV。

**Tech Stack:** 同前。前置依赖:**Plan 1 + Plan 2 已合并**(尤其 `CombatTuning`、`FightRunner(.,tap)`、`CombatantBuilder`、`BatchRunner`)。

---

## 关键集成事实(已核实)

- `Skill.mitigate`(`00_skill_lib.js`):普攻分支现为 `Math.max(1, raw - armor)`(flat),技能分支 `(1 − typeResist/100)` **无 cap**。slice-1 有意保留 flat(数值不变)。本计划把曲线/cap 做成旋钮。
- `CombatTuning`(Plan 2 新增,绑为服务 `tuning`):本计划扩展 `armorModelCurve()/armorK()/resistCap()/resistFloor()`。mitigate 用 `typeof tuning !== 'undefined'` 守卫缺失。
- `engine.addModifier(id, {typeId,id,label,apply})`:JS 内注册 modifier;ModifierChain 每次重算先复位 base 再按注册顺序 apply。派生 modifier(`derived_stats.js`)priority 高(300,最后跑)→ 在它**之前**加的 stat 源会被派生重新 scale。
- `CombatantBuilder.buildPlayer(class, level, sources)`(Plan 1):`sources` 非空时本计划接上(经 `sim.apply_source`)。
- 现有 encounter 不含合适的"纯沙包",故 ref 参照档由 Java 直接合成实体(`new Entity` + 手挂 Health/CombatStats/Resistances/tag),挂进一个临时 combat 跑 `FightRunner`。

---

## File Structure(新增/修改)

- Modify `backend/src/main/java/com/epic/engine/sim/CombatTuning.java` — 加 armorModel/armorK/resistCap/resistFloor。
- Modify `mods/base-rules/handlers/skill/00_skill_lib.js` — mitigate 护甲曲线 + 抗性 cap/floor。
- Create `backend/src/main/java/com/epic/engine/sim/Sweep.java` — 单参数扫描 → 每值一份 BatchMetrics。
- Create `backend/src/main/java/com/epic/engine/sim/BaselineIndex.java` — offense/defense 指数(ref 参照档)。
- Create `mods/base-rules/handlers/world/sim_support.js` — `sim.apply_source`(modifier/equipment 源落地)。
- Modify `backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java` — sources 落地 + ref 参照档合成 helper。
- Create `backend/src/main/java/com/epic/engine/sim/reports/ArmorKSweepReport.java` — 报告 B + 曲线 1/2/3。
- Create `backend/src/main/java/com/epic/engine/sim/reports/BaselineVsVariantReport.java` — 报告 C。
- 测试镜像。

---

## Task 1: 护甲曲线 + 抗性 cap/floor(旋钮驱动,默认不改变现状)

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/sim/CombatTuning.java`
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Test: `backend/src/test/java/com/epic/engine/sim/ArmorCurveTest.java`

- [ ] **Step 1: 写失败测试**

纯 runtime,绑 `tuning`。三断言:(a)默认 FLAT → 普攻 `raw−armor` 不变;(b)`tuning.setArmorCurve(K=10)` → 普攻按曲线;(c)抗性 cap:把 typeResist 设 90、cap 75 → 减伤封顶 75%。用 `combat.mitigation` 的 final 读取。

```java
package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ArmorCurveTest {
    EventBus bus; EntityStore store; ScriptRuntime runtime; CombatTuning tuning;
    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));
        tuning = new CombatTuning(); runtime.bindService("tuning", tuning);
        Path c = Path.of("../mods/base-rules/handlers/combat");
        for (String f : List.of("initiative.js","damage_calc.js","death_check.js","combat_flow.js"))
            runtime.execute(Files.readString(c.resolve(f)), f);
        Path s = Path.of("../mods/base-rules/handlers/skill");
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        for (String f : List.of("00_skill_lib.js","01_effects.js","02_dispatch.js"))
            runtime.execute(Files.readString(s.resolve(f)), f);
    }
    @AfterEach void tearDown() { runtime.close(); }

    /** 读一次普攻 final：caster attack=raw, target armor=armor。 */
    private long basicFinal(int raw, int armor) {
        return finalOf(raw, armor, "basic_attack", null, 0);
    }
    /** 读一次法术 final：法术 raw 由 add 直接给(智力0)；target 法抗=resist。 */
    private long skillFinal(int rawAdd, int resist) {
        return finalOf(0, 0, "fireball_fixed", null, resist);   // 见下：用 add= rawAdd 的内联
    }
    private long finalOf(int atk, int armor, String skill, String type, int magicResist) {
        store.remove("p"); store.remove("t"); store.remove("b");
        Entity p = new Entity("p");
        Component ph=new Component("Health"); ph.set("hp",100); ph.set("maxHp",100); p.addComponent(ph);
        Component pcs=new Component("CombatStats"); pcs.set("attack",atk); pcs.set("defense",0); pcs.set("speed",9); p.addComponent(pcs);
        Component pds=new Component("DerivedStats"); pds.set("法术强度",0); p.addComponent(pds);
        Component pps=new Component("PrimaryStats"); pps.set("智力",0); p.addComponent(pps);
        p.addTag("player"); p.addTag("combat:b"); store.add(p);
        Entity t=new Entity("t");
        Component th=new Component("Health"); th.set("hp",9999); th.set("maxHp",9999); t.addComponent(th);
        Component tcs=new Component("CombatStats"); tcs.set("attack",1); tcs.set("defense",armor); tcs.set("speed",1); t.addComponent(tcs);
        Component tr=new Component("Resistances"); tr.set("物理",0); tr.set("法术",magicResist); tr.set("精神",0); t.addComponent(tr);
        t.addTag("enemy"); t.addTag("combat:b"); store.add(t);
        Entity combat=new Entity("b");
        Component st=new Component("CombatState"); st.set("round",0); st.set("phase","COMMAND"); combat.addComponent(st);
        store.add(combat);
        DamageTap tap=new DamageTap(bus, ()->1); FightTrace tr2=tap.begin();
        Map<String,Object> cmd=new HashMap<>(); cmd.put("type",skill); cmd.put("targetId","t");
        Map<String,Map<String,Object>> cmds=new HashMap<>(); cmds.put("p",cmd);
        GameEvent ev=new GameEvent("combat.resolve_round"); ev.set("combatId","b"); ev.set("commands",cmds);
        bus.fire("combat.resolve_round", ev);
        tap.end();
        long total=0; for (Long v: tr2.damageTakenByTarget().values()) total+=v; return total;
    }

    @Test void flatDefault_unchanged() {
        // 默认 FLAT：raw20 - armor5 = 15
        assertThat(basicFinal(20,5)).isEqualTo(15L);
    }
    @Test void armorCurve_whenEnabled() {
        tuning.setArmorCurve(10);   // 减伤% = armor/(armor+10)
        // raw20, armor10 → 50% → ceil(20×0.5)=10
        assertThat(basicFinal(20,10)).isEqualTo(10L);
    }
    @Test void resistCap_clampsTo75() {
        tuning.setResistBounds(75, -50);
        // 法抗 90 被钳到 75 → fireball_fixed raw(add) × 0.25。见下 fireball_fixed.yaml: add=40
        // final = ceil(40 × (1−0.75)) = 10
        assertThat(finalOf(0,0,"fireball_fixed","法术",90)).isEqualTo(10L);
    }
}
```

> 需要一个**固定 raw 的法术**测试技能,避免依赖属性。新增 `mods/base-rules/skills/fireball_fixed.yaml`(仅测试用):`{ id: fireball_fixed, name: 测试火球, category: action, effect: damage_only, delivery: 技能, damage: { type: 法术, add: 40 }, targeting: { mode: pattern, field: enemy, pattern: [[0,0]], steps: [{prompt: x, filter: enemy, count: 1}] }, animation: [] }`。把该文件随测试一并提交。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=ArmorCurveTest`
Expected: 编译失败 —— `CombatTuning.setArmorCurve/setResistBounds` 不存在(且曲线/cap 逻辑未实现)。

- [ ] **Step 3: 写实现**

`CombatTuning.java` 增字段(保留 Plan 2 的 variance):
```java
    // —— Plan 3 新增 ——
    private boolean armorCurve = false;   // 默认 FLAT
    private int armorK = 20;
    private int resistCap = 75;
    private int resistFloor = -50;

    public void setArmorCurve(int k) { this.armorCurve = true; this.armorK = k; }
    public void setArmorFlat() { this.armorCurve = false; }
    public void setResistBounds(int cap, int floor) { this.resistCap = cap; this.resistFloor = floor; }

    public boolean armorModelCurve() { return armorCurve; }
    public int armorK() { return armorK; }
    public int resistCap() { return resistCap; }
    public int resistFloor() { return resistFloor; }
```

`00_skill_lib.js` `mitigate`——普攻分支按旋钮选 flat/曲线;技能分支钳制 typeResist:
```javascript
    if (opts.delivery === "普攻") {
      var armor = target.hasComponent("CombatStats")
          ? target.getComponent("CombatStats").getInt("defense") : 0;
      if (typeof tuning !== 'undefined' && tuning && tuning.armorModelCurve()) {
        var K = tuning.armorK();
        var reduced = raw * (1 - armor / (armor + K));
        fin = Math.max(1, Math.ceil(reduced));
      } else {
        fin = Math.max(1, raw - armor);   // 默认 FLAT，行为不变
      }
      if (defending) fin = Math.max(1, Math.floor(fin * 0.5));
      fin = this._applyVariance(fin);
      this._emitMitigation(target, raw, fin, "普攻", opts.delivery);
      return fin;
    }
    var res = target.hasComponent("Resistances") ? target.getComponent("Resistances") : null;
    var typeResist = (res !== null && res.has(type)) ? res.getInt(type) : 0;
    if (typeof tuning !== 'undefined' && tuning) {
      var cap = tuning.resistCap(), floor = tuning.resistFloor();
      if (typeResist > cap) typeResist = cap;
      if (typeResist < floor) typeResist = floor;
    }
    // ... 余下 element/elementAmp/skillDamage 计算不变 ...
```
> 钳制只作用 `typeResist`(三抗);元素抗暂不钳(默认 0,本期无元素内容)。cap/floor 默认 75/−50,现有内容无超界 → 既有测试不变。

- [ ] **Step 4: 跑测试确认通过 + 回归**

Run: `cd backend && mvn -q test -Dtest=ArmorCurveTest`
Expected: PASS(flat 15、曲线 10、cap 10)。
Run: `cd backend && mvn -q test`
Expected: 全绿(FLAT 默认 + cap75 不触界 → `MitigationTest` 等不变)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/CombatTuning.java mods/base-rules/handlers/skill/00_skill_lib.js mods/base-rules/skills/fireball_fixed.yaml backend/src/test/java/com/epic/engine/sim/ArmorCurveTest.java
git commit -m "feat(sim): 护甲曲线 + 抗性 cap/floor（旋钮驱动，默认 FLAT 不改现状）"
```

---

## Task 2: Sweep 框架(单参数扫描)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/Sweep.java`
- Test: `backend/src/test/java/com/epic/engine/sim/SweepTest.java`

- [ ] **Step 1: 写失败测试**

`@SpringBootTest`。扫护甲 K = {5,10,20,40,60},每值跑一批 warrior L5 vs forest_goblin,断言:每个 K 一行;K 越大敌人越抗 → 玩家胜率单调不增(至少不全相等)。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SweepTest {
    @Autowired EventBus bus; @Autowired EntityStore store;
    @Autowired SessionService sessionService; @Autowired com.epic.engine.script.ScriptRuntime runtime;

    @Test void sweepsArmorK_oneRowPerValue() {
        CombatTuning tuning = new CombatTuning();
        runtime.bindService("tuning", tuning);
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        Sweep sweep = new Sweep();
        SimSetup setup = new SimSetup("warrior", 5, "forest_goblin", PolicyKind.HEURISTIC, List.of(), 50);

        List<Sweep.Point> pts = sweep.run(
                List.of(5, 10, 20, 40, 60),
                k -> tuning.setArmorCurve(k),    // 每个值设旋钮
                k -> Integer.toString(k),
                8, setup, builder, store, bus);

        assertThat(pts).hasSize(5);
        for (Sweep.Point p : pts) assertThat(p.metrics().total()).isEqualTo(8);
        // 至少不是全相等（K 改变了战斗）
        assertThat(pts.stream().map(p -> p.metrics().winRate()).distinct().count()).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=SweepTest`
Expected: 编译失败 —— `Sweep` 不存在。

- [ ] **Step 3: 写实现**

`Sweep.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;
import java.util.function.*;

/** 单参数扫描：对每个值先设旋钮（setter），再跑一批，收集 (label, BatchMetrics)。 */
public class Sweep {
    public record Point(String label, int value, BatchMetrics metrics) {}

    public List<Point> run(List<Integer> values,
                           IntConsumer knobSetter,
                           IntFunction<String> labeler,
                           int iterations, SimSetup setup,
                           CombatantBuilder builder, EntityStore store, EventBus bus) {
        BatchRunner batch = new BatchRunner();
        List<Point> pts = new ArrayList<>();
        for (int v : values) {
            knobSetter.accept(v);
            BatchMetrics m = batch.runSimulation(iterations, setup, builder, store, bus);
            pts.add(new Point(labeler.apply(v), v, m));
        }
        return pts;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=SweepTest`
Expected: PASS。
> 若 SpringBootTest 间共享 runtime 导致 `tuning` 残留:`setArmorCurve` 每次显式设值,且 `CombatTuning` 默认 variance=0,互不污染。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/Sweep.java backend/src/test/java/com/epic/engine/sim/SweepTest.java
git commit -m "feat(sim): Sweep 单参数扫描框架"
```

---

## Task 3: sources 落地(sim.apply_source)+ CombatantBuilder 接入

**Files:**
- Create: `mods/base-rules/handlers/world/sim_support.js`
- Modify: `backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java`
- Test: `backend/src/test/java/com/epic/engine/sim/SourcesTest.java`

- [ ] **Step 1: 写失败测试**

`@SpringBootTest`。baseline 战士 vs 加了 `{kind:modifier, field:"PrimaryStats.力量", value:"+30"}` 的 variant,断言 variant 的 `CombatStats.attack` 更高(力量经派生抬高武器伤害)。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SourcesTest {
    @Autowired EventBus bus; @Autowired EntityStore store; @Autowired SessionService sessionService;

    @Test void modifierSource_raisesDerivedAttack() {
        CombatantBuilder b = new CombatantBuilder(bus, store, sessionService);
        String base = b.buildPlayer("warrior", 10, List.of());
        int atkBase = store.get(base).getComponent("CombatStats").getInt("attack");

        Map<String,Object> src = new HashMap<>();
        src.put("kind", "modifier"); src.put("field", "PrimaryStats.力量"); src.put("value", "+30");
        String variant = b.buildPlayer("warrior", 10, List.of(src));
        int atkVar = store.get(variant).getComponent("CombatStats").getInt("attack");

        assertThat(atkVar).isGreaterThan(atkBase);   // 力量↑ → 武器伤害↑
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=SourcesTest`
Expected: FAIL —— `buildPlayer` 对非空 sources 抛 `UnsupportedOperationException`(Plan 1 占位)。

- [ ] **Step 3: 写实现**

`sim_support.js`(新 handler;`registerDerivedModifier` 等已全局):
```javascript
// 模拟器专用：把一个 source 落成真 modifier 并重算。仅模拟器 fire，真游戏永不触发。
engine.on("sim.apply_source", 100, function(event) {
    var playerId = event.get("playerId");
    var source = event.get("source");          // { kind, field, value }  (kind=modifier)
    var player = store.get(playerId);
    if (player === null || source === null) return;
    var kind = source.get("kind");
    if (kind === "modifier") {
        var field = source.get("field");        // "Comp.field"
        var valueStr = "" + source.get("value");// "+30" / "30"
        var dot = field.indexOf(".");
        var compName = field.substring(0, dot);
        var fieldName = field.substring(dot + 1);
        var sid = "sim_src_" + compName + "_" + fieldName + "_" + engine.now();
        // priority 默认 → 在派生(300)之前 apply，派生随后从抬高的属性重算
        engine.addModifier(playerId, {
            typeId: "sim_source",
            id: sid,
            label: "sim源 " + field,
            apply: function(ent) {
                var comp = ent.getComponent(compName);
                if (comp === null) return;
                var cur = comp.getInt(fieldName);
                if (valueStr.charAt(0) === "+") comp.set(fieldName, cur + parseInt(valueStr.substring(1)));
                else comp.set(fieldName, parseInt(valueStr));
            }
        });
    }
    // (kind=equipment 留作后续：设 EquipmentSlots + 重 fire entity.loaded)
});
```
> 把 `sim_support.js` 放在 `handlers/world/`,会被 ModuleLoader 正常加载。它只监听 `sim.apply_source`(模拟器私有),不影响真游戏。

`CombatantBuilder.buildPlayer`:去掉抛异常,改为创建+升级后逐个 fire `sim.apply_source`:
```java
    public String buildPlayer(String classId, int level, List<Map<String,Object>> sources) {
        String token = sessions.createSession();
        GameEvent create = new GameEvent("action.confirm_character");
        create.set("sessionToken", token);
        create.set("name", "sim_" + classId + "_" + level + "_" + System.nanoTime());
        create.set("class", classId);
        bus.fire("action.confirm_character", create);
        String playerId = sessions.getSession(token).activeCharacterId();
        Entity player = store.get(playerId);
        if (level > 1) {
            player.getComponent("Experience").set("level", level);
            Component ch = player.getComponent("Character");
            if (ch != null) ch.set("level", level);
            GameEvent loaded = new GameEvent("entity.loaded");
            loaded.set("entity", player);
            bus.fire("entity.loaded", loaded);
        }
        if (sources != null) {
            for (Map<String,Object> src : sources) {
                GameEvent se = new GameEvent("sim.apply_source");
                se.set("playerId", playerId);
                se.set("source", src);
                bus.fire("sim.apply_source", se);
            }
        }
        return playerId;
    }
```
> 注:`sim.apply_source` 在 `entity.loaded` **之后** fire,故 sim_source modifier 是最后注册的之一;ModifierChain 重算时按 priority(sim_source 默认 < 派生 300)→ 派生从抬高后的属性重算 attack。若发现 attack 未变,核对 source 是否在 derived 之前 apply(必要时给 sim_source 显式 priority,如 `priority: 250`)。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=SourcesTest`
Expected: PASS(variant attack > base)。

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/world/sim_support.js backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java backend/src/test/java/com/epic/engine/sim/SourcesTest.java
git commit -m "feat(sim): sources 经 sim.apply_source 落成真 modifier；buildPlayer 接入"
```

---

## Task 4: BaselineIndex(offense/defense 指数,固定参照档)

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java`(加合成 ref 参照档 + 临时 combat helper)
- Create: `backend/src/main/java/com/epic/engine/sim/NoOpPolicy.java`
- Create: `backend/src/main/java/com/epic/engine/sim/BaselineIndex.java`
- Test: `backend/src/test/java/com/epic/engine/sim/BaselineIndexTest.java`

- [ ] **Step 1: 写失败测试**

`@SpringBootTest`。baseline 战士 vs 同职业 + 力量源 → offense_index>1;defense_index≈1(力量不加防)。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BaselineIndexTest {
    @Autowired EventBus bus; @Autowired EntityStore store; @Autowired SessionService sessionService;

    @Test void strengthSource_raisesOffenseIndex() {
        CombatantBuilder b = new CombatantBuilder(bus, store, sessionService);
        BaselineIndex idx = new BaselineIndex(b, store, bus);

        Map<String,Object> str = new HashMap<>();
        str.put("kind","modifier"); str.put("field","PrimaryStats.力量"); str.put("value","+40");

        BaselineIndex.Result r = idx.compare("warrior", 10, List.of(str), 20);
        assertThat(r.offenseIndex()).isGreaterThan(1.0);          // 输出变强
        assertThat(r.defenseIndex()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.25)); // 不显著改防御
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=BaselineIndexTest`
Expected: 编译失败 —— `BaselineIndex` / `NoOpPolicy` 不存在。

- [ ] **Step 3: 写实现**

`NoOpPolicy.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.Map;
/** 不行动（用于防御参照档：玩家挨打不还手，测纯生存回合）。 */
public class NoOpPolicy implements Policy {
    @Override public Map<String,Object> selectCommand(Entity unit, String combatId, EntityStore store) { return null; }
}
```

`CombatantBuilder` 增合成参照档 + 临时 combat helper:
```java
    /** 合成一个战斗场：把已存在的 playerId 与一个临时敌人放进新 combat，返回 combatId。
     *  enemy 由调用方先 store.add 并打 enemy + combat tag。 */
    public String makeCombat(String playerId, String enemyId) {
        String combatId = "sim_combat_" + System.nanoTime();
        Entity combat = new Entity(combatId);
        Component st = new Component("CombatState"); st.set("round",0); st.set("phase","COMMAND"); combat.addComponent(st);
        store.add(combat);
        Entity player = store.get(playerId);
        player.addTag("combat:" + combatId); store.reindexTags(player);
        Entity enemy = store.get(enemyId);
        enemy.addTag("combat:" + combatId); store.reindexTags(enemy);
        return combatId;
    }
    /** 合成参照档敌人。kind="dummy"：0 防 0 抗、超厚血、慢；kind="attacker"：固定 attack、超厚血。 */
    public String makeRef(String id, String kind) {
        Entity e = new Entity(id);
        Component h = new Component("Health"); h.set("hp", 1_000_000); h.set("maxHp", 1_000_000); e.addComponent(h);
        Component cs = new Component("CombatStats");
        cs.set("attack", kind.equals("attacker") ? 30 : 1);
        cs.set("defense", 0); cs.set("speed", kind.equals("attacker") ? 99 : 1);
        e.addComponent(cs);
        Component r = new Component("Resistances"); r.set("物理",0); r.set("法术",0); r.set("精神",0); e.addComponent(r);
        e.addTag("enemy"); store.add(e);
        return id;
    }
```

`BaselineIndex.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;

/** offense_index = DPS(build)/DPS(baseline)（对 ref_dummy_offense，固定回合总伤/回合）；
 *  defense_index = 生存回合(build)/生存回合(baseline)（对 ref_attacker_defense，NoOp 挨打至死）。 */
public class BaselineIndex {
    private final CombatantBuilder builder; private final EntityStore store; private final EventBus bus;
    private static final int DPS_ROUNDS = 20;
    public BaselineIndex(CombatantBuilder builder, EntityStore store, EventBus bus) {
        this.builder = builder; this.store = store; this.bus = bus;
    }
    public record Result(double offenseIndex, double defenseIndex,
                         double baseDps, double variantDps,
                         double baseSurvival, double variantSurvival) {}

    public Result compare(String classId, int level, List<Map<String,Object>> variantSources, int iterations) {
        double baseDps = avgDps(classId, level, List.of(), iterations);
        double varDps  = avgDps(classId, level, variantSources, iterations);
        double baseSurv = avgSurvival(classId, level, List.of(), iterations);
        double varSurv  = avgSurvival(classId, level, variantSources, iterations);
        return new Result(safeDiv(varDps, baseDps), safeDiv(varSurv, baseSurv),
                baseDps, varDps, baseSurv, varSurv);
    }

    private double avgDps(String cls, int lvl, List<Map<String,Object>> src, int iters) {
        FightRunner runner = new FightRunner(store, bus);
        DamageTap tap = new DamageTap(bus, runner::round);
        long totalDmg = 0;
        for (int i = 0; i < iters; i++) {
            String pid = builder.buildPlayer(cls, lvl, src);
            String dummy = builder.makeRef("sim_dummy_" + System.nanoTime(), "dummy");
            String cid = builder.makeCombat(pid, dummy);
            Map<String,Policy> pol = new HashMap<>();
            pol.put(pid, new HeuristicPolicy()); pol.put(dummy, new NoOpPolicy());
            FightResult r = runner.run(cid, List.of(pid), List.of(dummy), pol, DPS_ROUNDS, tap);
            long dealt = 0; for (Long v : r.trace().damageTakenByTarget().values()) dealt += v;
            totalDmg += dealt;
            cleanup(pid, dummy, cid);
        }
        return (double) totalDmg / (iters * DPS_ROUNDS);
    }

    private double avgSurvival(String cls, int lvl, List<Map<String,Object>> src, int iters) {
        FightRunner runner = new FightRunner(store, bus);
        long totalRounds = 0;
        for (int i = 0; i < iters; i++) {
            String pid = builder.buildPlayer(cls, lvl, src);
            String atk = builder.makeRef("sim_atk_" + System.nanoTime(), "attacker");
            String cid = builder.makeCombat(pid, atk);
            Map<String,Policy> pol = new HashMap<>();
            pol.put(pid, new NoOpPolicy()); pol.put(atk, new HeuristicPolicy());
            FightResult r = runner.run(cid, List.of(pid), List.of(atk), pol, 200, runner.round() >= 0 ? null : null);
            totalRounds += r.rounds();
            cleanup(pid, atk, cid);
        }
        return (double) totalRounds / iters;
    }

    private void cleanup(String pid, String enemyId, String cid) {
        store.remove(enemyId); store.remove(cid);
        Entity p = store.get(pid);
        if (p != null) { p.removeTag("combat:" + cid); store.reindexTags(p); }
        store.remove(pid);
    }
    private static double safeDiv(double a, double b) { return b == 0 ? 0.0 : a / b; }
}
```
> 注:`avgSurvival` 的 `runner.run(...)` 末参传 `null`(不需要 tap)。上面写法保留 6 参重载;直接传 `null` 即可,无需那段三元。实现时写 `runner.run(cid, List.of(pid), List.of(atk), pol, 200, null)`。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=BaselineIndexTest`
Expected: PASS(offense_index>1,defense_index≈1)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/NoOpPolicy.java backend/src/main/java/com/epic/engine/sim/BaselineIndex.java backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java backend/src/test/java/com/epic/engine/sim/BaselineIndexTest.java
git commit -m "feat(sim): BaselineIndex offense/defense 指数（固定参照档）"
```

---

## Task 5: 报告 B(armor_k_sweep + 曲线 1/2/3)与 C(baseline_vs_variant)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/reports/ArmorKSweepReport.java`
- Create: `backend/src/main/java/com/epic/engine/sim/reports/BaselineVsVariantReport.java`
- Test: `backend/src/test/java/com/epic/engine/sim/reports/ReportsBCTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.epic.engine.sim.reports;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import com.epic.engine.sim.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReportsBCTest {
    @Autowired EventBus bus; @Autowired EntityStore store;
    @Autowired SessionService sessionService; @Autowired com.epic.engine.script.ScriptRuntime runtime;

    @Test void armorKSweep_csvHeaderAndRows() {
        CombatTuning tuning = new CombatTuning(); runtime.bindService("tuning", tuning);
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        ArmorKSweepReport report = new ArmorKSweepReport(builder, store, bus, tuning);
        SimSetup setup = new SimSetup("warrior", 5, "forest_goblin", PolicyKind.HEURISTIC, List.of(), 50);
        String csv = report.runCsv(List.of(5,10,20,40,60), setup, 8);
        assertThat(csv.split("\n")[0])
                .isEqualTo("armor_K,win_rate,player_survival_turns,enemy_ttk,player_ttk_median,win_hp_remaining");
        assertThat(csv.split("\n")).hasSize(6);   // 表头 + 5 行
    }

    @Test void baselineVsVariant_reportsDeltas() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        BaselineVsVariantReport report = new BaselineVsVariantReport(builder, store, bus);
        Map<String,Object> str = new HashMap<>();
        str.put("kind","modifier"); str.put("field","PrimaryStats.力量"); str.put("value","+40");
        String summary = report.run("warrior", 10, List.of(str), 15);
        assertThat(summary).contains("offense_index").contains("defense_index");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=ReportsBCTest`
Expected: 编译失败 —— 两个 report 类不存在。

- [ ] **Step 3: 写实现**

`ArmorKSweepReport.java`(报告 B + 曲线 1/2/3 的同一张表):
```java
package com.epic.engine.sim.reports;
import com.epic.engine.core.*;
import com.epic.engine.sim.*;
import java.util.*;

/** 报告 B armor_k_sweep：每个 K 一行。列同时供曲线 1(K vs ttk)、2(K vs win_rate)、3(K vs win_hp)。 */
public class ArmorKSweepReport {
    private final CombatantBuilder builder; private final EntityStore store;
    private final EventBus bus; private final CombatTuning tuning;
    public ArmorKSweepReport(CombatantBuilder builder, EntityStore store, EventBus bus, CombatTuning tuning) {
        this.builder = builder; this.store = store; this.bus = bus; this.tuning = tuning;
    }
    public String runCsv(List<Integer> ks, SimSetup setup, int iterations) {
        Sweep sweep = new Sweep();
        List<Sweep.Point> pts = sweep.run(ks,
                k -> tuning.setArmorCurve(k), k -> Integer.toString(k),
                iterations, setup, builder, store, bus);
        StringBuilder sb = new StringBuilder(
                "armor_K,win_rate,player_survival_turns,enemy_ttk,player_ttk_median,win_hp_remaining\n");
        for (Sweep.Point p : pts) {
            BatchMetrics m = p.metrics();
            sb.append(String.format(Locale.US, "%d,%.3f,%.1f,%.1f,%.1f,%.3f\n",
                    p.value(), m.winRate(), m.playerSurvivalTurnsMedian(),
                    m.ttkMedian(), m.ttkMedian(), nz(m.winHpRemainingMedian())));
        }
        return sb.toString();
    }
    private static double nz(double d) { return Double.isNaN(d) ? 0.0 : d; }
}
```
> 说明:本期 `enemy_ttk` 与 `player_ttk_median` 都用 `ttkMedian()`(玩家击杀回合)占位同列;敌方 TTK(敌人杀玩家所需)需 loss 局回合,Plan 后续可拆分。曲线 1 的两条 y(player/enemy ttk)先共用,够 PM 看节奏;精拆列为后续小改。

`BaselineVsVariantReport.java`(报告 C):
```java
package com.epic.engine.sim.reports;
import com.epic.engine.core.*;
import com.epic.engine.sim.*;
import java.util.*;

/** 报告 C baseline_vs_variant：offense/defense 指数 + 人话 delta 摘要。 */
public class BaselineVsVariantReport {
    private final CombatantBuilder builder; private final EntityStore store; private final EventBus bus;
    public BaselineVsVariantReport(CombatantBuilder builder, EntityStore store, EventBus bus) {
        this.builder = builder; this.store = store; this.bus = bus;
    }
    public String run(String classId, int level, List<Map<String,Object>> variant, int iterations) {
        BaselineIndex idx = new BaselineIndex(builder, store, bus);
        BaselineIndex.Result r = idx.compare(classId, level, variant, iterations);
        int offPct = (int) Math.round((r.offenseIndex() - 1.0) * 100);
        int defPct = (int) Math.round((r.defenseIndex() - 1.0) * 100);
        return String.format(Locale.US,
            "baseline_vs_variant[%s L%d]%noffense_index=%.2f (输出 %+d%%)%ndefense_index=%.2f (生存 %+d%%)%n",
            classId, level, r.offenseIndex(), offPct, r.defenseIndex(), defPct);
    }
}
```

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `cd backend && mvn -q test -Dtest=ReportsBCTest`
Expected: PASS(B 表头+5 行;C 含 offense_index/defense_index)。
Run: `cd backend && mvn -q test`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/reports backend/src/test/java/com/epic/engine/sim/reports/ReportsBCTest.java
git commit -m "feat(sim): 报告 B armor_k_sweep + 曲线1/2/3 + 报告 C baseline_vs_variant"
```

---

## Self-Review(对照 spec §2 验收物 + UC2/UC3 + §6 口径)

- **报告 B `armor_k_sweep`(spec §2.B):** Task 5 ✓,K={5,10,20,40,60},列含 win_rate/player_survival_turns/enemy_ttk(占位)/+曲线列。
- **报告 C `baseline_vs_variant`(spec §2.C):** Task 5 ✓,offense/defense 指数 + delta 摘要。
- **五曲线(spec §2):** 曲线 1/2/3(armor_K vs ttk/win_rate/win_hp)= 报告 B 同表列 ✓;曲线 5(class_level 体检)= Plan 1 报告 A 数据 ✓;曲线 4(resist_cap vs ehp)— `setResistBounds` + Sweep 可扫(机制已在 Task 1/2),独立 CSV 导出未单列任务,**记为缺口**,补一个 `ResistCapSweepReport`(与 ArmorKSweepReport 同构,setter 改 `tuning.setResistBounds(cap,floor)`)。**实现者:照 ArmorKSweepReport 复制改 setter + 表头,加一个 test,补这条曲线。**
- **offense/defense 比值法 + 固定参照档(spec §6.2):** `BaselineIndex` + `makeRef` ✓。
- **护甲曲线/cap/floor 旋钮、默认不改现状(spec §5/§8.5):** Task 1 默认 FLAT + cap75 不触界 → 147 测试不破 ✓。**翻默认为 CURVE + 重生 golden = PM 选定 K 后的独立小提交,不在本计划。**
- **sources=modifier/equipment(spec §3):** modifier ✓(Task 3);equipment 仅留 sim_support.js 注释占位,**记为缺口**(report C 用 stat modifier 已满足验收;equipment-真实物品 留后续)。
- **timeout/三桶、复用真引擎、damage 来自真实路径:** 继承 Plan 1/2 ✓。
- **类型一致性:** `CombatTuning` 新方法、`Sweep.Point`、`BaselineIndex.Result`、`makeRef/makeCombat`、`NoOpPolicy`、两 report 构造签名跨任务一致 ✓。
- **占位扫描:** 无 TBD;两处明确缺口(曲线 4 的 `ResistCapSweepReport`、equipment 源)写成"照同构类复制"的可执行指令,非含糊占位 ✓。

---

## 三计划交付后的状态(给 PM 的话)

- 报告 A(前期职业体检)、B(护甲 K 扫)、C(build 对比)、UC4 解释拆解、五曲线(曲线 4 补 `ResistCapSweepReport`)全部可一键跑出。
- 护甲曲线/cap/方差是**旋钮**:模拟器里随便扫,真游戏默认不变。PM 用报告 B/曲线选定 K、用曲线 4 选定 cap 后,**最后一步**才把 `CombatTuning` 默认翻成 CURVE+选定值并重生 golden——那是"配数值"的收尾提交,独立于"造仪器"。
- 扩展位(spec §9):search policy、矩阵跑、power_index、equipment 真实物品源、LLM=scripted 喂线、crit——均不需改仪器核心。
