package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 复现 #3:装备武器后 CombatStats.attack 不变(用户报告普攻伤害不随武器变)。 */
class EquipWeaponBugTest {
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
        rt.bindService("persistence", new StubPersistence());
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/equipment/equip.js")), "equip.js");
        // load items.yaml via world.init
        js("var ev = engine.newEvent('world.init'); engine.fire('world.init', ev);");
    }
    @AfterEach void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    Entity mage(String id) {  // 力量高、智力低:双手剑(读力量30×2)应远高于法杖(读智力4×1)
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量", 30); p.set("敏捷", 6); p.set("智力", 4);
        p.set("体质", 8); p.set("意志", 3); p.set("weaponAttr", "智力");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 0); c.set("speed", 0); e.addComponent(c);
        Component slots = new Component("EquipmentSlots"); slots.set("weapon", null); slots.set("armor", null); slots.set("accessory", null);
        e.addComponent(slots);
        Component inv = new Component("Inventory");
        java.util.List<Object> items = new java.util.ArrayList<>();
        items.add("greatsword"); items.add("staff");
        inv.set("items", items);
        e.addComponent(inv);
        store.add(e);
        js("engine.setBase('"+id+"');");
        js("registerDerivedModifier('"+id+"');");
        return e;
    }

    @Test
    void equippedWeaponAttribute_drivesAttack() {
        mage("m");

        // 装备双手剑(绑定力量=30,×2) → attack = ⌈5×(1+30×2/100)⌉ = ⌈8⌉ = 8
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','m'); ev.set('itemId','greatsword'); engine.fire('action.equip', ev);");
        int attackGreatsword = store.get("m").getComponent("CombatStats").getInt("attack");
        Object slotAfterGreatsword = store.get("m").getComponent("EquipmentSlots").get("weapon");

        // 换法杖(绑定智力=4,×1) → attack = ⌈5×(1+4/100)⌉ = ⌈5.2⌉ = 6
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','m'); ev.set('itemId','staff'); engine.fire('action.equip', ev);");
        int attackStaff = store.get("m").getComponent("CombatStats").getInt("attack");
        Object slotAfterStaff = store.get("m").getComponent("EquipmentSlots").get("weapon");

        System.out.println("[EQUIP] greatsword attack="+attackGreatsword+" slot="+slotAfterGreatsword
            + " | staff attack="+attackStaff+" slot="+slotAfterStaff);

        // 装备槽必须在 recalculate 后存活(回归:EquipmentSlots 被 restoreBaseState 覆盖回 null 的 bug)
        assertThat(String.valueOf(slotAfterGreatsword)).isEqualTo("greatsword");
        assertThat(String.valueOf(slotAfterStaff)).isEqualTo("staff");
        // 武器绑定属性必须真正驱动伤害:双手剑(力量30×2)应明显高于法杖(智力4×1)
        assertThat(attackGreatsword).as("greatsword(力量30) attack should exceed staff(智力4) attack").isGreaterThan(attackStaff);
    }

    /** 复现 #2:反复换装不应让属性(防御)累加增长。 */
    @Test
    void swappingArmorRepeatedly_doesNotInflateDefense() {
        mage("m");
        int defBase = store.get("m").getComponent("CombatStats").getInt("defense");

        // leather_armor: CombatStats.defense +5
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','m'); ev.set('itemId','leather_armor'); engine.fire('action.equip', ev);");
        int defLeather1 = store.get("m").getComponent("CombatStats").getInt("defense");

        // chain_mail: CombatStats.defense +12
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','m'); ev.set('itemId','chain_mail'); engine.fire('action.equip', ev);");
        int defChain = store.get("m").getComponent("CombatStats").getInt("defense");

        // 换回 leather_armor —— 应回到与第一次相同的值,而非继续累加
        js("var ev=engine.newEvent('action.equip'); ev.set('playerId','m'); ev.set('itemId','leather_armor'); engine.fire('action.equip', ev);");
        int defLeather2 = store.get("m").getComponent("CombatStats").getInt("defense");

        System.out.println("[ARMOR] base="+defBase+" leather1="+defLeather1+" chain="+defChain+" leather2="+defLeather2);

        assertThat(defLeather1).as("leather +5 over base").isEqualTo(defBase + 5);
        assertThat(defChain).as("chain +12 over base (not stacked on leather)").isEqualTo(defBase + 12);
        assertThat(defLeather2).as("re-equipping leather returns to base+5, no inflation").isEqualTo(defBase + 5);
    }
}
