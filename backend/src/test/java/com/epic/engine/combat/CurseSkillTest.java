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
}
