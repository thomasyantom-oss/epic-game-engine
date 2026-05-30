// 在 world.init 时加载物品数据到 EntityStore
engine.on("world.init", 80, function(event) {
    // 加载稀有度颜色映射
    var rarityData = engine.loadYaml("item_rarity.yaml");
    var rarityList = rarityData.get("item_rarities");
    var rarityColors = engine.newMap();
    for (var r = 0; r < rarityList.size(); r++) {
        var rar = rarityList.get(r);
        rarityColors.put(String(rar.get("id")), String(rar.get("color")));
    }

    // 加载物品数据
    var itemsData = engine.loadYaml("entities/items.yaml");
    var items = itemsData.get("items");
    for (var i = 0; i < items.size(); i++) {
        var itemDef = items.get(i);
        var id = itemDef.get("id");
        var item = store.get(id);
        if (item === null) {
            item = engine.createEntity(id);
            item.addTag("item");
            store.add(item);
        }
        var meta = itemDef.get("meta");
        var rarity = meta.get("rarity");
        var metaComp = engine.newComponent("ItemMeta");
        metaComp.set("name", meta.get("name"));
        metaComp.set("type", meta.get("type"));
        metaComp.set("rarity", rarity);
        var rarityKey = String(rarity);
        var rarityColor = rarityColors.get(rarityKey);
        metaComp.set("rarityColor", rarityColor !== null ? rarityColor : "#ffffff");
        item.addComponent(metaComp);

        var statsMap = itemDef.get("stats");
        var statsComp = engine.newComponent("ItemStats");
        var statsKeys = statsMap.keySet().iterator();
        while (statsKeys.hasNext()) {
            var key = statsKeys.next();
            statsComp.set(key, statsMap.get(key));
        }
        item.addComponent(statsComp);
    }
});

// 注册装备 Modifier 的辅助函数（供 entity.loaded 重用）
function registerEquipmentModifier(entityId, itemId) {
    var item = store.get(itemId);
    if (item === null || !item.hasComponent("ItemStats")) return;
    var stats = item.getComponent("ItemStats");
    var statsCopy = engine.newMap();
    var statsKeys = stats.getAll().keySet().iterator();
    while (statsKeys.hasNext()) {
        var k = statsKeys.next();
        statsCopy.put(k, stats.getInt(k));
    }
    engine.addModifier(entityId, {
        typeId: "equipment",
        id: "equip_" + itemId,
        label: item.getComponent("ItemMeta").getString("name"),
        apply: function(entity) {
            var combatStats = entity.getComponent("CombatStats");
            if (combatStats === null) return;
            var keys = statsCopy.keySet().iterator();
            while (keys.hasNext()) {
                var k = keys.next();
                if (combatStats.has(k)) {
                    combatStats.set(k, combatStats.getInt(k) + statsCopy.get(k));
                }
            }
        }
    });
}

// 装备动作
engine.on("action.equip", 100, function(event) {
    var playerId = event.get("playerId");
    var itemId = event.get("itemId");
    var player = store.get(playerId);
    if (player === null) return;

    var item = store.get(itemId);
    if (item === null || !item.hasComponent("ItemMeta")) return;
    var slotName = item.getComponent("ItemMeta").getString("type");

    var slots = player.getComponent("EquipmentSlots");
    if (slots === null) return;

    var inv = player.getComponent("Inventory");

    // 卸下已装备的旧物品，放回背包
    var oldItemId = slots.get(slotName);
    if (oldItemId !== null) {
        engine.removeModifier(playerId, "equip_" + oldItemId);
        if (inv !== null) {
            inv.get("items").add(String(oldItemId));
        }
    }

    // 从背包移除新装备的物品
    if (inv !== null) {
        var items = inv.get("items");
        for (var k = 0; k < items.size(); k++) {
            if (String(items.get(k)) === String(itemId)) {
                items.remove(k);
                break;
            }
        }
    }

    // 更新装备槽
    slots.set(slotName, itemId);

    // 注册新 Modifier（内部触发 recalculate）
    registerEquipmentModifier(playerId, itemId);

    persistence.save(player);
});

// 卸装动作
engine.on("action.unequip", 100, function(event) {
    var playerId = event.get("playerId");
    var slotName = event.get("slot");
    var player = store.get(playerId);
    if (player === null) return;

    var slots = player.getComponent("EquipmentSlots");
    if (slots === null) return;

    var itemId = slots.get(slotName);
    if (itemId === null) return;

    engine.removeModifier(playerId, "equip_" + itemId);
    slots.set(slotName, null);

    // 放回背包
    var inv = player.getComponent("Inventory");
    if (inv !== null) {
        inv.get("items").add(String(itemId));
    }

    persistence.save(player);
});

// 整理背包：按类型（武器/护甲/饰品）→ 稀有度（传说→普通）排序
engine.on("action.sort_inventory", 100, function(event) {
    var playerId = event.get("playerId");
    var player = store.get(playerId);
    if (player === null) return;

    var inv = player.getComponent("Inventory");
    if (inv === null) return;

    var items = inv.get("items");
    var TYPE_ORDER = {"weapon": 0, "armor": 1, "accessory": 2};
    var RARITY_ORDER = {"legendary": 0, "epic": 1, "rare": 2, "uncommon": 3, "common": 4};

    var arr = [];
    for (var i = 0; i < items.size(); i++) {
        arr.push(String(items.get(i)));
    }

    arr.sort(function(a, b) {
        var itemA = store.get(a);
        var itemB = store.get(b);
        var typeA = itemA !== null && itemA.hasComponent("ItemMeta") ? String(itemA.getComponent("ItemMeta").getString("type")) : "";
        var typeB = itemB !== null && itemB.hasComponent("ItemMeta") ? String(itemB.getComponent("ItemMeta").getString("type")) : "";
        var rarityA = itemA !== null && itemA.hasComponent("ItemMeta") ? String(itemA.getComponent("ItemMeta").getString("rarity")) : "";
        var rarityB = itemB !== null && itemB.hasComponent("ItemMeta") ? String(itemB.getComponent("ItemMeta").getString("rarity")) : "";
        var typeOrdA = TYPE_ORDER[typeA] !== undefined ? TYPE_ORDER[typeA] : 99;
        var typeOrdB = TYPE_ORDER[typeB] !== undefined ? TYPE_ORDER[typeB] : 99;
        if (typeOrdA !== typeOrdB) return typeOrdA - typeOrdB;
        var rarOrdA = RARITY_ORDER[rarityA] !== undefined ? RARITY_ORDER[rarityA] : 99;
        var rarOrdB = RARITY_ORDER[rarityB] !== undefined ? RARITY_ORDER[rarityB] : 99;
        return rarOrdA - rarOrdB;
    });

    items.clear();
    for (var j = 0; j < arr.length; j++) {
        items.add(arr[j]);
    }

    persistence.save(player);
});
