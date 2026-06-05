package com.epic.engine.snapshot;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.persistence.PersistenceService;
import com.epic.engine.session.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TalentTreeSnapshotTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;
    @Autowired EntityStore entityStore;
    @Autowired PersistenceService persistenceService;
    @Autowired ModifierChainService modifierChainService;
    @Autowired EventBus eventBus;
    @Autowired ObjectMapper objectMapper;

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
    void unspecialized_nullBlock() {
        createMage("talent-snap-unspec");

        WorldSnapshot snapshot = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), WorldSnapshot.class)
                .getBody();

        assertThat(snapshot.talentTree()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void specialized_nodeStates() {
        Entity mage = createMage("talent-snap-states");
        setLevel(mage, 12);
        action("choose_specialization", Map.of("specId", "elementalist"));
        action("talent_unlock", Map.of("nodeId", "elem_root"));
        mage.getComponent("OrbPouch").set("爆破", 2);

        ResponseEntity<Map> response = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> talentTree = (Map<String, Object>) response.getBody().get("talentTree");

        assertThat(talentTree).isNotNull();
        assertThat(talentTree.get("root")).isEqualTo("elementalist");
        Map<String, Object> points = (Map<String, Object>) talentTree.get("points");
        assertThat(((Number) points.get("total")).intValue()).isEqualTo(2);
        assertThat(((Number) points.get("spent")).intValue()).isEqualTo(1);
        assertThat(((Number) points.get("available")).intValue()).isEqualTo(1);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) talentTree.get("nodes");
        assertThat(nodes).extracting(n -> String.valueOf(n.get("id")))
                .contains("elem_root", "fireball_slot", "fb_damage");
        assertThat(node(nodes, "elem_root").get("state")).isEqualTo("unlocked");
        assertThat(node(nodes, "fireball_slot").get("state")).isEqualTo("unlockable");
        assertThat(node(nodes, "fb_damage").get("state")).isEqualTo("locked");

        List<Map<String, Object>> orbs = (List<Map<String, Object>>) talentTree.get("orbInventory");
        assertThat(orbs).singleElement().satisfies(orb -> {
            assertThat(orb.get("type")).isEqualTo("爆破");
            assertThat(((Number) orb.get("count")).intValue()).isEqualTo(2);
        });
    }

    @Test
    void talentTree_jsonContractHasFrontendLayoutFields() throws Exception {
        Entity mage = createMage("talent-snap-json");
        setLevel(mage, 12);
        action("choose_specialization", Map.of("specId", "elementalist"));
        action("talent_unlock", Map.of("nodeId", "elem_root"));
        action("talent_unlock", Map.of("nodeId", "fireball_slot"));
        mage.getComponent("OrbPouch").set("爆破", 2);

        WorldSnapshot snapshot = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), WorldSnapshot.class)
                .getBody();
        JsonNode talentTree = objectMapper.readTree(objectMapper.writeValueAsString(snapshot)).path("talentTree");

        assertThat(talentTree.path("root").asText()).isEqualTo("elementalist");
        assertThat(talentTree.path("points").path("total").asInt()).isEqualTo(2);
        assertThat(talentTree.path("points").path("spent").asInt()).isEqualTo(2);
        assertThat(talentTree.path("points").path("available").asInt()).isZero();
        assertThat(talentTree.path("orbInventory").get(0).path("type").asText()).isEqualTo("爆破");
        assertThat(talentTree.path("orbInventory").get(0).path("count").asInt()).isEqualTo(2);

        JsonNode fireballSlot = jsonNode(talentTree.path("nodes"), "fireball_slot");
        assertThat(fireballSlot.path("tier").asInt()).isEqualTo(2);
        assertThat(fireballSlot.path("order").asInt()).isZero();
        assertThat(fireballSlot.path("parents").get(0).asText()).isEqualTo("elem_root");
        assertThat(fireballSlot.path("children").get(0).asText()).isEqualTo("fb_damage");
        assertThat(fireballSlot.path("state").asText()).isEqualTo("slot-empty");
        assertThat(fireballSlot.path("slot").path("skill").asText()).isEqualTo("fireball");
        assertThat(fireballSlot.path("slot").path("orbType").asText()).isEqualTo("爆破");
        assertThat(fireballSlot.path("slot").path("orbCount").asInt()).isEqualTo(1);
        assertThat(fireballSlot.path("slot").path("evolution").asText()).isEqualTo("pyroblast");
        assertThat(fireballSlot.path("slot").path("filled").asBoolean()).isFalse();
        assertThat(fireballSlot.path("actions").path("canUnlock").asBoolean()).isFalse();
        assertThat(fireballSlot.path("actions").path("canPlaceOrb").asBoolean()).isTrue();
        assertThat(fireballSlot.path("effectSummary").asText()).contains("爆破");
    }

    @Test
    @SuppressWarnings("unchecked")
    void evolvedSlotRelockedStateUsesPermanentEvolutionMemory() {
        Entity mage = createMage("talent-snap-relocked");
        setLevel(mage, 12);
        action("choose_specialization", Map.of("specId", "elementalist"));
        action("talent_unlock", Map.of("nodeId", "elem_root"));
        action("talent_unlock", Map.of("nodeId", "fireball_slot"));
        mage.getComponent("OrbPouch").set("爆破", 2);
        action("talent_place_orb", Map.of("nodeId", "fireball_slot"));
        action("talent_respec", Map.of());

        ResponseEntity<Map> response = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> talentTree = (Map<String, Object>) response.getBody().get("talentTree");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) talentTree.get("nodes");
        Map<String, Object> fireballSlot = node(nodes, "fireball_slot");
        Map<String, Object> slot = (Map<String, Object>) fireballSlot.get("slot");

        assertThat(fireballSlot.get("state")).isEqualTo("filled-node-relocked");
        assertThat(slot.get("filled")).isEqualTo(false);
        List<Map<String, Object>> known = (List<Map<String, Object>>) mage.getComponent("Skillbook").get("known");
        assertThat(known.stream()
                .filter(k -> "fireball".equals(String.valueOf(k.get("base"))))
                .findFirst()
                .orElseThrow()
                .get("node")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void skillbook_showsEvolutionName() {
        Entity mage = createMage("talent-snap-skillbook");

        Map<String, Object> baseFireball = skillbookEntry("fireball");
        assertThat(baseFireball.get("name")).isEqualTo("火球术");

        List<Map<String, Object>> known = (List<Map<String, Object>>) mage.getComponent("Skillbook").get("known");
        known.stream()
                .filter(k -> "fireball".equals(String.valueOf(k.get("base"))))
                .findFirst()
                .orElseThrow()
                .put("node", "pyroblast");

        Map<String, Object> evolved = skillbookEntry("fireball");
        assertThat(evolved.get("name")).isEqualTo("炎爆");
        assertThat(evolved.get("icon")).isEqualTo("火");
        assertThat(evolved.get("description")).isEqualTo("火球进化:一排 AOE,灼烧更强");
    }

    @Test
    @SuppressWarnings("unchecked")
    void skillbook_showsOnlyDisplayTalentPassivesAsReadonlyEntries() {
        Entity mage = createMage("talent-snap-passive-list");
        setLevel(mage, 50);
        action("choose_specialization", Map.of("specId", "elementalist"));
        action("choose_specialization", Map.of("specId", "pyromancer"));
        action("talent_unlock", Map.of("nodeId", "elem_root"));
        action("talent_unlock", Map.of("nodeId", "fireball_slot"));
        action("talent_unlock", Map.of("nodeId", "fb_damage"));
        action("talent_unlock", Map.of("nodeId", "ember_passive"));

        ResponseEntity<Map> response = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> skillbook = (Map<String, Object>) response.getBody().get("skillbook");
        List<Map<String, Object>> known = (List<Map<String, Object>>) skillbook.get("known");
        List<Map<String, Object>> talentPassives = known.stream()
                .filter(k -> "passive".equals(String.valueOf(k.get("kind"))))
                .filter(k -> "talent".equals(String.valueOf(k.get("source"))))
                .toList();

        assertThat(talentPassives).extracting(k -> String.valueOf(k.get("base")))
                .containsExactlyInAnyOrder("talent_fb_damage", "lifesteal_on_kill");
        assertThat(talentPassives).allSatisfy(k -> {
            assertThat(k.get("equipped")).isEqualTo(false);
            assertThat(k.get("node")).isNull();
        });
        assertThat(known).extracting(k -> String.valueOf(k.get("base")))
                .doesNotContain("talent_elem_root");
        assertThat(mage.getComponent("PrimaryStats").getInt("火属性")).isEqualTo(8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadRoundTrip_preservesTalentOrbEvolutionAndOverride() {
        Entity mage = createMage("talent-snap-reload");
        setLevel(mage, 50);
        action("choose_specialization", Map.of("specId", "elementalist"));
        action("choose_specialization", Map.of("specId", "pyromancer"));
        action("talent_unlock", Map.of("nodeId", "elem_root"));
        action("talent_unlock", Map.of("nodeId", "fireball_slot"));
        mage.getComponent("OrbPouch").set("爆破", 2);
        action("talent_place_orb", Map.of("nodeId", "fireball_slot"));

        int fireBefore = mage.getComponent("PrimaryStats").getInt("火属性");
        String playerId = mage.getId();
        persistenceService.save(mage);
        entityStore.remove(playerId);
        modifierChainService.clearChain(playerId);

        Entity loaded = persistenceService.load(playerId);
        entityStore.add(loaded);
        GameEvent event = new GameEvent("entity.loaded");
        event.set("entity", loaded);
        eventBus.fire("entity.loaded", event);

        assertThat(loaded.getComponent("PrimaryStats").getInt("火属性")).isEqualTo(fireBefore);
        List<String> path = (List<String>) loaded.getComponent("Specialization").get("path");
        assertThat(path).containsExactly("elementalist", "pyromancer");
        List<String> unlocked = (List<String>) loaded.getComponent("TalentTree").get("unlocked");
        assertThat(unlocked).containsExactly("elem_root", "fireball_slot");
        List<Map<String, Object>> known = (List<Map<String, Object>>) loaded.getComponent("Skillbook").get("known");
        assertThat(known.stream()
                .filter(k -> "fireball".equals(String.valueOf(k.get("base"))))
                .findFirst()
                .orElseThrow()
                .get("node")).isEqualTo("pyroblast");
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

    private void action(String type, Map<String, Object> params) {
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", type, "params", params), headers), Map.class);
    }

    private Map<String, Object> node(List<Map<String, Object>> nodes, String id) {
        return nodes.stream()
                .filter(n -> id.equals(String.valueOf(n.get("id"))))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode jsonNode(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Missing node " + id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> skillbookEntry(String base) {
        ResponseEntity<Map> response = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> skillbook = (Map<String, Object>) response.getBody().get("skillbook");
        List<Map<String, Object>> known = (List<Map<String, Object>>) skillbook.get("known");
        return known.stream()
                .filter(k -> base.equals(String.valueOf(k.get("base"))))
                .findFirst()
                .orElseThrow();
    }
}
