package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 贯穿射线:技能动画(beam)必须排在击杀死亡事件之前入队,否则死亡结算顺序错乱。 */
class PiercingRayOrderingTest {
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
        for (String f : new String[]{"initiative.js","damage_calc.js","death_check.js",
                "combat_flow.js","combat_events.js","combat_log.js","start_combat.js"}) {
            rt.execute(Files.readString(combat.resolve(f)), f);
        }
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        for (String f : new String[]{"00_skill_lib.js","01_effects.js","02_dispatch.js"}) {
            rt.execute(Files.readString(skill.resolve(f)), f);
        }
        rt.execute(Files.readString(Path.of("../mods/base-rules/skills/piercing_ray.js")), "piercing_ray.js");
    }
    @AfterEach void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    void mkCaster(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats"); p.set("意志", 10); p.set("力量",3); p.set("敏捷",3); p.set("智力",3); p.set("体质",3); e.addComponent(p);
        Component c = new Component("CombatStats"); c.set("attack", 20); c.set("defense", 0); c.set("speed", 5); e.addComponent(c);
        Component h = new Component("Health"); h.set("hp", 50); h.set("maxHp", 50); e.addComponent(h);
        Component pos = new Component("CombatPosition"); pos.set("row","BACK"); pos.set("slot",0); e.addComponent(pos);
        Component n = new Component("Name"); n.set("value","法师"); e.addComponent(n);
        e.addTag("player"); e.addTag("combat:b1"); store.add(e);
    }
    void mkEnemy(String id, String row) {
        Entity e = new Entity(id);
        Component c = new Component("CombatStats"); c.set("attack",1); c.set("defense",0); c.set("speed",1); e.addComponent(c);
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);  // 1血,必被秒
        Component pos = new Component("CombatPosition"); pos.set("row",row); pos.set("slot",0); e.addComponent(pos);
        Component n = new Component("Name"); n.set("value","靶子"); e.addComponent(n);
        e.addTag("enemy"); e.addTag("combat:b1"); store.add(e);
    }

    @Test
    void beamAnimation_isQueuedBeforeDeathEvents() {
        Entity combat = new Entity("b1");
        Component st = new Component("CombatState"); st.set("round",1); st.set("phase","RESOLVE"); combat.addComponent(st);
        Component ce = new Component("CombatEvents"); ce.set("queue", new ArrayList<>()); combat.addComponent(ce);
        Component cl = new Component("CombatLog"); cl.set("entries", new ArrayList<>()); combat.addComponent(cl);
        combat.addTag("active_combat"); store.add(combat);

        mkCaster("mage");
        mkEnemy("e_front", "FRONT");
        mkEnemy("e_mid", "MID");

        // 触发贯穿射线(同一列 slot0 全列)
        GameEvent ev = new GameEvent("combat.unit_action");
        ev.set("combatId", "b1"); ev.set("actorId", "mage");
        Map<String,Object> cmd = new HashMap<>(); cmd.put("type","piercing_ray"); cmd.put("targetId","e_front");
        ev.set("command", cmd);
        bus.fire("combat.unit_action", ev);

        List<?> queue = (List<?>) store.get("b1").getComponent("CombatEvents").get("queue");
        int beamIdx = -1, firstDeathIdx = -1;
        for (int i = 0; i < queue.size(); i++) {
            Object item = queue.get(i);
            @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) item;
            boolean hasBeam = false, hasDeath = false;
            Object anim = m.get("animation");
            if (anim instanceof List<?> al) for (Object a : al) {
                Object t = ((Map<?,?>) a).get("type");
                if ("beam".equals(String.valueOf(t))) hasBeam = true;
            }
            Object eff = m.get("effects");
            if (eff instanceof List<?> el) for (Object a : el) {
                Object t = ((Map<?,?>) a).get("type");
                if ("death".equals(String.valueOf(t))) hasDeath = true;
            }
            if (hasBeam && beamIdx < 0) beamIdx = i;
            if (hasDeath && firstDeathIdx < 0) firstDeathIdx = i;
        }
        System.out.println("[PIERCE] queueSize="+queue.size()+" beamIdx="+beamIdx+" firstDeathIdx="+firstDeathIdx);

        assertThat(beamIdx).as("beam event present").isGreaterThanOrEqualTo(0);
        assertThat(firstDeathIdx).as("death event present (enemies had 1 hp)").isGreaterThanOrEqualTo(0);
        assertThat(beamIdx).as("beam animation must be queued BEFORE death events").isLessThan(firstDeathIdx);
    }
}
