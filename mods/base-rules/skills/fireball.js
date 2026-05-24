engine.on("skill.can_use", 100, function(event) {
    if (event.get("skillId") !== "fireball") return;
    var entity = store.get(event.get("entityId"));
    if (entity === null) return;
    var mana = entity.getComponent("Mana");
    if (mana === null || mana.getInt("mp") < 10) {
        event.set("usable", false);
    }
});

engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "fireball") return;

    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (targetId === null || targetId === undefined) return;

    var caster = store.get(actorId);
    var target = store.get(targetId);
    if (target === null || !target.hasComponent("Health") || target.getComponent("Health").getInt("hp") <= 0) return;

    var mana = caster.getComponent("Mana");
    if (mana !== null) {
        mana.set("mp", Math.max(0, mana.getInt("mp") - 10));
    }

    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 10;

    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    engine.fire("combat.damage_dealt", dealEvent);

    var burnData = engine.newMap();
    burnData.put("damage", 3);
    burnData.put("remaining", 2);
    burnData.put("stacking", "refresh");
    burnData.put("source", actorId);
    buffs.applyBuff(targetId, "burning", burnData);
});
