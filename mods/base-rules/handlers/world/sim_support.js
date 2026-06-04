// Simulator-only hook. Real gameplay never fires this event.
engine.on("sim.reset_player_modifiers", 100, function(event) {
    var playerId = event.get("playerId");
    var player = store.get(playerId);
    if (player === null) return;
    engine.clearChain(playerId);
    var loaded = engine.newEvent("entity.loaded");
    loaded.set("entity", player);
    engine.fire("entity.loaded", loaded);
});

engine.on("sim.apply_source", 100, function(event) {
    var playerId = event.get("playerId");
    var source = event.get("source");
    var player = store.get(playerId);
    if (player === null || source === null) return;

    var kind = source.get("kind");
    if (kind !== "modifier") return;

    var field = source.get("field");
    var valueStr = "" + source.get("value");
    var dot = field.indexOf(".");
    if (dot < 0) return;
    var compName = field.substring(0, dot);
    var fieldName = field.substring(dot + 1);
    var sourceId = event.has("sourceId")
        ? "" + event.get("sourceId")
        : "sim_src_" + engine.now() + "_" + compName + "_" + fieldName;

    engine.addModifier(playerId, {
        typeId: "sim_source",
        id: sourceId,
        label: "sim source " + field,
        priority: 250,
        apply: function(ent) {
            var comp = ent.getComponent(compName);
            if (comp === null) return;
            var current = comp.getInt(fieldName);
            if (valueStr.charAt(0) === "+") {
                comp.set(fieldName, current + parseInt(valueStr.substring(1)));
            } else {
                comp.set(fieldName, parseInt(valueStr));
            }
        }
    });
    engine.recalculate(playerId);
});

// Simulator encounters reuse content-authored enemy ids such as goblin1_0 across
// iterations. Clear their modifier chains before the real start_encounter handler
// recreates them, otherwise a removed entity's old chain can point at stale objects.
engine.on("sim.before_start_encounter", 100, function(event) {
    var encounterId = event.get("encounterId");
    var encounterData = engine.loadYaml("entities/encounters/" + encounterId + ".yaml");
    if (encounterData === null) return;
    var enemies = encounterData.get("enemies");
    for (var i = 0; i < enemies.size(); i++) {
        var enemyDef = enemies.get(i);
        engine.clearChain("" + enemyDef.get("id") + "_" + i);
    }
});

engine.on("sim.cleanup_combat", 100, function(event) {
    var enemyIds = event.get("enemyIds");
    if (enemyIds !== null) {
        for (var i = 0; i < enemyIds.size(); i++) {
            engine.clearChain("" + enemyIds.get(i));
        }
    }
    var playerId = event.get("playerId");
    if (playerId !== null) engine.clearChain(playerId);
});

function simWeaponAttack(base, attrValue, attrName) {
    return Math.ceil(base * (1 + Math.sqrt(Math.max(0, attrValue * weaponMult(attrName))) / 10));
}

// Simulator-only encounter scaling. Reads encounter.yaml sim_scaling and applies
// it through real modifiers, so derived stats still come from the normal path.
engine.on("sim.scale_encounter", 100, function(event) {
    var encounterId = event.get("encounterId");
    var level = event.get("level");
    var gained = level - 1;
    if (gained <= 0) return;

    var encounterData = engine.loadYaml("entities/encounters/" + encounterId + ".yaml");
    if (encounterData === null) return;
    var scaling = encounterData.get("sim_scaling");
    if (scaling === null) return;
    var perLevel = scaling.get("per_level");
    if (perLevel === null) return;
    var weaponAttr = scaling.get("weaponAttr");

    var enemies = encounterData.get("enemies");
    for (var i = 0; i < enemies.size(); i++) {
        var enemyDef = enemies.get(i);
        var enemyId = "" + enemyDef.get("id") + "_" + i;
        engine.addModifier(enemyId, {
            typeId: "sim_source",
            id: "sim_enemy_level_scaling",
            label: "sim enemy level scaling",
            priority: 250,
            apply: function(ent) {
                var primary = ent.getComponent("PrimaryStats");
                if (primary === null) return;
                var keys = perLevel.keySet().iterator();
                while (keys.hasNext()) {
                    var stat = keys.next();
                    primary.set(stat, primary.getInt(stat) + perLevel.get(stat) * gained);
                }
                if (weaponAttr !== null) primary.set("weaponAttr", weaponAttr);
                var combat = ent.getComponent("CombatStats");
                if (combat !== null) combat.set("defense", combat.getInt("defense") + gained);
            }
        });
        engine.addModifier(enemyId, {
            typeId: "sim_source",
            id: "sim_enemy_level_weapon",
            label: "sim enemy level weapon",
            priority: 350,
            apply: function(ent) {
                var primary = ent.getComponent("PrimaryStats");
                var combat = ent.getComponent("CombatStats");
                if (primary === null || combat === null) return;
                var attr = primary.has("weaponAttr") ? primary.getString("weaponAttr") : "力量";
                var attrVal = primary.has(attr) ? primary.getInt(attr) : 0;
                    combat.set("attack", simWeaponAttack(5 + Math.floor(gained * gained / 38), attrVal, attr));
            }
        });
        var enemy = store.get(enemyId);
        if (enemy !== null) {
            var health = enemy.getComponent("Health");
            if (health !== null) health.set("hp", health.getInt("maxHp"));
        }
    }
});

// Simulator-only player baseline weapon. The real game still uses existing equipment;
// this gives PM trend reports a level-scaled weapon without authoring 50 items.
engine.on("sim.apply_level_weapon", 100, function(event) {
    var playerId = event.get("playerId");
    var level = event.get("level");
    var player = store.get(playerId);
    if (player === null) return;
    engine.addModifier(playerId, {
        typeId: "sim_source",
        id: "sim_level_weapon",
        label: "sim level weapon",
        priority: 350,
        apply: function(ent) {
            var primary = ent.getComponent("PrimaryStats");
            var combat = ent.getComponent("CombatStats");
            if (primary === null || combat === null) return;
            var attr = primary.has("weaponAttr") ? primary.getString("weaponAttr") : "力量";
            var attrVal = primary.has(attr) ? primary.getInt(attr) : 0;
            combat.set("attack", simWeaponAttack(20, attrVal, attr));
            combat.set("defense", combat.getInt("defense") + Math.max(0, level - 1));
        }
    });
    engine.recalculate(playerId);
});
