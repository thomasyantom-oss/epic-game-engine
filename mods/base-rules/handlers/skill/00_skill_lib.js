var Skill = {
  _specs: {},
  loadSpec: function(type) {
    if (this._specs[type] === undefined) {
      this._specs[type] = engine.loadYaml("skills/" + type + ".yaml");
    }
    return this._specs[type];
  },

  loadSpecAny: function(id) {
    var s = this.loadSpec(id);
    if (s !== null && s !== undefined) return s;
    var key = "passive:" + id;
    if (this._specs[key] === undefined) {
      this._specs[key] = engine.loadYaml("passives/" + id + ".yaml");
    }
    return this._specs[key];
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
    // 伤害 = base 属性(若声明)+ add + Σ⌈属性 × 系数⌉。
    // scaling 两级查找:先 DerivedStats(三强度)再 PrimaryStats(裸基础属性),见 lookupStat。
    var statName = dmgSpec.base;
    var base = (statName && caster.hasComponent("CombatStats"))
        ? caster.getComponent("CombatStats").getInt(statName) : 0;
    var total = base + (dmgSpec.add || 0);
    if (dmgSpec.scaling) {
        var keys = Object.keys(dmgSpec.scaling);
        for (var i = 0; i < keys.length; i++) {
            total += Math.ceil(this.lookupStat(caster, keys[i]) * dmgSpec.scaling[keys[i]]);
        }
    }
    return total;
  },

  // 两级查找一个属性值：先 DerivedStats 再 PrimaryStats，取不到(或无 caster)返回 0。
  lookupStat: function(caster, key) {
    if (caster === null || caster === undefined) return 0;
    var ds = caster.getComponent("DerivedStats");
    if (ds !== null && ds.has(key)) return ds.getInt(key);
    var ps = caster.getComponent("PrimaryStats");
    if (ps !== null && ps.has(key)) return ps.getInt(key);
    return 0;
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
      // Frontend BattleGrid contract: targetRow is grid row -> CombatPosition.slot,
      // targetCol is grid col -> CombatPosition.rowIdx (FRONT/MID/BACK mapped to 0/1/2).
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
  // skipLog controls ONLY the event flag (it does not call present()):
  //   false  -> combat_events.js + combat_log.js build the presentation (engine default flow).
  //   true / omitted (default) -> those handlers are suppressed; the CALLER is then responsible
  //                               for calling present() to emit the combatEvent + CombatLog entry.
  dealDamage: function(ctx, target, amount, skillId, skipLog) {
    this.applyDamage(target, amount);
    this.fireDamageDealt(ctx, target, amount, skillId, skipLog);
  },

  ownerInstance: function(ctx, baseId) {
    var c = ctx ? ctx.caster : null;
    if (c === null || c === undefined || !c.hasComponent("Skillbook")) return { node: null, level: 1 };
    var known = c.getComponent("Skillbook").get("known");
    if (known === null) return { node: null, level: 1 };
    for (var i = 0; i < known.size(); i++) {
      var k = known.get(i);
      if (String(k.get("base")) === String(baseId)) {
        return {
          node: k.get("node") !== null ? String(k.get("node")) : null,
          level: k.containsKey && k.containsKey("level") ? parseInt(k.get("level")) : (k.has && k.has("level") ? k.getInt("level") : 1)
        };
      }
    }
    return { node: null, level: 1 };
  },

  resolveSpec: function(ctx, baseId, baseSpec) {
    var spec = JSON.parse(JSON.stringify(baseSpec));
    var inst = this.ownerInstance(ctx, baseId);
    spec = this.applyLevelScaling(spec, inst.level);
    spec = this.applyNode(spec, inst.node);
    spec = this.applyPassivePatches(ctx, baseId, spec);
    spec = this.applySpecializationPatches(ctx, baseId, spec);
    return spec;
  },

  applyLevelScaling: function(spec, level) {
    var lv = parseInt(level || 1);
    if (lv <= 1 || spec.level_scaling == null) return spec;
    var keys = Object.keys(spec.level_scaling);
    for (var i = 0; i < keys.length; i++) {
      this._applyPatchValue(spec, keys[i], spec.level_scaling[keys[i]] * (lv - 1));
    }
    return spec;
  },

  applyNode: function(spec, node) {
    return spec;
  },

  applyPassivePatches: function(ctx, baseId, spec) {
    var passives = this.ownedPassives(ctx);
    for (var i = 0; i < passives.length; i++) {
      var ps = passives[i];
      if (ps.effect !== "skill_patch") continue;
      if (!this._matchSkill(ps.match, baseId, spec)) continue;
      this._applyPatch(spec, ps.patch);
    }
    return spec;
  },

  applySpecializationPatches: function(ctx, baseId, spec) {
    var caster = ctx ? ctx.caster : null;
    if (caster === null || caster === undefined) return spec;
    if (!caster.hasComponent("Character") || !caster.hasComponent("Specialization")) return spec;
    var classId = caster.getComponent("Character").getString("classId");
    var path = caster.getComponent("Specialization").get("path");
    if (path === null) return spec;
    var size = typeof path.size === "function" ? path.size() : path.length;
    for (var i = 0; i < size; i++) {
      var nodeId = typeof path.get === "function" ? path.get(i) : path[i];
      var node = null;
      if (typeof Specialization !== "undefined") {
        node = Specialization.specNode(classId, nodeId);
      } else {
        var tree = this._toJs(engine.loadYaml("specializations/" + classId + ".yaml"));
        if (tree !== null && tree !== undefined && tree.nodes !== undefined) {
          for (var n = 0; n < tree.nodes.length; n++) {
            if (String(tree.nodes[n].id) === String(nodeId)) node = tree.nodes[n];
          }
        }
      }
      if (node === null || node.skill_patches === undefined || node.skill_patches === null) continue;
      for (var p = 0; p < node.skill_patches.length; p++) {
        var patch = node.skill_patches[p];
        if (!this._matchSkill(patch.match, baseId, spec)) continue;
        this._applyPatch(spec, patch.patch);
      }
    }
    return spec;
  },

  ownedPassives: function(ctx) {
    var out = [];
    var c = ctx ? ctx.caster : null;
    if (c === null || c === undefined || !c.hasComponent("Skillbook")) return out;
    var known = c.getComponent("Skillbook").get("known");
    if (known === null) return out;
    for (var i = 0; i < known.size(); i++) {
      var base = String(known.get(i).get("base"));
      var raw = this.loadSpecAny(base);
      if (raw === null || raw === undefined) continue;
      var spec = this._toJs(raw);
      if (spec.kind === "passive") out.push(spec);
    }
    return out;
  },

  _matchSkill: function(match, baseId, spec) {
    if (match == null) return true;
    var keys = Object.keys(match);
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (k === "skill") {
        if (String(match[k]) !== String(baseId)) return false;
      } else if (this._getPath(spec, k) !== match[k]) {
        return false;
      }
    }
    return true;
  },

  _applyPatch: function(spec, patch) {
    if (patch == null) return;
    var keys = Object.keys(patch);
    for (var i = 0; i < keys.length; i++) this._applyPatchValue(spec, keys[i], patch[keys[i]]);
  },

  _applyPatchValue: function(spec, path, value) {
    var parts = path.split(".");
    var obj = spec;
    for (var i = 0; i < parts.length - 1; i++) {
      if (obj[parts[i]] === undefined || obj[parts[i]] === null) return;
      obj = obj[parts[i]];
    }
    var key = parts[parts.length - 1];
    if (typeof value === "number" && typeof obj[key] === "number") obj[key] = obj[key] + value;
    else obj[key] = value;
  },

  _getPath: function(obj, path) {
    var parts = path.split(".");
    var cur = obj;
    for (var i = 0; i < parts.length; i++) {
      if (cur === null || cur === undefined) return undefined;
      cur = cur[parts[i]];
    }
    return cur;
  },

  // 唯一减伤收口。delivery: "普攻" -> 护甲(flat,逐位对齐现行 damage_calc);
  // "技能" -> 类型抗% + 元素乘区。本期元素仅建框架,默认空跑。
  // opts = { delivery, type, element, elementAmp, ignoreDefend }
  mitigate: function(target, raw, opts) {
    opts = opts || {};
    var defending = target.hasComponent("Buff_defending") && !opts.ignoreDefend;
    var type = opts.type || "物理";
    var fin;
    if (opts.delivery === "普攻") {
      var armor = target.hasComponent("CombatStats")
          ? target.getComponent("CombatStats").getInt("defense") : 0;
      if (raw <= 0) raw = 1;
      if (this._useArmorCurve()) {
        var K = this._armorK();
        var reduced = raw * (1 - armor / (armor + K * raw));
        fin = Math.max(1, Math.ceil(reduced));
      } else {
        fin = Math.max(1, raw - armor);
      }
      if (defending) fin = Math.max(1, Math.floor(fin * 0.5));
      fin = this._applyVariance(fin);
      this._emitMitigation(target, raw, fin, "普攻", opts.delivery);
      return fin;
    }

    var res = target.hasComponent("Resistances") ? target.getComponent("Resistances") : null;
    var typeResist = this._clampResist((res !== null && res.has(type)) ? res.getInt(type) : 0);
    var element = opts.element || null;
    var elementResist = this._clampResist((element && res !== null && res.has(element)) ? res.getInt(element) : 0);
    var elementAmp = opts.elementAmp || 0;
    var skillDamage = raw
        * (1 + elementAmp / 100)
        * (1 - typeResist / 100)
        * (1 - elementResist / 100);
    if (defending) skillDamage = skillDamage * 0.5;
    fin = Math.max(1, Math.ceil(skillDamage));
    fin = this._applyVariance(fin);
    this._emitMitigation(target, raw, fin, type, opts.delivery);
    return fin;
  },

  _useArmorCurve: function() {
    return !(typeof tuning !== 'undefined' && tuning && !tuning.armorModelCurve());
  },

  _armorK: function() {
    if (typeof tuning !== 'undefined' && tuning) return Math.max(1, tuning.armorK());
    return 1;
  },

  _resistCap: function() {
    if (typeof tuning !== 'undefined' && tuning) return tuning.resistCap();
    return 75;
  },

  _resistFloor: function() {
    if (typeof tuning !== 'undefined' && tuning) return tuning.resistFloor();
    return -50;
  },

  _clampResist: function(value) {
    var cap = this._resistCap();
    var floor = this._resistFloor();
    if (value > cap) return cap;
    if (value < floor) return floor;
    return value;
  },

  _applyVariance: function(fin) {
    if (typeof tuning === 'undefined' || !tuning) return fin;
    var factor = tuning.rollVariance();
    return Math.max(1, Math.ceil(fin * factor));
  },

  _emitMitigation: function(target, raw, fin, type, delivery) {
    var ev = engine.newEvent("combat.mitigation");
    ev.set("targetId", target.getId());
    ev.set("raw", raw);
    ev.set("final", fin);
    ev.set("type", type);
    ev.set("delivery", delivery || "技能");
    engine.fire("combat.mitigation", ev);
  },

  // Mutate target HP only — does NOT fire any event (so no death cascade yet).
  // present()-path effects call this first, present() the skill animation, THEN
  // fireDamageDealt() — keeping the skill animation ahead of the death event in the queue.
  applyDamage: function(target, amount) {
    var health = target.getComponent("Health");
    health.set("hp", Math.max(0, health.getInt("hp") - amount));
  },

  // Fire combat.damage_dealt (death detection + future on-hit hooks). HP is assumed
  // already mutated by applyDamage. skipLog: see dealDamage note above.
  fireDamageDealt: function(ctx, target, amount, skillId, skipLog) {
    var ev = engine.newEvent("combat.damage_dealt");
    ev.set("attackerId", ctx.actorId);
    ev.set("targetId", target.getId());
    ev.set("damage", amount);
    ev.set("combatId", ctx.combatId);
    ev.set("skillId", skillId);
    if (skipLog !== false) ev.set("skipLog", true);   // default true; pass false to use combat_events presentation
    engine.fire("combat.damage_dealt", ev);
  },

  // 从 buff spec 应用 buff，解析 "@caster" 引用；
  // 若 buffSpec.scaling 存在(如 {damage:{智力:0.1}})，把 data 中对应字段加上 Σ⌈caster属性×系数⌉。
  applyBuffFromSpec: function(ctx, target, buffSpec) {
    var data = engine.newMap();
    var src = buffSpec.data || {};
    var keys = Object.keys(src);
    for (var i = 0; i < keys.length; i++) {
      var v = src[keys[i]];
      if (v === "@caster") v = ctx.actorId;   // resolve caster reference
      data.put(keys[i], v);
    }
    if (buffSpec.scaling) {
      var fields = Object.keys(buffSpec.scaling);          // 如 ["damage"]
      for (var f = 0; f < fields.length; f++) {
        var field = fields[f];
        var coefs = buffSpec.scaling[field];               // 如 {智力:0.1}
        var add = 0;
        var statKeys = Object.keys(coefs);
        for (var s = 0; s < statKeys.length; s++) {
          add += Math.ceil(this.lookupStat(ctx.caster, statKeys[s]) * coefs[statKeys[s]]);
        }
        var baseVal = data.containsKey(field) ? data.get(field) : 0;
        data.put(field, baseVal + add);
      }
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

  // Build a buff_applied effect entry. When buffId is given, embed a self-describing buff
  // descriptor read from the freshly-applied Buff_<id> component (mirrors snapshot BuffInfo).
  // This lets the frontend render the icon DURING this round's animation even for a buff that
  // is removed before the post-resolve snapshot (e.g. a "当回合即逝" buff like defend) — and is
  // harmless for persistent buffs (the frontend dedups by id against the snapshot).
  _buffEffect: function(target, buffId) {
    var eff = engine.newMap();
    eff.put("type", "buff_applied");
    eff.put("target", target.getId());
    if (buffId) {
      var comp = target.getComponent("Buff_" + buffId);
      if (comp !== null) {
        var desc = engine.newMap();
        desc.put("id", buffId);
        desc.put("stacks", comp.has("stacks") ? comp.getInt("stacks") : 1);
        desc.put("color", comp.has("color") ? comp.get("color") : null);
        desc.put("positive", comp.has("positive") && comp.getBoolean("positive"));
        desc.put("remaining", comp.has("remaining") ? comp.getInt("remaining") : -1);
        eff.put("buff", desc);
      }
    }
    return eff;
  },

  // Bespoke skill helpers: preserve the legacy direct-queue event shape while
  // keeping repeated segment/effect/queue boilerplate in one place.
  summaryLog: function(ctx, beforeDamage, amount, afterDamage) {
    var segments = engine.newList();
    var s1 = engine.newMap(); s1.put("text", ctx.casterName); s1.put("color", ctx.casterSide); segments.add(s1);
    var s2 = engine.newMap(); s2.put("text", beforeDamage); s2.put("color", "text"); segments.add(s2);
    var s3 = engine.newMap(); s3.put("text", "" + amount); s3.put("color", "damage"); segments.add(s3);
    var s4 = engine.newMap(); s4.put("text", afterDamage); s4.put("color", "text"); segments.add(s4);
    return segments;
  },

  nestedHpEffect: function(target, amount) {
    var eff = engine.newMap();
    eff.put("target", target.getId());
    eff.put("type", "hp_change");
    var data = engine.newMap();
    data.put("amount", -amount);
    data.put("hp", target.getComponent("Health").getInt("hp"));
    data.put("maxHp", target.getComponent("Health").getInt("maxHp"));
    eff.put("data", data);
    return eff;
  },

  emitCombatQueueEvent: function(combatId, segments, effects, animation) {
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatEvents")) return;
    var evt = engine.newMap();
    evt.put("segments", segments);
    evt.put("effects", effects);
    evt.put("animation", animation);
    combat.getComponent("CombatEvents").get("queue").add(evt);
  },

  // Flat-format animation resolution mirroring combat_events.resolveSkillAnimation.
  // A step whose `target` is "each" (or "field") expands to one entry per results[i].
  // Other steps use actor/target(first-target)/actor_side token replacement as before.
  // damages: optional array of per-target damage values; a step field with value "@damage"
  //   resolves to -damages[r] (negative, as displayed) for the r-th expanding result.
  resolveAnimation: function(spec, ctx, results, damages) {
    var out = engine.newList();
    var animDef = spec.animation;
    if (animDef == null) return out;
    var firstTargetId = results.length > 0 ? results[0].entity.getId() : null;
    for (var i = 0; i < animDef.length; i++) {
      var step = animDef[i];
      var targetVal = step["target"];
      if (targetVal === "each" || targetVal === "field") {
        // Expand: one entry per result, replacing "target" with each entity's id
        for (var r = 0; r < results.length; r++) {
          var anim = engine.newMap();
          var keys = Object.keys(step);
          for (var k = 0; k < keys.length; k++) {
            var val = step[keys[k]];
            if (keys[k] === "target") val = results[r].entity.getId();
            else if (val === "actor") val = ctx.actorId;
            else if (val === "actor_side") val = ctx.casterSide;
            else if (val === "@damage") val = damages ? -(damages[r]) : 0;
            anim.put(keys[k], val);
          }
          // damage_number without an explicit value gets the per-target damage (negative, as displayed)
          if (step["type"] === "damage_number" && step["value"] === undefined && damages) {
            anim.put("value", -(damages[r]));
          }
          out.add(anim);
        }
      } else {
        var anim = engine.newMap();
        var keys = Object.keys(step);
        for (var k = 0; k < keys.length; k++) {
          var val = step[keys[k]];
          if (val === "actor") val = ctx.actorId;
          else if (val === "target") val = firstTargetId;
          else if (val === "actor_side") val = ctx.casterSide;
          else if (val === "@damage") val = damages ? -(damages[0]) : 0;
          anim.put(keys[k], val);
        }
        // damage_number without an explicit value gets the first target's damage (negative, as displayed)
        if (step["type"] === "damage_number" && step["value"] === undefined && damages) {
          anim.put("value", -(damages[0]));
        }
        out.add(anim);
      }
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
  // Log mode:
  //   single result or per_target absent -> L.template used once (with first/only target)
  //   multiple results + L.per_target present -> one L.per_target line per result
  //   multiple results + L.per_target absent (L.template only) -> ONE summary line, per-target effects + animation
  // opts.buffApplied: when true, appends a buff_applied effect entry per result target.
  present: function(ctx, spec, results, damages, opts) {
    var log = engine.newList();
    var effects = engine.newList();
    var L = spec.log || {};
    // The buff a buffApplied effect describes: from spec.debuff or spec.buff (data-driven skills).
    var buffId = (opts && opts.buffApplied)
        ? ((spec.debuff && spec.debuff.id) || (spec.buff && spec.buff.id) || null) : null;
    var summaryMode = results.length > 1 && !L.per_target;
    if (summaryMode) {
      // ONE summary log line using L.template (caster only, no per-target substitution needed)
      var tgt = results.length > 0 ? results[0].entity : ctx.caster;
      log.add(this._renderLog(L.template, ctx, tgt, damages ? damages[0] : 0));
      // Per-target effects
      for (var i = 0; i < results.length; i++) {
        if (damages) effects.add(this._hpEffect(results[i].entity, damages[i]));
        if (opts && opts.buffApplied) effects.add(this._buffEffect(results[i].entity, buffId));
      }
    } else if (results.length === 0) {
      // AOE resolved to no targets (e.g. cast on empty ground). Emit only a flavor log if a
      // template exists — NEVER fabricate a caster-targeted hp/buff effect (that produced NaN).
      if (L.template) log.add(this._renderLog(L.template, ctx, ctx.caster, 0));
    } else if (results.length === 1) {
      var tgt = results[0].entity;
      log.add(this._renderLog(L.template, ctx, tgt, damages ? damages[0] : 0));
      if (damages) effects.add(this._hpEffect(tgt, damages[0]));
      if (opts && opts.buffApplied) effects.add(this._buffEffect(tgt, buffId));
    } else {
      for (var i = 0; i < results.length; i++) {
        log.add(this._renderLog(L.per_target, ctx, results[i].entity, damages ? damages[i] : 0));
        if (damages) effects.add(this._hpEffect(results[i].entity, damages[i]));
        if (opts && opts.buffApplied) effects.add(this._buffEffect(results[i].entity, buffId));
      }
    }
    var animation = this.resolveAnimation(spec, ctx, results, damages);
    var data = engine.newMap();
    data.put("log", log); data.put("effects", effects); data.put("animation", animation);
    engine.combatEvent(ctx.combatId, data);
  }
};
