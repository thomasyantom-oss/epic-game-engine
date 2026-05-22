package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {

    @Test
    void addAndGetComponent() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);

        entity.addComponent(health);

        assertThat(entity.hasComponent("Health")).isTrue();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    @Test
    void removeComponent() {
        Entity entity = new Entity("player1");
        entity.addComponent(new Component("Mana"));

        entity.removeComponent("Mana");

        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void tagOperations() {
        Entity entity = new Entity("goblin1");
        entity.addTag("humanoid");
        entity.addTag("goblin");

        assertThat(entity.hasTag("humanoid")).isTrue();
        assertThat(entity.hasTag("undead")).isFalse();
        assertThat(entity.getTags()).containsExactlyInAnyOrder("humanoid", "goblin");
    }

    @Test
    void tagIndex_findByTag() {
        TagIndex index = new TagIndex();
        Entity e1 = new Entity("goblin1");
        e1.addTag("humanoid");
        Entity e2 = new Entity("skeleton1");
        e2.addTag("undead");
        Entity e3 = new Entity("goblin2");
        e3.addTag("humanoid");

        index.register(e1);
        index.register(e2);
        index.register(e3);

        assertThat(index.getByTag("humanoid")).containsExactlyInAnyOrder(e1, e3);
        assertThat(index.getByTag("undead")).containsExactly(e2);
    }

    @Test
    void tagIndex_updatesOnTagChange() {
        TagIndex index = new TagIndex();
        Entity e1 = new Entity("goblin1");
        e1.addTag("humanoid");
        index.register(e1);

        e1.removeTag("humanoid");
        index.reindex(e1);

        assertThat(index.getByTag("humanoid")).isEmpty();
    }
}
