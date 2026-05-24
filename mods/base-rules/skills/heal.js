engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "heal") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var caster = store.get(actorId);
    var health = caster.getComponent("Health");
    var healAmount = 15;
    var newHp = Math.min(health.getInt("maxHp"), health.getInt("hp") + healAmount);
    health.set("hp", newHp);

    // Log as combat event manually (no damage_dealt for heals)
    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();
        var segments = engine.newList();
        var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", "player"); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 恢复了 "); s2.put("color", "text"); segments.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + healAmount); s3.put("color", "player"); segments.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点生命"); s4.put("color", "text"); segments.add(s4);
        evt.put("segments", segments);
        evt.put("effects", engine.newList());
        // Animation from YAML
        var skillDef = engine.loadYaml("skills/heal.yaml");
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
                anim.put(key, val);
            }
            anim.put("value", healAmount);
            animation.add(anim);
        }
        evt.put("animation", animation);
        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
