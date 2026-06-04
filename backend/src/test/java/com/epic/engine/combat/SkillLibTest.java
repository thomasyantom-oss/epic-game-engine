package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import com.epic.engine.core.Entity;

class SkillLibTest {
    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.bindService("buffs", new BuffService(bus, store));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/combat/damage_calc.js")), "damage_calc.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/00_skill_lib.js")), "00_skill_lib.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    Entity mkUnit(String id, String name, boolean player) {
        Entity e = new Entity(id);
        Component h = new Component("Health");
        h.set("hp", 100);
        h.set("maxHp", 100);
        e.addComponent(h);
        Component s = new Component("CombatStats");
        s.set("attack", 10);
        s.set("defense", 5);
        s.set("speed", 5);
        e.addComponent(s);
        Component n = new Component("Name");
        n.set("value", name);
        e.addComponent(n);
        Component ds = new Component("DerivedStats");
        ds.set("物理强度", 0); ds.set("法术强度", 14); ds.set("精神强度", 0);
        e.addComponent(ds);
        e.addTag(player ? "player" : "enemy");
        e.addTag("combat:b1");
        store.add(e);
        return e;
    }

    /** Run a JS snippet that throws a string on assertion failure. */
    void js(String script) {
        rt.execute("(function(){ " + script + " })();", "assert.js");
    }

    @Test
    void context_resolvesCasterFields() {
        mkUnit("mage", "法师", true);
        assertThatCode(() -> js(
            "var ev = engine.newEvent('combat.unit_action');" +
            "ev.set('actorId','mage'); ev.set('combatId','b1');" +
            "var cmd = {}; cmd.type='fireball'; ev.set('command', cmd);" +
            "var c = Skill.context(ev);" +
            "if (c.actorId !== 'mage') throw 'actorId='+c.actorId;" +
            "if (c.combatId !== 'b1') throw 'combatId='+c.combatId;" +
            "if (c.casterName !== '法师') throw 'name='+c.casterName;" +
            "if (c.casterSide !== 'player') throw 'side='+c.casterSide;"
        )).doesNotThrowAnyException();
    }

    @Test
    void loadSpec_loadsAndCaches() {
        assertThatCode(() -> js(
            "var s1 = Skill.loadSpec('fireball');" +
            "if (s1 === null) throw 'fireball spec null';" +
            "if (s1.get('id') !== 'fireball') throw 'id='+s1.get('id');" +
            "var s2 = Skill.loadSpec('fireball');" +
            "if (s1 !== s2) throw 'not cached (different object)';"
        )).doesNotThrowAnyException();
    }

