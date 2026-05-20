package com.epic.engine.scene;

import com.epic.engine.debug.GameEventLog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scene")
public class SceneController {

    private final SceneService sceneService;
    private final GameEventLog eventLog;

    public SceneController(SceneService sceneService, GameEventLog eventLog) {
        this.sceneService = sceneService;
        this.eventLog = eventLog;
    }

    @GetMapping("/{sceneId}")
    public ResponseEntity<Scene> getScene(@PathVariable String sceneId) {
        Scene scene = sceneService.getScene(sceneId);
        if (scene == null) {
            eventLog.logEvent("system", "scene_load", sceneId, "FAIL: not found");
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scene);
    }
}
