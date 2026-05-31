Skill.registerEffect("damage_only", function(ctx, spec, results) {
  var custom = (spec.log != null);
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    Skill.dealDamage(ctx, results[i].entity, dmg, spec.id, custom ? true : false);  // skipLog only when custom
    damages.push(dmg);
  }
  if (custom) Skill.present(ctx, spec, results, damages, null);
});

Skill.registerEffect("damage_with_debuff", function(ctx, spec, results) {
  var damages = [];
  for (var i = 0; i < results.length; i++) {
    var dmg = Skill.computeDamage(ctx.caster, results[i].entity, spec.damage);
    Skill.dealDamage(ctx, results[i].entity, dmg, spec.id);
    Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
    damages.push(dmg);
  }
  Skill.present(ctx, spec, results, damages, {buffApplied: true});
});

Skill.registerEffect("debuff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.debuff);
  Skill.present(ctx, spec, results, null);
});

Skill.registerEffect("buff_only", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) Skill.applyBuffFromSpec(ctx, results[i].entity, spec.buff);
  Skill.present(ctx, spec, results, null);
});

Skill.registerEffect("heal", function(ctx, spec, results) {
  for (var i = 0; i < results.length; i++) {
    var h = results[i].entity.getComponent("Health");
    h.set("hp", Math.min(h.getInt("maxHp"), h.getInt("hp") + spec.heal));
  }
  Skill.present(ctx, spec, results, null);
});
