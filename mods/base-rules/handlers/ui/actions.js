// JS 坐标数组 [[a,b],...] → Java List<List>，供快照序列化(JS 数组直接塞 Map 不可序列化)。
// swap=true:引擎 pattern 是 [rowIdxDelta,slotDelta]=[gridCol差,gridRow差]，
// 前端 aoeOffsets 要 [gridRow差,gridCol差]，故交换每对。
function pairsToJavaList(arr, swap) {
    var out = engine.newList();
    for (var i = 0; i < arr.length; i++) {
        var cell = engine.newList();
        cell.add(swap ? arr[i][1] : arr[i][0]);
        cell.add(swap ? arr[i][0] : arr[i][1]);
        out.add(cell);
    }
    return out;
}

function emitCombatCommand(entityId, actions, skillId, categoryOverride, node) {
    var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
    if (skillDef === null) return;
    if (skillDef.get("kind") === "passive") return;

    var name = skillDef.get("name");
    var description = skillDef.get("description");
    // 进化(node)覆盖显示名/描述，与技能书面板(skillbook.js)一致
    if (node !== null && node !== undefined && typeof Talent !== "undefined" && Talent !== null && typeof Talent.evolutionDisplay === "function") {
        var disp = Talent.evolutionDisplay(node);
        if (disp !== null) {
            if (disp.name !== null) name = disp.name;
            if (disp.description !== null) description = disp.description;
        }
    }
    var targeting = skillDef.get("targeting");
    var steps = targeting !== null ? targeting.get("steps") : null;
    var needsTarget = steps !== null && steps.size() > 0;

    var canUseEvent = engine.newEvent("skill.can_use");
    canUseEvent.set("entityId", entityId);
    canUseEvent.set("skillId", skillId);
    canUseEvent.set("usable", true);
    engine.fire("skill.can_use", canUseEvent);

    var usable = canUseEvent.get("usable");

    var params = engine.newMap();
    params.put("command", skillId);
    params.put("category", categoryOverride);

    if (needsTarget) {
        var prompt = steps.get(0).get("prompt");
        if (prompt !== null) params.put("prompt", prompt);
    }

    var mpCost = skillDef.get("mp_cost");
    if (mpCost !== null) params.put("mpCost", mpCost);

    var aoeOffsets = targeting !== null ? targeting.get("aoe_offsets") : null;
    // resolveSpec 会合并等级、天赋、被动、专精 patch；范围预览也跟随最终 spec。
    // 优先 aoe_offsets,否则取多格 pattern。
    var resolvedSpec = null;
    if (typeof Skill !== "undefined" && Skill !== null && typeof Skill.resolveSpec === "function") {
        var caster = store.get(entityId);
        if (caster !== null) {
            resolvedSpec = Skill.resolveSpec({ caster: caster }, skillId, Skill._toJs(skillDef));
            var rt = resolvedSpec !== null && resolvedSpec !== undefined ? resolvedSpec.targeting : null;
            var off = null;
            var swap = false;
            if (rt !== null && rt !== undefined) {
                if (rt.aoe_offsets !== undefined && rt.aoe_offsets !== null) { off = rt.aoe_offsets; swap = false; }
                else if (rt.pattern !== undefined && rt.pattern !== null && rt.pattern.length > 1) { off = rt.pattern; swap = true; }
            }
            if (off !== null) aoeOffsets = pairsToJavaList(off, swap);
        }
    }
    if (description !== null) params.put("description", description);
    if (typeof Skill !== "undefined" && Skill !== null && typeof Skill.buildTooltip === "function") {
        var tooltip = Skill.buildTooltip(entityId, description, resolvedSpec);
        if (tooltip !== null) params.put("tooltip", tooltip);
    }

    if (aoeOffsets !== null) {
        params.put("aoeOffsets", aoeOffsets);
    }
    var allowEmpty = targeting !== null && targeting.get("allow_empty") !== null && targeting.get("allow_empty");
    if (allowEmpty) {
        params.put("allowEmpty", true);
    }
    var style = needsTarget ? "requires_target" : "instant";
    if (!usable) {
        actions.add(engine.newActionOptionStyled("combat_command", name, params, "text", "disabled"));
    } else {
        actions.add(engine.newActionOptionStyled("combat_command", name, params, null, style));
    }
}

