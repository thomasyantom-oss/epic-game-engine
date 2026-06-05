package com.epic.engine.snapshot;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.session.SessionService;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpecializationSnapshotTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;
    @Autowired EntityStore entityStore;
    @Autowired EventBus eventBus;

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
    void level10Unspecialized_snapshotShowsTier1OptionsWithEffects() {
        Entity mage = createMage("spec-snap-pending");
        setLevel(mage, 10);

        Map<String, Object> specialization = specializationSnapshot();
        assertThat((List<Map<String, Object>>) specialization.get("path")).isEmpty();
        Map<String, Object> pending = (Map<String, Object>) specialization.get("pending");
        assertThat(((Number) pending.get("tier")).intValue()).isEqualTo(1);
        assertThat(((Number) pending.get("requiresLevel")).intValue()).isEqualTo(10);

        List<Map<String, Object>> options = (List<Map<String, Object>>) pending.get("options");
        assertThat(options).extracting(o -> String.valueOf(o.get("id")))
                .containsExactlyInAnyOrder("elementalist", "arcanist", "naturalist");

        Map<String, Object> arcanist = options.stream()
                .filter(o -> "arcanist".equals(o.get("id")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> effects = (Map<String, Object>) arcanist.get("effects");
        assertThat(effects.get("mainAttr")).isNull();
        assertThat((Map<String, Object>) effects.get("growth"))
                .containsEntry("智力", 4)
                .containsEntry("体质", 1);
        assertThat((List<String>) effects.get("grantPassives")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void elementalistAtLevel10_snapshotShowsLockedTier2() {
        Entity mage = createMage("spec-snap-locked");
        setLevel(mage, 10);
        choose("elementalist");

        Map<String, Object> specialization = specializationSnapshot();
        assertThat((List<Map<String, Object>>) specialization.get("path"))
                .extracting(p -> String.valueOf(p.get("id")))
                .containsExactly("elementalist");
        assertThat(specialization.get("pending")).isNull();
        assertThat((List<Map<String, Object>>) specialization.get("locked"))
                .singleElement()
                .satisfies(locked -> {
                    assertThat(((Number) locked.get("tier")).intValue()).isEqualTo(2);
                    assertThat(((Number) locked.get("requiresLevel")).intValue()).isEqualTo(50);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void pyromancerAtLevel50_snapshotShowsTerminalState() {
        Entity mage = createMage("spec-snap-terminal");
        setLevel(mage, 50);
        choose("elementalist");
        choose("pyromancer");

        Map<String, Object> specialization = specializationSnapshot();
        assertThat((List<Map<String, Object>>) specialization.get("path"))
                .extracting(p -> String.valueOf(p.get("id")))
                .containsExactly("elementalist", "pyromancer");
        assertThat(specialization.get("pending")).isNull();
        assertThat((List<Map<String, Object>>) specialization.get("locked")).isEmpty();
    }

    @Test
    void combat_snapshot_includes_level_and_typeLabel_for_players_and_monsters() {
        Entity mage = createMage("combat-type-label");
        setLevel(mage, 10);
        choose("arcanist");

        GameEvent start = new GameEvent("combat.start_encounter");
        start.set("playerId", mage.getId());
        start.set("encounterId", "forest_goblin");
        eventBus.fire("combat.start_encounter", start);

        WorldSnapshot snapshot = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), WorldSnapshot.class)
                .getBody();
        assertThat(snapshot.combat()).isNotNull();

        WorldSnapshot.CombatantInfo player = snapshot.combat().combatants().stream()
                .filter(c -> c.id().equals(mage.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(player.level()).isEqualTo(10);
        assertThat(player.typeLabel()).isEqualTo("奥术法师");

        assertThat(snapshot.combat().combatants().stream()
                .filter(c -> "ENEMY".equals(c.side()))
                .allMatch(c -> c.level() == 1 && "哥布林".equals(c.typeLabel())))
                .isTrue();
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

    private void choose(String specId) {
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "choose_specialization",
                        "params", Map.of("specId", specId)), headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> specializationSnapshot() {
        ResponseEntity<Map> response = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (Map<String, Object>) response.getBody().get("specialization");
    }
}
