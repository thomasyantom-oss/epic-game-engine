engine.on("combat.damage_calc", 100, function(event) {
    var attacker = store.get(event.get("attackerId"));
    var target = store.get(event.get("targetId"));

    var attack = attacker.getComponent("CombatStats").getInt("attack");
    var defense = target.getComponent("CombatStats").getInt("defense");
    var isDefending = target.hasComponent("Defending");

    var damage = Math.max(1, attack - defense);
    if (isDefending) {
        damage = Math.max(1, Math.floor(damage * 0.5));
    }

    event.set("damage", damage);
});
