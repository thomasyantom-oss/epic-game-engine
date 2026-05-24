engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "cross_blast") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (!targetId) return;

    var selectedTarget = store.get(targetId);
    if (selectedTarget === null) return;
    var targetPos = selectedTarget.hasComponent("CombatPosition") ? selectedTarget.getComponent("CombatPosition") : null;
    var centerRow = targetPos !== null ? targetPos.getString("row") : "FRONT";
    var centerSlot = targetPos !== null ? targetPos.getInt("slot") : 0;

    var rowOrder = ["FRONT", "MID", "BACK"];
    var centerRowIdx = rowOrder.indexOf(centerRow);

    var caster = store.get(actorId);
    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 8;

    // Find targets in cross pattern (center + adjacent 4)
    var targets = [];
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var eRow = pos !== null ? pos.getString("row") : "FRONT";
        var eSlot = pos !== null ? pos.getInt("slot") : 0;
        var eRowIdx = rowOrder.indexOf(eRow);

        var isCenter = (eRow === centerRow && eSlot === centerSlot);
        var isAdjRow = (eSlot === centerSlot && Math.abs(eRowIdx - centerRowIdx) === 1);
        var isAdjSlot = (eRow === centerRow && Math.abs(eSlot - centerSlot) === 1);

        if (isCenter || isAdjRow || isAdjSlot) {
            targets.push(entity.getId());
        }
    }

    for (var t = 0; t < targets.length; t++) {
        var tid = targets[t];
        var target = store.get(tid);
        var health = target.getComponent("Health");
        health.set("hp", Math.max(0, health.getInt("hp") - damage));

        var dealEvent = engine.newEvent("combat.damage_dealt");
        dealEvent.set("attackerId", actorId);
        dealEvent.set("targetId", tid);
        dealEvent.set("damage", damage);
        dealEvent.set("combatId", combatId);
        dealEvent.set("skillId", "cross_blast");
        dealEvent.set("skipLog", true);
        engine.fire("combat.damage_dealt", dealEvent);
    }

    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 的十字爆裂命中 " + targets.length + " 个目标，各造成 "); s2.put("color", "text"); segments.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + damage); s3.put("color", "damage"); segments.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点伤害"); s4.put("color", "text"); segments.add(s4);
        evt.put("segments", segments);
        var effects = engine.newList();
        for (var e = 0; e < targets.length; e++) {
            var eff = engine.newMap();
            eff.put("target", targets[e]);
            eff.put("type", "hp_change");
            var effData = engine.newMap();
            effData.put("amount", -damage);
            effData.put("hp", store.get(targets[e]).getComponent("Health").getInt("hp"));
            effData.put("maxHp", store.get(targets[e]).getComponent("Health").getInt("maxHp"));
            eff.put("data", effData);
            effects.add(eff);
        }
        evt.put("effects", effects);

        var animation = engine.newList();
        var pulseAnim = engine.newMap();
        pulseAnim.put("type", "pulse");
        pulseAnim.put("target", actorId);
        pulseAnim.put("color", "#ffee58");
        animation.add(pulseAnim);

        for (var t = 0; t < targets.length; t++) {
            var impactAnim = engine.newMap();
            impactAnim.put("type", "impact");
            impactAnim.put("target", targets[t]);
            impactAnim.put("color", "#ffee58");
            animation.add(impactAnim);

            var shakeAnim = engine.newMap();
            shakeAnim.put("type", "shake");
            shakeAnim.put("target", targets[t]);
            shakeAnim.put("intensity", "heavy");
            animation.add(shakeAnim);

            var dmgAnim = engine.newMap();
            dmgAnim.put("type", "damage_number");
            dmgAnim.put("target", targets[t]);
            dmgAnim.put("value", -damage);
            dmgAnim.put("color", "damage");
            animation.add(dmgAnim);
        }

        evt.put("animation", animation);
        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
