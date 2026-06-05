var Passive = {
  registerStatMods: function(entityId) {
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return;
    for (var i = 0; i < known.size(); i++) {
      var base = String(known.get(i).get("base"));
      var raw = Skill.loadSpecAny(base);
      if (raw === null || raw === undefined) continue;
      var spec = Skill._toJs(raw);
      if (spec.kind !== "passive" || spec.effect !== "stat_mod") continue;
      var level = known.get(i).containsKey && known.get(i).containsKey("level") ? parseInt(known.get(i).get("level")) : 1;
      this._registerOne(entityId, spec, level);
    }
  },

  owns: function(entityId, passiveId) {
    var raw = Skill.loadSpecAny(passiveId);
    if (raw === null || raw === undefined) return false;
    var spec = Skill._toJs(raw);
    if (spec.kind !== "passive") return false;
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return false;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return false;
    for (var i = 0; i < known.size(); i++) {
      if (String(known.get(i).get("base")) === String(passiveId)) return true;
    }
    return false;
  },

  grant: function(entityId, passiveId) {
    var raw = Skill.loadSpecAny(passiveId);
    if (raw === null || raw === undefined || Skill._toJs(raw).kind !== "passive") return false;
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return false;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return false;
    for (var i = 0; i < known.size(); i++) {
      if (String(known.get(i).get("base")) === String(passiveId)) return true;
    }
    var inst = engine.newMap();
    inst.put("base", passiveId);
    inst.put("node", null);
    inst.put("level", 1);
    known.add(inst);
    this.registerStatMods(entityId);
    engine.recalculate(entityId);
    return true;
  },

  setSkillLevel: function(entityId, baseId, level) {
    var e = store.get(entityId);
    if (e === null || !e.hasComponent("Skillbook")) return false;
    var known = e.getComponent("Skillbook").get("known");
    if (known === null) return false;
    for (var i = 0; i < known.size(); i++) {
      if (String(known.get(i).get("base")) === String(baseId)) {
        known.get(i).put("level", parseInt(level));
        this.registerStatMods(entityId);
        engine.recalculate(entityId);
        return true;
      }
    }
    return false;
  },

  _registerOne: function(entityId, spec, level) {
    var modId = "passive_" + spec.id + "_" + entityId;
    engine.removeModifier(entityId, modId);
    var mods = spec.modifiers || {};
    var keys = Object.keys(mods);
    if (keys.length === 0) return;
    var deltas = {};
    for (var i = 0; i < keys.length; i++) deltas[keys[i]] = mods[keys[i]] * level;
    engine.addModifier(entityId, {
      typeId: "passive",
      id: modId,
      label: spec.name || spec.id,
      apply: function(ent) {
        var p = ent.getComponent("PrimaryStats");
        if (p === null) return;
        var dk = Object.keys(deltas);
        for (var j = 0; j < dk.length; j++) {
          if (p.has(dk[j])) p.set(dk[j], p.getInt(dk[j]) + deltas[dk[j]]);
        }
      }
    });
  }
};

engine.on("debug.grant_passive", 100, function(event) {
  var entityId = event.get("entityId");
  var base = event.get("base");
  event.set("ok", Passive.grant(entityId, String(base)));
  var entity = store.get(entityId);
  if (entity !== null && typeof persistence !== "undefined" && persistence !== null) persistence.save(entity);
});

engine.on("debug.set_skill_level", 100, function(event) {
  var entityId = event.get("entityId");
  var base = event.get("base");
  var level = event.get("level");
  event.set("ok", Passive.setSkillLevel(entityId, String(base), level));
  var entity = store.get(entityId);
  if (entity !== null && typeof persistence !== "undefined" && persistence !== null) persistence.save(entity);
});
