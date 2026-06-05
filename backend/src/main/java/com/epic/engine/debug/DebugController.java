package com.epic.engine.debug;

import com.epic.engine.core.Entity;
import com.epic.engine.core.EntityStore;
import com.epic.engine.core.EventBus;
import com.epic.engine.core.GameEvent;
import com.epic.engine.session.SessionData;
import com.epic.engine.session.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final EntityStore entityStore;
    private final GameEventLog eventLog;
    private final EventBus eventBus;
    private final SessionService sessionService;

    public DebugController(EntityStore entityStore, GameEventLog eventLog,
                           EventBus eventBus, SessionService sessionService) {
        this.entityStore = entityStore;
        this.eventLog = eventLog;
        this.eventBus = eventBus;
        this.sessionService = sessionService;
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

    @PostMapping("/passive/{token}")
    public ResponseEntity<Map<String, Object>> grantPassive(@PathVariable String token, @RequestParam String base) {
        String playerId = activeCharacter(token);
        if (playerId == null) return ResponseEntity.notFound().build();
        GameEvent event = new GameEvent("debug.grant_passive");
        event.set("entityId", playerId);
        event.set("base", base);
        eventBus.fire("debug.grant_passive", event);
        return ResponseEntity.ok(Map.of("ok", Boolean.TRUE.equals(event.get("ok"))));
    }

    @PostMapping("/skill-level/{token}")
    public ResponseEntity<Map<String, Object>> setSkillLevel(@PathVariable String token,
                                                             @RequestParam String base,
                                                             @RequestParam int level) {
        String playerId = activeCharacter(token);
        if (playerId == null) return ResponseEntity.notFound().build();
        GameEvent event = new GameEvent("debug.set_skill_level");
        event.set("entityId", playerId);
        event.set("base", base);
        event.set("level", level);
        eventBus.fire("debug.set_skill_level", event);
        return ResponseEntity.ok(Map.of("ok", Boolean.TRUE.equals(event.get("ok"))));
    }

    // 调试脚手架(Feature #7 verify 用):给分型灵魂球,真供给留到后续内容/掉落系统。verify 完可删。
    @PostMapping("/orb/{token}")
    public ResponseEntity<Map<String, Object>> grantOrb(@PathVariable String token,
                                                        @RequestParam String type,
                                                        @RequestParam int count) {
        String playerId = activeCharacter(token);
        if (playerId == null) return ResponseEntity.notFound().build();
        GameEvent event = new GameEvent("debug.grant_orb");
        event.set("entityId", playerId);
        event.set("type", type);
        event.set("count", count);
        eventBus.fire("debug.grant_orb", event);
        return ResponseEntity.ok(Map.of("ok", Boolean.TRUE.equals(event.get("ok"))));
    }

    // 调试脚手架(Feature #6 verify 用):一键设角色等级,跳过 gain_xp grind,
    // 便于真人验证专精等级门(L10/L50)与选定后的属性/成长变化。verify 完可删。
    @PostMapping("/level/{token}")
    public ResponseEntity<Map<String, Object>> setLevel(@PathVariable String token,
                                                        @RequestParam int level) {
        String playerId = activeCharacter(token);
        if (playerId == null) return ResponseEntity.notFound().build();
        GameEvent event = new GameEvent("debug.set_level");
        event.set("entityId", playerId);
        event.set("level", level);
        eventBus.fire("debug.set_level", event);
        return ResponseEntity.ok(Map.of("ok", Boolean.TRUE.equals(event.get("ok"))));
    }

    private String activeCharacter(String token) {
        SessionData session = sessionService.getSession(token);
        return session != null ? session.activeCharacterId() : null;
    }
}
