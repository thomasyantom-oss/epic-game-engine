package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierChainServiceTest {

    EventBus bus;
    EntityStore store;
    ModifierTypeRegistry typeRegistry;
    ModifierChainService service;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        store = new EntityStore();
        typeRegistry = new ModifierTypeRegistry();
        typeRegistry.loadFromList(List.of(
            java.util.Map.of("id", "class", "label", "职业", "stack_rule", "exclusive", "base_priority", 180),
            java.util.Map.of("id", "equipment", "label", "装备", "stack_rule", "additive", "base_priority", 50)
        ));
        service = new ModifierChainService(bus, store, typeRegistry);
    }

    @Test
    void addModifier_exclusive_removesOldSameType() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("class_warrior", "class", "战士", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));
        service.addModifier("p1", new Modifier("class_mage", "class", "法师", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 1)));

        // exclusive → old warrior removed, only mage applies
        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(11);
    }

    @Test
    void addModifier_additive_keepsAll() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("sword", "equipment", "铁剑", "equip_sword", 50,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 8)));
        service.addModifier("p1", new Modifier("ring", "equipment", "攻击戒", "equip_ring", 50,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));

        assertThat(e.getComponent("CombatStats").getInt("attack")).isEqualTo(21);
    }

    @Test
    void recalculate_firesBeforeAfterEvents() {
        Entity e = new Entity("p1");
        Component health = new Component("Health");
        health.set("hp", 80);
        health.set("maxHp", 100);
        e.addComponent(health);
        store.add(e);
        service.setBase("p1");

        List<String> fired = new ArrayList<>();
        bus.on("entity.before_recalculate", 100, ev -> fired.add("before"));
        bus.on("entity.after_recalculate", 100, ev -> fired.add("after"));

        service.recalculate("p1");

        assertThat(fired).containsExactly("before", "after");
    }

    @Test
    void getContributions_returnsPerModifierDeltas() {
        Entity e = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        e.addComponent(stats);
        store.add(e);
        service.setBase("p1");

        service.addModifier("p1", new Modifier("class_warrior", "class", "战士", "class", 180,
                ent -> ent.getComponent("CombatStats").set("attack", ent.getComponent("CombatStats").getInt("attack") + 3)));

        List<ModifierDiff> diffs = service.getContributions("p1");
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).componentDeltas().get("CombatStats").get("attack")).isEqualTo(3L);
    }
}
