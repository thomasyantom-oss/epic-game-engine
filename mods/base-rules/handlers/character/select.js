// 角色列表：列出当前会话的所有角色
engine.on("session.list_characters", 100, function(event) {
    var token = event.get("sessionToken");
    var characters = event.get("characters");
    var entities = persistence.findByTag("session:" + token);

    for (var i = 0; i < entities.size(); i++) {
        var entity = entities.get(i);
        if (!entity.hasComponent("Character")) continue;
        var charComp = entity.getComponent("Character");
        var name = charComp.getString("name");
        var level = charComp.has("level") ? charComp.getInt("level") : 1;
        var classId = charComp.has("classId") ? charComp.getString("classId") : "";
        var classLabel = charComp.has("classLabel") ? charComp.getString("classLabel") : "";
        characters.add(engine.newCharacterInfo(entity.getId(), name, level, classId, classLabel));
    }
});

// 创建角色：根据 schema 构建表单
engine.on("action.create_character", 100, function(event) {
    var charSchema = schemas.get("character");
    var fields = engine.newList();

    // 从 schema 添加字段
    var schemaFields = charSchema.fields();
    for (var i = 0; i < schemaFields.size(); i++) {
        var f = schemaFields.get(i);
        var label = f.name();
        if (label === "name") label = "名字";
        fields.add(engine.newFormField(f.name(), label, f.fieldType(), f.required(), null));
    }

    // 添加必需的子选择（如职业）
    var requiredSubs = charSchema.requiredSubs();
    for (var i = 0; i < requiredSubs.size(); i++) {
        var category = requiredSubs.get(i);
        var subSchemas = schemas.getByCategory(category);
        var options = engine.newList();
        for (var j = 0; j < subSchemas.size(); j++) {
            var sub = subSchemas.get(j);
            options.add(engine.newFormOption(sub.id(), sub.label(), sub.description()));
        }
        var fieldLabel = category;
        if (category === "class") fieldLabel = "职业";
        fields.add(engine.newFormField(category, fieldLabel, "select", true, options));
    }

    event.set("form", engine.newFormData(fields));
});

