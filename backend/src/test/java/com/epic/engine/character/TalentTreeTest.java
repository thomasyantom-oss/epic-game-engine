package com.epic.engine.character;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.script.ScriptRuntime;
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

class TalentTreeTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
        typeReg.loadFromModPath(Path.of("../mods/base-rules"));
        ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
        runtime = new ScriptRuntime(bus, store, chainService, typeReg);
        runtime.setModuleContext(Path.of("../mods/base-rules"));
        Path skill = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(skill.resolve("00_skill_lib.js")), "00_skill_lib.js");
        runtime.execute(Files.readString(skill.resolve("04_passive_lib.js")), "04_passive_lib.js");
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownedSpecs_mergesKnownAndTalent() {
        Entity hero = heroWithPrimaryStats("hero");
        Component skillbook = new Component("Skillbook");
        skillbook.set("known", new ArrayList<>(List.of(known("lingering_burn", 2))));
        hero.addComponent(skillbook);
        store.add(hero);

        runtime.execute("""
            var Talent = {
              derivedPassives: function(entity) {
                return [{
                  id: 'talent_fb_damage',
                  kind: 'passive',
                  effect: 'skill_patch',
                  match: { skill: 'fireball' },
                  patch: { 'damage.add': 5 },
                  level: 1
                }];
              }
            };
            var specs = Passive.ownedSpecs(store.get('hero'));
            var probe = engine.newComponent('Probe');
            probe.set('count', specs.length);
            probe.set('knownSize', store.get('hero').getComponent('Skillbook').get('known').size());
            for (var i = 0; i < specs.length; i++) {
              if (specs[i].id === 'lingering_burn') {
                probe.set('knownEffect', specs[i].effect);
                probe.set('knownLevel', specs[i].level);
                probe.set('knownKind', specs[i].kind);
              }
              if (specs[i].id === 'talent_fb_damage') {
                probe.set('talentEffect', specs[i].effect);
                probe.set('talentSkill', specs[i].match.skill);
                probe.set('talentAdd', specs[i].patch['damage.add']);
                probe.set('talentLevel', specs[i].level);
                probe.set('talentKind', specs[i].kind);
              }
            }
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat(probe.getInt("count")).isEqualTo(2);
        assertThat(probe.getInt("knownSize")).isEqualTo(1);
        assertThat(probe.getString("knownEffect")).isEqualTo("skill_patch");
        assertThat(probe.getInt("knownLevel")).isEqualTo(2);
        assertThat(probe.getString("knownKind")).isEqualTo("passive");
        assertThat(probe.getString("talentEffect")).isEqualTo("skill_patch");
        assertThat(probe.getString("talentSkill")).isEqualTo("fireball");
        assertThat(probe.getInt("talentAdd")).isEqualTo(5);
        assertThat(probe.getInt("talentLevel")).isEqualTo(1);
        assertThat(probe.getString("talentKind")).isEqualTo("passive");
    }

    @Test
    void talentStatMod_removedAfterRespec() {
        Entity hero = heroWithPrimaryStats("hero");
        Component skillbook = new Component("Skillbook");
        skillbook.set("known", new ArrayList<>());
        hero.addComponent(skillbook);
        store.add(hero);

        runtime.execute("""
            var talentUnlocked = true;
            var Talent = {
              derivedPassives: function(entity) {
                if (!talentUnlocked) return [];
                return [{
                  id: 'talent_elem_root',
                  kind: 'passive',
                  effect: 'stat_mod',
                  modifiers: { '法强': 5 },
                  level: 1
                }];
              }
            };
            engine.setBase('hero');
            Passive.registerStatMods('hero');
            engine.recalculate('hero');
            var probe = engine.newComponent('Probe');
            probe.set('afterUnlock', store.get('hero').getComponent('PrimaryStats').getInt('法强'));
            talentUnlocked = false;
            Passive.registerStatMods('hero');
            engine.recalculate('hero');
            probe.set('afterRespec', store.get('hero').getComponent('PrimaryStats').getInt('法强'));
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat(probe.getInt("afterUnlock")).isEqualTo(5);
        assertThat(probe.getInt("afterRespec")).isEqualTo(0);
    }

    @Test
    void points_derivedFromLevel() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        Entity hero = heroWithPrimaryStats("hero");
        Component exp = new Component("Experience");
        exp.set("level", 12);
        hero.addComponent(exp);
        Component tree = new Component("TalentTree");
        tree.set("root", "elementalist");
        tree.set("unlocked", new ArrayList<>(List.of("elem_root")));
        hero.addComponent(tree);
        store.add(hero);

        runtime.execute("""
            var probe = engine.newComponent('Probe');
            probe.set('l9', Talent.totalPoints(9));
            probe.set('l10', Talent.totalPoints(10));
            probe.set('l12', Talent.totalPoints(12));
            probe.set('spent', Talent.spent(store.get('hero')));
            probe.set('available', Talent.available(store.get('hero')));
            store.get('hero').getComponent('TalentTree').get('unlocked').add('fireball_slot');
            store.get('hero').getComponent('TalentTree').get('unlocked').add('fb_damage');
            probe.set('overspentAvailable', Talent.available(store.get('hero')));
            var legacy = engine.createEntity('legacy');
            Talent.ensureComponents(legacy);
            probe.set('hasTree', legacy.hasComponent('TalentTree'));
            probe.set('hasOrbs', legacy.hasComponent('OrbPouch'));
            probe.set('legacyRootNull', legacy.getComponent('TalentTree').get('root') === null);
            probe.set('legacyUnlocked', legacy.getComponent('TalentTree').get('unlocked').size());
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat(probe.getInt("l9")).isEqualTo(0);
        assertThat(probe.getInt("l10")).isEqualTo(1);
        assertThat(probe.getInt("l12")).isEqualTo(2);
        assertThat(probe.getInt("spent")).isEqualTo(1);
        assertThat(probe.getInt("available")).isEqualTo(1);
        assertThat(probe.getInt("overspentAvailable")).isEqualTo(0);
        assertThat((Boolean) probe.get("hasTree")).isTrue();
        assertThat((Boolean) probe.get("hasOrbs")).isTrue();
        assertThat((Boolean) probe.get("legacyRootNull")).isTrue();
        assertThat(probe.getInt("legacyUnlocked")).isEqualTo(0);
    }

    @Test
    void applyNode_appliesEvolutionPatch() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        runtime.execute("""
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            var evolved = Skill.applyNode(JSON.parse(JSON.stringify(raw)), 'pyroblast');
            var plain = Skill.applyNode(JSON.parse(JSON.stringify(raw)), null);
            var probe = engine.newComponent('Probe');
            probe.set('plainSame', JSON.stringify(raw) === JSON.stringify(plain));
            probe.set('name', evolved.name);
            probe.set('damageAdd', evolved.damage.add);
            probe.set('pattern', JSON.stringify(evolved.targeting.pattern));
            var e = engine.createEntity('probe');
            e.addComponent(probe);
            store.add(e);
            """, "probe.js");

        Component probe = store.get("probe").getComponent("Probe");
        assertThat((Boolean) probe.get("plainSame")).isTrue();
        assertThat(probe.getString("name")).isEqualTo("炎爆");
        assertThat(probe.getInt("damageAdd")).isEqualTo(18);
        assertThat(probe.getString("pattern")).isEqualTo("[[0,0],[0,1],[0,-1]]");
    }

    @Test
    void derivedTalentPassives_supportStatOverrideSkillPatchAndHandler() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/specialization.js")), "specialization.js");
        Entity hero = talentMage("hero", 50, List.of("elem_root"));
        store.add(hero);

        runtime.execute("""
            engine.setBase('hero');
            applySpec('hero');
            var hero = store.get('hero');
            var probe = engine.newComponent('Probe');
            var first = Talent.derivedPassives(hero);
            probe.set('rootId', first[0].id);
            probe.set('rootEffect', first[0].effect);
            probe.set('rootMod', first[0].modifiers['法强']);
            probe.set('afterRootPower', hero.getComponent('PrimaryStats').getInt('法强'));
            hero.getComponent('Specialization').get('path').add('pyromancer');
            applySpec('hero');
            var overridden = Talent.derivedPassives(hero);
            probe.set('overrideFire', overridden[0].modifiers['火属性']);
            probe.set('afterOverridePower', hero.getComponent('PrimaryStats').getInt('法强'));
            probe.set('afterOverrideFire', hero.getComponent('PrimaryStats').getInt('火属性'));
            var unlocked = hero.getComponent('TalentTree').get('unlocked');
            unlocked.add('fb_damage');
            unlocked.add('ember_passive');
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'fireball', raw);
            probe.set('patchedDamage', out.damage.add);
            probe.set('ownsHandler', Passive.owns('hero', 'lifesteal_on_kill'));
            hero.addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat(probe.getString("rootId")).isEqualTo("talent_elem_root");
        assertThat(probe.getString("rootEffect")).isEqualTo("stat_mod");
        assertThat(probe.getInt("rootMod")).isEqualTo(5);
        assertThat(probe.getInt("afterRootPower")).isEqualTo(5);
        assertThat(probe.getInt("overrideFire")).isEqualTo(8);
        assertThat(probe.getInt("afterOverridePower")).isEqualTo(0);
        assertThat(probe.getInt("afterOverrideFire")).isEqualTo(8);
        assertThat(probe.getInt("patchedDamage")).isEqualTo(15);
        assertThat((Boolean) probe.get("ownsHandler")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void actions_validateUnlockPlaceOrbAndRespec() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/specialization.js")), "specialization.js");

        Entity unspec = talentMage("unspec", 10, List.of());
        ((List<String>) unspec.getComponent("Specialization").get("path")).clear();
        store.add(unspec);
        Entity hero = talentMage("hero", 10, List.of());
        hero.getComponent("Skillbook").set("known", new ArrayList<>(List.of(known("fireball", 1))));
        store.add(hero);
        Entity noSkill = talentMage("noskill", 12, List.of("elem_root", "fireball_slot"));
        store.add(noSkill);

        runtime.execute("""
            function action(type, playerId, nodeId) {
              var ev = engine.newEvent('action.' + type);
              ev.set('playerId', playerId);
              if (nodeId !== undefined && nodeId !== null) ev.set('nodeId', nodeId);
              engine.fire('action.' + type, ev);
              return ev;
            }
            function safeInt(comp, key) {
              return comp !== null && comp.has(key) ? comp.getInt(key) : -999;
            }
            engine.setBase('hero');
            engine.setBase('noskill');
            var probe = engine.newComponent('Probe');
            var unspecReject = action('talent_unlock', 'unspec', 'elem_root');
            probe.set('unspecRejected', unspecReject.get('success') === false);
            var disconnected = action('talent_unlock', 'hero', 'fb_damage');
            probe.set('disconnectedRejected', disconnected.get('success') === false);
            var root = action('talent_unlock', 'hero', 'elem_root');
            probe.set('rootSuccess', root.get('success') === true);
            probe.set('unlockedAfterRoot', store.get('hero').getComponent('TalentTree').get('unlocked').size());
            probe.set('afterRootPower', store.get('hero').getComponent('PrimaryStats').getInt('法强'));
            var noPoints = action('talent_unlock', 'hero', 'fireball_slot');
            probe.set('noPointsRejected', noPoints.get('success') === false);
            var req = action('talent_unlock', 'hero', 'ember_passive');
            probe.set('requiresRejected', req.get('success') === false);
            store.get('hero').getComponent('Experience').set('level', 9);
            var spent = action('talent_unlock', 'hero', 'fireball_slot');
            probe.set('spentRejected', spent.get('success') === false);
            store.get('hero').getComponent('Experience').set('level', 12);
            var slot = action('talent_unlock', 'hero', 'fireball_slot');
            probe.set('slotSuccess', slot.get('success') === true);
            var noSkillOrb = action('talent_place_orb', 'noskill', 'fireball_slot');
            probe.set('noSkillRejected', noSkillOrb.get('success') === false);
            probe.set('noSkillKnownSize', store.get('noskill').getComponent('Skillbook').get('known').size());
            var noOrb = action('talent_place_orb', 'hero', 'fireball_slot');
            probe.set('noOrbRejected', noOrb.get('success') === false);
            store.get('hero').getComponent('OrbPouch').set('爆破', 2);
            var placed = action('talent_place_orb', 'hero', 'fireball_slot');
            probe.set('placeSuccess', placed.get('success') === true);
            probe.set('orbLeft', safeInt(store.get('hero').getComponent('OrbPouch'), '爆破'));
            var treeAfterPlace = store.get('hero').getComponent('TalentTree');
            probe.set('nodeAfterPlace', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node'));
            probe.set('evolvedAfterPlace', treeAfterPlace.get('evolved').contains('pyroblast'));
            var respec = action('talent_respec', 'hero', null);
            probe.set('respecSuccess', respec.get('success') === true);
            probe.set('unlockedAfterRespec', store.get('hero').getComponent('TalentTree').get('unlocked').size());
            probe.set('nodeClearedAfterRespec', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node') === null);
            probe.set('evolvedAfterRespec', store.get('hero').getComponent('TalentTree').get('evolved').contains('pyroblast'));
            probe.set('powerAfterRespec', safeInt(store.get('hero').getComponent('PrimaryStats'), '法强'));
            store.get('hero').getComponent('Experience').set('level', 12);
            store.get('hero').getComponent('Character').set('level', 12);
            engine.setBase('hero');
            var rootAgain = action('talent_unlock', 'hero', 'elem_root');
            var orbBeforeRestore = safeInt(store.get('hero').getComponent('OrbPouch'), '爆破');
            var slotAgain = action('talent_unlock', 'hero', 'fireball_slot');
            probe.set('restoreRootSuccess', rootAgain.get('success') === true);
            probe.set('restoreSlotSuccess', slotAgain.get('success') === true);
            probe.set('nodeAfterRestore', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node'));
            probe.set('orbAfterRestore', safeInt(store.get('hero').getComponent('OrbPouch'), '爆破'));
            probe.set('restoreDidNotSpendOrb', orbBeforeRestore === safeInt(store.get('hero').getComponent('OrbPouch'), '爆破'));
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat((Boolean) probe.get("unspecRejected")).isTrue();
        assertThat((Boolean) probe.get("disconnectedRejected")).isTrue();
        assertThat((Boolean) probe.get("rootSuccess")).isTrue();
        assertThat(probe.getInt("unlockedAfterRoot")).isEqualTo(1);
        assertThat(probe.getInt("afterRootPower")).isEqualTo(5);
        assertThat((Boolean) probe.get("noPointsRejected")).isTrue();
        assertThat((Boolean) probe.get("requiresRejected")).isTrue();
        assertThat((Boolean) probe.get("spentRejected")).isTrue();
        assertThat((Boolean) probe.get("slotSuccess")).isTrue();
        assertThat((Boolean) probe.get("noSkillRejected")).isTrue();
        assertThat(probe.getInt("noSkillKnownSize")).isEqualTo(0);
        assertThat((Boolean) probe.get("noOrbRejected")).isTrue();
        assertThat((Boolean) probe.get("placeSuccess")).isTrue();
        assertThat(probe.getInt("orbLeft")).isEqualTo(0);
        assertThat(probe.getString("nodeAfterPlace")).isEqualTo("pyroblast");
        assertThat((Boolean) probe.get("evolvedAfterPlace")).isTrue();
        assertThat((Boolean) probe.get("respecSuccess")).isTrue();
        assertThat(probe.getInt("unlockedAfterRespec")).isEqualTo(0);
        assertThat((Boolean) probe.get("nodeClearedAfterRespec")).isTrue();
        assertThat((Boolean) probe.get("evolvedAfterRespec")).isTrue();
        assertThat(probe.getInt("powerAfterRespec")).isEqualTo(0);
        assertThat((Boolean) probe.get("restoreRootSuccess")).isTrue();
        assertThat((Boolean) probe.get("restoreSlotSuccess")).isTrue();
        assertThat(probe.getString("nodeAfterRestore")).isEqualTo("pyroblast");
        assertThat((Boolean) probe.get("restoreDidNotSpendOrb")).isTrue();
    }

    @Test
    void respecDisablesEvolutionUntilSlotIsUnlockedAgain() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        Entity hero = talentMage("hero", 12, List.of("elem_root", "fireball_slot"));
        hero.getComponent("Skillbook").set("known", new ArrayList<>(List.of(known("fireball", 1))));
        hero.getComponent("OrbPouch").set("爆破", 2);
        store.add(hero);

        runtime.execute("""
            function action(type, playerId, nodeId) {
              var ev = engine.newEvent('action.' + type);
              ev.set('playerId', playerId);
              if (nodeId !== undefined && nodeId !== null) ev.set('nodeId', nodeId);
              engine.fire('action.' + type, ev);
              return ev;
            }
            function fireballPattern() {
              var hero = store.get('hero');
              var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
              var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'fireball', raw);
              return JSON.stringify(out.targeting.pattern);
            }
            engine.setBase('hero');
            var probe = engine.newComponent('Probe');
            action('talent_place_orb', 'hero', 'fireball_slot');
            probe.set('afterPlaceNode', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node'));
            probe.set('afterPlacePattern', fireballPattern());
            action('talent_respec', 'hero', null);
            probe.set('afterRespecNodeNull', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node') === null);
            probe.set('afterRespecPattern', fireballPattern());
            action('talent_unlock', 'hero', 'elem_root');
            action('talent_unlock', 'hero', 'fireball_slot');
            probe.set('afterRestoreNode', store.get('hero').getComponent('Skillbook').get('known').get(0).get('node'));
            probe.set('afterRestorePattern', fireballPattern());
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat(probe.getString("afterPlaceNode")).isEqualTo("pyroblast");
        assertThat(probe.getString("afterPlacePattern")).isEqualTo("[[0,0],[0,1],[0,-1]]");
        assertThat((Boolean) probe.get("afterRespecNodeNull")).isTrue();
        assertThat(probe.getString("afterRespecPattern")).isEqualTo("[[0,0]]");
        assertThat(probe.getString("afterRestoreNode")).isEqualTo("pyroblast");
        assertThat(probe.getString("afterRestorePattern")).isEqualTo("[[0,0],[0,1],[0,-1]]");
    }

    @Test
    void debugGrantOrb_addsTypedOrbCount() throws Exception {
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/talent.js")), "talent.js");
        Entity hero = heroWithPrimaryStats("hero");
        store.add(hero);

        runtime.execute("""
            var first = engine.newEvent('debug.grant_orb');
            first.set('entityId', 'hero');
            first.set('type', '爆破');
            first.set('count', 2);
            engine.fire('debug.grant_orb', first);
            var second = engine.newEvent('debug.grant_orb');
            second.set('entityId', 'hero');
            second.set('type', '爆破');
            second.set('count', 3);
            engine.fire('debug.grant_orb', second);
            var probe = engine.newComponent('Probe');
            probe.set('firstOk', first.get('ok') === true);
            probe.set('secondOk', second.get('ok') === true);
            probe.set('count', store.get('hero').getComponent('OrbPouch').getInt('爆破'));
            store.get('hero').addComponent(probe);
            """, "probe.js");

        Component probe = hero.getComponent("Probe");
        assertThat((Boolean) probe.get("firstOk")).isTrue();
        assertThat((Boolean) probe.get("secondOk")).isTrue();
        assertThat(probe.getInt("count")).isEqualTo(5);
    }

    private Entity heroWithPrimaryStats(String id) {
        Entity hero = new Entity(id);
        Component primary = new Component("PrimaryStats");
        primary.set("法强", 0);
        primary.set("火属性", 0);
        hero.addComponent(primary);
        return hero;
    }

    private Entity talentMage(String id, int level, List<String> unlockedIds) {
        Entity hero = heroWithPrimaryStats(id);
        Component character = new Component("Character");
        character.set("classId", "mage");
        character.set("level", level);
        hero.addComponent(character);
        Component exp = new Component("Experience");
        exp.set("level", level);
        hero.addComponent(exp);
        Component skillbook = new Component("Skillbook");
        skillbook.set("known", new ArrayList<>());
        hero.addComponent(skillbook);
        Component spec = new Component("Specialization");
        spec.set("path", new ArrayList<>(List.of("elementalist")));
        hero.addComponent(spec);
        Component tree = new Component("TalentTree");
        tree.set("root", "elementalist");
        tree.set("unlocked", new ArrayList<>(unlockedIds));
        hero.addComponent(tree);
        Component pouch = new Component("OrbPouch");
        hero.addComponent(pouch);
        return hero;
    }

    private Map<String, Object> known(String base, int level) {
        Map<String, Object> item = new HashMap<>();
        item.put("base", base);
        item.put("node", null);
        item.put("level", level);
        return item;
    }
}
