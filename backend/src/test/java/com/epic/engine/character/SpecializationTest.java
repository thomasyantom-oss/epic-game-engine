package com.epic.engine.character;

import com.epic.engine.core.Component;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
import com.epic.engine.module.SchemaRegistry;
import com.epic.engine.persistence.PersistenceService;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.session.SessionService;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpecializationTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;
    @Autowired EntityStore entityStore;
    @Autowired EventBus eventBus;
    @Autowired PersistenceService persistence;
    @Autowired ModifierChainService modifierChainService;

    HttpHeaders headers;
    String token;

    @BeforeEach
    void setUp() {
        token = sessionService.createSession();
        headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMage_addsEmptySpecialization_andKeepsBaseline() {
        Entity mage = createMage("spec-create");

        assertThat(mage.hasComponent("Specialization")).isTrue();
        assertThat((List<Object>) mage.getComponent("Specialization").get("path")).isEmpty();
        assertThat(mage.getComponent("PrimaryStats").getInt("智力")).isEqualTo(14);
    }

    @Test
    @SuppressWarnings("unchecked")
    void arcanist_retroactivelyReplacesGrowthAndRaisesMaxHp() {
        Entity mage = createMage("spec-arcanist");
        setLevel(mage, 10);
        int hpBefore = mage.getComponent("Health").getInt("maxHp");

        GameEvent choose = choose(mage.getId(), "arcanist");

        assertThat(choose.has("success")).isFalse();
        Component primary = mage.getComponent("PrimaryStats");
        assertThat(primary.getInt("智力")).isEqualTo(14 + 4 * 9);
        assertThat(primary.getInt("敏捷")).isEqualTo(5);
        assertThat(primary.getInt("体质")).isEqualTo(7 + 1 * 9);
        assertThat(mage.getComponent("Health").getInt("maxHp")).isGreaterThan(hpBefore);
        assertThat(mage.getComponent("Health").getInt("hp"))
                .isLessThanOrEqualTo(mage.getComponent("Health").getInt("maxHp"));
        assertThat((List<String>) mage.getComponent("Specialization").get("path"))
                .containsExactly("arcanist");
    }

    @Test
    void elementalistKeepsClassDefaultGrowth() {
        Entity mage = createMage("spec-elementalist");
        setLevel(mage, 10);

        choose(mage.getId(), "elementalist");

        Component primary = mage.getComponent("PrimaryStats");
        assertThat(primary.getInt("智力")).isEqualTo(14 + 3 * 9);
        assertThat(primary.getInt("敏捷")).isEqualTo(5 + 1 * 9);
        assertThat(primary.getInt("体质")).isEqualTo(7 + 1 * 9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pyromancerGrantsPassiveOnce_andPathPersists() {
        Entity mage = createMage("spec-pyro");
        setLevel(mage, 50);
        choose(mage.getId(), "elementalist");

        choose(mage.getId(), "pyromancer");
        choose(mage.getId(), "pyromancer");
        fireLoaded(mage);

        List<String> path = (List<String>) mage.getComponent("Specialization").get("path");
        assertThat(path).containsExactly("elementalist", "pyromancer");

        List<Map<String, Object>> known = (List<Map<String, Object>>) mage.getComponent("Skillbook").get("known");
        assertThat(known.stream().filter(k -> "lifesteal_on_kill".equals(String.valueOf(k.get("base")))).count())
                .isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidChoicesWithoutChangingState() {
        Entity mage = createMage("spec-rejects");
        setLevel(mage, 10);

        GameEvent tooEarly = choose(mage.getId(), "pyromancer");
        assertRejected(tooEarly);
        assertThat((List<String>) mage.getComponent("Specialization").get("path")).isEmpty();

        choose(mage.getId(), "arcanist");
        List<String> afterRoot = new ArrayList<>((List<String>) mage.getComponent("Specialization").get("path"));

        GameEvent wrongParent = choose(mage.getId(), "pyromancer");
        assertRejected(wrongParent);
        assertThat((List<String>) mage.getComponent("Specialization").get("path")).isEqualTo(afterRoot);

        GameEvent sameTier = choose(mage.getId(), "naturalist");
        assertRejected(sameTier);
        assertThat((List<String>) mage.getComponent("Specialization").get("path")).isEqualTo(afterRoot);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectedActionResultReachesSnapshot() {
        Entity mage = createMage("spec-result");
        setLevel(mage, 10);

        ResponseEntity<Map> response = rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "choose_specialization",
                        "params", Map.of("specId", "pyromancer")), headers), Map.class);

        Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(result.get("message"))).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reloadRoundTrip_doesNotInflateSpecializedOrUnspecializedGrowth() {
        Entity specialized = createMage("spec-reload-a");
        setLevel(specialized, 10);
        choose(specialized.getId(), "arcanist");
        int intBefore = specialized.getComponent("PrimaryStats").getInt("智力");
        int hpBefore = specialized.getComponent("Health").getInt("maxHp");
        persistence.save(specialized);

        entityStore.remove(specialized.getId());
        modifierChainService.clearChain(specialized.getId());
        Entity reloaded = persistence.load(specialized.getId());
        entityStore.add(reloaded);
        fireLoaded(reloaded);
        assertThat(reloaded.getComponent("PrimaryStats").getInt("智力")).isEqualTo(intBefore);
        assertThat(reloaded.getComponent("Health").getInt("maxHp")).isEqualTo(hpBefore);
        assertThat((List<String>) reloaded.getComponent("Specialization").get("path")).containsExactly("arcanist");

        Entity plain = createMage("spec-reload-b");
        setLevel(plain, 10);
        fireLoaded(plain);
        int plainInt = plain.getComponent("PrimaryStats").getInt("智力");
        int plainAgi = plain.getComponent("PrimaryStats").getInt("敏捷");
        persistence.save(plain);
        entityStore.remove(plain.getId());
        modifierChainService.clearChain(plain.getId());
        Entity plainReloaded = persistence.load(plain.getId());
        entityStore.add(plainReloaded);
        fireLoaded(plainReloaded);
        assertThat(plainReloaded.getComponent("PrimaryStats").getInt("智力")).isEqualTo(plainInt);
        assertThat(plainReloaded.getComponent("PrimaryStats").getInt("敏捷")).isEqualTo(plainAgi);
        assertThat((List<String>) plainReloaded.getComponent("Specialization").get("path")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void oldSaveWithoutSpecializationGetsEmptyFallbackOnLoad() {
        Entity mage = createMage("spec-old-save");
        mage.removeComponent("Specialization");

        fireLoaded(mage);

        assertThat(mage.hasComponent("Specialization")).isTrue();
        assertThat((List<String>) mage.getComponent("Specialization").get("path")).isEmpty();
        assertThat(mage.getComponent("PrimaryStats").getInt("智力")).isEqualTo(14);
    }

    @Test
    void allocatePointStaysDormantEvenWithPendingPoints() {
        Entity mage = createMage("spec-alloc");
        setLevel(mage, 10);
        choose(mage.getId(), "arcanist");
        int intBefore = mage.getComponent("PrimaryStats").getInt("智力");
        mage.getComponent("Experience").set("pendingPoints", 1);

        GameEvent event = new GameEvent("action.allocate_point");
        event.set("playerId", mage.getId());
        event.set("stat", "maxHp");
        eventBus.fire("action.allocate_point", event);

        assertThat(mage.getComponent("Experience").getInt("pendingPoints")).isEqualTo(1);
        assertThat(mage.getComponent("PrimaryStats").getInt("智力")).isEqualTo(intBefore);
        assertThat(event.has("success")).isTrue();
        assertThat((Object) event.get("success")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void mainAttrSpecModifierRunsBeforeDerived_withoutChangingWeaponMetadata() throws Exception {
        try (SpecializationHarness h = new SpecializationHarness()) {
            h.execute("""
                var hero = testMage('hero');
                var slots = hero.getComponent('EquipmentSlots');
                slots.set('weapon', 'staff');
                engine.updateBase('hero', 'EquipmentSlots');
                registerEquipmentModifier('hero', 'staff');
                Specialization._trees['mage'] = { nodes: [
                  { id: 'body_mage', label: '体术法师', tier: 1, requires_level: 1, parent: null, main_attr: '体质' }
                ] };
                hero.getComponent('Specialization').get('path').add('body_mage');
                applySpec('hero');
                var weapon = store.get('staff').getComponent('ItemStats');
                var probe = engine.newComponent('Probe');
                probe.set('weaponAttr', hero.getComponent('PrimaryStats').getString('weaponAttr'));
                probe.set('phys', hero.getComponent('DerivedStats').getInt('物理强度'));
                probe.set('weaponMetaAttr', weapon.getString('weaponAttr'));
                probe.set('weaponBase', weapon.getInt('base'));
                hero.addComponent(probe);
                """);

            Entity hero = h.store.get("hero");
            Component probe = hero.getComponent("Probe");
            assertThat(probe.getString("weaponAttr")).isEqualTo("体质");
            assertThat(probe.getInt("phys")).isEqualTo(hero.getComponent("PrimaryStats").getInt("体质"));
            assertThat(probe.getString("weaponMetaAttr")).isEqualTo("智力");
            assertThat(probe.getInt("weaponBase")).isEqualTo(5);
        }
    }

    private Entity createMage(String name) {
        ResponseEntity<Map> response = rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "confirm_character",
                        "params", Map.of("name", name, "class", "mage")), headers), Map.class);
        String playerId = String.valueOf(response.getBody().get("playerId"));
        return entityStore.get(playerId);
    }

    private void setLevel(Entity entity, int level) {
        entity.getComponent("Experience").set("level", level);
        entity.getComponent("Character").set("level", level);
    }

    private GameEvent choose(String playerId, String specId) {
        GameEvent event = new GameEvent("action.choose_specialization");
        event.set("playerId", playerId);
        event.set("specId", specId);
        eventBus.fire("action.choose_specialization", event);
        return event;
    }

    private void fireLoaded(Entity entity) {
        GameEvent event = new GameEvent("entity.loaded");
        event.set("entity", entity);
        eventBus.fire("entity.loaded", event);
    }

    private void assertRejected(GameEvent event) {
        assertThat(event.has("success")).isTrue();
        assertThat((Object) event.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf((Object) event.get("message"))).isNotBlank();
    }

    static class SpecializationHarness implements AutoCloseable {
        final EventBus bus = new EventBus();
        final EntityStore store = new EntityStore();
        final ScriptRuntime runtime;

        SpecializationHarness() throws Exception {
            ModifierTypeRegistry typeReg = new ModifierTypeRegistry();
            typeReg.loadFromModPath(Path.of("../mods/base-rules"));
            ModifierChainService chainService = new ModifierChainService(bus, store, typeReg);
            runtime = new ScriptRuntime(bus, store, chainService, typeReg);
            runtime.setModuleContext(Path.of("../mods/base-rules"));
            SchemaRegistry schemas = new SchemaRegistry();
            schemas.loadFromModPath(Path.of("../mods/base-rules"));
            runtime.bindService("schemas", schemas);
            runtime.bindService("persistence", new StubPersistence());
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/equipment/equip.js")), "equip.js");
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/derived_stats.js")), "derived_stats.js");
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/00_skill_lib.js")), "00_skill_lib.js");
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/skill/04_passive_lib.js")), "04_passive_lib.js");
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/leveling.js")), "leveling.js");
            runtime.execute(Files.readString(Path.of("../mods/base-rules/handlers/character/specialization.js")), "specialization.js");
            execute("var ev = engine.newEvent('world.init'); engine.fire('world.init', ev);");
            execute("""
                testMage = function(id) {
                  var e = engine.createEntity(id);
                  var p = engine.newComponent('PrimaryStats');
                  p.set('力量', 3); p.set('敏捷', 5); p.set('智力', 14); p.set('体质', 7); p.set('意志', 6); p.set('weaponAttr', '智力'); e.addComponent(p);
                  var d = engine.newComponent('DerivedStats'); e.addComponent(d);
                  var h = engine.newComponent('Health'); h.set('hp', 30); h.set('maxHp', 30); e.addComponent(h);
                  var m = engine.newComponent('Mana'); m.set('mp', 50); m.set('maxMp', 50); e.addComponent(m);
                  var c = engine.newComponent('CombatStats'); c.set('attack', 6); c.set('defense', 3); c.set('speed', 5); e.addComponent(c);
                  var ch = engine.newComponent('Character'); ch.set('name', id); ch.set('classId', 'mage'); ch.set('level', 1); e.addComponent(ch);
                  var exp = engine.newComponent('Experience'); exp.set('xp', 0); exp.set('level', 1); exp.set('pendingPoints', 0); e.addComponent(exp);
                  var slots = engine.newComponent('EquipmentSlots'); slots.set('weapon', null); slots.set('armor', null); slots.set('accessory', null); e.addComponent(slots);
                  var sb = engine.newComponent('Skillbook'); sb.set('known', engine.newList()); e.addComponent(sb);
                  var spec = engine.newComponent('Specialization'); spec.set('path', engine.newList()); e.addComponent(spec);
                  store.add(e); engine.setBase(id); registerDerivedModifier(id); applySpec(id); return e;
                };
                """);
        }

        void execute(String script) {
            runtime.execute("(function(){ " + script + " })();", "probe.js");
        }

        @Override
        public void close() {
            runtime.close();
        }
    }

    public static class StubPersistence {
        @HostAccess.Export public void save(Object entity) {}
    }
}
