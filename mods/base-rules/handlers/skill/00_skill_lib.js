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

  _toJs: function(v) {
    if (v === null || v === undefined) return v;
    if (typeof v.entrySet === "function") {            // java.util.Map
      var o = {}; var it = v.keySet().iterator();
      while (it.hasNext()) { var k = it.next(); o[k] = this._toJs(v.get(k)); }
      return o;
    }
    if (typeof v.size === "function" && typeof v.get === "function") { // java.util.List
      var a = []; for (var i = 0; i < v.size(); i++) a.push(this._toJs(v.get(i)));
      return a;
    }
    return v;
  },

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
  },

  _rowOrder: ["FRONT", "MID", "BACK"],

  _cellOf: function(entity) {
    var p = entity.hasComponent("CombatPosition") ? entity.getComponent("CombatPosition") : null;
    var row = p !== null ? p.getString("row") : "FRONT";
    var slot = p !== null ? p.getInt("slot") : 0;
    return { rowIdx: this._rowOrder.indexOf(row), slot: slot };
  },

  resolveTargets: function(ctx, spec) {
    var t = spec.targeting;
    var mode = (t && t.mode) ? t.mode : "pattern";
    var out = [];
    if (mode === "self") {
      return [{ entity: ctx.caster, cell: this._cellOf(ctx.caster) }];
    }
    var field = t.field;                       // "enemy" | "ally"
    var casterIsPlayer = ctx.casterSide !== undefined
        ? (ctx.casterSide === "player")
        : (ctx.caster !== null && ctx.caster.hasTag("player"));
    var combatants = store.getByTagAsList("combat:" + ctx.combatId);
    var live = [];
    for (var i = 0; i < combatants.size(); i++) {
      var e = combatants.get(i);
      var isAlly = casterIsPlayer ? e.hasTag("player") : !e.hasTag("player");
      var sideMatch = (field === "ally") ? isAlly : !isAlly;
      if (!sideMatch) continue;
      if (!e.hasComponent("Health") || e.getComponent("Health").getInt("hp") <= 0) continue;
      live.push(e);
    }
    if (mode === "group") {
      for (var k = 0; k < live.length; k++) out.push({ entity: live[k], cell: this._cellOf(live[k]) });
      return out;
    }
    // mode === "pattern": center from cmd.targetId (fallback targetRow/targetCol)
    var center;
    var tid = ctx.cmd.targetId !== undefined ? ctx.cmd.targetId : (ctx.cmd.get ? ctx.cmd.get("targetId") : null);
    var centerEntity = tid ? store.get(tid) : null;
    if (centerEntity !== null && centerEntity !== undefined) {
      center = this._cellOf(centerEntity);
    } else {
      var r = ctx.cmd.targetRow, c = ctx.cmd.targetCol;
      center = { slot: parseInt(r) || 0, rowIdx: parseInt(c) || 0 };
    }
    var pattern = t.pattern || [[0,0]];
    for (var j = 0; j < live.length; j++) {
      var cell = this._cellOf(live[j]);
      for (var o = 0; o < pattern.length; o++) {
        // pattern entries are [rowIdxDelta, slotDelta]
        if (cell.rowIdx === center.rowIdx + pattern[o][0] && cell.slot === center.slot + pattern[o][1]) {
          out.push({ entity: live[j], cell: cell });
          break;
        }
      }
    }
    return out;
  },

  // Mutate target HP and fire combat.damage_dealt.
  // skipLog: pass false to let combat_events.js build the presentation (plain-damage path).
  //          any other value (including undefined) defaults to true (present() path).
  dealDamage: function(ctx, target, amount, skillId, skipLog) {
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - amount));
    var ev = engine.newEvent("combat.damage_dealt");
    ev.set("attackerId", ctx.actorId);
    ev.set("targetId", target.getId());
    ev.set("damage", amount);
    ev.set("combatId", ctx.combatId);
    ev.set("skillId", skillId);
    if (skipLog !== false) ev.set("skipLog", true);   // default true; pass false to use combat_events presentation
    engine.fire("combat.damage_dealt", ev);
  },

  // Apply a buff from a buff spec object (id + data map), resolving "@caster" references.
  applyBuffFromSpec: function(ctx, target, buffSpec) {
    var data = engine.newMap();
    var src = buffSpec.data || {};
    var keys = Object.keys(src);
    for (var i = 0; i < keys.length; i++) {
      var v = src[keys[i]];
      if (v === "@caster") v = ctx.actorId;   // resolve caster reference
      data.put(keys[i], v);
    }
    buffs.applyBuff(target.getId(), buffSpec.id, data);
  },

  // Build a flat hp_change effect entry (after HP has already been mutated by dealDamage).
  _hpEffect: function(target, amount) {
    var eff = engine.newMap();
    eff.put("target", target.getId());
    eff.put("type", "hp_change");
    eff.put("amount", -amount);
    eff.put("hp", target.getComponent("Health").getInt("hp"));
    eff.put("maxHp", target.getComponent("Health").getInt("maxHp"));
    return eff;
  },

  // Build a buff_applied effect entry.
  _buffEffect: function(target) {
    var eff = engine.newMap();
    eff.put("type", "buff_applied");
    eff.put("target", target.getId());
    return eff;
  },

  // Flat-format animation resolution mirroring combat_events.resolveSkillAnimation.
  // Single-target token replacement now; multi-target expansion added in a later task.
  resolveAnimation: function(spec, ctx, results) {
    var out = engine.newList();
    var animDef = spec.animation;
    if (animDef == null) return out;
    var firstTargetId = results.length > 0 ? results[0].entity.getId() : null;
    for (var i = 0; i < animDef.length; i++) {
      var step = animDef[i];
      var anim = engine.newMap();
      var keys = Object.keys(step);
      for (var k = 0; k < keys.length; k++) {
        var val = step[keys[k]];
        if (val === "actor") val = ctx.actorId;
        else if (val === "target") val = firstTargetId;
        else if (val === "actor_side") val = ctx.casterSide;
        anim.put(keys[k], val);
      }
      out.add(anim);
    }
    return out;
  },

  // Build a segment list from a "{caster}/{target}/{damage}" template.
  _renderLog: function(tmpl, ctx, target, amount) {
    var seg = engine.newList();
    if (tmpl == null) return seg;
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : target.getId();
    var targetSide = target.hasTag("player") ? "player" : "enemy";
    var parts = tmpl.split(/(\{caster\}|\{target\}|\{damage\})/);
    for (var i = 0; i < parts.length; i++) {
      var p = parts[i]; if (p === "") continue;
      var s = engine.newMap();
      if (p === "{caster}") { s.put("text", ctx.casterName); s.put("color", ctx.casterSide); }
      else if (p === "{target}") { s.put("text", targetName); s.put("color", targetSide); }
      else if (p === "{damage}") { s.put("text", "" + amount); s.put("color", "damage"); }
      else { s.put("text", p); s.put("color", "text"); }
      seg.add(s);
    }
    return seg;
  },

  // Emit one combatEvent via engine.combatEvent with log, effects, and animation.
  // Single-target and per-target log; multi-target animation expansion is a later task.
  // opts.buffApplied: when true, appends a buff_applied effect entry per result target.
  present: function(ctx, spec, results, damages, opts) {
    var log = engine.newList();
    var effects = engine.newList();
    var L = spec.log || {};
    if (results.length <= 1) {
      var tgt = results.length > 0 ? results[0].entity : ctx.caster;
      log.add(this._renderLog(L.template, ctx, tgt, damages ? damages[0] : 0));
      if (damages) effects.add(this._hpEffect(tgt, damages[0]));
      if (opts && opts.buffApplied) effects.add(this._buffEffect(tgt));
    } else {
      for (var i = 0; i < results.length; i++) {
        log.add(this._renderLog(L.per_target, ctx, results[i].entity, damages ? damages[i] : 0));
        if (damages) effects.add(this._hpEffect(results[i].entity, damages[i]));
        if (opts && opts.buffApplied) effects.add(this._buffEffect(results[i].entity));
      }
    }
    var animation = this.resolveAnimation(spec, ctx, results);
    var data = engine.newMap();
    data.put("log", log); data.put("effects", effects); data.put("animation", animation);
    engine.combatEvent(ctx.combatId, data);
  }
};