// 确认创建角色
engine.on("action.confirm_character", 100, function(event) {
    var token = event.get("sessionToken");
    var name = event.get("name");
    var classId = event.get("class");

    var charId = event.has("characterId") ? event.get("characterId") : "char_" + engine.now();

    var charSchema = schemas.get("character");
    var classSchema = schemas.get(classId);

    var entity = engine.createEntity(charId);

    // 1. 添加 base stat 组件
    var baseComps = charSchema.baseComponents();
    var compTypes = baseComps.keySet().iterator();
    while (compTypes.hasNext()) {
        var compType = compTypes.next();
        var compData = baseComps.get(compType);
        var comp = engine.newComponent(compType);
        var keys = compData.keySet().iterator();
        while (keys.hasNext()) {
            var key = keys.next();
            comp.set(key, compData.get(key));
        }
        entity.addComponent(comp);
    }

    // 2. 添加标签和到 store（setBase 需要实体在 store 中）
    entity.addTag("persistent");
    entity.addTag("player");
    entity.addTag("session:" + token);
    store.add(entity);

    // 主属性的武器属性(决定物理强度吃哪条基础属性)——必须在 setBase 前写入,
    // 才能进 base 快照并随持久化保存。
    var primaryComp = entity.getComponent("PrimaryStats");
    if (primaryComp !== null && classSchema !== null && classSchema.raw().get("weapon_attr") !== null) {
        primaryComp.set("weaponAttr", classSchema.raw().get("weapon_attr"));
    }

    // 3. Snapshot base state（仅包含 stat 组件）
    engine.setBase(charId);

    // 4. 添加非 stat 组件
    var charComp = engine.newComponent("Character");
    charComp.set("name", name);
    charComp.set("level", 1);
    charComp.set("xp", 0);
    charComp.set("classId", classId);
    charComp.set("classLabel", classSchema !== null ? classSchema.label() : classId);
    entity.addComponent(charComp);

    var nameComp = engine.newComponent("Name");
    nameComp.set("value", name);
    entity.addComponent(nameComp);

    var skills = engine.newComponent("Skillbook");
    var known = engine.newList();
    if (classSchema !== null && classSchema.raw().get("starting_skills") !== null) {
        var startSkills = classSchema.raw().get("starting_skills");
        for (var sk = 0; sk < startSkills.size(); sk++) {
            var inst = engine.newMap();
            inst.put("base", String(startSkills.get(sk)));
            inst.put("node", null);
            inst.put("level", 1);
            inst.put("equipped", true);
            known.add(inst);
        }
    }
    if (classSchema !== null && classSchema.raw().get("starting_passives") !== null) {
        var startPassives = classSchema.raw().get("starting_passives");
        for (var sp = 0; sp < startPassives.size(); sp++) {
            var pinst = engine.newMap();
            pinst.put("base", String(startPassives.get(sp)));
            pinst.put("node", null);
            pinst.put("level", 1);
            known.add(pinst);
        }
    }
    var skillSlots = 6;
    if (classSchema !== null && classSchema.raw().get("skill_slots") !== null) {
        skillSlots = parseInt(String(classSchema.raw().get("skill_slots")));
    }
    skills.set("slots", skillSlots);
    skills.set("known", known);
    entity.addComponent(skills);

    var slotsComp = engine.newComponent("EquipmentSlots");
    slotsComp.set("weapon", null);
    slotsComp.set("armor", null);
    slotsComp.set("accessory", null);
    entity.addComponent(slotsComp);

    var invComp = engine.newComponent("Inventory");
    var startingItems = engine.newList();
    // 给每个职业发全部 5 把武器，用户逐一测试不同武器在不同职业手上的表现
    var weapons = ["greatsword", "dagger", "staff", "gauntlet", "totem"];
    for (var wi = 0; wi < weapons.length; wi++) startingItems.add(weapons[wi]);
    startingItems.add("leather_armor");
    startingItems.add("speed_ring");
    invComp.set("items", startingItems);
    entity.addComponent(invComp);

    var expComp = engine.newComponent("Experience");
    expComp.set("xp", 0);
    expComp.set("level", 1);
    expComp.set("pendingPoints", 0);   // Feature #2:成长全自动(按职业 growth 模板),手动分配搁置(spec §5);保留字段供 UI/未来专精复用
    entity.addComponent(expComp);

    // 5. 注册职业 Modifier（exclusive，替换旧职业）
    var capturedClassSchema = classSchema;
    var capturedClassId = classId;
    engine.addModifier(charId, {
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

    // 注册派生 modifier(priority 300,在 class 之后跑)→ 自动 recalculate 算出二级属性 + maxHp
    if (typeof registerDerivedModifier !== 'undefined') {
        registerDerivedModifier(charId);
    }
    if (typeof applySkillLevelCurve !== 'undefined') {
        applySkillLevelCurve(charId);
    }
    if (typeof Passive !== 'undefined') {
        Passive.registerStatMods(charId);
        engine.recalculate(charId);
    }
    // 新建角色满血(base hp 仅 30,maxHp 由体质派生)
    var hpComp = entity.getComponent("Health");
    if (hpComp !== null) hpComp.set("hp", hpComp.getInt("maxHp"));

    persistence.save(entity);
    sessions.setActiveCharacter(token, charId);
});

// 选择已有角色
engine.on("action.select_character", 100, function(event) {
    var token = event.get("sessionToken");
    var characterId = event.get("characterId");

    var entity = store.get(characterId);
    if (entity === null) {
        entity = persistence.load(characterId);
        if (entity !== null) {
            store.add(entity);
        }
    }

    if (entity !== null && entity.hasTag("session:" + token)) {
        sessions.setActiveCharacter(token, characterId);
    }
});

// 删除角色
engine.on("action.delete_character", 100, function(event) {
    var token = event.get("sessionToken");
    var characterId = event.get("characterId");

    var entity = store.get(characterId);
    if (entity === null) {
        entity = persistence.load(characterId);
    }
    if (entity === null || !entity.hasTag("session:" + token)) return;

    persistence.delete(characterId);
    sessions.clearActiveCharacter(token);
});

// 登出
engine.on("action.logout", 100, function(event) {
    var token = event.get("sessionToken");
    sessions.clearActiveCharacter(token);
});
