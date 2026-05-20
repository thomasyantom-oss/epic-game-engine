package com.epic.engine;

import com.epic.engine.action.ActionController.ActionRequest;
import com.epic.engine.action.ActionResponse;
import com.epic.engine.scene.Scene;
import com.epic.engine.save.PlayerState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void fullGameLoop() {
        // 1. 新玩家获取初始状态
        PlayerState state = rest.getForObject("/api/save/player1", PlayerState.class);
        assertThat(state.getPlayerId()).isEqualTo("player1");
        assertThat(state.getCurrentScene()).isEqualTo("village_square");

        // 2. 获取当前场景
        Scene scene = rest.getForObject("/api/scene/village_square", Scene.class);
        assertThat(scene.id()).isEqualTo("village_square");
        assertThat(scene.description()).isNotEmpty();
        assertThat(scene.actions()).hasSizeGreaterThanOrEqualTo(2);

        // 3. 执行移动操作
        ActionRequest moveRequest = new ActionRequest("player1", "move", Map.of("target", "tavern"));
        ActionResponse response = rest.postForObject("/api/action", moveRequest, ActionResponse.class);
        assertThat(response.success()).isTrue();
        assertThat(response.refreshPanels()).isNotEmpty();

        // 4. 验证状态已更新
        PlayerState updated = rest.getForObject("/api/save/player1", PlayerState.class);
        assertThat(updated.getCurrentScene()).isEqualTo("tavern");

        // 5. 获取新场景
        Scene tavern = rest.getForObject("/api/scene/tavern", Scene.class);
        assertThat(tavern.id()).isEqualTo("tavern");
        assertThat(tavern.description()).isNotEmpty();

        // 6. 移动到不存在的场景仍然成功（move handler不验证目标是否存在）
        // 但获取该场景会404
        ActionRequest badMove = new ActionRequest("player1", "move", Map.of("target", "nonexistent"));
        ActionResponse badResponse = rest.postForObject("/api/action", badMove, ActionResponse.class);
        assertThat(badResponse.success()).isTrue();

        ResponseEntity<Scene> notFound = rest.getForEntity("/api/scene/nonexistent", Scene.class);
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void unknownActionTypeFails() {
        ActionRequest request = new ActionRequest("player1", "fly", Map.of());
        ActionResponse response = rest.postForObject("/api/action", request, ActionResponse.class);
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("未知的操作类型");
    }

    @Test
    void debugEndpoints() {
        // health check
        @SuppressWarnings("unchecked")
        Map<String, Object> health = rest.getForObject("/api/debug/health", Map.class);
        assertThat(health.get("status")).isEqualTo("running");
        assertThat((Integer) health.get("scenes")).isGreaterThanOrEqualTo(3);
    }
}
