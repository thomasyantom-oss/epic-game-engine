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

    // Mutate HP only. Fire damage_dealt after the skill event is queued so death
    // events cannot appear before the cross_blast animation.
    for (var i = 0; i < results.length; i++) {
        Skill.applyDamage(results[i].entity, damage);
    }

    // Emit via direct queue push to preserve golden format (no logCount, nested effects.data)
    var segments = Skill.summaryLog(ctx, " 的十字爆裂命中 " + results.length + " 个目标，各造成 ", damage, " 点伤害");
    var effects = engine.newList();
    for (var e = 0; e < results.length; e++) {
        effects.add(Skill.nestedHpEffect(results[e].entity, damage));
    }

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

    Skill.emitCombatQueueEvent(ctx.combatId, segments, effects, animation);

    for (var d = 0; d < results.length; d++) {
        Skill.fireDamageDealt(ctx, results[d].entity, damage, "cross_blast", true);
    }
});
