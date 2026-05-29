engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "defend") return;

    var actorId = event.get("actorId");
    var combatId = event.get("combatId");
    var actor = store.get(actorId);

    var actorName = actor.hasComponent("Name") ? actor.getComponent("Name").getString("value") : actorId;

    // Build log entry
    var logEntry = engine.newList();
    var s1 = engine.newMap(); s1.put("text", actorName); s1.put("color", "player"); logEntry.add(s1);
    var s2 = engine.newMap(); s2.put("text", " 进入防守姿态"); s2.put("color", "text"); logEntry.add(s2);

    var logEntries = engine.newList();
    logEntries.add(logEntry);

    var effects = engine.newList();

    // Apply defending buff BEFORE combat event
    var data = engine.newMap();
    data.put("color", "#66bb6a");
    data.put("permanent", false);
    data.put("positive", true);
    buffs.applyBuff(actorId, "defending", data);

    // Add buff_applied effect so frontend syncs buff icons at hit time
    var buffEff = engine.newMap();
    buffEff.put("type", "buff_applied");
    buffEff.put("target", actorId);
    effects.add(buffEff);

    // Build animation
    var animation = engine.newList();
    var buffAnim = engine.newMap();
    buffAnim.put("type", "buff_up");
    buffAnim.put("target", actorId);
    buffAnim.put("color", "#66bb6a");
    animation.add(buffAnim);

    var eventData = engine.newMap();
    eventData.put("log", logEntries);
    eventData.put("effects", effects);
    eventData.put("animation", animation);

    engine.combatEvent(combatId, eventData);
});
