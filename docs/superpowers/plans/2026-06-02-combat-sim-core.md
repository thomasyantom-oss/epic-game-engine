# 数值平衡模拟器 — Plan 1:仿真核心 → Report A 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建一个无头批量战斗模拟器的核心:用真引擎跑 class+level baseline vs encounter 的战斗 N 次,聚合出 win/loss/timeout、TTK、剩血等指标,并产出 PM 验收报告 A `early_class_health_check`(五职业 × 1-10 级 × 前期怪)。

**Architecture:** 薄测量层贴真引擎。仿真不另写战斗数学——它复用真 `combat.resolve_round` 回合循环、真 `combat.start_encounter` 怪物生成、真角色创建/`entity.loaded` 派生链。每回合由可插拔 `Policy` 为双方每个存活单位产出 command(`{type, targetId}`),喂给 `combat.resolve_round`,读 `CombatState.phase` 判终局。Java 侧组件:`FightRunner`(单场)、`BatchRunner`/`BatchMetrics`(N 场聚合)、`CombatantBuilder`(复用真创建路径)、`EarlyClassHealthCheck`(报告 A)。

**Tech Stack:** Java 21 / Spring Boot 3(`@SpringBootTest` 拿到 module-loaded 引擎与 `EventBus`/`EntityStore`/`SessionService` bean)+ 纯 `ScriptRuntime` harness(核心 runner 的隔离单测,沿用 `NewCombatIntegrationTest` 模式)+ JUnit5 / AssertJ。

**本计划是模拟器三连计划的第 1 个(见文末 Roadmap)。Plan 1 独立可交付:跑完即得报告 A。**

---

## 关键集成事实(实现者必读,均已在代码库核实)

- **驱动一回合:** `bus.fire("combat.resolve_round", ev)`,`ev.set("combatId", id)`、`ev.set("commands", Map<String, Map<String,Object>>)`。command 形如 `{"type": skillId, "targetId": id}`(可选 `targetRow`/`targetCol`)。见 `mods/base-rules/handlers/combat/combat_flow.js`、`NewCombatIntegrationTest`。
- **终局:** 一回合后读 `store.get(combatId).getComponent("CombatState").getString("phase")`。值 ∈ `COMMAND`(继续)/`VICTORY`/`DEFEAT`。判定逻辑见 `death_check.js` 的 `combat.check_end`:无 enemy 存活=VICTORY,无 player 存活=DEFEAT。
- **存活判定:** 单位有 `LifeState` 时看 `state=="alive"`,否则 `Health.hp>0`(`death_check.js`)。本期单位无 LifeState,用 hp>0。
- **怪物生成(复用):** `bus.fire("combat.start_encounter", ev)`,`ev.set("playerId", pid)`、`ev.set("encounterId", encId)`。它建 combat 实体(id=`"combat_"+now`)、从 `entities/encounters/<encId>.yaml` 造敌人(真 `registerDerivedModifier`、满血)、给 player 打 `combat:<combatId>` tag。见 `start_combat.js`。⚠️ 不要用 `action.combat_command`——它内含硬编码敌人 AI(只会 basic_attack)和 endCombat 副作用(重生/持久化)。仿真要绕过它,自己用 policy 驱动双方。
- **combatId 回取:** start_encounter 不回传 id;从 player 的 tag 找:遍历 `player.getTags()`,取以 `combat:` 开头的那个,截 `substring(7)`。见 `start_combat.js` 的 `action.combat_command`。
- **角色创建(复用):** `bus.fire("action.confirm_character", ev)`,`ev.set("sessionToken", token)`、`ev.set("name", n)`、`ev.set("class", classId)`。先 `sessionService.createSession()`。创建后 active character id 从 session 取(`SnapshotController` 用 `session.activeCharacterId()`)。见 `CharacterFlowTest`、`select.js`(`action.confirm_character`,priority 100)。
- **升级到 N 级(复用派生链):** 设 `player.getComponent("Experience").set("level", N)` 与 `player.getComponent("Character").set("level", N)`,再 `bus.fire("entity.loaded", ev)`(`ev.set("entity", player)`)。`entity.loaded`(`recalculate_hooks.js`)会复位 base、重挂 class/equipment/`registerLevelGrowthModifier(id, N)`/`registerDerivedModifier`、重算、回满血。这是把角色干净重建到任意等级的真路径。
- **bean 注入:** `EventBus eventBus`、`EntityStore entityStore`、`ScriptRuntime scriptRuntime`、`SessionService sessionService` 均为 Spring bean(`EngineConfig`)。`entityStore.getByTagAsList("combat:"+id)` 返回 `List<Entity>`(JS 侧用法已证)。
- **派生数值(锚):** 玩家 `CombatStats.attack = ceil(武器base × (1+weaponAttr×倍率/100))`(力量倍率2,余1);`Health.maxHp = 30 + 体质×10`;`CombatStats.speed = 敏捷`。见 `derived_stats.js`。class schema(`schemas/sub/class_*.schema.yaml`)有 `growth`/`modifiers`/`starting_skills`/`weapon_attr`。
- **纯 runtime harness(隔离单测用):** 见 `NewCombatIntegrationTest.setUp()`——手动 `new EventBus/EntityStore/ScriptRuntime`,`runtime.execute(Files.readString(...), name)` 逐个加载 `combat/{initiative,damage_calc,death_check,combat_flow}.js` + `skill/{00_skill_lib,01_effects,02_dispatch}.js` + `skills/defend.js`,`runtime.setModuleContext(Path.of("../mods/base-rules"))`。

