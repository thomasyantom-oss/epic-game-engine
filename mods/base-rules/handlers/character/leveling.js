function xpForLevel(level) {
    return level * 100;
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
            var growth = classSchema.raw().get("growth");
            if (growth === null) return;
            var gained = capturedLevel - 1;   // L1 不加,每升一级加一份模板
            var keys = growth.keySet().iterator();
            while (keys.hasNext()) {
                var stat = keys.next();
                p.set(stat, p.getInt(stat) + growth.get(stat) * gained);
            }
        }
    });
}

engine.on("action.gain_xp", 100, function(event) {
    var playerId = event.get("playerId");
    var amount = event.get("amount");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null) return;

    var currentXp = exp.getInt("xp") + amount;
    var currentLevel = exp.getInt("level");
    var threshold = xpForLevel(currentLevel);

    if (currentXp >= threshold) {
        currentXp -= threshold;
        currentLevel++;
        exp.set("level", currentLevel);
        exp.set("xp", currentXp);

        var charComp = player.getComponent("Character");
        if (charComp !== null) charComp.set("level", currentLevel);

        registerLevelGrowthModifier(playerId, currentLevel);

        var levelUpEvent = engine.newEvent("entity.level_up");
        levelUpEvent.set("entity", player);
        levelUpEvent.set("level", currentLevel);
        engine.fire("entity.level_up", levelUpEvent);
    } else {
        exp.set("xp", currentXp);
    }

    persistence.save(player);
});

engine.on("action.allocate_point", 100, function(event) {
    var playerId = event.get("playerId");
    var stat = event.get("stat");
    var player = store.get(playerId);
    if (player === null) return;

    var exp = player.getComponent("Experience");
    if (exp === null || exp.getInt("pendingPoints") <= 0) return;

    exp.set("pendingPoints", exp.getInt("pendingPoints") - 1);

    var isHealthStat = (stat === "maxHp");

    engine.addModifier(playerId, {
        typeId: "level",
        id: "level_growth",
        label: exp.getInt("level") + "级成长（含加点）",
        apply: function(entity) {
            var level = entity.getComponent("Experience").getInt("level");
            var health = entity.getComponent("Health");
            if (health !== null) {
                health.set("maxHp", health.getInt("maxHp") + level * 5);
            }
            var stats2 = entity.getComponent("CombatStats");
            if (stats2 !== null) {
                stats2.set("attack", stats2.getInt("attack") + level);
            }
            var allocComp = entity.getComponent(isHealthStat ? "Health" : "CombatStats");
            if (allocComp !== null && allocComp.has(stat)) {
                allocComp.set(stat, allocComp.getInt(stat) + 1);
            }
        }
    });

    persistence.save(player);
});
