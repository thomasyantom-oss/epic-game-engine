package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class BespokeSkillOrderingTest {

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

        Path combat = Path.of("../mods/base-rules/handlers/combat");
        for (String f : new String[]{"death_check.js", "combat_events.js", "combat_log.js"}) {
            rt.execute(Files.readString(combat.resolve(f)), f);
        }
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        for (String f : new String[]{"00_skill_lib.js", "01_effects.js", "02_dispatch.js"}) {
            rt.execute(Files.readString(skill.resolve(f)), f);
        }
        Path skills = Path.of("../mods/base-rules/skills");
        rt.execute(Files.readString(skills.resolve("cleave.js")), "cleave.js");
        rt.execute(Files.readString(skills.resolve("cross_blast.js")), "cross_blast.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    @Test
    void cleaveSkillAnimation_isQueuedBeforeDeathEvents() {
        setupCombat();
        mkCaster("hero");
        mkEnemy("e_front_0", "FRONT", 0);
        mkEnemy("e_front_1", "FRONT", 1);

        fire("cleave", "e_front_0");

        List<?> queue = queue();
        int skillIdx = firstEventWithAnimation(queue, "slash");
        int deathIdx = firstDeathEvent(queue);

        assertThat(skillIdx).as("cleave slash event present").isGreaterThanOrEqualTo(0);
        assertThat(deathIdx).as("death event present").isGreaterThanOrEqualTo(0);
        assertThat(skillIdx).as("cleave animation must be queued before death events").isLessThan(deathIdx);
    }

    @Test
    void crossBlastSkillAnimation_isQueuedBeforeDeathEvents() {
        setupCombat();
        mkCaster("hero");
        mkEnemy("e_mid_1", "MID", 1);
        mkEnemy("e_mid_0", "MID", 0);
        mkEnemy("e_front_1", "FRONT", 1);

        fire("cross_blast", "e_mid_1");

        List<?> queue = queue();
        int skillIdx = firstEventWithAnimation(queue, "pulse");
        int deathIdx = firstDeathEvent(queue);

        assertThat(skillIdx).as("cross_blast pulse event present").isGreaterThanOrEqualTo(0);
        assertThat(deathIdx).as("death event present").isGreaterThanOrEqualTo(0);
        assertThat(skillIdx).as("cross_blast animation must be queued before death events").isLessThan(deathIdx);
    }

    private void setupCombat() {
        Entity combat = new Entity("b1");
        Component state = new Component("CombatState");
        state.set("round", 1);
        state.set("phase", "RESOLVE");
        combat.addComponent(state);
        Component events = new Component("CombatEvents");
        events.set("queue", new ArrayList<>());
        combat.addComponent(events);
        Component log = new Component("CombatLog");
        log.set("entries", new ArrayList<>());
        combat.addComponent(log);
        store.add(combat);
    }

    private void mkCaster(String id) {
        Entity e = new Entity(id);
        Component stats = new Component("CombatStats");
        stats.set("attack", 20);
        stats.set("defense", 0);
        stats.set("speed", 5);
        e.addComponent(stats);
        Component primary = new Component("PrimaryStats");
        primary.set("力量", 10);
        primary.set("敏捷", 3);
        primary.set("智力", 3);
        primary.set("体质", 3);
        primary.set("意志", 3);
        e.addComponent(primary);
        Component health = new Component("Health");
        health.set("hp", 50);
        health.set("maxHp", 50);
        e.addComponent(health);
        Component pos = new Component("CombatPosition");
        pos.set("row", "BACK");
        pos.set("slot", 0);
        e.addComponent(pos);
        Component name = new Component("Name");
        name.set("value", "战士");
        e.addComponent(name);
        e.addTag("player");
        e.addTag("combat:b1");
        store.add(e);
    }

    private void mkEnemy(String id, String row, int slot) {
        Entity e = new Entity(id);
        Component stats = new Component("CombatStats");
        stats.set("attack", 1);
        stats.set("defense", 0);
        stats.set("speed", 1);
        e.addComponent(stats);
        Component health = new Component("Health");
        health.set("hp", 1);
        health.set("maxHp", 1);
        e.addComponent(health);
        Component pos = new Component("CombatPosition");
        pos.set("row", row);
        pos.set("slot", slot);
        e.addComponent(pos);
        Component name = new Component("Name");
        name.set("value", "靶子");
        e.addComponent(name);
        e.addTag("enemy");
        e.addTag("combat:b1");
        store.add(e);
    }

    private void fire(String type, String targetId) {
        GameEvent event = new GameEvent("combat.unit_action");
        event.set("combatId", "b1");
        event.set("actorId", "hero");
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("type", type);
        cmd.put("targetId", targetId);
        event.set("command", cmd);
        bus.fire("combat.unit_action", event);
    }

    private List<?> queue() {
        return (List<?>) store.get("b1").getComponent("CombatEvents").get("queue");
    }

    private int firstEventWithAnimation(List<?> queue, String type) {
        for (int i = 0; i < queue.size(); i++) {
            Map<?, ?> event = (Map<?, ?>) queue.get(i);
            Object animation = event.get("animation");
            if (animation instanceof List<?> steps) {
                for (Object step : steps) {
                    if (type.equals(String.valueOf(((Map<?, ?>) step).get("type")))) return i;
                }
            }
        }
        return -1;
    }

    private int firstDeathEvent(List<?> queue) {
        for (int i = 0; i < queue.size(); i++) {
            Map<?, ?> event = (Map<?, ?>) queue.get(i);
            Object effects = event.get("effects");
            if (effects instanceof List<?> effs) {
                for (Object effect : effs) {
                    if ("death".equals(String.valueOf(((Map<?, ?>) effect).get("type")))) return i;
                }
            }
        }
        return -1;
    }
}
