// 武器对应属性的倍率:力量 ×2(纯输出补偿),其余 ×1。
function weaponMult(attr) { return attr === "力量" ? 2 : 1; }

function weaponAttack(base, attrValue, attr) {
    return Math.ceil(base * (1 + Math.sqrt(Math.max(0, attrValue * weaponMult(attr))) / 10));
}

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
            var wAttr = p.has("weaponAttr") ? p.getString("weaponAttr") : "力量";
            var phys = Math.ceil(p.getInt(wAttr) * weaponMult(wAttr));
            if (d !== null) {
                d.set("物理强度", phys);
                d.set("法术强度", p.getInt("智力"));
                d.set("精神强度", p.getInt("意志"));
            }
            var h = ent.getComponent("Health");
            if (h !== null) {
                h.set("maxHp", MAXHP_FLOOR + p.getInt("体质") * 10);   // SET,带底盘
                // 体质被削(诅咒)→ maxHp 下降时,当前 hp 不得超过新上限;maxHp 回升不补 hp。
                if (h.getInt("hp") > h.getInt("maxHp")) h.set("hp", h.getInt("maxHp"));
            }
            var c = ent.getComponent("CombatStats");
            if (c !== null) {
                c.set("speed", p.getInt("敏捷"));
                // 武器最终伤害(第二层)：读装备武器绑定属性；无武器走占位物理强度。
                var atk = weaponAttack(PLACEHOLDER_WEAPON_BASE, p.getInt(wAttr), wAttr);
                var slots = ent.getComponent("EquipmentSlots");
                if (slots !== null && slots.get("weapon") !== null) {
                    var weapon = store.get(slots.get("weapon"));
                    if (weapon !== null && weapon.hasComponent("ItemStats")) {
                        var ws = weapon.getComponent("ItemStats");
                        var wAttrName = ws.has("weaponAttr") ? ws.getString("weaponAttr") : "力量";
                        var B = ws.has("base") ? ws.getInt("base") : PLACEHOLDER_WEAPON_BASE;
                        var wAttrVal = p.has(wAttrName) ? p.getInt(wAttrName) : 0;   // 防武器绑定的属性名实体没有 → 避免 NPE
                        atk = weaponAttack(B, wAttrVal, wAttrName);
                    }
                }
                c.set("attack", atk);
            }
        }
    });
}
