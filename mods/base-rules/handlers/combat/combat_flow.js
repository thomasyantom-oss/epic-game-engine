engine.on("combat.resolve_round", 100, function(event) {
    var combatId = event.get("combatId");
    var commands = event.get("commands");
    var combat = store.get(combatId);
    var state = combat.getComponent("CombatState");

    state.set("phase", "RESOLVE");

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
        // Fire round_end so buff ticks (burning, poison, etc.) can apply
        var roundEndEvent = engine.newEvent("combat.round_end");
        roundEndEvent.set("combatId", combatId);
        engine.fire("combat.round_end", roundEndEvent);
        // Advance to next round
        state.set("round", state.getInt("round") + 1);
        state.set("phase", "COMMAND");
    }
});
