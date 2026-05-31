# Feature #1 — Skill Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 13 hand-coded skill JS handlers with a data-driven layer (one shared `Skill` library + a generic dispatcher + per-skill YAML specs), keeping all observable combat output byte-identical, guarded by a golden-snapshot safety net.

**Architecture:** A global `Skill` namespace object (single GraalJS context = shared global) provides `context` / `resolveTargets` / `computeDamage` (pure) / `dealDamage` / `applyBuffFromSpec` / `present` / `resolveAnimation`. A generic dispatcher listens on `combat.unit_action`, reads the skill's YAML spec, and runs the registered `effect` executor. Skills with an `effect:` key carry zero JS; flee and any inexpressible AOE animation stay bespoke. The existing `combat_events.js` presentation logic is extracted into `Skill.resolveAnimation` and reused, not reinvented.

**Tech Stack:** Java 21, GraalJS (mods are JS), SnakeYAML, JUnit 5 + AssertJ, Jackson (for golden serialization, already on the Spring Boot classpath).

**Spec:** `docs/superpowers/specs/2026-05-30-feature1-skill-architecture-refactor-design.md`
**Parent decisions:** `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md` §1

**Conventions for every task:**
- All commands run from the `backend/` directory (its working dir resolves `../mods/...` and `src/test/...`, matching the existing `NewCombatIntegrationTest`).
- Mod code lives under `mods/base-rules/`. No Java engine changes in this feature.
- Commit after each task with the message shown.

---

## File Structure

**New (mod):**
- `mods/base-rules/handlers/skill/00_skill_lib.js` — the `Skill` global namespace (all shared helpers).
- `mods/base-rules/handlers/skill/01_effects.js` — registers the 5 effect executors.
- `mods/base-rules/handlers/skill/02_dispatch.js` — the generic `combat.unit_action` dispatcher.

> Load order: `ModuleLoader` walks `handlers/` fully before `skills/`, sorting by path. `handlers/skill/00…01…02` load after `handlers/combat/…`; that's fine because handler *bodies* run at event-fire time, by which point all files are loaded and the global `Skill` exists.

**New (test):**
- `backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java` — builds a deterministic combat scene, fires one skill, serializes the resulting `CombatEvents` queue + `CombatLog` to a stable JSON string.
- `backend/src/test/java/com/epic/engine/combat/SkillFidelityTest.java` — golden assertions (one case per skill).
- `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java` — unit tests for `Skill.*` helpers (purity, targeting, etc.).
- `backend/src/test/resources/golden/<skill>.json` — captured baselines (13 files).

**Modified (mod):**
- `mods/base-rules/skills/*.yaml` — 12 skills gain spec keys (`effect`, `damage`, `targeting.mode/field/pattern`, `debuff`/`buff`, `log`).
- `mods/base-rules/skills/*.js` — 12 skill handlers deleted (flee.js kept).
- `mods/base-rules/handlers/combat/combat_events.js` — `resolveSkillAnimation` body delegates to `Skill.resolveAnimation`.

---

## Task 1: Golden baseline harness + capture (the safety net, BEFORE any refactor)

**Files:**
- Create: `backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java`
- Create: `backend/src/test/java/com/epic/engine/combat/SkillFidelityTest.java`
- Create: `backend/src/test/resources/golden/` (13 `.json` files, generated)

- [ ] **Step 1: Write the harness** that loads the full combat stack, builds a fixed scene, fires one skill via `combat.unit_action`, and returns a stable JSON of the queue.

`SkillFidelityHarness.java`:

```java
package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Deterministic single-skill capture for fidelity testing. */
public class SkillFidelityHarness implements AutoCloseable {
    final EventBus bus = new EventBus();
    final EntityStore store = new EntityStore();
    final ScriptRuntime runtime;
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public SkillFidelityHarness() throws Exception {
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.bindService("buffs", new BuffService(bus, store));
        // Load combat handlers
        Path h = Path.of("../mods/base-rules/handlers/combat");
        for (String f : new String[]{"initiative.js","damage_calc.js","death_check.js",
                "combat_flow.js","combat_events.js","combat_log.js"}) {
            runtime.execute(Files.readString(h.resolve(f)), f);
        }
        // Load skill engine (skip on pre-refactor capture; present after Task 6)
        Path skl = Path.of("../mods/base-rules/handlers/skill");
        if (Files.isDirectory(skl)) {
            for (String f : new String[]{"00_skill_lib.js","01_effects.js","02_dispatch.js"}) {
                if (Files.exists(skl.resolve(f)))
                    runtime.execute(Files.readString(skl.resolve(f)), f);
            }
        }
        // Load all skill .js that still exist (bespoke handlers)
        try (var files = Files.list(Path.of("../mods/base-rules/skills"))) {
            files.filter(p -> p.toString().endsWith(".js")).sorted().forEach(p -> {
                try { runtime.execute(Files.readString(p), p.getFileName().toString()); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }

    /** Build a fresh scene: mage + 3 enemies in a column, fixed stats. */
    public void scene() {
        store.clear(); // ensure EntityStore has a clear(); if not, new store per call
        addUnit("mage", "法师", 100, 10, 5, 6, true, "FRONT", 0);
        addUnit("goblin1", "哥布林甲", 200, 6, 2, 3, false, "FRONT", 0);
        addUnit("goblin2", "哥布林乙", 200, 6, 2, 3, false, "MID", 0);
        addUnit("goblin3", "哥布林丙", 200, 6, 2, 3, false, "BACK", 0);
        Entity combat = new Entity("battle1");
        Component st = new Component("CombatState"); st.set("round", 1); st.set("phase", "RESOLVE");
        combat.addComponent(st);
        Component ce = new Component("CombatEvents"); ce.set("queue", new ArrayList<>());
        combat.addComponent(ce);
        Component cl = new Component("CombatLog"); cl.set("entries", new ArrayList<>());
        combat.addComponent(cl);
        store.add(combat);
    }

    private void addUnit(String id, String name, int hp, int atk, int def, int spd,
                         boolean player, String row, int slot) {
        Entity e = new Entity(id);
        Component h = new Component("Health"); h.set("hp", hp); h.set("maxHp", hp); e.addComponent(h);
        Component s = new Component("CombatStats"); s.set("attack", atk); s.set("defense", def); s.set("speed", spd); e.addComponent(s);
        Component n = new Component("Name"); n.set("value", name); e.addComponent(n);
        Component p = new Component("CombatPosition"); p.set("row", row); p.set("slot", slot); e.addComponent(p);
        e.addTag(player ? "player" : "enemy");
        e.addTag("combat:battle1");
        store.add(e);
    }

    /** Fire one skill from the mage and return the queue as stable JSON. */
    public String fire(String type, String targetId) throws Exception {
        scene();
        Map<String,Object> cmd = new HashMap<>();
        cmd.put("type", type);
        if (targetId != null) cmd.put("targetId", targetId);
        GameEvent e = new GameEvent("combat.unit_action");
        e.set("combatId", "battle1");
        e.set("actorId", "mage");
        e.set("command", cmd);
        bus.fire("combat.unit_action", e);
        Object queue = store.get("battle1").getComponent("CombatEvents").get("queue");
        return MAPPER.writeValueAsString(queue);
    }

    public String goldenPath(String skill) { return "src/test/resources/golden/" + skill + ".json"; }

    @Override public void close() { runtime.close(); }
}
```

