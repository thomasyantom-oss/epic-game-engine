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
