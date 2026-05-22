engine.on("combat.determine_order", 100, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);
    var sorted = [];

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasComponent("Health") && entity.getComponent("Health").getInt("hp") > 0) {
            sorted.push({
                id: entity.getId(),
                speed: entity.hasComponent("CombatStats") ? entity.getComponent("CombatStats").getInt("speed") : 0
            });
        }
    }

    sorted.sort(function(a, b) { return b.speed - a.speed; });
    var order = [];
    for (var i = 0; i < sorted.length; i++) {
        order.push(sorted[i].id);
    }
    event.set("turnOrder", order);
});