> If `EntityStore` has no `clear()`, build a new `SkillFidelityHarness` per skill instead of calling `scene()` repeatedly. Verify the method exists; adapt minimally.

- [ ] **Step 2: Write the capture+assert test.** On first run it writes goldens if missing; thereafter it asserts equality.

`SkillFidelityTest.java`:

```java
package com.epic.engine.combat;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillFidelityTest {
    // skill → target (null = no target / self / group)
    static final String[][] CASES = {
        {"basic_attack","goblin1"}, {"crescent_slash","goblin1"}, {"ice_beam","goblin1"},
        {"pulse_wave","goblin1"}, {"fireball","goblin1"}, {"poison_dart","goblin1"},
        {"cleave","goblin1"}, {"piercing_ray","goblin1"}, {"cross_blast","goblin1"},
        {"light_field","goblin1"}, {"heal",null}, {"defend",null},
        {"war_cry",null}, {"curse",null}
    };

    @Test
    void allSkills_matchGolden() throws Exception {
        StringBuilder diffs = new StringBuilder();
        for (String[] c : CASES) {
            try (SkillFidelityHarness h = new SkillFidelityHarness()) {
                String actual = h.fire(c[0], c[1]);
                Path golden = Path.of(h.goldenPath(c[0]));
                if (!Files.exists(golden)) {
                    Files.createDirectories(golden.getParent());
                    Files.writeString(golden, actual);
                    diffs.append("CAPTURED new golden: ").append(c[0]).append("\n");
                } else {
                    String expected = Files.readString(golden);
                    if (!expected.equals(actual)) {
                        diffs.append("MISMATCH ").append(c[0]).append(":\n")
                             .append("--- expected ---\n").append(expected)
                             .append("\n--- actual ---\n").append(actual).append("\n");
                    }
                }
            }
        }
        assertThat(diffs.toString()).isEmpty();
    }
}
```

- [ ] **Step 3: Run once to capture baselines.**

Run: `cd backend && mvn test -Dtest=SkillFidelityTest`
Expected: FAIL with "CAPTURED new golden: …" for all 13 (it captured and reported). The 13 `golden/*.json` now exist. (flee has no combat output; it's excluded from CASES.)

- [ ] **Step 4: Re-run to confirm green against captured baselines.**

Run: `mvn test -Dtest=SkillFidelityTest`
Expected: PASS (actual == captured golden for every skill). This proves the harness is deterministic.

- [ ] **Step 5: Commit the safety net.**

```bash
git add backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java \
        backend/src/test/java/com/epic/engine/combat/SkillFidelityTest.java \
        backend/src/test/resources/golden/
git commit -m "test: golden baseline harness capturing all 13 skills pre-refactor"
```

---

## Task 2: `Skill` lib skeleton — `context` + `loadSpec`

**Files:**
- Create: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Create: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`

- [ ] **Step 1: Write the failing test** that loads the lib and checks `context`.

`SkillLibTest.java`:

```java
package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillLibTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;

    @BeforeEach void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.bindService("buffs", new BuffService(bus, store));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/combat/damage_calc.js")), "damage_calc.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/00_skill_lib.js")), "00_skill_lib.js");
    }
    @AfterEach void tearDown() { rt.close(); }

    Entity mkUnit(String id, String name, boolean player) {
        Entity e = new Entity(id);
        Component h = new Component("Health"); h.set("hp",100); h.set("maxHp",100); e.addComponent(h);
        Component s = new Component("CombatStats"); s.set("attack",10); s.set("defense",5); s.set("speed",5); e.addComponent(s);
        Component n = new Component("Name"); n.set("value",name); e.addComponent(n);
        e.addTag(player?"player":"enemy"); e.addTag("combat:b1"); store.add(e); return e;
    }

    @Test
    void context_resolvesCasterFields() {
        mkUnit("mage","法师",true);
        // expose a tiny probe: a test-only global function reading Skill.context
        Map<String,Object> cmd = new HashMap<>(); cmd.put("type","fireball");
        GameEvent e = new GameEvent("combat.unit_action");
        e.set("actorId","mage"); e.set("combatId","b1"); e.set("command", cmd);
        // call Skill.context via a script that stashes the result on a global
        rt.execute("var __ctx = Skill.context(arguments0);", "probe.js"); // see note
        // Simplest reliable probe: evaluate an expression. Use the helper below instead.
        assertThat(true).isTrue();
    }
}
```

> Probing a JS global from Java is awkward. Use this reliable pattern: have the lib expose pure helpers that take plain args, and test them by executing a script that calls the helper and writes a primitive to a Java-visible place. The cleanest is to assert through **behavior** (Tasks 5–9 cover end-to-end via the golden harness). For Task 2–4, test the lib by executing small JS snippets that `throw` on assertion failure:

Replace the test bodies in this file with the snippet-runner pattern:

```java
    void js(String script) { rt.execute(
        "(function(){ " + script + " })();", "assert.js"); }

    @Test
    void context_resolvesCasterFields() {
        mkUnit("mage","法师",true);
        js("var ev = engine.newEvent('combat.unit_action');" +
           "ev.set('actorId','mage'); ev.set('combatId','b1');" +
           "var cmd = {}; cmd.type='fireball'; ev.set('command', cmd);" +
           "var c = Skill.context(ev);" +
           "if (c.actorId !== 'mage') throw 'actorId';" +
           "if (c.combatId !== 'b1') throw 'combatId';" +
           "if (c.casterName !== '法师') throw 'name='+c.casterName;" +
           "if (c.casterSide !== 'player') throw 'side='+c.casterSide;");
    }
