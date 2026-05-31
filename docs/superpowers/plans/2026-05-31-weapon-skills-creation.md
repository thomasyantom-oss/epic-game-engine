# 批次 B 实现计划 — 武器系统 / 职业技能 / 创建页

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把武器最终伤害(第二层)、五职业差异化技能、创建/选择页 UI 落地到 `feature/ch1-f2-attribute-table` 分支。

**Architecture:** `CombatStats.attack` 改由 `derived_stats.js`(priority 300)读「装备武器绑定属性」算最终伤害,无武器走占位。装备 modifier 与技能 scaling 都泛化成「`组件.字段` / 两级属性查找」。新增/重做技能走既有数据驱动 effect 框架;war_cry/curse 用 buff→modifier 桥(buff typeId priority 10,在 derived 之前跑,削/加基础属性后 derived 重算 maxHp/强度)。创建页前端用 frontend-design,后端补下发职业预览。

**Tech Stack:** Java 21 + Spring Boot 3(JUnit5 + AssertJ),GraalJS 事件 handler,SnakeYAML 数据,Vue 3。

**基线:** 后端 108/108 绿。每个 Task 后保持全绿(含新增测试)。

**设计依据:** `docs/superpowers/specs/2026-05-31-weapon-skills-creation.md`(公式已锁:武器伤害 `⌈B×(1+属性×倍率/100)⌉`,B=5,力量×2 其余×1;技能 scaling 默认读裸 PrimaryStats,computeDamage 两级查找 DerivedStats→PrimaryStats)。

---

## 文件清单

