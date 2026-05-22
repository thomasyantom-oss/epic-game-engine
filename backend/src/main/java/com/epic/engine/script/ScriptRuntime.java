package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

public class ScriptRuntime implements AutoCloseable {

    private final Context context;
    private final EventBus bus;
    private final EntityStore store;

    public ScriptRuntime(EventBus bus, EntityStore store) {
        this.bus = bus;
        this.store = store;
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> false)
                .build();
        bindApi();
    }

    private void bindApi() {
        Value bindings = context.getBindings("js");
        bindings.putMember("engine", new EngineApi());
        bindings.putMember("store", store);
    }

    public void execute(String script, String sourceName) {
        try {
            Source source = Source.newBuilder("js", script, sourceName).build();
            context.eval(source);
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute script: " + sourceName, e);
        }
    }

    @Override
    public void close() {
        context.close();
    }

    public class EngineApi {
        @HostAccess.Export
        public void on(String eventType, int priority, Value handler) {
            bus.on(eventType, priority, event -> handler.execute(event));
        }

        @HostAccess.Export
        public void fire(String eventType, GameEvent event) {
            bus.fire(eventType, event);
        }

        @HostAccess.Export
        public GameEvent newEvent(String type) {
            return new GameEvent(type);
        }

        @HostAccess.Export
        public Entity createEntity(String id) {
            return new Entity(id);
        }

        @HostAccess.Export
        public Component newComponent(String type) {
            return new Component(type);
        }
    }
}
