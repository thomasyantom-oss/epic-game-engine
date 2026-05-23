package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import com.epic.engine.snapshot.WorldSnapshot;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        @HostAccess.Export
        public WorldSnapshot.CharacterInfo newCharacterInfo(String id, String name, int level, String classId, String classLabel) {
            return new WorldSnapshot.CharacterInfo(id, name, level, classId, classLabel);
        }

        @HostAccess.Export
        public WorldSnapshot.FormData newFormData(List<WorldSnapshot.FormField> fields) {
            return new WorldSnapshot.FormData(fields);
        }

        @HostAccess.Export
        public WorldSnapshot.FormField newFormField(String name, String label, String type, boolean required, List<WorldSnapshot.FormOption> options) {
            return new WorldSnapshot.FormField(name, label, type, required, options);
        }

        @HostAccess.Export
        public WorldSnapshot.FormOption newFormOption(String value, String label, String description) {
            return new WorldSnapshot.FormOption(value, label, description);
        }

        @HostAccess.Export
        public ArrayList<Object> newList() {
            return new ArrayList<>();
        }

        @HostAccess.Export
        public WorldSnapshot.StatusBar newStatusBar(String id, String label, int current, int max, String color, int priority) {
            return new WorldSnapshot.StatusBar(id, label, current, max, color, priority);
        }

        @HostAccess.Export
        public WorldSnapshot.ActionOption newActionOption(String type, String label, Map<String, Object> params) {
            return new WorldSnapshot.ActionOption(type, label, params);
        }

        @HostAccess.Export
        public HashMap<String, Object> newMap() {
            return new HashMap<>();
        }

        @HostAccess.Export
        public long now() {
            return System.currentTimeMillis();
        }
    }
}
