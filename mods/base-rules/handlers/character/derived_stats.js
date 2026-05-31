// 武器对应属性的倍率:力量 ×2(纯输出补偿),其余 ×1。
function weaponMult(attr) { return attr === "力量" ? 2 : 1; }

var PLACEHOLDER_WEAPON_BASE = 5;    // #2 占位武器基础;Ch2 真武器替换
var MAXHP_FLOOR = 30;               // 基础血量底盘:体质削到 0 也有此血量,不暴毙

// 共享:给实体注册「派生」modifier(exclusive,priority 300 → 最后跑)。
// 读 PrimaryStats → 写 DerivedStats(三强度)+ Health.maxHp + CombatStats.speed/attack。
// 全部 SET 语义(非累加),对重算/持久化重载幂等(ModifierChain 每次先 restoreBaseState 再 apply)。
function registerDerivedModifier(entityId) {
    engine.addModifier(entityId, {
        typeId: "derived",
        id: "derived_stats",
        label: "派生属性",
        apply: function(ent) {
            var p = ent.getComponent("PrimaryStats");
            if (p === null) return;
            var d = ent.getComponent("DerivedStats");
            var wAttr = p.has("weaponAttr") ? p.get("weaponAttr") : "力量";
            var phys = Math.ceil(p.getInt(wAttr) * weaponMult(wAttr));
            if (d !== null) {
                d.set("物理强度", phys);
                d.set("法术强度", p.getInt("智力"));
                d.set("精神强度", p.getInt("意志"));
            }
            var h = ent.getComponent("Health");
            if (h !== null) h.set("maxHp", MAXHP_FLOOR + p.getInt("体质") * 10);   // SET,带底盘
            var c = ent.getComponent("CombatStats");
            if (c !== null) {
                c.set("speed", p.getInt("敏捷"));
                c.set("attack", Math.ceil(PLACEHOLDER_WEAPON_BASE * (1 + phys / 100)));
            }
        }
    });
}
