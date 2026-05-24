engine.on("world.init", 100, function(event) {
    // Load map (idempotent — update if exists)
    var mapData = engine.loadYaml("entities/maps/world_map.yaml");
    var terrainsData = engine.loadYaml("entities/terrains.yaml");

    var mapId = mapData.get("id");
    var mapEntity = store.get(mapId);
    if (mapEntity === null) {
        mapEntity = engine.createEntity(mapId);
        mapEntity.addTag("map");
        store.add(mapEntity);
    }
    mapEntity.removeComponent("MapData");
    var mapComponent = engine.newComponent("MapData");
    mapComponent.set("width", mapData.get("width"));
    mapComponent.set("height", mapData.get("height"));
    mapComponent.set("name", mapData.get("name"));
    mapComponent.set("terrain", mapData.get("terrain"));
    mapComponent.set("terrains", terrainsData.get("terrains"));
    mapComponent.set("pois", mapData.get("pois"));
    mapEntity.addComponent(mapComponent);

    // Load colors into global config entity
    var colorsData = engine.loadYaml("colors.yaml");
    var configEntity = store.get("_config");
    if (configEntity === null) {
        configEntity = engine.createEntity("_config");
        configEntity.addComponent(engine.newComponent("Colors"));
        store.add(configEntity);
    }
    var colorsComp = configEntity.getComponent("Colors");
    var colors = colorsData.get("colors");
    var keys = colors.keySet().iterator();
    while (keys.hasNext()) {
        var key = keys.next();
        colorsComp.set(key, colors.get(key));
    }
});
