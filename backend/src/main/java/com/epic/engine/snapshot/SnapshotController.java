package com.epic.engine.snapshot;

import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final EventBus eventBus;
    private final SnapshotService snapshotService;

    public SnapshotController(EventBus eventBus, SnapshotService snapshotService) {
        this.eventBus = eventBus;
        this.snapshotService = snapshotService;
    }

    @PostMapping("/v2/action")
    public WorldSnapshot performAction(@RequestBody Map<String, Object> request) {
        String playerId = (String) request.get("playerId");
        String type = (String) request.get("type");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        GameEvent actionEvent = new GameEvent("action." + type);
        actionEvent.set("playerId", playerId);
        actionEvent.set("type", type);
        params.forEach(actionEvent::set);
        eventBus.fire("action." + type, actionEvent);

        boolean success = !actionEvent.has("error");
        String message = actionEvent.has("error") ? actionEvent.get("error") : "ok";

        return snapshotService.buildSnapshot(playerId, new WorldSnapshot.ActionResult(success, message));
    }

    @GetMapping("/snapshot/{playerId}")
    public WorldSnapshot getSnapshot(@PathVariable String playerId) {
        return snapshotService.buildSnapshot(playerId, new WorldSnapshot.ActionResult(true, "ok"));
    }
}
