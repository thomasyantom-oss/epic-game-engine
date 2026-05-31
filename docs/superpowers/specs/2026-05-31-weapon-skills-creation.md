# 设计 — Ch1/F2 批次 B:武器系统 + 职业技能 + 创建页

> 日期 2026-05-31 · 分支 `feature/ch1-f2-attribute-table` · 接 prompt.md 批次 B(#3/#5/#6)。
> 后端基线 108/108 绿。三块按 #3 武器 → #5 技能(依赖#3)→ #6 创建页 顺序实现。

## 锁定的公式(用户拍板)

1. **武器最终伤害** = `⌈B × (1 + 武器属性值 × 倍率 / 100)⌉`,B 统一 = 5;倍率沿用面板:**力量 ×2,其余 ×1**。
2. **技能 scaling 默认读裸 PrimaryStats**(`{力量:0.1}` → 读裸力量)。`computeDamage` 泛化成两级查找:按 key 名**先查 DerivedStats(三强度)再查 PrimaryStats**,为以后只读强度的技能留路。全程 `Math.ceil`。

---

## #3 武器系统

### 两层强度(已存在,确认不动)
- **第一层 面板物理强度** = `主属性值 × 倍率`(力量×2 其余×1),`derived_stats.js` 已写 `DerivedStats.物理强度`。强度系技能/buff 吃它。
- **第二层 武器最终伤害**:由**手上武器绑定的属性**算,与主属性无关。武器类型→吃的属性:
  | 武器 | 属性 | 倍率 |
  |---|---|---|
  | 双手剑 | 力量 | 2 |
  | 匕首 | 敏捷 | 1 |
  | 法杖 | 智力 | 1 |
  | 拳套 | 体质 | 1 |
  | 图腾 | 意志 | 1 |

  例:法师拿双手剑→伤害按他的(低)力量算;力量职业拿匕首→只吃自己(低)敏捷。防止用不顺手武器白嫖主属性强度。

### `CombatStats.attack` 改为「当前装备武器的最终伤害」
- **算法挪进 `derived_stats.js`**(priority 300 最后跑,能读到装备槽信息,避免装备 modifier 设 attack 后被 derived 覆盖的顺序冲突)。
- derived modifier 的 apply 末尾:读 `EquipmentSlots.weapon` → 取武器 `ItemMeta`/`ItemStats` 中绑定的属性 key + 基础值 B → `attack = ⌈B × (1 + 裸属性值 × 倍率 / 100)⌉`。
- **无武器 fallback**:沿用现有占位 `⌈PLACEHOLDER_WEAPON_BASE × (1 + 物理强度/100)⌉`。
- 武器绑定属性靠 `weaponAttr`(武器读 `ItemStats.weaponAttr`,如 `"力量"`);倍率用现有 `weaponMult(attr)`。

### 装备 modifier 改造(`equipment/equip.js`)
- `registerEquipmentModifier` 现硬编码只往 `CombatStats` 加。改成支持**全限定字段** `"PrimaryStats.力量": +N` / `"DerivedStats.物理强度": +N` / `"CombatStats.defense": +N`,解析 `组件.字段`。
- **复用 select.js 已有的 `组件.字段` 解析模式**(class modifier apply,select.js:167-179):`indexOf(".")` 切组件名/字段名,`+` 前缀=累加否则=SET。
- `attack` 字段特例:武器的 attack 不再当 CombatStats 加性 modifier(改由 derived 算最终伤害),武器 stats 改放 `weaponAttr`(绑定属性)+ `base`(基础值 B,默认 5)。普通护甲/饰品仍走全限定字段加 `CombatStats.defense`/`CombatStats.speed`。

### `items.yaml` 同步改格式
- 武器条目:`stats: { weaponAttr: "力量", base: 5 }`(B 统一 5)。
- 护甲/饰品:`stats: { "CombatStats.defense": 5 }` 全限定。
- 新增 **5 把初始武器**(每职业一把,对应五属性):
  | id | name | weaponAttr |
  |---|---|---|
  | greatsword | 双手剑 | 力量 |
  | dagger | 匕首 | 敏捷 |
  | staff | 法杖 | 智力 |
  | gauntlet | 拳套 | 体质 |
  | totem | 图腾 | 意志 |
- 用户要逐一测试不同武器在不同职业手上的表现 → **每个职业发全部 5 把**(改 select.js startingItems)。
- 旧武器(iron_sword/steel_sword/fire_staff)迁到新格式或保留作护甲对照;护甲/饰品 leather_armor/chain_mail/speed_ring 改全限定字段。

---

## #5 职业技能

`base` = 武器最终伤害(即 `CombatStats.attack`);scaling 见各条。`computeDamage` 泛化两级查找后,`{力量:0.1}` 读裸力量。

| 职业 | 技能 | 公式 |
|---|---|---|
| 战士 | 顺劈斩 cleave | 武器伤害 + ⌈0.1×力量⌉ |
| 战士 | 战吼 war_cry | 提高力量值 = ⌈0.5×物理强度⌉ |
| 法师 | 火球 fireball | 10 + ⌈0.2×智力⌉,灼烧 debuff 3 + ⌈0.1×智力⌉ |
| 法师 | 光击阵 light_field | 15 + ⌈0.1×智力⌉ |
| 盗贼 | 毒镖 poison_dart | 武器伤害 + ⌈0.1×敏捷⌉,中毒 3 + ⌈0.1×敏捷⌉ |
| 盗贼 | **背刺 backstab(新建)** | 单体,武器伤害 + ⌈0.2×敏捷⌉,无位置/潜行条件 |
| 德鲁伊 | 诅咒 curse(重做) | 见下 |
| 德鲁伊 | 贯穿射线 piercing_ray | 10 + ⌈0.2×意志⌉ |
| 护卫 | 治愈 heal | 10 + ⌈0.1×体质⌉ |
| 护卫 | **震波(pulse_wave 改名)** | **单体**,武器伤害 + ⌈0.1×体质⌉ |
| 全员 | basic_attack / defend / flee | 保留 |

> 火球现为 `base8 + 0.5×法术强度` → 改 `10 + 0.2×智力`(智力≡法术强度数值等价,但走裸属性路径)。

### 诅咒重做(德鲁伊,最复杂项)
- `mode: group`(全体敌人),`remaining: 3`。
- 应用 `cursed` buff,其 modifier 对**5 个主属性各 −2**,`clamp ≥0`(不能为负)。
- buff modifier 写 PrimaryStats,priority 须 **< 300**(在 derived 之前跑),让 derived 据削后体质重算 maxHp。
- **maxHp/hp 行为**:−2 体质 → derived SET maxHp 下降 → 当前 hp `clamp` 到新上限。buff 过期 → maxHp 回升但 **hp 不补**(SET maxHp 与 hp 独立,自然行为,无需特判)。

### 战吼无限迭代(如实实现让用户实测)
- 力量→物理强度→战吼加力量→… 用户想测会不会爆炸。
- 预判:不会爆炸——ModifierChain 每次 recalc 先 `restoreBaseState` 再按 priority 跑一遍,单次不迭代到不动点;战吼更可能"加不上或只加一次"。
- **如实按 spec 实现**,实测后再决定要不要改快照式一次性加值。

### 落地
- 职业 schema 加 `starting_skills: [...]`。
- `select.js` 读 `classSchema.raw().get("starting_skills")` 替代硬编码(现 line ~119-126 只给法师 fireball/light_field)。

---

## #6 创建/选择页 UI

### 布局(用户描述)
- 上面一排角色列表。
- 下面选中角色详情:第一行角色名;下方左列属性 + 升级加成(growth),右列放头像 + 头像下角色描述。

### 头像
- **预留图片位不填**:后端下发 `portrait` 字段(路径),前端预留 `<img>` 占位框,不放图(以后丢图进 assets 即显示)。

### 后端缺口
- `buildCharacterSelectSnapshot`(SnapshotService.java)目前**不下发**职业属性预览/growth/description。需补下发:从职业 schema 读 `label`/`description`/`growth`/`modifiers`(`Schema.raw()` 读自定义字段),组装进 select snapshot。

### 前端
- `CharacterSelect.vue` / `CharacterCreate.vue`,用 frontend-design skill。
- **约束**:这是用户**明确要求改布局**的页面,不受"只改颜色/字体禁改布局"那条记忆约束。

---

## 关键技术事实(别重踩)
- **ModifierChain 升序应用**(priority 小先跑);`derived`=300 最后跑。
- **持久化存 base 快照**;加性 modifier 重载不翻倍;要持久化字段须在 `setBase` 前写。
- `engine.clearChain(entityId)` 已暴露,实体移除时清缓存。
- 读 schema 自定义字段:`classSchema.raw().get("weapon_attr"/"growth"/"starting_skills")`。
- 跨 handler 文件全局函数可见(同 GraalJS 上下文):registerDerivedModifier / registerEquipmentModifier。
- **golden 重生成**:火球数值改 → 删 `golden/*.json` → 跑 SkillFidelityTest 自动 capture → 再跑确认绿;SkillFidelityHarness 给单位补的 DerivedStats/装备需对应新公式。
- 数值基调:maxHp=30+体质×10(SET 幂等,底盘 30);法术/精神强度=智力/意志(×1)。

## 测试策略
- 每块 TDD:武器伤害公式(双手剑力量×2 vs 匕首敏捷×1 在不同职业)、装备全限定字段解析、各技能 computeDamage、诅咒 maxHp/hp 行为、starting_skills 分发。
- 后端保持 108/108 绿 + 新增测试全绿。
- golden(SkillFidelity)火球改后重生成。
