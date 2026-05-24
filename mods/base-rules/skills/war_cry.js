engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    if (cmd.get("type") !== "war_cry") return;
    var actorId = event.get("actorId");
    var combatId = event.get("combatId");

    // Apply buff to all allies
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasTag("player") && entity.getComponent("Health").getInt("hp") > 0) {
            var data = engine.newMap();
            data.put("color", "#ffab40");
            data.put("stacking", "refresh");
            buffs.applyBuff(entity.getId(), "war_cry", data);
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
        var s2 = engine.newMap(); s2.put("text", " 发出了战吼！全体攻击力提升"); s2.put("color", "text"); segments.add(s2);
        evt.put("segments", segments);
        evt.put("effects", engine.newList());

        var animation = engine.newList();
        var buffAnim = engine.newMap();
        buffAnim.put("type", "buff_up");
        buffAnim.put("target", "field");
        buffAnim.put("scope", "field");
        buffAnim.put("color", "#ffab40");
        animation.add(buffAnim);
        evt.put("animation", animation);

        combat.getComponent("CombatEvents").get("queue").add(evt);
    }
});
