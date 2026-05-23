package com.epic.engine.character;

import com.epic.engine.core.EntityStore;
import com.epic.engine.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CharacterFlowTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;
    @Autowired EntityStore entityStore;

    @Test
    void createCharacter_fullFlow() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. 请求角色创建表单
        var createReq = Map.of("type", "create_character", "params", Map.of());
        ResponseEntity<Map> createResp = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(createReq, headers), Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody().get("phase")).isEqualTo("character_create");
        assertThat(createResp.getBody().get("form")).isNotNull();

        // 2. 确认角色创建
        var confirmReq = Map.of("type", "confirm_character",
                "params", Map.of("name", "测试勇者", "class", "warrior"));
        ResponseEntity<Map> confirmResp = rest.exchange(
                "/api/action", HttpMethod.POST,
                new HttpEntity<>(confirmReq, headers), Map.class);
        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResp.getBody().get("phase")).isEqualTo("in_game");
        assertThat(confirmResp.getBody().get("playerId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectCharacter_afterCreation() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建角色
        var confirmReq = Map.of("type", "confirm_character",
                "params", Map.of("name", "选择测试", "class", "mage"));
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(confirmReq, headers), Map.class);

        // 登出
        var logoutReq = Map.of("type", "logout", "params", Map.of());
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(logoutReq, headers), Map.class);

        // 获取快照 — 应为 character_select 包含我们的角色
        ResponseEntity<Map> snapshot = rest.exchange(
                "/api/snapshot", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(snapshot.getBody().get("phase")).isEqualTo("character_select");
        List<Map> characters = (List<Map>) snapshot.getBody().get("characters");
        assertThat(characters).isNotEmpty();
    }
}
