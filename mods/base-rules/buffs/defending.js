// 防御状态：当回合生效、当回合失效。
// 防御是先制动作（defend.yaml: preemptive），在回合伊始（combat_flow pass 1）全场结算，
// 护住本回合所有攻击；在本回合 round_end（攻击均已结算后）移除。
//
// 因为快照在 resolve 结束后才生成，本回合移除意味着指令阶段的快照里【不含】Buff_defending
// —— 这正是我们要的「下一回合不再显示图标」。当回合的图标可见性由 defend.js 的 buff_applied
// 效果自带的 buff 描述符在前端动画播放期间叠加显示（见 defend.js / BattleGrid.vue）。
engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        if (entity.hasComponent("Buff_defending")) {
            buffs.removeBuff(entity.getId(), "defending");
        }
    }
});
