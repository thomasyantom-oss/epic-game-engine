package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ModifierChainTest {

    @Test
    void singleModifier_appliesEffect() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        health.set("maxHp", 100);
        entity.addComponent(health);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("ring_hp", "ring_a", 100, e -> {
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + 20);
            e.getComponent("Health").set("maxHp", e.getComponent("Health").getInt("maxHp") + 20);
        }));
        chain.recalculate();

        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(120);
        assertThat(entity.getComponent("Health").getInt("maxHp")).isEqualTo(120);
    }

    @Test
    void modifiers_applyInPriorityOrder() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 50);
        entity.addComponent(mana);

        ModifierChain chain = new ModifierChain(entity);
        // Priority 100: mp +20
        chain.addModifier(new Modifier("ring_mp", "ring_a", 100, e -> {
            e.getComponent("Mana").set("mp", e.getComponent("Mana").getInt("mp") + 20);
        }));
        // Priority 200: convert mp to hp, remove mana
        chain.addModifier(new Modifier("blood_convert", "blood_staff", 200, e -> {
            int mp = e.getComponent("Mana").getInt("mp");
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + mp);
            e.removeComponent("Mana");
        }));
        chain.recalculate();

        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(170);
        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void removeModifierBySource_recalculates() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 50);
        entity.addComponent(mana);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("ring_mp", "ring_a", 100, e -> {
            e.getComponent("Mana").set("mp", e.getComponent("Mana").getInt("mp") + 20);
        }));
        chain.addModifier(new Modifier("blood_convert", "blood_staff", 200, e -> {
            int mp = e.getComponent("Mana").getInt("mp");
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + mp);
            e.removeComponent("Mana");
        }));
        chain.recalculate();

        // Now remove ring_a
        chain.removeBySource("ring_a");
        chain.recalculate();

        // blood_staff still converts, but no +20 from ring
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(150);
        assertThat(entity.hasComponent("Mana")).isFalse();
    }

    @Test
    void recalculate_restoresBaseStateBetweenRuns() {
        Entity entity = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 100);
        entity.addComponent(health);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("buff", "potion", 100, e -> {
            e.getComponent("Health").set("hp", e.getComponent("Health").getInt("hp") + 50);
        }));
        chain.recalculate();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(150);

        // Remove the buff, recalculate — should go back to base
        chain.removeBySource("potion");
        chain.recalculate();
        assertThat(entity.getComponent("Health").getInt("hp")).isEqualTo(100);
    }
}
