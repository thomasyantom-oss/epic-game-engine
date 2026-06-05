var Talent = {
  _trees: {},
  _evolutionIndex: null,

  _toJs: function(v) {
    if (v === null || v === undefined) return v;
    if (typeof v.entrySet === "function") {
      var out = {};
      var it = v.keySet().iterator();
      while (it.hasNext()) {
        var k = it.next();
        out[String(k)] = this._toJs(v.get(k));
      }
      return out;
    }
    if (typeof v.size === "function" && typeof v.get === "function") {
      var arr = [];
      for (var i = 0; i < v.size(); i++) arr.push(this._toJs(v.get(i)));
      return arr;
    }
    return v;
  },

  ensureComponents: function(entity) {
    if (entity === null || entity === undefined) return;
    if (!entity.hasComponent("TalentTree")) {
      var tree = engine.newComponent("TalentTree");
      tree.set("root", null);
      tree.set("unlocked", engine.newList());
      tree.set("evolved", engine.newList());
      entity.addComponent(tree);
    } else {
      var existing = entity.getComponent("TalentTree");
      if (!existing.has("evolved") || existing.get("evolved") === null) existing.set("evolved", engine.newList());
    }
    if (!entity.hasComponent("OrbPouch")) {
      entity.addComponent(engine.newComponent("OrbPouch"));
    }
  },

  totalPoints: function(level) {
    var lv = parseInt(level || 1);
    if (lv < 10) return 0;
    return Math.floor((lv - 10) / 2) + 1;
  },

  spent: function(entity) {
    if (entity === null || entity === undefined || !entity.hasComponent("TalentTree")) return 0;
    var unlocked = entity.getComponent("TalentTree").get("unlocked");
    if (unlocked === null || unlocked === undefined) return 0;
    return typeof unlocked.size === "function" ? unlocked.size() : unlocked.length;
  },

  available: function(entity) {
    var level = 1;
    if (entity !== null && entity !== undefined) {
      var exp = entity.getComponent("Experience");
      if (exp !== null && exp.has("level")) level = exp.getInt("level");
      else {
        var ch = entity.getComponent("Character");
        if (ch !== null && ch.has("level")) level = ch.getInt("level");
      }
    }
    return Math.max(0, this.totalPoints(level) - this.spent(entity));
  },

  _path: function(entity) {
    if (entity === null || entity === undefined || !entity.hasComponent("Specialization")) return [];
    var raw = entity.getComponent("Specialization").get("path");
    var out = [];
    if (raw === null || raw === undefined) return out;
    var n = typeof raw.size === "function" ? raw.size() : raw.length;
    for (var i = 0; i < n; i++) out.push(String(typeof raw.get === "function" ? raw.get(i) : raw[i]));
    return out;
  },

  _contains: function(arr, value) {
    for (var i = 0; i < arr.length; i++) if (String(arr[i]) === String(value)) return true;
    return false;
  },

  _listContains: function(list, value) {
    if (list === null || list === undefined) return false;
    var n = typeof list.size === "function" ? list.size() : list.length;
    for (var i = 0; i < n; i++) {
      var v = typeof list.get === "function" ? list.get(i) : list[i];
      if (String(v) === String(value)) return true;
    }
    return false;
  },

  _pathRoot: function(entity) {
    var path = this._path(entity);
    return path.length > 0 ? path[0] : null;
  },

  _reject: function(event, message) {
    event.set("success", false);
    event.set("message", message);
  },

  _accept: function(event, message) {
    event.set("success", true);
    if (message !== undefined && message !== null) event.set("message", message);
  },

  _save: function(entity) {
    if (entity !== null && typeof persistence !== "undefined" && persistence !== null) persistence.save(entity);
  },

  _reapply: function(entity) {
    if (entity === null || entity === undefined) return;
    if (typeof applySpec !== "undefined") applySpec(entity.getId());
    else if (typeof Specialization !== "undefined" && Specialization !== null) Specialization.applySpec(entity.getId());
    else if (typeof Passive !== "undefined" && Passive !== null) {
      Passive.registerStatMods(entity.getId());
      engine.recalculate(entity.getId());
    }
  },

  prepareAction: function(event) {
    var playerId = event.get("playerId");
    var entity = store.get(playerId);
    if (entity === null) {
      this._reject(event, "找不到角色");
      return null;
    }
    this.ensureComponents(entity);
    var root = this._pathRoot(entity);
    if (root === null) {
      this._reject(event, "专精后解锁天赋树");
      return null;
    }
    var comp = entity.getComponent("TalentTree");
    var currentRoot = comp.get("root");
    if (currentRoot === null || currentRoot === undefined) {
      comp.set("root", root);
    } else if (String(currentRoot) !== String(root)) {
      this._reject(event, "天赋树与当前专精不匹配");
      return null;
    }
    return entity;
  },

  loadTalentTree: function(specRoot) {
    if (specRoot === null || specRoot === undefined || String(specRoot) === "") return null;
    var key = String(specRoot);
    if (this._trees[key] === undefined) {
      this._trees[key] = this._toJs(engine.loadYaml("talents/" + key + ".yaml"));
    }
    return this._trees[key];
  },

  talentNode: function(root, id) {
    var tree = this.loadTalentTree(root);
    if (tree === null || tree === undefined || tree.nodes === undefined) return null;
    for (var i = 0; i < tree.nodes.length; i++) {
      if (String(tree.nodes[i].id) === String(id)) return tree.nodes[i];
    }
    return null;
  },

  buildEvolutionIndex: function() {
    if (this._evolutionIndex !== null) return this._evolutionIndex;
    var index = {};
    var manifest = this._toJs(engine.loadYaml("talents/index.yaml"));
    var roots = manifest !== null && manifest !== undefined ? manifest.roots : null;
    if (roots !== null && roots !== undefined) {
      for (var i = 0; i < roots.length; i++) {
        var tree = this.loadTalentTree(roots[i]);
        if (tree === null || tree === undefined || tree.evolutions === undefined || tree.evolutions === null) continue;
        var ids = Object.keys(tree.evolutions);
        for (var e = 0; e < ids.length; e++) {
          var id = String(ids[e]);
          if (index[id] !== undefined) throw new Error("Duplicate talent evolution id: " + id);
          index[id] = tree.evolutions[ids[e]];
        }
      }
    }
    this._evolutionIndex = index;
    return index;
  },

  evolutionPatch: function(id) {
    if (id === null || id === undefined) return null;
    var evo = this.buildEvolutionIndex()[String(id)];
    return evo !== undefined && evo !== null ? evo.patch : null;
  },

  evolutionDisplay: function(id) {
    if (id === null || id === undefined) return null;
    var evo = this.buildEvolutionIndex()[String(id)];
    if (evo === undefined || evo === null) return null;
    return {
      name: evo.name !== undefined && evo.name !== null ? String(evo.name) : null,
      icon: evo.icon !== undefined && evo.icon !== null ? String(evo.icon) : null,
      description: evo.description !== undefined && evo.description !== null ? String(evo.description) : null
    };
  },

  effectiveNodeEffect: function(entity, node) {
    if (node === null || node === undefined) return null;
    var path = this._path(entity);
    if (node.requires_spec !== undefined && node.requires_spec !== null && !this._contains(path, node.requires_spec)) return null;
    var effect = node.effect;
    if (node.overrides !== undefined && node.overrides !== null) {
      for (var i = path.length - 1; i >= 0; i--) {
        var specId = path[i];
        if (node.overrides[specId] !== undefined && node.overrides[specId] !== null) {
          effect = node.overrides[specId];
          break;
        }
      }
    }
    return effect !== undefined ? effect : null;
  },

  _flattenPassiveEffect: function(nodeId, passive) {
    if (passive === null || passive === undefined) return null;
    if (passive.ref !== undefined && passive.ref !== null) {
      if (typeof Skill === "undefined" || Skill === null) return null;
      var raw = Skill.loadSpecAny(String(passive.ref));
      if (raw === null || raw === undefined) return null;
      var spec = Skill._toJs(raw);
      if (spec.kind !== "passive") return null;
      var refOut = {
        id: String(passive.ref),
        kind: "passive",
        effect: spec.effect,
        level: 1
      };
      if (spec.modifiers !== undefined) refOut.modifiers = spec.modifiers;
      if (spec.match !== undefined) refOut.match = spec.match;
      if (spec.patch !== undefined) refOut.patch = spec.patch;
      if (spec.name !== undefined) refOut.name = spec.name;
      if (spec.description !== undefined) refOut.description = spec.description;
      if (spec.icon !== undefined) refOut.icon = spec.icon;
      return refOut;
    }
    if (passive.stat_mod !== undefined && passive.stat_mod !== null) {
      return {
        id: "talent_" + nodeId,
        kind: "passive",
        effect: "stat_mod",
        modifiers: passive.stat_mod,
        level: 1
      };
    }
    if (passive.skill_patch !== undefined && passive.skill_patch !== null) {
      return {
        id: "talent_" + nodeId,
        kind: "passive",
        effect: "skill_patch",
        match: passive.skill_patch.match,
        patch: passive.skill_patch.patch,
        level: 1
      };
    }
    return null;
  },

  derivedPassives: function(entity) {
    var out = [];
    if (entity === null || entity === undefined || !entity.hasComponent("TalentTree")) return out;
    var comp = entity.getComponent("TalentTree");
    var root = comp.get("root");
    if (root === null || root === undefined) {
      var path = this._path(entity);
      root = path.length > 0 ? path[0] : null;
    }
    if (root === null || root === undefined) return out;
    var unlocked = comp.get("unlocked");
    if (unlocked === null || unlocked === undefined) return out;
    var n = typeof unlocked.size === "function" ? unlocked.size() : unlocked.length;
    for (var i = 0; i < n; i++) {
      var nodeId = String(typeof unlocked.get === "function" ? unlocked.get(i) : unlocked[i]);
      var node = this.talentNode(root, nodeId);
      if (node === null || node.skill_slot !== undefined) continue;
      var effect = this.effectiveNodeEffect(entity, node);
      if (effect === null || effect.passive === undefined || effect.passive === null) continue;
      var flat = this._flattenPassiveEffect(nodeId, effect.passive);
      if (flat !== null) out.push(flat);
    }
    return out;
  },

  isConnectedUnlock: function(entity, node) {
    var parents = node.parents || [];
    if (parents.length === 0) return true;
    var unlocked = entity.getComponent("TalentTree").get("unlocked");
    for (var i = 0; i < parents.length; i++) {
      if (this._listContains(unlocked, parents[i])) return true;
    }
    return false;
  },

  knownSkill: function(entity, skillId) {
    if (entity === null || !entity.hasComponent("Skillbook")) return null;
    var known = entity.getComponent("Skillbook").get("known");
    if (known === null) return null;
    for (var i = 0; i < known.size(); i++) {
      var item = known.get(i);
      if (String(item.get("base")) === String(skillId)) return item;
    }
    return null;
  },

  evolvedList: function(entity) {
    this.ensureComponents(entity);
    return entity.getComponent("TalentTree").get("evolved");
  },

  hasEvolved: function(entity, evolutionId) {
    return this._listContains(this.evolvedList(entity), evolutionId);
  },

  addEvolved: function(entity, evolutionId) {
    var evolved = this.evolvedList(entity);
    if (!this._listContains(evolved, evolutionId)) evolved.add(String(evolutionId));
  },

  listValues: function(list) {
    var out = [];
    if (list === null || list === undefined) return out;
    var n = typeof list.size === "function" ? list.size() : list.length;
    for (var i = 0; i < n; i++) out.push(String(typeof list.get === "function" ? list.get(i) : list[i]));
    return out;
  },

  restoreEvolved: function(entity, values) {
    this.ensureComponents(entity);
    var evolved = engine.newList();
    for (var i = 0; i < values.length; i++) {
      if (!this._listContains(evolved, values[i])) evolved.add(String(values[i]));
    }
    entity.getComponent("TalentTree").set("evolved", evolved);
  },

  restoreUnlocked: function(entity, values) {
    this.ensureComponents(entity);
    var unlocked = engine.newList();
    for (var i = 0; i < values.length; i++) {
      if (!this._listContains(unlocked, values[i])) unlocked.add(String(values[i]));
    }
    entity.getComponent("TalentTree").set("unlocked", unlocked);
  },

  clearActiveSlotEvolutions: function(entity) {
    if (entity === null || entity === undefined || !entity.hasComponent("TalentTree")) return;
    var comp = entity.getComponent("TalentTree");
    var root = comp.get("root");
    if (root === null || root === undefined) root = this._pathRoot(entity);
    var tree = this.loadTalentTree(root);
    if (tree === null || tree === undefined || tree.nodes === undefined) return;
    for (var i = 0; i < tree.nodes.length; i++) {
      var node = tree.nodes[i];
      if (node.skill_slot === undefined || node.skill_slot === null) continue;
      var known = this.knownSkill(entity, node.skill_slot.skill);
      if (known !== null) known.put("node", null);
    }
  },

  nodeState: function(entity, node) {
    if (entity === null || node === null || node === undefined) return "locked";
    var path = this._path(entity);
    if (node.requires_spec !== undefined && node.requires_spec !== null && !this._contains(path, node.requires_spec)) return "hidden";
    var unlocked = entity.getComponent("TalentTree").get("unlocked");
    var isUnlocked = this._listContains(unlocked, node.id);
    var slot = node.skill_slot;
    if (slot !== undefined && slot !== null) {
      var known = this.knownSkill(entity, slot.skill);
      var filled = known !== null && known.get("node") !== null;
      if (isUnlocked && filled) return "slot-filled";
      if (isUnlocked) return "slot-empty";
      if (this.hasEvolved(entity, slot.evolution)) return "filled-node-relocked";
    }
    if (isUnlocked) return "unlocked";
    if (!this.isConnectedUnlock(entity, node)) return "locked";
    return this.available(entity) > 0 ? "unlockable" : "locked";
  },

  effectSummary: function(entity, node) {
    if (node === null || node === undefined) return "";
    if (node.skill_slot !== undefined && node.skill_slot !== null) {
      var s = node.skill_slot;
      var orb = s.orb || {};
      return "主动增强 " + s.skill + " -> " + s.evolution + "; 灵魂球 " + orb.type + " x" + orb.count;
    }
    var effect = this.effectiveNodeEffect(entity, node);
    if (effect === null || effect.passive === undefined || effect.passive === null) return "";
    var passive = effect.passive;
    if (passive.ref !== undefined && passive.ref !== null) return "被动 " + passive.ref;
    if (passive.stat_mod !== undefined && passive.stat_mod !== null) return "被动 stat_mod";
    if (passive.skill_patch !== undefined && passive.skill_patch !== null) return "主动增强 skill_patch";
    return "被动";
  }
};

