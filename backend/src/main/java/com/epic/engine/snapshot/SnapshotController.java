package com.epic.engine.snapshot;

import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.session.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final EventBus eventBus;
    private final SnapshotService snapshotService;
    private final SessionService sessionService;

    public SnapshotController(EventBus eventBus, SnapshotService snapshotService, SessionService sessionService) {
        this.eventBus = eventBus;
        this.snapshotService = snapshotService;
        this.sessionService = sessionService;
    }

    @PostMapping("/action")
    public WorldSnapshot performAction(
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @RequestBody Map<String, Object> request) {

        token = ensureToken(token);
        sessionService.touchSession(token);

        String type = (String) request.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        GameEvent actionEvent = new GameEvent("action." + type);
        actionEvent.set("sessionToken", token);
        actionEvent.set("type", type);
        params.forEach(actionEvent::set);

        var session = sessionService.getSession(token);
        if (session != null && session.activeCharacterId() != null) {
            actionEvent.set("playerId", session.activeCharacterId());
        }

        eventBus.fire("action." + type, actionEvent);

        // Check if handler wants to show a form (character creation)
        if (actionEvent.has("form")) {
            WorldSnapshot.FormData form = actionEvent.get("form");
            return WorldSnapshot.characterCreate(token, form, snapshotService.buildColorMap());
        }

        return snapshotService.buildSnapshot(token);
    }

    @GetMapping("/snapshot")
    public WorldSnapshot getSnapshot(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        token = ensureToken(token);
        sessionService.touchSession(token);
        return snapshotService.buildSnapshot(token);
    }

    private String ensureToken(String token) {
        if (token == null) {
            return sessionService.createSession();
        }
        if (sessionService.getSession(token) == null) {
            return sessionService.restoreSession(token);
        }
        return token;
    }
}
