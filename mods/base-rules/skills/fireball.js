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

    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : targetId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var targetSide = target.hasTag("player") ? "player" : "enemy";

    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 10;

    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - damage));

    // Fire damage_dealt for death check (skipLog to avoid duplicate)
    var dealEvent = engine.newEvent("combat.damage_dealt");
    dealEvent.set("attackerId", actorId);
    dealEvent.set("targetId", targetId);
    dealEvent.set("damage", damage);
    dealEvent.set("combatId", combatId);
    dealEvent.set("skillId", "fireball");
    dealEvent.set("skipLog", true);
    engine.fire("combat.damage_dealt", dealEvent);

    // Build animation from skill YAML
    var skillDef = engine.loadYaml("skills/fireball.yaml");
    var animDef = skillDef.get("animation");
    var animation = engine.newList();
    for (var i = 0; i < animDef.size(); i++) {
        var step = animDef.get(i);
        var anim = engine.newMap();
        var keys = step.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.next();
            var val = step.get(key);
            if (val === "actor") val = actorId;
            else if (val === "target") val = targetId;
            else if (val === "actor_side") val = casterSide;
            anim.put(key, val);
        }
        animation.add(anim);
    }

    // Emit combat event
    var logEntry = engine.newList();
    var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); logEntry.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 的火球术命中 "); s2.put("color", "text"); logEntry.add(s2);
    var s3 = engine.newMap(); s3.put("text", targetName); s3.put("color", targetSide); logEntry.add(s3);
    var s4 = engine.newMap(); s4.put("text", "，造成 "); s4.put("color", "text"); logEntry.add(s4);
    var s5 = engine.newMap(); s5.put("text", "" + damage); s5.put("color", "damage"); logEntry.add(s5);
    var s6 = engine.newMap(); s6.put("text", " 点伤害"); s6.put("color", "text"); logEntry.add(s6);

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

    var eventData = engine.newMap();
    eventData.put("log", logEntries);
    eventData.put("effects", effects);
    eventData.put("animation", animation);

    // Apply burning buff
    var burnData = engine.newMap();
    burnData.put("damage", 3);
    burnData.put("remaining", 2);
    burnData.put("stacking", "refresh");
    burnData.put("source", actorId);
    burnData.put("color", "#ff4400");
    burnData.put("permanent", false);
    burnData.put("positive", false);
    buffs.applyBuff(targetId, "burning", burnData);

    // Add buff_applied effect so frontend syncs buff icons at hit time
    var buffEff = engine.newMap();
    buffEff.put("type", "buff_applied");
    buffEff.put("target", targetId);
    effects.add(buffEff);

    engine.combatEvent(combatId, eventData);
});
