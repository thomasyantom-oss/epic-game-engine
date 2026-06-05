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

    // 持久化存的是「派生/装备/职业 modifier 已应用后」的属性值。若直接把这些值当 base 快照,
    // 重载时 class/equipment 等「累加型」modifier 会再叠一遍 → 每次重启属性翻倍膨胀(bug #2)。
    // 正确做法:先把这些可重建的属性组件复位成 schema 干净默认值(class/level/equipment/derived
    // modifier 随后会从干净 base 重新叠出正确值),再快照。Position 是真实空间状态,绝不复位。
    var charBaseComps = schemas.get("character").baseComponents();
    var statCompTypes = engine.newList();
    var baseKeys = charBaseComps.keySet().iterator();
    while (baseKeys.hasNext()) {
        var compType = baseKeys.next();
        statCompTypes.add(compType);
        if (compType === "Position") continue;   // 空间状态:保留持久化值,不复位
        var comp = entity.getComponent(compType);
        if (comp === null) continue;
        var defaults = charBaseComps.get(compType);
        var fieldKeys = defaults.keySet().iterator();
        while (fieldKeys.hasNext()) {
            var f = fieldKeys.next();
            comp.set(f, defaults.get(f));
        }
    }
    // 武器属性由职业决定(决定物理强度吃哪条属性),schema 默认是"力量",需按职业还原。
    var primaryReset = entity.getComponent("PrimaryStats");
    if (primaryReset !== null && classSchema !== null && classSchema.raw().get("weapon_attr") !== null) {
        primaryReset.set("weaponAttr", classSchema.raw().get("weapon_attr"));
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

    if (typeof registerDerivedModifier !== 'undefined' && entity.getComponent("PrimaryStats") !== null) {
        registerDerivedModifier(entity.getId());
    }
    if (typeof applySkillLevelCurve !== 'undefined') {
        applySkillLevelCurve(entity.getId());
    }
    if (!entity.hasComponent("Specialization")) {
        var specComp = engine.newComponent("Specialization");
        specComp.set("path", engine.newList());
        entity.addComponent(specComp);
    }
    if (typeof Talent !== 'undefined' && Talent !== null) {
        Talent.ensureComponents(entity);
    }
    if (typeof applySpec !== 'undefined') {
        applySpec(entity.getId());
    } else if (typeof Passive !== 'undefined') {
        Passive.registerStatMods(entity.getId());
        engine.recalculate(entity.getId());
    }
    // 重载满血(持久化的是 base hp=30;maxHp 由体质派生)
    var loadedHp = entity.getComponent("Health");
    if (loadedHp !== null) loadedHp.set("hp", loadedHp.getInt("maxHp"));
});

// 接缝：人物等级 → 技能等级。Ch1 内容设计轮在此填 charLevel→skillLevel 曲线（每技能不同）。
function applySkillLevelCurve(entityId) {
    var entity = store.get(entityId);
    if (entity === null) return;
    if (!entity.hasComponent("Skillbook")) return;
    var charLevel = 1;
    var ch = entity.getComponent("Character");
    if (ch !== null) charLevel = ch.getInt("level");
    var known = entity.getComponent("Skillbook").get("known");
    if (known === null) return;
    for (var i = 0; i < known.size(); i++) {
        var entry = known.get(i);
        var base = String(entry.get("base"));
        var raw = (typeof Skill !== "undefined") ? Skill.loadSpecAny(base) : null;
        var spec = (raw !== null && raw !== undefined) ? Skill._toJs(raw) : null;
        var lv = (typeof Progression !== "undefined") ? Progression.skillLevelFor(charLevel, spec) : 1;
        entry.put("level", lv);
    }
}

engine.on("entity.level_up", 100, function(event) {
    var entity = event.get("entity");
    if (entity === null) return;
    applySkillLevelCurve(entity.getId());
    if (typeof Passive !== 'undefined') {
        Passive.registerStatMods(entity.getId());
        engine.recalculate(entity.getId());
    }
});
