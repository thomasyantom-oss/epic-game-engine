// 战吼 buff→modifier 桥：应用时给 力量 +⌈0.5×物理强度⌉(typeId buff，priority 10，在 derived 之前跑)；
// 移除时撤销。modifier id 含 targetId，支持多单位独立。
// 注：modifier 在 recalc 时 restoreBaseState 会抹掉 DerivedStats，
//     所以在 buff.applied 时先读取当前 物理强度（上一次 recalc 的结果）并闭包保存，
//     以实现「上一轮 物理强度 → 本轮 力量加成」的迭代行为（符合 spec 意图）。
engine.on("buff.applied", 50, function(event) {
    if (event.get("buffId") !== "war_cry") return;
    var targetId = event.get("targetId");
    var target = store.get(targetId);
    if (target === null) return;
    // 读取应用时刻的 物理强度（上次 recalc 结果），闭包给 modifier 使用
    var d = target.getComponent("DerivedStats");
    var physStrength = (d !== null && d.has("物理强度")) ? d.getInt("物理强度") : 0;
    var bonus = Math.ceil(0.5 * physStrength);
    // 幂等:重复施放(war_cry stacking=refresh,buff.applied 会再次触发)先撤旧 modifier,避免叠加翻倍。
    engine.removeModifier(targetId, "war_cry_" + targetId);
    if (bonus === 0) return;   // 无加成不注册空 modifier
    engine.addModifier(targetId, {
        typeId: "buff",
        id: "war_cry_" + targetId,
        label: "战吼",
        apply: function(ent) {
            var p = ent.getComponent("PrimaryStats");
            if (p === null) return;
            p.set("力量", p.getInt("力量") + bonus);
        }
    });
});

engine.on("buff.removed", 50, function(event) {
    if (event.get("buffId") !== "war_cry") return;
    engine.removeModifier(event.get("targetId"), "war_cry_" + event.get("targetId"));
});
