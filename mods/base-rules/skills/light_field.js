engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "light_field") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    var targetRow = cmd.get("targetRow");
    var targetCol = cmd.get("targetCol");

    // Determine center: use targetId position if entity exists, else use row/col
    var centerSlot, centerRowIdx;
    var rowOrder = ["FRONT", "MID", "BACK"];
    var targetEntity = targetId ? store.get(targetId) : null;
    if (targetEntity !== null && targetEntity !== undefined) {
        var pos = targetEntity.hasComponent("CombatPosition") ? targetEntity.getComponent("CombatPosition") : null;
        centerSlot = pos !== null ? pos.getInt("slot") : 0;
        var centerRow = pos !== null ? pos.getString("row") : "FRONT";
        centerRowIdx = rowOrder.indexOf(centerRow);
    } else {
        centerSlot = parseInt(targetRow) || 0;
        centerRowIdx = parseInt(targetCol) || 0;
    }

    var caster = store.get(actorId);
    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 7;

    // Cross offsets: [0,0],[-1,0],[1,0],[0,-1],[0,1] in slot/rowIdx space
    var offsets = [[0,0],[-1,0],[1,0],[0,-1],[0,1]];
    var targets = [];
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var eSlot = pos !== null ? pos.getInt("slot") : 0;
        var eRow = pos !== null ? pos.getString("row") : "FRONT";
        var eRowIdx = rowOrder.indexOf(eRow);

        for (var j = 0; j < offsets.length; j++) {
            if (eSlot === centerSlot + offsets[j][0] && eRowIdx === centerRowIdx + offsets[j][1]) {
                targets.push(entity.getId());
                break;
            }
        }
    }

    // Phase 1: deal damage to all targets (no death check yet)
    for (var t = 0; t < targets.length; t++) {
        var tid = targets[t];
        var target = store.get(tid);
        var health = target.getComponent("Health");
        health.set("hp", Math.max(0, health.getInt("hp") - damage));
    }

    // Build log entries (one per target)
    var logEntries = engine.newList();
    for (var l = 0; l < targets.length; l++) {
        var logEntry = engine.newList();
        var targetName = store.get(targets[l]).hasComponent("Name") ? store.get(targets[l]).getComponent("Name").getString("value") : targets[l];
        var ls1 = engine.newMap(); ls1.put("text", casterName); ls1.put("color", casterSide); logEntry.add(ls1);
        var ls2 = engine.newMap(); ls2.put("text", " 对 "); ls2.put("color", "text"); logEntry.add(ls2);
        var ls3 = engine.newMap(); ls3.put("text", targetName); ls3.put("color", "enemy"); logEntry.add(ls3);
        var ls4 = engine.newMap(); ls4.put("text", " 造成 "); ls4.put("color", "text"); logEntry.add(ls4);
        var ls5 = engine.newMap(); ls5.put("text", "" + damage); ls5.put("color", "damage"); logEntry.add(ls5);
        var ls6 = engine.newMap(); ls6.put("text", " 点伤害"); ls6.put("color", "text"); logEntry.add(ls6);
        logEntries.add(logEntry);
    }

    // Build effects
    var effects = engine.newList();
    for (var e = 0; e < targets.length; e++) {
        var eff = engine.newMap();
        eff.put("target", targets[e]);
        eff.put("type", "hp_change");
        eff.put("amount", -damage);
        eff.put("hp", store.get(targets[e]).getComponent("Health").getInt("hp"));
        eff.put("maxHp", store.get(targets[e]).getComponent("Health").getInt("maxHp"));
        effects.add(eff);
    }

    // Build animation
    var animation = engine.newList();
    for (var t = 0; t < targets.length; t++) {
        var impactAnim = engine.newMap();
        impactAnim.put("type", "impact");
        impactAnim.put("target", targets[t]);
        impactAnim.put("color", "#ffee58");
        animation.add(impactAnim);

        var shakeAnim = engine.newMap();
        shakeAnim.put("type", "shake");
        shakeAnim.put("target", targets[t]);
        shakeAnim.put("intensity", "normal");
        animation.add(shakeAnim);

        var dmgAnim = engine.newMap();
        dmgAnim.put("type", "damage_number");
        dmgAnim.put("target", targets[t]);
        dmgAnim.put("value", -damage);
        dmgAnim.put("color", "damage");
        animation.add(dmgAnim);
    }

    // Emit combat event via helper (before death checks so it appears first in queue)
    var eventData = engine.newMap();
    eventData.put("log", logEntries);
    eventData.put("effects", effects);
    eventData.put("animation", animation);
    engine.combatEvent(combatId, eventData);

    // Phase 2: fire damage_dealt for death detection (after combat event is queued)
    for (var t = 0; t < targets.length; t++) {
        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", targets[t]);
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        dealEvent.set("skillId", "light_field");
        dealEvent.set("skipLog", true);
        engine.fire("combat.damage_dealt", dealEvent);
    }
});
