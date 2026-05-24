engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "piercing_ray") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (!targetId) return;

    var selectedTarget = store.get(targetId);
    if (selectedTarget === null) return;
    var targetPos = selectedTarget.hasComponent("CombatPosition") ? selectedTarget.getComponent("CombatPosition") : null;
    var targetSlot = targetPos !== null ? targetPos.getInt("slot") : 0;

    var caster = store.get(actorId);
    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 6;

    var targets = [];
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var slot = pos !== null ? pos.getInt("slot") : 0;
        if (slot !== targetSlot) continue;
        targets.push(entity.getId());
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
        dealEvent.set("skillId", "piercing_ray");
        dealEvent.set("skipLog", true);
        engine.fire("combat.damage_dealt", dealEvent);
    }

    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 的贯穿射线命中 " + targets.length + " 个目标，各造成 "); s2.put("color", "text"); segments.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + damage); s3.put("color", "damage"); segments.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点伤害"); s4.put("color", "text"); segments.add(s4);
        evt.put("segments", segments);
        evt.put("effects", engine.newList());

        var animation = engine.newList();
        var beamAnim = engine.newMap();
        beamAnim.put("type", "beam");
        beamAnim.put("from", actorId);
        beamAnim.put("to", targets[0]);
        beamAnim.put("color", "#81d4fa");
        animation.add(beamAnim);

        for (var t = 0; t < targets.length; t++) {
            var impactAnim = engine.newMap();
            impactAnim.put("type", "impact");
            impactAnim.put("target", targets[t]);
            impactAnim.put("color", "#81d4fa");
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
