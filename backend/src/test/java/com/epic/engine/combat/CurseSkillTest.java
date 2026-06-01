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
        // recalculate_hooks.js: before/after_recalculate 用 scratch 保住当前 hp(=min(旧hp,maxHp))。
        // 加载它使测试与生产一致 —— 诅咒降体质后 hp clamp、消除后 maxHp 回升不补 hp 均由它收口。
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
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
        js("buffs.applyBuff('w1', 'war_cry', engine.newMap());");
        int afterApply = store.get("w1").getComponent("PrimaryStats").getInt("力量");
        assertThat(afterApply).isGreaterThan(10);   // 力量被战吼抬高
        js("buffs.removeBuff('w1', 'war_cry');");
        assertThat(store.get("w1").getComponent("PrimaryStats").getInt("力量")).isEqualTo(10); // 撤回 base
    }

    // 物理强度固定为 20(写进 base 快照,不注册 derived → 不会因 力量 抬升而自我膨胀),
    // 隔离测「重复施放不叠加」:bonus=⌈0.5×20⌉=10,两次施放仍只 +10(=20),而非 +20(=30)。
    Entity warriorStablePhys(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量", 10); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 10); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        Component d = new Component("DerivedStats"); d.set("物理强度", 20); e.addComponent(d);
        Component h = new Component("Health"); h.set("hp", 130); h.set("maxHp", 130); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        store.add(e);
        js("engine.setBase('" + id + "');");   // 物理强度=20 进 base，不注册 derived → recalc 后仍 20
        return e;
    }

    @Test
    void war_cry_recast_does_not_double_stack() {
        warriorStablePhys("w2");
        js("buffs.applyBuff('w2', 'war_cry', engine.newMap());");   // +10 → 20
        assertThat(store.get("w2").getComponent("PrimaryStats").getInt("力量")).isEqualTo(20);
        js("buffs.applyBuff('w2', 'war_cry', engine.newMap());");   // refresh 再触发 buff.applied:幂等,仍 +10
        assertThat(store.get("w2").getComponent("PrimaryStats").getInt("力量"))
            .as("重复施放不叠加(单一 modifier 替换,而非两个 +10)").isEqualTo(20);
        js("buffs.removeBuff('w2', 'war_cry');");
        assertThat(store.get("w2").getComponent("PrimaryStats").getInt("力量")).isEqualTo(10);
    }

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

    @Test
    void curse_recast_does_not_double_reduce() {
        warrior("c3");
        js("var d = engine.newMap(); d.put('remaining', 3); d.put('stacking','refresh'); buffs.applyBuff('c3', 'cursed', d);");
        assertThat(store.get("c3").getComponent("PrimaryStats").getInt("力量")).isEqualTo(8);  // -2
        js("var d2 = engine.newMap(); d2.put('remaining', 3); d2.put('stacking','refresh'); buffs.applyBuff('c3', 'cursed', d2);");
        // 重复施放仍 -2(幂等),而非 -4
        assertThat(store.get("c3").getComponent("PrimaryStats").getInt("力量"))
            .as("重复施放不叠加").isEqualTo(8);
    }
}
