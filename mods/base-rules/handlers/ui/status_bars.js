engine.on("ui.render_status", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null) return;

    var bars = event.get("bars");

    // Read colors directly from colors.yaml (hot-reloadable, no caching issues)
    var colorsYaml = engine.loadYaml("colors.yaml");
    var c = colorsYaml !== null ? colorsYaml.get("colors") : null;
    var textColor   = (c !== null && c.get("text")   !== null) ? String(c.get("text"))   : "#c8d0dc";
    var playerColor = (c !== null && c.get("player") !== null) ? String(c.get("player")) : "#66cc55";
    var hpColor     = (c !== null && c.get("hp")     !== null) ? String(c.get("hp"))     : "#e84848";
    var mpColor     = (c !== null && c.get("mp")     !== null) ? String(c.get("mp"))     : "#2eb8cc";

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
