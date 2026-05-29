// 中毒：每回合结束时每层扣 damage 点 HP，递减 remaining，到 0 移除

engine.on("combat.round_end", 50, function(event) {
    var combatId = event.get("combatId");
    if (!combatId) return;
    var combatants = store.getByTagAsList("combat:" + combatId);

    for (var i = 0; i < combatants.size(); i++) {
        var entity = combatants.get(i);
        var poison = entity.getComponent("Buff_poison");
        if (poison === null) continue;

        // 已死亡单位不触发 buff 效果
        var health = entity.getComponent("Health");
        if (health === null || health.getInt("hp") <= 0) continue;

        var stacks = poison.has("stacks") ? poison.getInt("stacks") : 1;
        var dmgPerStack = poison.has("damage") ? poison.getInt("damage") : 3;
        var totalDmg = stacks * dmgPerStack;

        health.set("hp", Math.max(0, health.getInt("hp") - totalDmg));

        var remaining = poison.has("remaining") ? poison.getInt("remaining") : 3;
        remaining--;
        if (remaining <= 0) {
            buffs.removeBuff(entity.getId(), "poison");
        } else {
            poison.set("remaining", remaining);
        }
    }
});
