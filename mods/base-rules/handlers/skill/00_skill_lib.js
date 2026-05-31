var Skill = {
  _specs: {},
  loadSpec: function(type) {
    if (this._specs[type] === undefined) {
      this._specs[type] = engine.loadYaml("skills/" + type + ".yaml");
    }
    return this._specs[type];
  },

  effects: {},
  registerEffect: function(name, fn) { this.effects[name] = fn; },

  context: function(event) {
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var caster = store.get(actorId);
    var casterName = (caster !== null && caster.hasComponent("Name"))
        ? caster.getComponent("Name").getString("value") : actorId;
    var casterSide = (caster !== null && caster.hasTag("player")) ? "player" : "enemy";
    return {
      actorId: actorId, combatId: combatId, caster: caster,
      casterName: casterName, casterSide: casterSide, cmd: event.get("command")
    };
  },

  computeDamage: function(caster, target, dmgSpec) {
    if (dmgSpec == null) return 0;
    if (dmgSpec.via_damage_calc) {
      var ev = engine.newEvent("combat.damage_calc");
      ev.set("attackerId", caster.getId());
      ev.set("targetId", target.getId());
      engine.fire("combat.damage_calc", ev);
      return ev.get("damage");
    }
    var statName = dmgSpec.base || "attack";
    var base = caster.hasComponent("CombatStats")
        ? caster.getComponent("CombatStats").getInt(statName) : 5;
    return base + (dmgSpec.add || 0);
    // Feature #2 将在此处追加属性加成；战斗、tooltip、AI 均调用此函数。
  }
};
