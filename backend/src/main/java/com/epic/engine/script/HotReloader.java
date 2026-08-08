package com.epic.engine.script;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class HotReloader {

    private static final Logger log = LoggerFactory.getLogger(HotReloader.class);

    private final ScriptRuntime runtime;
    private final EventBus bus;
    private final EntityStore store;
    private final Map<String, String> loadedScripts = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private Thread watchThread;
    private Path modsPath;

    public HotReloader(ScriptRuntime runtime, EventBus bus) {
        this(runtime, bus, null);
    }

    public HotReloader(ScriptRuntime runtime, EventBus bus, EntityStore store) {
        this.runtime = runtime;
        this.bus = bus;
        this.store = store;
    }

    public void reload(String sourceName, String newScript) {
        bus.clear();
        loadedScripts.put(sourceName, newScript);

        for (Map.Entry<String, String> entry : loadedScripts.entrySet()) {
            // Restore moduleContext for each script before executing
            if (modsPath != null) {
                Path scriptPath = modsPath.resolve(entry.getKey());
                Path modDir = scriptPath;
                while (modDir != null && !modDir.equals(modsPath)) {
                    if (Files.exists(modDir.resolve("mod.yaml"))) {
                        runtime.setModuleContext(modDir);
                        break;
                    }
                    modDir = modDir.getParent();
                }
            }
            runtime.execute(entry.getValue(), entry.getKey());
        }
        rebindExistingEntities();
    }

    public void trackScript(String sourceName, String script) {
        loadedScripts.put(sourceName, script);
    }

    public void startWatching(Path modsPath) {
        this.modsPath = modsPath;
        running = true;
        watchThread = new Thread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                registerRecursive(modsPath, watcher);
                log.info("JS 热加载已启动，监听: {}", modsPath);

                while (running) {
                    WatchKey key;
                    try {
                        key = watcher.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (key == null) continue;

                    boolean needsReload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = ((Path) key.watchable()).resolve((Path) event.context());
                        String name = changed.toString();
                        if (name.endsWith(".js") || name.endsWith(".yaml")) {
                            log.info("检测到变更: {}", changed.getFileName());
                            needsReload = true;
                        }
                    }
                    key.reset();

                    if (needsReload) {
                        reloadAll(modsPath);
                    }
                }
            } catch (IOException e) {
                log.error("文件监听失败", e);
            }
        }, "js-hot-reloader");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void reloadAll(Path modsPath) {
        log.info("重新加载所有 JS handlers...");
        bus.clear();
        loadedScripts.clear();

        try (Stream<Path> dirs = Files.walk(modsPath)) {
            dirs.filter(p -> p.toString().endsWith(".js"))
                .sorted()
                .forEach(jsFile -> {
                    try {
                        String script = Files.readString(jsFile);
                        String name = modsPath.relativize(jsFile).toString();
                        loadedScripts.put(name, script);

                        Path modDir = jsFile;
                        while (modDir != null && !modDir.equals(modsPath)) {
                            if (Files.exists(modDir.resolve("mod.yaml"))) {
                                runtime.setModuleContext(modDir);
                                break;
                            }
                            modDir = modDir.getParent();
                        }

                        runtime.execute(script, name);
                    } catch (IOException e) {
                        log.error("加载失败: {}", jsFile, e);
                    }
                });
        } catch (IOException e) {
            log.error("扫描 JS 文件失败", e);
        }
        log.info("JS handlers 重新加载完成，共 {} 个脚本", loadedScripts.size());
        bus.fire("world.init", new GameEvent("world.init"));
        rebindExistingEntities();
    }

    private void rebindExistingEntities() {
        if (store == null) return;
        for (Entity entity : store.all()) {
            GameEvent scriptsReloaded = new GameEvent("entity.scripts_reloaded");
            scriptsReloaded.set("entity", entity);
            bus.fire("entity.scripts_reloaded", scriptsReloaded);

            GameEvent loaded = new GameEvent("entity.loaded");
            loaded.set("entity", entity);
            bus.fire("entity.loaded", loaded);
        }
    }

    private void registerRecursive(Path root, WatchService watcher) throws IOException {
        try (Stream<Path> dirs = Files.walk(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(watcher,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException e) {
                    log.warn("无法监听目录: {}", dir);
                }
            });
        }
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