---

## File Structure

新增包 `com.epic.engine.sim`(main)与 `com.epic.engine.sim`(test),职责单一、文件聚焦:

- `backend/src/main/java/com/epic/engine/sim/FightOutcome.java` — enum WIN/LOSS/TIMEOUT。
- `backend/src/main/java/com/epic/engine/sim/FightResult.java` — record:单场结果(outcome、rounds、playerHpFrac、enemyHpFrac)。
- `backend/src/main/java/com/epic/engine/sim/Policy.java` — 接口:为一个单位选 command。
- `backend/src/main/java/com/epic/engine/sim/ScriptedPolicy.java` — 固定出招表,非法回退 basic_attack。
- `backend/src/main/java/com/epic/engine/sim/HeuristicPolicy.java` — v1:打最低血敌人的 basic_attack。
- `backend/src/main/java/com/epic/engine/sim/FightRunner.java` — 单场回合循环。
- `backend/src/main/java/com/epic/engine/sim/BatchMetrics.java` — record:N 场聚合指标 + 分位工具。
- `backend/src/main/java/com/epic/engine/sim/BatchRunner.java` — 跑 N 场并聚合。
- `backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java` — 复用真路径造玩家(class+level)与敌人(encounter)。
- `backend/src/main/java/com/epic/engine/sim/SimSetup.java` — record:一场仿真的输入(玩家 class/level/policy + encounterId + maxTurns)。
- `backend/src/main/java/com/epic/engine/sim/reports/EarlyClassHealthCheck.java` — 报告 A 矩阵驱动 + CSV。
- 测试镜像于 `backend/src/test/java/com/epic/engine/sim/...`。

---

## Task 1: FightResult + FightRunner 单场循环(纯 runtime 隔离)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/FightOutcome.java`
- Create: `backend/src/main/java/com/epic/engine/sim/FightResult.java`
- Create: `backend/src/main/java/com/epic/engine/sim/Policy.java`
- Create: `backend/src/main/java/com/epic/engine/sim/FightRunner.java`
- Test: `backend/src/test/java/com/epic/engine/sim/FightRunnerTest.java`

- [ ] **Step 1: 写失败测试**

沿用 `NewCombatIntegrationTest` 的纯 runtime harness 与 `setupCombat()`(手建 player1 hp100/atk10/def5/spd6 + goblin1 hp20/atk5/def2/spd3,均打 `combat:battle1` tag,combat 实体 battle1)。FightRunner 用"双方都 basic_attack 打对方"的内联 policy,应在已知回合数内 WIN。

```java
package com.epic.engine.sim;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class FightRunnerTest {
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

    @Test void run_playerWinsKnownFight() {
        setupCombat();
        // policy: 每个单位都 basic_attack 打第一个活着的敌方
        Policy attackFirstEnemy = (unit, combatId, st) -> {
            boolean unitIsPlayer = unit.hasTag("player");
            for (Entity e : st.getByTagAsList("combat:" + combatId)) {
                boolean enemy = unitIsPlayer ? !e.hasTag("player") : e.hasTag("player");
                if (enemy && e.getComponent("Health").getInt("hp") > 0) {
                    Map<String,Object> cmd = new HashMap<>();
                    cmd.put("type", "basic_attack"); cmd.put("targetId", e.getId());
                    return cmd;
                }
            }
            return null;
        };
        FightRunner runner = new FightRunner(store, bus);
        FightResult r = runner.run("battle1",
                List.of("player1"), List.of("goblin1"),
                Map.of("player1", attackFirstEnemy, "goblin1", attackFirstEnemy), 50);

        // player 每回合对 goblin 造成 10-2=8;goblin 20hp -> 3 回合(8,16,24)杀死
        assertThat(r.outcome()).isEqualTo(FightOutcome.WIN);
        assertThat(r.rounds()).isEqualTo(3);
        assertThat(r.playerHpFrac()).isGreaterThan(0.0);
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

Run: `cd backend && mvn -q test -Dtest=FightRunnerTest`
Expected: 编译失败 —— `FightOutcome` / `FightResult` / `Policy` / `FightRunner` 不存在。

- [ ] **Step 3: 写最小实现**

`FightOutcome.java`:
```java
package com.epic.engine.sim;
public enum FightOutcome { WIN, LOSS, TIMEOUT }
```

`FightResult.java`:
```java
package com.epic.engine.sim;
/** 单场战斗结果。rounds=结束时回合数(=TTK);hpFrac=结束时该方 hp/maxHp 合计占比。 */
public record FightResult(FightOutcome outcome, int rounds, double playerHpFrac, double enemyHpFrac) {}
```

`Policy.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import java.util.Map;
/** 为一个存活单位选出本回合 command（{"type":skillId,"targetId":id} 或 null=不动）。 */
public interface Policy {
    Map<String,Object> selectCommand(Entity unit, String combatId, EntityStore store);
}
```

`FightRunner.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;

