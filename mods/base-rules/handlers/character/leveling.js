function xpForLevel(level) {
    return (typeof Progression !== "undefined") ? Progression.xpForLevel(level) : level * 100;
}

function registerLevelGrowthModifier(entityId, level) {
    var capturedLevel = level;
    engine.addModifier(entityId, {
        typeId: "level",
        id: "level_growth",
        label: level + "级成长",
        apply: function(entity) {
            var ch = entity.getComponent("Character");
            var p = entity.getComponent("PrimaryStats");
            if (ch === null || p === null) return;
            var classSchema = schemas.get(ch.getString("classId"));
            if (classSchema === null) return;
            var growth = (typeof effectiveGrowth !== "undefined") ? effectiveGrowth(entity) : classSchema.raw().get("growth");
            if (growth === null) return;
            var gained = capturedLevel - 1;   // L1 不加,每升一级加一份模板
            if (typeof growth.keySet === "function") {
                var keys = growth.keySet().iterator();
                while (keys.hasNext()) {
                    var stat = keys.next();
                    p.set(stat, p.getInt(stat) + growth.get(stat) * gained);
                }
            } else {
                var jsKeys = Object.keys(growth);
                for (var i = 0; i < jsKeys.length; i++) {
                    var k = jsKeys[i];
                    p.set(k, p.getInt(k) + parseInt(growth[k]) * gained);
                }
            }
        }
    });
}

// 调试脚手架(Feature #6 verify):一键设等级,跳过 gain_xp grind。复用 applySpec 重推
// 等级成长(spec-aware)+ recalc;回满血便于观察。verify 完可删(连同 DebugController /level)。
engine.on("debug.set_level", 100, function(event) {
    var entityId = event.get("entityId");
    var level = parseInt(event.get("level"));
    var entity = store.get(entityId);
    if (entity === null || level < 1) { event.set("ok", false); return; }
    var __cap = (typeof Progression !== "undefined") ? Progression.cap() : 100;
    if (level > __cap) level = __cap;
    var exp = entity.getComponent("Experience");
    if (exp !== null) exp.set("level", level);
    var ch = entity.getComponent("Character");
    if (ch !== null) ch.set("level", level);
    if (typeof applySkillLevelCurve !== "undefined") applySkillLevelCurve(entityId);
    if (typeof applySpec !== "undefined") {
        applySpec(entityId);
    } else if (typeof registerLevelGrowthModifier !== "undefined") {
        registerLevelGrowthModifier(entityId, level);
        engine.recalculate(entityId);
    }
    var hp = entity.getComponent("Health");
    if (hp !== null) hp.set("hp", hp.getInt("maxHp"));
    event.set("ok", true);
    if (typeof persistence !== "undefined" && persistence !== null) persistence.save(entity);
});

engine.on("action.gain_xp", 100, function(event) {
    var playerId = event.get("playerId");
    var amount = event.get("amount");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null) return;

    var currentXp = exp.getInt("xp") + amount;
    var currentLevel = exp.getInt("level");
    var cap = (typeof Progression !== "undefined") ? Progression.cap() : 100;

    var leveled = false;
    while (currentLevel < cap && currentXp >= xpForLevel(currentLevel)) {
        currentXp -= xpForLevel(currentLevel);
        currentLevel++;
        leveled = true;
    }
    if (currentLevel >= cap) { currentLevel = cap; currentXp = 0; }

    exp.set("level", currentLevel);
    exp.set("xp", currentXp);
    var charComp = player.getComponent("Character");
    if (charComp !== null) charComp.set("level", currentLevel);

    if (leveled) {
        registerLevelGrowthModifier(playerId, currentLevel);

        var levelUpEvent = engine.newEvent("entity.level_up");
        levelUpEvent.set("entity", player);
        levelUpEvent.set("level", currentLevel);
        engine.fire("entity.level_up", levelUpEvent);
    }

    persistence.save(player);
});

engine.on("action.allocate_point", 100, function(event) {
    var playerId = event.get("playerId");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null || exp.getInt("pendingPoints") <= 0) return;
    event.set("success", false);
    event.set("message", "手动加点暂未开放");
});