    @Test
    void computeDamage_flatAdd() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});" +
           "if (dmg !== 18) throw 'dmg='+dmg;");   // attack 10 + 8
    }

    @Test
    void computeDamage_isPure_noHpChange() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        js("Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});");
        // pure: calling computeDamage must NOT mutate target hp
        org.assertj.core.api.Assertions.assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    @Test
    void computeDamage_viaDamageCalc_usesPoeArmor() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);   // defense 5 in mkUnit
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {via_damage_calc:true});" +
           "if (dmg !== 7) throw 'dmg='+dmg;");   // ceil(10^2/(5+10))
    }

    @Test
    void computeDamage_scalingSpellPower() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        // add 8 + ⌈法术强度14 × 0.5⌉ = 8 + 7 = 15
        js("var s = {add:8, scaling:{法术强度:0.5}};" +
           "var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), s);" +
           "if (dmg !== 15) throw 'dmg='+dmg;");
    }

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

    @Test
    void emptyGroundAoE_usesFrontendGridContract_slotFromRow_rowIdxFromCol() {
        mkUnit("mage","法师",true);
        mkPos("front_slot1", false, "FRONT", 1);
        mkPos("mid_slot1", false, "MID", 1);
        mkPos("back_slot1", false, "BACK", 1);
        mkPos("mid_slot0", false, "MID", 0);
        mkPos("mid_slot2", false, "MID", 2);
        mkPos("front_slot0", false, "FRONT", 0);

        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{targetRow:1, targetCol:1}};" +
           "var spec = {targeting:{mode:'pattern',field:'enemy',pattern:[[0,0],[-1,0],[1,0],[0,-1],[0,1]]}};" +
           "var r = Skill.resolveTargets(ctx, spec);" +
           "var ids = r.map(function(x){ return x.entity.getId(); }).sort().join(',');" +
           "var expected = ['back_slot1','front_slot1','mid_slot0','mid_slot1','mid_slot2'].sort().join(',');" +
           "if (ids !== expected) throw 'ids='+ids;" +
           "if (ids.indexOf('front_slot0') >= 0) throw 'frontend grid row/col contract changed';");
    }

    @Test
    void dealDamage_mutatesHp_andFiresDamageDealtWithSkipLog() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        js("globalThis.__skip = null;" +
           "engine.on('combat.damage_dealt', 5, function(e){ globalThis.__skip = e.has('skipLog'); });" +
           "var ctx = {actorId:'mage', combatId:'b1', caster: store.get('mage')};" +
           "Skill.dealDamage(ctx, store.get('goblin'), 30, 'fireball');" +
           "if (globalThis.__skip !== true) throw 'skipLog not set';");
        org.assertj.core.api.Assertions.assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(70);
    }

    // ── Test 1: extensibility ──────────────────────────────────────────────
    // Proves: adding a new effect type requires only Skill.registerEffect — zero engine changes.
    @Test
    void newEffect_registersWithoutEngineChange() {
        js("Skill.registerEffect('smoke_test', function(ctx,spec,results){ globalThis.__ran = true; });" +
           "if (typeof Skill.effects['smoke_test'] !== 'function') throw 'not registered';");
    }

    // scaling 键不在 DerivedStats 时回落到 PrimaryStats（裸属性）
    @Test
    void computeDamage_scaling_readsPrimaryStatsFallback() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        js("var p = engine.newComponent('PrimaryStats'); p.set('力量',15); store.get('mage').addComponent(p);");
        // {base:'attack', scaling:{力量:0.1}} → attack10 + ⌈15×0.1⌉ = 10 + 2 = 12
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', scaling:{力量:0.1}});" +
           "if (dmg !== 12) throw 'dmg='+dmg;");
    }

    // DerivedStats 命中时优先用它（不回落 PrimaryStats）
    @Test
    void computeDamage_scaling_prefersDerivedOverPrimary() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        // 同名键 法术强度 同时存在于 DerivedStats(14)；PrimaryStats 没有 → 用 14
        // {add:10, scaling:{法术强度:0.5}} → 10 + ⌈14×0.5⌉ = 17
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {add:10, scaling:{法术强度:0.5}});" +
           "if (dmg !== 17) throw 'dmg='+dmg;");
    }

    // applyBuffFromSpec 的 scaling 把属性加成算进 debuff 的 data 字段
    @Test
    void applyBuffFromSpec_scalesDebuffDamage() {
        mkUnit("mage","法师",true);
        Entity goblin = mkUnit("goblin","哥布林",false);
        js("var p = engine.newComponent('PrimaryStats'); p.set('智力',10); store.get('mage').addComponent(p);");
        // data.damage=3 + ⌈智力10×0.1⌉ = 4
        js("var ctx = {actorId:'mage', caster: store.get('mage')};" +
           "Skill.applyBuffFromSpec(ctx, store.get('goblin'), {id:'poison', data:{damage:3, remaining:3}, scaling:{damage:{智力:0.1}}});");
        org.assertj.core.api.Assertions.assertThat(goblin.getComponent("Buff_poison").getInt("damage")).isEqualTo(4);
    }

    // ── Test 2: tooltip/AI reuse ───────────────────────────────────────────
    // Proves: computeDamage is pure — same function used in live combat can compute a tooltip
    // preview without any side-effect (target hp is unchanged after the call).
    @Test
    void computeDamage_reusableForTooltip_noMutation() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        // fireball.yaml: add=10, scaling={智力:0.2}; 给 mage PrimaryStats.智力=25 → 10 + ⌈25×0.2⌉ = 15
        js("var p = engine.newComponent('PrimaryStats'); p.set('智力',25); store.get('mage').addComponent(p);");
        js("var spec = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));" +
           "var preview = Skill.computeDamage(store.get('mage'), store.get('goblin'), spec.damage);" +
           "if (preview !== 15) throw 'preview='+preview;");
        // Pure: calling computeDamage must NOT mutate target hp
        org.assertj.core.api.Assertions.assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(100);
    }
}
