// 诅咒 buff→modifier 桥：5 主属性各 -2(clamp ≥0)，typeId buff(priority 10，derived 前跑)。
// 体质削减经 derived 重算 maxHp；hp 由 recalculate_hooks 的 scratch 机制收口——
// 它在 recalc 后置 hp=min(recalc前的hp, maxHp)：体质降 → maxHp 降 → hp clamp 到新上限；
// 诅咒消除 → maxHp 回升但 hp 取 min(旧hp, 新maxHp)=旧hp,不补血。无需改基础快照。
var CURSE_STATS = ["力量", "敏捷", "智力", "体质", "意志"];

engine.on("buff.applied", 50, function(event) {
    if (event.get("buffId") !== "cursed") return;
    var targetId = event.get("targetId");
    // 幂等:cursed stacking=refresh，buff.applied 会重复触发，先撤旧 modifier 避免叠加。
    engine.removeModifier(targetId, "cursed_" + targetId);
    engine.addModifier(targetId, {
        typeId: "buff",
        id: "cursed_" + targetId,
        label: "诅咒",
        apply: function(ent) {
            var p = ent.getComponent("PrimaryStats");
            if (p === null) return;
            for (var i = 0; i < CURSE_STATS.length; i++) {
                var k = CURSE_STATS[i];
                if (p.has(k)) p.set(k, Math.max(0, p.getInt(k) - 2));   // clamp ≥0
            }
        }
    });
});

engine.on("buff.removed", 50, function(event) {
    if (event.get("buffId") !== "cursed") return;
    engine.removeModifier(event.get("targetId"), "cursed_" + event.get("targetId"));
});

// 每回合结束递减 remaining，到 0 移除(仿 poison tick，但无 DoT 伤害)。
engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);
    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var c = entity.getComponent("Buff_cursed");
        if (c === null) continue;
        var remaining = c.has("remaining") ? c.getInt("remaining") : 3;
        remaining--;
        if (remaining <= 0) buffs.removeBuff(entity.getId(), "cursed");
        else c.set("remaining", remaining);
    }
});
