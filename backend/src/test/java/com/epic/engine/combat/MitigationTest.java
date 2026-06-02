package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class MitigationTest {
    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;
    ModifierTypeRegistry typeReg;
    ModifierChainService chainService;

    public static class StubPersistence {
        @HostAccess.Export public void save(Object e) {}
    }

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));

        SchemaRegistry schemas = new SchemaRegistry();
        schemas.loadFromModPath(Path.of("../mods/base-rules"));
        rt.bindService("schemas", schemas);
        rt.bindService("buffs", new BuffService(bus, store));
        rt.bindService("persistence", new StubPersistence());
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    void js(String script) {
        rt.execute("(function(){ " + script + " })();", "test.js");
    }

    @Test
    void resistances_defaultZero_equipmentAdds_derivedNeverWrites() throws Exception {
        loadScripts(
                "../mods/base-rules/handlers/character/derived_stats.js",
                "../mods/base-rules/handlers/character/leveling.js",
                "../mods/base-rules/handlers/equipment/equip.js",
                "../mods/base-rules/handlers/character/recalculate_hooks.js"
        );
        addResistAccessory("resist_ring", "Resistances.法术", 20);
        persistedWarriorWithAccessory("hero", "resist_ring");

        loadEntity("hero");

        Component res = store.get("hero").getComponent("Resistances");
        assertThat(res).as("Resistances component exists").isNotNull();
        assertThat(res.getInt("物理")).isEqualTo(0);
        assertThat(res.getInt("精神")).isEqualTo(0);
        assertThat(res.getInt("法术")).as("equipment adds 法抗").isEqualTo(20);

        loadEntity("hero");
        assertThat(store.get("hero").getComponent("Resistances").getInt("法术"))
                .as("equipment resistance does not inflate on reload")
                .isEqualTo(20);
    }

    @Test
    void enemySpawn_readsEncounterResistances_defaultingToZero() throws Exception {
        loadScripts(
                "../mods/base-rules/handlers/combat/start_combat.js",
                "../mods/base-rules/handlers/character/derived_stats.js"
        );
        addMinimalPlayer("hero");

        js("var ev=engine.newEvent('combat.start_encounter');"
                + "ev.set('playerId','hero');"
                + "ev.set('encounterId','mitigation_resist_test');"
                + "engine.fire('combat.start_encounter', ev);");

        Component configured = store.get("resist_dummy_0").getComponent("Resistances");
        assertThat(configured).isNotNull();
        assertThat(configured.getInt("物理")).isEqualTo(0);
        assertThat(configured.getInt("法术")).isEqualTo(50);
        assertThat(configured.getInt("精神")).isEqualTo(0);

        Component defaults = store.get("plain_dummy_1").getComponent("Resistances");
        assertThat(defaults).isNotNull();
        assertThat(defaults.getInt("物理")).isEqualTo(0);
        assertThat(defaults.getInt("法术")).isEqualTo(0);
        assertThat(defaults.getInt("精神")).isEqualTo(0);
    }

    @Test
    void mitigate_branches() throws Exception {
        loadScripts("../mods/base-rules/handlers/skill/00_skill_lib.js");
        addProbe();

        addTarget("t", 5, 0, 0, 0, false);
        assertThat(mitigate("t", 20, "{delivery:'普攻'}")).isEqualTo(15);
        assertThat(mitigate("t", 3, "{delivery:'普攻'}")).isEqualTo(1);
        assertThat(mitigate("t", 20, "{delivery:'技能', type:'法术'}")).isEqualTo(20);

        addTarget("r", 5, 0, 50, 0, false);
        assertThat(mitigate("r", 20, "{delivery:'技能', type:'法术'}")).isEqualTo(10);
        assertThat(mitigate("r", 20, "{delivery:'技能', type:'物理'}")).isEqualTo(20);
        assertThat(mitigate("r", 20, "{delivery:'普攻'}")).isEqualTo(15);

        addTarget("n", 0, -100, 0, 0, false);
        assertThat(mitigate("n", 10, "{delivery:'技能', type:'物理'}")).isEqualTo(20);

        addTarget("d", 5, 0, 0, 0, true);
        assertThat(mitigate("d", 20, "{delivery:'普攻'}")).isEqualTo(7);
        assertThat(mitigate("d", 20, "{delivery:'技能', type:'物理'}")).isEqualTo(10);
        assertThat(mitigate("d", 20, "{delivery:'技能', type:'物理', ignoreDefend:true}")).isEqualTo(20);
    }

    @Test
    void basicAttack_numbersUnchanged() throws Exception {
        loadCombatScriptsForRound();
        addCombat("battle1");
        addCombatUnit("hero", "勇者", 100, 14, 4, 10, true, "FRONT", 0);
        addCombatUnit("goblin", "哥布林", 100, 14, 4, 1, false, "FRONT", 0);

        resolveRound("battle1", "hero", "basic_attack", "goblin", "goblin", "basic_attack", "hero");
        assertThat(store.get("goblin").getComponent("Health").getInt("hp"))
                .as("basic attack still equals attack-defense")
                .isEqualTo(90);

        store.get("hero").getComponent("Health").set("hp", 100);
        store.get("goblin").getComponent("Health").set("hp", 100);
        resolveRound("battle1", "hero", "defend", null, "goblin", "basic_attack", "hero");
        assertThat(store.get("hero").getComponent("Health").getInt("hp"))
                .as("defending basic attack still floors half damage")
                .isEqualTo(95);
    }

    void loadScripts(String... paths) throws Exception {
        for (String path : paths) {
            Path p = Path.of(path);
            rt.execute(Files.readString(p), p.getFileName().toString());
        }
    }

    void loadCombatScriptsForRound() throws Exception {
        loadScripts(
                "../mods/base-rules/handlers/combat/initiative.js",
                "../mods/base-rules/handlers/combat/damage_calc.js",
                "../mods/base-rules/handlers/combat/death_check.js",
                "../mods/base-rules/handlers/combat/combat_flow.js",
                "../mods/base-rules/handlers/combat/combat_events.js",
                "../mods/base-rules/handlers/combat/combat_log.js",
                "../mods/base-rules/handlers/skill/00_skill_lib.js",
                "../mods/base-rules/handlers/skill/01_effects.js",
                "../mods/base-rules/handlers/skill/02_dispatch.js",
                "../mods/base-rules/skills/defend.js",
                "../mods/base-rules/buffs/defending.js"
        );
    }

    void addResistAccessory(String id, String statKey, int value) {
        Entity item = new Entity(id);
        Component meta = new Component("ItemMeta");
        meta.set("name", "抗性戒指");
        meta.set("type", "accessory");
        meta.set("rarity", "common");
        item.addComponent(meta);
        Component stats = new Component("ItemStats");
        stats.set(statKey, value);
        item.addComponent(stats);
        store.add(item);
    }

    int mitigate(String targetId, int raw, String optsJs) {
        js("store.get('probe').getComponent('Probe').set('v', "
                + "Skill.mitigate(store.get('" + targetId + "'), " + raw + ", " + optsJs + "));");
        return store.get("probe").getComponent("Probe").getInt("v");
    }

    void addProbe() {
        Entity e = new Entity("probe");
        Component p = new Component("Probe");
        p.set("v", 0);
        e.addComponent(p);
        store.add(e);
    }

    void addTarget(String id, int defense, int physical, int magical, int mental, boolean defending) {
        Entity e = new Entity(id);
        Component h = new Component("Health");
        h.set("hp", 100);
        h.set("maxHp", 100);
        e.addComponent(h);
        Component c = new Component("CombatStats");
        c.set("attack", 0);
        c.set("defense", defense);
        c.set("speed", 0);
        e.addComponent(c);
        Component r = new Component("Resistances");
        r.set("物理", physical);
        r.set("法术", magical);
        r.set("精神", mental);
        e.addComponent(r);
        if (defending) e.addComponent(new Component("Buff_defending"));
        store.add(e);
    }

    void addCombat(String combatId) {
        Entity combat = new Entity(combatId);
        Component state = new Component("CombatState");
        state.set("round", 1);
        state.set("phase", "COMMAND");
        combat.addComponent(state);
        Component events = new Component("CombatEvents");
        events.set("queue", new ArrayList<>());
        combat.addComponent(events);
        Component log = new Component("CombatLog");
        log.set("entries", new ArrayList<>());
        combat.addComponent(log);
        store.add(combat);
    }

    void addCombatUnit(String id, String name, int hp, int attack, int defense, int speed,
                       boolean player, String row, int slot) {
        Entity e = new Entity(id);
        Component h = new Component("Health");
        h.set("hp", hp);
        h.set("maxHp", hp);
        e.addComponent(h);
        Component c = new Component("CombatStats");
        c.set("attack", attack);
        c.set("defense", defense);
        c.set("speed", speed);
        e.addComponent(c);
        Component r = new Component("Resistances");
        r.set("物理", 0);
        r.set("法术", 0);
        r.set("精神", 0);
        e.addComponent(r);
        Component n = new Component("Name");
        n.set("value", name);
        e.addComponent(n);
        Component pos = new Component("CombatPosition");
        pos.set("row", row);
        pos.set("slot", slot);
        e.addComponent(pos);
        e.addTag(player ? "player" : "enemy");
        e.addTag("combat:battle1");
        store.add(e);
    }

    void resolveRound(String combatId,
                      String actorA, String skillA, String targetA,
                      String actorB, String skillB, String targetB) {
        js("var commands=engine.newMap();"
                + "var a=engine.newMap(); a.put('type','" + skillA + "');"
                + (targetA != null ? "a.put('targetId','" + targetA + "');" : "")
                + "commands.put('" + actorA + "', a);"
                + "var b=engine.newMap(); b.put('type','" + skillB + "');"
                + (targetB != null ? "b.put('targetId','" + targetB + "');" : "")
                + "commands.put('" + actorB + "', b);"
                + "var ev=engine.newEvent('combat.resolve_round');"
                + "ev.set('combatId','" + combatId + "'); ev.set('commands', commands);"
                + "engine.fire('combat.resolve_round', ev);");
    }

    void loadEntity(String id) {
        js("var ev=engine.newEvent('entity.loaded'); ev.set('entity', store.get('" + id + "')); engine.fire('entity.loaded', ev);");
    }

    void persistedWarriorWithAccessory(String id, String accessoryId) {
        Entity e = new Entity(id);
        Component p = new Component("PrimaryStats");
        p.set("力量", 14); p.set("敏捷", 5); p.set("智力", 3);
        p.set("体质", 12); p.set("意志", 3); p.set("weaponAttr", "力量");
        e.addComponent(p);
        e.addComponent(new Component("DerivedStats"));
        Component r = new Component("Resistances");
        r.set("物理", 0); r.set("法术", 0); r.set("精神", 0);
        e.addComponent(r);
        Component h = new Component("Health"); h.set("hp", 150); h.set("maxHp", 150); e.addComponent(h);
        Component m = new Component("Mana"); m.set("mp", 50); m.set("maxMp", 50); e.addComponent(m);
        Component c = new Component("CombatStats"); c.set("attack", 6); c.set("defense", 3); c.set("speed", 5); e.addComponent(c);
        Component pos = new Component("Position"); pos.set("map", "world_map"); pos.set("x", 7); pos.set("y", 2); e.addComponent(pos);
        Component ch = new Component("Character"); ch.set("name", "勇者"); ch.set("classId", "warrior"); ch.set("level", 1); e.addComponent(ch);
        Component exp = new Component("Experience"); exp.set("xp", 0); exp.set("level", 1); exp.set("pendingPoints", 0); e.addComponent(exp);
        Component slots = new Component("EquipmentSlots"); slots.set("weapon", null); slots.set("armor", null); slots.set("accessory", accessoryId); e.addComponent(slots);
        Component inv = new Component("Inventory"); inv.set("items", new ArrayList<>()); e.addComponent(inv);
        store.add(e);
    }

    void addMinimalPlayer(String id) {
        Entity e = new Entity(id);
        Component h = new Component("Health"); h.set("hp", 100); h.set("maxHp", 100); e.addComponent(h);
        Component c = new Component("CombatStats"); c.set("attack", 10); c.set("defense", 3); c.set("speed", 3); e.addComponent(c);
        e.addTag("player");
        store.add(e);
    }
}
