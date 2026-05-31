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
    var combatants = store.getByTagAsList("combat:" + ctx.combatId);
    var live = [];
    for (var i = 0; i < combatants.size(); i++) {
      var e = combatants.get(i);
      var isAlly = e.hasTag("player");
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
  }
};
