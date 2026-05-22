engine.on("combat.resolve_round", 100, function(event) {
    var combatId = event.get("combatId");
    var commands = event.get("commands");
    var combat = store.get(combatId);
    var state = combat.getComponent("CombatState");

    state.set("round", state.getInt("round") + 1);
    state.set("phase", "RESOLVE");

    // Clear defending status from previous round
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        combatants.get(i).removeComponent("Defending");
    }

    // Determine turn order
    var initEvent = engine.newEvent("combat.determine_order");
    initEvent.set("combatId", combatId);
    engine.fire("combat.determine_order", initEvent);
    var turnOrder = initEvent.get("turnOrder");

    // Execute each unit's action
    for (var i = 0; i < turnOrder.length; i++) {
        var unitId = turnOrder[i];
        var unit = store.get(unitId);
        if (unit === null) continue;
        if (!unit.hasComponent("Health")) continue;
        if (unit.getComponent("Health").getInt("hp") <= 0) continue;

        var cmd = commands.get(unitId);
        if (cmd === null || cmd === undefined) continue;

        var actionEvent = engine.newEvent("combat.unit_action");
        actionEvent.set("combatId", combatId);
        actionEvent.set("actorId", unitId);
        actionEvent.set("command", cmd);
        engine.fire("combat.unit_action", actionEvent);
    }

    // Check combat end
    var endEvent = engine.newEvent("combat.check_end");
    endEvent.set("combatId", combatId);
    engine.fire("combat.check_end", endEvent);

    if (endEvent.has("ended") && endEvent.get("ended")) {
        state.set("phase", endEvent.get("result"));
    } else {
        state.set("phase", "COMMAND");
    }
});
