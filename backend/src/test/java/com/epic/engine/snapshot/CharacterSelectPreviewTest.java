package com.epic.engine.snapshot;

import com.epic.engine.session.SessionService;
import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CharacterSelectPreviewTest {

    @Autowired
    SessionService sessionService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    EntityStore entityStore;

    @Test
    void select_snapshot_includes_class_previews() {
        // A fresh session has no active character → buildSnapshot returns character_select
        String token = sessionService.createSession();
        WorldSnapshot snap = snapshotService.buildSnapshot(token);

        assertThat(snap.phase()).isEqualTo("character_select");
        assertThat(snap.classPreviews()).isNotNull();
        assertThat(snap.classPreviews()).isNotEmpty();

        var warrior = snap.classPreviews().stream()
                .filter(p -> p.id().equals("warrior"))
                .findFirst()
                .orElse(null);
        assertThat(warrior).isNotNull();
        assertThat(warrior.label()).isEqualTo("战士");
        // SnakeYAML loads plain integers as Integer
        assertThat(warrior.growth().get("力量")).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void select_snapshot_characterInfo_uses_effective_specialization_data() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> create = rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "confirm_character",
                        "params", Map.of("name", "select-spec-mage", "class", "mage")), headers), Map.class);
        String playerId = String.valueOf(create.getBody().get("playerId"));
        Entity mage = entityStore.get(playerId);
        mage.getComponent("Experience").set("level", 10);
        mage.getComponent("Character").set("level", 10);

        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "choose_specialization",
                        "params", Map.of("specId", "arcanist")), headers), Map.class);

        sessionService.clearActiveCharacter(token);
        WorldSnapshot snap = snapshotService.buildSnapshot(token);

        assertThat(snap.phase()).isEqualTo("character_select");
        WorldSnapshot.CharacterInfo info = snap.characters().stream()
                .filter(c -> c.id().equals(playerId))
                .findFirst()
                .orElseThrow();
        assertThat(info.classLabel()).isEqualTo("奥术法师");
        assertThat(info.growth()).containsEntry("智力", 4).containsEntry("体质", 1);
        assertThat(info.primaryStats()).containsEntry("智力", 50).containsEntry("体质", 16);
        assertThat(info.description()).isEqualTo("远程法术输出,智力主属性");
    }
}
