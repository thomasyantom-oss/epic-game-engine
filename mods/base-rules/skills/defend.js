engine.on("combat.unit_action", 80, function(event) {
    var cmd = event.get("command");
    var cmdType = cmd.get("type");
    if (cmdType !== "defend") return;

    var actorId = event.get("actorId");
    var data = engine.newMap();
    data.put("color", "#66bb6a");
    buffs.applyBuff(actorId, "defending", data);
});