```

> A thrown JS string surfaces as a `PolyglotException` and fails the test — adequate for lib unit tests. `engine.newEvent(...).set(...)` is available; `event.get(...)` in JS reads it back.

Run: `mvn test -Dtest=SkillLibTest#context_resolvesCasterFields`
Expected: FAIL — `Skill is not defined`.

- [ ] **Step 2: Implement the lib skeleton.**

`00_skill_lib.js`:

```js
var Skill = {
  _specs: {},
  loadSpec: function(type) {
    if (this._specs[type] === undefined) {
      this._specs[type] = engine.loadYaml("skills/" + type + ".yaml");
    }
    return this._specs[type];
  },

  effects: {},
  registerEffect: function(name, fn) { this.effects[name] = fn; },

  context: function(event) {
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var caster = store.get(actorId);
    var casterName = (caster !== null && caster.hasComponent("Name"))
        ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = (caster !== null && caster.hasTag("player")) ? "player" : "enemy";
    return {
      actorId: actorId, combatId: combatId, caster: caster,
      casterName: casterName, casterSide: casterSide, cmd: event.get("command")
    };
  }
};
```

Run: `mvn test -Dtest=SkillLibTest#context_resolvesCasterFields`
Expected: PASS.

- [ ] **Step 3: Commit.**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js \
        backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skills): Skill lib skeleton — context + loadSpec"
```

---

## Task 3: `computeDamage` — pure function + purity/reuse test

This is the single-source-of-truth damage function the skill panel / AI will reuse. The test asserts both the value AND that calling it changes no state (the "no-side-effect" check the design promises).

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`

- [ ] **Step 1: Write the failing tests.**

Add to `SkillLibTest`:

```java
    @Test
    void computeDamage_flatAdd() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});" +
           "if (dmg !== 18) throw 'dmg='+dmg;"); // attack 10 + 8
    }

    @Test
    void computeDamage_isPure_noHpChange() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        js("Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});");
        // calling computeDamage must NOT mutate target hp
        assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    @Test
    void computeDamage_viaDamageCalc_subtractsDefense() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false); // defense 5
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {via_damage_calc:true});" +
           "if (dmg !== 5) throw 'dmg='+dmg;"); // 10 attack - 5 defense
    }
```

Run: `mvn test -Dtest=SkillLibTest#computeDamage_flatAdd`
Expected: FAIL — `Skill.computeDamage is not a function`.

- [ ] **Step 2: Implement `computeDamage`.**

Add to `Skill` in `00_skill_lib.js`:

```js
  computeDamage: function(caster, target, dmgSpec) {
    if (dmgSpec == null) return 0;
    if (dmgSpec.via_damage_calc) {
      var ev = engine.newEvent("combat.damage_calc");
      ev.set("attackerId", caster.getId());
      ev.set("targetId", target.getId());
      engine.fire("combat.damage_calc", ev);
      return ev.get("damage");
    }
    var statName = dmgSpec.base || "attack";
    var base = caster.hasComponent("CombatStats")
        ? caster.getComponent("CombatStats").getInt(statName) : 5;
    return base + (dmgSpec.add || 0);
    // Feature #2 appends scaling here; same function feeds combat + panel + AI.
  },
```

Run: `mvn test -Dtest=SkillLibTest#computeDamage_flatAdd -Dtest=SkillLibTest`
Expected: PASS for all three computeDamage tests.

> Note: `via_damage_calc` fires an event (reads back damage) — a deliberate fidelity compromise for basic_attack; the pure tests for it assert value only. The flat path is fully pure (proven by `computeDamage_isPure_noHpChange`).

- [ ] **Step 3: Commit.**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js \
        backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skills): pure computeDamage (flat + via_damage_calc), with purity test"
```

---

## Task 4: `resolveTargets` — offset patterns + group/self

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`

- [ ] **Step 1: Write the failing tests.** Build a positioned enemy column and assert which entities resolve.

Add to `SkillLibTest` (extend `mkUnit` with position, or add a positioned variant):

```java
    Entity mkPos(String id, boolean player, String row, int slot) {
        Entity e = mkUnit(id, id, player);
        Component p = new Component("CombatPosition"); p.set("row", row); p.set("slot", slot);
        e.addComponent(p); return e;
    }

    @Test
    void resolveTargets_single() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{targetId:'g1'}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'pattern',field:'enemy',pattern:[[0,0]]}});" +
           "if (r.length !== 1) throw 'len='+r.length;" +
           "if (r[0].entity.getId() !== 'g1') throw 'id='+r[0].entity.getId();");
    }

    @Test
    void resolveTargets_column() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0); mkPos("g3", false, "BACK", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{targetId:'g2'}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'pattern',field:'enemy',pattern:[[-1,0],[0,0],[1,0]]}});" +
           "if (r.length !== 3) throw 'len='+r.length;");
    }

    @Test
    void resolveTargets_group_allEnemies() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'group',field:'enemy'}});" +
           "if (r.length !== 2) throw 'len='+r.length;");
    }

    @Test
    void resolveTargets_self() {
        mkUnit("mage","法师",true);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'self'}});" +
           "if (r.length !== 1 || r[0].entity.getId() !== 'mage') throw 'self';");
    }
```

Run: `mvn test -Dtest=SkillLibTest#resolveTargets_single`
Expected: FAIL — `Skill.resolveTargets is not a function`.

- [ ] **Step 2: Implement `resolveTargets`.** Reuses light_field's row-index/slot offset model.

Add to `Skill`:

```js
  _rowOrder: ["FRONT", "MID", "BACK"],

  _cellOf: function(entity) {
    var p = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
    var row = p !== null ? p.getString("row") : "FRONT";
    var slot = p !== null ? p.getInt("slot") : 0;
    return { rowIdx: this._rowOrder.indexOf(row), slot: slot };
  },

  resolveTargets: function(ctx, spec) {
    var t = spec.targeting;
    var mode = (t && t.mode) ? t.mode : "pattern";
    var out = [];
    if (mode === "self") {
      return [{ entity: ctx.caster, cell: this._cellOf(ctx.caster) }];
    }
    var field = t.field;                       // "enemy" | "ally"
    var combatants = store.getByTagAsList("combat:" + ctx.combatId);
    var live = [];
    for (var i = 0; i < combatants.size(); i++) {
      var e = combatants.get(i);
      var isAlly = e.hasTag("player");
      var sideMatch = (field === "ally") ? isAlly : !isAlly;
      if (!sideMatch) continue;
      if (!e.hasComponent("Health") || e.getComponent("Health").getInt("hp") <= 0) continue;
      live.push(e);
    }
    if (mode === "group") {
      for (var k = 0; k < live.length; k++) out.push({ entity: live[k], cell: this._cellOf(live[k]) });
      return out;
    }
    // mode === "pattern": center from cmd.targetId (fallback targetRow/targetCol)
    var center;
    var tid = ctx.cmd.targetId !== undefined ? ctx.cmd.targetId : (ctx.cmd.get ? ctx.cmd.get("targetId") : null);
    var centerEntity = tid ? store.get(tid) : null;
    if (centerEntity !== null && centerEntity !== undefined) {
      center = this._cellOf(centerEntity);
    } else {
      var r = ctx.cmd.targetRow, c = ctx.cmd.targetCol;
      center = { slot: parseInt(r) || 0, rowIdx: parseInt(c) || 0 };
    }
    var pattern = t.pattern || [[0,0]];
    for (var j = 0; j < live.length; j++) {
      var cell = this._cellOf(live[j]);
      for (var o = 0; o < pattern.length; o++) {
        // pattern entries are [rowIdxDelta, slotDelta]
        if (cell.rowIdx === center.rowIdx + pattern[o][0] && cell.slot === center.slot + pattern[o][1]) {
          out.push({ entity: live[j], cell: cell });
          break;
        }
      }
    }
    return out;
  },
```

> The pattern axis convention `[rowIdxDelta, slotDelta]` must match each migrated skill's offsets. Cross-check against the old JS per skill in Task 9 (cleave iterated row, piercing_ray iterated slot, light_field used `[slotΔ,rowΔ]` order — normalize when authoring the YAML pattern, and the goldens will catch any mismatch).

Run: `mvn test -Dtest=SkillLibTest`
Expected: PASS for all resolveTargets tests.

- [ ] **Step 3: Commit.**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js \
        backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skills): resolveTargets — offset patterns + group/self"
```

---

## Task 5: Apply + present layer — `dealDamage`, `applyBuffFromSpec`, `resolveAnimation`, `present`

This task extracts `combat_events.js`'s presentation into `Skill.resolveAnimation` and adds `present`. Single-target fidelity is validated end-to-end in Task 7; here we add the functions and a focused unit test.

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`
- Modify: `mods/base-rules/handlers/combat/combat_events.js`
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`

- [ ] **Step 1: Write a failing test** for `dealDamage` (mutates hp, fires damage_dealt with skipLog).

```java
    @Test
    void dealDamage_mutatesHp_andFiresDamageDealt() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        // record damage_dealt
        js("globalThis.__dealt = false; engine.on('combat.damage_dealt', 10, function(e){" +
           "  if (e.has && e.has('skipLog')) globalThis.__dealt = true; });");
        js("var ctx = {actorId:'mage', combatId:'b1', caster: store.get('mage')};" +
           "Skill.dealDamage(ctx, store.get('goblin'), 30, 'fireball');");
        assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(70);
        js("if (!globalThis.__dealt) throw 'damage_dealt not fired with skipLog';");
    }
```

Run: `mvn test -Dtest=SkillLibTest#dealDamage_mutatesHp_andFiresDamageDealt`
Expected: FAIL — `Skill.dealDamage is not a function`.

- [ ] **Step 2: Implement apply + present helpers.**

Add to `Skill`:

```js
  dealDamage: function(ctx, target, amount, skillId, opts) {
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - amount));
    var ev = engine.newEvent("combat.damage_dealt");
    ev.set("attackerId", ctx.actorId);
    ev.set("targetId", target.getId());
    ev.set("damage", amount);
    ev.set("combatId", ctx.combatId);
    ev.set("skillId", skillId);
    ev.set("skipLog", true);                 // present() is the sole emitter for skills
    engine.fire("combat.damage_dealt", ev);
  },

  applyBuffFromSpec: function(ctx, target, buffSpec) {
    var data = engine.newMap();
    var keys = Object.keys(buffSpec.data || {});
    for (var i = 0; i < keys.length; i++) data.put(keys[i], buffSpec.data[keys[i]]);
    if (buffSpec.data && buffSpec.data.source === "@caster") data.put("source", ctx.actorId);
    buffs.applyBuff(target.getId(), buffSpec.id, data);
  },

  // hp_change effect entry (flat shape — the unified form)
  _hpEffect: function(target, amount) {
    var eff = engine.newMap();
    eff.put("target", target.getId());
    eff.put("type", "hp_change");
    eff.put("amount", -amount);
    eff.put("hp", target.getComponent("Health").getInt("hp"));
    eff.put("maxHp", target.getComponent("Health").getInt("maxHp"));
    return eff;
  },

  _buffEffect: function(target) {
    var eff = engine.newMap();
    eff.put("type", "buff_applied");
    eff.put("target", target.getId());
    return eff;
  },

  // Flat-format animation resolution (extracted from combat_events.resolveSkillAnimation),
  // generalized for multi-target (see Task 9 for the multi-target branch).
  resolveAnimation: function(spec, ctx, results) {
    var animDef = spec.animation;
    if (animDef == null) return engine.newList();
    var single = (results.length === 1);
    var out = engine.newList();
    for (var i = 0; i < animDef.length; i++) {
      var step = animDef[i];
      // per-target steps (filled in Task 9); single-target path = token replace
      var anim = engine.newMap();
      for (var key in step) {
        var val = step[key];
        if (val === "actor") val = ctx.actorId;
        else if (val === "target") val = (results[0] ? results[0].entity.getId() : null);
        else if (val === "actor_side") val = ctx.casterSide;
        anim.put(key, val);
      }
      out.add(anim);
    }
    return out;
  },

  present: function(ctx, spec, results, damages) {
    var log = engine.newList();
    var effects = engine.newList();
    // log + effects
    var L = spec.log || {};
    if (results.length <= 1) {
      var tgt = results[0] ? results[0].entity : ctx.caster;
      log.add(this._renderLog(L.template, ctx, tgt, damages ? damages[0] : 0));
      if (damages) effects.add(this._hpEffect(tgt, damages[0]));
    } else {
      for (var i = 0; i < results.length; i++) {
        log.add(this._renderLog(L.per_target, ctx, results[i].entity, damages ? damages[i] : 0));
        if (damages) effects.add(this._hpEffect(results[i].entity, damages[i]));
      }
    }
    var animation = this.resolveAnimation(spec, ctx, results);
    var data = engine.newMap();
    data.put("log", log); data.put("effects", effects); data.put("animation", animation);
    engine.combatEvent(ctx.combatId, data);
  },

  // build a segment list from a "{caster}/{target}/{damage}" template
  _renderLog: function(tmpl, ctx, target, damage) {
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : target.getId();
    var targetSide = target.hasTag("player") ? "player" : "enemy";
    // minimal templating: split on known tokens, emit colored segments
    var seg = engine.newList();
    var parts = (tmpl || "{caster} → {target} {damage}").split(/(\{caster\}|\{target\}|\{damage\})/);
    for (var i = 0; i < parts.length; i++) {
      var p = parts[i]; if (p === "") continue;
      var s = engine.newMap();
      if (p === "{caster}") { s.put("text", ctx.casterName); s.put("color", ctx.casterSide); }
      else if (p === "{target}") { s.put("text", targetName); s.put("color", targetSide); }
      else if (p === "{damage}") { s.put("text", "" + damage); s.put("color", "damage"); }
      else { s.put("text", p); s.put("color", "text"); }
      seg.add(s);
    }
    return seg;
  },
```