/** 单场回合循环：每回合为双方每个存活单位调 policy 收集 commands，
 *  fire combat.resolve_round，读 CombatState.phase 判终局；到 maxTurns 记 TIMEOUT。 */
public class FightRunner {
    private final EntityStore store;
    private final EventBus bus;
    public FightRunner(EntityStore store, EventBus bus) { this.store = store; this.bus = bus; }

    public FightResult run(String combatId, List<String> playerIds, List<String> enemyIds,
                           Map<String, Policy> policies, int maxTurns) {
        Entity combat = store.get(combatId);
        Component state = combat.getComponent("CombatState");
        int round = 0;
        String phase = state.getString("phase");
        while (round < maxTurns && !"VICTORY".equals(phase) && !"DEFEAT".equals(phase)) {
            Map<String, Map<String,Object>> commands = new HashMap<>();
            for (String id : allUnits(playerIds, enemyIds)) {
                Entity u = store.get(id);
                if (u == null || !u.hasComponent("Health") || u.getComponent("Health").getInt("hp") <= 0) continue;
                Policy pol = policies.get(id);
                if (pol == null) continue;
                Map<String,Object> cmd = pol.selectCommand(u, combatId, store);
                if (cmd != null) commands.put(id, cmd);
            }
            GameEvent ev = new GameEvent("combat.resolve_round");
            ev.set("combatId", combatId);
            ev.set("commands", commands);
            bus.fire("combat.resolve_round", ev);
            round++;
            phase = state.getString("phase");
        }
        double pFrac = hpFraction(playerIds);
        double eFrac = hpFraction(enemyIds);
        FightOutcome outcome = "VICTORY".equals(phase) ? FightOutcome.WIN
                : "DEFEAT".equals(phase) ? FightOutcome.LOSS : FightOutcome.TIMEOUT;
        return new FightResult(outcome, round, pFrac, eFrac);
    }

    private List<String> allUnits(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a); all.addAll(b); return all;
    }
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

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=FightRunnerTest`
Expected: PASS（`rounds==3`，outcome WIN）。若 `rounds` 不是 3,核对 `combat_flow.js` 是否在 resolve 后把 round 自增 + phase 逻辑;FightRunner 自己计回合数(每 fire 一次 +1)与之解耦,断言以 FightRunner 的计数为准。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim backend/src/test/java/com/epic/engine/sim
git commit -m "feat(sim): FightRunner 单场回合循环 + FightResult"
```

---

## Task 2: ScriptedPolicy + HeuristicPolicy

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/ScriptedPolicy.java`
- Create: `backend/src/main/java/com/epic/engine/sim/HeuristicPolicy.java`
- Test: `backend/src/test/java/com/epic/engine/sim/PolicyTest.java`

- [ ] **Step 1: 写失败测试**

纯单元测试(无需引擎):手建一个 store + 单位 + 两个不同血量敌人,断言两种 policy 的选择。

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyTest {
    EntityStore store;
    @BeforeEach void setUp() {
        store = new EntityStore();
        Entity p = new Entity("p"); Component ph = new Component("Health"); ph.set("hp",100); ph.set("maxHp",100);
        p.addComponent(ph); p.addTag("player"); p.addTag("combat:b"); store.add(p);
        addEnemy("e_full", 20);   // 满血
        addEnemy("e_low", 3);     // 残血
    }
    private void addEnemy(String id, int hp) {
        Entity e = new Entity(id); Component h = new Component("Health"); h.set("hp",hp); h.set("maxHp",20);
        e.addComponent(h); e.addTag("enemy"); e.addTag("combat:b"); store.add(e);
    }

    @Test void heuristic_targetsLowestHpEnemy_withBasicAttack() {
        HeuristicPolicy pol = new HeuristicPolicy();
        Map<String,Object> cmd = pol.selectCommand(store.get("p"), "b", store);
        assertThat(cmd.get("type")).isEqualTo("basic_attack");
        assertThat(cmd.get("targetId")).isEqualTo("e_low");
    }

    @Test void scripted_replaysSteps_thenFallsBackToBasicAttack() {
        // 出招表：第1步 cleave；耗尽后回退 basic_attack。target 由 policy 补成最低血敌人。
        ScriptedPolicy pol = new ScriptedPolicy(List.of("cleave"));
        Map<String,Object> first = pol.selectCommand(store.get("p"), "b", store);
        assertThat(first.get("type")).isEqualTo("cleave");
        Map<String,Object> second = pol.selectCommand(store.get("p"), "b", store);
        assertThat(second.get("type")).isEqualTo("basic_attack");   // 回退
        assertThat(second.get("targetId")).isEqualTo("e_low");
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=PolicyTest`
Expected: 编译失败 —— `ScriptedPolicy` / `HeuristicPolicy` 不存在。

- [ ] **Step 3: 写最小实现**

