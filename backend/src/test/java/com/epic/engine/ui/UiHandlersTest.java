package com.epic.engine.ui;

import com.epic.engine.core.*;
import com.epic.engine.script.ScriptRuntime;
import com.epic.engine.snapshot.WorldSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiHandlersTest {

    EventBus bus;
    EntityStore store;
    ScriptRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        bus = new EventBus();
        store = new EntityStore();
        runtime = new ScriptRuntime(bus, store);

        Path baseDir = Path.of("../mods/base-rules/handlers/ui");
        runtime.execute(Files.readString(baseDir.resolve("status_bars.js")), "status_bars.js");
        runtime.execute(Files.readString(baseDir.resolve("actions.js")), "actions.js");

        Entity player = new Entity("player1");
        Component health = new Component("Health");
        health.set("hp", 80);
        health.set("maxHp", 100);
        player.addComponent(health);
        Component mana = new Component("Mana");
        mana.set("mp", 30);
        mana.set("maxMp", 50);
        player.addComponent(mana);
        Component pos = new Component("Position");
        pos.set("map", "world_map");
        pos.set("x", 4);
        pos.set("y", 3);
        player.addComponent(pos);
        store.add(player);
    }

    @AfterEach
    void tearDown() { runtime.close(); }

    @Test
    void statusBars_rendersHealthAndMana() {
        GameEvent event = new GameEvent("ui.render_status");
        event.set("entityId", "player1");
        event.set("bars", new ArrayList<WorldSnapshot.StatusBar>());
        event.set("buffs", new ArrayList<WorldSnapshot.BuffEntry>());
        bus.fire("ui.render_status", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.StatusBar> bars = event.get("bars");
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).id()).isEqualTo("hp");
        assertThat(bars.get(0).current()).isEqualTo(80);
        assertThat(bars.get(1).id()).isEqualTo("mp");
    }

    @Test
    void actions_rendersMovementAndLogout() {
        GameEvent event = new GameEvent("ui.render_actions");
        event.set("entityId", "player1");
        event.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
        bus.fire("ui.render_actions", event);

        @SuppressWarnings("unchecked")
        List<WorldSnapshot.ActionOption> actions = event.get("actions");
        // 4 directions + 1 logout = 5
        assertThat(actions).hasSize(5);
        assertThat(actions.stream().map(WorldSnapshot.ActionOption::type).toList())
                .contains("map_move", "logout");
    }
}