engine.on("action.talent_unlock", 100, function(event) {
  var entity = Talent.prepareAction(event);
  if (entity === null) return;
  var nodeId = String(event.get("nodeId"));
  var root = entity.getComponent("TalentTree").get("root");
  var node = Talent.talentNode(root, nodeId);
  if (node === null) {
    Talent._reject(event, "未知天赋节点");
    return;
  }
  var unlocked = entity.getComponent("TalentTree").get("unlocked");
  if (Talent._listContains(unlocked, nodeId)) {
    Talent._reject(event, "该节点已学会");
    return;
  }
  var path = Talent._path(entity);
  if (node.requires_spec !== undefined && node.requires_spec !== null && !Talent._contains(path, node.requires_spec)) {
    Talent._reject(event, "专精路线不满足");
    return;
  }
  if (Talent.available(entity) <= 0) {
    Talent._reject(event, "天赋点不足");
    return;
  }
  if (!Talent.isConnectedUnlock(entity, node)) {
    Talent._reject(event, "必须连接已学会的节点");
    return;
  }
  var evolvedBefore = Talent.listValues(Talent.evolvedList(entity));
  var unlockedAfter = Talent.listValues(unlocked);
  if (!Talent._contains(unlockedAfter, nodeId)) unlockedAfter.push(nodeId);
  unlocked.add(nodeId);
  Talent._reapply(entity);
  Talent.restoreUnlocked(entity, unlockedAfter);
  Talent.restoreEvolved(entity, evolvedBefore);
  if (node.skill_slot !== undefined && node.skill_slot !== null && Talent.hasEvolved(entity, node.skill_slot.evolution)) {
    var known = Talent.knownSkill(entity, node.skill_slot.skill);
    if (known !== null) known.put("node", String(node.skill_slot.evolution));
  }
  Talent._save(entity);
  Talent._accept(event, "已学习");
});