engine.on("ui.render_actions", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var actions = event.get("actions");

    // Check if in combat
    var inCombat = false;
    var tags = entity.getTags().toArray();
    for (var i = 0; i < tags.length; i++) {
        if (tags[i].toString().indexOf("combat:") === 0) {
            inCombat = true;
            break;
        }
    }

    if (inCombat) {
        var universalIds = ["basic_attack", "defend", "flee"];
        for (var u = 0; u < universalIds.length; u++) {
            emitCombatCommand(entityId, actions, universalIds[u], "action");
        }

        var skillsComp = entity.hasComponent("Skillbook") ? entity.getComponent("Skillbook") : null;
        var known = skillsComp !== null ? skillsComp.get("known") : null;
        if (known !== null) {
            for (var j = 0; j < known.size(); j++) {
                var sk = known.get(j);
                if (sk.get("equipped") !== true) continue;
                var base = String(sk.get("base"));
                var activeDef = engine.loadYaml("skills/" + base + ".yaml");
                if (activeDef === null || activeDef.get("kind") === "passive") continue;
                var skNode = sk.get("node") !== null ? String(sk.get("node")) : null;
                emitCombatCommand(entityId, actions, base, "skill", skNode);
            }
        }
    } else {
        // Map movement (hidden from panel, used by keyboard)
        if (entity.hasComponent("Position")) {
            var directions = [
                ["NORTH", "向北"],
                ["SOUTH", "向南"],
                ["WEST", "向西"],
                ["EAST", "向东"]
            ];
            for (var i = 0; i < directions.length; i++) {
                var params = engine.newMap();
                params.put("direction", directions[i][0]);
                params.put("entityId", entityId);
                actions.add(engine.newActionOption("map_move", directions[i][1], params));
            }

            // POI actions if player is on a POI tile
            var pos = entity.getComponent("Position");
            var mapId = pos.getString("map");
            var map = store.get(mapId);
            if (map !== null && map.hasComponent("MapData")) {
                var mapData = map.getComponent("MapData");
                var pois = mapData.get("pois");
                if (pois !== null) {
                    var px = pos.getInt("x");
                    var py = pos.getInt("y");
                    for (var j = 0; j < pois.size(); j++) {
                        var poi = pois.get(j);
                        var poiX = poi.get("x");
                        var poiY = poi.get("y");
                        if (poiX == px && poiY == py) {
                            var poiParams = engine.newMap();
                            poiParams.put("poiId", poi.get("id"));
                            poiParams.put("poiType", poi.get("type"));
                            poiParams.put("target", poi.get("target"));
                            actions.add(engine.newActionOptionStyled("poi_interact", poi.get("label"), poiParams, null, "instant"));
                        }
                    }
                }
            }
        }

        // Logout
        actions.add(engine.newActionOptionStyled("logout", "退出角色", engine.newMap(), null, "instant"));
    }
});

// Handle map_move action — delegate to map.move event
engine.on("action.map_move", 100, function(event) {
    var playerId = event.get("playerId");
    var direction = event.get("direction");
    var entityId = event.get("entityId");
    if (!entityId) entityId = playerId;

    var moveEvent = engine.newEvent("map.move");
    moveEvent.set("entityId", entityId);
    moveEvent.set("direction", direction);
    engine.fire("map.move", moveEvent);
});

// Handle click-to-move: compute path and execute one step
engine.on("action.map_moveto", 100, function(event) {
    var playerId = event.get("playerId");
    var targetX = event.get("targetX");
    var targetY = event.get("targetY");
    var entityId = event.get("entityId");
    if (!entityId) entityId = playerId;

    var pathEvent = engine.newEvent("map.pathfind");
    pathEvent.set("entityId", entityId);
    pathEvent.set("targetX", targetX);
    pathEvent.set("targetY", targetY);
    engine.fire("map.pathfind", pathEvent);

    var found = pathEvent.get("found");
    if (found) {
        var path = pathEvent.get("path");
        if (path.length > 0) {
            var entity = store.get(entityId);
            var pos = entity.getComponent("Position");
            var cx = pos.getInt("x");
            var cy = pos.getInt("y");
            var step = path[0];
            var sx = step.x !== undefined ? step.x : step.get("x");
            var sy = step.y !== undefined ? step.y : step.get("y");
            var dir = "";
            if (sx > cx) dir = "EAST";
            else if (sx < cx) dir = "WEST";
            else if (sy > cy) dir = "SOUTH";
            else if (sy < cy) dir = "NORTH";

            var moveEvent = engine.newEvent("map.move");
            moveEvent.set("entityId", entityId);
            moveEvent.set("direction", dir);
            engine.fire("map.move", moveEvent);
        }
        event.set("pathRemaining", path.length - 1);
    } else {
        event.set("pathRemaining", 0);
    }
});

// Handle POI interaction
engine.on("action.poi_interact", 100, function(event) {
    var playerId = event.get("playerId");
    var poiType = event.get("poiType");
    var target = event.get("target");

    if (poiType === "combat") {
        var combatEvent = engine.newEvent("combat.start_encounter");
        combatEvent.set("playerId", playerId);
        combatEvent.set("encounterId", target);
        engine.fire("combat.start_encounter", combatEvent);
    }
});
