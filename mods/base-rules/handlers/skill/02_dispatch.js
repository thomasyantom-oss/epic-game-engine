engine.on("combat.unit_action", 80, function(event) {
  var cmd = event.get("command");
  var type = (cmd && typeof cmd.get === "function") ? cmd.get("type") : (cmd ? cmd.type : null);
  if (type == null) return;
  var rawSpec = Skill.loadSpec(type);
  if (rawSpec == null) return;
  // rawSpec is a Java Map (SnakeYAML). Read 'effect' via Java .get; bail if absent (bespoke skill).
  var effectName = (typeof rawSpec.get === "function") ? rawSpec.get("effect") : rawSpec.effect;
  if (effectName == null) return;
  var ctx = Skill.context(event);
  var spec = Skill.resolveSpec(ctx, type, Skill._toJs(rawSpec));
  var results = Skill.resolveTargets(ctx, spec);
  var fn = Skill.effects[spec.effect];
  if (fn == null) return;
  fn(ctx, spec, results);
});
