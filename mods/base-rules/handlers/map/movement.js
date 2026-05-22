engine.on("map.move", 100, function(event) {
    var entityId = event.get("entityId");
    var direction = event.get("direction");
    var entity = store.get(entityId);
    var pos = entity.getComponent("Position");
    var mapId = pos.getString("map");
    var x = pos.getInt("x");
    var y = pos.getInt("y");

    var dx = 0, dy = 0;
    if (direction === "NORTH") dy = -1;
    else if (direction === "SOUTH") dy = 1;
    else if (direction === "WEST") dx = -1;
    else if (direction === "EAST") dx = 1;

    var newX = x + dx;
    var newY = y + dy;

    var map = store.get(mapId);
    if (map === null) { event.set("success", false); return; }

    var mapData = map.getComponent("MapData");
    var width = mapData.getInt("width");
    var height = mapData.getInt("height");

    if (newX < 0 || newX >= width || newY < 0 || newY >= height) {
        event.set("success", false);
        return;
    }

    pos.set("x", newX);
    pos.set("y", newY);
    event.set("success", true);
    event.set("newX", newX);
    event.set("newY", newY);

    var enterEvent = engine.newEvent("map.enter_area");
    enterEvent.set("entityId", entityId);
    enterEvent.set("x", newX);
    enterEvent.set("y", newY);
    enterEvent.set("mapId", mapId);
    engine.fire("map.enter_area", enterEvent);
});
