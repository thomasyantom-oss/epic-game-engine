package com.epic.engine.mod;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ModLoader {

    private final Path modsPath;

    @Autowired
    public ModLoader(@Value("${epic.mods-path:../mods}") String modsPath) {
        this.modsPath = Path.of(modsPath).toAbsolutePath();
    }

    public ModLoader(Path modsPath) {
        this.modsPath = modsPath;
    }

    @SuppressWarnings("unchecked")
    public List<ModDescriptor> discoverMods() throws IOException {
        List<ModDescriptor> mods = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path modFile = entry.resolve("mod.yaml");
                    if (Files.exists(modFile)) {
                        mods.add(parseModDescriptor(modFile, entry));
                    }
                }
            }
        }
        mods.sort(Comparator.comparingInt(ModDescriptor::loadOrder));
        return mods;
    }

    @SuppressWarnings("unchecked")
    private ModDescriptor parseModDescriptor(Path modFile, Path modDir) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(modFile)) {
            Map<String, Object> data = yaml.load(is);
            return new ModDescriptor(
                    (String) data.get("id"),
                    (String) data.get("name"),
                    (String) data.get("version"),
                    (String) data.get("description"),
                    data.containsKey("load-order") ? (int) data.get("load-order") : 0,
                    data.containsKey("dependencies") ? (List<String>) data.get("dependencies") : List.of(),
                    modDir
            );
        }
    }
}
