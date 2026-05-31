# Feature #1 — Skill Architecture Refactor · Technical Design

**Date:** 2026-05-30
**Type:** Design + implementation doc (HLD + LLD) for Chapter 1 / Feature #1.
**Parent doc:** `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md` (the §1 architecture decisions are binding).
**Hard scope line:** **Pure refactor, behavior unchanged.** No attribute scaling, no upgrade/instance model, no new gameplay — those belong to Features #2–#7. This feature only converts "hardcoded skills" into "skeleton + shared helpers + spec data", and proves observable output is unchanged with regression tests.

---

## 1. Background & Problem

Today `mods/base-rules/skills/` holds 13 skills, each a standalone hardcoded JS handler. Every skill does the same 6 things end to end (read data → pick targets → compute damage → mutate hp / apply buff → assemble log/effects/animation → emit), **copy-pasted 13 times**, and the copies have drifted into inconsistency:

- **Two emit paths.** `fireball` / `poison_dart` / `light_field` call `engine.combatEvent()` (top-level key `log`). `heal` / `war_cry` / `cleave` / `curse` / `cross_blast` / `piercing_ray` bypass it and manually push to `CombatEvents.queue` (top-level key `segments`), and never write `CombatLog`.
- **Two effect shapes.** `hp_change` is flat (`amount`/`hp`/`maxHp`) in fireball, but nested under `data` in cleave/cross_blast/piercing_ray.
- **Animation: a shared resolver plus JS overrides.** Every skill's YAML already has a flat `animation` block. Single-target skills (fireball, ice_beam, crescent_slash, pulse_wave, poison_dart) have it resolved by the shared `combat.damage_dealt` handler in `combat_events.js` via `resolveSkillAnimation` (replaces the `actor`/`target`/`actor_side` tokens). The multi-target AOE skills (cleave/cross_blast/piercing_ray/light_field) **ignore their YAML block and hand-build animation in JS** with per-target loops and computed cell coordinates — because `resolveSkillAnimation` cannot do per-target expansion or cell math. curse/war_cry build buff animations in JS.
- **Damage formula.** All `attack + N` (basic_attack is the exception — it goes through the `combat.damage_calc` event for defense subtraction).

**These 13 skills are not finalized content.** They are a test corpus used to validate the frontend animations; the animations are already tuned and visually signed off. So this feature's job is to **swap the architecture while keeping observable output identical**, not to "correct" the skills' design.

---

## 2. Goals

1. Extract the shared flow into **one set of shared code**, built once; a skill collapses to a declarative spec (YAML).
2. Common skills carry **zero JS**; genuinely structural skills keep a bespoke-JS escape hatch (parent doc §1.1).
3. **Damage calculation becomes a standalone pure function** taking caster + target, reusable by combat / skill panel / AI (single source of truth).
4. Flatten the three inconsistencies from §1 into one output shape.
5. Use a two-tier test strategy (behavioral assertions + animation golden) to guarantee observable output is unchanged.
6. **Pre-cut the seams** for later features: declarative damage-formula shell (scaling left empty), spec flows through a ModifierChain merge point (single source for now).

---

## 3. High-Level Design (HLD)

### 3.1 Four-layer responsibilities

```
        spec (YAML data, pure recipe)
                │  engine reads spec
                ▼
   ┌──────────────────────────────────────────────────┐
   │  pick targets  resolveTargets(ctx, spec)           │  offset pattern → live units
   ├──────────────────────────────────────────────────┤
   │  compute       computeDamage(caster, target, dmg)  │  pure function, no side effects, reusable
   ├──────────────────────────────────────────────────┤
   │  apply         dealDamage / applyBuffFromSpec       │  mutates entity state (side effects)
   ├──────────────────────────────────────────────────┤
   │  present       present(ctx, spec, results)          │  log/effects/animation, derived from data
   └──────────────────────────────────────────────────┘
```

Borrowed from Unreal GAS: separation of compute (Execution Calculation) / apply (Modifier application) / present (Cue — cosmetic only, never feeds back into game state).