- [ ] **Step 3: Point `combat_events.js` at the shared resolver** (single implementation). Replace the body of `resolveSkillAnimation` to delegate when `Skill` is present, keeping the old code as fallback:

In `mods/base-rules/handlers/combat/combat_events.js`, change `resolveSkillAnimation` to:

```js
function resolveSkillAnimation(skillId, actorId, targetId) {
    var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
    if (skillDef === null) return null;
    var animDef = skillDef.get("animation");
    if (animDef === null || animDef.size() === 0) return null;
    var actor = store.get(actorId);
    var actorSide = actor !== null && actor.hasTag("player") ? "player" : "enemy";
    var resolved = engine.newList();
    for (var i = 0; i < animDef.size(); i++) {
        var step = animDef.get(i);
        var anim = engine.newMap();
        var keys = step.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.next();
            var val = step.get(key);
            if (val === "actor") val = actorId;
            else if (val === "target") val = targetId;
            else if (val === "actor_side") val = actorSide;
            anim.put(key, val);
        }
        resolved.add(anim);
    }
    return resolved;
}
```

> Leave this handler functionally intact — it still serves non-skill damage (e.g. buff-tick damage that fires `damage_dealt` without `skipLog`). The shared `Skill.resolveAnimation` mirrors its single-target logic; the two are kept consistent by the goldens. (Full unification of the two into one function is deferred — it is not required for behavior-unchanged and would widen blast radius.)

Run: `mvn test -Dtest=SkillLibTest#dealDamage_mutatesHp_andFiresDamageDealt`
Expected: PASS.

- [ ] **Step 4: Commit.**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js \
        mods/base-rules/handlers/combat/combat_events.js \
        backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skills): apply + present layer (dealDamage, applyBuffFromSpec, present, resolveAnimation)"
```

---

## Task 6: Effect registry + generic dispatcher

**Files:**
- Create: `mods/base-rules/handlers/skill/01_effects.js`
- Create: `mods/base-rules/handlers/skill/02_dispatch.js`

- [ ] **Step 1: Write the 5 effect executors.**

`01_effects.js`:

```js
Skill.registerEffect("damage_only", function(ctx, spec, results) {
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    Skill.dealDamage(ctx, results[i].entity, dmg, spec.id);
    damages.push(dmg);
  }
  Skill.present(ctx, spec, results, damages);
});

Skill.registerEffect("damage_with_debuff", function(ctx, spec, results) {
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    Skill.dealDamage(ctx, results[i].entity, dmg, spec.id);
    Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
    damages.push(dmg);
  }
  Skill.present(ctx, spec, results, damages); // present appends buff_applied effects (see note)
});

Skill.registerEffect("debuff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
  Skill.present(ctx, spec, results, null);
});

Skill.registerEffect("buff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.buff);
  Skill.present(ctx, spec, results, null);
});

Skill.registerEffect("heal", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) {
    var h = results[i].entity.getComponent("Health");
    h.set("hp", Math.min(h.getInt("maxHp"), h.getInt("hp") + spec.heal));
  }
  Skill.present(ctx, spec, results, null);
});
```

> `present` must append a `buff_applied` effect per target when the effect applied a buff. Extend `present` to accept the targets that got buffed, OR have the buff/debuff executors add `Skill._buffEffect(...)` into the event. Reconcile exact effect ordering against the goldens in Tasks 7–9; adjust `present`'s signature if needed (e.g. pass an `opts={buffApplied:true}`).

- [ ] **Step 2: Write the dispatcher.**

`02_dispatch.js`:

```js
engine.on("combat.unit_action", 80, function(event) {
  var cmd = event.get("command");
  var type = (typeof cmd.get === "function") ? cmd.get("type") : cmd.type;
  var spec = Skill.loadSpec(type);
  if (spec === null || spec.get("effect") === undefined || spec.get("effect") === null) return; // bespoke / no spec
  var jsSpec = Skill._toJs(spec);          // convert SnakeYAML Map → plain JS object (see note)
  var ctx = Skill.context(event);
  var results = Skill.resolveTargets(ctx, jsSpec);
  var fn = Skill.effects[jsSpec.effect];
  if (fn == null) { return; }
  fn(ctx, jsSpec, results);
});
```

> `engine.loadYaml` returns Java `Map`/`List` (SnakeYAML). The helpers in Tasks 3–5 assume plain JS objects (`spec.targeting.pattern`, `dmgSpec.add`). Add `Skill._toJs(value)` to `00_skill_lib.js` that deep-converts Java Map/List to JS object/array (recursively: `Map`→`{}`, `List`→`[]`, scalars passthrough). Apply it once in the dispatcher. This keeps every other helper plain-JS and testable. **Update the Task 2–5 unit tests' inline specs (already plain JS) — they remain valid.**

- [ ] **Step 3: Implement `_toJs` and register-time wiring.** Add to `00_skill_lib.js`:

```js
  _toJs: function(v) {
    if (v === null || v === undefined) return v;
    if (typeof v.entrySet === "function") {            // java.util.Map
      var o = {}; var it = v.keySet().iterator();
      while (it.hasNext()) { var k = it.next(); o[k] = this._toJs(v.get(k)); }
      return o;
    }
    if (typeof v.size === "function" && typeof v.get === "function") { // java.util.List
      var a = []; for (var i = 0; i < v.size(); i++) a.push(this._toJs(v.get(i)));
      return a;
    }
    return v;
  },
