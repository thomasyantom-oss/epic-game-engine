engine.on("combat.damage_calc", 100, function(event) {
    var attackerId = event.get("attackerId");
    var targetId = event.get("targetId");
    var attacker = store.get(attackerId);
    var target = store.get(targetId);

    if (attacker === null || target === null) {
        event.set("damage", 0);
        return;
    }

    var attack = attacker.getComponent("CombatStats").getInt("attack");
    var damage;
    if (typeof Skill !== "undefined" && Skill && Skill.mitigate) {
        damage = Skill.mitigate(target, attack, {delivery: "普攻"});
    } else {
        var defense = target.getComponent("CombatStats").getInt("defense");
        if (attack <= 0) attack = 1;
        damage = Math.max(1, Math.ceil(attack * (1 - defense / (defense + attack))));
        if (target.hasComponent("Buff_defending")) {
            damage = Math.max(1, Math.floor(damage * 0.5));
        }
    }

    event.set("damage", damage);
});
