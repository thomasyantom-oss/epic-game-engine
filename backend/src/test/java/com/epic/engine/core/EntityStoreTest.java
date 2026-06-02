package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntityStoreTest {

    EntityStore store;

    @BeforeEach
    void setUp() {
        store = new EntityStore();
    }

    @Test
    void addAndGet() {
        Entity entity = new Entity("player1");
        store.add(entity);

        assertThat(store.get("player1")).isSameAs(entity);
    }

    @Test
    void remove() {
        Entity entity = new Entity("player1");
        store.add(entity);
        store.remove("player1");

        assertThat(store.get("player1")).isNull();
    }

    @Test
    void getByTag() {
        Entity e1 = new Entity("goblin1");
        e1.addTag("enemy");
        Entity e2 = new Entity("player1");
        e2.addTag("player");
        Entity e3 = new Entity("goblin2");
        e3.addTag("enemy");

        store.add(e1);
        store.add(e2);
        store.add(e3);

        assertThat(store.getByTag("enemy")).containsExactlyInAnyOrder(e1, e3);
    }

    @Test
    void getByComponent() {
        Entity e1 = new Entity("player1");
        e1.addComponent(new Component("Health"));
        e1.addComponent(new Component("Mana"));
        Entity e2 = new Entity("goblin1");
        e2.addComponent(new Component("Health"));

        store.add(e1);
        store.add(e2);

        assertThat(store.getByComponent("Mana")).containsExactly(e1);
        assertThat(store.getByComponent("Health")).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void all_returnsAllEntities() {
        store.add(new Entity("a"));
        store.add(new Entity("b"));

        assertThat(store.all()).hasSize(2);
    }

    @Test
    void clear_removesEntitiesAndTagIndexEntries() {
        Entity enemy = new Entity("goblin1");
        enemy.addTag("enemy");
        store.add(enemy);

        store.clear();

        assertThat(store.all()).isEmpty();
        assertThat(store.get("goblin1")).isNull();
        assertThat(store.getByTag("enemy")).isEmpty();
    }
}
