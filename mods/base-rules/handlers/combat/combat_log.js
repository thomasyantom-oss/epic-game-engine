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

    // Add round separator
    var round = combat.getComponent("CombatState").getInt("round");
    var segments = engine.newList();
    var s = engine.newMap();
    s.put("text", "— 第 " + round + " 回合 —");
    s.put("color", "highlight");
    s.put("round", round);
    segments.add(s);
    combat.getComponent("CombatLog").get("entries").add(segments);
});

// Log damage dealt - store as pre-formatted TextSegment arrays with semantic roles
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
    var attackerColor = attacker.hasTag("player") ? "player" : "enemy";
    var targetColor = target.hasTag("player") ? "player" : "enemy";

    // Store as segments with semantic color roles, not hex values
    var segments = engine.newList();
    var s1 = engine.newMap(); s1.put("text", attackerName); s1.put("color", attackerColor); segments.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 对 "); s2.put("color", "text"); segments.add(s2);
    var s3 = engine.newMap(); s3.put("text", targetName); s3.put("color", targetColor); segments.add(s3);
    var s4 = engine.newMap(); s4.put("text", " 造成 "); s4.put("color", "text"); segments.add(s4);
    var s5 = engine.newMap(); s5.put("text", "" + damage); s5.put("color", "damage"); segments.add(s5);
    var s6 = engine.newMap(); s6.put("text", " 点伤害"); s6.put("color", "text"); segments.add(s6);

    combat.getComponent("CombatLog").get("entries").add(segments);
});

// Log deaths - store as pre-formatted TextSegment arrays with semantic roles
engine.on("combat.unit_death", 50, function(event) {
    var combatId = event.get("combatId");
    var combat = store.get(combatId);
    if (combat === null || !combat.hasComponent("CombatLog")) return;

    var deadId = event.get("deadId");
    var dead = store.get(deadId);
    var deadName = dead.hasComponent("Name") ? dead.getComponent("Name").getString("value") : deadId;
    var deadColor = dead.hasTag("player") ? "player" : "enemy";

    var segments = engine.newList();
    var s1 = engine.newMap(); s1.put("text", deadName); s1.put("color", deadColor); segments.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 被击败了！"); s2.put("color", "enemy"); segments.add(s2);

    combat.getComponent("CombatLog").get("entries").add(segments);
});
