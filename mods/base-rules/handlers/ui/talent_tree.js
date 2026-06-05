engine.on("ui.render_talent_tree", 100, function(event) {
  var entity = store.get(event.get("entityId"));
  if (entity === null || typeof Talent === "undefined" || Talent === null) return;
  Talent.ensureComponents(entity);
  var root = entity.getComponent("TalentTree").get("root");
  if (root === null || root === undefined) root = Talent._pathRoot(entity);
  if (root === null || root === undefined) return;
  var tree = Talent.loadTalentTree(root);
  if (tree === null || tree === undefined || tree.nodes === undefined) return;

  event.set("root", String(root));
  var total = Talent.totalPoints(levelOf(entity));
  var spent = Talent.spent(entity);
  var available = Talent.available(entity);
  event.set("points", engine.newTalentPoints(total, spent, available));

  var outOrbs = event.get("orbInventory");
  var pouch = entity.getComponent("OrbPouch");
  if (outOrbs !== null && pouch !== null) {
    var keys = pouch.getAll().keySet().iterator();
    while (keys.hasNext()) {
      var k = String(keys.next());
      outOrbs.add(engine.newOrbStack(k, pouch.getInt(k)));
    }
  }

  var childrenByParent = {};
  for (var c = 0; c < tree.nodes.length; c++) {
    var parents = tree.nodes[c].parents || [];
    for (var p = 0; p < parents.length; p++) {
      var parent = String(parents[p]);
      if (childrenByParent[parent] === undefined) childrenByParent[parent] = [];
      childrenByParent[parent].push(String(tree.nodes[c].id));
    }
  }

  var outNodes = event.get("nodes");
  if (outNodes === null) return;
  for (var i = 0; i < tree.nodes.length; i++) {
    var node = tree.nodes[i];
    var state = Talent.nodeState(entity, node);
    var parentsList = stringList(node.parents || []);
    var childrenList = stringList(childrenByParent[String(node.id)] || []);
    var slot = null;
    var actions = nodeActions(entity, node, state);
    if (node.skill_slot !== undefined && node.skill_slot !== null) {
      var s = node.skill_slot;
      var orb = s.orb || {};
      var known = Talent.knownSkill(entity, s.skill);
      slot = engine.newTalentSlot(
        String(s.skill),
        orb.type !== undefined && orb.type !== null ? String(orb.type) : "",
        parseInt(orb.count || 0),
        String(s.evolution),
        known !== null && known.get("node") !== null
      );
    }
    outNodes.add(engine.newTalentNode(
      String(node.id),
      node.label !== undefined && node.label !== null ? String(node.label) : String(node.id),
      node.icon !== undefined && node.icon !== null ? String(node.icon) : "",
      node.skill_slot !== undefined && node.skill_slot !== null ? "skill_slot" : "passive",
      parentsList,
      childrenList,
      parseInt(node.tier || 0),
      parseInt(node.order || 0),
      state,
      false,
      node.requires_spec !== undefined && node.requires_spec !== null ? String(node.requires_spec) : null,
      Talent.effectSummary(entity, node),
      slot,
      actions
    ));
  }
});

function levelOf(entity) {
  var exp = entity.getComponent("Experience");
  if (exp !== null && exp.has("level")) return exp.getInt("level");
  var ch = entity.getComponent("Character");
  return ch !== null && ch.has("level") ? ch.getInt("level") : 1;
}

function stringList(src) {
  var out = engine.newList();
  if (src === null || src === undefined) return out;
  for (var i = 0; i < src.length; i++) out.add(String(src[i]));
  return out;
}

function nodeActions(entity, node, state) {
  // 已激活态 / 隐藏:无操作,且不显示废话(reason 留空)
  if (state === "unlocked" || state === "slot-filled" || state === "hidden") {
    return engine.newTalentActions(false, false, "");
  }
  // 技能槽已点未插球:可插球
  if (state === "slot-empty") return engine.newTalentActions(false, true, "");
  // 其余(unlockable / locked / filled-node-relocked)= 尚未学会的节点:按真实学习条件判定。
  // filled-node-relocked(洗点后球已花)也走这里 → 连接 + 有点 → 可重新学习(B:免费恢复进化)。
  var canUnlock = Talent.isConnectedUnlock(entity, node) && Talent.available(entity) > 0;
  // 只在"有点但没连上"这种用户会想知道的情况给一句;其余一律留空,不堆废话。
  var reason = (!canUnlock && Talent.available(entity) <= 0) ? "天赋点不足" : "";
  return engine.newTalentActions(canUnlock, false, reason);
}
