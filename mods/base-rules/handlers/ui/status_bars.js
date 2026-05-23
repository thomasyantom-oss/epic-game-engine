engine.on("ui.render_status", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var bars = event.get("bars");

    // Character info as text bars (level, class)
    if (entity.hasComponent("Character")) {
        var charComp = entity.getComponent("Character");
        var name = charComp.getString("name");
        var level = charComp.has("level") ? charComp.getInt("level") : 1;
        var classLabel = charComp.has("classLabel") ? charComp.getString("classLabel") : "";
        bars.add(engine.newStatusBar("name", name, level, level, "#ffd93d", 0));
        bars.add(engine.newStatusBar("class", classLabel, level, 99, "#b388ff", 0));
    }

    if (entity.hasComponent("Health")) {
        var health = entity.getComponent("Health");
        bars.add(engine.newStatusBar("hp", "生命", health.getInt("hp"), health.getInt("maxHp"), "#e74c3c", 1));
    }

    if (entity.hasComponent("Mana")) {
        var mana = entity.getComponent("Mana");
        bars.add(engine.newStatusBar("mp", "法力", mana.getInt("mp"), mana.getInt("maxMp"), "#3498db", 2));
    }

    if (entity.hasComponent("CombatStats")) {
        var stats = entity.getComponent("CombatStats");
        bars.add(engine.newStatusBar("atk", "攻击", stats.getInt("attack"), stats.getInt("attack"), "#ff7043", 3));
        bars.add(engine.newStatusBar("def", "防御", stats.getInt("defense"), stats.getInt("defense"), "#66bb6a", 4));
        bars.add(engine.newStatusBar("spd", "速度", stats.getInt("speed"), stats.getInt("speed"), "#29b6f6", 5));
    }
});
