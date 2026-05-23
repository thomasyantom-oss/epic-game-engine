package com.epic.engine.persistence;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
