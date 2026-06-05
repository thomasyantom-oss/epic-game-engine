// 敌方单位死亡 → 给本场玩家发经验。
// reward 优先读怪 CombatantMeta.xpReward（难度/种族/精英/剧情倍率的唯一落点，Ch3），
// 否则按等级走 Progression.xpReward 默认曲线。每只怪单独结算（unit_death 每次一份）。
engine.on("combat.unit_death", 40, function(event) {
    var dead = store.get(event.get("deadId"));
    if (dead === null || !dead.hasTag("enemy")) return;

    var meta = dead.getComponent("CombatantMeta");
    var monLevel = (meta !== null && meta.has("level")) ? meta.getInt("level") : 1;
    var reward;
    if (meta !== null && meta.has("xpReward")) {
        reward = parseInt(meta.getInt("xpReward"));
    } else {
        reward = (typeof Progression !== "undefined") ? Progression.xpReward(monLevel) : monLevel * 14;
    }
    if (reward <= 0) return;

    var combatId = event.get("combatId");
    if (combatId === null) return;
    var combatants = store.getByTagAsList("combat:" + combatId);
    var player = null;
    for (var i = 0; i < combatants.size(); i++) {
        if (combatants.get(i).hasTag("player")) { player = combatants.get(i); break; }
    }
    if (player === null) return;

    var gx = engine.newEvent("action.gain_xp");
    gx.set("playerId", player.getId());
    gx.set("amount", reward);
    engine.fire("action.gain_xp", gx);
});
