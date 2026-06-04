function _skillbookEntity(event) {
    var id = event.get("entityId");
    if (id === null) id = event.get("playerId");
    return id !== null ? store.get(id) : null;
}

function _skillbookInCombat(entity) {
    var tags = entity.getTags().toArray();
    for (var i = 0; i < tags.length; i++) {
        if (tags[i].toString().indexOf("combat:") === 0) return true;
    }
    return false;
}

function _setSkillbookEquipped(event, wantEquipped) {
    var entity = _skillbookEntity(event);
    if (entity === null || !entity.hasComponent("Skillbook")) return;
    if (_skillbookInCombat(entity)) return;

    var skills = entity.getComponent("Skillbook");
    var known = skills.get("known");
    if (known === null) return;

    var slots = skills.has("slots") ? skills.getInt("slots") : 6;
    var base = event.get("base");
    var target = null;
    var equippedCount = 0;

    for (var i = 0; i < known.size(); i++) {
        var skill = known.get(i);
        if (skill.get("equipped") === true) equippedCount++;
        if (String(skill.get("base")) === String(base)) target = skill;
    }

    if (target === null) return;

    if (wantEquipped) {
        if (target.get("equipped") === true) return;
        if (equippedCount >= slots) return;
        target.put("equipped", true);
    } else {
        target.put("equipped", false);
    }

    if (typeof persistence !== "undefined" && persistence !== null) {
        persistence.save(entity);
    }
}

engine.on("action.skillbook_equip", 100, function(event) {
    _setSkillbookEquipped(event, true);
});

engine.on("action.skillbook_unequip", 100, function(event) {
    _setSkillbookEquipped(event, false);
});
