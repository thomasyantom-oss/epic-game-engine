package com.epic.engine.snapshot;

import java.util.List;
import java.util.Map;

public record WorldSnapshot(
        String playerId,
        ActionResult result,
        List<StatusBar> statusBars,
        List<BuffEntry> buffs,
        MapSnapshot map,
        CombatSnapshot combat,
        List<ActionOption> actions,
        List<LogEntry> log
) {
    public record ActionResult(boolean success, String message) {}
    public record StatusBar(String id, String label, int current, int max, String color, int priority) {}
    public record BuffEntry(String id, String name, String remaining, int priority) {}
    public record MapSnapshot(String mapId, int playerX, int playerY, int width, int height) {}
    public record CombatSnapshot(String combatId, String phase, int round, List<CombatantInfo> combatants) {}
    public record CombatantInfo(String id, String name, String side, int hp, int maxHp, boolean alive) {}
    public record ActionOption(String type, String label, Map<String, Object> params) {}
    public record LogEntry(List<TextSegment> segments) {}
    public record TextSegment(String text, String color) {}
}
