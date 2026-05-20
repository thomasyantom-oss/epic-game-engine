package com.epic.engine.scene;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scene")
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping("/{sceneId}")
    public ResponseEntity<Scene> getScene(@PathVariable String sceneId) {
        Scene scene = sceneService.getScene(sceneId);
        if (scene == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scene);
    }
}
