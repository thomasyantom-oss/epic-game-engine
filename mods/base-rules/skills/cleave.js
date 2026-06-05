// cleave: bespoke emitter (keeps direct-queue push to preserve golden effects.data
// format and avoid logCount; slash.area uses computed column cells). Uses Skill lib
// for target selection, damage, and HP mutation.
engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "cleave") return;

    var ctx = Skill.context(event);
    var targetId = cmd.get("targetId");
    if (!targetId) return;
    if (store.get(targetId) === null) return;

    // Load spec for damage definition
    var rawSpec = Skill.loadSpec("cleave");
    var spec = Skill.resolveSpec(ctx, "cleave", Skill._toJs(rawSpec));

    // Resolve targets via lib (same-row pattern)
    var results = Skill.resolveTargets(ctx, spec);

    // Compute damage (same for all targets)
    var damage = Skill.computeDamage(ctx.caster, results.length > 0 ? results[0].entity : ctx.caster, spec.damage);

    // Derive grid column from target row (FRONT=0, MID=1, BACK=2)
    var selectedTarget = store.get(targetId);
    var targetPos = selectedTarget.hasComponent("CombatPosition") ? selectedTarget.getComponent("CombatPosition") : null;
    var targetRow = targetPos !== null ? targetPos.getString("row") : "FRONT";
    var rowToCol = { "FRONT": 0, "MID": 1, "BACK": 2 };
    var gridCol = rowToCol[targetRow] || 0;

    var damages = [];

    // Mutate HP only. Fire damage_dealt after the skill event is queued so death
    // events cannot appear before the cleave animation.
    for (var i = 0; i < results.length; i++) {
        var finalDamage = Skill.mitigate(results[i].entity, damage, {
            delivery: spec.delivery || "技能",
            type: (spec.damage && spec.damage.type) || "物理",
            element: spec.damage && spec.damage.element,
            elementAmp: spec.damage && spec.damage.elementAmp
        });
        damages.push(finalDamage);
        Skill.applyDamage(results[i].entity, finalDamage);
    }

    // Emit via direct queue push to preserve golden format (no logCount, nested effects.data)
    var segments = Skill.summaryLog(ctx, " 的顺劈斩命中 " + results.length + " 个目标，各造成 ", damages.length > 0 ? damages[0] : 0, " 点伤害");
    var effects = engine.newList();
    for (var e = 0; e < results.length; e++) {
        effects.add(Skill.nestedHpEffect(results[e].entity, damages[e]));
    }

    // Animation: lunge on actor, slash covering the column, per-target shake/damage_number
    var animation = engine.newList();
    var lungeAnim = engine.newMap();
    lungeAnim.put("type", "lunge");
    lungeAnim.put("target", ctx.actorId);
    lungeAnim.put("side", ctx.casterSide);
    animation.add(lungeAnim);

    var areaCells = engine.newList();
    areaCells.add("cell_0_" + gridCol);
    areaCells.add("cell_1_" + gridCol);
    areaCells.add("cell_2_" + gridCol);
    var slashAnim = engine.newMap();
    slashAnim.put("type", "slash");
    slashAnim.put("area", areaCells);
    slashAnim.put("color", "#ffffff");
    animation.add(slashAnim);

    for (var t = 0; t < results.length; t++) {
        var shakeAnim = engine.newMap();
        shakeAnim.put("type", "shake");
        shakeAnim.put("target", results[t].entity.getId());
        shakeAnim.put("intensity", "heavy");
        animation.add(shakeAnim);

        var dmgAnim = engine.newMap();
        dmgAnim.put("type", "damage_number");
        dmgAnim.put("target", results[t].entity.getId());
        dmgAnim.put("value", -damages[t]);
        dmgAnim.put("color", "damage");
        animation.add(dmgAnim);
    }

    Skill.emitCombatQueueEvent(ctx.combatId, segments, effects, animation);

    for (var d = 0; d < results.length; d++) {
        Skill.fireDamageDealt(ctx, results[d].entity, damages[d], "cleave", true);
    }
});
