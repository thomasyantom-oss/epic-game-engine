engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "poison_dart") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (!targetId) return;
    var target = store.get(targetId);
    if (target === null || target.getComponent("Health").getInt("hp") <= 0) return;
    var caster = store.get(actorId);
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 3;
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : targetId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var targetSide = target.hasTag("player") ? "player" : "enemy";

    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    dealEvent.set("skillId", "poison_dart");
    dealEvent.set("skipLog", true);
    engine.fire("combat.damage_dealt", dealEvent);

    // Build log entry
    var logEntry = engine.newList();
    var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); logEntry.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 的毒镖命中 "); s2.put("color", "text"); logEntry.add(s2);
    var s3 = engine.newMap(); s3.put("text", targetName); s3.put("color", targetSide); logEntry.add(s3);
    var s4 = engine.newMap(); s4.put("text", "，造成 "); s4.put("color", "text"); logEntry.add(s4);
    var s5 = engine.newMap(); s5.put("text", "" + damage); s5.put("color", "damage"); logEntry.add(s5);
    var s6 = engine.newMap(); s6.put("text", " 点伤害并中毒"); s6.put("color", "text"); logEntry.add(s6);

    var logEntries = engine.newList();
    logEntries.add(logEntry);

    var effects = engine.newList();
    var eff = engine.newMap();
    eff.put("target", targetId);
    eff.put("type", "hp_change");
    eff.put("amount", -damage);
    eff.put("hp", health.getInt("hp"));
    eff.put("maxHp", health.getInt("maxHp"));
    effects.add(eff);

    // Apply poison buff BEFORE combat event
    var poisonData = engine.newMap();
    poisonData.put("damage", 3);
    poisonData.put("remaining", 3);
    poisonData.put("stacking", "stack");
    poisonData.put("maxStacks", 5);
    poisonData.put("color", "#9c27b0");
    poisonData.put("permanent", false);
    poisonData.put("positive", false);
    buffs.applyBuff(targetId, "poison", poisonData);

    // Add buff_applied effect so frontend syncs buff icons at hit time
    var buffEff = engine.newMap();
    buffEff.put("type", "buff_applied");
    buffEff.put("target", targetId);
    effects.add(buffEff);

    var eventData = engine.newMap();
    eventData.put("log", logEntries);
    eventData.put("effects", effects);
    eventData.put("animation", engine.newList());

    engine.combatEvent(combatId, eventData);
});
