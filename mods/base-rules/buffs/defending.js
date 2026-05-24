// 防御状态：回合结算开始时自动移除（在 resolve_round 最早阶段）
engine.on("combat.resolve_round", 98, function(event) {
    var combatId = event.get("combatId");
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasComponent("Buff_defending")) {
            buffs.removeBuff(entity.getId(), "defending");
        }
    }
});