`HeuristicPolicy.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;

/** v1 启发式：basic_attack 打"最低血"的活着的敌方单位。
 *  （技能轮换 / 保送人头打分留到 Plan 2+。） */
public class HeuristicPolicy implements Policy {
    @Override public Map<String,Object> selectCommand(Entity unit, String combatId, EntityStore store) {
        Entity target = lowestHpEnemy(unit, combatId, store);
        if (target == null) return null;
        Map<String,Object> cmd = new HashMap<>();
        cmd.put("type", "basic_attack");
        cmd.put("targetId", target.getId());
        return cmd;
    }
    static Entity lowestHpEnemy(Entity unit, String combatId, EntityStore store) {
        boolean unitIsPlayer = unit.hasTag("player");
        Entity best = null; int bestHp = Integer.MAX_VALUE;
        for (Entity e : store.getByTagAsList("combat:" + combatId)) {
            boolean enemy = unitIsPlayer ? !e.hasTag("player") : e.hasTag("player");
            if (!enemy || !e.hasComponent("Health")) continue;
            int hp = e.getComponent("Health").getInt("hp");
            if (hp <= 0) continue;
            if (hp < bestHp) { bestHp = hp; best = e; }
        }
        return best;
    }
}
```

`ScriptedPolicy.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import java.util.*;

/** 固定出招表（skillId 序列）；每回合取下一个，耗尽后回退 basic_attack。
 *  target 一律补成"最低血敌方"（v1 单目标技能足够；AOE 的 targetRow/Col 留到后续）。 */
public class ScriptedPolicy implements Policy {
    private final List<String> steps; private int idx = 0;
    public ScriptedPolicy(List<String> steps) { this.steps = new ArrayList<>(steps); }
    @Override public Map<String,Object> selectCommand(Entity unit, String combatId, EntityStore store) {
        Entity target = HeuristicPolicy.lowestHpEnemy(unit, combatId, store);
        if (target == null) return null;
        String type = idx < steps.size() ? steps.get(idx) : "basic_attack";
        idx++;
        Map<String,Object> cmd = new HashMap<>();
        cmd.put("type", type);
        cmd.put("targetId", target.getId());
        return cmd;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=PolicyTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/ScriptedPolicy.java backend/src/main/java/com/epic/engine/sim/HeuristicPolicy.java backend/src/test/java/com/epic/engine/sim/PolicyTest.java
git commit -m "feat(sim): ScriptedPolicy + HeuristicPolicy（最低血集火）"
```

---

## Task 3: BatchRunner + BatchMetrics(N 场聚合)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/BatchMetrics.java`
- Create: `backend/src/main/java/com/epic/engine/sim/BatchRunner.java`
- Test: `backend/src/test/java/com/epic/engine/sim/BatchMetricsTest.java`

- [ ] **Step 1: 写失败测试**

`BatchMetrics.of(List<FightResult>)` 聚合;断言 win_rate/timeout_rate 三桶、ttk 中位、win 时玩家剩血中位、loss 时敌方剩血中位。用手造的 FightResult 列表(不跑引擎,纯聚合数学)。

```java
package com.epic.engine.sim;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BatchMetricsTest {
    @Test void aggregates_threeBuckets_andMedians() {
        List<FightResult> rs = List.of(
            new FightResult(FightOutcome.WIN, 3, 0.40, 0.0),
            new FightResult(FightOutcome.WIN, 5, 0.20, 0.0),
            new FightResult(FightOutcome.LOSS, 7, 0.0, 0.30),
            new FightResult(FightOutcome.TIMEOUT, 50, 0.10, 0.55)
        );
        BatchMetrics m = BatchMetrics.of(rs);
        assertThat(m.total()).isEqualTo(4);
        assertThat(m.winRate()).isEqualTo(0.5);
        assertThat(m.lossRate()).isEqualTo(0.25);
        assertThat(m.timeoutRate()).isEqualTo(0.25);
        // 三桶率之和=1
        assertThat(m.winRate()+m.lossRate()+m.timeoutRate()).isCloseTo(1.0, within(1e-9));
        // ttk 中位（全部 3,5,7,50 → 中位取较低中值 5）
        assertThat(m.ttkMedian()).isEqualTo(5.0);
        // 胜利时玩家剩血中位（0.40,0.20 → 0.30）
        assertThat(m.winHpRemainingMedian()).isCloseTo(0.30, within(1e-9));
        // 失败时敌方剩血中位（仅 0.30）
        assertThat(m.lossEnemyHpRemainingMedian()).isCloseTo(0.30, within(1e-9));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=BatchMetricsTest`
Expected: 编译失败 —— `BatchMetrics` 不存在。

- [ ] **Step 3: 写最小实现**

