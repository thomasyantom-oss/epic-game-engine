package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端复现用户实测:进入战斗→战斗结束→换装,检查
 *   (a) 敌人生成血量不应卡在占位 1(派生 maxHp 必须生效)
 *   (b) 战吼 buff 不应泄漏到战斗结束后
 *   (c) 战斗结束后换装,职业属性(力量/体质/敏捷)与防御不应累加膨胀
 */
class CombatLifecycleInflationTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;
    ModifierTypeRegistry typeReg; ModifierChainService chainService;

    public static class StubPersistence { @HostAccess.Export public void save(Object e) {} }

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus(); store = new EntityStore();
        typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        SchemaRegistry schemas = new SchemaRegistry();
        schemas.loadFromModPath(Path.of("../mods/base-rules"));
        rt.bindService("schemas", schemas);
        rt.bindService("buffs", new BuffService(bus, store));
        rt.bindService("persistence", new StubPersistence());

        Path combat = Path.of("../mods/base-rules/handlers/combat");
        for (String f : new String[]{"initiative.js","death_check.js",
                "combat_flow.js","combat_events.js","combat_log.js","start_combat.js"}) {
            rt.execute(Files.readString(combat.resolve(f)), f);
        }
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        for (String f : new String[]{"00_skill_lib.js","01_effects.js","02_dispatch.js"}) {
            rt.execute(Files.readString(skill.resolve(f)), f);
        }
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/leveling.js")), "leveling.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/equipment/equip.js")), "equip.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/buffs/war_cry.js")), "war_cry.js");
        js("var ev=engine.newEvent('world.init'); engine.fire('world.init', ev);");
    }
    @AfterEach void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }
    Entity p() { return store.get("hero"); }
    int prim(String k) { return p().getComponent("PrimaryStats").getInt(k); }

    /** 模拟持久化恢复的战士(schema 默认值,entity.loaded 复位+职业 modifier 重建)。 */
    void loadWarrior() {
        Entity e = new Entity("hero");
        Component pr = new Component("PrimaryStats");
        pr.set("力量", 3); pr.set("敏捷", 3); pr.set("智力", 3); pr.set("体质", 3); pr.set("意志", 3); pr.set("weaponAttr", "力量");
        e.addComponent(pr);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 30); h.set("maxHp", 30); e.addComponent(h);
        Component mn = new Component("Mana"); mn.set("mp", 50); mn.set("maxMp", 50); e.addComponent(mn);
        Component c = new Component("CombatStats"); c.set("attack", 10); c.set("defense", 3); c.set("speed", 3); e.addComponent(c);
        Component pos = new Component("Position"); pos.set("map", "world_map"); pos.set("x", 4); pos.set("y", 3); e.addComponent(pos);
        Component ch = new Component("Character"); ch.set("name", "wr"); ch.set("classId", "warrior"); ch.set("level", 1); e.addComponent(ch);
        Component exp = new Component("Experience"); exp.set("xp", 0); exp.set("level", 1); exp.set("pendingPoints", 0); e.addComponent(exp);
        Component slots = new Component("EquipmentSlots"); slots.set("weapon", null); slots.set("armor", null); slots.set("accessory", null); e.addComponent(slots);
        Component inv = new Component("Inventory");
        List<Object> items = new ArrayList<>(); items.add("greatsword"); items.add("leather_armor"); items.add("chain_mail"); inv.set("items", items);
        e.addComponent(inv);
        e.addTag("player"); e.addTag("persistent");
        store.add(e);
        js("var ev=engine.newEvent('entity.loaded'); ev.set('entity', store.get('hero')); engine.fire('entity.loaded', ev);");
    }

    void startEncounter(String encounterId) {
        js("var ev=engine.newEvent('combat.start_encounter'); ev.set('playerId','hero'); ev.set('encounterId','"+encounterId+"'); engine.fire('combat.start_encounter', ev);");
    }
    void command(String cmd) {
        js("var ev=engine.newEvent('action.combat_command'); ev.set('playerId','hero'); ev.set('command','"+cmd+"'); engine.fire('action.combat_command', ev);");
    }

    @Test
    void enterCombat_thenEndCombat_thenEquip_doesNotInflate() {
        loadWarrior();
        int str0 = prim("力量"), con0 = prim("体质"), dex0 = prim("敏捷");
        // 战士:力量 3+11=14,体质 3+9=12,敏捷 3+2=5
        assertThat(str0).isEqualTo(14);
        assertThat(con0).isEqualTo(12);
        assertThat(dex0).isEqualTo(5);

        startEncounter("forest_goblin");
        // (a) 敌人 maxHp 必须由派生算出(>1),而不是卡在占位 1
        List<Entity> goblins = new ArrayList<>();
        for (Entity en : store.all()) if (en.hasTag("enemy")) goblins.add(en);
        assertThat(goblins).isNotEmpty();
        for (Entity g : goblins) {
            int mhp = g.getComponent("Health").getInt("maxHp");
            System.out.println("[ENEMY] "+g.getId()+" maxHp="+mhp+" hp="+g.getComponent("Health").getInt("hp"));
            assertThat(mhp).as("enemy maxHp must be derived, not placeholder 1").isGreaterThan(1);
        }

        // 战斗中施放战吼 —— 不应让单位退出战斗(回归:recalc 不再抹掉 combat 标签)
        command("war_cry");
        boolean inCombatAfterWarCry = false;
        for (Object t : p().getTags()) if (t.toString().startsWith("combat:")) inCombatAfterWarCry = true;
        System.out.println("[WARCRY] inCombat="+inCombatAfterWarCry+" buff="+p().hasComponent("Buff_war_cry")+" 力量="+prim("力量"));
        assertThat(inCombatAfterWarCry).as("casting war_cry must NOT remove the combat tag (bug #1)").isTrue();

        // 逃跑结束战斗 → endCombat 应剥离非永久 buff
        command("flee");
        boolean stillInCombat = false;
        for (Object t : p().getTags()) if (t.toString().startsWith("combat:")) stillInCombat = true;
        boolean buffAfterCombat = p().hasComponent("Buff_war_cry");
        System.out.println("[POST] inCombat="+stillInCombat+" warCryBuffLeft="+buffAfterCombat+" 力量="+prim("力量"));

        // (b) 战吼 buff 不应泄漏
        assertThat(stillInCombat).as("flee should exit combat").isFalse();
        assertThat(buffAfterCombat).as("war_cry buff must be stripped at combat end").isFalse();
        // 力量回到 14(buff 撤销)
        assertThat(prim("力量")).as("力量 back to base after combat").isEqualTo(14);

        // (c) 战斗结束后换装,反复几次,属性不得累加
        for (int i = 0; i < 3; i++) {
            js("var ev=engine.newEvent('action.equip'); ev.set('playerId','hero'); ev.set('itemId','leather_armor'); engine.fire('action.equip', ev);");
            js("var ev=engine.newEvent('action.equip'); ev.set('playerId','hero'); ev.set('itemId','chain_mail'); engine.fire('action.equip', ev);");
        }
        int strEnd = prim("力量"), conEnd = prim("体质"), dexEnd = prim("敏捷");
        int defEnd = p().getComponent("CombatStats").getInt("defense");
        System.out.println("[EQUIP-AFTER-COMBAT] 力量="+strEnd+" 体质="+conEnd+" 敏捷="+dexEnd+" 防御="+defEnd);

        assertThat(strEnd).as("力量 must not inflate after combat+equip").isEqualTo(14);
        assertThat(conEnd).as("体质 must not inflate after combat+equip").isEqualTo(12);
        assertThat(dexEnd).as("敏捷 must not inflate after combat+equip").isEqualTo(5);
        // chain_mail 最终装备:defense = schema 3 + 12 = 15
        assertThat(defEnd).as("defense = base 3 + chain_mail 12, no stacking").isEqualTo(15);
    }

    /** 复现用户实测:战吼数次→被打死(DEFEAT)→之后换装一直涨主属性。 */
    @Test
    void warCryThenDie_thenEquip_doesNotInflate() {
        loadWarrior();
        int str0 = prim("力量"), con0 = prim("体质"), dex0 = prim("敏捷");
        assertThat(str0).isEqualTo(14);

        // 先装把武器,并打几场(进-战吼-逃)模拟多场战斗后再死
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','hero'); ev.set('itemId','greatsword'); engine.fire('action.equip', ev);");
        for (int cyc = 0; cyc < 2; cyc++) {
            startEncounter("forest_goblin");
            command("war_cry");
            command("flee");
            System.out.println("[CYCLE"+cyc+"] 力量="+prim("力量")+" 体质="+prim("体质")+" 敏捷="+prim("敏捷"));
        }
        assertThat(prim("力量")).as("力量 stable across combat cycles").isEqualTo(14);

        startEncounter("forest_goblin");

        // 战吼数次(力量逐次 ramp)
        command("war_cry");
        command("war_cry");
        command("war_cry");
        System.out.println("[RAMP] 力量="+prim("力量")+" hp="+p().getComponent("Health").getInt("hp"));

        // 把英雄血压到 1,再走一回合让敌人补刀 → 死亡 → DEFEAT → endCombat
        js("var h=store.get('hero').getComponent('Health'); h.set('hp', 1);");
        command("war_cry");   // 英雄行动(无伤),敌人攻击补刀致死

        boolean inCombat = false;
        for (Object t : p().getTags()) if (t.toString().startsWith("combat:")) inCombat = true;
        int strAfterDeath = prim("力量"), conAfterDeath = prim("体质"), dexAfterDeath = prim("敏捷");
        System.out.println("[DEATH] inCombat="+inCombat+" 力量="+strAfterDeath+" 体质="+conAfterDeath
            +" 敏捷="+dexAfterDeath+" buff="+p().hasComponent("Buff_war_cry")
            +" hp="+p().getComponent("Health").getInt("hp"));

        // 死亡/战败后换装数次
        for (int i = 0; i < 3; i++) {
            js("var ev=engine.newEvent('action.equip'); ev.set('playerId','hero'); ev.set('itemId','leather_armor'); engine.fire('action.equip', ev);");
            js("var ev=engine.newEvent('action.equip'); ev.set('playerId','hero'); ev.set('itemId','chain_mail'); engine.fire('action.equip', ev);");
        }
        int strEnd = prim("力量"), conEnd = prim("体质"), dexEnd = prim("敏捷");
        System.out.println("[DEATH-EQUIP] 力量="+strEnd+" 体质="+conEnd+" 敏捷="+dexEnd
            +" 防御="+p().getComponent("CombatStats").getInt("defense"));

        // 战败后主属性必须回到职业基础值,且换装不再累加
        assertThat(strAfterDeath).as("力量 back to base after defeat").isEqualTo(14);
        assertThat(strEnd).as("力量 must not inflate after defeat+equip").isEqualTo(14);
        assertThat(conEnd).as("体质 must not inflate").isEqualTo(12);
        assertThat(dexEnd).as("敏捷 must not inflate").isEqualTo(5);
    }
}
