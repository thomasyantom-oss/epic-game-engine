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

    var charId = "char_" + engine.now();

    var charSchema = schemas.get("character");
    var classSchema = schemas.get(classId);

    // 创建实体，添加基础组件
    var entity = engine.createEntity(charId);

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

    // 应用职业修正值
    if (classSchema !== null && classSchema.modifiers() !== null) {
        var mods = classSchema.modifiers();
        for (var i = 0; i < mods.size(); i++) {
            var mod = mods.get(i);
            var fieldStr = mod.field();
            var dotIdx = fieldStr.indexOf(".");
            var compName = fieldStr.substring(0, dotIdx);
            var fieldName = fieldStr.substring(dotIdx + 1);
            var valueStr = mod.value();
            var comp = entity.getComponent(compName);
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

    // 添加 Character 元数据组件
    var charComp = engine.newComponent("Character");
    charComp.set("name", name);
    charComp.set("level", 1);
    charComp.set("classId", classId);
    charComp.set("classLabel", classSchema !== null ? classSchema.label() : classId);
    entity.addComponent(charComp);

    // Name 组件用于战斗等界面显示
    var nameComp = engine.newComponent("Name");
    nameComp.set("value", name);
    entity.addComponent(nameComp);

    // 标签
    entity.addTag("persistent");
    entity.addTag("player");
    entity.addTag("session:" + token);

    store.add(entity);
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
