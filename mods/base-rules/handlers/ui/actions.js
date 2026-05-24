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
        var skills = entity.hasComponent("Skills") ? entity.getComponent("Skills").get("list") : null;
        if (skills !== null) {
            for (var j = 0; j < skills.size(); j++) {
                var sk = skills.get(j);
                var skillId = sk.get("id");
                var skillDef = engine.loadYaml("skills/" + skillId + ".yaml");
                if (skillDef === null) continue;

                var name = skillDef.get("name");
                var category = skillDef.get("category") || "action";
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
                params.put("category", category);

                // AOE offsets for frontend range indicator
                var aoeOffsets = targeting !== null ? targeting.get("aoe_offsets") : null;
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
