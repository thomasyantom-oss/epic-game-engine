package com.epic.engine.mod;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class ModRegistry {

    private final ModLoader modLoader;
    private final Map<String, Map<String, Object>> scenes = new LinkedHashMap<>();

    public ModRegistry(ModLoader modLoader) {
        this.modLoader = modLoader;
    }

    @PostConstruct
    public void initialize() throws IOException {
        List<ModDescriptor> mods = modLoader.discoverMods();
        for (ModDescriptor mod : mods) {
            loadModContent(mod);
        }
    }

    public Optional<Map<String, Object>> getScene(String sceneId) {
        return Optional.ofNullable(scenes.get(sceneId));
    }

    public Collection<String> getAllSceneIds() {
        return Collections.unmodifiableSet(scenes.keySet());
    }

    @SuppressWarnings("unchecked")
    private void loadModContent(ModDescriptor mod) throws IOException {
        Path scenesDir = mod.path().resolve("scenes");
        if (Files.isDirectory(scenesDir)) {
            Yaml yaml = new Yaml();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scenesDir, "*.yaml")) {
                for (Path sceneFile : stream) {
                    try (InputStream is = Files.newInputStream(sceneFile)) {
                        Map<String, Object> sceneData = yaml.load(is);
                        String id = (String) sceneData.get("id");
                        scenes.put(id, sceneData);
                    }
                }
            }
        }
    }
}
