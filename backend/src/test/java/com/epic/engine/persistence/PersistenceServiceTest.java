package com.epic.engine.persistence;

import com.epic.engine.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PersistenceServiceTest {

    @Autowired PersistenceService persistenceService;
    @Autowired EntityStore entityStore;

    @Test
    void saveAndLoad_roundTrips() {
        Entity player = new Entity("persist_test_player");
        Component health = new Component("Health");
        health.set("hp", 85);
        health.set("maxHp", 100);
        player.addComponent(health);
        player.addTag("persistent");
        player.addTag("player");

        persistenceService.save(player);

        Entity loaded = persistenceService.load("persist_test_player");

        assertThat(loaded).isNotNull();
        assertThat(loaded.hasComponent("Health")).isTrue();
        assertThat(loaded.getComponent("Health").getInt("hp")).isEqualTo(85);
        assertThat(loaded.hasTag("player")).isTrue();
        assertThat(loaded.hasTag("persistent")).isTrue();
    }

    @Test
    void nonPersistent_notSaved() {
        Entity goblin = new Entity("temp_goblin");
        goblin.addComponent(new Component("Health"));
        // No "persistent" tag

        persistenceService.save(goblin);

        assertThat(persistenceService.load("temp_goblin")).isNull();
    }

    @Test
    void save_withChain_savesBaseStateValues() {
        ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
        EventBus bus = new EventBus();
        ModifierChainService chainService = new ModifierChainService(bus, entityStore, typeReg);

        Entity e = new Entity("char_test");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        Component slots = new Component("EquipmentSlots");
        slots.set("weapon", "iron_sword");
        e.addComponent(slots);
        e.addTag("persistent");
        entityStore.add(e);

        // setBase with only stat components
        chainService.setBaseSelective("char_test", List.of("CombatStats"));
        // Add modifier that inflates attack
        chainService.addModifier("char_test", new Modifier("sword", "equipment", "铁剑", "equipment_sword", 50,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 8)));

        // Current state: attack=18, EquipmentSlots.weapon="iron_sword"
        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(18);

        // Inject chainService and save
        persistenceService.setModifierChainService(chainService);
        persistenceService.save(e);

        // Load and verify: attack should be BASE value (10), not 18
        Entity loaded = persistenceService.load("char_test");
        assertThat(loaded.getComponent("CombatStats").getInt("attack")).isEqualTo(10);
        // Non-stat component preserved as-is
        assertThat(loaded.getComponent("EquipmentSlots").getString("weapon")).isEqualTo("iron_sword");
    }
}
