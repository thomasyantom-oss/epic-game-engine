package com.epic.engine.mod;

import com.epic.engine.map.MapData;
import com.epic.engine.map.TerrainDefinition;
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
    private final Map<String, Map<String, Object>> encounters = new LinkedHashMap<>();
    private final Map<Character, TerrainDefinition> terrains = new LinkedHashMap<>();
    private final Map<String, MapData> maps = new LinkedHashMap<>();

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

    public Optional<Map<String, Object>> getEncounter(String encounterId) {
        return Optional.ofNullable(encounters.get(encounterId));
    }

    public Map<Character, TerrainDefinition> getTerrains() {
        return Collections.unmodifiableMap(terrains);
    }

    public Optional<MapData> getMap(String mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public Collection<String> getAllMapIds() {
        return Collections.unmodifiableSet(maps.keySet());
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

        Path encountersDir = mod.path().resolve("encounters");
        if (Files.isDirectory(encountersDir)) {
            Yaml yaml = new Yaml();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(encountersDir, "*.yaml")) {
                for (Path encounterFile : stream) {
                    try (InputStream is = Files.newInputStream(encounterFile)) {
                        Map<String, Object> encounterData = yaml.load(is);
                        String id = (String) encounterData.get("id");
                        encounters.put(id, encounterData);
                    }
                }
            }
        }

        // Load terrains.yaml
        Path terrainsFile = mod.path().resolve("terrains.yaml");
        if (Files.exists(terrainsFile)) {
            Yaml yaml = new Yaml();
            try (InputStream is = Files.newInputStream(terrainsFile)) {
                Map<String, Object> data = yaml.load(is);
                Map<String, Map<String, Object>> terrainDefs = (Map<String, Map<String, Object>>) data.get("terrains");
                if (terrainDefs != null) {
                    for (var entry : terrainDefs.entrySet()) {
                        char ch = entry.getKey().charAt(0);
                        Map<String, Object> def = entry.getValue();
                        terrains.put(ch, new TerrainDefinition(
                                (String) def.get("id"),
                                ch,
                                def.containsKey("requires") ? (List<String>) def.get("requires") : List.of(),
                                (String) def.get("color"),
                                (String) def.get("text-color"),
                                def.containsKey("move-cost") ? ((Number) def.get("move-cost")).doubleValue() : 1.0
                        ));
                    }
                }
            }
        }

        // Load maps
        Path mapsDir = mod.path().resolve("maps");
        if (Files.isDirectory(mapsDir)) {
            Yaml yaml = new Yaml();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(mapsDir, "*.yaml")) {
                for (Path mapFile : stream) {
                    try (InputStream is = Files.newInputStream(mapFile)) {
                        Map<String, Object> data = yaml.load(is);
                        String id = (String) data.get("id");
                        String name = (String) data.get("name");
                        int width = (int) data.get("width");
                        int height = (int) data.get("height");
                        List<String> terrainRows = (List<String>) data.get("terrain");
                        char[][] grid = new char[height][width];
                        for (int y = 0; y < height; y++) {
                            String row = terrainRows.get(y);
                            for (int x = 0; x < width; x++) {
                                grid[y][x] = row.charAt(x);
                            }
                        }
                        List<Map<String, Object>> poisRaw = (List<Map<String, Object>>) data.getOrDefault("pois", List.of());
                        List<MapData.PointOfInterest> pois = poisRaw.stream()
                                .map(p -> new MapData.PointOfInterest(
                                        (String) p.get("id"),
                                        (int) p.get("x"),
                                        (int) p.get("y"),
                                        (String) p.get("type"),
                                        (String) p.get("target"),
                                        (String) p.get("label")
                                )).toList();
                        Map<String, Object> spawn = (Map<String, Object>) data.get("spawn");
                        maps.put(id, new MapData(id, name, width, height, grid, pois,
                                (int) spawn.get("x"), (int) spawn.get("y")));
                    }
                }
            }
        }
    }
}
