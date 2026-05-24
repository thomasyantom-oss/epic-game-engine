engine.on("combat.damage_calc", 100, function(event) {
    var attackerId = event.get("attackerId");
    var targetId = event.get("targetId");
    var attacker = store.get(attackerId);
    var target = store.get(targetId);

    if (attacker === null || target === null) {
        event.set("damage", 0);
        event.set("error", "attacker=" + attackerId + "(" + (attacker !== null) + ") target=" + targetId + "(" + (target !== null) + ")");
        return;
    }

    var attack = attacker.getComponent("CombatStats").getInt("attack");
    var defense = target.getComponent("CombatStats").getInt("defense");
    var isDefending = target.hasComponent("Defending");

    var damage = Math.max(1, attack - defense);
    if (isDefending) {
        damage = Math.max(1, Math.floor(damage * 0.5));
    }

    event.set("damage", damage);
});
