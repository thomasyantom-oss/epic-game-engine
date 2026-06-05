engine.on("ui.render_specialization", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null || !entity.hasComponent("Character") || !entity.hasComponent("Specialization")) return;

    var classId = entity.getComponent("Character").getString("classId");
    var tree = loadSpecTree(classId);
    var pathComp = entity.getComponent("Specialization").get("path");
    var selected = {};
    var selectedTiers = {};
    var pathIds = [];
    var outPath = event.get("path");
    var outLocked = event.get("locked");
    if (pathComp !== null) {
        for (var p = 0; p < pathComp.size(); p++) {
            var nodeId = String(pathComp.get(p));
            var pathNode = specNode(classId, nodeId);
            pathIds.push(nodeId);
            selected[nodeId] = true;
            if (pathNode !== null) {
                selectedTiers[parseInt(pathNode.tier)] = true;
                if (outPath !== null) {
                    outPath.add(engine.newSpecPathNode(nodeId, labelOf(pathNode, nodeId)));
                }
            }
        }
    }

    if (tree === null || tree.nodes === undefined) {
        event.set("pending", null);
        return;
    }

    var level = 1;
    if (entity.hasComponent("Experience")) {
        level = entity.getComponent("Experience").getInt("level");
    } else if (entity.hasComponent("Character")) {
        level = entity.getComponent("Character").getInt("level");
    }

    var parent = pathIds.length > 0 ? pathIds[pathIds.length - 1] : null;
    var pendingNodes = [];
    for (var i = 0; i < tree.nodes.length; i++) {
        var node = tree.nodes[i];
        var nodeParent = node.parent === undefined ? null : node.parent;
        var tier = parseInt(node.tier);
        var requires = parseInt(node.requires_level);
        if (sameParent(nodeParent, parent) && selectedTiers[tier] !== true && requires <= level) {
            pendingNodes.push(node);
        }
    }

    if (pendingNodes.length > 0) {
        var options = engine.newList();
        var minRequires = parseInt(pendingNodes[0].requires_level);
        var pendingTier = parseInt(pendingNodes[0].tier);
        for (var o = 0; o < pendingNodes.length; o++) {
            var opt = pendingNodes[o];
            minRequires = Math.min(minRequires, parseInt(opt.requires_level));
            options.add(engine.newSpecOption(
                String(opt.id),
                labelOf(opt, opt.id),
                opt.description !== undefined && opt.description !== null ? String(opt.description) : "",
                engine.newSpecEffects(
                    opt.main_attr !== undefined && opt.main_attr !== null ? String(opt.main_attr) : null,
                    copyMap(finalGrowthAfterOption(entity, opt)),
                    copyStringList(opt.grant_passives)
                )
            ));
        }
        event.set("pending", engine.newSpecPending(pendingTier, minRequires, options));
    } else {
        event.set("pending", null);
    }

    var lockedByTier = {};
    for (var n = 0; n < tree.nodes.length; n++) {
        var lockedNode = tree.nodes[n];
        var lockedParent = lockedNode.parent === undefined ? null : lockedNode.parent;
        var lockedTier = parseInt(lockedNode.tier);
        var lockedRequires = parseInt(lockedNode.requires_level);
        if (selectedTiers[lockedTier] === true || lockedRequires <= level) continue;
        if (!parentInSelectedPath(lockedParent, selected)) continue;

        var existing = lockedByTier[lockedTier];
        if (existing === undefined || lockedRequires < existing) lockedByTier[lockedTier] = lockedRequires;
    }

    if (outLocked !== null) {
        var tiers = Object.keys(lockedByTier).map(function(t) { return parseInt(t); }).sort(function(a, b) { return a - b; });
        for (var l = 0; l < tiers.length; l++) {
            outLocked.add(engine.newSpecLocked(tiers[l], lockedByTier[tiers[l]]));
        }
    }
});

function sameParent(left, right) {
    if (left === null || left === undefined) return right === null || right === undefined;
    return right !== null && right !== undefined && String(left) === String(right);
}

function parentInSelectedPath(parent, selected) {
    if (parent === null || parent === undefined) return true;
    return selected[String(parent)] === true;
}

function labelOf(node, fallback) {
    return node.label !== undefined && node.label !== null ? String(node.label) : String(fallback);
}

function copyMap(src) {
    if (src === undefined || src === null) return null;
    var out = engine.newMap();
    var keys = Object.keys(src);
    for (var i = 0; i < keys.length; i++) out.put(keys[i], src[keys[i]]);
    return out;
}

function finalGrowthAfterOption(entity, option) {
    var growth = typeof effectiveGrowth !== "undefined" ? effectiveGrowth(entity) : {};
    if (option.growth === undefined || option.growth === null) return growth;
    var out = {};
    var keys = Object.keys(option.growth);
    for (var i = 0; i < keys.length; i++) out[keys[i]] = option.growth[keys[i]];
    return out;
}

function copyStringList(src) {
    var out = engine.newList();
    if (src === undefined || src === null) return out;
    for (var i = 0; i < src.length; i++) out.add(String(src[i]));
    return out;
}