### 3.2 Two tiers of skill + a registry

- **Common skill:** YAML declares `effect: <name>` + params. A **generic dispatcher** reads the spec, looks up the effect registry, runs the standard flow. Zero JS.
- **Structural skill / escape hatch:** spec declares no `effect` (or no spec exists); the skill registers its own `combat.unit_action` handler, but still calls the shared layers internally (computeDamage / dealDamage / present).
- **`effect` is an open registry**, not a hardcoded engine enum. base-rules ships 5; adding a new common effect = register one more function, engine untouched.

### 3.3 Module / file layout

```
mods/base-rules/
  handlers/
    skill/                         # ★ new; under handlers/ → loads before skills/
      00_skill_lib.js              # global namespace  Skill = { ... }  shared library
      01_effects.js                # registers the 5 effect executors
      02_dispatch.js               # generic dispatcher; listens on combat.unit_action
  skills/
    fireball.yaml                  # collapses to pure spec (no .js)
    fireball.js                    # ← deleted
    ...                            # 12 common skills: delete .js, write/extend .yaml
    flee.js / flee.yaml            # untouched (placeholder, handled in start_combat.js)
```

> The `00/01/02` filename prefixes exploit ModuleLoader's alphabetical load order, so lib loads before registration loads before dispatcher. All live under `handlers/`, which loads in full before `skills/`.

### 3.4 Data flow (fireball example)

```
frontend POST /api/action {type: fireball, targetId: goblin1}
   → combat.resolve_round → per unit fire combat.unit_action
   → 02_dispatch.js matches:
        spec  = Skill.loadSpec("fireball")        # loadYaml + cache
        ctx   = Skill.context(event)              # resolve caster/combat/names/sides
        tgts  = Skill.resolveTargets(ctx, spec)   # [{entity:goblin1, cell:...}]
        Skill.effects["damage_with_debuff"](ctx, spec, tgts)
              → per target: dmg = computeDamage(caster, target, spec.damage)
                            dealDamage(ctx, target, dmg, "fireball")
              → applyBuffFromSpec(ctx, target, spec.debuff)
              → present(ctx, spec, tgts)          # routes through engine.combatEvent
```

---

## 4. Low-Level Design (LLD)

### 4.1 Shared library `Skill` (global namespace)

GraalJS runs a single context, so a global object is the shared surface. `00_skill_lib.js`:

```js
var Skill = {
  // ---- spec cache ----
  _specs: {},
  loadSpec: function(type) {            // type == filename == skill id
    if (!this._specs[type]) {
      this._specs[type] = engine.loadYaml("skills/" + type + ".yaml");
    }
    return this._specs[type];
  },

  // ---- effect registry ----
  effects: {},
  registerEffect: function(name, fn) { this.effects[name] = fn; },

  // ---- boilerplate: resolve caster / combat / names / side ----
  // ctx = a "pre-resolved info bag" for one skill activation, computed once and
  // passed to every helper so they don't each re-derive the same lookups
  // (store.get(actorId), Name component, player/enemy tag, ...).
  context: function(event) {
    // → { actorId, combatId, caster, casterName, casterSide, cmd }
  },

  // ---- pick targets ----
  resolveTargets: function(ctx, spec) {
    // see 4.3; returns [{ entity, cell:{rowIdx, slot} }, ...]
  },

  // ---- compute layer (pure, no side effects) ----
  computeDamage: function(caster, target, dmgSpec) {
    // see 4.4; returns an integer
  },

  // ---- apply layer (side effects) ----
  dealDamage: function(ctx, target, amount, skillId, opts) {
    // target.Health.hp -= amount; fire combat.damage_dealt
  },
  applyBuffFromSpec: function(ctx, target, buffSpec) {
    // assemble buff data → buffs.applyBuff
  },

  // ---- present layer ----
  present: function(ctx, spec, results) {
    // assemble log + effects + animation → engine.combatEvent(ctx.combatId, data)
  },
  resolveAnimation: function(spec, ctx, results) {
    // see 4.5; flat-format token replacement + multi-target expansion → animation array
    // (extracted from combat_events.js resolveSkillAnimation, generalized for multi-target)
  }
};
```

