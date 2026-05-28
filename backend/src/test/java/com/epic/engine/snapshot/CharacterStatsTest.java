package com.epic.engine.snapshot;

import com.epic.engine.core.*;
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
class CharacterStatsTest {

    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService;

    @Test
    @SuppressWarnings("unchecked")
    void getStats_returnsBreakdown() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建角色
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "confirm_character",
                        "params", Map.of("name", "测试", "class", "warrior")), headers), Map.class);

        // 获取属性明细
        ResponseEntity<Map> resp = rest.exchange(
                "/api/character/stats", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotEmpty();

        // 应有 CombatStats.attack 的 breakdown
        // 结构: { "CombatStats.attack": { "final": 13, "breakdown": [...] } }
        var statsMap = resp.getBody();
        assertThat(statsMap).containsKey("CombatStats.attack");
        var attackStat = (Map<String, Object>) statsMap.get("CombatStats.attack");
        assertThat(attackStat).containsKey("final");
        assertThat(attackStat).containsKey("breakdown");
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_hasEquipmentAndPendingPoints() {
        String token = sessionService.createSession();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建角色
        rest.exchange("/api/action", HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "confirm_character",
                        "params", Map.of("name", "测试员", "class", "warrior")), headers), Map.class);

        // 获取 snapshot
        ResponseEntity<Map> resp = rest.exchange(
                "/api/snapshot", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
        assertThat(body).containsKey("equipment");
        assertThat(body).containsKey("pendingPoints");

        var equipment = (Map<String, Object>) body.get("equipment");
        assertThat(equipment).containsKey("slots");
        assertThat(equipment).containsKey("inventory");
    }
}
