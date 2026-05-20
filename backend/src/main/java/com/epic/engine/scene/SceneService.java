package com.epic.engine.scene;

import com.epic.engine.mod.ModRegistry;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SceneService {

    private final ModRegistry modRegistry;

    public SceneService(ModRegistry modRegistry) {
        this.modRegistry = modRegistry;
    }

    @SuppressWarnings("unchecked")
    public Scene getScene(String sceneId) {
        Optional<Map<String, Object>> raw = modRegistry.getScene(sceneId);
        if (raw.isEmpty()) {
            return null;
        }
        Map<String, Object> data = raw.get();
        String id = (String) data.get("id");

        List<Map<String, String>> descRaw = (List<Map<String, String>>) data.get("description");
        List<TextSegment> description = descRaw.stream()
                .map(seg -> new TextSegment(seg.get("text"), seg.get("color")))
                .toList();

        List<Map<String, Object>> actionsRaw = (List<Map<String, Object>>) data.getOrDefault("actions", Collections.emptyList());
        List<SceneAction> actions = actionsRaw.stream()
                .map(a -> new SceneAction(
                        (String) a.get("id"),
                        (String) a.get("label"),
                        (String) a.get("type"),
                        Map.of("target", (String) a.getOrDefault("target", ""))
                ))
                .toList();

        return new Scene(id, description, actions);
    }
}
