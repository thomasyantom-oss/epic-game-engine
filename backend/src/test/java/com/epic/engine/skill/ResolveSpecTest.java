package com.epic.engine.skill;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveSpecTest {

    ScriptRuntime rt;
    EntityStore store;

    ScriptRuntime newRuntime() {
        EventBus bus = new EventBus();
        store = new EntityStore();
        rt = new ScriptRuntime(bus, store);
        rt.setModuleContext(Path.of("../mods/base-rules"));
        return rt;
    }

    ScriptRuntime withSkillLib() throws Exception {
        ScriptRuntime runtime = newRuntime();
        Path d = Path.of("../mods/base-rules/handlers/skill");
        runtime.execute(Files.readString(d.resolve("00_skill_lib.js")), "00_skill_lib.js");
        return runtime;
    }

    @AfterEach
    void tearDown() {
        if (rt != null) rt.close();
    }

    @Test
    void passiveYaml_loadable() {
        ScriptRuntime runtime = newRuntime();
        runtime.execute("""
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', engine.loadYaml('passives/iron_skin.yaml') !== null);
            e.addComponent(c);
            store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void resolveSpec_passthrough_whenNoSkillbook() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute("""
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            var ctx = { caster: null, actorId: 'ghost' };
            var out = Skill.resolveSpec(ctx, 'fireball', raw);
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', JSON.stringify(out) === JSON.stringify(raw));
            e.addComponent(c);
            store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void skillPatchPassive_extendsBurn() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute("""
            var hero = engine.createEntity('hero');
            var sb = engine.newComponent('Skillbook');
            var known = engine.newList();
            var p = engine.newMap(); p.put('base','lingering_burn'); p.put('node',null); p.put('level',1); known.add(p);
            sb.set('known', known); hero.addComponent(sb); store.add(hero);
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'fireball', raw);
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', out.debuff.data.remaining === 3);
            e.addComponent(c); store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void skillPatchPassive_canPatchBespokeCleaveButLingeringBurnDoesNot() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute("""
            Skill._specs['passive:cleave_patch_probe'] = {
              id:'cleave_patch_probe', kind:'passive', effect:'skill_patch',
              match:{ skill:'cleave' }, patch:{ 'damage.scaling.力量': 0.2 }
            };
            var hero = engine.createEntity('hero');
            var sb = engine.newComponent('Skillbook');
            var known = engine.newList();
            var burn = engine.newMap(); burn.put('base','lingering_burn'); burn.put('node',null); burn.put('level',1); known.add(burn);
            var cleave = engine.newMap(); cleave.put('base','cleave_patch_probe'); cleave.put('node',null); cleave.put('level',1); known.add(cleave);
            sb.set('known', known); hero.addComponent(sb); store.add(hero);
            var raw = Skill._toJs(engine.loadYaml('skills/cleave.yaml'));
            var baseScale = raw.damage.scaling['力量'];
            var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'cleave', raw);
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', out.damage.scaling['力量'] === baseScale + 0.2 && out.debuff === raw.debuff);
            e.addComponent(c); store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void specializationPatchSeam_noOpsForDemoPathAndMissingComponent() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/specialization.js")), "specialization.js");
        runtime.execute("""
            var hero = engine.createEntity('hero');
            var ch = engine.newComponent('Character'); ch.set('classId', 'mage'); hero.addComponent(ch);
            var specComp = engine.newComponent('Specialization');
            var path = engine.newList(); path.add('elementalist'); specComp.set('path', path); hero.addComponent(specComp);
            store.add(hero);
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'fireball', raw);
            var monster = engine.createEntity('monster'); store.add(monster);
            var out2 = Skill.resolveSpec({ caster: monster, actorId: 'monster' }, 'fireball', raw);
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', JSON.stringify(out) === JSON.stringify(raw) && JSON.stringify(out2) === JSON.stringify(raw));
            e.addComponent(c); store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void levelScaling_addsConfiguredPerLevelDelta() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute("""
            var hero = engine.createEntity('hero');
            var sb = engine.newComponent('Skillbook');
            var known = engine.newList();
            var fire = engine.newMap(); fire.put('base','fireball'); fire.put('node',null); fire.put('level',3); known.add(fire);
            sb.set('known', known); hero.addComponent(sb); store.add(hero);
            var raw = Skill._toJs(engine.loadYaml('skills/fireball.yaml'));
            raw.level_scaling = { 'damage.add': 2 };
            var baseAdd = raw.damage.add;
            var out = Skill.resolveSpec({ caster: hero, actorId: 'hero' }, 'fireball', raw);
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', out.damage.add === baseAdd + 4);
            e.addComponent(c); store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }

    @Test
    void skillLevelSeam_isNoOpForNow() throws Exception {
        ScriptRuntime runtime = withSkillLib();
        runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/recalculate_hooks.js")), "recalculate_hooks.js");
        runtime.execute("""
            var e = engine.createEntity('probe');
            var c = engine.newComponent('Probe');
            c.set('ok', typeof applySkillLevelCurve === 'function');
            e.addComponent(c); store.add(e);
            """, "probe.js");
        assertThat(Boolean.TRUE.equals(store.get("probe").getComponent("Probe").get("ok"))).isTrue();
    }
}
