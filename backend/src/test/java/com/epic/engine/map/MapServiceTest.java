package com.epic.engine.map;

import com.epic.engine.mod.ModRegistry;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MapServiceTest {

    @Autowired
    ModRegistry modRegistry;

    @Autowired
    MapService mapService;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Test
    void terrainsLoadedFromMod() {
        Map<Character, TerrainDefinition> terrains = modRegistry.getTerrains();
        assertThat(terrains).isNotEmpty();
        assertThat(terrains).containsKey('林');
        assertThat(terrains.get('林').id()).isEqualTo("forest");
        assertThat(terrains.get('林').color()).isEqualTo("#2d6a4f");
        assertThat(terrains.get('水').requires()).containsExactly("swim");
    }

    @Test
    void mapLoadedFromMod() {
        Optional<MapData> map = modRegistry.getMap("world_map");
        assertThat(map).isPresent();
        assertThat(map.get().width()).isEqualTo(10);
        assertThat(map.get().height()).isEqualTo(10);
        assertThat(map.get().terrain()[0][0]).isEqualTo('林');
        assertThat(map.get().pois()).hasSize(1);
        assertThat(map.get().spawnX()).isEqualTo(4);
        assertThat(map.get().spawnY()).isEqualTo(3);
    }

    @Test
    void moveInDirection() {
        PlayerState state = new PlayerState("maptest1", "village_square");
        playerStateRepository.save(state);

        var result = mapService.move("maptest1", "RIGHT");
        assertThat(result.success()).isTrue();
        assertThat(result.newX()).isEqualTo(5);
        assertThat(result.newY()).isEqualTo(3);
    }

    @Test
    void moveBlockedByImpassableTerrain() {
        PlayerState state = new PlayerState("maptest2", "village_square");
        state.setMapX(6);
        state.setMapY(0);
        playerStateRepository.save(state);

        var result = mapService.move("maptest2", "RIGHT");
        assertThat(result.success()).isFalse();
    }

    @Test
    void moveToWithPathfinding() {
        PlayerState state = new PlayerState("maptest3", "village_square");
        playerStateRepository.save(state);

        var result = mapService.moveTo("maptest3", 6, 3);
        assertThat(result.success()).isTrue();
        assertThat(result.path()).isNotEmpty();
        assertThat(result.path().get(0).x()).isEqualTo(4);
        assertThat(result.path().get(0).y()).isEqualTo(3);
        assertThat(result.path().get(result.path().size() - 1).x()).isEqualTo(6);
        assertThat(result.path().get(result.path().size() - 1).y()).isEqualTo(3);
    }

    @Test
    void moveDetectsPoi() {
        PlayerState state = new PlayerState("maptest4", "village_square");
        playerStateRepository.save(state);

        var result = mapService.move("maptest4", "UP");
        assertThat(result.success()).isTrue();
        assertThat(result.poi()).isNotNull();
        assertThat(result.poi().id()).isEqualTo("village");
    }
}
