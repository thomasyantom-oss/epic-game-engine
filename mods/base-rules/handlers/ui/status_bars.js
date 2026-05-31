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

    // 五基础属性（PrimaryStats）—— 纯数值，value==max（无进度条语义）
    if (entity.hasComponent("PrimaryStats")) {
        var primary = entity.getComponent("PrimaryStats");
        var primaryDefs = [
            { id: "str", label: "力量", field: "力量" },
            { id: "agi", label: "敏捷", field: "敏捷" },
            { id: "int", label: "智力", field: "智力" },
            { id: "con", label: "体质", field: "体质" },
            { id: "wil", label: "意志", field: "意志" }
        ];
        for (var i = 0; i < primaryDefs.length; i++) {
            var pd = primaryDefs[i];
            var pv = primary.has(pd.field) ? primary.getInt(pd.field) : 0;
            bars.add(engine.newStatusBar(pd.id, pd.label, pv, pv, textColor, 10 + i));
        }
    }

    // 三强度（DerivedStats）—— 纯数值
    if (entity.hasComponent("DerivedStats")) {
        var derived = entity.getComponent("DerivedStats");
        var derivedDefs = [
            { id: "phys", label: "物理强度", field: "物理强度" },
            { id: "spell", label: "法术强度", field: "法术强度" },
            { id: "ment", label: "精神强度", field: "精神强度" }
        ];
        for (var j = 0; j < derivedDefs.length; j++) {
            var dd = derivedDefs[j];
            var dv = derived.has(dd.field) ? derived.getInt(dd.field) : 0;
            bars.add(engine.newStatusBar(dd.id, dd.label, dv, dv, textColor, 20 + j));
        }
    }

    if (entity.hasComponent("CombatStats")) {
        var stats = entity.getComponent("CombatStats");
        bars.add(engine.newStatusBar("atk", "攻击", stats.getInt("attack"), stats.getInt("attack"), textColor, 30));
        bars.add(engine.newStatusBar("def", "防御", stats.getInt("defense"), stats.getInt("defense"), textColor, 31));
        // 先攻 = 敏捷（CombatStats.speed）
        bars.add(engine.newStatusBar("spd", "先攻", stats.getInt("speed"), stats.getInt("speed"), textColor, 32));
    }
});
