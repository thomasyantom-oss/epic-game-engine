engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "piercing_ray") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (!targetId) return;

    // Find the slot (column) of the selected target
    var selectedTarget = store.get(targetId);
    if (selectedTarget === null) return;
    var targetPos = selectedTarget.hasComponent("CombatPosition") ? selectedTarget.getComponent("CombatPosition") : null;
    var targetSlot = targetPos !== null ? targetPos.getInt("slot") : 0;

    var caster = store.get(actorId);
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 6;

    // Hit all enemies in that column (same slot, any row)
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var slot = pos !== null ? pos.getInt("slot") : 0;
        if (slot !== targetSlot) continue;

        var health = entity.getComponent("Health");
        health.set("hp", Math.max(0, health.getInt("hp") - damage));

        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", entity.getId());
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        dealEvent.set("skillId", "piercing_ray");
        engine.fire("combat.damage_dealt", dealEvent);
    }
});
