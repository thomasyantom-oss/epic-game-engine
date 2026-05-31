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
}