```

- [ ] **Step 4: Verify the engine still boots & nothing double-fires yet.** No skill has `effect:` in YAML yet, so the dispatcher is a no-op and all 13 still run via their bespoke JS.

Run: `mvn test -Dtest=SkillFidelityTest`
Expected: PASS — goldens unchanged (dispatcher inert until specs declare `effect:`).

- [ ] **Step 5: Commit.**

```bash
git add mods/base-rules/handlers/skill/01_effects.js \
        mods/base-rules/handlers/skill/02_dispatch.js \
        mods/base-rules/handlers/skill/00_skill_lib.js
git commit -m "feat(skills): effect registry + generic dispatcher (inert until specs opt in)"
```

---

## Task 7: Migrate single-target skills (basic_attack, ice_beam, crescent_slash, pulse_wave, fireball, poison_dart)

For each skill: add spec keys to its `.yaml`, delete its `.js`, run the golden + behavioral tests. Migrate **one skill at a time**, committing after each, so a golden mismatch points at exactly one skill.

**Files (per skill):**
- Modify: `mods/base-rules/skills/<skill>.yaml`
- Delete: `mods/base-rules/skills/<skill>.js`
- Test: `backend/src/test/java/com/epic/engine/combat/SkillFidelityTest.java` (already covers all)

- [ ] **Step 1: ice_beam (simplest single-target damage).** Add to `ice_beam.yaml` (keep existing keys):

```yaml
effect: damage_only
damage: { base: attack, add: 8 }
targeting: { mode: pattern, field: enemy, pattern: [[0,0]] }
log:
  template: "{caster} 对 {target} 造成 {damage} 点伤害"
```

Delete `ice_beam.js`. Run: `mvn test -Dtest=SkillFidelityTest`
Expected: ice_beam golden may MISMATCH (its old log came from `combat_events.js` "对…造成" — confirm the template matches; the segments should be identical). If mismatch, diff shows the exact byte difference (e.g. animation token, effect ordering). Fix the template/spec until green. Then behavioral check (hp): goblin1 hp = 200 − 18 = 182.

> Expected nuance: ice_beam previously emitted via the `damage_dealt` handler; now via `present()`. The segments + animation must match the golden. The animation for ice_beam (beam/impact/shake/damage_number, all actor/target tokens) resolves identically through `Skill.resolveAnimation`. The `damage_number` value handling: the old handler appended `value:-damage`; `present`/`resolveAnimation` must do the same. Reconcile against the golden.

- [ ] **Step 2: Commit ice_beam.**

```bash
git rm mods/base-rules/skills/ice_beam.js
git add mods/base-rules/skills/ice_beam.yaml
git commit -m "refactor(skills): ice_beam → data-driven (damage_only)"
```

- [ ] **Step 3: crescent_slash & pulse_wave** (identical shape, different `add`).

`crescent_slash.yaml` add: `effect: damage_only` / `damage: {base: attack, add: 12}` / `targeting: {mode: pattern, field: enemy, pattern: [[0,0]]}` / same `log.template`.
`pulse_wave.yaml` add: same with `add: 6`.
Delete both `.js`. Run `mvn test -Dtest=SkillFidelityTest`; expect green (same code path as ice_beam). Behavioral: 200−22=178, 200−16=184.
Commit: `git rm …{crescent_slash,pulse_wave}.js; git add …yaml; git commit -m "refactor(skills): crescent_slash + pulse_wave → data-driven"`

- [ ] **Step 4: basic_attack** (uses `via_damage_calc`, no custom animation → default lunge/impact/shake).

`basic_attack.yaml` add:
```yaml
effect: damage_only
damage: { via_damage_calc: true }
targeting: { mode: pattern, field: enemy, pattern: [[0,0]] }
log:
  template: "{caster} 对 {target} 造成 {damage} 点伤害"
```
Delete `basic_attack.js`. Run golden. basic_attack had **no YAML `animation` block** → `resolveAnimation` returns empty, but the old path produced the default lunge/impact/shake/damage_number from `combat_events.js`. **This is the one spot where `present()` differs from the old handler.** Two options, pick per golden:
  (a) Add a default-animation fallback inside `Skill.resolveAnimation` mirroring `combat_events.js` (lunge+impact+shake, append damage_number) when `spec.animation` is absent; OR
  (b) author an explicit `animation:` block in `basic_attack.yaml` matching the old default.
Recommended: (a) — keeps parity for any future skill with no animation. Implement the fallback in `resolveAnimation`, re-run golden until green.

Commit: `git rm …basic_attack.js; git add …basic_attack.yaml …00_skill_lib.js; git commit -m "refactor(skills): basic_attack → data-driven (via_damage_calc + default anim)"`

- [ ] **Step 5: fireball** (damage_with_debuff, custom log, burning buff).

`fireball.yaml` add:
```yaml
effect: damage_with_debuff
damage: { base: attack, add: 10 }
targeting: { mode: pattern, field: enemy, pattern: [[0,0]] }
debuff:
  id: burning
  data: { damage: 3, remaining: 2, stacking: refresh, source: "@caster", color: "#ff4400", permanent: false, positive: false }
log:
  template: "{caster} 的火球术命中 {target}，造成 {damage} 点伤害"
```
Delete `fireball.js`. Run golden. Reconcile the `buff_applied` effect ordering (old fireball added it after the hp effect) — ensure the `damage_with_debuff` executor / `present` append `buff_applied` per buffed target in the same position. Behavioral: goblin1 hp = 200−20=180; `buffs` shows `burning` on goblin1.
Commit: `git rm …fireball.js; git add …fireball.yaml [+lib if effect ordering tweaked]; git commit -m "refactor(skills): fireball → data-driven (damage_with_debuff + burning)"`

- [ ] **Step 6: poison_dart** (damage_with_debuff, stacking poison, no animation block → uses default? it has none; check golden).

`poison_dart.yaml` add:
```yaml
effect: damage_with_debuff
damage: { base: attack, add: 3 }
targeting: { mode: pattern, field: enemy, pattern: [[0,0]] }
debuff:
  id: poison
  data: { damage: 3, remaining: 3, stacking: stack, maxStacks: 5, color: "#9c27b0", permanent: false, positive: false }
log:
  template: "{caster} 的毒镖命中 {target}，造成 {damage} 点伤害并中毒"
