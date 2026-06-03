# 数值平衡模拟器 — Plan 2:解释力(伤害拆解)+ 方差源 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 PM 能解释"为什么这场胜率 43%"——产出每场/每批的伤害拆解(`damage_by_skill / damage_taken_by_source / mitigation_saved / kill_source / 平均死亡回合`),并在真减伤路径加入 `damage_variance`(默认 0、可种子复现),为"将将击败"提供方差源。对应 spec UC4 + §7。

**Architecture:** 唯一减伤收口 `Skill.mitigate` 既是方差注入点也是 instrumentation 点:在其中 fire 一个**纯增量事件** `combat.mitigation`(无既有监听者,绝不破坏现有行为)。Java 侧 `DamageTap` **一次性**订阅 `combat.damage_dealt`/`combat.unit_death`/`combat.mitigation`,通过"可切换的 active 收集器"按场归集(过期 tap 在 active=null 时空跑)。方差由一个 `CombatTuning` 服务(种子化 `Random`)提供 `rollVariance()`,mitigate 乘上;未绑定时(纯 runtime / 真游戏)默认 ×1。

**Tech Stack:** 同 Plan 1。前置依赖:**Plan 1 已合并**(`FightRunner`/`FightResult`/`BatchRunner`/`CombatantBuilder`/`SimSetup`)。

---

## 关键集成事实(已核实)

- **减伤收口:** `Skill.mitigate(target, raw, opts)`(`mods/base-rules/handlers/skill/00_skill_lib.js`),`opts={delivery,type,element,elementAmp,ignoreDefend}`。普攻/技能/DoT 全过它。call site 在 `01_effects.js` 的 `damage_only`/`damage_with_debuff`(先 `computeDamage` 出 raw,再 `mitigate` 得 final)。
- **既有伤害事件:** `combat.damage_dealt` 携带 `attackerId / targetId / damage(=final) / combatId / skillId`(`Skill.fireDamageDealt`)。`death_check.js` 监听它(prio 100)→ 故新订阅用更高 prio(200)只读不干扰。
- **死亡事件:** `combat.unit_death` 携带 `deadId / killerId / combatId`(`death_check.js`)。
- **EventBus(Java):** `bus.on(String type, int priority, Consumer<GameEvent>)` 订阅;`fire`;`removeHandlersFor(type)` 会清掉**该类型全部**handler(含真引擎的)——**禁止对共享事件用它**。故 tap 一次性注册 + 切换 active 收集器。
- **服务绑定:** `runtime.bindService("name", obj)`;JS 内 `typeof name !== 'undefined'` 守卫缺失情形(模式见 `typeof registerDerivedModifier`)。
- **GameEvent 取值:** `ev.get("k")`、`ev.set("k",v)`;数值可能是 Integer/Double,Java 侧读取用 `((Number) ev.get("damage")).intValue()`。

---

## File Structure(新增/修改)

- Create `backend/src/main/java/com/epic/engine/sim/FightTrace.java` — 单场伤害拆解累加器。
- Create `backend/src/main/java/com/epic/engine/sim/DamageTap.java` — 一次性订阅 + active 切换。
- Create `backend/src/main/java/com/epic/engine/sim/CombatTuning.java` — 方差旋钮 + 种子 RNG(Plan 3 复用并扩展 K/cap)。
- Modify `mods/base-rules/handlers/skill/00_skill_lib.js` — `Skill.mitigate` 加 `combat.mitigation` 增量事件 + 方差乘子。
- Modify `backend/src/main/java/com/epic/engine/sim/FightRunner.java` — 接受可选 `DamageTap`,按场 begin/end。
- Modify `backend/src/main/java/com/epic/engine/sim/BatchRunner.java` — 跨场聚合 trace → `BatchTrace`。
- Create `backend/src/main/java/com/epic/engine/sim/BatchTrace.java` — 批量拆解聚合。
- 测试镜像于 `backend/src/test/java/com/epic/engine/sim/...`。

---

## Task 1: FightTrace + DamageTap(纯 runtime)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/FightTrace.java`
- Create: `backend/src/main/java/com/epic/engine/sim/DamageTap.java`
- Test: `backend/src/test/java/com/epic/engine/sim/DamageTapTest.java`

- [ ] **Step 1: 写失败测试**

