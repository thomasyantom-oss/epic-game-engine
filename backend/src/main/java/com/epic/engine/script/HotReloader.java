package com.epic.engine.script;

import com.epic.engine.core.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HotReloader {

    private final ScriptRuntime runtime;
    private final EventBus bus;
    private final Map<String, String> loadedScripts = new ConcurrentHashMap<>();

    public HotReloader(ScriptRuntime runtime, EventBus bus) {
        this.runtime = runtime;
        this.bus = bus;
    }

    public void reload(String sourceName, String newScript) {
        bus.clear();
        loadedScripts.put(sourceName, newScript);

        for (Map.Entry<String, String> entry : loadedScripts.entrySet()) {
            runtime.execute(entry.getValue(), entry.getKey());
        }
    }

    public void trackScript(String sourceName, String script) {
        loadedScripts.put(sourceName, script);
    }

    public void stop() {
        // Placeholder for file watcher shutdown
    }
}
