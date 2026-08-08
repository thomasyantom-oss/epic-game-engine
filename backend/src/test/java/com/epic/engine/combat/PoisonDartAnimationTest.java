package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 毒镖必须发出技能动画(飞射 + 伤害数字),而不是无动画无数字。 */
class PoisonDartAnimationTest {
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
    }
    @AfterEach void tearDown() { rt.close(); }

    @Test
    void poisonDart_emitsProjectileAndDamageNumber() {
        Entity combat = new Entity("b1");
        Component st = new Component("CombatState"); st.set("round",1); st.set("phase","RESOLVE"); combat.addComponent(st);
        Component ce = new Component("CombatEvents"); ce.set("queue", new ArrayList<>()); combat.addComponent(ce);
        Component cl = new Component("CombatLog"); cl.set("entries", new ArrayList<>()); combat.addComponent(cl);
        combat.addTag("active_combat"); store.add(combat);

        Entity hero = new Entity("hero");
        Component p = new Component("PrimaryStats"); p.set("敏捷",10); p.set("力量",3); p.set("智力",3); p.set("体质",3); p.set("意志",3); hero.addComponent(p);
        Component c = new Component("CombatStats"); c.set("attack",12); c.set("defense",0); c.set("speed",10); hero.addComponent(c);
        Component h = new Component("Health"); h.set("hp",30); h.set("maxHp",30); hero.addComponent(h);
        Component pos = new Component("CombatPosition"); pos.set("row","FRONT"); pos.set("slot",0); hero.addComponent(pos);
        Component n = new Component("Name"); n.set("value","盗贼"); hero.addComponent(n);
        hero.addTag("player"); hero.addTag("combat:b1"); store.add(hero);

        Entity enemy = new Entity("gob");
        Component ec = new Component("CombatStats"); ec.set("attack",1); ec.set("defense",0); ec.set("speed",1); enemy.addComponent(ec);
        Component eh = new Component("Health"); eh.set("hp",40); eh.set("maxHp",40); enemy.addComponent(eh);
        Component epos = new Component("CombatPosition"); epos.set("row","FRONT"); epos.set("slot",0); enemy.addComponent(epos);
        Component en = new Component("Name"); en.set("value","哥布林"); enemy.addComponent(en);
        enemy.addTag("enemy"); enemy.addTag("combat:b1"); store.add(enemy);

        GameEvent ev = new GameEvent("combat.unit_action");
        ev.set("combatId","b1"); ev.set("actorId","hero");
        Map<String,Object> cmd = new HashMap<>(); cmd.put("type","poison_dart"); cmd.put("targetId","gob");
        ev.set("command", cmd);
        bus.fire("combat.unit_action", ev);

        List<?> queue = (List<?>) store.get("b1").getComponent("CombatEvents").get("queue");
        boolean hasProjectile = false, hasDamageNumber = false; Object dmgVal = null;
        for (Object item : queue) {
            @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) item;
            Object anim = m.get("animation");
            if (anim instanceof List<?> al) for (Object a : al) {
                Map<?,?> am = (Map<?,?>) a;
                String t = String.valueOf(am.get("type"));
                if ("projectile".equals(t)) hasProjectile = true;
                if ("damage_number".equals(t)) { hasDamageNumber = true; dmgVal = am.get("value"); }
            }
        }
        System.out.println("[POISON] queueSize="+queue.size()+" projectile="+hasProjectile+" dmgNum="+hasDamageNumber+" dmgVal="+dmgVal);

        assertThat(hasProjectile).as("poison_dart must emit a projectile animation").isTrue();
        assertThat(hasDamageNumber).as("poison_dart must emit a damage_number").isTrue();
        assertThat(dmgVal).as("damage_number value auto-filled (negative)").isNotNull();
        // 敏捷10 → 伤害 = attack12 + ⌈10×0.1⌉ = 13;显示为 -13
        assertThat(((Number) dmgVal).intValue()).isEqualTo(-13);
    }
}
