package com.epic.engine.save;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/save")
public class SaveController {

    private final PlayerStateRepository repository;

    public SaveController(PlayerStateRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerState> getState(@PathVariable String playerId) {
        return repository.findByPlayerId(playerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    PlayerState newState = new PlayerState(playerId, "village_square");
                    repository.save(newState);
                    return ResponseEntity.ok(newState);
                });
    }
}
