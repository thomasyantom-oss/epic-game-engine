package com.epic.engine.module;

import com.epic.engine.script.ScriptRuntime;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class ModuleLoader {

    private final Path modsPath;
    private final ScriptRuntime runtime;

    public ModuleLoader(Path modsPath, ScriptRuntime runtime) {
        this.modsPath = modsPath.toAbsolutePath();
        this.runtime = runtime;
    }

    @SuppressWarnings("unchecked")
    public List<ModuleDescriptor> discoverModules() throws IOException {
        List<ModuleDescriptor> modules = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path modFile = entry.resolve("mod.yaml");
                    if (Files.exists(modFile)) {
                        modules.add(parseDescriptor(modFile, entry));
                    }
                }
            }
        }
        modules.sort(Comparator.comparingInt(ModuleDescriptor::loadOrder));
        return modules;
    }

    public void loadAll() throws IOException {
        List<ModuleDescriptor> modules = discoverModules();
        for (ModuleDescriptor mod : modules) {
            loadModule(mod);
        }
    }

    private void loadModule(ModuleDescriptor mod) throws IOException {
        runtime.setModuleContext(mod.path());
        Path handlersDir = mod.path().resolve("handlers");
        if (!Files.isDirectory(handlersDir)) return;

        try (Stream<Path> files = Files.walk(handlersDir)) {
            files.filter(p -> p.toString().endsWith(".js"))
                 .sorted()
                 .forEach(jsFile -> {
                     try {
                         String script = Files.readString(jsFile);
                         runtime.execute(script, mod.id() + "/" + modsPath.relativize(jsFile));
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to load handler: " + jsFile, e);
                     }
                 });
        }
    }

    @SuppressWarnings("unchecked")
    private ModuleDescriptor parseDescriptor(Path modFile, Path modDir) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(modFile)) {
            Map<String, Object> data = yaml.load(is);
            return new ModuleDescriptor(
                    (String) data.get("id"),
                    (String) data.get("name"),
                    (String) data.get("version"),
                    data.containsKey("load-order") ? ((Number) data.get("load-order")).intValue() : 0,
                    data.containsKey("dependencies") ? (List<String>) data.get("dependencies") : List.of(),
                    modDir
            );
        }
    }
}