### 4.2 Generic dispatcher `02_dispatch.js`

```js
engine.on("combat.unit_action", 80, function(event) {
  var type = event.get("command").get("type");
  var spec = Skill.loadSpec(type);
  if (spec == null || spec.get("effect") == null) return;  // bespoke / no spec → skip
  var ctx = Skill.context(event);
  var results = Skill.resolveTargets(ctx, spec);
  var fn = Skill.effects[spec.get("effect")];
  if (fn == null) { /* log an engine warning */ return; }
  fn(ctx, spec, results);
});
```

**Boundary with bespoke skills:** spec has `effect:` → dispatcher handles it; no `effect:` (or no spec) → the skill's own `engine.on` handles it. No double-fire on the same cmd type, because bespoke skills don't declare `effect`. Priority stays at the current `80`.

### 4.3 Target resolution `resolveTargets`

Targeting is data, not named modes. Two modes:

```yaml
# (a) pick one cell, spread by an offset pattern
targeting:
  mode: pattern            # default
  field: enemy             # enemy | ally
  pattern: [[0,0]]         # [[rowIdxΔ, slotΔ], ...] relative to the picked cell
# column = [[-1,0],[0,0],[1,0]]; cross = [[0,0],[-1,0],[1,0],[0,-1],[0,1]];
# "cross without center" = drop [0,0]

# (b) no pick, hit a whole group
targeting:
  mode: group              # group | self
  field: ally              # required for group; ignored for self
```

