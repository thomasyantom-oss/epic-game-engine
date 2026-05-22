engine.on("map.pathfind", 100, function(event) {
    var entityId = event.get("entityId");
    var targetX = event.get("targetX");
    var targetY = event.get("targetY");
    var entity = store.get(entityId);
    var pos = entity.getComponent("Position");
    var mapId = pos.getString("map");
    var map = store.get(mapId);
    var mapData = map.getComponent("MapData");
    var width = mapData.getInt("width");
    var height = mapData.getInt("height");

    var startX = pos.getInt("x");
    var startY = pos.getInt("y");

    var open = [{x: startX, y: startY, g: 0, f: 0, parent: null}];
    var closed = {};
    var key = function(x, y) { return x + "," + y; };

    while (open.length > 0) {
        open.sort(function(a, b) { return a.f - b.f; });
        var current = open.shift();

        if (current.x === targetX && current.y === targetY) {
            var path = [];
            var node = current;
            while (node.parent !== null) {
                path.unshift({x: node.x, y: node.y});
                node = node.parent;
            }
            event.set("path", path);
            event.set("found", true);
            return;
        }

        closed[key(current.x, current.y)] = true;

        var neighbors = [
            {x: current.x, y: current.y - 1},
            {x: current.x, y: current.y + 1},
            {x: current.x - 1, y: current.y},
            {x: current.x + 1, y: current.y}
        ];

        for (var i = 0; i < neighbors.length; i++) {
            var n = neighbors[i];
            if (n.x < 0 || n.x >= width || n.y < 0 || n.y >= height) continue;
            if (closed[key(n.x, n.y)]) continue;

            var g = current.g + 1;
            var h = Math.abs(n.x - targetX) + Math.abs(n.y - targetY);
            var f = g + h;

            var found = false;
            for (var j = 0; j < open.length; j++) {
                if (open[j].x === n.x && open[j].y === n.y) {
                    if (open[j].g <= g) found = true;
                    break;
                }
            }
            if (found) continue;

            open.push({x: n.x, y: n.y, g: g, f: f, parent: current});
        }
    }

    event.set("found", false);
    event.set("path", []);
});
