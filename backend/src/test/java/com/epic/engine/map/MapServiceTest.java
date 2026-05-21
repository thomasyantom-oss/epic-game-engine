package com.epic.engine.map;

import com.epic.engine.mod.ModRegistry;
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
}
