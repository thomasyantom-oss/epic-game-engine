package com.epic.engine.core;

import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归:移动同步 Position 进 base 时,绝不能用 setBaseSelective(它清空整个 base 只留 Position),
 * 否则 PrimaryStats 等被踢出 base → restoreBaseState 不再复位 → 累加型 modifier 每次 recalc 无限叠加。
 * 必须用 updateBase(只更新 Position 那一项,保留其余 base)。
 */
class BaseSnapshotIntegrityTest {
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
    }
    @AfterEach void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    Entity warriorish(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats"); p.set("力量", 3); e.addComponent(p);
        Component pos = new Component("Position"); pos.set("map", "world_map"); pos.set("x", 4); pos.set("y", 3); e.addComponent(pos);
        store.add(e);
        js("engine.setBase('"+id+"');");
        // 累加型职业 modifier:力量 +11(模拟 class_warrior)
        js("engine.addModifier('"+id+"', { typeId:'class', id:'class_warrior', label:'战士',"
            + " apply: function(ent){ var p=ent.getComponent('PrimaryStats'); p.set('力量', p.getInt('力量')+11); } });");
        return e;
    }
    int str(String id) { return store.get(id).getComponent("PrimaryStats").getInt("力量"); }

    @Test
    void updateBasePosition_preservesOtherBaseComponents_noInflation() {
        warriorish("w");
        assertThat(str("w")).as("base 3 + class 11").isEqualTo(14);

        // 移动(修复版):只更新 Position 进 base,再多次 recalc,力量必须恒定 14
        for (int i = 0; i < 5; i++) {
            js("store.get('w').getComponent('Position').set('x', " + (4 + i) + ");");
            js("engine.updateBase('w', 'Position');");
            js("engine.recalculate('w');");
        }
        assertThat(str("w")).as("力量 stays 14 across moves+recalcs (no inflation)").isEqualTo(14);
        assertThat(store.get("w").getComponent("Position").getInt("x")).as("position synced").isEqualTo(8);
    }

    @Test
    void setBaseSelectivePosition_wipesBase_causesInflation_documentsBug() {
        warriorish("w");
        assertThat(str("w")).isEqualTo(14);

        // 旧错误用法:setBaseSelective 清空 base 只留 Position → PrimaryStats 不再被复位
        js("var l=engine.newList(); l.add('Position'); engine.setBaseSelective('w', l);");
        js("engine.recalculate('w');");
        // 证伪:力量从 14 涨到 25(每次 recalc +11),这正是必须改用 updateBase 的原因
        assertThat(str("w")).as("setBaseSelective wipes PrimaryStats from base → class re-adds → inflation")
            .isEqualTo(25);
    }
}