`BatchMetrics.java`:
```java
package com.epic.engine.sim;
import java.util.*;
import java.util.stream.*;

/** N 场聚合。win/loss/timeout 三桶率之和=1（timeout 不并入 loss）。
 *  中位数：排序后取 index floor((n-1)/2)（偶数取较低中值，稳定、无插值）。 */
public record BatchMetrics(
        int total, double winRate, double lossRate, double timeoutRate,
        double ttkMedian, double ttkP10, double ttkP90,
        double winHpRemainingMedian, double lossEnemyHpRemainingMedian,
        double playerSurvivalTurnsMedian) {

    public static BatchMetrics of(List<FightResult> rs) {
        int n = rs.size();
        long wins = rs.stream().filter(r -> r.outcome()==FightOutcome.WIN).count();
        long losses = rs.stream().filter(r -> r.outcome()==FightOutcome.LOSS).count();
        long timeouts = rs.stream().filter(r -> r.outcome()==FightOutcome.TIMEOUT).count();
        List<Double> ttk = rs.stream().map(r -> (double) r.rounds()).sorted().toList();
        List<Double> winHp = rs.stream().filter(r -> r.outcome()==FightOutcome.WIN)
                .map(FightResult::playerHpFrac).sorted().toList();
        List<Double> lossEnemyHp = rs.stream().filter(r -> r.outcome()==FightOutcome.LOSS)
                .map(FightResult::enemyHpFrac).sorted().toList();
        // 玩家存活回合：非胜局（loss/timeout）的 rounds；胜局玩家未死，记 rounds（撑满全程）
        List<Double> survival = rs.stream().map(r -> (double) r.rounds()).sorted().toList();
        return new BatchMetrics(n,
                rate(wins,n), rate(losses,n), rate(timeouts,n),
                pct(ttk,0.5), pct(ttk,0.10), pct(ttk,0.90),
                pct(winHp,0.5), pct(lossEnemyHp,0.5), pct(survival,0.5));
    }
    private static double rate(long c, int n) { return n==0 ? 0.0 : (double) c / n; }
    /** 升序列表的分位（floor 索引，无插值）；空列表返回 NaN。 */
    static double pct(List<Double> sorted, double q) {
        if (sorted.isEmpty()) return Double.NaN;
        int idx = (int) Math.floor(q * (sorted.size() - 1));
        return sorted.get(idx);
    }
}
```

`BatchRunner.java`(本任务先做"接收已生成 FightResult 列表"的聚合入口与"重复跑一个 supplier"的批量入口;真实建场/跑场在 Task 5 接上):
```java
package com.epic.engine.sim;
import java.util.*;
import java.util.function.Supplier;

/** 跑 N 场并聚合。每场由 supplier 产出一个 FightResult（Task 5 用真 builder+runner 填充该 supplier）。 */
public class BatchRunner {
    public BatchMetrics run(int iterations, Supplier<FightResult> oneFight) {
        List<FightResult> results = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) results.add(oneFight.get());
        return BatchMetrics.of(results);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=BatchMetricsTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/BatchMetrics.java backend/src/main/java/com/epic/engine/sim/BatchRunner.java backend/src/test/java/com/epic/engine/sim/BatchMetricsTest.java
git commit -m "feat(sim): BatchMetrics 三桶聚合 + 分位 + BatchRunner"
```

---

## Task 4: CombatantBuilder(复用真创建/派生路径)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java`
- Test: `backend/src/test/java/com/epic/engine/sim/CombatantBuilderTest.java`

> 本任务起进入 `@SpringBootTest`(需要 module-loaded 引擎:schemas / 角色创建 / encounter)。模式见 `CharacterFlowTest`。

- [ ] **Step 1: 写失败测试**

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
class CombatantBuilderTest {
    @Autowired EventBus bus;
    @Autowired EntityStore store;
    @Autowired SessionService sessionService;

    @Test void buildsWarrior_statsScaleWithLevel() {
        CombatantBuilder b = new CombatantBuilder(bus, store, sessionService);
        String p1 = b.buildPlayer("warrior", 1, List.of());
        int hp1 = store.get(p1).getComponent("Health").getInt("maxHp");
        String p10 = b.buildPlayer("warrior", 10, List.of());
        int hp10 = store.get(p10).getComponent("Health").getInt("maxHp");
        // 战士 growth 体质+2/级 → maxHp=30+体质×10 随等级显著上升
        assertThat(hp10).isGreaterThan(hp1);
        // 等级写进 Experience
        assertThat(store.get(p10).getComponent("Experience").getInt("level")).isEqualTo(10);
        // 初始技能在身（战士 starting_skills 含 cleave）
        var skills = (List<Map<String,Object>>) store.get(p10).getComponent("Skills").get("list");
        assertThat(skills.stream().map(m -> String.valueOf(m.get("id"))).toList()).contains("cleave");
    }

