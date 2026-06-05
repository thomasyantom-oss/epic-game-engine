package com.epic.engine.skill;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PassiveStatModTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime rt;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
        rt = new ScriptRuntime(bus, store, chainService, typeReg);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        rt.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        rt.execute(Files.readString(skill.resolve("04_passive_lib.js")), "04_passive_lib.js");
    }

    @AfterEach
    void tearDown() {
        rt.close();
    }

    @Test
    void statPassive_registersPrimaryStatModifier() {
        Entity e = new Entity("m");
        Component ps = new Component("PrimaryStats");
        ps.set("力量", 5);
        e.addComponent(ps);
        Component sb = new Component("Skillbook");
        sb.set("known", new java.util.ArrayList<>(java.util.List.of(skill("iron_skin", 1))));
        e.addComponent(sb);
        store.add(e);

        rt.execute("""
            engine.setBase('m');
            Passive.registerStatMods('m');
            engine.recalculate('m');
            """, "probe.js");

        assertThat(store.get("m").getComponent("PrimaryStats").getInt("力量")).isEqualTo(15);
    }

    private java.util.Map<String, Object> skill(String base, int level) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("base", base);
        m.put("node", null);
        m.put("level", level);
        return m;
    }
}
