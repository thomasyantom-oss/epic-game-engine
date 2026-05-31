// Start a combat encounter — creates enemy entities and combat state
engine.on("combat.start_encounter", 100, function(event) {
    var playerId = event.get("playerId");
    var encounterId = event.get("encounterId");

    var encounterData = engine.loadYaml("entities/encounters/" + encounterId + ".yaml");
    if (encounterData === null) return;

    var combatId = "combat_" + engine.now();

    // Create combat state entity
    var combatEntity = engine.createEntity(combatId);
    var combatState = engine.newComponent("CombatState");
    combatState.set("round", 1);
    combatState.set("phase", "COMMAND");
    combatEntity.addComponent(combatState);
    combatEntity.addTag("active_combat");
    store.add(combatEntity);

    // Initialize combat log with first round marker
    var logComp = engine.newComponent("CombatLog");
    var entries = engine.newList();
    var firstRound = engine.newList();
    var s = engine.newMap();
    s.put("text", "— 第 1 回合 —");
    s.put("color", "highlight");
    s.put("round", 1);
    firstRound.add(s);
    entries.add(firstRound);
    logComp.set("entries", entries);
    combatEntity.addComponent(logComp);

    // Tag the player as in this combat, clear old combat log
    var player = store.get(playerId);
    player.removeComponent("LastCombatLog");
    var playerPos = engine.newComponent("CombatPosition");
    playerPos.set("row", "FRONT");
    playerPos.set("slot", 1);
    player.addComponent(playerPos);
    player.addTag("combat:" + combatId);
    store.reindexTags(player);

    // Create enemy entities from encounter definition
    var enemies = encounterData.get("enemies");
    for (var i = 0; i < enemies.size(); i++) {
        var enemyDef = enemies.get(i);
        var enemyId = "" + enemyDef.get("id") + "_" + i;
        var enemy = engine.createEntity(enemyId);

        var health = engine.newComponent("Health");
        health.set("hp", enemyDef.get("hp"));
        health.set("maxHp", enemyDef.get("hp"));
        enemy.addComponent(health);

        var stats = engine.newComponent("CombatStats");
        stats.set("attack", enemyDef.get("attack"));
        stats.set("defense", enemyDef.get("defense"));
        stats.set("speed", enemyDef.get("speed"));
        enemy.addComponent(stats);

        var nameComp = engine.newComponent("Name");
        nameComp.set("value", enemyDef.get("name"));
        enemy.addComponent(nameComp);

        var combatPos = engine.newComponent("CombatPosition");
        combatPos.set("row", enemyDef.get("row") || "FRONT");
        combatPos.set("slot", enemyDef.get("slot") !== null ? enemyDef.get("slot") : i % 3);
        enemy.addComponent(combatPos);

        enemy.addTag("enemy");
        enemy.addTag("combat:" + combatId);
        store.add(enemy);
    }
});

// Handle combat command action from frontend
engine.on("action.combat_command", 100, function(event) {
    var playerId = event.get("playerId");
    if (playerId === null || playerId === undefined) return;
    var command = event.get("command");
    var targetId = event.get("targetId");

    var player = store.get(playerId);
    if (player === null) return;

    // Find active combat
    var combatId = null;
    var tags = player.getTags().toArray();
    for (var i = 0; i < tags.length; i++) {
        var tag = tags[i].toString();
        if (tag.indexOf("combat:") === 0) {
            combatId = tag.substring(7);
            break;
        }
    }
    if (combatId === null) return;

    // Handle FLEE — exit combat immediately
    if (command === "flee") {
        endCombat(player, combatId, "FLEE");
        return;
    }

    // Build commands map: player command + AI commands for enemies
    var commands = engine.newMap();

    var playerCmd = engine.newMap();
    playerCmd.put("type", command);
    if (targetId) playerCmd.put("targetId", targetId);
    if (event.has("targetRow")) playerCmd.put("targetRow", event.get("targetRow"));
    if (event.has("targetCol")) playerCmd.put("targetCol", event.get("targetCol"));
    commands.put(playerId, playerCmd);

    // Enemy AI
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasTag("enemy") && entity.hasComponent("Health") && entity.getComponent("Health").getInt("hp") > 0) {
            var enemyCmd = engine.newMap();
            enemyCmd.put("type", "basic_attack");
            enemyCmd.put("targetId", playerId);
            commands.put(entity.getId(), enemyCmd);
        }
    }

    // Resolve the round
    var resolveEvent = engine.newEvent("combat.resolve_round");
    resolveEvent.set("combatId", combatId);
    resolveEvent.set("commands", commands);
    engine.fire("combat.resolve_round", resolveEvent);

    // Check if combat ended
    var combat = store.get(combatId);
    var state = combat.getComponent("CombatState");
    var phase = state.getString("phase");
    if (phase === "VICTORY" || phase === "DEFEAT") {
        endCombat(player, combatId, phase);
    }
});

function endCombat(player, combatId, result) {
    var combat = store.get(combatId);

    // Save combat log to player for post-battle viewing
    if (combat !== null && combat.hasComponent("CombatLog")) {
        var log = combat.getComponent("CombatLog");
        var resultSegments = engine.newList();
        var rs = engine.newMap();
        var textMap = { "DEFEAT": "— 战斗失败 —", "VICTORY": "— 战斗胜利 —", "FLEE": "— 逃跑成功 —" };
        var colorMap = { "DEFEAT": "enemy", "VICTORY": "player", "FLEE": "highlight" };
        rs.put("text", textMap[result] || "— 战斗结束 —");
        rs.put("color", colorMap[result] || "highlight");
        resultSegments.add(rs);
        log.get("entries").add(resultSegments);

        player.removeComponent("LastCombatLog");
        var saved = engine.newComponent("LastCombatLog");
        saved.set("entries", log.get("entries"));
        player.addComponent(saved);
    }

    var combatants = store.getByTagAsList("combat:" + combatId);
    player.removeTag("combat:" + combatId);
    player.removeComponent("CombatPosition");
    store.reindexTags(player);

    // Strip combat-scoped (non-permanent) buffs so they don't leak into the next encounter
    var playerComps = player.getAllComponents();
    var buffIds = [];
    for (var b = 0; b < playerComps.size(); b++) {
        var comp = playerComps.get(b);
        if (!comp.getType().startsWith("Buff_")) continue;
        var permanent = comp.has("permanent") && comp.getBoolean("permanent");
        if (!permanent) buffIds.push(comp.getType().substring(5));
    }
    for (var bi = 0; bi < buffIds.length; bi++) {
        buffs.removeBuff(player.getId(), buffIds[bi]);
    }
    for (var j = 0; j < combatants.size(); j++) {
        var c = combatants.get(j);
        if (c.hasTag("enemy")) {
            store.remove(c.getId());
        }
    }
    store.remove(combatId);

    // On defeat, respawn with full stats
    if (result === "DEFEAT") {
        var health = player.getComponent("Health");
        if (health !== null) {
            health.set("hp", health.getInt("maxHp"));
        }
        var mana = player.getComponent("Mana");
        if (mana !== null) {
            mana.set("mp", mana.getInt("maxMp"));
        }
    }

    persistence.save(player);
}
