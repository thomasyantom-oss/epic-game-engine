engine.on("ui.render_skillbook", 100, function(event) {
    var entityId = event.get("entityId");
    var entity = store.get(entityId);
    if (entity === null || !entity.hasComponent("Skillbook")) return;

    var skills = entity.getComponent("Skillbook");
    var known = skills.get("known");
    var out = event.get("known");
    if (known === null || out === null) return;

    for (var i = 0; i < known.size(); i++) {
        var skill = known.get(i);
        var base = String(skill.get("base"));
        var def = engine.loadYaml("skills/" + base + ".yaml");
        if (def === null) continue;

        var name = def.get("name") !== null ? String(def.get("name")) : base;
        var description = def.get("description") !== null ? String(def.get("description")) : "";
        var icon = def.get("icon") !== null ? String(def.get("icon")) : name.substring(0, 1);
        var node = skill.get("node") !== null ? String(skill.get("node")) : null;
        var equipped = skill.get("equipped") === true;
        out.add(engine.newSkillEntry(base, name, description, icon, equipped, node));
    }

    event.set("slots", skills.has("slots") ? skills.getInt("slots") : 6);
});
