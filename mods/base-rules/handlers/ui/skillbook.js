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
        var kind = "active";
        if (def === null) {
            def = engine.loadYaml("passives/" + base + ".yaml");
            kind = "passive";
        }
        if (def === null) continue;

        var name = def.get("name") !== null ? String(def.get("name")) : base;
        var description = def.get("description") !== null ? String(def.get("description")) : "";
        var icon = def.get("icon") !== null ? String(def.get("icon")) : name.substring(0, 1);
        var node = skill.get("node") !== null ? String(skill.get("node")) : null;
        if (node !== null && typeof Talent !== "undefined" && Talent !== null && typeof Talent.evolutionDisplay === "function") {
            var display = Talent.evolutionDisplay(node);
            if (display !== null) {
                if (display.name !== null) name = display.name;
                if (display.description !== null) description = display.description;
                if (display.icon !== null) icon = display.icon;
            }
        }
        var equipped = skill.get("equipped") === true;
        var level = skill.containsKey && skill.containsKey("level") ? parseInt(skill.get("level")) : 1;
        out.add(engine.newSkillEntry(base, name, description, icon, equipped, node, level, kind));
    }

    if (typeof Talent !== "undefined" && Talent !== null && typeof Talent.derivedPassives === "function") {
        var talentPassives = Talent.derivedPassives(entity);
        for (var t = 0; t < talentPassives.length; t++) {
            var passive = talentPassives[t];
            var effect = passive.effect !== undefined && passive.effect !== null ? String(passive.effect) : "";
            if (effect !== "skill_patch" && effect !== "handler") continue;
            var passiveId = passive.id !== undefined && passive.id !== null ? String(passive.id) : ("talent_passive_" + t);
            var passiveName = passive.name !== undefined && passive.name !== null ? String(passive.name) : passiveId;
            var passiveDescription = passive.description !== undefined && passive.description !== null
                ? String(passive.description)
                : (effect === "skill_patch" ? "天赋提供的技能增强" : "天赋提供的特殊效果");
            var passiveIcon = passive.icon !== undefined && passive.icon !== null
                ? String(passive.icon)
                : passiveName.substring(0, 1);
            var passiveLevel = passive.level !== undefined && passive.level !== null ? parseInt(passive.level) : 1;
            out.add(engine.newSkillEntryWithSource(passiveId, passiveName, passiveDescription, passiveIcon, false, null, passiveLevel, "passive", "talent"));
        }
    }

    event.set("slots", skills.has("slots") ? skills.getInt("slots") : 6);
});
