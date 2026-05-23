package com.epic.engine.debug;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final EntityStore entityStore;
    private final GameEventLog eventLog;

    public DebugController(EntityStore entityStore, GameEventLog eventLog) {
        this.entityStore = entityStore;
        this.eventLog = eventLog;
    }

    @GetMapping("/state/{entityId}")
    public ResponseEntity<Map<String, Object>> getEntityState(@PathVariable String entityId) {
        Entity entity = entityStore.get(entityId);
        if (entity == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "id", entity.getId(),
                "tags", entity.getTags(),
                "components", entity.getAllComponents().stream()
                        .map(c -> Map.of("type", c.getType(), "data", c.getAll()))
                        .toList()
        ));
    }

    @GetMapping("/log")
    public List<GameEventLog.LogEntry> getRecentLog(
            @RequestParam(defaultValue = "50") int count) {
        return eventLog.getRecentEntries(count);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "running",
                "entities", entityStore.all().size()
        );
    }
}