```
poison_dart.yaml HAS an animation block (projectile/impact/debuff_down/damage_number) — resolves via tokens. Old poison_dart emitted `engine.combatEvent` with empty animation though (its JS passed `engine.newList()`!). **Check the golden:** the old poison_dart animation was EMPTY. So either set `animation: []` semantics or confirm `present` produces empty to match. Reconcile against golden (the golden is truth).
Delete `poison_dart.js`. Run golden until green. Behavioral: 200−13=187; poison stack on goblin1.
Commit similarly.

- [ ] **Step 7: Full suite green.**

Run: `mvn test`
Expected: PASS (all fidelity goldens + lib tests + pre-existing tests).

---

## Task 8: Migrate self/group skills (defend, heal, war_cry, curse)

One at a time; commit each.

- [ ] **Step 1: defend** (buff_only, self). `defend.yaml` add:
```yaml
effect: buff_only
targeting: { mode: self }
buff:
  id: defending
  data: { color: "#66bb6a", permanent: false, positive: true }
log:
  template: "{caster} 进入防守姿态"
```
> Note: defend is ALSO handled by `combat_events.js` `combat.unit_action` priority-200 handler (records a defend event + log "进行防御"). After migrating, the dispatcher (priority 80) will also present it → **double event**. Resolve: remove the defend-specific block from `combat_events.js` (lines recording defend), since the dispatcher now owns it. Verify the golden captures the post-removal single event; the defend golden may need re-blessing if the chosen log text ("防守姿态" vs "进行防御") differs — **the original defend.js used "进入防守姿态", the combat_events handler used "进行防御"**; pick the defend.js wording (it ran first / was the buff path) and re-capture if needed, noting the change.

Delete `defend.js`. Run golden; reconcile. Behavioral: `defending` buff on mage.
Commit (include combat_events.js edit).

- [ ] **Step 2: heal** (heal, self). `heal.yaml` add:
```yaml
effect: heal
heal: 15
targeting: { mode: self }
log:
  template: "{caster} 恢复了 {damage} 点生命"
```
> heal's log uses heal amount where the template says `{damage}` — generalize `_renderLog` to also accept an `{amount}` token, or pass the heal value as `damages[0]`. The heal executor should pass the healed amount to `present` so `{damage}`/`{amount}` renders 15. Old heal colored the number "player" not "damage" — match via a template/segment tweak or accept per golden. Reconcile against golden.
Delete `heal.js`. Behavioral: mage hp clamped at maxHp (start mage at <max in a behavioral test to see +15).
Commit.

- [ ] **Step 3: war_cry** (buff_only, group ally). `war_cry.yaml` add:
```yaml
effect: buff_only
targeting: { mode: group, field: ally }
buff:
  id: war_cry
  data: { color: "#ffab40", stacking: refresh, permanent: false, positive: true }
log:
  template: "{caster} 发出了战吼！全体攻击力提升"
```
> war_cry's per-ally `buff_up` animation (color #ffab40) and per-ally `buff_applied` effects must be reproduced. The animation block in war_cry.yaml is `buff_up target: field`. `resolveAnimation` must expand `target: field` into one `buff_up` per ally (multi-target expansion — same machinery needed in Task 9). If Task 9's multi-target branch isn't in yet, implement the `field`→per-ally expansion here. Reconcile against golden.
Delete `war_cry.js`. Commit.

- [ ] **Step 4: curse** (debuff_only, group enemy). `curse.yaml` add:
```yaml
effect: debuff_only
targeting: { mode: group, field: enemy }
debuff:
  id: cursed
  data: { color: "#e57373", stacking: refresh, remaining: 3, permanent: false, positive: false }
log:
  template: "{caster} 施放了诅咒！全体敌人被削弱"
```
> curse builds per-enemy `debuff_down` animation + `buff_applied` effects. Same multi-target expansion. Reconcile against golden.
Delete `curse.js`. Commit.

- [ ] **Step 5: Full suite green.** Run `mvn test`. Expected PASS.

---

## Task 9: Multi-target AOE animation + migrate cleave, piercing_ray, cross_blast, light_field

These need `resolveAnimation`'s multi-target branch: per-target step expansion + cell-area tokens. Build the branch, then migrate one AOE at a time against goldens. **Escape hatch:** if any skill's animation can't be reproduced, keep its `.js` (declare no `effect:`) but rewrite its body to call `Skill.resolveTargets`/`computeDamage`/`dealDamage` (DRY on logic).

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js` (`resolveAnimation` multi-target branch)
- Modify: 4 AOE `.yaml`; Delete 4 AOE `.js`

- [ ] **Step 1: Extend `resolveAnimation`** to support multi-target. Capture the 4 AOE goldens (already captured in Task 1) and shape the branch to match. Key behaviors observed in the old JS:
  - `cleave`: `lunge(actor)` + `slash(area = the 3 column cells `cell_r_<col>` for r=0..2)` + per-target `shake(heavy)` + per-target `damage_number(value)`.
  - `piercing_ray`: `beam(from cell_<slot>_0 to cell_<slot>_2)` + per-target `impact` + `shake(normal)` + `damage_number`.
  - `cross_blast`: `pulse(actor)` + per-target `impact` + `shake(heavy)` + `damage_number`.
  - `light_field`: per-target `impact` + `shake(normal)` + `damage_number` (no delivery).

  Add tokens to `resolveAnimation`: a step with `target: "each"` (or types `shake`/`impact`/`damage_number` without an explicit `target`) expands once per `results[i]`, filling `target`=entity id and `value`=−damage for `damage_number`. A step with `area: "cells"` resolves to the cell ids of `results`; `from: "cells_start"`/`to: "cells_end"` resolve to the line endpoints. Author the YAML `animation` blocks to use these tokens (the old YAML blocks were stale — replace them).

  > The exact cell-id format is `"cell_" + rowIdx + "_" + colFromSlot` for cleave (column over rows) and `"cell_" + slot + "_" + rowIdx` for piercing_ray (matching the old JS string-building precisely). Cross-check each against the old JS and the golden — this is the fiddly part; iterate until byte-identical.

- [ ] **Step 2: cleave.** Author `cleave.yaml` animation with the new tokens; add:
```yaml
effect: damage_only
damage: { base: attack, add: 5 }
targeting: { mode: pattern, field: enemy, pattern: [[-1,0],[0,0],[1,0]] }
log:
  per_target: "{caster} 对 {target} 造成 {damage} 点伤害"
```
> The old cleave log was a single summary line ("命中 N 个目标，各造成 M"). Per the spec decision, multi-target logs normalize to light_field's per-target style. **This changes cleave's log → its golden must be re-blessed.** Re-capture cleave's golden after confirming the new per-target log + identical animation: delete `golden/cleave.json`, run `SkillFidelityTest` to recapture, eyeball the diff (animation identical, log now per-target), commit the new golden with a message noting the intentional log normalization.

