package com.epic.engine.combat;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class DerivedStatsTest {
    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;
    ModifierTypeRegistry typeReg;
    ModifierChainService chainService;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    Entity warrior() {
        Entity e = new Entity("w1");
        Component p = new Component("PrimaryStats");
        p.set("力量", 14); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 12); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 3); c.set("speed", 0); e.addComponent(c);
        store.add(e);
        return e;
    }

    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    @Test
    void derives_warrior_stats() {
        warrior();
        js("engine.setBase('w1');");             // snapshot base
        js("registerDerivedModifier('w1');");    // registers + recalculates
        Entity e = store.get("w1");
        Component d = e.getComponent("DerivedStats");
        assertThat(d.getInt("物理强度")).isEqualTo(28);   // 力量14 × 2
        assertThat(d.getInt("法术强度")).isEqualTo(3);    // 智力3
        assertThat(d.getInt("精神强度")).isEqualTo(3);    // 意志3
        assertThat(e.getComponent("Health").getInt("maxHp")).isEqualTo(150); // 30 + 体质12×10
        assertThat(e.getComponent("CombatStats").getInt("speed")).isEqualTo(5); // 敏捷
        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(7); // ⌈5×1.28⌉
    }

    @Test
    void derived_reads_post_class_primary_value() {   // 衍生自衍生顺序
        warrior();
        js("engine.setBase('w1');");
        // 一个 priority 180(class 段)的 modifier 给力量 +6,模拟职业/等级先跑
        js("engine.addModifier('w1', { typeId:'class', id:'fake_class', priority:180," +
           " apply:function(ent){ var p=ent.getComponent('PrimaryStats');" +
           " p.set('力量', p.getInt('力量')+6); } });");
        js("registerDerivedModifier('w1');");    // derived priority 300 → 必须最后跑
        assertThat(store.get("w1").getComponent("DerivedStats").getInt("物理强度"))
            .isEqualTo(40);   // (14+6)×2 — 证明 derived 读到累加后的力量
    }
}
