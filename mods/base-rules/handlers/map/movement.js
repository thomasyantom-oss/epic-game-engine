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

    // Check terrain passability
    var terrain = mapData.get("terrain");
    var terrains = mapData.get("terrains");
    if (terrain !== null && terrains !== null) {
        var row = terrain.get(newY);
        var ch = row.charAt(newX);
        var terrainInfo = terrains.get("" + ch);

        if (terrainInfo !== null) {
            var requires = terrainInfo.get("requires");
            if (requires !== null && requires.size() > 0) {
                // Check if entity has required abilities
                var abilities = entity.hasComponent("Abilities") ? entity.getComponent("Abilities") : null;
                var canPass = true;
                for (var i = 0; i < requires.size(); i++) {
                    var req = requires.get(i);
                    if (abilities === null || !abilities.has(req.toString())) {
                        canPass = false;
                        break;
                    }
                }
                if (!canPass) {
                    event.set("success", false);
                    event.set("blocked", true);
                    event.set("reason", "需要能力: " + requires);
                    return;
                }
            }
        }
    }

    pos.set("x", newX);
    pos.set("y", newY);
    event.set("success", true);
    event.set("newX", newX);
    event.set("newY", newY);

    // 位置是可变状态:把当前 Position 同步进 base 快照(避免后续 recalc 的
    // restoreBaseState 把玩家拉回出生点),并持久化真实位置到 H2(重启不重置)。
    var posTypes = engine.newList();
    posTypes.add("Position");
    engine.setBaseSelective(entityId, posTypes);
    if (typeof persistence !== 'undefined') {
        persistence.save(entity); // save 内部会跳过非 persistent 实体
    }

    var enterEvent = engine.newEvent("map.enter_area");
    enterEvent.set("entityId", entityId);
    enterEvent.set("x", newX);
    enterEvent.set("y", newY);
    enterEvent.set("mapId", mapId);
    engine.fire("map.enter_area", enterEvent);
});
