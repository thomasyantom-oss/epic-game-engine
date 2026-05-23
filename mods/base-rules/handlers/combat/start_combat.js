// Start a combat encounter — creates enemy entities and combat state
engine.on("combat.start_encounter", 100, function(event) {
    var playerId = event.get("playerId");
    var encounterId = event.get("encounterId");

    // Load encounter definition
    var encounterData = engine.loadYaml("entities/encounters/" + encounterId + ".yaml");
    if (encounterData === null) return;

    var combatId = "combat_" + engine.now();

    // Create combat state entity
    var combatEntity = engine.createEntity(combatId);
    var combatState = engine.newComponent("CombatState");
    combatState.set("round", 0);
    combatState.set("phase", "COMMAND");
    combatEntity.addComponent(combatState);
    combatEntity.addTag("active_combat");
    store.add(combatEntity);

    // Tag the player as in this combat
    var player = store.get(playerId);
    player.addTag("combat:" + combatId);
    store.reindexTags(player);

    // Create enemy entities from encounter definition
    var enemies = encounterData.get("enemies");
    for (var i = 0; i < enemies.size(); i++) {
        var enemyDef = enemies.get(i);
        var enemyId = enemyDef.get("id") + "_" + engine.now() + "_" + i;
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

        enemy.addTag("enemy");
        enemy.addTag("combat:" + combatId);
        store.add(enemy);
    }
});

// Handle combat command action from frontend
engine.on("action.combat_command", 100, function(event) {
    var playerId = event.get("playerId");
    var command = event.get("command");
    var targetId = event.get("targetId");

    // Find the active combat for this player
    var player = store.get(playerId);
    if (player === null) return;

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

    // Build commands map: player command + AI commands for enemies
    var commands = engine.newMap();

    // Player command
    var playerCmd = engine.newMap();
    playerCmd.put("type", command);
    if (targetId) playerCmd.put("targetId", targetId);
    commands.put(playerId, playerCmd);

    // Enemy AI: each alive enemy attacks the player
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasTag("enemy") && entity.hasComponent("Health") && entity.getComponent("Health").getInt("hp") > 0) {
            var enemyCmd = engine.newMap();
            enemyCmd.put("type", "ATTACK");
            enemyCmd.put("targetId", playerId);
            commands.put(entity.getId(), enemyCmd);
        }
    }

    // Resolve the round
    var resolveEvent = engine.newEvent("combat.resolve_round");
    resolveEvent.set("combatId", combatId);
    resolveEvent.set("commands", commands);
    engine.fire("combat.resolve_round", resolveEvent);

    // Check if combat ended - clean up if VICTORY/DEFEAT
    var combat = store.get(combatId);
    var state = combat.getComponent("CombatState");
    var phase = state.getString("phase");
    if (phase === "VICTORY" || phase === "DEFEAT") {
        // Remove combat tags from player
        player.removeTag("combat:" + combatId);
        store.reindexTags(player);
        // Remove enemy entities
        for (var j = 0; j < combatants.size(); j++) {
            var c = combatants.get(j);
            if (c.hasTag("enemy")) {
                store.remove(c.getId());
            }
        }
        // Remove combat entity
        store.remove(combatId);

        // Save player state after combat
        persistence.save(player);
    }
});
