package com.epic.engine.combat;

import com.epic.engine.buff.BuffService;
import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for four post-merge combat bugs:
 *   1a. fireball (present() path) emitted a damage_number animation with no `value` → invisible.
 *   1b. burning/poison ticked at round_end AFTER check_end, so a DoT kill on the last enemy
 *       didn't end combat until an extra round; DoT kills also skipped the death flow entirely.
 *   2.  defend was applied in initiative order (a faster enemy hit before the guard went up) and
 *       the buff lingered into the next round. It should be PRE-EMPTIVE (resolves before all
 *       normal actions) and last only the current round.
 *   3.  buffs survived combat end and leaked into the next encounter.
 */
class CombatBugfixTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    /** Minimal persistence stub so combat_events.js endCombat() can call persistence.save(). */
    public static class StubPersistence {
        @HostAccess.Export
        public void save(Object entity) { /* no-op */ }
    }

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        runtime.bindService("buffs", new BuffService(bus, store));
        runtime.bindService("persistence", new StubPersistence());

        Path combatDir = Path.of("../mods/base-rules/handlers/combat");
        runtime.execute(Files.readString(combatDir.resolve("initiative.js")),    "initiative.js");
        runtime.execute(Files.readString(combatDir.resolve("damage_calc.js")),   "damage_calc.js");
        runtime.execute(Files.readString(combatDir.resolve("death_check.js")),   "death_check.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_flow.js")),   "combat_flow.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_events.js")), "combat_events.js");
        runtime.execute(Files.readString(combatDir.resolve("combat_log.js")),    "combat_log.js");
        runtime.execute(Files.readString(combatDir.resolve("start_combat.js")),  "start_combat.js");

        Path skillDir = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(skillDir.resolve("00_skill_lib.js")), "00_skill_lib.js");
        runtime.execute(Files.readString(skillDir.resolve("01_effects.js")),   "01_effects.js");
        runtime.execute(Files.readString(skillDir.resolve("02_dispatch.js")),  "02_dispatch.js");

        runtime.execute(Files.readString(Path.of("../mods/base-rules/skills/defend.js")), "defend.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/buffs/burning.js")), "burning.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/buffs/defending.js")), "defending.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    // ── Bug 1a: fireball damage_number carries a value ─────────────────────────
    @Test
    @SuppressWarnings("unchecked")
    void fireball_damageNumberAnimation_hasValue() {
        setupCombat(6, 3, 50);

        resolveRound(cmd("fireball", "goblin1"), cmd("basic_attack", "mage"));

        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");

        // The fireball event is the first in the queue, so its damage_number is the first found.
        Map<String, Object> dmgNum = null;
        outer:
        for (Object evt : queue) {
            List<Object> anim = (List<Object>) ((Map<String, Object>) evt).get("animation");
            if (anim == null) continue;
            for (Object a : anim) {
                Map<String, Object> step = (Map<String, Object>) a;
                if ("damage_number".equals(step.get("type"))) { dmgNum = step; break outer; }
            }
        }
        assertThat(dmgNum).as("fireball event has a damage_number animation step").isNotNull();
        assertThat(dmgNum.get("value")).as("damage_number carries a numeric value").isEqualTo(-20);
    }

    // ── Bug 4: a fireball KILL plays the skill animation before the death event ─
    @Test
    @SuppressWarnings("unchecked")
    void fireballKill_skillAnimationPlaysBeforeDeath() {
        // goblin hp 20: fireball deals exactly 20 -> dies from the hit itself
        setupCombat(6, 3, 20);

        resolveRound(cmd("fireball", "goblin1"), cmd("basic_attack", "mage"));

        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");

        int skillIdx = -1, deathIdx = -1;
        for (int i = 0; i < queue.size(); i++) {
            Map<String, Object> evt = (Map<String, Object>) queue.get(i);
            List<Object> anim = (List<Object>) evt.get("animation");
            if (anim != null) {
                for (Object a : anim) {
                    if ("projectile".equals(((Map<String, Object>) a).get("type")) && skillIdx < 0) skillIdx = i;
                }
            }
            List<Object> effects = (List<Object>) evt.get("effects");
            if (effects != null) {
                for (Object e : effects) {
                    if ("death".equals(((Map<String, Object>) e).get("type")) && deathIdx < 0) deathIdx = i;
                }
            }
        }
        assertThat(skillIdx).as("fireball (projectile) event present").isGreaterThanOrEqualTo(0);
        assertThat(deathIdx).as("death event present").isGreaterThanOrEqualTo(0);
        assertThat(skillIdx).as("skill animation enqueued before the death event").isLessThan(deathIdx);
    }

    // ── Bug 6: an AOE that hits empty ground must not fabricate a NaN effect on caster ─
    @Test
    @SuppressWarnings("unchecked")
    void lightField_onEmptyGround_noNaNEffectOnCaster() {
        setupCombat(6, 3, 50);

        // light_field centered on a cell where no enemy stands (goblin is at rowIdx 0/slot 0)
        Map<String, Object> mageCmd = new HashMap<>();
        mageCmd.put("type", "light_field");
        mageCmd.put("targetRow", 2);  // -> center slot 2
        mageCmd.put("targetCol", 2);  // -> center rowIdx 2
        Map<String, Object> commands = new HashMap<>();
        commands.put("mage", mageCmd);   // goblin gets no command -> stays put
        GameEvent e = new GameEvent("combat.resolve_round");
        e.set("combatId", "battle1");
        e.set("commands", commands);
        bus.fire("combat.resolve_round", e);

        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");
        for (Object evtObj : queue) {
            List<Object> effects = (List<Object>) ((Map<String, Object>) evtObj).get("effects");
            if (effects == null) continue;
            for (Object eff : effects) {
                Map<String, Object> m = (Map<String, Object>) eff;
                Object amount = m.get("amount");
                assertThat(amount == null || !(amount instanceof Double && ((Double) amount).isNaN()))
                        .as("no effect carries a NaN amount").isTrue();
                if ("hp_change".equals(m.get("type"))) {
                    assertThat(m.get("target")).as("empty-ground AOE must not hit the caster").isNotEqualTo("mage");
                }
            }
        }
        // and the caster's HP is untouched
        assertThat(store.get("mage").getComponent("Health").getInt("hp")).isEqualTo(100);
    }

    // ── Bug 5: an unknown command type must not crash the round (missing skill YAML) ─
    @Test
    void unknownCommand_doesNotCrashRound() {
        setupCombat(6, 3, 50);

        // "ATTACK" has no skills/ATTACK.yaml — loadYaml now returns null and the round resolves.
        resolveRound(cmd("ATTACK", "goblin1"), cmd("basic_attack", "mage"));

        Entity combat = store.get("battle1");
        assertThat(combat.getComponent("CombatState").getString("phase"))
                .as("round still resolves past an unknown command").isNotEqualTo("RESOLVE");
    }

    // ── Bug 1b: a burning kill on the last enemy ends combat THIS round ─────────
    @Test
    void burningKill_endsCombatSameRound() {
        // goblin hp 22: fireball deals 20 -> 2, burning tick deals 3 -> dead this round
        setupCombat(6, 3, 22);

        resolveRound(cmd("fireball", "goblin1"), cmd("basic_attack", "mage"));

        Entity goblin = store.get("goblin1");
        Entity combat = store.get("battle1");
        assertThat(goblin.getComponent("Health").getInt("hp")).as("goblin killed by burn").isEqualTo(0);
        assertThat(combat.getComponent("CombatState").getString("phase"))
                .as("combat ends VICTORY the same round the burn kills").isEqualTo("VICTORY");
    }

    // ── Bug 2: defend is pre-emptive (protects vs a faster enemy) & expires the SAME round ─
    @Test
    @SuppressWarnings("unchecked")
    void defend_isPreemptive_protectsCastRound_andExpiresSameRound() {
        // goblin faster (speed 9 > mage 6) and hits hard (attack 15 vs mage defense 5 = 10; halved = 5)
        setupCombat(6, 9, 50);
        Entity mage = store.get("mage");
        store.get("goblin1").getComponent("CombatStats").set("attack", 15);

        // Round N: mage defends. Pre-emptive -> guard up before the faster goblin's hit -> 10 halved to 5.
        resolveRound(cmd("defend", null), cmd("basic_attack", "mage"));
        assertThat(mage.getComponent("Health").getInt("hp"))
                .as("cast round protected: 10 dmg halved to 5").isEqualTo(95);

        // 当回合失效: buff is gone by the time the round resolves, so it never reaches the
        // post-resolve snapshot (no lingering icon in the next command phase).
        assertThat(mage.hasComponent("Buff_defending"))
                .as("defending expires the same round it was cast").isFalse();

        // The cast-round buff_applied effect carries a buff descriptor so the icon can render
        // DURING this round's animation despite being absent from the snapshot.
        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");
        boolean carriesDescriptor = false;
        for (Object evtObj : queue) {
            List<Object> effects = (List<Object>) ((Map<String, Object>) evtObj).get("effects");
            if (effects == null) continue;
            for (Object e : effects) {
                Map<String, Object> m = (Map<String, Object>) e;
                if ("buff_applied".equals(m.get("type")) && m.get("buff") instanceof Map
                        && "defending".equals(((Map<String, Object>) m.get("buff")).get("id"))) {
                    carriesDescriptor = true;
                }
            }
        }
        assertThat(carriesDescriptor).as("buff_applied carries a defending descriptor for transient render").isTrue();
    }

    // ── data-driven debuff skills auto-carry a buff descriptor on buff_applied ─
    @Test
    @SuppressWarnings("unchecked")
    void dataDrivenDebuff_buffAppliedCarriesDescriptor() {
        setupCombat(6, 3, 50);

        // poison_dart is data-driven (damage_with_debuff -> poison). No bespoke handler involved.
        resolveRound(cmd("poison_dart", "goblin1"), cmd("basic_attack", "mage"));

        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");
        boolean carries = false;
        for (Object evtObj : queue) {
            List<Object> effects = (List<Object>) ((Map<String, Object>) evtObj).get("effects");
            if (effects == null) continue;
            for (Object e : effects) {
                Map<String, Object> m = (Map<String, Object>) e;
                if ("buff_applied".equals(m.get("type")) && m.get("buff") instanceof Map
                        && "poison".equals(((Map<String, Object>) m.get("buff")).get("id"))) {
                    carries = true;
                }
            }
        }
        assertThat(carries).as("present() auto-embeds the applied buff's descriptor for the frontend").isTrue();
    }

    // ── defend emits exactly one animation (no duplicate recorder) ─
    @Test
    @SuppressWarnings("unchecked")
    void defend_emitsSingleAnimation() {
        setupCombat(6, 3, 50);

        resolveRound(cmd("defend", null), cmd("basic_attack", "mage"));

        Entity combat = store.get("battle1");
        List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");
        int buffUpEvents = 0;
        for (Object evtObj : queue) {
            List<Object> anim = (List<Object>) ((Map<String, Object>) evtObj).get("animation");
            if (anim == null) continue;
            for (Object a : anim) {
                if ("buff_up".equals(((Map<String, Object>) a).get("type"))) { buffUpEvents++; break; }
            }
        }
        assertThat(buffUpEvents).as("exactly one defend buff_up animation (no duplicate recorder)").isEqualTo(1);
    }

    // ── Bug 3: leaving combat clears combat-scoped buffs off the player ─────────
    @Test
    void leavingCombat_clearsPlayerBuffs() {
        setupCombat(6, 3, 50);
        Entity mage = store.get("mage");
        buffsApplyDefending("mage");
        assertThat(mage.hasComponent("Buff_defending")).isTrue();

        GameEvent flee = new GameEvent("action.combat_command");
        flee.set("playerId", "mage");
        flee.set("command", "flee");
        bus.fire("action.combat_command", flee);

        assertThat(mage.hasComponent("Buff_defending"))
                .as("buffs cleared when combat ends, not carried into next encounter").isFalse();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void buffsApplyDefending(String id) {
        Map<String, Object> data = new HashMap<>();
        data.put("permanent", false);
        new BuffService(bus, store).applyBuff(id, "defending", data);
    }

    private Map<String, Object> cmd(String type, String targetId) {
        Map<String, Object> c = new HashMap<>();
        c.put("type", type);
        if (targetId != null) c.put("targetId", targetId);
        return c;
    }

    private void resolveRound(Map<String, Object> mageCmd, Map<String, Object> goblinCmd) {
        Map<String, Object> commands = new HashMap<>();
        commands.put("mage", mageCmd);
        commands.put("goblin1", goblinCmd);
        GameEvent e = new GameEvent("combat.resolve_round");
        e.set("combatId", "battle1");
        e.set("commands", commands);
        bus.fire("combat.resolve_round", e);
    }

    private void setupCombat(int mageSpeed, int goblinSpeed, int goblinHp) {
        Entity mage = new Entity("mage");
        Component mHealth = new Component("Health");
        mHealth.set("hp", 100); mHealth.set("maxHp", 100);
        mage.addComponent(mHealth);
        Component mStats = new Component("CombatStats");
        mStats.set("attack", 10); mStats.set("defense", 5); mStats.set("speed", mageSpeed);
        mage.addComponent(mStats);
        Component mName = new Component("Name");
        mName.set("value", "法师");
        mage.addComponent(mName);
        Component mPos = new Component("CombatPosition");
        mPos.set("row", "FRONT"); mPos.set("slot", 0);
        mage.addComponent(mPos);
        mage.addTag("player");
        mage.addTag("combat:battle1");
        store.add(mage);

        Entity goblin = new Entity("goblin1");
        Component gHealth = new Component("Health");
        gHealth.set("hp", goblinHp); gHealth.set("maxHp", goblinHp);
        goblin.addComponent(gHealth);
        Component gStats = new Component("CombatStats");
        gStats.set("attack", 6); gStats.set("defense", 2); gStats.set("speed", goblinSpeed);
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

        Entity combat = new Entity("battle1");
        Component combatState = new Component("CombatState");
        combatState.set("round", 1);
        combatState.set("phase", "COMMAND");
        combat.addComponent(combatState);
        combat.addComponent(new Component("CombatEvents"));
        combat.getComponent("CombatEvents").set("queue", new java.util.ArrayList<>());
        Component log = new Component("CombatLog");
        log.set("entries", new java.util.ArrayList<>());
        combat.addComponent(log);
        combat.addTag("active_combat");
        store.add(combat);
    }
}
