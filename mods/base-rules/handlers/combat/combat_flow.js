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

    // A command is pre-emptive if its skill YAML declares `preemptive: true` (e.g. defend).
    // Pre-emptive actions resolve for the whole field BEFORE any normal action this round.
    function isPreemptive(cmd) {
        if (cmd === null || cmd === undefined) return false;
        var type = cmd.get("type");
        if (type === null || type === undefined) return false;
        var spec = engine.loadYaml("skills/" + type + ".yaml");
        return spec !== null && spec.get("preemptive") === true;
    }

    function runAction(unitId, cmd) {
        var unit = store.get(unitId);
        if (unit === null) return;
        if (!unit.hasComponent("Health")) return;
        if (unit.getComponent("Health").getInt("hp") <= 0) return;
        if (cmd === null || cmd === undefined) return;
        var actionEvent = engine.newEvent("combat.unit_action");
        actionEvent.set("combatId", combatId);
        actionEvent.set("actorId", unitId);
        actionEvent.set("command", cmd);
        engine.fire("combat.unit_action", actionEvent);
    }

    // Pass 1 — pre-emptive actions (initiative-ordered)
    for (var i = 0; i < turnOrder.length; i++) {
        var cmd1 = commands.get(turnOrder[i]);
        if (isPreemptive(cmd1)) runAction(turnOrder[i], cmd1);
    }
    // Pass 2 — normal actions (initiative-ordered)
    for (var j = 0; j < turnOrder.length; j++) {
        var cmd2 = commands.get(turnOrder[j]);
        if (!isPreemptive(cmd2)) runAction(turnOrder[j], cmd2);
    }

    // Fire round_end so end-of-round buff ticks (burning, poison, defend expiry) apply
    // BEFORE the end check — a DoT that kills the last enemy must end combat this round.
    var roundEndEvent = engine.newEvent("combat.round_end");
    roundEndEvent.set("combatId", combatId);
    engine.fire("combat.round_end", roundEndEvent);

    // Check combat end
    var endEvent = engine.newEvent("combat.check_end");
    endEvent.set("combatId", combatId);
    engine.fire("combat.check_end", endEvent);

    if (endEvent.has("ended") && endEvent.get("ended")) {
        state.set("phase", endEvent.get("result"));
    } else {
        // Advance to next round
        state.set("round", state.getInt("round") + 1);
        state.set("phase", "COMMAND");
    }
});
