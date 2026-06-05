package com.epic.engine.skill;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PassiveHandlerTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        rt.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        rt.execute(Files.readString(skill.resolve("04_passive_lib.js")), "04_passive_lib.js");
        rt.execute(Files.readString(Path.of("../mods/base-rules/passives/lifesteal_on_kill.js")), "lifesteal_on_kill.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    @Test
    void lifesteal_healsOnKill() {
        Entity killer = new Entity("killer");
        Component h = new Component("Health");
        h.set("hp", 10);
        h.set("maxHp", 30);
        killer.addComponent(h);
        Component sb = new Component("Skillbook");
        sb.set("known", new java.util.ArrayList<>(java.util.List.of(skill("lifesteal_on_kill"))));
        killer.addComponent(sb);
        store.add(killer);

        rt.execute("""
            var ev = engine.newEvent('combat.unit_death');
            ev.set('killerId', 'killer');
            ev.set('deadId', 'dead');
            ev.set('combatId', 'c1');
            engine.fire('combat.unit_death', ev);
            """, "probe.js");

        assertThat(store.get("killer").getComponent("Health").getInt("hp")).isEqualTo(15);
    }

    private java.util.Map<String, Object> skill(String base) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("base", base);
        m.put("node", null);
        m.put("level", 1);
        return m;
    }
}
