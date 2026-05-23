engine.on("ui.render_status", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var bars = event.get("bars");

    if (entity.hasComponent("Health")) {
        var health = entity.getComponent("Health");
        bars.add(engine.newStatusBar("hp", "生命", health.getInt("hp"), health.getInt("maxHp"), "#e74c3c", 1));
    }

    if (entity.hasComponent("Mana")) {
        var mana = entity.getComponent("Mana");
        bars.add(engine.newStatusBar("mp", "法力", mana.getInt("mp"), mana.getInt("maxMp"), "#3498db", 2));
    }
});
