package com.epic.engine.world;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorldBootstrapTest {

    @Autowired EntityStore entityStore;

    @Test
    void worldMap_loadedOnStartup() {
        Entity map = entityStore.get("world_map");
        assertThat(map).isNotNull();
        assertThat(map.hasComponent("MapData")).isTrue();
        assertThat(map.getComponent("MapData").getInt("width")).isEqualTo(10);
        assertThat(map.getComponent("MapData").getInt("height")).isEqualTo(10);
        assertThat(map.hasTag("map")).isTrue();
    }
}
