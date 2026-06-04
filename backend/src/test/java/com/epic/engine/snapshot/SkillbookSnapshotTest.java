package com.epic.engine.snapshot;

import com.epic.engine.session.SessionService;
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
class SkillbookSnapshotTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_includesSkillbook() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        rest.exchange("/api/action", HttpMethod.POST, new HttpEntity<>(Map.of(
                "type", "confirm_character",
                "params", Map.of("name", "快照技能测试", "class", "mage")), headers), Map.class);

        ResponseEntity<Map> snap = rest.exchange("/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> skillbook = (Map<String, Object>) snap.getBody().get("skillbook");
        assertThat(skillbook).isNotNull();
        assertThat(((Number) skillbook.get("slots")).intValue()).isEqualTo(6);
        assertThat(((Number) skillbook.get("equippedCount")).intValue()).isEqualTo(2);

        List<Map<String, Object>> known = (List<Map<String, Object>>) skillbook.get("known");
        List<String> bases = known.stream().map(skill -> String.valueOf(skill.get("base"))).toList();
        assertThat(bases).contains("fireball", "light_field");

        Map<String, Object> fireball = known.stream()
                .filter(skill -> "fireball".equals(skill.get("base")))
                .findFirst()
                .orElseThrow();
        assertThat(fireball.get("name")).isEqualTo("火球术");
        assertThat(fireball.get("description")).isInstanceOf(String.class);
        assertThat(fireball.get("icon")).isInstanceOf(String.class);
        assertThat(fireball.get("equipped")).isEqualTo(Boolean.TRUE);
        assertThat(fireball.get("node")).isNull();
    }
}
