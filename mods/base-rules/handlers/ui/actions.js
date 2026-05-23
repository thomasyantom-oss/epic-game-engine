engine.on("ui.render_actions", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var actions = event.get("actions");

    // Movement actions if player has Position
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
                        actions.add(engine.newActionOption("poi_interact", poi.get("label"), poiParams));
                    }
                }
            }
        }
    }

    // Logout always available
    actions.add(engine.newActionOption("logout", "退出角色", engine.newMap()));
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

// Handle click-to-move (pathfind + execute path)
engine.on("action.map_moveto", 100, function(event) {
    var playerId = event.get("playerId");
    var targetX = event.get("targetX");
    var targetY = event.get("targetY");
    var entityId = event.get("entityId");
    if (!entityId) entityId = playerId;

    // Pathfind
    var pathEvent = engine.newEvent("map.pathfind");
    pathEvent.set("entityId", entityId);
    pathEvent.set("targetX", targetX);
    pathEvent.set("targetY", targetY);
    engine.fire("map.pathfind", pathEvent);

    var found = pathEvent.get("found");
    if (found) {
        // Execute path - move to final position directly
        var entity = store.get(entityId);
        var pos = entity.getComponent("Position");
        pos.set("x", targetX);
        pos.set("y", targetY);

        // Fire enter_area event for destination
        var enterEvent = engine.newEvent("map.enter_area");
        enterEvent.set("entityId", entityId);
        enterEvent.set("x", targetX);
        enterEvent.set("y", targetY);
        enterEvent.set("mapId", pos.getString("map"));
        engine.fire("map.enter_area", enterEvent);
    }
});
