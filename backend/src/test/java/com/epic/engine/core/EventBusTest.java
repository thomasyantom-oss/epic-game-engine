package com.epic.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {

    EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
    }

    @Test
    void fireEvent_callsRegisteredHandler() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 100, event -> results.add("handled"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("handled");
    }

    @Test
    void handlers_executedInPriorityOrder_lowNumberFirst() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 200, event -> results.add("second"));
        bus.on("test.event", 100, event -> results.add("first"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("first", "second");
    }

    @Test
    void cancel_stopsSubsequentHandlers() {
        var results = new java.util.ArrayList<String>();
        bus.on("test.event", 100, event -> {
            results.add("first");
            event.cancel();
        });
        bus.on("test.event", 200, event -> results.add("should not run"));

        bus.fire("test.event", new GameEvent("test.event"));

        assertThat(results).containsExactly("first");
    }

    @Test
    void differentEvents_dontInterfere() {
        var results = new java.util.ArrayList<String>();
        bus.on("event.a", 100, event -> results.add("a"));
        bus.on("event.b", 100, event -> results.add("b"));

        bus.fire("event.a", new GameEvent("event.a"));

        assertThat(results).containsExactly("a");
    }

    @Test
    void eventData_accessibleInHandler() {
        var results = new java.util.ArrayList<Object>();
        bus.on("test.event", 100, event -> results.add(event.get("value")));

        GameEvent event = new GameEvent("test.event");
        event.set("value", 42);
        bus.fire("test.event", event);

        assertThat(results).containsExactly(42);
    }
}
