package com.epic.engine.debug;

import com.epic.engine.mod.ModRegistry;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final PlayerStateRepository playerStateRepository;
    private final ModRegistry modRegistry;
    private final GameEventLog eventLog;

    public DebugController(PlayerStateRepository playerStateRepository,
                           ModRegistry modRegistry,
                           GameEventLog eventLog) {
        this.playerStateRepository = playerStateRepository;
        this.modRegistry = modRegistry;
        this.eventLog = eventLog;
    }

    @GetMapping("/state/{playerId}")
    public ResponseEntity<Map<String, Object>> getPlayerDebugState(@PathVariable String playerId) {
        return playerStateRepository.findByPlayerId(playerId)
                .map(state -> ResponseEntity.ok(Map.<String, Object>of(
                        "playerId", state.getPlayerId(),
                        "currentScene", state.getCurrentScene(),
                        "sceneExists", modRegistry.getScene(state.getCurrentScene()).isPresent()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/log")
    public List<GameEventLog.LogEntry> getRecentLog(
            @RequestParam(defaultValue = "50") int count) {
        return eventLog.getRecentEntries(count);
    }

    @GetMapping("/log/{playerId}")
    public List<GameEventLog.LogEntry> getPlayerLog(
            @PathVariable String playerId,
            @RequestParam(defaultValue = "50") int count) {
        return eventLog.getEntriesForPlayer(playerId, count);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "running",
                "scenes", modRegistry.getAllSceneIds().size(),
                "players", playerStateRepository.count()
        );
    }
}
