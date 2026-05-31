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

    // 一件饰品用全限定字段 PrimaryStats.力量+3 与 CombatStats.defense+5
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
        // 无点号元数据 key(weaponAttr)不被 modifier 误当字段处理,仍是原 String 值
        assertThat(r.getComponent("PrimaryStats").getString("weaponAttr")).isEqualTo("力量");
    }

    // 主属性=智力(智力低=4)拿双手剑(weaponAttr=力量=20,B=5)→ attack 按武器力量算,与主属性无关。
    // 选值让「占位(读主属性智力)」≠「新公式(读武器力量)」,确保实现前测试真失败。
    @Test
    void weapon_final_damage_uses_bound_attr_not_main() {
        Entity e = new Entity("m1");
        Component p = new Component("PrimaryStats");
        p.set("力量", 20); p.set("敏捷", 6); p.set("智力", 4);
        p.set("体质", 0); p.set("意志", 0); p.set("weaponAttr", "智力");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        Component slots = new Component("EquipmentSlots");
        slots.set("weapon", "greatsword1"); slots.set("armor", null); slots.set("accessory", null);
        e.addComponent(slots);
        store.add(e);

        js("var w = engine.createEntity('greatsword1'); w.addTag('item');" +
           " var m = engine.newComponent('ItemMeta'); m.set('name','双手剑'); m.set('type','weapon'); m.set('rarity','common'); w.addComponent(m);" +
           " var s = engine.newComponent('ItemStats'); s.set('weaponAttr','力量'); s.set('base',5); w.addComponent(s);" +
           " store.add(w);");
        js("engine.setBase('m1');");
        js("registerDerivedModifier('m1');");

        // 新公式: ⌈5 × (1 + 20×2/100)⌉ = ⌈5×1.4⌉ = 7  (实现前占位读智力4: ⌈5×1.04⌉=6 → 真失败)
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

    // 体质被削 → maxHp 下降时 hp clamp 到新上限(回升不补 hp 由「clamp 只降」自然保证)
    @Test
    void hp_clamps_when_maxhp_drops() {
        Entity e = new Entity("k1");
        Component p = new Component("PrimaryStats");
        p.set("力量", 0); p.set("敏捷", 0); p.set("智力", 0);
        p.set("体质", 10); p.set("意志", 0); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 130); h.set("maxHp", 130); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        store.add(e);
        js("engine.setBase('k1');");
        js("registerDerivedModifier('k1');");   // 体质10 → maxHp = 30+100 = 130, hp 130 不变
        assertThat(store.get("k1").getComponent("Health").getInt("hp")).isEqualTo(130);
        // 模拟体质被削到 5:加一个 priority 10 的 modifier 削体质 -5,再触发 recalc
        js("engine.addModifier('k1', { typeId:'buff', id:'fake_curse', priority:10," +
           " apply:function(ent){ var pp=ent.getComponent('PrimaryStats'); pp.set('体质', pp.getInt('体质')-5); } });");
        // maxHp = 30+50 = 80 → hp 从 130 clamp 到 80
        assertThat(store.get("k1").getComponent("Health").getInt("maxHp")).isEqualTo(80);
        assertThat(store.get("k1").getComponent("Health").getInt("hp")).isEqualTo(80);
    }
}
