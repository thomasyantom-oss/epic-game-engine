package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import com.epic.engine.core.Entity;

class SkillLibTest {
    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        rt.bindService("buffs", new BuffService(bus, store));
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/combat/damage_calc.js")), "damage_calc.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/00_skill_lib.js")), "00_skill_lib.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    Entity mkUnit(String id, String name, boolean player) {
        Entity e = new Entity(id);
        Component h = new Component("Health");
        h.set("hp", 100);
        h.set("maxHp", 100);
        e.addComponent(h);
        Component s = new Component("CombatStats");
        s.set("attack", 10);
        s.set("defense", 5);
        s.set("speed", 5);
        e.addComponent(s);
        Component n = new Component("Name");
        n.set("value", name);
        e.addComponent(n);
        e.addTag(player ? "player" : "enemy");
        e.addTag("combat:b1");
        store.add(e);
        return e;
    }

    /** Run a JS snippet that throws a string on assertion failure. */
    void js(String script) {
        rt.execute("(function(){ " + script + " })();", "assert.js");
    }

    @Test
    void context_resolvesCasterFields() {
        mkUnit("mage", "法师", true);
        assertThatCode(() -> js(
            "var ev = engine.newEvent('combat.unit_action');" +
            "ev.set('actorId','mage'); ev.set('combatId','b1');" +
            "var cmd = {}; cmd.type='fireball'; ev.set('command', cmd);" +
            "var c = Skill.context(ev);" +
            "if (c.actorId !== 'mage') throw 'actorId='+c.actorId;" +
            "if (c.combatId !== 'b1') throw 'combatId='+c.combatId;" +
            "if (c.casterName !== '法师') throw 'name='+c.casterName;" +
            "if (c.casterSide !== 'player') throw 'side='+c.casterSide;"
        )).doesNotThrowAnyException();
    }

    @Test
    void loadSpec_loadsAndCaches() {
        assertThatCode(() -> js(
            "var s1 = Skill.loadSpec('fireball');" +
            "if (s1 === null) throw 'fireball spec null';" +
            "if (s1.get('id') !== 'fireball') throw 'id='+s1.get('id');" +
            "var s2 = Skill.loadSpec('fireball');" +
            "if (s1 !== s2) throw 'not cached (different object)';"
        )).doesNotThrowAnyException();
    }

    @Test
    void computeDamage_flatAdd() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});" +
           "if (dmg !== 18) throw 'dmg='+dmg;");   // attack 10 + 8
    }

    @Test
    void computeDamage_isPure_noHpChange() {
        mkUnit("mage","法师",true);
        Entity g = mkUnit("goblin","哥布林",false);
        js("Skill.computeDamage(store.get('mage'), store.get('goblin'), {base:'attack', add:8});");
        // pure: calling computeDamage must NOT mutate target hp
        org.assertj.core.api.Assertions.assertThat(g.getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    @Test
    void computeDamage_viaDamageCalc_subtractsDefense() {
        mkUnit("mage","法师",true);
        mkUnit("goblin","哥布林",false);   // defense 5 in mkUnit
        js("var dmg = Skill.computeDamage(store.get('mage'), store.get('goblin'), {via_damage_calc:true});" +
           "if (dmg !== 5) throw 'dmg='+dmg;");   // 10 attack - 5 defense
    }

    Entity mkPos(String id, boolean player, String row, int slot) {
        Entity e = mkUnit(id, id, player);
        Component p = new Component("CombatPosition"); p.set("row", row); p.set("slot", slot);
        e.addComponent(p); return e;
    }

    @Test
    void resolveTargets_single() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{targetId:'g1'}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'pattern',field:'enemy',pattern:[[0,0]]}});" +
           "if (r.length !== 1) throw 'len='+r.length;" +
           "if (r[0].entity.getId() !== 'g1') throw 'id='+r[0].entity.getId();");
    }

    @Test
    void resolveTargets_column() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0); mkPos("g3", false, "BACK", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{targetId:'g2'}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'pattern',field:'enemy',pattern:[[-1,0],[0,0],[1,0]]}});" +
           "if (r.length !== 3) throw 'len='+r.length;");
    }

    @Test
    void resolveTargets_group_allEnemies() {
        mkUnit("mage","法师",true);
        mkPos("g1", false, "FRONT", 0); mkPos("g2", false, "MID", 0);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'group',field:'enemy'}});" +
           "if (r.length !== 2) throw 'len='+r.length;");
    }

    @Test
    void resolveTargets_self() {
        mkUnit("mage","法师",true);
        js("var ctx = {caster: store.get('mage'), combatId:'b1', cmd:{}};" +
           "var r = Skill.resolveTargets(ctx, {targeting:{mode:'self'}});" +
           "if (r.length !== 1 || r[0].entity.getId() !== 'mage') throw 'self';");
    }
}
