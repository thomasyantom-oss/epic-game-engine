package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SnapshotControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired EntityStore entityStore;

    @Test
    void getSnapshot_returnsValidStructure() {
        WorldSnapshot snapshot = rest.getForObject("/api/snapshot/player1", WorldSnapshot.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.playerId()).isEqualTo("player1");
        assertThat(snapshot.result().success()).isTrue();
    }

    @Test
    void getSnapshot_withEntityInStore_returnsMapData() {
        Entity player = new Entity("test_snap_player");
        Component pos = new Component("Position");
        pos.set("map", "test_map");
        pos.set("x", 5);
        pos.set("y", 3);
        player.addComponent(pos);
        entityStore.add(player);

        Entity map = new Entity("test_map");
        Component mapData = new Component("MapData");
        mapData.set("width", 10);
        mapData.set("height", 10);
        map.addComponent(mapData);
        entityStore.add(map);

        WorldSnapshot snapshot = rest.getForObject("/api/snapshot/test_snap_player", WorldSnapshot.class);

        assertThat(snapshot.map()).isNotNull();
        assertThat(snapshot.map().playerX()).isEqualTo(5);
        assertThat(snapshot.map().playerY()).isEqualTo(3);

        // cleanup
        entityStore.remove("test_snap_player");
        entityStore.remove("test_map");
    }

    @Test
    void performAction_firesEventAndReturnsSnapshot() {
        var request = Map.of("playerId", "player1", "type", "test_action", "params", Map.of());
        WorldSnapshot snapshot = rest.postForObject("/api/v2/action", request, WorldSnapshot.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.playerId()).isEqualTo("player1");
        assertThat(snapshot.result().success()).isTrue();
    }
}
