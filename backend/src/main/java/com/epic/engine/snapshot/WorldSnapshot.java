package com.epic.engine.snapshot;

import java.util.List;
import java.util.Map;

public record WorldSnapshot(
        String phase,
        String sessionToken,
        String playerId,
        ActionResult result,
        List<CharacterInfo> characters,
        Integer maxSlots,
        FormData form,
        List<StatusBar> statusBars,
        List<BuffEntry> buffs,
        MapSnapshot map,
        CombatSnapshot combat,
        List<ActionOption> actions,
        List<LogEntry> log
) {
    public record ActionResult(boolean success, String message) {}
    public record CharacterInfo(String id, String name, int level, String classId, String classLabel) {}
    public record FormData(List<FormField> fields) {}
    public record FormField(String name, String label, String type, boolean required, List<FormOption> options) {}
    public record FormOption(String value, String label, String description) {}
    public record StatusBar(String id, String label, int current, int max, String color, int priority) {}
    public record BuffEntry(String id, String name, String remaining, int priority) {}
    public record MapSnapshot(String mapId, String mapName, int playerX, int playerY, int width, int height,
                               List<String> terrain, Map<String, TerrainInfo> terrains,
                               String currentTerrain, String currentTerrainName,
                               List<PoiInfo> pois) {}
    public record TerrainInfo(String color, String textColor, List<String> requires, double moveCost) {}
    public record PoiInfo(String id, int x, int y, String type, String target, String label) {}
    public record CombatSnapshot(String combatId, String phase, int round, List<CombatantInfo> combatants) {}
    public record CombatantInfo(String id, String name, String side, int hp, int maxHp, boolean alive) {}
    public record ActionOption(String type, String label, Map<String, Object> params) {}
    public record LogEntry(List<TextSegment> segments, Integer round) {
        public LogEntry(List<TextSegment> segments) { this(segments, null); }
    }
    public record TextSegment(String text, String color) {}

    public static WorldSnapshot characterSelect(String sessionToken, List<CharacterInfo> characters, int maxSlots) {
        return new WorldSnapshot("character_select", sessionToken, null,
                new ActionResult(true, "ok"), characters, maxSlots, null,
                null, null, null, null, null, null);
    }

    public static WorldSnapshot characterCreate(String sessionToken, FormData form) {
        return new WorldSnapshot("character_create", sessionToken, null,
                new ActionResult(true, "ok"), null, null, form,
                null, null, null, null, null, null);
    }

    public static WorldSnapshot inGame(String sessionToken, String playerId, ActionResult result,
                                        List<StatusBar> statusBars, List<BuffEntry> buffs,
                                        MapSnapshot map, CombatSnapshot combat,
                                        List<ActionOption> actions, List<LogEntry> log) {
        return new WorldSnapshot("in_game", sessionToken, playerId, result,
                null, null, null, statusBars, buffs, map, combat, actions, log);
    }
}
