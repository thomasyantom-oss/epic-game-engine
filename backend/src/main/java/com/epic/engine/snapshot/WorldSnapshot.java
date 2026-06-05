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
        List<LogEntry> log,
        Map<String, String> colors,
        Integer pendingPoints,
        EquipmentData equipment,
        List<ClassPreview> classPreviews,
        Skillbook skillbook,
        Specialization specialization,
        TalentTree talentTree
) {
    public record ActionResult(boolean success, String message) {}
    public record CharacterInfo(String id, String name, int level, String classId, String classLabel,
                                Map<String, Object> primaryStats, Map<String, Object> growth,
                                String description, String portrait) {}
    public record ClassPreview(String id, String label, String description,
                               Map<String, Object> growth, Map<String, String> modifiers,
                               String portrait) {}
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
    public record CombatSnapshot(String combatId, String phase, int round, int turnTimer,
                                     List<CombatantInfo> combatants, List<CombatEvent> events) {}
    public record CombatantInfo(String id, String name, String side, int level, String typeLabel,
                                int hp, int maxHp, int mp, int maxMp, boolean alive,
                                List<BuffInfo> buffs, String row, int slot, String hpColor, String mpColor) {}
    public record BuffInfo(String id, int stacks, String color, boolean positive, int remaining) {}
    public record CombatEvent(List<TextSegment> segments, List<Effect> effects, List<Map<String, Object>> animation, int logCount) {}
    public record Effect(String target, String type, Map<String, Object> data) {}
    public record ActionOption(String type, String label, Map<String, Object> params, String color, String style) {
        public ActionOption(String type, String label, Map<String, Object> params) {
            this(type, label, params, null, null);
        }
    }
    public record LogEntry(List<TextSegment> segments, Integer round) {
        public LogEntry(List<TextSegment> segments) { this(segments, null); }
    }
    public record TextSegment(String text, String color) {}

    public record ItemInfo(String id, String name, String type, String rarity, String rarityColor,
                           Map<String, Integer> stats) {}
    public record EquipmentData(Map<String, ItemInfo> slots, List<ItemInfo> inventory) {}
    public record SkillEntry(String base, String name, String description, String icon,
                             boolean equipped, String node, int level, String kind, String source) {}
    public record Skillbook(int slots, int equippedCount, List<SkillEntry> known) {}
    public record SpecOption(String id, String label, String description, SpecEffects effects) {}
    public record SpecEffects(String mainAttr, Map<String, Object> growth, List<String> grantPassives) {}
    public record SpecPending(int tier, int requiresLevel, List<SpecOption> options) {}
    public record SpecPathNode(String id, String label) {}
    public record SpecLocked(int tier, int requiresLevel) {}
    public record Specialization(List<SpecPathNode> path, SpecPending pending, List<SpecLocked> locked) {}
    public record TalentTree(String root, TalentPoints points, List<TalentNode> nodes, List<OrbStack> orbInventory) {}
    public record TalentPoints(int total, int spent, int available) {}
    public record OrbStack(String type, int count) {}
    public record TalentNode(String id, String label, String icon, String type,
                             List<String> parents, List<String> children,
                             int tier, int order, String state, boolean selected,
                             String requiresSpec, String effectSummary,
                             TalentSlot slot, TalentActions actions) {}
    public record TalentSlot(String skill, String orbType, int orbCount, String evolution, boolean filled) {}
    public record TalentActions(boolean canUnlock, boolean canPlaceOrb, String reason) {}

    public static WorldSnapshot characterSelect(String sessionToken, List<CharacterInfo> characters, int maxSlots, Map<String, String> colors, List<ClassPreview> classPreviews) {
        return new WorldSnapshot("character_select", sessionToken, null,
                new ActionResult(true, "ok"), characters, maxSlots, null,
                null, null, null, null, null, null, colors, null, null, classPreviews, null, null, null);
    }

    public static WorldSnapshot characterCreate(String sessionToken, FormData form, Map<String, String> colors, List<ClassPreview> classPreviews) {
        return new WorldSnapshot("character_create", sessionToken, null,
                new ActionResult(true, "ok"), null, null, form,
                null, null, null, null, null, null, colors, null, null, classPreviews, null, null, null);
    }

    public static WorldSnapshot inGame(String sessionToken, String playerId, ActionResult result,
                                        List<StatusBar> statusBars, List<BuffEntry> buffs,
                                        MapSnapshot map, CombatSnapshot combat,
                                        List<ActionOption> actions, List<LogEntry> log,
                                        Map<String, String> colors,
                                        Integer pendingPoints, EquipmentData equipment,
                                        Skillbook skillbook,
                                        Specialization specialization,
                                        TalentTree talentTree) {
        return new WorldSnapshot("in_game", sessionToken, playerId, result,
                null, null, null, statusBars, buffs, map, combat, actions, log, colors,
                pendingPoints, equipment, null, skillbook, specialization, talentTree);
    }
}