engine.on("action.talent_respec", 100, function(event) {
  var entity = Talent.prepareAction(event);
  if (entity === null) return;
  // 就地清空(而非替换引用):recalculate 会把"替换掉的引用"还原回基线捕获的旧列表,
  // 但就地修改对基线同一对象可见 —— 与 talent_unlock 的 unlocked.add() 同款模式。
  var evolvedBefore = Talent.listValues(Talent.evolvedList(entity));
  var unlocked = entity.getComponent("TalentTree").get("unlocked");
  if (unlocked !== null && typeof unlocked.clear === "function") unlocked.clear();
  else entity.getComponent("TalentTree").set("unlocked", engine.newList());
  Talent.clearActiveSlotEvolutions(entity);
  Talent._reapply(entity);   // 摘掉天赋授予的被动(走 registerStatMods 的 previous−next 清理)
  Talent.restoreUnlocked(entity, []);
  Talent.restoreEvolved(entity, evolvedBefore);
  Talent.clearActiveSlotEvolutions(entity);
  Talent._save(entity);
  Talent._accept(event, "已重置");
});

engine.on("action.talent_place_orb", 100, function(event) {
  var entity = Talent.prepareAction(event);
  if (entity === null) return;
  var nodeId = String(event.get("nodeId"));
  var root = entity.getComponent("TalentTree").get("root");
  var node = Talent.talentNode(root, nodeId);
  if (node === null || node.skill_slot === undefined || node.skill_slot === null) {
    Talent._reject(event, "该节点不是技能槽");
    return;
  }
  var unlocked = entity.getComponent("TalentTree").get("unlocked");
  if (!Talent._listContains(unlocked, nodeId)) {
    Talent._reject(event, "技能槽尚未学习");
    return;
  }
  var slot = node.skill_slot;
  var known = Talent.knownSkill(entity, slot.skill);
  if (known === null) {
    Talent._reject(event, "尚未学会该技能");
    return;
  }
  var pouch = entity.getComponent("OrbPouch");
  var orb = slot.orb || {};
  var type = String(orb.type);
  var count = parseInt(orb.count || 0);
  var have = pouch !== null && pouch.has(type) ? pouch.getInt(type) : 0;
  if (have < count) {
    Talent._reject(event, "灵魂球不足");
    return;
  }
  Talent._reapply(entity);
  pouch = entity.getComponent("OrbPouch");
  known = Talent.knownSkill(entity, slot.skill);
  if (pouch === null || known === null) {
    Talent._reject(event, "技能槽状态已失效");
    return;
  }
  pouch.set(type, have - count);
  Talent.addEvolved(entity, slot.evolution);
  known.put("node", String(slot.evolution));
  Talent._save(entity);
  Talent._accept(event, "已进化");
});

engine.on("debug.grant_orb", 100, function(event) {
  var entity = store.get(event.get("entityId"));
  if (entity === null) {
    event.set("ok", false);
    return;
  }
  Talent.ensureComponents(entity);
  var type = String(event.get("type"));
  var count = parseInt(event.get("count") || 0);
  var pouch = entity.getComponent("OrbPouch");
  var current = pouch.has(type) ? pouch.getInt(type) : 0;
  pouch.set(type, current + count);
  event.set("ok", true);
  Talent._save(entity);
});
