engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "curse") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");

    // Apply debuff to all enemies
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasTag("enemy") && entity.getComponent("Health").getInt("hp") > 0) {
            var data = engine.newMap();
            data.put("color", "#e57373");
            data.put("stacking", "refresh");
            data.put("remaining", 3);
            data.put("permanent", false);
            data.put("positive", false);
            buffs.applyBuff(entity.getId(), "cursed", data);
        }
    }

    // Log event
    var combat = store.get(combatId);
    if (combat !== null && combat.hasComponent("CombatEvents")) {
        var caster = store.get(actorId);
        var casterName = caster.hasComponent("Name") ? caster.getComponent("Name").getString("value") : actorId;
        var evt = engine.newMap();
        var segments = engine.newList();
        var s1 = engine.newMap(); s1.put("text", casterName); s1.put("color", "player"); segments.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 施放了诅咒！全体敌人被削弱"); s2.put("color", "text"); segments.add(s2);
        evt.put("segments", segments);
        var effects = engine.newList();

        // Add buff_applied effects for each affected enemy
        var allEnemiesForEffects = store.getByTagAsList("combat:" + combatId);
        for (var j = 0; j < allEnemiesForEffects.size(); j++) {
            var en2 = allEnemiesForEffects.get(j);
            if (en2.hasTag("enemy") && en2.getComponent("Health").getInt("hp") > 0) {
                var buffEff = engine.newMap();
                buffEff.put("type", "buff_applied");
                buffEff.put("target", en2.getId());
                effects.add(buffEff);
            }
        }

        evt.put("effects", effects);

        var animation = engine.newList();
        var allEnemies = store.getByTagAsList("combat:" + combatId);
        for (var k = 0; k < allEnemies.size(); k++) {
            var en = allEnemies.get(k);
            if (en.hasTag("enemy") && en.getComponent("Health").getInt("hp") > 0) {
                var debuffAnim = engine.newMap();
                debuffAnim.put("type", "debuff_down");
                debuffAnim.put("target", en.getId());
                debuffAnim.put("color", "#e57373");
                animation.add(debuffAnim);
            }
        }
        evt.put("animation", animation);

        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
