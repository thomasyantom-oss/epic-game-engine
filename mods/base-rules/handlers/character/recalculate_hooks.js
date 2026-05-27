engine.on("entity.before_recalculate", 100, function(event) {
    var entity = event.get("entity");
    var scratch = event.get("scratch");
    var health = entity.getComponent("Health");
    if (health !== null) scratch.put("hp", health.getInt("hp"));
    var mana = entity.getComponent("Mana");
    if (mana !== null) scratch.put("mp", mana.getInt("mp"));
});

engine.on("entity.after_recalculate", 100, function(event) {
    var entity = event.get("entity");
    var scratch = event.get("scratch");
    var health = entity.getComponent("Health");
    if (health !== null && scratch.containsKey("hp")) {
        health.set("hp", Math.min(scratch.get("hp"), health.getInt("maxHp")));
    }
    var mana = entity.getComponent("Mana");
    if (mana !== null && scratch.containsKey("mp")) {
        mana.set("mp", Math.min(scratch.get("mp"), mana.getInt("maxMp")));
    }
});

engine.on("entity.loaded", 100, function(event) {
    var entity = event.get("entity");
    var charComp = entity.getComponent("Character");
    if (charComp === null) return;

    var classId = charComp.getString("classId");
    var classSchema = schemas.get(classId);

    var statCompTypes = engine.newList();
    var baseKeys = schemas.get("character").baseComponents().keySet().iterator();
    while (baseKeys.hasNext()) {
        statCompTypes.add(baseKeys.next());
    }
    engine.setBaseSelective(entity.getId(), statCompTypes);

    var capturedClassSchema = classSchema;
    engine.addModifier(entity.getId(), {
        typeId: "class",
        id: "class_" + classId,
        label: classSchema !== null ? classSchema.label() : classId,
        apply: function(ent) {
            if (capturedClassSchema === null || capturedClassSchema.modifiers() === null) return;
            var mods = capturedClassSchema.modifiers();
            for (var i = 0; i < mods.size(); i++) {
                var mod = mods.get(i);
                var dotIdx = mod.field().indexOf(".");
                var compName = mod.field().substring(0, dotIdx);
                var fieldName = mod.field().substring(dotIdx + 1);
                var valueStr = mod.value();
                var comp = ent.getComponent(compName);
                if (comp !== null) {
                    var current = comp.getInt(fieldName);
                    if (valueStr.startsWith("+")) {
                        comp.set(fieldName, current + parseInt(valueStr.substring(1)));
                    } else {
                        comp.set(fieldName, parseInt(valueStr));
                    }
                }
            }
        }
    });

    var slots = entity.getComponent("EquipmentSlots");
    if (slots !== null) {
        var slotNames = ["weapon", "armor", "accessory"];
        for (var s = 0; s < slotNames.length; s++) {
            var itemId = slots.get(slotNames[s]);
            if (itemId !== null && typeof registerEquipmentModifier !== 'undefined') {
                registerEquipmentModifier(entity.getId(), itemId);
            }
        }
    }

    var exp = entity.getComponent("Experience");
    if (exp !== null && exp.getInt("level") > 1 && typeof registerLevelGrowthModifier !== 'undefined') {
        registerLevelGrowthModifier(entity.getId(), exp.getInt("level"));
    }
});
