package com.epic.engine.script;

import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.core.Entity;
import com.epic.engine.core.Component;
import com.epic.engine.core.Modifier;
import com.epic.engine.core.ModifierChainService;
import com.epic.engine.core.ModifierTypeRegistry;
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

    private ModifierChainService modifierChainService;
    private ModifierTypeRegistry modifierTypeRegistry;
    private final Context context;
    private final EventBus bus;
    private final EntityStore store;
    private Path moduleContext;

    public ScriptRuntime(EventBus bus, EntityStore store) {
        this(bus, store, null, null);
    }

    public ScriptRuntime(EventBus bus, EntityStore store,
                         ModifierChainService modifierChainService,
                         ModifierTypeRegistry modifierTypeRegistry) {
        this.bus = bus;
        this.store = store;
        this.modifierChainService = modifierChainService;
        this.modifierTypeRegistry = modifierTypeRegistry;
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

    public synchronized void execute(String script, String sourceName) {
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
            bus.on(eventType, priority, event -> {
                synchronized (ScriptRuntime.this) {
                    handler.execute(event);
                }
            });
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
            return new WorldSnapshot.ActionOption(type, label, params, null, null);
        }

        @HostAccess.Export
        public WorldSnapshot.ActionOption newActionOptionStyled(String type, String label, Map<String, Object> params, String color, String style) {
            return new WorldSnapshot.ActionOption(type, label, params, color, style);
        }

        @HostAccess.Export
        public HashMap<String, Object> newMap() {
            return new HashMap<>();
        }

        @HostAccess.Export
        public long now() {
            return System.currentTimeMillis();
        }

        @HostAccess.Export
        @SuppressWarnings("unchecked")
        public void combatEvent(String combatId, Map<String, Object> data) {
            Entity combat = store.get(combatId);
            if (combat == null) return;

            // Write to CombatEvents queue
            if (combat.hasComponent("CombatEvents")) {
                List<Object> queue = (List<Object>) combat.getComponent("CombatEvents").get("queue");
                Map<String, Object> evt = new HashMap<>();

                // segments: first log entry (for single-target compat)
                List<Object> log = (List<Object>) data.get("log");
                if (log != null && !log.isEmpty()) {
                    evt.put("segments", log.get(0));
                    evt.put("logCount", log.size());
                } else {
                    evt.put("segments", new ArrayList<>());
                    evt.put("logCount", 0);
                }

                evt.put("effects", data.getOrDefault("effects", new ArrayList<>()));
                evt.put("animation", data.getOrDefault("animation", new ArrayList<>()));
                queue.add(evt);
            }

            // Write to CombatLog
            if (combat.hasComponent("CombatLog")) {
                List<Object> entries = (List<Object>) combat.getComponent("CombatLog").get("entries");
                List<Object> log = (List<Object>) data.get("log");
                if (log != null) {
                    for (Object entry : log) {
                        entries.add(entry);
                    }
                }
            }
        }

        @HostAccess.Export
        public void setBase(String entityId) {
            if (modifierChainService != null) {
                modifierChainService.setBase(entityId);
            }
        }

        @HostAccess.Export
        public void setBaseSelective(String entityId, Value componentTypes) {
            if (modifierChainService == null) return;
            List<String> types = new java.util.ArrayList<>();
            for (int i = 0; i < componentTypes.getArraySize(); i++) {
                types.add(componentTypes.getArrayElement(i).asString());
            }
            modifierChainService.setBaseSelective(entityId, types);
        }

        @HostAccess.Export
        public void addModifier(String entityId, Value config) {
            if (modifierChainService == null) return;
            String id = config.getMember("id").asString();
            String typeId = config.hasMember("typeId") ? config.getMember("typeId").asString() : null;
            String label = config.hasMember("label") ? config.getMember("label").asString() : id;
            Value applyFn = config.getMember("apply");

            int priority;
            if (config.hasMember("priority")) {
                priority = config.getMember("priority").asInt();
            } else if (typeId != null && modifierTypeRegistry != null) {
                priority = modifierTypeRegistry.getBasePriority(typeId);
            } else {
                priority = 0;
            }

            String source = typeId != null ? typeId + "_" + id : id;
            Modifier modifier = new Modifier(id, typeId, label, source, priority, entity -> {
                synchronized (ScriptRuntime.this) {
                    applyFn.execute(entity);
                }
            });
            modifierChainService.addModifier(entityId, modifier);
        }

        @HostAccess.Export
        public void removeModifier(String entityId, String modifierId) {
            if (modifierChainService != null) {
                modifierChainService.removeModifier(entityId, modifierId);
            }
        }

        @HostAccess.Export
        public void recalculate(String entityId) {
            if (modifierChainService != null) {
                modifierChainService.recalculate(entityId);
            }
        }
    }
}