**Phase A — 武器系统(#3)**
- Modify: `mods/base-rules/handlers/equipment/equip.js`(`registerEquipmentModifier` 泛化 `组件.字段`)
- Modify: `mods/base-rules/handlers/character/derived_stats.js`(attack=武器最终伤害 + hp clamp)
- Modify: `mods/base-rules/entities/items.yaml`(5 武器 weaponAttr+base;护甲/饰品全限定字段)
- Modify: `mods/base-rules/handlers/character/select.js`(发 5 把初始武器)
- Test: `backend/src/test/java/com/epic/engine/combat/WeaponDamageTest.java`(新建)

**Phase B — 职业技能(#5)**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`(computeDamage 两级查找 + debuff scaling)
- Modify: 技能 yaml:`cleave/fireball/light_field/poison_dart/piercing_ray/pulse_wave/heal`
- Modify: `mods/base-rules/skills/heal.js`(体质 scaling)
- Create: `mods/base-rules/skills/backstab.yaml`(盗贼背刺)
- Create: `mods/base-rules/buffs/war_cry.js`(buff→modifier 桥)
- Create: `mods/base-rules/buffs/cursed.js`(buff→modifier 桥 + round_end tick)
- Modify: `mods/base-rules/skills/curse.yaml`(buff id 改 `cursed`)
- Modify: 5 个 `mods/base-rules/schemas/sub/class_*.schema.yaml`(加 `starting_skills`)
- Modify: `mods/base-rules/handlers/character/select.js`(读 starting_skills)
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java`(caster 补 PrimaryStats)
- Modify/regen: `backend/src/test/resources/golden/*.json`(改了数值的技能)
- Test: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`(扩),`CurseSkillTest.java`(新建)

**Phase C — 创建/选择页(#6)**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java` + `WorldSnapshot.java`(下发职业预览)
- Modify/Create: `frontend/src/components/CharacterSelect.vue` / `CharacterCreate.vue`
- Test: `backend/src/test/java/com/epic/engine/snapshot/CharacterSelectPreviewTest.java`(新建)

---

# Phase A — 武器系统(#3)

## Task A1: 装备 modifier 泛化「组件.字段」

**Files:**
- Modify: `mods/base-rules/handlers/equipment/equip.js:46-73`(`registerEquipmentModifier`)
- Test: `backend/src/test/java/com/epic/engine/combat/WeaponDamageTest.java`(新建)

- [ ] **Step 1: 写失败测试** — `WeaponDamageTest.java`(仿 `DerivedStatsTest` 的 JS 加载模式)

```java
package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class WeaponDamageTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;
    ModifierTypeRegistry typeReg; ModifierChainService chainService;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/equipment/equip.js")), "equip.js");
    }

    @AfterEach
    void tearDown() { rt.close(); }

    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    // 一件护甲用全限定字段 PrimaryStats.力量+3 与 CombatStats.defense+5
    @Test
    void equipment_applies_fully_qualified_fields() {
        Entity e = new Entity("h1");
        Component p = new Component("PrimaryStats");
        p.set("力量", 10); p.set("敏捷", 0); p.set("智力", 0);
        p.set("体质", 0); p.set("意志", 0); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        store.add(e);

        // 用 JS 建一件物品(全限定字段)放进 store
        js("var it = engine.createEntity('amulet'); it.addTag('item');" +
           " var m = engine.newComponent('ItemMeta'); m.set('name','力量护符'); m.set('type','accessory'); m.set('rarity','common'); it.addComponent(m);" +
           " var s = engine.newComponent('ItemStats'); s.set('PrimaryStats.力量', 3); s.set('CombatStats.defense', 5); it.addComponent(s);" +
           " store.add(it);");
        js("engine.setBase('h1');");
        js("registerDerivedModifier('h1');");
        js("registerEquipmentModifier('h1', 'amulet');");

        Entity r = store.get("h1");
        assertThat(r.getComponent("PrimaryStats").getInt("力量")).isEqualTo(13);   // 10 + 3
        assertThat(r.getComponent("CombatStats").getInt("defense")).isEqualTo(5);  // 0 + 5
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=WeaponDamageTest#equipment_applies_fully_qualified_fields`
Expected: FAIL — 旧 `registerEquipmentModifier` 只往 `CombatStats` 加,`PrimaryStats.力量` 不会生效(力量仍 10)。

- [ ] **Step 3: 改 `registerEquipmentModifier`** — `equip.js:46-73` 整段替换

```js
// 注册装备 Modifier 的辅助函数（供 entity.loaded 重用）
// stats 用全限定字段 "组件.字段"(如 "CombatStats.defense":+5 / "PrimaryStats.力量":+3)。
// 无点号的 key(weaponAttr / base)是武器元数据，由 derived_stats 读取，不作为 modifier。
function registerEquipmentModifier(entityId, itemId) {
    var item = store.get(itemId);
    if (item === null || !item.hasComponent("ItemStats")) return;
    var stats = item.getComponent("ItemStats");
    var statsCopy = engine.newMap();
    var statsKeys = stats.getAll().keySet().iterator();
    while (statsKeys.hasNext()) {
        var k = statsKeys.next();
        statsCopy.put(k, stats.get(k));
    }
    engine.addModifier(entityId, {
        typeId: "equipment",
        id: "equip_" + itemId,
        label: item.getComponent("ItemMeta").getString("name"),
        apply: function(entity) {
            var keys = statsCopy.keySet().iterator();
            while (keys.hasNext()) {
                var k = keys.next();
                var key = "" + k;
                var dotIdx = key.indexOf(".");
                if (dotIdx < 0) continue;   // weaponAttr / base：武器元数据，derived 读，非 modifier
                var compName = key.substring(0, dotIdx);
                var fieldName = key.substring(dotIdx + 1);
                var comp = entity.getComponent(compName);
                if (comp !== null && comp.has(fieldName)) {
                    comp.set(fieldName, comp.getInt(fieldName) + statsCopy.get(k));
                }
            }
        }
    });
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=WeaponDamageTest#equipment_applies_fully_qualified_fields`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/equipment/equip.js backend/src/test/java/com/epic/engine/combat/WeaponDamageTest.java
git commit -m "feat(equip): 装备 modifier 支持全限定字段「组件.字段」"
```

---

## Task A2: derived_stats 算武器最终伤害 + hp clamp

**Files:**
- Modify: `mods/base-rules/handlers/character/derived_stats.js:28-33`(CombatStats 段)
- Test: `WeaponDamageTest.java`(加 2 个用例)

- [ ] **Step 1: 写失败测试** — 在 `WeaponDamageTest` 加

```java
// 法师(主属性=智力,智力低=4)拿双手剑(weaponAttr=力量=20,B=5)→ attack 按力量算,与主属性/法术无关。
// 关键:选值让「占位(读主属性智力)」≠「新公式(读武器力量)」以确保实现前测试真失败。
@Test
void weapon_final_damage_uses_bound_attr_not_main() {
    Entity e = new Entity("m1");
    Component p = new Component("PrimaryStats");
    p.set("力量", 20); p.set("敏捷", 6); p.set("智力", 4);
    p.set("体质", 0); p.set("意志", 0); p.set("weaponAttr", "智力");   // 主属性=智力(决定占位物理强度)
    e.addComponent(p);
    e.addComponent(new Component("DerivedStats"));
    Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
    Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
    Component slots = new Component("EquipmentSlots");
    slots.set("weapon", "greatsword1"); slots.set("armor", null); slots.set("accessory", null);
    e.addComponent(slots);
    store.add(e);

    // 双手剑：weaponAttr=力量, base=5
    js("var w = engine.createEntity('greatsword1'); w.addTag('item');" +
       " var m = engine.newComponent('ItemMeta'); m.set('name','双手剑'); m.set('type','weapon'); m.set('rarity','common'); w.addComponent(m);" +
       " var s = engine.newComponent('ItemStats'); s.set('weaponAttr','力量'); s.set('base',5); w.addComponent(s);" +
       " store.add(w);");
    js("engine.setBase('m1');");
    js("registerDerivedModifier('m1');");

    // 新公式(读武器力量20 ×2)：attack = ⌈5 × (1 + 20×2/100)⌉ = ⌈5×1.4⌉ = 7
    // 实现前占位(读主属性智力4 ×1)：⌈5×(1+4/100)⌉ = ⌈5.2⌉ = 6 ≠ 7 → 测试真失败
    assertThat(store.get("m1").getComponent("CombatStats").getInt("attack")).isEqualTo(7);
}

// 无武器 fallback：占位 ⌈5×(1+物理强度/100)⌉
@Test
void no_weapon_falls_back_to_placeholder() {
    Entity e = new Entity("n1");
    Component p = new Component("PrimaryStats");
    p.set("力量", 10); p.set("敏捷", 0); p.set("智力", 0);
    p.set("体质", 0); p.set("意志", 0); p.set("weaponAttr", "力量");
    e.addComponent(p);
    e.addComponent(new Component("DerivedStats"));
    Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
    Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
    store.add(e);
    js("engine.setBase('n1');");
    js("registerDerivedModifier('n1');");
    // 物理强度 = 力量10×2 = 20 → attack = ⌈5×1.2⌉ = 6
    assertThat(store.get("n1").getComponent("CombatStats").getInt("attack")).isEqualTo(6);
}
```

- [ ] **Step 2: 跑确认失败**

Run: `cd backend && mvn test -Dtest=WeaponDamageTest#weapon_final_damage_uses_bound_attr_not_main`
Expected: FAIL — 实现前 derived 不读装备槽,attack 走占位「主属性=智力4 ×1」= ⌈5×1.04⌉ = 6 ≠ 期望 7(新公式读「武器力量20 ×2」)。断言 7,实际 6 → 真失败。

- [ ] **Step 3: 改 derived_stats.js** — `:28-33` 的 CombatStats 段替换为

```js
            var h = ent.getComponent("Health");
            if (h !== null) {
                h.set("maxHp", MAXHP_FLOOR + p.getInt("体质") * 10);   // SET,带底盘
                // 体质被削(诅咒)→ maxHp 下降时，当前 hp 不得超过新上限；maxHp 回升不补 hp。
                if (h.getInt("hp") > h.getInt("maxHp")) h.set("hp", h.getInt("maxHp"));
            }
            var c = ent.getComponent("CombatStats");
            if (c !== null) {
                c.set("speed", p.getInt("敏捷"));
                // 武器最终伤害(第二层)：读装备武器绑定属性；无武器走占位物理强度。
                var atk = Math.ceil(PLACEHOLDER_WEAPON_BASE * (1 + phys / 100));
                var slots = ent.getComponent("EquipmentSlots");
                if (slots !== null && slots.get("weapon") !== null) {
                    var weapon = store.get(slots.get("weapon"));
                    if (weapon !== null && weapon.hasComponent("ItemStats")) {
                        var ws = weapon.getComponent("ItemStats");
                        var wAttrName = ws.has("weaponAttr") ? ws.getString("weaponAttr") : "力量";
                        var B = ws.has("base") ? ws.getInt("base") : PLACEHOLDER_WEAPON_BASE;
                        atk = Math.ceil(B * (1 + p.getInt(wAttrName) * weaponMult(wAttrName) / 100));
                    }
                }
                c.set("attack", atk);
            }
```

(删除原 `var h = ...` 与 `var c = ...` 两段,用上面整体替换。)

- [ ] **Step 4: 跑确认通过 + 旧 DerivedStatsTest 仍绿**

Run: `cd backend && mvn test -Dtest=WeaponDamageTest,DerivedStatsTest`
Expected: PASS(`DerivedStatsTest.derives_warrior_stats` 仍 attack=7,无 EquipmentSlots 走占位不变)

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/handlers/character/derived_stats.js backend/src/test/java/com/epic/engine/combat/WeaponDamageTest.java
git commit -m "feat(weapon): attack=装备武器最终伤害(derived 第二层)+ hp clamp 上限"
```

---

## Task A3: items.yaml 5 武器 + 全限定护甲;select 发武器

**Files:**
- Modify: `mods/base-rules/entities/items.yaml`(整文件)
- Modify: `mods/base-rules/handlers/character/select.js:136-147`(startingItems)
- Test: `WeaponDamageTest.java`(加 1 个加载断言)+ 既有 `WorldBootstrapTest`/手测

- [ ] **Step 1: 改 items.yaml** — 整文件替换

```yaml
items:
  # ── 5 把初始武器(每职业一把,B 统一 5)──
  - id: greatsword
    meta: { name: 双手剑, type: weapon, rarity: common }
    stats: { weaponAttr: "力量", base: 5 }
  - id: dagger
    meta: { name: 匕首, type: weapon, rarity: common }
    stats: { weaponAttr: "敏捷", base: 5 }
  - id: staff
    meta: { name: 法杖, type: weapon, rarity: common }
    stats: { weaponAttr: "智力", base: 5 }
  - id: gauntlet
    meta: { name: 拳套, type: weapon, rarity: common }
    stats: { weaponAttr: "体质", base: 5 }
  - id: totem
    meta: { name: 图腾, type: weapon, rarity: common }
    stats: { weaponAttr: "意志", base: 5 }

  # ── 护甲 / 饰品:全限定字段 ──
  - id: leather_armor
    meta: { name: 皮甲, type: armor, rarity: common }
    stats: { "CombatStats.defense": 5 }
  - id: chain_mail
    meta: { name: 锁子甲, type: armor, rarity: uncommon }
    stats: { "CombatStats.defense": 12 }
  - id: speed_ring
    meta: { name: 疾速戒指, type: accessory, rarity: rare }
    stats: { "CombatStats.speed": 5 }
```

> 注:旧 `iron_sword/steel_sword/fire_staff` 删除(被 5 把新武器取代)。

- [ ] **Step 2: 改 select.js 发武器** — `select.js:136-147` 的 Inventory 段替换

```js
    var invComp = engine.newComponent("Inventory");
    var startingItems = engine.newList();
    // 给每个职业发全部 5 把武器，用户逐一测试不同武器在不同职业手上的表现
    var weapons = ["greatsword", "dagger", "staff", "gauntlet", "totem"];
    for (var wi = 0; wi < weapons.length; wi++) startingItems.add(weapons[wi]);
    startingItems.add("leather_armor");
    startingItems.add("speed_ring");
    invComp.set("items", startingItems);
    entity.addComponent(invComp);
```

- [ ] **Step 3: 写加载断言** — `WeaponDamageTest` 加

```java
@Test
void items_yaml_loads_five_weapons() throws Exception {
    rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/world/bootstrap.js")), "bootstrap.js");
    // 触发 world.init(equip.js 监听 prio 80 加载 items.yaml)
    js("var ev = engine.newEvent('world.init'); engine.fire('world.init', ev);");
    String[] ids = {"greatsword","dagger","staff","gauntlet","totem"};
    for (String id : ids) {
        assertThat(store.get(id)).as(id).isNotNull();
        assertThat(store.get(id).getComponent("ItemStats").getString("weaponAttr")).isNotNull();
    }
}
```

> 若 `world.init` 还需其它 handler 先注册,执行子代理应读 `bootstrap.js` / `world.init` 监听链,必要时改为直接调用 `equip.js` 里的 `world.init` 注册逻辑(只加载 items)。最小验证目标:5 武器实体带 `weaponAttr`。

- [ ] **Step 4: 跑确认通过**

Run: `cd backend && mvn test -Dtest=WeaponDamageTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/entities/items.yaml mods/base-rules/handlers/character/select.js backend/src/test/java/com/epic/engine/combat/WeaponDamageTest.java
git commit -m "feat(items): 5 把初始武器(weaponAttr+B5)+ 护甲全限定字段;每职业发全武器"
```

---

# Phase B — 职业技能(#5)

## Task B1: computeDamage 两级查找 + debuff scaling

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`(`computeDamage:40-65` + `applyBuffFromSpec:157-167`)
- Test: `backend/src/test/java/com/epic/engine/combat/SkillLibTest.java`(扩)

- [ ] **Step 1: 写失败测试** — 在 `SkillLibTest` 加。先读该文件头部确认它如何构造 caster/调用 `Skill.computeDamage`(仿现有用例;caster 需同时含 DerivedStats 与 PrimaryStats)。

```java
// scaling {力量:0.1} 读裸 PrimaryStats；{物理强度:x} 读 DerivedStats（两级查找）
@Test
void compute_damage_two_level_lookup() {
    // 构造 caster：DerivedStats.物理强度=20, PrimaryStats.力量=15, CombatStats.attack=6
    // (沿用本测试类既有的 caster 构造 helper；若无则仿 DerivedStatsTest 建实体)
    // dmgSpec1: { base:"attack", scaling:{力量:0.1} } → 6 + ⌈15×0.1⌉ = 6 + 2 = 8
    // dmgSpec2: { add:10, scaling:{物理强度:0.5} }   → 10 + ⌈20×0.5⌉ = 10 + 10 = 20
    // 用 rt 执行 JS 读取结果，断言 8 与 20（具体写法对齐 SkillLibTest 既有风格）
}
```

> 执行子代理:先 Read `SkillLibTest.java` 全文,套用其既有 caster/断言风格补全此用例(上面注释给出期望值)。

- [ ] **Step 2: 跑确认失败**

Run: `cd backend && mvn test -Dtest=SkillLibTest#compute_damage_two_level_lookup`
Expected: FAIL — 现 `computeDamage` 只查 DerivedStats,`{力量:..}` 取不到(力量∈PrimaryStats)→ 加值 0。

- [ ] **Step 3: 改 computeDamage** — `00_skill_lib.js` `if (dmgSpec.scaling) {...}` 块(`:55-63`)替换

```js
    if (dmgSpec.scaling) {
        var ds = caster.getComponent("DerivedStats");
        var ps = caster.getComponent("PrimaryStats");
        var keys = Object.keys(dmgSpec.scaling);
        for (var i = 0; i < keys.length; i++) {
            var k = keys[i];
            var statVal = 0;
            if (ds !== null && ds.has(k)) statVal = ds.getInt(k);          // 先查三强度
            else if (ps !== null && ps.has(k)) statVal = ps.getInt(k);     // 再查裸基础属性
            total += Math.ceil(statVal * dmgSpec.scaling[k]);
        }
    }
```

并加一个共享 helper(debuff scaling 复用),放在 `computeDamage` 之后:

```js
  // 两级查找一个属性值：先 DerivedStats 再 PrimaryStats，取不到返回 0。
  lookupStat: function(caster, key) {
    var ds = caster.getComponent("DerivedStats");
    if (ds !== null && ds.has(key)) return ds.getInt(key);
    var ps = caster.getComponent("PrimaryStats");
    if (ps !== null && ps.has(key)) return ps.getInt(key);
    return 0;
  },
```

- [ ] **Step 4: 改 applyBuffFromSpec 支持 debuff scaling** — `:157-167` 替换

```js
  // 从 buff spec 应用 buff，解析 "@caster" 引用；
  // 若 buffSpec.scaling 存在(如 {damage:{智力:0.1}})，把 data 中对应字段加上 Σ⌈caster属性×系数⌉。
  applyBuffFromSpec: function(ctx, target, buffSpec) {
    var data = engine.newMap();
    var src = buffSpec.data || {};
    var keys = Object.keys(src);
    for (var i = 0; i < keys.length; i++) {
      var v = src[keys[i]];
      if (v === "@caster") v = ctx.actorId;
      data.put(keys[i], v);
    }
    if (buffSpec.scaling) {
      var fields = Object.keys(buffSpec.scaling);          // 如 ["damage"]
      for (var f = 0; f < fields.length; f++) {
        var field = fields[f];
        var coefs = buffSpec.scaling[field];               // 如 {智力:0.1}
        var add = 0;
        var statKeys = Object.keys(coefs);
        for (var s = 0; s < statKeys.length; s++) {
          add += Math.ceil(this.lookupStat(ctx.caster, statKeys[s]) * coefs[statKeys[s]]);
        }
        var baseVal = data.containsKey(field) ? data.get(field) : 0;
        data.put(field, baseVal + add);
      }
    }
    buffs.applyBuff(target.getId(), buffSpec.id, data);
  },
```

- [ ] **Step 5: 跑确认通过**

Run: `cd backend && mvn test -Dtest=SkillLibTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add mods/base-rules/handlers/skill/00_skill_lib.js backend/src/test/java/com/epic/engine/combat/SkillLibTest.java
git commit -m "feat(skill): computeDamage 两级查找(DerivedStats→PrimaryStats)+ debuff scaling"
```

---

## Task B2: 技能 yaml 公式更新 + heal 体质 scaling

**Files:**
- Modify: `mods/base-rules/skills/cleave.yaml`(damage 块)
- Modify: `mods/base-rules/skills/fireball.yaml`(damage + debuff scaling)
- Modify: `mods/base-rules/skills/light_field.yaml`(damage 块)
- Modify: `mods/base-rules/skills/poison_dart.yaml`(damage + debuff scaling)
- Modify: `mods/base-rules/skills/piercing_ray.yaml`(damage 块)
- Modify: `mods/base-rules/skills/pulse_wave.yaml`(改名「震波」+ damage)
- Modify: `mods/base-rules/skills/heal.js`(体质 scaling)

- [ ] **Step 1: 改各技能 damage 块**

`cleave.yaml`(战士,顺劈斩 = 武器伤害 + ⌈0.1×力量⌉):
```yaml
damage:
  base: attack
  scaling: { 力量: 0.1 }
```
(删除 `add: 5`)

`fireball.yaml`(法师,10 + ⌈0.2×智力⌉;灼烧 3 + ⌈0.1×智力⌉):
```yaml
damage:
  add: 10
  scaling: { 智力: 0.2 }
```
debuff 块加 scaling(data.damage 仍是基础 3):
```yaml
debuff:
  id: burning
  scaling: { damage: { 智力: 0.1 } }
  data:
    damage: 3
    remaining: 2
    stacking: refresh
    source: "@caster"
    color: "#ff4400"
    permanent: false
    positive: false
```

`light_field.yaml`(法师,15 + ⌈0.1×智力⌉,无武器底):
```yaml
damage:
  add: 15
  scaling: { 智力: 0.1 }
```
(删除 `base: attack`、`add: 7`)

`poison_dart.yaml`(盗贼,武器伤害 + ⌈0.1×敏捷⌉;中毒 3 + ⌈0.1×敏捷⌉):
```yaml
damage:
  base: attack
  scaling: { 敏捷: 0.1 }
```
(删除 `add: 3`)。debuff 块加 scaling:
```yaml
debuff:
  id: poison
  scaling: { damage: { 敏捷: 0.1 } }
  data:
    damage: 3
    remaining: 3
    stacking: stack
    maxStacks: 5
    color: "#9c27b0"
    permanent: false
    positive: false
```

`piercing_ray.yaml`(德鲁伊,10 + ⌈0.2×意志⌉,无武器底):
```yaml
damage:
  add: 10
  scaling: { 意志: 0.2 }
```
(删除 `base: attack`、`add: 6`)

`pulse_wave.yaml`(护卫震波,武器伤害 + ⌈0.1×体质⌉):改 `name: "震波"`、`description: "震波，对目标造成冲击伤害"`;damage 块:
```yaml
damage:
  base: attack
  scaling: { 体质: 0.1 }
```
(删除 `add: 6`;**保留 id `pulse_wave`** 不改,避免动 golden 用例名/技能引用——仅改显示名)

- [ ] **Step 2: 改 heal.js 体质 scaling** — `heal.js` 的 `var healAmount = 15;` 替换

```js
    var caster = store.get(actorId);
    var prim = caster.getComponent("PrimaryStats");
    var con = (prim !== null && prim.has("体质")) ? prim.getInt("体质") : 0;
    var healAmount = 10 + Math.ceil(0.1 * con);   // 护卫治愈 = 10 + ⌈0.1×体质⌉
```
(原 `var caster = store.get(actorId);` 已在上方;若重复则只插入 healAmount 计算两行,复用既有 caster 变量。)

- [ ] **Step 3: 编译检查(语法)**

Run: `cd backend && mvn test -Dtest=EngineBootTest`
Expected: PASS(handler/yaml 加载无语法错)

- [ ] **Step 4: 提交**

```bash
git add mods/base-rules/skills/cleave.yaml mods/base-rules/skills/fireball.yaml mods/base-rules/skills/light_field.yaml mods/base-rules/skills/poison_dart.yaml mods/base-rules/skills/piercing_ray.yaml mods/base-rules/skills/pulse_wave.yaml mods/base-rules/skills/heal.js
git commit -m "feat(skill): 按新数值改各技能公式(火球/光击阵/顺劈斩/毒镖/贯穿/震波/治愈)"
```

---

## Task B3: golden 重生成(harness 补 PrimaryStats)

**Files:**
- Modify: `backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java:75-102`(`addUnit` 补 PrimaryStats + EquipmentSlots)
- Regen: `backend/src/test/resources/golden/{fireball,pulse_wave,cleave,light_field,poison_dart,piercing_ray,heal}.json`

> 改了数值的技能 golden 必须重生成。harness 的 caster(mage)目前**无 PrimaryStats**,新 scaling 读裸属性会取 0;需给 caster 补 PrimaryStats 才能让 golden 体现公式。

- [ ] **Step 1: 给 addUnit 补 PrimaryStats** — `SkillFidelityHarness.addUnit`(`:75-102`)在 DerivedStats 之后加

```java
        Component prim = new Component("PrimaryStats");
        // 确定性测试值：让各属性区分明显，便于核对公式
        prim.set("力量", 12); prim.set("敏捷", 7); prim.set("智力", 10);
        prim.set("体质", 8);  prim.set("意志", 5); prim.set("weaponAttr", "力量");
        e.addComponent(prim);
```
(DerivedStats 的 `法术强度` 已 = atk,法师 atk=10 → 智力也设 10,数值自洽。)

- [ ] **Step 2: 手算各 golden 期望值(核对用)**

caster mage:attack(CombatStats)=10,力量12/敏捷7/智力10/体质8/意志5,法术强度=10。
- fireball = 10 + ⌈10×0.2⌉ = 12;灼烧 damage = 3 + ⌈10×0.1⌉ = 4
- light_field = 15 + ⌈10×0.1⌉ = 16(每目标)
- cleave = 10 + ⌈12×0.1⌉ = 12
- poison_dart = 10 + ⌈7×0.1⌉ = 11;中毒 damage = 3 + ⌈7×0.1⌉ = 4
- piercing_ray = 10 + ⌈5×0.2⌉ = 11
- pulse_wave(震波) = 10 + ⌈8×0.1⌉ = 11
- heal = 10 + ⌈0.1×8⌉ = 11

- [ ] **Step 3: 删旧 golden 让其重新 capture**

```bash
cd backend && rm src/test/resources/golden/fireball.json src/test/resources/golden/pulse_wave.json src/test/resources/golden/cleave.json src/test/resources/golden/light_field.json src/test/resources/golden/poison_dart.json src/test/resources/golden/piercing_ray.json src/test/resources/golden/heal.json
```

- [ ] **Step 4: 跑 SkillFidelityTest 自动 capture,再跑确认绿**

Run: `cd backend && mvn test -Dtest=SkillFidelityTest`(第一次 capture)
Run 再次: `cd backend && mvn test -Dtest=SkillFidelityTest`(确认 PASS)
Expected: 第二次 PASS。**人工核对**新 golden 里 hp_change/damage_number 数值与 Step 2 手算一致(打开几个 json 确认 `amount`/`value`)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/test/java/com/epic/engine/combat/SkillFidelityHarness.java backend/src/test/resources/golden/
git commit -m "test(golden): harness 补 PrimaryStats,重生成改数值技能的 golden"
```

---

## Task B4: 新建背刺(盗贼)

**Files:**
- Create: `mods/base-rules/skills/backstab.yaml`

- [ ] **Step 1: 建 backstab.yaml**(单体,武器伤害 + ⌈0.2×敏捷⌉,无位置条件)

```yaml
id: backstab
name: "背刺"
description: "背刺，对单体造成高额伤害"
category: skill
silenceable: true
effect: damage_only
damage:
  base: attack
  scaling: { 敏捷: 0.2 }
targeting:
  mode: pattern
  field: enemy
  pattern: [[0,0]]
  steps:
    - prompt: "选择目标"
      filter: enemy
      count: 1
log:
  template: "{caster} 的背刺命中 {target}，造成 {damage} 点伤害"
animation:
  - type: lunge
    target: actor
    side: actor_side
  - type: slash
    target: target
    color: "#b0bec5"
  - type: impact
    target: target
    color: "#b0bec5"
  - type: shake
    target: target
    intensity: heavy
  - type: damage_number
    target: target
    color: damage
```

- [ ] **Step 2: 加 fidelity 用例(可选但推荐)** — 在 `SkillFidelityTest.CASES` 加 `{"backstab", "goblin1"}`,跑一次 capture golden。

Run: `cd backend && mvn test -Dtest=SkillFidelityTest`(capture)→ 再跑确认绿。
期望 backstab = 10 + ⌈7×0.2⌉ = 12。核对 golden。

- [ ] **Step 3: 提交**

```bash
git add mods/base-rules/skills/backstab.yaml backend/src/test/java/com/epic/engine/combat/SkillFidelityTest.java backend/src/test/resources/golden/backstab.json
git commit -m "feat(skill): 新建盗贼背刺(单体,武器伤害+⌈0.2×敏捷⌉)"
```

---

## Task B5: war_cry buff→modifier 桥

**Files:**
- Create: `mods/base-rules/buffs/war_cry.js`
- Test: `backend/src/test/java/com/epic/engine/combat/CurseSkillTest.java`(新建,含 war_cry 用例;命名沿用但覆盖 buff 桥)

> war_cry 当前只挂 `Buff_war_cry` 组件,无任何属性效果。新增桥:buff 应用时注册 `typeId:"buff"`(priority 10,在 derived 300 前跑)modifier,给 `PrimaryStats.力量` 加 ⌈0.5×物理强度⌉;移除时撤。**如实按 spec 实现让用户实测无限迭代行为,不做快照式特判。**

- [ ] **Step 1: 写失败测试** — `CurseSkillTest.java`(JS 加载模式,载 derived_stats + war_cry.js + curse 相关)

```java
package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class CurseSkillTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;
    ModifierTypeRegistry typeReg; ModifierChainService chainService;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.bindService("buffs", new BuffService(bus, store));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/buffs/war_cry.js")), "war_cry.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/buffs/cursed.js")), "cursed.js");
    }
    @AfterEach
    void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    Entity warrior(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量", 10); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 10); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 130); h.set("maxHp", 130); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        store.add(e);
        js("engine.setBase('" + id + "');");
        js("registerDerivedModifier('" + id + "');");
        return e;
    }

    @Test
    void war_cry_boosts_strength_and_removal_restores() {
        warrior("w1");
        // 物理强度 = 力量10×2 = 20 → 战吼加力量 ⌈0.5×20⌉ = 10（apply 时读到的物理强度见实现说明）
        js("buffs.applyBuff('w1', 'war_cry', engine.newMap());");
        int afterApply = store.get("w1").getComponent("PrimaryStats").getInt("力量");
        assertThat(afterApply).isGreaterThan(10);   // 力量被战吼抬高
        js("buffs.removeBuff('w1', 'war_cry');");
        assertThat(store.get("w1").getComponent("PrimaryStats").getInt("力量")).isEqualTo(10); // 撤回 base
    }
}
```

> 注:断言用 `isGreaterThan(10)` 而非精确值——因 buff modifier(prio 10)在 derived(prio 300)前跑,读到的物理强度取决于 recalc 时序,精确值留给用户实测核对(prompt:如实实现、实测)。撤销后必回到 base 力量 10(modifier 移除 + restoreBaseState)。

- [ ] **Step 2: 跑确认失败**

Run: `cd backend && mvn test -Dtest=CurseSkillTest#war_cry_boosts_strength_and_removal_restores`
Expected: FAIL — `war_cry.js` 不存在 / 力量不变。

- [ ] **Step 3: 建 war_cry.js**

```js
// 战吼 buff→modifier 桥：应用时给 力量 +⌈0.5×物理强度⌉(typeId buff，priority 10，在 derived 之前跑)；
// 移除时撤销。modifier id 含 targetId，支持多单位独立。
// 注：如实按 spec 实现，物理强度在 prio 10 读到的是本次 recalc 中 derived(prio 300) 尚未刷新的值——
//     用户要实测「力量→物理强度→战吼」的迭代行为，不做快照式特判。
engine.on("buff.applied", 50, function(event) {
    if (event.get("buffId") !== "war_cry") return;
    var targetId = event.get("targetId");
    engine.addModifier(targetId, {
        typeId: "buff",
        id: "war_cry_" + targetId,
        label: "战吼",
        apply: function(ent) {
            var d = ent.getComponent("DerivedStats");
            var p = ent.getComponent("PrimaryStats");
            if (d === null || p === null) return;
            var bonus = Math.ceil(0.5 * d.getInt("物理强度"));
            p.set("力量", p.getInt("力量") + bonus);
        }
    });
});

engine.on("buff.removed", 50, function(event) {
    if (event.get("buffId") !== "war_cry") return;
    engine.removeModifier(event.get("targetId"), "war_cry_" + event.get("targetId"));
});
```

> 执行子代理须确认 `engine.removeModifier` 触发 recalc(从 `equip.js` unequip 路径已知它会)。`buff.applied` 由 `BuffService.applyBuff` 在写入 `Buff_war_cry` 组件后 fire。

- [ ] **Step 4: 跑确认通过(war_cry 用例)**

Run: `cd backend && mvn test -Dtest=CurseSkillTest#war_cry_boosts_strength_and_removal_restores`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add mods/base-rules/buffs/war_cry.js backend/src/test/java/com/epic/engine/combat/CurseSkillTest.java
git commit -m "feat(skill): 战吼 buff→modifier 桥(力量+⌈0.5×物理强度⌉,移除可撤)"
```

---

## Task B6: 诅咒重做(cursed buff→modifier + tick)

**Files:**
- Create: `mods/base-rules/buffs/cursed.js`
- Modify: `mods/base-rules/skills/curse.yaml`(`debuff.id: cursed`)
- Test: `CurseSkillTest.java`(加诅咒用例)

> 诅咒:全体敌人 5 主属性各 −2(clamp ≥0),3 回合。−2 体质 → derived 重算 maxHp 下降 → hp clamp 到新上限(A2 已加);过期 maxHp 回升但 hp 不补(clamp 只降不升)。

- [ ] **Step 1: 改 curse.yaml** — `debuff.id: cursed`(原 `id: cursed` 已是?核对:当前 curse.yaml debuff.id=`cursed`。**已一致,无需改 id**。确认 `remaining: 3`、`stacking: refresh` 保留。)

> 若当前 curse.yaml 的 `debuff.id` 已是 `cursed`(见现状),本 Step 仅确认,无改动。

- [ ] **Step 2: 写失败测试** — `CurseSkillTest` 加

```java
@Test
void curse_reduces_all_primary_stats_and_clamps_hp() {
    Entity e = warrior("c1");
    int maxBefore = e.getComponent("Health").getInt("maxHp"); // 30 + 体质10×10 = 130
    js("var d = engine.newMap(); d.put('remaining', 3); d.put('stacking','refresh');" +
       " buffs.applyBuff('c1', 'cursed', d);");
    Entity r = store.get("c1");
    assertThat(r.getComponent("PrimaryStats").getInt("力量")).isEqualTo(8);   // 10-2
    assertThat(r.getComponent("PrimaryStats").getInt("体质")).isEqualTo(8);   // 10-2
    // 体质8 → maxHp = 30 + 80 = 110；hp 原 130 被 clamp 到 110
    assertThat(r.getComponent("Health").getInt("maxHp")).isEqualTo(110);
    assertThat(r.getComponent("Health").getInt("hp")).isEqualTo(110);
    assertThat(maxBefore).isEqualTo(130);

    // 移除：体质回 10 → maxHp 回 130，但 hp 不补（仍 110）
    js("buffs.removeBuff('c1', 'cursed');");
    Entity r2 = store.get("c1");
    assertThat(r2.getComponent("PrimaryStats").getInt("力量")).isEqualTo(10);
    assertThat(r2.getComponent("Health").getInt("maxHp")).isEqualTo(130);
    assertThat(r2.getComponent("Health").getInt("hp")).isEqualTo(110);   // 不补
}

@Test
void curse_clamps_primary_stats_at_zero() {
    Entity e = new Entity("c2");
    Component p = new Component("PrimaryStats");
    p.set("力量", 1); p.set("敏捷", 1); p.set("智力", 1);
    p.set("体质", 1); p.set("意志", 1); p.set("weaponAttr", "力量");
    e.addComponent(p);
    e.addComponent(new Component("DerivedStats"));
    Component h = new Component("Health"); h.set("hp", 40); h.set("maxHp", 40); e.addComponent(h);
    Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
    store.add(e);
    js("engine.setBase('c2'); registerDerivedModifier('c2');");
    js("var d = engine.newMap(); d.put('remaining', 3); buffs.applyBuff('c2', 'cursed', d);");
    // 1-2 clamp 到 0，不为负
    assertThat(store.get("c2").getComponent("PrimaryStats").getInt("力量")).isEqualTo(0);
}
```

- [ ] **Step 3: 跑确认失败**

Run: `cd backend && mvn test -Dtest=CurseSkillTest#curse_reduces_all_primary_stats_and_clamps_hp`
Expected: FAIL — `cursed.js` 不存在,属性未削。

- [ ] **Step 4: 建 cursed.js**(modifier 桥 + round_end tick)

```js
// 诅咒 buff→modifier 桥：5 主属性各 -2(clamp ≥0)，typeId buff(priority 10，derived 前跑)。
// 体质削减经 derived 重算 maxHp，hp 由 derived_stats 的 clamp 收口(只降不升 → 过期不补血)。
var CURSE_STATS = ["力量", "敏捷", "智力", "体质", "意志"];

engine.on("buff.applied", 50, function(event) {
    if (event.get("buffId") !== "cursed") return;
    var targetId = event.get("targetId");
    engine.addModifier(targetId, {
        typeId: "buff",
        id: "cursed_" + targetId,
        label: "诅咒",
        apply: function(ent) {
            var p = ent.getComponent("PrimaryStats");
            if (p === null) return;
            for (var i = 0; i < CURSE_STATS.length; i++) {
                var k = CURSE_STATS[i];
                if (p.has(k)) p.set(k, Math.max(0, p.getInt(k) - 2));   // clamp ≥0
            }
        }
    });
});

engine.on("buff.removed", 50, function(event) {
    if (event.get("buffId") !== "cursed") return;
    engine.removeModifier(event.get("targetId"), "cursed_" + event.get("targetId"));
});

// 每回合结束递减 remaining，到 0 移除(仿 poison tick，但无 DoT 伤害)。
engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var c = entity.getComponent("Buff_cursed");
        if (c === null) continue;
        var remaining = c.has("remaining") ? c.getInt("remaining") : 3;
        remaining--;
        if (remaining <= 0) buffs.removeBuff(entity.getId(), "cursed");
        else c.set("remaining", remaining);
    }
});
```

- [ ] **Step 5: 跑确认通过(全 CurseSkillTest)**

Run: `cd backend && mvn test -Dtest=CurseSkillTest`
Expected: PASS

- [ ] **Step 6: 重生成 curse golden** — debuff_only 输出可能不含数值变化(诅咒 golden 是 buff_applied 动画),但属性桥不影响 combatEvent 输出格式。仍跑一次 SkillFidelityTest 确认 curse golden 不破:

Run: `cd backend && mvn test -Dtest=SkillFidelityTest`
Expected: PASS(若 curse.json 因 debuff 结构变化 mismatch,删除并重 capture,核对仅动画/log)

- [ ] **Step 7: 提交**

```bash
git add mods/base-rules/buffs/cursed.js mods/base-rules/skills/curse.yaml backend/src/test/java/com/epic/engine/combat/CurseSkillTest.java backend/src/test/resources/golden/curse.json
git commit -m "feat(skill): 诅咒重做 — 全敌5属性-2(clamp≥0)+体质降maxHp不补hp+3回合tick"
```

---

## Task B7: starting_skills 入 schema + select 读取

**Files:**
- Modify: `mods/base-rules/schemas/sub/class_warrior.schema.yaml` 等 5 个
- Modify: `mods/base-rules/handlers/character/select.js:108-128`(技能分发)
- Test: `backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java`(扩,验证按职业发技能)

- [ ] **Step 1: 5 个 schema 加 starting_skills**

`class_warrior.schema.yaml` 末尾加:
```yaml
starting_skills: [cleave, war_cry]
```
`class_mage.schema.yaml`:
```yaml
starting_skills: [fireball, light_field]
```
`class_rogue.schema.yaml`:
```yaml
starting_skills: [poison_dart, backstab]
```
`class_druid.schema.yaml`:
```yaml
starting_skills: [curse, piercing_ray]
```
`class_guardian.schema.yaml`:
```yaml
starting_skills: [heal, pulse_wave]
```

- [ ] **Step 2: 改 select.js 读 starting_skills** — `select.js:119-126`(`if (classId === "mage") {...}` 块)替换

```js
    // 职业技能:读 schema 的 starting_skills(替代硬编码)
    if (classSchema !== null && classSchema.raw().get("starting_skills") !== null) {
        var startSkills = classSchema.raw().get("starting_skills");
        for (var sk = 0; sk < startSkills.size(); sk++) {
            var ss = engine.newMap();
            ss.put("id", String(startSkills.get(sk))); ss.put("level", 1); ss.put("cooldown", 0);
            skillList.add(ss);
        }
    }
```

- [ ] **Step 3: 写测试** — `CharacterFlowTest` 加(套用该文件既有创建角色流程)

```java
// 创建战士 → Skills 含 cleave + war_cry（来自 schema starting_skills）
@Test
void warrior_gets_class_starting_skills() {
    // 走该测试类既有的 confirm_character 流程，classId="warrior"
    // 取创建出的角色实体 Skills.list，断言含 "cleave" 与 "war_cry"
    // (具体构造对齐 CharacterFlowTest 既有用例风格)
}
```

> 执行子代理:Read `CharacterFlowTest.java`,套用既有创建角色用例补全(断言 Skills.list 含 cleave/war_cry;再补一个 mage→fireball/light_field 防回归)。

- [ ] **Step 4: 跑确认通过**

Run: `cd backend && mvn test -Dtest=CharacterFlowTest`
Expected: PASS

- [ ] **Step 5: 全量回归**

Run: `cd backend && mvn test`
Expected: 全绿(108 + 新增)。

- [ ] **Step 6: 提交**

```bash
git add mods/base-rules/schemas/sub/ mods/base-rules/handlers/character/select.js backend/src/test/java/com/epic/engine/character/CharacterFlowTest.java
git commit -m "feat(skill): 职业 starting_skills 入 schema,select.js 按职业发技能"
```

---

# Phase C — 创建/选择页(#6)

## Task C1: 后端下发职业预览

**Files:**
- Modify: `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`(`buildCharacterSelectSnapshot` + 注入 SchemaRegistry)
- Modify: `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`(characterSelect 加 classPreviews)
- Test: `backend/src/test/java/com/epic/engine/snapshot/CharacterSelectPreviewTest.java`(新建)

> 执行子代理须先 Read `WorldSnapshot.java` 与 `SnapshotService.java` 全文,确认 record 结构与 SchemaRegistry 访问方式(SchemaRegistry 是 Spring bean,可构造注入)。

- [ ] **Step 1: 在 WorldSnapshot 加 ClassPreview record + characterSelect 重载**

```java
// WorldSnapshot.java 内
public record ClassPreview(String id, String label, String description,
                           Map<String, Integer> growth, Map<String, String> modifiers,
                           String portrait) {}
```
给 `characterSelect(...)` 增加一个 `List<ClassPreview> classPreviews` 参数(或在现有 record 加字段,保持既有调用点编译——优先新增字段并更新所有调用点)。

- [ ] **Step 2: SnapshotService 构造注入 SchemaRegistry,组装预览**

```java
// 构造器加 SchemaRegistry schemaRegistry 参数并存字段
private List<WorldSnapshot.ClassPreview> buildClassPreviews() {
    List<WorldSnapshot.ClassPreview> out = new ArrayList<>();
    for (Schema s : schemaRegistry.getByCategory("class")) {   // 确认 API：getByCategory / 遍历
        Map<String, Object> raw = s.raw();
        @SuppressWarnings("unchecked")
        Map<String, Integer> growth = raw.get("growth") instanceof Map
                ? (Map<String, Integer>) raw.get("growth") : Map.of();
        // modifiers: List<{field,value}> → Map<field,value>
        Map<String, String> mods = new LinkedHashMap<>();
        Object mlist = raw.get("modifiers");
        if (mlist instanceof List<?> ml) {
            for (Object mo : ml) if (mo instanceof Map<?,?> mm)
                mods.put(String.valueOf(mm.get("field")), String.valueOf(mm.get("value")));
        }
        String portrait = raw.get("portrait") != null ? String.valueOf(raw.get("portrait")) : null;
        out.add(new WorldSnapshot.ClassPreview(s.id(), s.label(), s.description(), growth, mods, portrait));
    }
    return out;
}
```
`buildCharacterSelectSnapshot` 末尾把 `buildClassPreviews()` 传入 `characterSelect(...)`。

> 注:`SchemaRegistry` 的确切只读 API(`getByCategory`/`all`/`get`)以源码为准;`Schema.raw()` 已知可读自定义字段(select.js 用过)。`portrait` 字段 schema 暂无,返回 null,前端预留占位(spec:预留图片位不填)。

- [ ] **Step 3: 写测试** — `CharacterSelectPreviewTest.java`

```java
// buildSnapshot(无 session)→ classPreviews 含 5 职业,warrior 的 growth/label 正确
@Test
void select_snapshot_includes_class_previews() {
    // 用 Spring 上下文或手动装配 SnapshotService(注入真实 SchemaRegistry,loadFromModPath base-rules)
    // 断言 snapshot.classPreviews() 含 id=warrior，label="战士"，growth.get("力量")==3
}
```
> 执行子代理:参考 `CharacterStatsTest`/`SchemaRegistryTest` 的装配方式构造 SchemaRegistry 与 SnapshotService。

- [ ] **Step 4: 跑确认通过 + 全量回归**

Run: `cd backend && mvn test -Dtest=CharacterSelectPreviewTest` 然后 `mvn test`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/epic/engine/snapshot/ backend/src/test/java/com/epic/engine/snapshot/CharacterSelectPreviewTest.java
git commit -m "feat(snapshot): 选择页下发职业预览(label/description/growth/modifiers/portrait)"
```

---

## Task C2: 创建/选择页前端

**Files:**
- Modify/Create: `frontend/src/components/CharacterSelect.vue` / `frontend/src/components/CharacterCreate.vue`

> **用 frontend-design skill。** 无 Vue 单测基建 → 手测验证(`./start.ps1` 起服务,浏览器看)。
> **布局约束(spec / 用户记忆):** 这是用户**明确要求改布局**的页面,不受「只改颜色/字体禁改布局」记忆约束。边框遵守「2px+ 粗边框」记忆。

- [ ] **Step 1: 调用 frontend-design skill**,实现布局:
  - 上方一排角色列表(选中态高亮)。
  - 下方选中角色详情:第一行角色名;左列属性 + 升级加成(growth);右列头像占位 `<img>` 框(portrait 字段,可空)+ 头像下角色描述。
  - 创建页:职业选择时用 `classPreviews` 实时显示该职业 growth/属性/描述 + 头像占位。

- [ ] **Step 2: 接后端数据** — `CharacterSelect.vue` 读 snapshot 的 `classPreviews`(C1 下发);`api/client.js` 无需改(snapshot 端点已带)。

- [ ] **Step 3: 手测**
  - `./start.ps1` 起前后端。
  - 浏览器开 `localhost:5173`:进创建页,切换职业看 growth/属性/描述/头像占位刷新;创建后回选择页看角色详情布局。
  - 截图或描述确认布局符合 spec。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/CharacterSelect.vue frontend/src/components/CharacterCreate.vue
git commit -m "feat(ui): 创建/选择页重做布局(角色列表+属性/growth+头像占位+描述)"
```

---

# 收尾

- [ ] **全量回归**:`cd backend && mvn test` 全绿。
- [ ] **手测整链**:创建 5 职业角色,各装 5 把武器观察 attack 变化(双手剑力量×2 vs 不顺手武器);打一场遭遇战验证各职业技能(顺劈/战吼迭代、火球灼烧、毒镖背刺、诅咒降属性掉血上限、治愈震波)。
- [ ] **收尾分支**:用 `superpowers:finishing-a-development-branch` 收尾整个 `feature/ch1-f2-attribute-table`(批次 A + 批次 B 一起)。
- [ ] **更新 prompt.md**:批次 B 完成状态 + Feature #3「伤害类型/抗性」接缝提示(本批已提前部分武器/伤害公式)。

---

## Self-Review 记录

- **Spec 覆盖**:#3 武器(A1-A3)、#5 技能(B1-B7 覆盖 computeDamage 泛化/各公式/背刺/战吼/诅咒/starting_skills)、#6 创建页(C1-C2)。✅ 全覆盖。
- **类型一致**:`weaponAttr`/`base`(items.yaml ↔ derived_stats 读取)一致;`组件.字段`解析(equip.js)与 select.js class modifier 同模式;buff modifier id 格式 `<buffId>_<targetId>`(war_cry/cursed 一致)。
- **占位**:无 TBD;少数测试用例(SkillLibTest/CharacterFlowTest/CharacterSelectPreviewTest)给出期望值与构造指引,要求执行子代理对齐既有测试类风格补全——因这些断言需读既有 helper,属合理的「按现有风格补全」而非占位。
- **风险点**:war_cry/cursed 的 buff modifier 在 prio 10 读 DerivedStats(prio 300 后刷新)→ 读到的强度可能是上一轮值;**这是 spec 明确要求的实测行为**,测试用宽松断言(isGreaterThan),精确值留实测。