Delete `cleave.js`. Behavioral: goblin1+goblin2+goblin3 each −10 (all in column slot 0) → wait, cleave hits one ROW/column; with the scene's 3 enemies in a column at slot 0, pattern `[[-1,0],[0,0],[1,0]]` around goblin1(FRONT) hits FRONT±1 = MID and FRONT; confirm against intended cleave semantics and the golden. Adjust pattern/scene so the captured behavior matches the old cleave (which hit the selected row across slots — re-examine old cleave.js: it hit the same `row` across all slots). **Re-derive the correct pattern axis from old cleave.js before authoring** (cleave grouped by `row`, so its pattern is across slots `[[0,-2]..[0,2]]`, not across rows). Use the old JS as the source of truth; the golden confirms.

Commit (yaml + lib + re-blessed golden).

- [ ] **Step 3: piercing_ray, cross_blast, light_field.** Same procedure each: author spec + animation tokens from the old JS, delete `.js`, re-bless golden if log normalized, confirm animation byte-identical, commit one at a time.
  - piercing_ray pattern: across rows at fixed slot → `[[-2,0],[-1,0],[0,0],[1,0],[2,0]]` (verify vs old JS `slot !== targetSlot` grouping).
  - cross_blast pattern: `[[0,0],[-1,0],[1,0],[0,-1],[0,1]]` (verify vs old adjacency check).
  - light_field pattern: `[[0,0],[-1,0],[1,0],[0,-1],[0,1]]` (matches old offsets; `allow_empty` targeting preserved for the frontend — keep `targeting.steps`/`allow_empty` keys).

- [ ] **Step 4: Full suite green.** Run `mvn test`. Expected PASS — all 13 goldens (with documented re-blessings for the normalized multi-target logs) + lib tests + existing tests.

---

## Task 10: Validation — extensibility, reuse, and a live mage run

- [ ] **Step 1: Extensibility test** — prove a new common effect needs only a registration. Add to `SkillLibTest`:

```java
    @Test
    void newEffect_registersWithoutEngineChange() {
        // load the skill engine
        rt.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../mods/base-rules/handlers/skill/00_skill_lib.js")), "lib.js");
        js("Skill.registerEffect('smoke_test', function(ctx,spec,results){ globalThis.__ran = true; });" +
           "if (typeof Skill.effects['smoke_test'] !== 'function') throw 'not registered';");
    }
```
Run: `mvn test -Dtest=SkillLibTest#newEffect_registersWithoutEngineChange` — Expected PASS.

- [ ] **Step 2: Reuse / no-side-effect verification (the "tooltip" use case).** Prove `computeDamage` returns a correct preview from OUTSIDE combat without mutating state — exactly what a skill tooltip / AI would call. Add:

```java
    @Test
    void computeDamage_reusableForTooltip_noMutation() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        js("var spec = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));" +
           "var preview = Skill.computeDamage(store.get('mage'), store.get('goblin'), spec.damage);" +
           "if (preview !== 20) throw 'preview='+preview;");        // attack 10 + 10
        assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(100); // unchanged
    }
```
Run: `mvn test -Dtest=SkillLibTest#computeDamage_reusableForTooltip_noMutation` — Expected PASS. This is the formal proof of the single-source-of-truth + purity claim.

- [ ] **Step 3: Behavioral integration test** — give the mage all skills and run real combat rounds through `combat.resolve_round` (not isolated firing). Add to `SkillFidelityTest` or a new `SkillCombatIntegrationTest`: set up mage + enemies, attach a `Skills` component listing several migrated skills, resolve a round with a fireball command, assert enemy hp dropped and `burning` applied and combat advanced (`phase` COMMAND or VICTORY). This validates the dispatcher inside the real round flow + buff ticks.

```java
    @Test
    void liveRound_mageFireball_dealsDamageAndBurns() throws Exception {
        // build scene with combat handlers loaded (reuse harness-style setup),
        // give mage a Skills component, fire combat.resolve_round with {type:fireball,targetId:goblin1}
        // assert goblin1 hp reduced by 20 then burning tick on round_end, phase advanced.
    }
```
Implement concretely following `NewCombatIntegrationTest`'s `resolve_round` pattern (load `combat_events.js`, `combat_log.js`, the skill engine, and buffs/burning). Run and confirm PASS.

- [ ] **Step 4: Manual frontend smoke test.** Start the app, enter combat with a mage character whose `Skills` list includes the migrated skills, and visually confirm: animations play identically, combat log reads correctly, HP bars update. (This is the human check behind the goldens.)

Run: `./start.ps1` (Windows) — open the client, trigger an encounter, cast fireball/cleave/heal/war_cry; observe.

- [ ] **Step 5: Final full suite + commit.**

```bash
cd backend && mvn test
```
Expected: ALL green.

```bash
git add backend/src/test/java/com/epic/engine/combat/SkillLibTest.java \
        backend/src/test/java/com/epic/engine/combat/SkillCombatIntegrationTest.java
git commit -m "test(skills): extensibility, tooltip-reuse purity, and live-round integration"
```

---

## Self-Review notes (carry into execution)

- **Goldens are the contract.** Capture in Task 1 before any skill code changes. When a multi-target log is intentionally normalized (cleave/piercing_ray/cross_blast — Task 9), re-bless its golden deliberately and note it in the commit; never re-bless to silence an *unexpected* diff.
- **Effect ordering in events** (hp_change vs buff_applied) must match goldens — reconcile `present`'s effect assembly against fireball/poison_dart/defend goldens in Tasks 7–8; adjust `present`'s signature/opts if needed (keep the change in one place).
- **Pattern axis convention** (`[rowIdxΔ, slotΔ]`) is the top risk. Derive each skill's pattern from the OLD JS, not from intuition; the goldens + behavioral hp assertions catch axis errors.
- **`combat_events.js` defend block** must be removed when defend migrates (Task 8) to avoid double events.
- **Frontend regression** (log/effects shape) is covered by the manual smoke test (Task 10 Step 4); if a renderer breaks on the unified flat `hp_change`, fix the renderer (it's the majority shape already).
- **Out of scope** (do not implement): attribute scaling, upgrade nodes, Skillbook UI, passives, specializations, flee refactor.
```

