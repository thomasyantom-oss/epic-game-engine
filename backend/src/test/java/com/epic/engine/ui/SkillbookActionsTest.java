package com.epic.engine.ui;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.snapshot.WorldSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillbookActionsTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));

        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/03_skillbook.js")), "03_skillbook.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/ui/actions.js")), "actions.js");

        Entity player = new Entity("player1");
        Component skillbook = new Component("Skillbook");
        skillbook.set("slots", 2);
        skillbook.set("known", new ArrayList<>(List.of(
                skill("fireball", true),
                skill("light_field", true),
                skill("ice_beam", false)
        )));
        player.addComponent(skillbook);
        store.add(player);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void unequip_thenEquip_togglesEquipped() {
        fireSkillbook("skillbook_unequip", "fireball");
        assertThat(equippedBases()).doesNotContain("fireball");

        fireSkillbook("skillbook_equip", "fireball");
        assertThat(equippedBases()).contains("fireball");
    }

    @Test
    void equip_whenSlotsFull_rejected() {
        fireSkillbook("skillbook_equip", "ice_beam");
        assertThat(equippedBases()).hasSize(2).doesNotContain("ice_beam");
    }

    @Test
    void equip_whenInCombat_rejected() {
        store.get("player1").addTag("combat:c1");
        fireSkillbook("skillbook_unequip", "fireball");
        assertThat(equippedBases()).contains("fireball");
    }

    @Test
    void equip_unknownBase_rejected() {
        fireSkillbook("skillbook_equip", "nonexistent");
        assertThat(equippedBases()).hasSize(2);
    }

    @Test
    void combatActions_universalAlways_onlyEquippedSkills() {
        store.get("player1").addTag("combat:c1");

        GameEvent event = new GameEvent("ui.render_actions");
        event.set("entityId", "player1");
        event.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        bus.fire("ui.render_actions", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = event.get("actions");
        List<String> commands = actions.stream()
                .map(action -> String.valueOf(action.params().get("command")))
                .toList();
        assertThat(commands).contains("basic_attack", "defend", "flee");
        assertThat(commands).contains("fireball", "light_field");
        assertThat(commands).doesNotContain("ice_beam");
        assertThat(actions.stream().filter(a -> "skill".equals(a.params().get("category"))).map(a -> a.params().get("command")).toList())
                .containsExactly("fireball", "light_field");
    }

    private void fireSkillbook(String type, String base) {
        GameEvent event = new GameEvent("action." + type);
        event.set("playerId", "player1");
        event.set("base", base);
        bus.fire("action." + type, event);
    }

    @SuppressWarnings("unchecked")
    private List<String> equippedBases() {
        List<Map<String, Object>> known = (List<Map<String, Object>>) store.get("player1").getComponent("Skillbook").get("known");
        return known.stream()
                .filter(skill -> Boolean.TRUE.equals(skill.get("equipped")))
                .map(skill -> String.valueOf(skill.get("base")))
                .toList();
    }

    private Map<String, Object> skill(String base, boolean equipped) {
        Map<String, Object> skill = new HashMap<>();
        skill.put("base", base);
        skill.put("node", null);
        skill.put("equipped", equipped);
        return skill;
    }
}
