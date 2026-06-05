var Passive = {
  _registered: {},

  _flattenSpec: function(spec, level) {
    if (spec === null || spec === undefined || spec.kind !== "passive") return null;
    var out = {
      id: String(spec.id),
      kind: "passive",
      effect: spec.effect,
      level: parseInt(level || 1)
    };
    if (spec.modifiers !== undefined) out.modifiers = spec.modifiers;
    if (spec.match !== undefined) out.match = spec.match;
    if (spec.patch !== undefined) out.patch = spec.patch;
    if (spec.name !== undefined) out.name = spec.name;
    return out;
  },

  ownedSpecs: function(entity) {
    var out = [];
    var seen = {};
    function add(spec) {
      if (spec === null || spec === undefined || spec.kind !== "passive" || spec.id === undefined) return;
      var id = String(spec.id);
      if (seen[id]) return;
      seen[id] = true;
      if (spec.level === undefined || spec.level === null) spec.level = 1;
      out.push(spec);
    }

    if (entity !== null && entity !== undefined && entity.hasComponent("Skillbook")) {
      var known = entity.getComponent("Skillbook").get("known");
      if (known !== null) {
        for (var i = 0; i < known.size(); i++) {
          var base = String(known.get(i).get("base"));
          var raw = Skill.loadSpecAny(base);
          if (raw === null || raw === undefined) continue;
          var level = known.get(i).containsKey && known.get(i).containsKey("level") ? parseInt(known.get(i).get("level")) : 1;
          add(this._flattenSpec(Skill._toJs(raw), level));
        }
      }
    }

    if (typeof Talent !== "undefined" && Talent !== null && typeof Talent.derivedPassives === "function") {
      var derived = Talent.derivedPassives(entity);
      if (derived !== null && derived !== undefined) {
        for (var d = 0; d < derived.length; d++) add(derived[d]);
      }
    }
    return out;
  },

  registerStatMods: function(entityId) {
    var e = store.get(entityId);
    if (e === null) return;
    var specs = this.ownedSpecs(e);
    var next = {};
    for (var i = 0; i < specs.length; i++) {
      var spec = specs[i];
      if (spec.effect !== "stat_mod") continue;
      var modId = this._modId(entityId, spec);
      next[modId] = true;
    }

    var previous = this._registered[entityId] || {};
    var oldIds = Object.keys(previous);
    for (var r = 0; r < oldIds.length; r++) {
      if (!next[oldIds[r]]) engine.removeModifier(entityId, oldIds[r]);
    }

    for (var j = 0; j < specs.length; j++) {
      var s = specs[j];
      if (s.effect !== "stat_mod") continue;
      this._registerOne(entityId, s, s.level || 1);
    }
    this._registered[entityId] = next;
  },

  owns: function(entityId, passiveId) {
    var e = store.get(entityId);
    if (e === null) return false;
    var specs = this.ownedSpecs(e);
    for (var i = 0; i < specs.length; i++) {
      if (String(specs[i].id) === String(passiveId)) return true;
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

  _modId: function(entityId, spec) {
    return "passive_" + spec.id + "_" + entityId;
  },

  _registerOne: function(entityId, spec, level) {
    var modId = this._modId(entityId, spec);
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
          var current = p.has(dk[j]) ? p.getInt(dk[j]) : 0;
          p.set(dk[j], current + deltas[dk[j]]);
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
