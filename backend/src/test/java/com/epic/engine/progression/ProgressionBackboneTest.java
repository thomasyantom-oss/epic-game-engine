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
        runtime.execute(Files.readString(ch.resolve("leveling.js")), "leveling.js");
        // stub 掉成长 modifier 与持久化，隔离 gain_xp 的 XP/等级数学。
        // 这些单测 runtime 未 bindService('persistence')，故直接在全局定义即可（每个测试新建 runtime，互不影响）。
        // registerLevelGrowthModifier 是 leveling.js 里的 function 声明，可被全局重新赋值覆盖。
        runtime.execute(
            "registerLevelGrowthModifier = function(){}; " +
            "var persistence = { save: function(){} }; " +
            "applySpec = function(id){ };",
            "stubs.js");
        runtime.execute(Files.readString(ch.resolve("recalculate_hooks.js")), "recalculate_hooks.js");
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

    @Test
    void debugSetLevel_clampsToCap() {
        makeHero("d1", 1, 0);
        runtime.execute(
            "var ev = engine.newEvent('debug.set_level'); ev.set('entityId','d1'); ev.set('level', 250); " +
            "engine.fire('debug.set_level', ev);",
            "setlevel.js");
        assertThat(store.get("d1").getComponent("Experience").getInt("level")).isEqualTo(100);
    }

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

    @Test
    void orbCost_defaultsWhenMissing() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        long missing = probe("Talent.orbCount({ type: '爆破' })");
        long explicit = probe("Talent.orbCount({ type: '爆破', count: 3 })");
        assertThat(missing).isEqualTo(1L);
        assertThat(explicit).isEqualTo(3L);
    }

    @Test
    void xpReward_followsCurve() {
        // reward = round(14 * L^0.9): L1=14, L10=round(14*7.943)=111
        assertThat(probe("Progression.xpReward(1)")).isEqualTo(14L);
        assertThat(probe("Progression.xpReward(10)")).isEqualTo(111L);
    }

    /** 造玩家(player tag + Experience)与敌人(enemy tag + CombatantMeta)，同挂 combat:cid。 */
    void makeCombat(String cid, int playerLevel, int monLevel, Integer xpRewardOverride) throws Exception {
        String enemyExtra = xpRewardOverride != null
            ? "m.set('xpReward', " + xpRewardOverride + "); " : "";
        runtime.execute(
            "var p = engine.createEntity('pc'); " +
            "var exp = engine.newComponent('Experience'); exp.set('level', " + playerLevel + "); exp.set('xp', 0); p.addComponent(exp); " +
            "var ch = engine.newComponent('Character'); ch.set('level', " + playerLevel + "); ch.set('classId','mage'); p.addComponent(ch); " +
            "p.addTag('player'); p.addTag('combat:" + cid + "'); store.add(p); " +
            "var en = engine.createEntity('en'); " +
            "var m = engine.newComponent('CombatantMeta'); m.set('level', " + monLevel + "); " + enemyExtra +
            "en.addComponent(m); en.addTag('enemy'); en.addTag('combat:" + cid + "'); store.add(en);",
            "mkcombat.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/combat/xp_reward.js")), "xp_reward.js");
    }

    void fireUnitDeath(String deadId, String cid) {
        runtime.execute(
            "var ev = engine.newEvent('combat.unit_death'); ev.set('deadId','" + deadId + "'); ev.set('combatId','" + cid + "'); " +
            "engine.fire('combat.unit_death', ev);",
            "death.js");
    }

    @Test
    void enemyDeath_grantsPlayerXp_default() throws Exception {
        makeCombat("c1", 1, 1, null);   // Lv.1 怪 → reward 14
        fireUnitDeath("en", "c1");
        assertThat(store.get("pc").getComponent("Experience").getInt("xp")).isEqualTo(14);
    }

    @Test
    void enemyDeath_xpRewardOverride_wins() throws Exception {
        makeCombat("c2", 1, 1, 50);     // 显式 xpReward=50 覆盖默认曲线
        fireUnitDeath("en", "c2");
        assertThat(store.get("pc").getComponent("Experience").getInt("xp")).isEqualTo(50);
    }
}
