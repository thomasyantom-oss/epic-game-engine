engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);

    var logEntries = engine.newList();
    var effects = engine.newList();
    var animation = engine.newList();

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var burning = entity.getComponent("Buff_burning");
        if (burning === null) continue;

        var dmg = burning.has("damage") ? burning.getInt("damage") : 3;
        var health = entity.getComponent("Health");
        if (health !== null) {
            health.set("hp", Math.max(0, health.getInt("hp") - dmg));
        }

        var entityName = entity.hasComponent("Name") ? entity.getComponent("Name").getString("value") : entity.getId();
        var entitySide = entity.hasTag("player") ? "player" : "enemy";

        // Log
        var logEntry = engine.newList();
        var s1 = engine.newMap(); s1.put("text", entityName); s1.put("color", entitySide); logEntry.add(s1);
        var s2 = engine.newMap(); s2.put("text", " 被灼烧，受到 "); s2.put("color", "text"); logEntry.add(s2);
        var s3 = engine.newMap(); s3.put("text", "" + dmg); s3.put("color", "damage"); logEntry.add(s3);
        var s4 = engine.newMap(); s4.put("text", " 点伤害"); s4.put("color", "text"); logEntry.add(s4);
        logEntries.add(logEntry);

        // Effect
        var eff = engine.newMap();
        eff.put("target", entity.getId());
        eff.put("type", "hp_change");
        eff.put("amount", -dmg);
        eff.put("hp", health.getInt("hp"));
        eff.put("maxHp", health.getInt("maxHp"));
        effects.add(eff);

        // Animation
        var shakeAnim = engine.newMap();
        shakeAnim.put("type", "shake");
        shakeAnim.put("target", entity.getId());
        shakeAnim.put("intensity", "light");
        animation.add(shakeAnim);
        var dmgAnim = engine.newMap();
        dmgAnim.put("type", "damage_number");
        dmgAnim.put("target", entity.getId());
        dmgAnim.put("value", -dmg);
        dmgAnim.put("color", "damage");
        animation.add(dmgAnim);

        // Tick down
        var remaining = burning.has("remaining") ? burning.getInt("remaining") : 2;
        remaining--;
        if (remaining <= 0) {
            buffs.removeBuff(entity.getId(), "burning");
        } else {
            burning.set("remaining", remaining);
        }
    }

    if (logEntries.size() > 0) {
        var eventData = engine.newMap();
        eventData.put("log", logEntries);
        eventData.put("effects", effects);
        eventData.put("animation", animation);
        engine.combatEvent(combatId, eventData);
    }
});
