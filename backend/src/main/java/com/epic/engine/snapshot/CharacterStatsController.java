package com.epic.engine.snapshot;

import com.epic.engine.core.*;
import com.epic.engine.session.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/character")
public class CharacterStatsController {

    private final EntityStore entityStore;
    private final SessionService sessionService;
    private final ModifierChainService modifierChainService;

    public CharacterStatsController(EntityStore entityStore, SessionService sessionService,
                                     ModifierChainService modifierChainService) {
        this.entityStore = entityStore;
        this.sessionService = sessionService;
        this.modifierChainService = modifierChainService;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null) return Map.of();

        var session = sessionService.getSession(token);
        if (session == null || session.activeCharacterId() == null) return Map.of();

        String playerId = session.activeCharacterId();
        Entity player = entityStore.get(playerId);
        if (player == null) return Map.of();

        Map<String, Component> baseState = modifierChainService.getBaseState(playerId);
        List<ModifierDiff> contributions = modifierChainService.getContributions(playerId);

        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Long> baseValues = new LinkedHashMap<>();
        if (baseState != null) {
            baseState.forEach((compType, comp) ->
                comp.getAll().forEach((field, val) -> {
                    if (val instanceof Number n) {
                        baseValues.put(compType + "." + field, n.longValue());
                    }
                })
            );
        }

        Set<String> allKeys = new LinkedHashSet<>(baseValues.keySet());
        for (ModifierDiff diff : contributions) {
            diff.componentDeltas().forEach((comp, fields) ->
                fields.keySet().forEach(f -> allKeys.add(comp + "." + f))
            );
        }

        for (String key : allKeys) {
            List<Map<String, Object>> breakdown = new ArrayList<>();
            long base = baseValues.getOrDefault(key, 0L);
            breakdown.add(Map.of("label", "基础", "value", base, "type", "base"));

            for (ModifierDiff diff : contributions) {
                String[] parts = key.split("\\.", 2);
                String compType = parts[0];
                String fieldName = parts[1];
                Map<String, Long> compDelta = diff.componentDeltas().getOrDefault(compType, Map.of());
                Long delta = compDelta.get(fieldName);
                if (delta != null && delta != 0) {
                    breakdown.add(Map.of(
                        "label", diff.label() != null ? diff.label() : diff.modifierId(),
                        "value", delta,
                        "type", diff.typeId() != null ? diff.typeId() : "modifier"
                    ));
                }
            }

            String[] parts = key.split("\\.", 2);
            Component comp = player.getComponent(parts[0]);
            long finalVal = comp != null && comp.has(parts[1])
                    ? ((Number) comp.get(parts[1])).longValue() : base;

            result.put(key, Map.of("final", finalVal, "breakdown", breakdown));
        }

        return result;
    }
}
