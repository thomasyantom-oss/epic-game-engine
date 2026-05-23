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
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ScriptRuntime implements AutoCloseable {

    private final Context context;
    private final EventBus bus;
    private final EntityStore store;
    private Path moduleContext;

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

    public void setModuleContext(Path modulePath) {
        this.moduleContext = modulePath;
    }

    public void bindService(String name, Object service) {
        context.getBindings("js").putMember(name, service);
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

        @HostAccess.Export
        public Map<String, Object> loadYaml(String relativePath) {
            if (moduleContext == null) {
                throw new RuntimeException("No module context set");
            }
            Path yamlPath = moduleContext.resolve(relativePath);
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(yamlPath)) {
                return yaml.load(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load YAML: " + yamlPath, e);
            }
        }
    }
}
