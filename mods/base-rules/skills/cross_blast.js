// cross_blast: bespoke emitter (keeps direct-queue push to preserve golden effects.data
// format and avoid logCount). Uses Skill lib for target selection, damage, and HP mutation.
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "cross_blast") return;

    var ctx = Skill.context(event);
    var targetId = cmd.get("targetId");
    if (!targetId) return;
    if (store.get(targetId) === null) return;

    // Load spec for damage definition (targeting resolved manually below for summary log)
    var rawSpec = Skill.loadSpec("cross_blast");
    var spec = Skill._toJs(rawSpec);

    // Resolve targets via lib (cross pattern)
    var results = Skill.resolveTargets(ctx, spec);

    // Compute damage (same for all targets)
    var damage = Skill.computeDamage(ctx.caster, results.length > 0 ? results[0].entity : ctx.caster, spec.damage);

    // Mutate HP + fire damage_dealt (skipLog=true, we emit our own event below)
    for (var i = 0; i < results.length; i++) {
        Skill.dealDamage(ctx, results[i].entity, damage, "cross_blast", true);
    }

    // Emit via direct queue push to preserve golden format (no logCount, nested effects.data)
    var combat = store.get(ctx.combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var evt = engine.newMap();

        // Summary segments
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", ctx.casterName); s1.put("color", ctx.casterSide); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 的十字爆裂命中 " + results.length + " 个目标，各造成 "); s2.put("color", "text"); segments.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + damage); s3.put("color", "damage"); segments.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点伤害"); s4.put("color", "text"); segments.add(s4);
        evt.put("segments", segments);

        // Effects (nested data format, matching golden)
        var effects = engine.newList();
        for (var e = 0; e < results.length; e++) {
            var eff = engine.newMap();
            eff.put("target", results[e].entity.getId());
            eff.put("type", "hp_change");
            var effData = engine.newMap();
            effData.put("amount", -damage);
            effData.put("hp", results[e].entity.getComponent("Health").getInt("hp"));
            effData.put("maxHp", results[e].entity.getComponent("Health").getInt("maxHp"));
            eff.put("data", effData);
            effects.add(eff);
        }
        evt.put("effects", effects);

        // Animation: per-target impact/shake/damage_number, then pulse on actor
        var animation = engine.newList();
        for (var t = 0; t < results.length; t++) {
            var impactAnim = engine.newMap();
            impactAnim.put("type", "impact");
            impactAnim.put("target", results[t].entity.getId());
            impactAnim.put("color", "#ffee58");
            animation.add(impactAnim);

            var shakeAnim = engine.newMap();
            shakeAnim.put("type", "shake");
            shakeAnim.put("target", results[t].entity.getId());
            shakeAnim.put("intensity", "heavy");
            animation.add(shakeAnim);

            var dmgAnim = engine.newMap();
            dmgAnim.put("type", "damage_number");
            dmgAnim.put("target", results[t].entity.getId());
            dmgAnim.put("value", -damage);
            dmgAnim.put("color", "damage");
            animation.add(dmgAnim);
        }
        var pulseAnim = engine.newMap();
        pulseAnim.put("type", "pulse");
        pulseAnim.put("target", ctx.actorId);
        pulseAnim.put("color", "#ffee58");
        animation.add(pulseAnim);

        evt.put("animation", animation);
        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
