engine.on("world.init", 100, function(event) {
    var mapData = engine.loadYaml("entities/maps/world_map.yaml");

    var mapEntity = engine.createEntity(mapData.get("id"));
    var mapComponent = engine.newComponent("MapData");
    mapComponent.set("width", mapData.get("width"));
    mapComponent.set("height", mapData.get("height"));
    mapComponent.set("name", mapData.get("name"));
    mapEntity.addComponent(mapComponent);
    mapEntity.addTag("map");
    store.add(mapEntity);
});
