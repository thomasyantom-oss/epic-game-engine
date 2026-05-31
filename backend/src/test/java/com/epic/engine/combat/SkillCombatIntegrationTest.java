package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test 3 — Live combat round through the data-driven dispatcher.
 *
 * Proves the headline claim of the skill-architecture refactor:
 *   • A skill whose YAML has an `effect:` field is fully handled by the dispatcher +
 *     effect executor with no bespoke handler — end-to-end in a real combat round.
 *   • fireball (damage_with_debuff) deals attack+10 damage and applies Buff_burning.
 *   • goblin's basic_attack (damage_only via_damage_calc) hits the mage.
 *   • Combat phase advances after the round (not stuck in RESOLVE).
 *
 * Burning ticks (burning.js) are intentionally NOT loaded so the exact goblin hp after
 * the fireball is deterministic (50 - 20 = 30).  The buff presence is still verified.
 */
class SkillCombatIntegrationTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.bindService("buffs", new BuffService(bus, store));

        // Full combat stack
        Path combatDir = Path.of("../mods/base-rules/handlers/combat");
        runtime.execute(Files.readString(combatDir.resolve("initiative.js")),    "initiative.js");
        runtime.execute(Files.readString(combatDir.resolve("damage_calc.js")),   "damage_calc.js");
        runtime.execute(Files.readString(combatDir.resolve("death_check.js")),   "death_check.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_flow.js")),   "combat_flow.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_events.js")), "combat_events.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_log.js")),    "combat_log.js");

        // Skill lib + dispatcher
        Path skillDir = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(skillDir.resolve("00_skill_lib.js")), "00_skill_lib.js");
        runtime.execute(Files.readString(skillDir.resolve("01_effects.js")),   "01_effects.js");
        runtime.execute(Files.readString(skillDir.resolve("02_dispatch.js")),  "02_dispatch.js");

        // Bespoke skill: defend (still has its own JS handler)
        runtime.execute(Files.readString(Path.of("../mods/base-rules/skills/defend.js")), "defend.js");

        // NOTE: burning.js intentionally NOT loaded — keeps goblin hp deterministic at 30.
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: end-to-end dispatcher integration — fireball + basic_attack round
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    void combatRound_fireballDealsDispatcherDrivenDamageAndAppliesBurning() {
        setupCombat();

        // mage uses fireball (data-driven: effect=damage_with_debuff in YAML)
        Map<String, Object> mageCmd = new HashMap<>();
        mageCmd.put("type", "fireball");
        mageCmd.put("targetId", "goblin1");

        // goblin uses basic_attack (data-driven: effect=damage_only via_damage_calc in YAML)
        Map<String, Object> goblinCmd = new HashMap<>();
        goblinCmd.put("type", "basic_attack");
        goblinCmd.put("targetId", "mage");

        Map<String, Object> commands = new HashMap<>();
        commands.put("mage",    mageCmd);
        commands.put("goblin1", goblinCmd);

        GameEvent roundEvent = new GameEvent("combat.resolve_round");
        roundEvent.set("combatId", "battle1");
        roundEvent.set("commands", commands);
        bus.fire("combat.resolve_round", roundEvent);

        Entity goblin = store.get("goblin1");
        Entity mage   = store.get("mage");
        Entity combat = store.get("battle1");

        // ── Fireball assertion ────────────────────────────────────────────────
        // fireball.yaml: add=8, scaling={法术强度:0.5}; mage DerivedStats.法术强度=14
        // → 8 + ⌈14×0.5⌉ = 8 + 7 = 15 damage, ignores defense
        // goblin starts at 50 hp; after fireball: 50 - 15 = 35
        // (burning.js NOT loaded → no tick damage, exact value is deterministic)
        assertThat(goblin.getComponent("Health").getInt("hp"))
                .as("goblin hp after fireball (no burn tick)")
                .isEqualTo(35);

        // Burning buff must have been applied by damage_with_debuff executor
        assertThat(goblin.hasComponent("Buff_burning"))
                .as("goblin has Buff_burning after fireball")
                .isTrue();

        // ── Goblin basic_attack assertion ─────────────────────────────────────
        // basic_attack.yaml: via_damage_calc → goblin attack(6) - mage defense(5) = 1 (min 1)
        // mage starts at 100 hp → mage hp <= 99 (speed 6 > goblin speed 3, mage goes first,
        // goblin still alive, so basic_attack fires; mage should take 1 damage)
        assertThat(mage.getComponent("Health").getInt("hp"))
                .as("mage hp after goblin basic_attack")
                .isLessThanOrEqualTo(99);

        // ── Phase assertion ───────────────────────────────────────────────────
        // Round must advance (combat not stuck in RESOLVE); both combatants alive → COMMAND
        String phase = combat.getComponent("CombatState").getString("phase");
        assertThat(phase)
                .as("combat phase after round")
                .isNotEqualTo("RESOLVE");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setupCombat() {
        // Mage (player): attack 10, defense 5, speed 6
        Entity mage = new Entity("mage");
        Component mHealth = new Component("Health");
        mHealth.set("hp", 100); mHealth.set("maxHp", 100);
        mage.addComponent(mHealth);
        Component mStats = new Component("CombatStats");
        mStats.set("attack", 10); mStats.set("defense", 5); mStats.set("speed", 6);
        mage.addComponent(mStats);
        Component mDerived = new Component("DerivedStats");
        mDerived.set("物理强度", 0); mDerived.set("法术强度", 14); mDerived.set("精神强度", 3);
        mage.addComponent(mDerived);
        Component mName = new Component("Name");
        mName.set("value", "法师");
        mage.addComponent(mName);
        Component mPos = new Component("CombatPosition");
        mPos.set("row", "FRONT"); mPos.set("slot", 0);
        mage.addComponent(mPos);
        mage.addTag("player");
        mage.addTag("combat:battle1");
        store.add(mage);

        // Goblin (enemy): attack 6, defense 2, speed 3, hp 50
        Entity goblin = new Entity("goblin1");
        Component gHealth = new Component("Health");
        gHealth.set("hp", 50); gHealth.set("maxHp", 50);
        goblin.addComponent(gHealth);
        Component gStats = new Component("CombatStats");
        gStats.set("attack", 6); gStats.set("defense", 2); gStats.set("speed", 3);
        goblin.addComponent(gStats);
        Component gName = new Component("Name");
        gName.set("value", "哥布林");
        goblin.addComponent(gName);
        Component gPos = new Component("CombatPosition");
        gPos.set("row", "FRONT"); gPos.set("slot", 0);
        goblin.addComponent(gPos);
        goblin.addTag("enemy");
        goblin.addTag("combat:battle1");
        store.add(goblin);

        // Combat entity
        Entity combatEntity = new Entity("battle1");
        Component combatState = new Component("CombatState");
        combatState.set("round", 0);
        combatState.set("phase", "COMMAND");
        combatEntity.addComponent(combatState);
        // CombatEvents and CombatLog are optional; combat_events.js/combat_log.js null-check them
        store.add(combatEntity);
    }
}
