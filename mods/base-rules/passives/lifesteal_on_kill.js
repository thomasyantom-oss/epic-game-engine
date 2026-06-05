engine.on("combat.unit_death", 60, function(event) {
    var killerId = event.get("killerId");
    if (killerId === null) return;
    if (!Passive.owns(killerId, "lifesteal_on_kill")) return;
    var killer = store.get(killerId);
    if (killer === null) return;
    var h = killer.getComponent("Health");
    if (h === null) return;
    h.set("hp", Math.min(h.getInt("maxHp"), h.getInt("hp") + 5));
});
