// Two presentation paths, chosen by whether the spec authored a `log:` block:
//   NO log  -> dealDamage(skipLog=false): combat_events.js builds the combatEvent and
//              combat_log.js writes the log line (the engine's default damage flow).
//   HAS log -> dealDamage(skipLog=true) suppresses those handlers, and present() becomes
//              the SOLE emitter of the combatEvent + CombatLog entry (custom log text).
// => Any effect that calls present() MUST set skipLog=true on its dealDamage calls, and
//    MUST have a `log:` template, or it produces a blank log line.
Skill.registerEffect("damage_only", function(ctx, spec, results) {
  var custom = (spec.log != null);   // custom log => present() path; else combat_events path
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    if (custom) Skill.applyDamage(results[i].entity, dmg);                 // present path: mutate now, death later
    else Skill.dealDamage(ctx, results[i].entity, dmg, spec.id, false);    // combat_events path: enqueues attack (prio 60) before death (prio 100)
    damages.push(dmg);
  }
  if (custom) {
    Skill.present(ctx, spec, results, damages, null);                      // skill animation FIRST
    for (var j = 0; j < results.length; j++) {
      Skill.fireDamageDealt(ctx, results[j].entity, damages[j], spec.id, true);  // THEN death cascade
    }
  }
});

// Always takes the present() path (skipLog=true) — present() is the sole emitter, so a
// `log:` template is required (see the two-paths note above).
Skill.registerEffect("damage_with_debuff", function(ctx, spec, results) {
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    Skill.applyDamage(results[i].entity, dmg);                    // mutate HP now (present() reads it)
    Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
    damages.push(dmg);
  }
  Skill.present(ctx, spec, results, damages, {buffApplied: true});  // skill animation FIRST
  for (var j = 0; j < results.length; j++) {
    Skill.fireDamageDealt(ctx, results[j].entity, damages[j], spec.id, true);  // THEN death cascade
  }
});

Skill.registerEffect("debuff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
  Skill.present(ctx, spec, results, null, {buffApplied: true});
});

Skill.registerEffect("buff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.buff);
  Skill.present(ctx, spec, results, null, {buffApplied: true});
});

Skill.registerEffect("heal", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) {
    var h = results[i].entity.getComponent("Health");
    h.set("hp", Math.min(h.getInt("maxHp"), h.getInt("hp") + spec.heal));
  }
  Skill.present(ctx, spec, results, null);
});
