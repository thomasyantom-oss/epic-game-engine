engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var burning = entity.getComponent("Buff_burning");
        if (burning === null) continue;

        var dmg = burning.has("damage") ? burning.getInt("damage") : 3;
        var health = entity.getComponent("Health");
        if (health !== null) {
            health.set("hp", Math.max(0, health.getInt("hp") - dmg));
        }

        var remaining = burning.has("remaining") ? burning.getInt("remaining") : 2;
        remaining--;
        if (remaining <= 0) {
            buffs.removeBuff(entity.getId(), "burning");
        } else {
            burning.set("remaining", remaining);
        }
    }
});
