var Specialization = {
    _trees: {},

    _toJs: function(v) {
        if (v === null || v === undefined) return v;
        if (typeof v.entrySet === "function") {
            var out = {};
            var keys = v.keySet().iterator();
            while (keys.hasNext()) {
                var k = keys.next();
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

    _field: function(obj, key) {
        if (obj === null || obj === undefined) return null;
        if (typeof obj.get === "function") {
            var v = obj.get(key);
            return v === undefined ? null : v;
        }
        return obj[key] === undefined ? null : obj[key];
    },

    _pathList: function(entity) {
        if (entity === null || !entity.hasComponent("Specialization")) return null;
        var comp = entity.getComponent("Specialization");
        return comp !== null ? comp.get("path") : null;
    },

    _pathSize: function(path) {
        if (path === null || path === undefined) return 0;
        return typeof path.size === "function" ? path.size() : path.length;
    },

    _pathGet: function(path, index) {
        return typeof path.get === "function" ? path.get(index) : path[index];
    },

    loadSpecTree: function(classId) {
        if (classId === null || classId === undefined || String(classId) === "") return null;
        if (this._trees[classId] === undefined) {
            this._trees[classId] = this._toJs(engine.loadYaml("specializations/" + classId + ".yaml"));
        }
        return this._trees[classId];
    },

    specNode: function(classId, id) {
        var tree = this.loadSpecTree(classId);
        if (tree === null || tree === undefined || tree.nodes === undefined || id === null || id === undefined) return null;
        for (var i = 0; i < tree.nodes.length; i++) {
            if (String(tree.nodes[i].id) === String(id)) return tree.nodes[i];
        }
        return null;
    },

    pathNodes: function(entity) {
        var out = [];
        if (entity === null || !entity.hasComponent("Character")) return out;
        var ch = entity.getComponent("Character");
        var classId = ch.getString("classId");
        var path = this._pathList(entity);
        var count = this._pathSize(path);
        for (var i = 0; i < count; i++) {
            var node = this.specNode(classId, this._pathGet(path, i));
            if (node !== null) out.push(node);
        }
        return out;
    },

    effectiveGrowth: function(entity) {
        if (entity === null || !entity.hasComponent("Character")) return {};
        var ch = entity.getComponent("Character");
        var classSchema = schemas.get(ch.getString("classId"));
        if (classSchema === null) return {};
        var base = this._toJs(classSchema.raw().get("growth"));
        var growth = {};
        if (base !== null && base !== undefined) {
            var baseKeys = Object.keys(base);
            for (var i = 0; i < baseKeys.length; i++) growth[baseKeys[i]] = base[baseKeys[i]];
        }
        var nodes = this.pathNodes(entity);
        for (var n = 0; n < nodes.length; n++) {
            if (nodes[n].growth !== undefined && nodes[n].growth !== null) {
                growth = {};
                var g = nodes[n].growth;
                var keys = Object.keys(g);
                for (var k = 0; k < keys.length; k++) growth[keys[k]] = g[keys[k]];
            }
        }
        return growth;
    },

    effectiveSpecNode: function(entity) {
        var nodes = this.pathNodes(entity);
        return nodes.length > 0 ? nodes[nodes.length - 1] : null;
    },

    effectiveClassLabel: function(entity) {
        if (entity === null || !entity.hasComponent("Character")) return "";
        var node = this.effectiveSpecNode(entity);
        if (node !== null && node.label !== undefined && node.label !== null) return String(node.label);
        var ch = entity.getComponent("Character");
        return ch.has("classLabel") ? ch.getString("classLabel") : "";
    },

    effectiveDescription: function(entity) {
        if (entity === null || !entity.hasComponent("Character")) return "";
        var node = this.effectiveSpecNode(entity);
        if (node !== null && node.description !== undefined && node.description !== null) return String(node.description);
        var classId = entity.getComponent("Character").getString("classId");
        var classSchema = schemas.get(classId);
        return classSchema !== null && classSchema.description() !== null ? String(classSchema.description()) : "";
    },

    effectivePortrait: function(entity) {
        if (entity === null || !entity.hasComponent("Character")) return null;
        var node = this.effectiveSpecNode(entity);
        if (node !== null && node.portrait !== undefined && node.portrait !== null) return String(node.portrait);
        var classId = entity.getComponent("Character").getString("classId");
        var classSchema = schemas.get(classId);
        if (classSchema === null || classSchema.raw().get("portrait") === null) return null;
        return String(classSchema.raw().get("portrait"));
    },

    registerSpecModifier: function(entityId) {
        engine.addModifier(entityId, {
            typeId: "spec",
            id: "spec_mod",
            label: "专精",
            apply: function(ent) {
                var p = ent.getComponent("PrimaryStats");
                if (p === null) return;
                var nodes = Specialization.pathNodes(ent);
                for (var i = 0; i < nodes.length; i++) {
                    if (nodes[i].main_attr !== undefined && nodes[i].main_attr !== null) {
                        p.set("weaponAttr", String(nodes[i].main_attr));
                    }
                }
            }
        });
    },

    ensureComponent: function(entity) {
        if (entity === null || entity.hasComponent("Specialization")) return;
        var specComp = engine.newComponent("Specialization");
        specComp.set("path", engine.newList());
        entity.addComponent(specComp);
    },

    grantPassives: function(entityId) {
        var entity = store.get(entityId);
        if (entity === null || !entity.hasComponent("Skillbook")) return;
        var known = entity.getComponent("Skillbook").get("known");
        if (known === null) return;
        var nodes = this.pathNodes(entity);
        for (var i = 0; i < nodes.length; i++) {
            var grants = nodes[i].grant_passives;
            if (grants === undefined || grants === null) continue;
            for (var g = 0; g < grants.length; g++) {
                var passiveId = String(grants[g]);
                var exists = false;
                for (var k = 0; k < known.size(); k++) {
                    if (String(known.get(k).get("base")) === passiveId) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    var inst = engine.newMap();
                    inst.put("base", passiveId);
                    inst.put("node", null);
                    inst.put("level", 1);
                    known.add(inst);
                }
            }
        }
    },

    applySpec: function(entityId) {
        var entity = store.get(entityId);
        if (entity === null) return;
        this.ensureComponent(entity);
        this.registerSpecModifier(entityId);
        var exp = entity.getComponent("Experience");
        var level = exp !== null ? exp.getInt("level") : 1;
        if (typeof registerLevelGrowthModifier !== "undefined") {
            registerLevelGrowthModifier(entityId, level);
        }
        this.grantPassives(entityId);
        if (typeof Passive !== "undefined") {
            Passive.registerStatMods(entityId);
        }
        engine.recalculate(entityId);
    },

    reject: function(event, message) {
        event.set("success", false);
        event.set("message", message);
    }
};

function loadSpecTree(classId) { return Specialization.loadSpecTree(classId); }
function specNode(classId, id) { return Specialization.specNode(classId, id); }
function pathNodes(entity) { return Specialization.pathNodes(entity); }
function effectiveGrowth(entity) { return Specialization.effectiveGrowth(entity); }
function effectiveSpecNode(entity) { return Specialization.effectiveSpecNode(entity); }
function effectiveClassLabel(entity) { return Specialization.effectiveClassLabel(entity); }
function effectiveDescription(entity) { return Specialization.effectiveDescription(entity); }
function effectivePortrait(entity) { return Specialization.effectivePortrait(entity); }
function registerSpecModifier(entityId) { Specialization.registerSpecModifier(entityId); }
function applySpec(entityId) { Specialization.applySpec(entityId); }

engine.on("action.choose_specialization", 100, function(event) {
    var playerId = event.get("playerId");
    var player = store.get(playerId);
    if (player === null || !player.hasComponent("Character")) {
        Specialization.reject(event, "找不到角色");
        return;
    }

    Specialization.ensureComponent(player);
    var classId = player.getComponent("Character").getString("classId");
    var specId = String(event.get("specId"));
    var node = Specialization.specNode(classId, specId);
    if (node === null) {
        Specialization.reject(event, "未知专精");
        return;
    }

    var path = player.getComponent("Specialization").get("path");
    var pathSize = Specialization._pathSize(path);
    var expectedTier = pathSize + 1;
    if (parseInt(node.tier) !== expectedTier) {
        Specialization.reject(event, "该专精层级当前不可选择");
        return;
    }

    var selectedTier = false;
    for (var i = 0; i < pathSize; i++) {
        var existing = Specialization.specNode(classId, Specialization._pathGet(path, i));
        if (existing !== null && parseInt(existing.tier) === parseInt(node.tier)) selectedTier = true;
    }
    if (selectedTier) {
        Specialization.reject(event, "该层专精已经选择过");
        return;
    }

    var parent = node.parent === undefined ? null : node.parent;
    var last = pathSize > 0 ? String(Specialization._pathGet(path, pathSize - 1)) : null;
    var parentOk = (pathSize === 0 && parent === null) || (pathSize > 0 && String(parent) === last);
    if (!parentOk) {
        Specialization.reject(event, "必须沿已选择的专精路线继续");
        return;
    }

    var exp = player.getComponent("Experience");
    var level = exp !== null ? exp.getInt("level") : 1;
    if (parseInt(node.requires_level) > level) {
        Specialization.reject(event, "等级不足，L" + node.requires_level + " 解锁");
        return;
    }

    path.add(specId);
    Specialization.applySpec(playerId);
    if (typeof persistence !== "undefined" && persistence !== null) persistence.save(player);
});
