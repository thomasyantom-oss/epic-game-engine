package com.epic.engine.map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MapIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void getMapData() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.getForObject("/api/map/world_map", Map.class);
        assertThat(response.get("id")).isEqualTo("world_map");
        assertThat(response.get("width")).isEqualTo(10);
        assertThat(response.get("height")).isEqualTo(10);
        assertThat(response).containsKey("terrain");
        assertThat(response).containsKey("terrains");
        assertThat(response).containsKey("pois");
    }

    @Test
    void moveDirection() {
        Map<String, Object> request = Map.of("playerId", "mapapi1", "direction", "RIGHT");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject("/api/map/move", request, Map.class);
        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response.get("newX")).isEqualTo(5);
        assertThat(response.get("newY")).isEqualTo(3);
    }

    @Test
    void moveToWithPath() {
        Map<String, Object> request = Map.of("playerId", "mapapi2", "targetX", 6, "targetY", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject("/api/map/moveTo", request, Map.class);
        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response).containsKey("path");
    }

    @Test
    void getPosition() {
        // Trigger a move first to create the player
        rest.postForObject("/api/map/move",
                Map.of("playerId", "mapapi3", "direction", "DOWN"), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> pos = rest.getForObject("/api/map/position/mapapi3", Map.class);
        assertThat(pos.get("mapId")).isEqualTo("world_map");
        assertThat(pos.get("x")).isEqualTo(4);
        assertThat(pos.get("y")).isEqualTo(4);
    }
}
