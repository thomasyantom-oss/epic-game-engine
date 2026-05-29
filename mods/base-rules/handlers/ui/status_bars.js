engine.on("ui.render_status", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var bars = event.get("bars");

    // Read semantic colors from _config entity's Colors component (populated by bootstrap)
    var configEntity = store.get("_config");
    var colorsComp = configEntity !== null ? configEntity.getComponent("Colors") : null;
    var textColor   = (colorsComp !== null && colorsComp.has("text"))   ? colorsComp.getString("text")   : "#c8d0dc";
    var playerColor = (colorsComp !== null && colorsComp.has("player")) ? colorsComp.getString("player") : "#66cc55";
    var hpColor     = (colorsComp !== null && colorsComp.has("hp"))     ? colorsComp.getString("hp")     : "#e84848";
    var mpColor     = (colorsComp !== null && colorsComp.has("mp"))     ? colorsComp.getString("mp")     : "#2eb8cc";

    if (entity.hasComponent("Character")) {
        var charComp = entity.getComponent("Character");
        var name = charComp.getString("name");
        var level = charComp.has("level") ? charComp.getInt("level") : 1;
        var classLabel = charComp.has("classLabel") ? charComp.getString("classLabel") : "";
        bars.add(engine.newStatusBar("name", name, level, level, playerColor, 0));
        bars.add(engine.newStatusBar("class", classLabel, level, 99, textColor, 0));
    }

    if (entity.hasComponent("Health")) {
        var health = entity.getComponent("Health");
        bars.add(engine.newStatusBar("hp", "生命", health.getInt("hp"), health.getInt("maxHp"), hpColor, 1));
    }

    if (entity.hasComponent("Mana")) {
        var mana = entity.getComponent("Mana");
        bars.add(engine.newStatusBar("mp", "法力", mana.getInt("mp"), mana.getInt("maxMp"), mpColor, 2));
    }

    if (entity.hasComponent("CombatStats")) {
        var stats = entity.getComponent("CombatStats");
        bars.add(engine.newStatusBar("atk", "攻击", stats.getInt("attack"), stats.getInt("attack"), textColor, 3));
        bars.add(engine.newStatusBar("def", "防御", stats.getInt("defense"), stats.getInt("defense"), textColor, 4));
        bars.add(engine.newStatusBar("spd", "速度", stats.getInt("speed"), stats.getInt("speed"), textColor, 5));
    }
});
