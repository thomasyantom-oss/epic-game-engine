package com.epic.engine.snapshot;

import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionRoutingTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;

    @Test
    void noToken_returnsNewSessionWithCharacterSelect() {
        ResponseEntity<Map> response = rest.getForEntity("/api/snapshot", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = response.getBody();
        assertThat(body.get("phase")).isEqualTo("character_select");
        assertThat(body.get("sessionToken")).isNotNull();
    }

    @Test
    void withToken_noActiveCharacter_returnsCharacterSelect() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        ResponseEntity<Map> response = rest.exchange(
                "/api/snapshot", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map body = response.getBody();
        assertThat(body.get("phase")).isEqualTo("character_select");
    }

    @Test
    void action_withToken_passesTokenToEvent() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of("type", "test_ping", "params", Map.of());
        ResponseEntity<Map> response = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(request, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("phase")).isEqualTo("character_select");
    }
}
