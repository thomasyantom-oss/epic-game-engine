engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "light_field") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    var targetRow = cmd.get("targetRow");
    var targetCol = cmd.get("targetCol");

    // Determine center: use targetId position if available, else use row/col
    var centerSlot, centerRowIdx;
    var rowOrder = ["FRONT", "MID", "BACK"];
    if (targetId !== null && targetId !== undefined) {
        var t = store.get(targetId);
        var pos = t !== null && t.hasComponent("CombatPosition") ? t.getComponent("CombatPosition") : null;
        centerSlot = pos !== null ? pos.getInt("slot") : 0;
        var centerRow = pos !== null ? pos.getString("row") : "FRONT";
        centerRowIdx = rowOrder.indexOf(centerRow);
    } else {
        centerSlot = targetRow !== null ? targetRow : 1;
        centerRowIdx = targetCol !== null ? targetCol : 1;
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
        dealEvent.set("skillId", "light_field");
        dealEvent.set("skipLog", true);
        engine.fire("combat.damage_dealt", dealEvent);
    }

    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 的光击阵命中 " + targets.length + " 个目标，各造成 "); s2.put("color", "text"); segments.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + damage); s3.put("color", "damage"); segments.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点伤害"); s4.put("color", "text"); segments.add(s4);
        evt.put("segments", segments);
        evt.put("effects", engine.newList());

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

        evt.put("animation", animation);
        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
