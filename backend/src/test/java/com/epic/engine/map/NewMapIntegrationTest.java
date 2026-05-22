package com.epic.engine.map;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NewMapIntegrationTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path baseDir = Path.of("../mods/base-rules/handlers/map");
        runtime.execute(Files.readString(baseDir.resolve("movement.js")), "movement.js");
        runtime.execute(Files.readString(baseDir.resolve("pathfinding.js")), "pathfinding.js");

        Entity map = new Entity("world_map");
        Component mapData = new Component("MapData");
        mapData.set("width", 10);
        mapData.set("height", 10);
        map.addComponent(mapData);
        store.add(map);

        Entity player = new Entity("player1");
        Component pos = new Component("Position");
        pos.set("map", "world_map");
        pos.set("x", 4);
        pos.set("y", 3);
        player.addComponent(pos);
        store.add(player);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
    }

    @Test
    void moveNorth_updatesPosition() {
        GameEvent event = new GameEvent("map.move");
        event.set("entityId", "player1");
        event.set("direction", "NORTH");
        bus.fire("map.move", event);

        assertThat((boolean) event.get("success")).isTrue();
        Entity player = store.get("player1");
        assertThat(player.getComponent("Position").getInt("y")).isEqualTo(2);
    }

    @Test
    void moveOutOfBounds_fails() {
        Entity player = store.get("player1");
        player.getComponent("Position").set("y", 0);

        GameEvent event = new GameEvent("map.move");
        event.set("entityId", "player1");
        event.set("direction", "NORTH");
        bus.fire("map.move", event);

        assertThat((boolean) event.get("success")).isFalse();
    }

    @Test
    void pathfind_findsRoute() {
        GameEvent event = new GameEvent("map.pathfind");
        event.set("entityId", "player1");
        event.set("targetX", 6);
        event.set("targetY", 3);
        bus.fire("map.pathfind", event);

        assertThat((boolean) event.get("found")).isTrue();
    }
}
