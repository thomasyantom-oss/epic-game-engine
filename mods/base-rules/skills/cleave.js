engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "cleave") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var targetId = cmd.get("targetId");
    if (!targetId) return;

    var selectedTarget = store.get(targetId);
    if (selectedTarget === null) return;
    var targetPos = selectedTarget.hasComponent("CombatPosition") ? selectedTarget.getComponent("CombatPosition") : null;
    var targetRow = targetPos !== null ? targetPos.getString("row") : "FRONT";

    // Grid mapping: enemy FRONT=col0, MID=col1, BACK=col2
    var rowToCol = { "FRONT": 0, "MID": 1, "BACK": 2 };
    var gridCol = rowToCol[targetRow] || 0;

    var caster = store.get(actorId);
    var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = caster.hasTag("player") ? "player" : "enemy";
    var attack = caster.hasComponent("CombatStats") ? caster.getComponent("CombatStats").getInt("attack") : 5;
    var damage = attack + 5;

    // Collect targets in that row
    var targets = [];
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (!entity.hasTag("enemy")) continue;
        if (!entity.hasComponent("Health") || entity.getComponent("Health").getInt("hp") <= 0) continue;
        var pos = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
        var row = pos !== null ? pos.getString("row") : "FRONT";
        if (row !== targetRow) continue;
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
        dealEvent.set("skillId", "cleave");
        dealEvent.set("skipLog", true);
        engine.fire("combat.damage_dealt", dealEvent);
    }

    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", casterSide); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 的顺劈斩命中 " + targets.length + " 个目标，各造成 "); s2.put("color", "text"); segments.add(s2);
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
        var lungeAnim = engine.newMap();
        lungeAnim.put("type", "lunge");
        lungeAnim.put("target", actorId);
        lungeAnim.put("side", casterSide);
        animation.add(lungeAnim);

        // Slash covering the entire column (3 cells)
        var areaCells = engine.newList();
        areaCells.add("cell_0_" + gridCol);
        areaCells.add("cell_1_" + gridCol);
        areaCells.add("cell_2_" + gridCol);
        var slashAnim = engine.newMap();
        slashAnim.put("type", "slash");
        slashAnim.put("area", areaCells);
        slashAnim.put("color", "#ffffff");
        animation.add(slashAnim);

        for (var t = 0; t < targets.length; t++) {
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
