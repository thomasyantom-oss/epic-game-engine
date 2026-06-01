package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 复现 #2:存档→重载循环导致属性膨胀。
 *
 * 持久化保存的是「class/equipment 等 modifier 应用后」的实时属性值。重载时 entity.loaded
 * 原先用 setBaseSelective 把这些(已膨胀)值当成 base 快照,随后 class(+11 力量)/equipment(+5 防御)
 * 等累加型 modifier 又叠一遍 → 每次重启属性翻倍累加。
 *
 * 修复:entity.loaded 先把可重建的属性组件复位成 schema 干净默认值,再快照。
 */
class ReloadInflationBugTest {
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
        rt.bindService("persistence", new StubPersistence());
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/leveling.js")), "leveling.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/equipment/equip.js")), "equip.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
        js("var ev = engine.newEvent('world.init'); engine.fire('world.init', ev);");
    }
    @AfterEach void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    /** 模拟从持久化恢复的战士(已装皮甲):组件值是 modifier 应用后的"脏"值。 */
    Entity persistedWarrior(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        // 脏值:力量 = schema 3 + 职业 +11 = 14(持久化存的就是这个最终值)
        p.set("力量", 14); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 12); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 150); h.set("maxHp", 150); e.addComponent(h);
        Component m = new Component("Mana"); m.set("mp", 50); m.set("maxMp", 50); e.addComponent(m);
        // 脏值:defense = schema 3 + 皮甲 +5 = 8
        Component c = new Component("CombatStats"); c.set("attack", 6); c.set("defense", 8); c.set("speed", 5); e.addComponent(c);
        Component pos = new Component("Position"); pos.set("map", "world_map"); pos.set("x", 7); pos.set("y", 2); e.addComponent(pos);
        Component ch = new Component("Character"); ch.set("name", "勇者"); ch.set("classId", "warrior"); ch.set("level", 1); e.addComponent(ch);
        Component exp = new Component("Experience"); exp.set("xp", 0); exp.set("level", 1); exp.set("pendingPoints", 0); e.addComponent(exp);
        Component slots = new Component("EquipmentSlots"); slots.set("weapon", null); slots.set("armor", "leather_armor"); slots.set("accessory", null);
        e.addComponent(slots);
        Component inv = new Component("Inventory"); inv.set("items", new ArrayList<>()); e.addComponent(inv);
        store.add(e);
        return e;
    }

    void load(String id) {
        js("var ev=engine.newEvent('entity.loaded'); ev.set('entity', store.get('"+id+"')); engine.fire('entity.loaded', ev);");
    }

    @Test
    void repeatedReload_doesNotInflateStats() {
        persistedWarrior("w");

        load("w");
        int str1 = store.get("w").getComponent("PrimaryStats").getInt("力量");
        int def1 = store.get("w").getComponent("CombatStats").getInt("defense");

        load("w");   // 第二次重启
        int str2 = store.get("w").getComponent("PrimaryStats").getInt("力量");
        int def2 = store.get("w").getComponent("CombatStats").getInt("defense");

        load("w");   // 第三次重启
        int str3 = store.get("w").getComponent("PrimaryStats").getInt("力量");
        int def3 = store.get("w").getComponent("CombatStats").getInt("defense");

        Object x = store.get("w").getComponent("Position").get("x");
        System.out.println("[RELOAD] 力量="+str1+"/"+str2+"/"+str3+" defense="+def1+"/"+def2+"/"+def3+" posX="+x);

        // 战士:力量 = schema 3 + 职业 +11 = 14;defense = schema 3 + 皮甲 +5 = 8。多次重载恒定。
        assertThat(str1).as("力量 after 1st load").isEqualTo(14);
        assertThat(str2).as("力量 stable on reload").isEqualTo(14);
        assertThat(str3).as("力量 stable on repeated reload").isEqualTo(14);
        assertThat(def1).as("defense after 1st load").isEqualTo(8);
        assertThat(def2).as("defense stable on reload").isEqualTo(8);
        assertThat(def3).as("defense stable on repeated reload").isEqualTo(8);
        // Position 是真实空间状态:重载不得被复位成出生点(4,3)
        assertThat(String.valueOf(x)).as("position preserved across reload").isEqualTo("7");
    }
}
