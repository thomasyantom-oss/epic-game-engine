package com.epic.engine.core;

import org.junit.jupiter.api.Test;
import java.util.List;
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

    @Test
    void modifier_hasTypeIdAndLabel() {
        Modifier m = new Modifier("id1", "equipment", "铁剑", "source1", 50,
                e -> e.getComponent("Health").set("hp", 10));
        assertThat(m.typeId()).isEqualTo("equipment");
        assertThat(m.label()).isEqualTo("铁剑");
    }

    @Test
    void recalculateWithTracking_recordsDeltas() {
        Entity entity = new Entity("p1");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        entity.addComponent(stats);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("class_warrior", "warrior", "战士", "class", 180,
                e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 3)));

        List<ModifierDiff> diffs = chain.recalculateWithTracking();

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).modifierId()).isEqualTo("class_warrior");
        assertThat(diffs.get(0).componentDeltas().get("CombatStats").get("attack")).isEqualTo(3L);
    }

    @Test
    void removeByTypeId_removesOnlyMatchingType() {
        Entity entity = new Entity("p3");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        entity.addComponent(stats);

        ModifierChain chain = new ModifierChain(entity);
        chain.addModifier(new Modifier("sword_atk", "equipment", "铁剑", "sword", 100,
                e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 5)));
        chain.addModifier(new Modifier("warrior_atk", "class", "战士", "class", 180,
                e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 3)));
        chain.recalculate();
        assertThat(entity.getComponent("CombatStats").getInt("attack")).isEqualTo(18);

        chain.removeByTypeId("equipment");
        chain.recalculate();

        assertThat(entity.getComponent("CombatStats").getInt("attack")).isEqualTo(13);
        assertThat(chain.getModifiers()).hasSize(1);
        assertThat(chain.getModifiers().get(0).id()).isEqualTo("warrior_atk");
    }

    @Test
    void setBaseSelective_onlyTracksListedComponents() {
        Entity entity = new Entity("p4");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        entity.addComponent(stats);
        Component slots = new Component("EquipmentSlots");
        slots.set("weapon", "iron_sword");
        entity.addComponent(slots);

        ModifierChain chain = new ModifierChain(entity, false);
        chain.setBaseSelective(List.of("CombatStats"));

        // getBaseState only contains CombatStats
        assertThat(chain.getBaseState()).containsKey("CombatStats");
        assertThat(chain.getBaseState()).doesNotContainKey("EquipmentSlots");

        chain.addModifier(new Modifier("cls", "class", 180,
                e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 5)));
        chain.recalculate();

        // CombatStats is restored to base (attack=10) then modifier applied (+5)
        assertThat(entity.getComponent("CombatStats").getInt("attack")).isEqualTo(15);
        // EquipmentSlots is not in base state so recalculate leaves it intact
        assertThat(entity.hasComponent("EquipmentSlots")).isTrue();
        assertThat(entity.getComponent("EquipmentSlots").getString("weapon")).isEqualTo("iron_sword");
    }

    @Test
    void recalculate_onlyRestoresBaseStateComponents() {
        Entity entity = new Entity("p2");
        Component stats = new Component("CombatStats");
        stats.set("attack", 10);
        entity.addComponent(stats);

        ModifierChain chain = new ModifierChain(entity);

        // Add a non-base component AFTER setBase was called (via constructor)
        Component slots = new Component("EquipmentSlots");
        slots.set("weapon", "iron_sword");
        entity.addComponent(slots);

        chain.addModifier(new Modifier("cls", "class", 180,
                e -> e.getComponent("CombatStats").set("attack", e.getComponent("CombatStats").getInt("attack") + 5)));
        chain.recalculate();

        // EquipmentSlots should NOT be touched by recalculate
        assertThat(entity.hasComponent("EquipmentSlots")).isTrue();
        assertThat(entity.getComponent("EquipmentSlots").getString("weapon")).isEqualTo("iron_sword");
        assertThat(entity.getComponent("CombatStats").getInt("attack")).isEqualTo(15);
    }
}