复用 Plan 1 `FightRunnerTest` 的纯 runtime harness(同 `@BeforeEach` setUp 加载 combat+skill JS + `setupCombat`)。手动 fire 两个 `combat.damage_dealt` + 一个 `combat.unit_death`,断言 tap 归集正确。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class DamageTapTest {
    EventBus bus; EntityStore store;
    @BeforeEach void setUp() { bus = new EventBus(); store = new EntityStore(); }

    @Test void capturesDamageBySkill_killSource_andDeathRound() {
        DamageTap tap = new DamageTap(bus, () -> 4);   // roundSupplier: 当前回合恒为 4
        FightTrace trace = tap.begin();

        fireDamage("player1", "goblin1", 8, "basic_attack");
        fireDamage("player1", "goblin1", 8, "cleave");
        fireDeath("goblin1", "player1");

        FightTrace done = tap.end();
        assertThat(done).isSameAs(trace);
        assertThat(done.damageBySkill().get("basic_attack")).isEqualTo(8L);
        assertThat(done.damageBySkill().get("cleave")).isEqualTo(8L);
        assertThat(done.damageTakenByTarget().get("goblin1")).isEqualTo(16L);
        assertThat(done.killSource().get("player1")).isEqualTo(1);
        assertThat(done.deathRounds()).containsExactly(4);
    }

    @Test void inactiveTap_isNoOp() {
        DamageTap tap = new DamageTap(bus, () -> 1);
        // 未 begin → active null → 不记录、不抛异常
        fireDamage("a", "b", 5, "basic_attack");
        FightTrace t = tap.begin();
        assertThat(t.damageBySkill()).isEmpty();
    }

    private void fireDamage(String atk, String tgt, int dmg, String skill) {
        GameEvent e = new GameEvent("combat.damage_dealt");
        e.set("attackerId", atk); e.set("targetId", tgt); e.set("damage", dmg); e.set("skillId", skill);
        bus.fire("combat.damage_dealt", e);
    }
    private void fireDeath(String dead, String killer) {
        GameEvent e = new GameEvent("combat.unit_death");
        e.set("deadId", dead); e.set("killerId", killer);
        bus.fire("combat.unit_death", e);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=DamageTapTest`
Expected: 编译失败 —— `FightTrace` / `DamageTap` 不存在。

- [ ] **Step 3: 写最小实现**

`FightTrace.java`:
```java
package com.epic.engine.sim;
import java.util.*;

/** 单场伤害拆解累加器（可变，归集中使用；归集后只读读取）。 */
public class FightTrace {
    private final Map<String,Long> damageBySkill = new HashMap<>();
    private final Map<String,Long> damageTakenByTarget = new HashMap<>();
    private final Map<String,Long> mitigationSavedByTarget = new HashMap<>();
    private final Map<String,Long> damageByType = new HashMap<>();
    private final Map<String,Integer> killSource = new HashMap<>();
    private final List<Integer> deathRounds = new ArrayList<>();

    void addDamage(String skillId, String targetId, long dmg) {
        damageBySkill.merge(skillId == null ? "?" : skillId, dmg, Long::sum);
        damageTakenByTarget.merge(targetId, dmg, Long::sum);
    }
    void addMitigation(String targetId, String type, long raw, long fin) {
        mitigationSavedByTarget.merge(targetId, Math.max(0, raw - fin), Long::sum);
        if (type != null) damageByType.merge(type, fin, Long::sum);
    }
    void addDeath(String killerId, int round) {
        if (killerId != null) killSource.merge(killerId, 1, Integer::sum);
        deathRounds.add(round);
    }
    public Map<String,Long> damageBySkill() { return damageBySkill; }
    public Map<String,Long> damageTakenByTarget() { return damageTakenByTarget; }
    public Map<String,Long> mitigationSavedByTarget() { return mitigationSavedByTarget; }
    public Map<String,Long> damageByType() { return damageByType; }
    public Map<String,Integer> killSource() { return killSource; }
    public List<Integer> deathRounds() { return deathRounds; }
}
```

`DamageTap.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.function.IntSupplier;

/** 一次性订阅 combat.damage_dealt / unit_death / mitigation；按场用 active 收集器切换。
 *  过期实例在 active=null 时空跑——绝不对共享事件做 removeHandlersFor。 */
public class DamageTap {
    private final IntSupplier roundSupplier;
    private FightTrace active;

    public DamageTap(EventBus bus, IntSupplier roundSupplier) {
        this.roundSupplier = roundSupplier;
        bus.on("combat.damage_dealt", 200, e -> {
            if (active == null) return;
            Object d = e.get("damage");
            long dmg = d instanceof Number n ? n.longValue() : 0L;
            active.addDamage(str(e.get("skillId")), str(e.get("targetId")), dmg);
        });
        bus.on("combat.mitigation", 200, e -> {
            if (active == null) return;
            long raw = num(e.get("raw")), fin = num(e.get("final"));
            active.addMitigation(str(e.get("targetId")), str(e.get("type")), raw, fin);
        });
        bus.on("combat.unit_death", 200, e -> {
            if (active == null) return;
            active.addDeath(str(e.get("killerId")), roundSupplier.getAsInt());
        });
    }
    public FightTrace begin() { active = new FightTrace(); return active; }
    public FightTrace end() { FightTrace t = active; active = null; return t; }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static long num(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=DamageTapTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/FightTrace.java backend/src/main/java/com/epic/engine/sim/DamageTap.java backend/src/test/java/com/epic/engine/sim/DamageTapTest.java
git commit -m "feat(sim): FightTrace 伤害拆解 + DamageTap 一次性订阅"
```

---

## Task 2: FightRunner 接入 DamageTap(按场 begin/end + 死亡回合)

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/sim/FightRunner.java`
- Test: `backend/src/test/java/com/epic/engine/sim/FightRunnerTraceTest.java`

- [ ] **Step 1: 写失败测试**

复用 Plan 1 `FightRunnerTest` harness。给 FightRunner 传一个 DamageTap;跑完一场后从返回的 trace 读 damage_by_skill。FightRunner 的当前回合数要喂给 tap 的 roundSupplier(死亡回合用)。

```java
package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class FightRunnerTraceTest {
    EventBus bus; EntityStore store; ScriptRuntime runtime;
    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));
        Path c = Path.of("../mods/base-rules/handlers/combat");
        for (String f : List.of("initiative.js","damage_calc.js","death_check.js","combat_flow.js"))
            runtime.execute(Files.readString(c.resolve(f)), f);
        Path s = Path.of("../mods/base-rules/handlers/skill");
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        for (String f : List.of("00_skill_lib.js","01_effects.js","02_dispatch.js"))
            runtime.execute(Files.readString(s.resolve(f)), f);
        runtime.execute(Files.readString(Path.of("../mods/base-rules/skills/defend.js")), "defend.js");
    }
    @AfterEach void tearDown() { runtime.close(); }

    @Test void run_capturesTracePerFight() {
        setupCombat();
        Policy atk = (u, cid, st) -> {
            boolean p = u.hasTag("player");
            for (Entity e : st.getByTagAsList("combat:" + cid)) {
                boolean en = p ? !e.hasTag("player") : e.hasTag("player");
                if (en && e.getComponent("Health").getInt("hp") > 0) {
                    Map<String,Object> cmd = new HashMap<>(); cmd.put("type","basic_attack"); cmd.put("targetId", e.getId()); return cmd;
                }
            }
            return null;
        };
        DamageTap tap = new DamageTap(bus, () -> 0);   // round 由 runner 内部覆盖
        FightRunner runner = new FightRunner(store, bus);
        FightResult r = runner.run("battle1", List.of("player1"), List.of("goblin1"),
                Map.of("player1", atk, "goblin1", atk), 50, tap);
        FightTrace trace = r.trace();
        assertThat(trace).isNotNull();
        assertThat(trace.damageBySkill().getOrDefault("basic_attack", 0L)).isGreaterThan(0L);
        assertThat(trace.killSource().getOrDefault("player1", 0)).isEqualTo(1);   // 玩家击杀哥布林
        assertThat(trace.deathRounds()).isNotEmpty();
    }

    private void setupCombat() {
        Entity p = new Entity("player1");
        Component h = new Component("Health"); h.set("hp",100); h.set("maxHp",100); p.addComponent(h);
        Component cs = new Component("CombatStats"); cs.set("attack",10); cs.set("defense",5); cs.set("speed",6); p.addComponent(cs);
        p.addTag("player"); p.addTag("combat:battle1"); store.add(p);
        Entity g = new Entity("goblin1");
        Component gh = new Component("Health"); gh.set("hp",20); gh.set("maxHp",20); g.addComponent(gh);
        Component gs = new Component("CombatStats"); gs.set("attack",5); gs.set("defense",2); gs.set("speed",3); g.addComponent(gs);
        g.addTag("enemy"); g.addTag("combat:battle1"); store.add(g);
        Entity combat = new Entity("battle1");
        Component st = new Component("CombatState"); st.set("round",0); st.set("phase","COMMAND"); combat.addComponent(st);
        store.add(combat);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=FightRunnerTraceTest`
Expected: 编译失败 —— `FightResult.trace()` 与带 DamageTap 的 `run(...)` 重载不存在。

- [ ] **Step 3: 写实现**

改 `FightResult.java` 增加可选 trace 字段(保持旧 4 参构造,新增带 trace 的工厂):
```java
package com.epic.engine.sim;
public record FightResult(FightOutcome outcome, int rounds, double playerHpFrac, double enemyHpFrac, FightTrace trace) {
    public FightResult(FightOutcome outcome, int rounds, double playerHpFrac, double enemyHpFrac) {
        this(outcome, rounds, playerHpFrac, enemyHpFrac, null);
    }
}
```
> 注:Plan 1 测试用的是 4 参构造 → 仍可用。`BatchMetrics` 只读前几项,不受影响。

改 `FightRunner.java`:把 round 计数暴露给 tap,并加 `run(..., DamageTap tap)` 重载;旧 5 参重载委托给新 6 参(tap=null)。
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;

public class FightRunner {
    private final EntityStore store; private final EventBus bus;
    private int currentRound = 0;
    public FightRunner(EntityStore store, EventBus bus) { this.store = store; this.bus = bus; }

    /** tap 的 roundSupplier 应引用本 runner 的 currentRound（构造 tap 时用 () -> runner.round()）。
     *  为简化，本重载内部用传入的 tap.begin()/end()，死亡回合由 tap 自己的 roundSupplier 决定；
     *  调用方应以 () -> runnerRoundHolder[0] 形式提供。这里改为：tap 由 runner 在每场内驱动。 */
    public FightResult run(String combatId, List<String> playerIds, List<String> enemyIds,
                           Map<String, Policy> policies, int maxTurns) {
        return run(combatId, playerIds, enemyIds, policies, maxTurns, null);
    }

    public FightResult run(String combatId, List<String> playerIds, List<String> enemyIds,
                           Map<String, Policy> policies, int maxTurns, DamageTap tap) {
        Entity combat = store.get(combatId);
        Component state = combat.getComponent("CombatState");
        currentRound = 0;
        FightTrace trace = tap != null ? tap.begin() : null;
        String phase = state.getString("phase");
        while (currentRound < maxTurns && !"VICTORY".equals(phase) && !"DEFEAT".equals(phase)) {
            Map<String, Map<String,Object>> commands = new HashMap<>();
            List<String> all = new ArrayList<>(playerIds); all.addAll(enemyIds);
            for (String id : all) {
                Entity u = store.get(id);
                if (u == null || !u.hasComponent("Health") || u.getComponent("Health").getInt("hp") <= 0) continue;
                Policy pol = policies.get(id);
                if (pol == null) continue;
                Map<String,Object> cmd = pol.selectCommand(u, combatId, store);
                if (cmd != null) commands.put(id, cmd);
            }
            GameEvent ev = new GameEvent("combat.resolve_round");
            ev.set("combatId", combatId); ev.set("commands", commands);
            bus.fire("combat.resolve_round", ev);
            currentRound++;
            phase = state.getString("phase");
        }
        if (tap != null) tap.end();
        double pFrac = hpFraction(playerIds), eFrac = hpFraction(enemyIds);
        FightOutcome outcome = "VICTORY".equals(phase) ? FightOutcome.WIN
                : "DEFEAT".equals(phase) ? FightOutcome.LOSS : FightOutcome.TIMEOUT;
        return new FightResult(outcome, currentRound, pFrac, eFrac, trace);
    }

    public int round() { return currentRound; }   // 供 DamageTap roundSupplier 引用

    private double hpFraction(List<String> ids) {
        int hp = 0, max = 0;
        for (String id : ids) {
            Entity e = store.get(id);
            if (e == null || !e.hasComponent("Health")) continue;
            hp += Math.max(0, e.getComponent("Health").getInt("hp"));
            max += e.getComponent("Health").getInt("maxHp");
        }
        return max == 0 ? 0.0 : (double) hp / max;
    }
}
```
> 死亡回合精度:测试里 tap 用 `() -> 0`,只断言 `deathRounds() 非空`。端到端用法应 `new DamageTap(bus, runner::round)` 让死亡回合取 runner 实时回合。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=FightRunnerTraceTest,FightRunnerTest`
Expected: PASS(新旧都过——验证 5 参重载向后兼容)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/FightRunner.java backend/src/main/java/com/epic/engine/sim/FightResult.java backend/src/test/java/com/epic/engine/sim/FightRunnerTraceTest.java
git commit -m "feat(sim): FightRunner 按场接入 DamageTap，FightResult 带 trace"
```

---

## Task 3: combat.mitigation 增量事件(真减伤路径)→ 捕获 raw/saved/type

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`(`Skill.mitigate`)
- Test: `backend/src/test/java/com/epic/engine/sim/MitigationTraceTest.java`

- [ ] **Step 1: 写失败测试**

纯 runtime harness。给一个有 `Resistances{法术:50}` 的目标,用一个法术技能打它(直接调 `damage_only` 或 fire 一个技能 cmd)。最简做法:直接在 JS 暴露面外,用 `combat.resolve_round` 让玩家放 `fireball`(法术,add10)。tap 应捕获 mitigation_saved>0、damageByType 含 "法术"。

```java
package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class MitigationTraceTest {
    EventBus bus; EntityStore store; ScriptRuntime runtime;
    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));
        Path c = Path.of("../mods/base-rules/handlers/combat");
        for (String f : List.of("initiative.js","damage_calc.js","death_check.js","combat_flow.js"))
            runtime.execute(Files.readString(c.resolve(f)), f);
        Path s = Path.of("../mods/base-rules/handlers/skill");
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        for (String f : List.of("00_skill_lib.js","01_effects.js","02_dispatch.js"))
            runtime.execute(Files.readString(s.resolve(f)), f);
    }
    @AfterEach void tearDown() { runtime.close(); }

    @Test void mitigation_eventCarriesRawFinalTypeSaved() {
        // caster 智力10；target 法抗50、血厚
        Entity p = new Entity("p");
        Component ph = new Component("Health"); ph.set("hp",100); ph.set("maxHp",100); p.addComponent(ph);
        Component pcs = new Component("CombatStats"); pcs.set("attack",10); pcs.set("defense",0); pcs.set("speed",9); p.addComponent(pcs);
        Component pds = new Component("DerivedStats"); pds.set("法术强度",10); p.addComponent(pds);
        Component pps = new Component("PrimaryStats"); pps.set("智力",10); p.addComponent(pps);
        p.addTag("player"); p.addTag("combat:b"); store.add(p);

        Entity t = new Entity("t");
        Component th = new Component("Health"); th.set("hp",500); th.set("maxHp",500); t.addComponent(th);
        Component tcs = new Component("CombatStats"); tcs.set("attack",1); tcs.set("defense",0); tcs.set("speed",1); t.addComponent(tcs);
        Component tr = new Component("Resistances"); tr.set("物理",0); tr.set("法术",50); tr.set("精神",0); t.addComponent(tr);
        t.addTag("enemy"); t.addTag("combat:b"); store.add(t);

        Entity combat = new Entity("b");
        Component st = new Component("CombatState"); st.set("round",0); st.set("phase","COMMAND"); combat.addComponent(st);
        store.add(combat);

        DamageTap tap = new DamageTap(bus, () -> 1);
        FightTrace trace = tap.begin();
        // 玩家放 fireball 打 t；敌人不动
        Map<String,Object> cmd = new HashMap<>(); cmd.put("type","fireball"); cmd.put("targetId","t");
        Map<String,Map<String,Object>> cmds = new HashMap<>(); cmds.put("p", cmd);
        GameEvent ev = new GameEvent("combat.resolve_round");
        ev.set("combatId","b"); ev.set("commands", cmds);
        bus.fire("combat.resolve_round", ev);
        tap.end();

        // fireball raw = add10 + ceil(智力10×0.2)=10+2=12；法抗50 → final=ceil(12×0.5)=6；saved=6
        assertThat(trace.mitigationSavedByTarget().get("t")).isEqualTo(6L);
        assertThat(trace.damageByType().get("法术")).isEqualTo(6L);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=MitigationTraceTest`
Expected: FAIL —— `mitigationSavedByTarget().get("t")` 为 null(尚未 fire `combat.mitigation`)。

- [ ] **Step 3: 改 Skill.mitigate 发增量事件**

在 `00_skill_lib.js` 的 `mitigate` 内,**两条分支都在 return 前**发一个纯增量事件。改法:把两处 `return` 改成统一收尾。最小侵入版——在普攻分支与技能分支各自 return 前插入 fire(用一个内部 helper):

```javascript
  // 唯一减伤收口。... (注释保留)
  mitigate: function(target, raw, opts) {
    opts = opts || {};
    var defending = target.hasComponent("Buff_defending") && !opts.ignoreDefend;
    var type = opts.type || "物理";
    var fin;
    if (opts.delivery === "普攻") {
      var armor = target.hasComponent("CombatStats")
          ? target.getComponent("CombatStats").getInt("defense") : 0;
      fin = Math.max(1, raw - armor);
      if (defending) fin = Math.max(1, Math.floor(fin * 0.5));
      this._emitMitigation(target, raw, fin, "普攻", opts.delivery);
      return fin;
    }
    var res = target.hasComponent("Resistances") ? target.getComponent("Resistances") : null;
    var typeResist = (res !== null && res.has(type)) ? res.getInt(type) : 0;
    var element = opts.element || null;
    var elementResist = (element && res !== null && res.has(element)) ? res.getInt(element) : 0;
    var elementAmp = opts.elementAmp || 0;
    var skillDamage = raw
        * (1 + elementAmp / 100)
        * (1 - typeResist / 100)
        * (1 - elementResist / 100);
    if (defending) skillDamage = skillDamage * 0.5;
    fin = Math.max(1, Math.ceil(skillDamage));
    this._emitMitigation(target, raw, fin, type, opts.delivery);
    return fin;
  },

  // 纯增量诊断事件：无既有监听者，只供模拟器 DamageTap 归集。raw/final/saved 供 mitigation_saved。
  _emitMitigation: function(target, raw, fin, type, delivery) {
    var ev = engine.newEvent("combat.mitigation");
    ev.set("targetId", target.getId());
    ev.set("raw", raw);
    ev.set("final", fin);
    ev.set("type", type);
    ev.set("delivery", delivery || "技能");
    engine.fire("combat.mitigation", ev);
  },
```
> 这是纯增量改动:`combat.mitigation` 无任何既有监听者,真游戏行为不变(只多 fire 一个没人听的事件)。`engine.newEvent`/`engine.fire` 已在 skill_lib 广泛使用。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `cd backend && mvn -q test -Dtest=MitigationTraceTest`
Expected: PASS(saved=6、法术=6)。
Run: `cd backend && mvn -q test`
Expected: 全绿——确认发事件未破坏既有(尤其 `MitigationTest`)。

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/sim/MitigationTraceTest.java
git commit -m "feat(sim): Skill.mitigate 发 combat.mitigation 增量事件（raw/final/type/saved）"
```

---

## Task 4: CombatTuning + damage_variance(真减伤路径,默认关,种子复现)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/CombatTuning.java`
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`(`mitigate` 末乘方差)
- Test: `backend/src/test/java/com/epic/engine/sim/VarianceTest.java`

- [ ] **Step 1: 写失败测试**

绑定 `tuning` 服务;variance=0 时 final 不变;variance=0.5 时多次出现波动;同 seed 复现同序列。用纯 runtime 直接调用一个法术多次(或读 `combat.mitigation` 的 final 分布)。

```java
package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class VarianceTest {
    EventBus bus; EntityStore store; ScriptRuntime runtime; CombatTuning tuning;
    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.bindService("buffs", new BuffService(bus, store));
        tuning = new CombatTuning();
        runtime.bindService("tuning", tuning);
        Path c = Path.of("../mods/base-rules/handlers/combat");
        for (String f : List.of("initiative.js","damage_calc.js","death_check.js","combat_flow.js"))
            runtime.execute(Files.readString(c.resolve(f)), f);
        Path s = Path.of("../mods/base-rules/handlers/skill");
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        for (String f : List.of("00_skill_lib.js","01_effects.js","02_dispatch.js"))
            runtime.execute(Files.readString(s.resolve(f)), f);
    }
    @AfterEach void tearDown() { runtime.close(); }

    private List<Long> finals(int rounds) {
        List<Long> out = new ArrayList<>();
        DamageTap tap = new DamageTap(bus, () -> 1);
        for (int i = 0; i < rounds; i++) {
            // 重建一个满血目标，玩家放 fireball
            store.remove("t"); store.remove("p"); store.remove("b");
            Entity p = new Entity("p");
            Component ph = new Component("Health"); ph.set("hp",100); ph.set("maxHp",100); p.addComponent(ph);
            Component pcs = new Component("CombatStats"); pcs.set("attack",10); pcs.set("defense",0); pcs.set("speed",9); p.addComponent(pcs);
            Component pds = new Component("DerivedStats"); pds.set("法术强度",10); p.addComponent(pds);
            Component pps = new Component("PrimaryStats"); pps.set("智力",10); p.addComponent(pps);
            p.addTag("player"); p.addTag("combat:b"); store.add(p);
            Entity t = new Entity("t");
            Component th = new Component("Health"); th.set("hp",500); th.set("maxHp",500); t.addComponent(th);
            Component tcs = new Component("CombatStats"); tcs.set("attack",1); tcs.set("defense",0); tcs.set("speed",1); t.addComponent(tcs);
            t.addComponent(new Component("Resistances"));
            t.addTag("enemy"); t.addTag("combat:b"); store.add(t);
            Entity combat = new Entity("b");
            Component st = new Component("CombatState"); st.set("round",0); st.set("phase","COMMAND"); combat.addComponent(st);
            store.add(combat);

            FightTrace tr = tap.begin();
            Map<String,Object> cmd = new HashMap<>(); cmd.put("type","fireball"); cmd.put("targetId","t");
            Map<String,Map<String,Object>> cmds = new HashMap<>(); cmds.put("p", cmd);
            GameEvent ev = new GameEvent("combat.resolve_round"); ev.set("combatId","b"); ev.set("commands", cmds);
            bus.fire("combat.resolve_round", ev);
            tap.end();
            out.add(tr.damageByType().getOrDefault("法术", 0L));
        }
        return out;
    }

    @Test void varianceZero_isDeterministic() {
        tuning.setVariance(0.0, 1L);
        List<Long> f = finals(5);
        assertThat(f).containsOnly(12L);   // 无方差，恒 12
    }

    @Test void variancePositive_seededReproducible() {
        tuning.setVariance(0.5, 42L);
        List<Long> a = finals(8);
        tuning.setVariance(0.5, 42L);
        List<Long> b = finals(8);
        assertThat(a).isEqualTo(b);                 // 同 seed 复现
        assertThat(new HashSet<>(a).size()).isGreaterThan(1);  // 确有波动
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=VarianceTest`
Expected: 编译失败 —— `CombatTuning` 不存在。

- [ ] **Step 3: 写实现**

`CombatTuning.java`:
```java
package com.epic.engine.sim;
import java.util.Random;

/** 战斗调参旋钮 + 种子 RNG。绑为 ScriptRuntime 服务 "tuning"；未绑定时 mitigate 默认 ×1。
 *  Plan 3 会在此扩展 armorK / resistCap / resistFloor / armorModel。 */
public class CombatTuning {
    private double variance = 0.0;
    private Random rng = new Random(0L);

    public void setVariance(double v, long seed) { this.variance = v; this.rng = new Random(seed); }

    /** 返回伤害方差乘子：1 + U(-variance, +variance)。variance=0 → 恒 1。JS 在 mitigate 末调用。 */
    public double rollVariance() {
        if (variance <= 0.0) return 1.0;
        return 1.0 + (rng.nextDouble() * 2.0 - 1.0) * variance;
    }
}
```

改 `00_skill_lib.js` `mitigate`:在两分支 `fin = ...` 之后、`_emitMitigation` 之前,统一乘方差并重新取整下限。把两处收尾抽成 helper:
```javascript
  // 在普攻分支：
  //   fin = Math.max(1, raw - armor); if (defending) ...;
  //   fin = this._applyVariance(fin);
  //   this._emitMitigation(...); return fin;
  // 在技能分支：
  //   fin = Math.max(1, Math.ceil(skillDamage));
  //   fin = this._applyVariance(fin);
  //   this._emitMitigation(...); return fin;

  _applyVariance: function(fin) {
    if (typeof tuning === 'undefined' || !tuning) return fin;
    var f = tuning.rollVariance();
    return Math.max(1, Math.ceil(fin * f));
  },
```
具体把 Task 3 写好的两个分支,在各自 `this._emitMitigation(...)` **之前**插入一行 `fin = this._applyVariance(fin);`(emit 的 final 用方差后的值,saved 仍 = raw−final)。

- [ ] **Step 4: 跑测试确认通过 + 回归**

Run: `cd backend && mvn -q test -Dtest=VarianceTest`
Expected: PASS(variance 0 恒 12;seed 42 可复现且有波动)。
Run: `cd backend && mvn -q test`
Expected: 全绿(纯 runtime 既有测试无 `tuning` 绑定 → `typeof tuning==='undefined'` → ×1,行为不变)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/CombatTuning.java mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/sim/VarianceTest.java
git commit -m "feat(sim): CombatTuning + damage_variance（真减伤路径，默认关，种子复现）"
```

---

## Task 5: BatchTrace 聚合 + UC4 解释入口

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/BatchTrace.java`
- Modify: `backend/src/main/java/com/epic/engine/sim/BatchRunner.java`(`runSimulation` 复用单个 DamageTap + 聚合 trace)
- Test: `backend/src/test/java/com/epic/engine/sim/BatchTraceTest.java`

- [ ] **Step 1: 写失败测试**

`@SpringBootTest`。跑 warrior L5 vs forest_goblin 一批,断言聚合拆解非空且自洽(`damage_by_skill` 总和 ≈ 击杀所需,`avgDeathRound>0`)。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BatchTraceTest {
    @Autowired EventBus bus; @Autowired EntityStore store; @Autowired SessionService sessionService;

    @Test void batch_aggregatesExplainability() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        BatchRunner batch = new BatchRunner();
        SimSetup setup = new SimSetup("warrior", 5, "forest_goblin", PolicyKind.HEURISTIC, List.of(), 50);
        BatchRunner.Aggregated agg = batch.runSimulationWithTrace(15, setup, builder, store, bus);
        assertThat(agg.metrics().total()).isEqualTo(15);
        BatchTrace t = agg.trace();
        assertThat(t.totalDamageBySkill().getOrDefault("basic_attack", 0L)).isGreaterThan(0L);
        assertThat(t.avgDeathRound()).isGreaterThan(0.0);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=BatchTraceTest`
Expected: 编译失败 —— `BatchTrace` / `runSimulationWithTrace` / `Aggregated` 不存在。

- [ ] **Step 3: 写实现**

`BatchTrace.java`:
```java
package com.epic.engine.sim;
import java.util.*;

/** 跨场聚合拆解。把每场 FightTrace 合并：skill/type/target 累加，死亡回合求均值。 */
public class BatchTrace {
    private final Map<String,Long> totalDamageBySkill = new HashMap<>();
    private final Map<String,Long> totalDamageByType = new HashMap<>();
    private final Map<String,Long> totalMitigationSaved = new HashMap<>();
    private final Map<String,Integer> totalKillSource = new HashMap<>();
    private final List<Integer> allDeathRounds = new ArrayList<>();

    public void merge(FightTrace t) {
        if (t == null) return;
        t.damageBySkill().forEach((k,v) -> totalDamageBySkill.merge(k, v, Long::sum));
        t.damageByType().forEach((k,v) -> totalDamageByType.merge(k, v, Long::sum));
        t.mitigationSavedByTarget().forEach((k,v) -> totalMitigationSaved.merge(k, v, Long::sum));
        t.killSource().forEach((k,v) -> totalKillSource.merge(k, v, Integer::sum));
        allDeathRounds.addAll(t.deathRounds());
    }
    public Map<String,Long> totalDamageBySkill() { return totalDamageBySkill; }
    public Map<String,Long> totalDamageByType() { return totalDamageByType; }
    public Map<String,Long> totalMitigationSaved() { return totalMitigationSaved; }
    public Map<String,Integer> totalKillSource() { return totalKillSource; }
    public double avgDeathRound() {
        return allDeathRounds.isEmpty() ? 0.0
                : allDeathRounds.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}
```

`BatchRunner.java` 增加带 trace 的真跑(单个 DamageTap 跨场复用,roundSupplier 引用 runner):
```java
    public record Aggregated(BatchMetrics metrics, BatchTrace trace) {}

    public Aggregated runSimulationWithTrace(int iterations, SimSetup setup,
                                             CombatantBuilder builder,
                                             com.epic.engine.core.EntityStore store,
                                             com.epic.engine.core.EventBus bus) {
        FightRunner runner = new FightRunner(store, bus);
        DamageTap tap = new DamageTap(bus, runner::round);   // 死亡回合取 runner 实时回合
        BatchTrace batchTrace = new BatchTrace();
        java.util.List<FightResult> results = new java.util.ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            String playerId = builder.buildPlayer(setup.playerClass(), setup.playerLevel(), java.util.List.of());
            CombatantBuilder.Spawn spawn = builder.spawnEncounter(playerId, setup.encounterId());
            java.util.Map<String, Policy> policies = new java.util.HashMap<>();
            policies.put(playerId, setup.newPlayerPolicy());
            for (String eid : spawn.enemyIds()) policies.put(eid, new HeuristicPolicy());
            FightResult r = runner.run(spawn.combatId(),
                    java.util.List.of(playerId), spawn.enemyIds(), policies, setup.maxTurns(), tap);
            batchTrace.merge(r.trace());
            results.add(r);
            builder.cleanup(playerId, spawn);
            store.remove(playerId);
        }
        return new Aggregated(BatchMetrics.of(results), batchTrace);
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=BatchTraceTest`
Expected: PASS。

- [ ] **Step 5: 全量回归 + 提交**

Run: `cd backend && mvn -q test`
Expected: 全绿。

```bash
git add backend/src/main/java/com/epic/engine/sim/BatchTrace.java backend/src/main/java/com/epic/engine/sim/BatchRunner.java backend/src/test/java/com/epic/engine/sim/BatchTraceTest.java
git commit -m "feat(sim): BatchTrace 跨场拆解聚合 + runSimulationWithTrace（UC4 解释力）"
```

---

## Self-Review(对照 spec UC4 + §7)

- **UC4 解释力字段(spec §5.A / UC4):** `damage_by_skill`(✓ FightTrace/BatchTrace)、`damage_taken_by_source`(✓ damageTakenByTarget / damageBySkill)、`mitigation_saved`(✓ via combat.mitigation)、`kill_source`(✓ unit_death killerId)、`平均死亡回合`(✓ avgDeathRound)。
- **instrumentation 来自真实路径(spec §6.5):** `combat.damage_dealt`/`unit_death` 既有真事件 + `combat.mitigation` 在真 `Skill.mitigate` 内发,**无事后旁路重算** ✓。
- **方差只做 damage_variance(spec §7):** ✓,真减伤路径、默认 0、种子复现、crit 未做(future)。
- **不破坏真游戏 / 既有 147 测试:** `combat.mitigation` 无既有监听者;`tuning` 未绑定时 `typeof` 守卫 → ×1;每步含全量回归 `mvn test` ✓。
- **共享事件不滥删 handler:** DamageTap 一次性注册 + active 切换,过期实例空跑 ✓(不调 removeHandlersFor)。
- **类型一致性:** `FightResult(...,trace)` 新 5 字段 + 旧 4 参兼容构造;`DamageTap.begin/end`、`CombatTuning.rollVariance/setVariance`、`BatchTrace.merge`、`BatchRunner.Aggregated/runSimulationWithTrace` 跨任务一致 ✓。
- **占位扫描:** 无 TBD/TODO,每步完整代码+命令 ✓。
