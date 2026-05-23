// Record combat events as log entries on the combat entity
engine.on("combat.resolve_round", 50, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null) return;

    // Initialize log component if not present
    if (!combat.hasComponent("CombatLog")) {
        combat.addComponent(engine.newComponent("CombatLog"));
        combat.getComponent("CombatLog").set("entries", engine.newList());
    }
});

// Log damage dealt
engine.on("combat.damage_dealt", 50, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatLog")) return;

    var attackerId = event.get("attackerId");
    var targetId = event.get("targetId");
    var damage = event.get("damage");

    var attacker = store.get(attackerId);
    var target = store.get(targetId);
    var attackerName = attacker.hasComponent("Name") ? attacker.getComponent("Name").getString("value") : attackerId;
    var targetName = target.hasComponent("Name") ? target.getComponent("Name").getString("value") : targetId;
    var attackerSide = attacker.hasTag("player") ? "player" : "enemy";
    var targetSide = target.hasTag("player") ? "player" : "enemy";

    var entry = engine.newMap();
    entry.put("type", "damage");
    entry.put("attackerName", attackerName);
    entry.put("attackerSide", attackerSide);
    entry.put("targetName", targetName);
    entry.put("targetSide", targetSide);
    entry.put("damage", damage);
    entry.put("targetHp", target.getComponent("Health").getInt("hp"));
    entry.put("targetMaxHp", target.getComponent("Health").getInt("maxHp"));

    combat.getComponent("CombatLog").get("entries").add(entry);
});

// Log deaths
engine.on("combat.unit_death", 50, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatLog")) return;

    var deadId = event.get("deadId");
    var dead = store.get(deadId);
    var deadName = dead.hasComponent("Name") ? dead.getComponent("Name").getString("value") : deadId;

    var entry = engine.newMap();
    entry.put("type", "death");
    entry.put("name", deadName);
    entry.put("side", dead.hasTag("player") ? "player" : "enemy");

    combat.getComponent("CombatLog").get("entries").add(entry);
});
