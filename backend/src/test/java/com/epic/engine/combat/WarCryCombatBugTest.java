package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 复现:战斗中施放战吼后战斗异常结束(用户报告 bug #1)。 */
class WarCryCombatBugTest {
    EventBus bus; EntityStore store; ScriptRuntime rt;
    ModifierTypeRegistry typeReg; ModifierChainService chainService;

    public static class StubPersistence {
        @HostAccess.Export public void save(Object e) {}
    }

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
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/buffs/war_cry.js")), "war_cry.js");
        // war_cry skill is buff_only (data-driven) — no bespoke JS needed.
    }

    @AfterEach
    void tearDown() { rt.close(); }
    void js(String s) { rt.execute("(function(){ " + s + " })();", "t.js"); }

    void mkWarrior(String id) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量", 14); p.set("敏捷", 6); p.set("智力", 3);
        p.set("体质", 12); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp", 1); h.set("maxHp", 1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 0); c.set("defense", 3); c.set("speed", 0); e.addComponent(c);
        Component pos = new Component("CombatPosition"); pos.set("row","FRONT"); pos.set("slot",1); e.addComponent(pos);
        Component n = new Component("Name"); n.set("value","勇者"); e.addComponent(n);
        e.addTag("player"); e.addTag("combat:b1");
        store.add(e);
        js("engine.setBase('"+id+"');");
        js("registerDerivedModifier('"+id+"');");
        js("var h=store.get('"+id+"').getComponent('Health'); h.set('hp', h.getInt('maxHp'));"); // full hp
    }

    void mkEnemy(String id, int slot) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量",4); p.set("敏捷",4); p.set("智力",0); p.set("体质",6); p.set("意志",0); p.set("weaponAttr","敏捷");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component h = new Component("Health"); h.set("hp",1); h.set("maxHp",1); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack",0); c.set("defense",1); c.set("speed",0); e.addComponent(c);
        Component pos = new Component("CombatPosition"); pos.set("row","FRONT"); pos.set("slot",slot); e.addComponent(pos);
        Component n = new Component("Name"); n.set("value","哥布林"); e.addComponent(n);
        e.addTag("enemy"); e.addTag("combat:b1");
        store.add(e);
        js("engine.setBase('"+id+"');");
        js("registerDerivedModifier('"+id+"');");
        js("var h=store.get('"+id+"').getComponent('Health'); h.set('hp', h.getInt('maxHp'));");
    }

    void mkCombat() {
        Entity combat = new Entity("b1");
        Component st = new Component("CombatState"); st.set("round",1); st.set("phase","COMMAND"); combat.addComponent(st);
        Component ce = new Component("CombatEvents"); ce.set("queue", new ArrayList<>()); combat.addComponent(ce);
        Component cl = new Component("CombatLog"); cl.set("entries", new ArrayList<>()); combat.addComponent(cl);
        combat.addTag("active_combat");
        store.add(combat);
    }

    @Test
    void warCry_castInCombat_doesNotEndCombat() {
        mkCombat();
        mkWarrior("hero");
        mkEnemy("gob", 0);

        int heroHp0 = store.get("hero").getComponent("Health").getInt("hp");
        int gobHp0 = store.get("gob").getComponent("Health").getInt("hp");
        assertThat(heroHp0).isGreaterThan(0);
        assertThat(gobHp0).isGreaterThan(0);

        // Resolve a round: hero casts war_cry, enemy basic_attacks hero.
        Map<String,Object> heroCmd = new HashMap<>(); heroCmd.put("type","war_cry");
        Map<String,Object> gobCmd = new HashMap<>(); gobCmd.put("type","basic_attack"); gobCmd.put("targetId","hero");
        Map<String,Object> commands = new HashMap<>();
        commands.put("hero", heroCmd); commands.put("gob", gobCmd);
        GameEvent ev = new GameEvent("combat.resolve_round");
        ev.set("combatId","b1"); ev.set("commands", commands);
        bus.fire("combat.resolve_round", ev);

        String phase = store.get("b1").getComponent("CombatState").getString("phase");
        int heroHp = store.get("hero").getComponent("Health").getInt("hp");
        int gobHp = store.get("gob") != null ? store.get("gob").getComponent("Health").getInt("hp") : -999;

        System.out.println("[WARCRY-BUG] phase="+phase+" heroHp="+heroHp+" gobHp="+gobHp
            + " 力量="+store.get("hero").getComponent("PrimaryStats").getInt("力量"));

        // 期望:战斗未结束(还是 COMMAND,进入下一回合),敌人还活着
        assertThat(gobHp).as("enemy still alive after war_cry round").isGreaterThan(0);
        assertThat(phase).as("combat should NOT end after war_cry").isEqualTo("COMMAND");
    }

    // 走真实前端入口 action.combat_command(start_combat.js)——它在 resolve 后查 phase 并可能 endCombat。
    @Test
    void warCry_viaCombatCommand_keepsPlayerInCombat() {
        mkCombat();
        mkWarrior("hero");
        mkEnemy("gob", 0);

        GameEvent ev = new GameEvent("action.combat_command");
        ev.set("playerId","hero");
        ev.set("command","war_cry");   // 注意:无 targetId(战吼是 group/ally)
        bus.fire("action.combat_command", ev);

        boolean stillInCombat = store.get("hero").hasTag("combat:b1");
        boolean combatExists = store.get("b1") != null;
        int 力量 = store.get("hero").getComponent("PrimaryStats").getInt("力量");
        System.out.println("[WARCRY-CMD] stillInCombat="+stillInCombat+" combatExists="+combatExists+" 力量="+力量);

        assertThat(stillInCombat).as("player should remain in combat after casting war_cry").isTrue();
        assertThat(combatExists).as("combat entity should still exist").isTrue();
    }
}