Algorithm (absorbs light_field's existing offset logic):
1. `pattern` mode: derive the center `{rowIdx, slot}` from `cmd.targetId` (or `targetRow/targetCol`); for each offset compute a target cell; scan units tagged `combat:<id>`, of `field` side, with `hp>0`, and collect those landing on a target cell.
2. `group` mode: all living units of `field` side. `self`: just `ctx.caster`.
3. Returns `[{ entity, cell:{rowIdx, slot} }]`. **A single resolver replaces the per-skill target-collection loops in cleave/cross_blast/piercing_ray/light_field.**

### 4.4 Compute layer `computeDamage` (pure function)

Declarative shell (the parent doc §1.1 shape); Feature #1 fills only existing fields:

```yaml
damage:
  base: attack            # which caster stat (only attack today)
  add: 10                 # flat add
  # scaling: { 智力: 1.2 }   # ← filled in Feature #2; left empty here
  via_damage_calc: false  # basic_attack=true: fire combat.damage_calc for the defense-subtraction + defend-buff path
```

```js
computeDamage: function(caster, target, dmgSpec) {
  if (dmgSpec.via_damage_calc) {
    // fidelity for basic_attack: fire combat.damage_calc, read damage back
  }
  var base = caster.CombatStats[dmgSpec.base || "attack"]  // default 5 (keep existing fallback)
  return base + (dmgSpec.add || 0);
  // Feature #2 appends scaling here; panel/AI call the same function → single source of truth
}
```

**Pure:** does not mutate hp or fire events (except the `via_damage_calc` branch, which must fire to obtain the post-defense value — a deliberate fidelity compromise; once Feature #2/#3 fold defense subtraction into this function it can be removed).

### 4.5 Present layer & animation `present` / `buildAnimation`

**Flatten the dual paths:** every skill goes through `engine.combatEvent(combatId, {log, effects, animation})` (it already derives `segments` + `logCount` and writes `CombatLog`). The manual `queue.push` path is retired. `hp_change` uses the flat shape (`amount`/`hp`/`maxHp`); the nested `data` shape is dropped.

**log:** generated from the spec's `log` template. **Only two skills have tested/tuned logs — `fireball` (single line) and `light_field` (one line per target) — so they are the canonical reference shapes.** The other skills' logs were never adjusted; they are **normalized to these two patterns**, not byte-preserved. In particular the "命中 N 个目标，各造成 M 点伤害" summary style (cleave/cross_blast/piercing_ray) is dropped in favor of light_field's per-target lines.

```yaml
log:
  template:   "{caster} 的火球术命中 {target}，造成 {damage} 点伤害"   # single target — fireball style
  per_target: "{caster} 对 {target} 造成 {damage} 点伤害"             # multi target — light_field style: one entry per target
```

**animation — reuse the existing flat format, do NOT invent a new one.** The engine already resolves the flat YAML `animation` block via `resolveSkillAnimation` (token replacement: `actor`→actorId, `target`→targetId, `actor_side`→side). Single-target skills already work this way and their output is golden-tested; **inventing a `delivery`/`per_target` template would force re-authoring every animation and put the goldens at risk.** So:

- **Keep the flat YAML `animation` format unchanged.**
- **Move `resolveSkillAnimation` into the shared lib as `Skill.resolveAnimation`** (single implementation; `combat_events.js` calls it too). Single-target skills resolve byte-identically as today.
- **Add multi-target capability** for the 4 AOE skills, which is the only genuinely new animation code. `Skill.resolveAnimation` gains two extra tokens, driven by the targeting result:
  - per-target expansion: a step whose `target: each` (or `shake`/`damage_number` steps) is repeated once per resolved target;
  - cell areas: `area: cells` / `from: cells_start` / `to: cells_end` resolve to the cells computed by `resolveTargets` (the slash column, the beam endpoints).

  The exact token set is fixed empirically: capture each AOE skill's current JS-built animation as a golden, then shape `resolveAnimation`'s multi-target branch until it reproduces them byte-for-byte.
- **Escape hatch:** if a given AOE animation can't be expressed this way, that skill stays bespoke JS but still calls `Skill.resolveTargets` / `computeDamage` / `dealDamage` (DRY on logic, custom on animation only). This keeps the risk capped.

**Fidelity blind spot — double logging:** `basic_attack` / `ice_beam` / `crescent_slash` / `pulse_wave` currently **emit no combat event**; their log comes from the shared `combat.damage_dealt` handler as a fallback (they fire `damage_dealt` **without** `skipLog`). After the refactor, `present()` emits the log proactively, so if `dealDamage` still triggers the fallback log it will **duplicate**. Rule: `dealDamage` always sets `skipLog: true`; logs come solely from `present()`. Consequence: for these 4 skills the log source moves from the `damage_dealt` handler to `present`, and their log shape is unified along with everyone else's (semantics asserted, old bytes not locked). Confirm the `damage_dealt` handler's default-log branch isn't relied on elsewhere (e.g. enemy AI, buff-tick damage); if it is, keep that branch and only set `skipLog` on the skill side.

### 4.6 Effect executors `01_effects.js` (5)

```js
Skill.registerEffect("damage_only", function(ctx, spec, results) {
  results.forEach(function(r) {
    var dmg = Skill.computeDamage(ctx.caster, r.entity, spec.damage);
    Skill.dealDamage(ctx, r.entity, dmg, spec.id);
  });
  Skill.present(ctx, spec, results);
});

Skill.registerEffect("damage_with_debuff", function(ctx, spec, results) {
  results.forEach(function(r) {
    var dmg = Skill.computeDamage(ctx.caster, r.entity, spec.damage);
    Skill.dealDamage(ctx, r.entity, dmg, spec.id);
    Skill.applyBuffFromSpec(ctx, r.entity, spec.debuff);
  });
  Skill.present(ctx, spec, results);
});

Skill.registerEffect("debuff_only", function(ctx, spec, results) { /* applyBuffFromSpec + present only */ });
Skill.registerEffect("buff_only",   function(ctx, spec, results) { /* applyBuffFromSpec over results (self/all_allies) */ });
Skill.registerEffect("heal",        function(ctx, spec, results) { /* restore hp + present */ });
```

---

## 5. Skill Classification (migration list)

| Skill | effect | targeting | Notes |
|------|--------|-----------|------|
| basic_attack | damage_only | pattern, enemy, `[[0,0]]` | `damage.via_damage_calc: true` |
| crescent_slash | damage_only | same | add 12 |
| ice_beam | damage_only | same | add 8; no custom animation today |
| pulse_wave | damage_only | same | add 6; no custom animation today |
| cleave | damage_only | pattern, enemy, column | delivery=slash@cells |
| piercing_ray | damage_only | pattern, enemy, same-slot line | delivery=beam@from→to |
| cross_blast | damage_only | pattern, enemy, cross | delivery=pulse@actor |
| light_field | damage_only | pattern, enemy, cross | no delivery |
| fireball | damage_with_debuff | pattern, enemy, `[[0,0]]` | debuff=burning |
| poison_dart | damage_with_debuff | pattern, enemy, `[[0,0]]` | debuff=poison (stack) |
| heal | heal | self | heal 15 |
| defend | buff_only | self | buff=defending |
| war_cry | buff_only | group, ally | buff=war_cry |
| curse | debuff_only | group, enemy | buff=cursed |
| **flee** | — | — | **untouched**, bespoke |

12 migrate to pure-data archetypes; flee stays as-is.

---

## 6. Test Strategy (two-tier)

Test harness reuses `NewCombatIntegrationTest` (driven by `bus.fire("combat.resolve_round")`, JUnit).

| What | Method | Notes |
|------|--------|------|
| Game state: hp loss, buff applied / stacks, death, victory | **behavioral assertions** | ≥1 case per skill; assert `hp`/`phase`/buff components. Expected values are spelled out by hand. |
| **Animation arrays** | **golden snapshot** | Establish the baseline **before** touching code: run each skill once, serialize the `animation` (plus `segments`/`effects` where needed) from `CombatEvents.queue` into `*.golden.json`; after the refactor, assert byte-identical. Rules can't be spelled out but the current output is correct — lock "same as before". |
| log/effects semantics | assert the new unified shape | Assert "who dealt how much to whom / which buff applied"; do **not** lock the old segments/log bytes (that's the drift being flattened). |

**Critical ordering:** the golden baseline must be captured **before** any skill code changes (current implementation produces the answer key). Recommended first step: add a "capture golden" test/script, run against the current state, write to disk, then start the refactor.

**Frontend impact:** after log/effects shapes are unified, the spots in the frontend that read `segments`/`hp_change` need one regression pass (`combatEvent` already produces `segments`; flat `hp_change` is the majority case today, so the blast radius is small). Put it on the acceptance checklist.

---

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Animation port breaks visible effects | golden locks byte-for-byte; baseline captured first |
| AOE delivery animations (slash@cells / beam@from→to) not fully expressible as templates | if a given skill's template can't hold, **let it fall back to bespoke JS** (escape hatch); don't force the data model |
| Unifying log/effects shapes breaks frontend rendering | frontend regression checklist; `combatEvent`'s existing normalization lowers risk |
| `via_damage_calc` makes computeDamage impure | local compromise, annotated; removed once Feature #2/#3 fold defense subtraction into the function |
| Dispatcher and bespoke double-fire | rule: dispatcher only handles when `effect:` is present; bespoke declares no effect |

---

## 8. Out of Scope

- Attribute scaling, secondary stats, damage types/resistances (Features #2/#3).
- Skill instance/ownership/upgrade node, Skillbook, multi-source ModifierChain merge (Features #4/#7) — the spec is single-source here, but **leaves the merge seam in place**.
- Passives, specializations (Features #5/#6).
- Redesigning these 13 skills' identity/numbers/class assignment (they are a test corpus).
- Refactoring flee.

---

## 9. Acceptance Criteria

1. 12 skills drop their bespoke `.js`, driven instead by spec + shared library (flee excepted).
2. All behavioral assertions pass: hp/buff/death/victory match pre-refactor.
3. All animation goldens pass: byte-identical.
4. Manual frontend regression of one combat round: animations, log, and HP bars render correctly.
5. `mvn test` all green.
6. Adding a new common effect requires only registering one function in `01_effects.js`, with no engine or dispatcher change (validate extensibility with one hypothetical effect).
```