    @Test void spawnsEnemiesFromEncounter() {
        CombatantBuilder b = new CombatantBuilder(bus, store, sessionService);
        String pid = b.buildPlayer("warrior", 5, List.of());
        CombatantBuilder.Spawn spawn = b.spawnEncounter(pid, "forest_goblin");
        assertThat(spawn.combatId()).startsWith("combat_");
        assertThat(spawn.enemyIds()).isNotEmpty();
        // 敌人满血、有派生 maxHp
        Entity e0 = store.get(spawn.enemyIds().get(0));
        assertThat(e0.getComponent("Health").getInt("hp"))
                .isEqualTo(e0.getComponent("Health").getInt("maxHp"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=CombatantBuilderTest`
Expected: 编译失败 —— `CombatantBuilder` 不存在。

- [ ] **Step 3: 写最小实现**

`CombatantBuilder.java`:
```java
package com.epic.engine.sim;
import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import java.util.*;

/** 复用真路径造战斗单位：
 *  - 玩家：action.confirm_character 建 L1 角色 → 设 Experience/Character.level=N → entity.loaded 重建派生。
 *  - 敌人：combat.start_encounter 从 encounter YAML 生成（真 registerDerivedModifier、满血）。
 *  禁止另写属性数学。 */
public class CombatantBuilder {
    private final EventBus bus; private final EntityStore store; private final SessionService sessions;
    public CombatantBuilder(EventBus bus, EntityStore store, SessionService sessions) {
        this.bus = bus; this.store = store; this.sessions = sessions;
    }

    /** 建一个 class+level 的裸装玩家（sources 暂未实现，Plan 1 只接受空列表）。 */
    public String buildPlayer(String classId, int level, List<Map<String,Object>> sources) {
        if (sources != null && !sources.isEmpty())
            throw new UnsupportedOperationException("sources 在 Plan 1 不支持，预留给 Plan 3");
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
        return playerId;
    }

    public record Spawn(String combatId, List<String> enemyIds) {}

    /** 从 encounter 生成敌人，返回 combatId 与敌人 id 列表（从 player 的 combat: tag 回取 combatId）。 */
    public Spawn spawnEncounter(String playerId, String encounterId) {
        GameEvent ev = new GameEvent("combat.start_encounter");
        ev.set("playerId", playerId);
        ev.set("encounterId", encounterId);
        bus.fire("combat.start_encounter", ev);
        Entity player = store.get(playerId);
        String combatId = null;
        for (Object t : player.getTags().toArray()) {
            String tag = t.toString();
            if (tag.startsWith("combat:")) { combatId = tag.substring(7); break; }
        }
        List<String> enemyIds = new ArrayList<>();
        for (Entity e : store.getByTagAsList("combat:" + combatId))
            if (e.hasTag("enemy")) enemyIds.add(e.getId());
        return new Spawn(combatId, enemyIds);
    }

    /** 战后清理：移除敌人 + combat 实体 + 解 player 的 combat tag（避免污染下一场）。 */
    public void cleanup(String playerId, Spawn spawn) {
        for (String eid : spawn.enemyIds()) { store.remove(eid); }
        store.remove(spawn.combatId());
        Entity player = store.get(playerId);
        if (player != null) {
            player.removeTag("combat:" + spawn.combatId());
            player.removeComponent("CombatPosition");
            store.reindexTags(player);
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=CombatantBuilderTest`
Expected: PASS。若 `sessions.getSession(token).activeCharacterId()` 方法名不符,核对 `SessionService`/`SnapshotController`(后者用 `session.activeCharacterId()`)取对应 getter;断言不变。若 `entity.loaded` 未重算等级,核对 `recalculate_hooks.js` 是否依赖 `Experience.level>1`(已确认)且 `Character.classId` 存在。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/CombatantBuilder.java backend/src/test/java/com/epic/engine/sim/CombatantBuilderTest.java
git commit -m "feat(sim): CombatantBuilder 复用真创建/派生与 encounter 生成"
```

---

## Task 5: SimSetup + 端到端单场/批量(真引擎)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/SimSetup.java`
- Modify: `backend/src/main/java/com/epic/engine/sim/BatchRunner.java`(加一个吃 SimSetup 的真跑入口)
- Test: `backend/src/test/java/com/epic/engine/sim/SimEndToEndTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.epic.engine.sim;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SimEndToEndTest {
    @Autowired EventBus bus;
    @Autowired EntityStore store;
    @Autowired SessionService sessionService;

    @Test void warriorVsGoblin_batchProducesSaneMetrics() {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessionService);
        BatchRunner batch = new BatchRunner();
        SimSetup setup = new SimSetup("warrior", 5, "forest_goblin",
                PolicyKind.HEURISTIC, java.util.List.of(), 50);
        BatchMetrics m = batch.runSimulation(20, setup, builder, store, bus);

        assertThat(m.total()).isEqualTo(20);
        // 三桶率之和=1
        assertThat(m.winRate()+m.lossRate()+m.timeoutRate())
                .isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        // L5 战士打前期哥布林：应几乎必胜、TTK 有限、无 timeout
        assertThat(m.winRate()).isGreaterThan(0.5);
        assertThat(m.timeoutRate()).isEqualTo(0.0);
        assertThat(m.ttkMedian()).isGreaterThan(0.0).isLessThan(50.0);
    }
}
```

> 注:需一个 `PolicyKind` 枚举(HEURISTIC / SCRIPTED)。在 `SimSetup.java` 内顺带定义,或单独文件。下方实现放在 `SimSetup.java` 同文件。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=SimEndToEndTest`
Expected: 编译失败 —— `SimSetup` / `PolicyKind` / `BatchRunner.runSimulation` 不存在。

- [ ] **Step 3: 写最小实现**

`SimSetup.java`:
```java
package com.epic.engine.sim;
import java.util.*;

/** 一场仿真的输入。scriptedSteps 仅 PolicyKind.SCRIPTED 时使用。 */
public record SimSetup(String playerClass, int playerLevel, String encounterId,
                       PolicyKind policyKind, List<String> scriptedSteps, int maxTurns) {
    Policy newPlayerPolicy() {
        return policyKind == PolicyKind.SCRIPTED ? new ScriptedPolicy(scriptedSteps) : new HeuristicPolicy();
    }
}
```

`PolicyKind.java`:
```java
package com.epic.engine.sim;
public enum PolicyKind { HEURISTIC, SCRIPTED }
```

`BatchRunner.java` 增加真跑入口(保留 Task 3 的 supplier 版):
```java
    /** 真跑：每场 build → run → cleanup，返回聚合指标。敌方一律 HeuristicPolicy（v1）。 */
    public BatchMetrics runSimulation(int iterations, SimSetup setup,
                                      CombatantBuilder builder,
                                      com.epic.engine.core.EntityStore store,
                                      com.epic.engine.core.EventBus bus) {
        FightRunner runner = new FightRunner(store, bus);
        return run(iterations, () -> {
            String playerId = builder.buildPlayer(setup.playerClass(), setup.playerLevel(), java.util.List.of());
            CombatantBuilder.Spawn spawn = builder.spawnEncounter(playerId, setup.encounterId());
            java.util.Map<String, Policy> policies = new java.util.HashMap<>();
            policies.put(playerId, setup.newPlayerPolicy());
            for (String eid : spawn.enemyIds()) policies.put(eid, new HeuristicPolicy());
            FightResult r = runner.run(spawn.combatId(),
                    java.util.List.of(playerId), spawn.enemyIds(), policies, setup.maxTurns());
            builder.cleanup(playerId, spawn);
            store.remove(playerId);   // 仿真角色用完即弃，避免 store 膨胀
            return r;
        });
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=SimEndToEndTest`
Expected: PASS。若 timeout_rate>0 或 win_rate 异常低,先单跑一场打印 FightResult 核对:多半是敌方 policy 没收到正确 targetId,或 cleanup 未清干净导致跨场污染(检查 `store.remove(playerId)` 与 enemy 清理)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/sim/SimSetup.java backend/src/main/java/com/epic/engine/sim/PolicyKind.java backend/src/main/java/com/epic/engine/sim/BatchRunner.java backend/src/test/java/com/epic/engine/sim/SimEndToEndTest.java
git commit -m "feat(sim): SimSetup + 端到端真跑批量 runSimulation"
```

---

## Task 6: Report A — early_class_health_check(PM 验收物)

**Files:**
- Create: `backend/src/main/java/com/epic/engine/sim/reports/EarlyClassHealthCheck.java`
- Test: `backend/src/test/java/com/epic/engine/sim/reports/EarlyClassHealthCheckTest.java`

- [ ] **Step 1: 写失败测试**

矩阵:给定职业集 × 等级集 × encounter,产出每格一行(含 win_rate / ttk_median / win_hp_remaining / loss_enemy_hp_remaining)。测试用小矩阵(2 职业 × 2 等级 × 1 怪 × 少量迭代)验证形状与 CSV 表头。

```java
package com.epic.engine.sim.reports;

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
class EarlyClassHealthCheckTest {
    @Autowired EventBus bus;
    @Autowired EntityStore store;
    @Autowired SessionService sessionService;

    @Test void producesMatrixRows_andCsvHeader() {
        EarlyClassHealthCheck report = new EarlyClassHealthCheck(bus, store, sessionService);
        List<EarlyClassHealthCheck.Row> rows = report.run(
                List.of("warrior", "mage"),
                List.of(1, 5),
                List.of("forest_goblin"),
                10);   // iterations per cell
        // 2 职业 × 2 等级 × 1 怪 = 4 行
        assertThat(rows).hasSize(4);
        for (EarlyClassHealthCheck.Row r : rows) {
            assertThat(r.winRate()).isBetween(0.0, 1.0);
        }
        String csv = EarlyClassHealthCheck.toCsv(rows);
        assertThat(csv.split("\n")[0])
                .isEqualTo("class,level,encounter,win_rate,ttk_median,win_hp_remaining,loss_enemy_hp_remaining");
        assertThat(csv.split("\n")).hasSize(5);   // 表头 + 4 行
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=EarlyClassHealthCheckTest`
Expected: 编译失败 —— `EarlyClassHealthCheck` 不存在。

- [ ] **Step 3: 写最小实现**

`EarlyClassHealthCheck.java`:
```java
package com.epic.engine.sim.reports;
import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import com.epic.engine.sim.*;
import java.util.*;

/** 报告 A：五职业 × 等级 × 前期怪 的前期体检表。 */
public class EarlyClassHealthCheck {
    private final EventBus bus; private final EntityStore store; private final SessionService sessions;
    public EarlyClassHealthCheck(EventBus bus, EntityStore store, SessionService sessions) {
        this.bus = bus; this.store = store; this.sessions = sessions;
    }

    public record Row(String playerClass, int level, String encounter,
                      double winRate, double ttkMedian,
                      double winHpRemaining, double lossEnemyHpRemaining) {}

    public List<Row> run(List<String> classes, List<Integer> levels,
                         List<String> encounters, int iterations) {
        CombatantBuilder builder = new CombatantBuilder(bus, store, sessions);
        BatchRunner batch = new BatchRunner();
        List<Row> rows = new ArrayList<>();
        for (String cls : classes)
            for (int lvl : levels)
                for (String enc : encounters) {
                    SimSetup setup = new SimSetup(cls, lvl, enc, PolicyKind.HEURISTIC, List.of(), 50);
                    BatchMetrics m = batch.runSimulation(iterations, setup, builder, store, bus);
                    rows.add(new Row(cls, lvl, enc, m.winRate(), m.ttkMedian(),
                            m.winHpRemainingMedian(), m.lossEnemyHpRemainingMedian()));
                }
        return rows;
    }

    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder(
                "class,level,encounter,win_rate,ttk_median,win_hp_remaining,loss_enemy_hp_remaining\n");
        for (Row r : rows)
            sb.append(String.format(Locale.US, "%s,%d,%s,%.3f,%.1f,%.3f,%.3f%n",
                    r.playerClass(), r.level(), r.encounter(), r.winRate(), r.ttkMedian(),
                    nanToZero(r.winHpRemaining()), nanToZero(r.lossEnemyHpRemaining())));
        return sb.toString();
    }
    private static double nanToZero(double d) { return Double.isNaN(d) ? 0.0 : d; }
}
```

> 注:`String.format(... "%n")` 在断言里按 `\n` 切分可能因平台换行不一致。实现中统一用 `\n`(把 `%n` 改为 `\n`,`%.3f...\n`),保证测试 `split("\n")` 稳定。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=EarlyClassHealthCheckTest`
Expected: PASS（4 行 + CSV 表头精确匹配）。

- [ ] **Step 5: 全量回归 + 提交**

Run: `cd backend && mvn -q test`
Expected: 全绿(原 147 + 新增 sim 测试)。

```bash
git add backend/src/main/java/com/epic/engine/sim/reports backend/src/test/java/com/epic/engine/sim/reports
git commit -m "feat(sim): Report A early_class_health_check 矩阵 + CSV"
```

---

## Self-Review(对照 spec §2 验收物 + §6 口径)

- **报告 A `early_class_health_check`(spec §2.A):** Task 6 覆盖,列含 win_rate/ttk_median/win_hp_remaining/loss_enemy_hp_remaining ✓。曲线 5 `class_level_1_10_health_check` 的数据即报告 A 的行(x=level,series=class)——Plan 1 产出表数据,绘图/曲线导出归 Plan 3。
- **timeout 不判负(spec §6.1):** `FightOutcome.TIMEOUT` 独立桶,`BatchMetrics` 三桶率之和=1 ✓(BatchMetricsTest 断言)。
- **复用真 ModifierChain/derived(spec §6.4):** `CombatantBuilder` 经 `action.confirm_character` + `entity.loaded` + `combat.start_encounter`,零自写数学 ✓。
- **policy 生成合法 command(spec §6.3):** `{type,targetId}`,scripted 非法步骤回退 basic_attack ✓;MP/沉默/AOE 的完整合法性校验 = Plan 2+(v1 只单目标 + basic_attack/脚本技能)。
- **报告 B/C、sweep、offense/defense 指数、damage breakdown、damage_variance:** 不在 Plan 1,见 Roadmap。
- **占位扫描:** 无 TBD/TODO;每步含完整代码与命令 ✓。
- **类型一致性:** `FightResult(outcome,rounds,playerHpFrac,enemyHpFrac)`、`BatchMetrics.of`、`Policy.selectCommand`、`CombatantBuilder.Spawn(combatId,enemyIds)`、`SimSetup`/`PolicyKind`、`BatchRunner.run`/`runSimulation`、`EarlyClassHealthCheck.Row`/`toCsv` 跨任务签名一致 ✓。
- **已知 v1 简化(诚实记录):** HeuristicPolicy 仅 basic_attack 集火(不评估技能伤害),故报告 A 测的是"裸普攻前期体检";若要"靠 1-2 技能"的体检,用 ScriptedPolicy 配每职业出招表(Task 2 已支持)。完整启发式技能轮换 = Plan 2。

---

## Roadmap(后续两计划,本次不实现)

- **Plan 2 — 仪表盘解释力 + 方差源(spec UC4 + §7):** 在真 `Skill.mitigate` 处加 instrumentation,产出 `damage_by_skill / damage_taken_by_source / mitigation_saved / kill_source / 平均死亡回合`;在真出伤/减伤路径加 `damage_variance`(默认 0)。让 PM 能解释"为什么 43%"。
- **Plan 3 — sweep + 基线指数 + 报告 B/C + 五曲线(spec UC2/UC3 + §2):** 单参数 sweep 框架;固定参照档 `ref_dummy_offense`/`ref_attacker_defense` + offense/defense 指数;`sources[]`(modifier/equipment kind)落地;报告 B `armor_k_sweep`、C `baseline_vs_variant`、五条曲线数据导出。产出 K/cap 初值。
- **HeuristicPolicy 技能轮换、search policy、矩阵跑、LLM=scripted 喂线**:按 spec §9 为后续扩展位。
