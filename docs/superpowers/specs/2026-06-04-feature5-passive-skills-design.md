# Feature #5 — 被动技能(框架)· 设计

**日期:** 2026-06-04
**性质:** Chapter 1 第 5 个 feature 的设计 spec。**只搭框架(机制 + 接缝),不做内容设计**——真被动 / 真数值 / 真曲线留给后续「Ch1 内容设计轮」。
**母文档:** `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md`(§1 架构决策必须遵守)。
**前序:** #1 技能架构重构(`骨架+helper+spec`)、#4 Skillbook 出战配置(`2026-06-03-feature4-skillbook-loadout-design.md`)。

---

## 0. 一句话

给「技能」这个概念补上**被动子类**,并装上**技能 spec 合并管道**——被动通过这条管道 patch 别的技能,通过常驻 modifier 改属性,通过战斗事件 JS 做特殊效果。本 feature 只造机制,内容真空。

---

## 1. 范围

### IN(本 feature 造)
- 技能数据模型升级:**父类「技能」+ active/passive 两子类**,统一实例形状,**实体无关持有**。
- 被动三条 effect 通路的**机制管线**:
  - 属性被动 → 常驻 ModifierChain modifier
  - 技能 patch 被动 → **技能 spec 合并管道**(本 feature 新建)
  - 特殊效果被动 → 战斗事件专属 JS(逃生舱)
- **被动 YAML spec 格式**(archetype 约定)。
- **统一 `level` 字段 + 管道按 level 缩放**(主动 + 被动共用)。
- **人物升级 → 技能等级**:只留接缝(hook 点),不写曲线。
- 前端 `SkillbookPanel` **被动 tab** 渲染(只读)。
- 每条通路 1 个 **demo 被动夹具**(证明管线通 + 当回归测试)。
- **debug**:给被动 / 设 level(测试用)。

### OUT(留口子,本 feature 不做)
- **真被动内容 / 真数值 / 每技能等级曲线** → 后续 Ch1 内容设计轮。
- **装备加技能等级 / 装备给新技能** → Chapter 2(管道预留 patch 槽)。
- **灵魂球进化被动 / node 变换** → Feature #7(实例预留 `node` 字段,空实现)。
- **专精 patch 技能 spec** → Feature #6(管道预留 patch 槽)。
- **怪物技能内容 / 怪物 AI 调主动** → 战斗 AI / 怪物种族子种族(`2026-06-03-monster-race-subrace-design.md`)。本 feature 只保证机制不排斥怪物持有。

---

## 2. 遵循的母文档架构决策

- **§1.5 出战/被动**:被动不占出战格;两类(纯属性 / 特殊效果)。本 feature **扩成三味**——把母文档「特殊效果」拆出「**技能 patch(数据层联动)**」一味走统一管道,「结构性 / 触发型」仍落 JS。这与 §1.2「数据层 patch 走统一管道,结构性联动落专属 JS」一致。
- **§1.2 技能也有一条 ModifierChain**:本 feature 给技能 spec 装**第一个真实 patch 源(被动)**。母文档原话「Feature 1 立骨架(单来源),4/5/6/7 各自加 patch 源」——核实后 #1 实际**未留 merge 缝**(`02_dispatch.js` 直接喂 raw spec),故本 feature 是**净新建这条管道**,而非补一段。
- **§1.6 升级 = 作者手画有限树**:`base + node + level` 是**同一实例上的字段**,不是「每个变体一个 id」(刻意不要 WoW 式 per-rank id)。
- **§1.8 等级 = 尺子,自动成长不手动分配**:技能 level 随人物等级**确定性**派生(曲线后续填),非「用多自涨」(非熟练度)。

---

## 3. 数据模型

### 3.1 父类「技能」+ 子类

「技能」是父抽象,主动 / 被动是子类(在数据驱动引擎里 = 共享 spec schema + `kind` 判别 + 各自生命周期):

- **父类 = spec**:每个技能(主动 / 被动)是一份 YAML spec,带 `id`、`kind: active | passive` + 共享字段。`kind` 挂 **spec**(类型属性),不挂实例。被动与主动**共用一个 id 命名空间**,全局唯一。
- **共享机制**(父类层,主动被动 + 玩家怪物都走同一份 helper):`base / node / level` 三件套、spec 合并管道(level 缩放 + 各 patch 源)。
- **子类差异**:
  - **主动**(`kind: active`):有 `equipped` / 占出战格、进指令栏、targeting / 动画 / MP;**触发方**另算——玩家走指令栏 UI,怪物走战斗 AI。
  - **被动**(`kind: passive`):拥有即生效、不占格、不进指令栏、三条 effect 通路。

### 3.2 实例形状(甲方案:统一一个 `known` 列表)

持有者的 `Skillbook` 组件:

```
Skillbook {
  slots,                                  # 出战格(仅约束主动)
  known: [ { base, node, level, equipped } ]
}
```

- `base` — 哪个技能 / 被动(指向 spec id)。
- `node` — 进化形态;主动 火球→炎爆,被动 灵魂球进化。**两子类都有,机制都是 #7**;本 feature 预留字段,默认 `null`。
- `level` — 量化标量,默认 `1`。喂给合并管道做缩放。来源:① 随人物等级自动涨(曲线留口子);② 外部 build(装备,Ch2)。
- `equipped` — **仅主动有意义**;被动拥有即生效,该字段忽略。

`kind` **不存进实例**——由 `base` 查 spec 得到。前端要分 tab → **snapshot 按 base 查出 `kind` 一起吐给前端**,实例保持最小。

### 3.3 实体无关持有

`Skillbook` 可挂任意实体。被动三通路天然实体无关:
- 属性被动 → `engine.addModifier(entityId, …)` 按实体注册;
- 技能 patch 被动 → 管道读**施法者本人**的 owned passives;
- 特殊效果 JS → 守卫「**这个实体**是否持有被动 X」。

**现状对齐:** 怪物现在**无 Skillbook**(`start_combat.js` 从 encounter 建属性,AI 写死 `basic_attack`)。本 feature 不给怪物加被动内容;管道对「无 Skillbook 施法者」退化为 passthrough(见 §5.3),怪物行为不变。

---

## 4. 被动 spec 格式(三 archetype)

被动 spec 存放位置:`mods/base-rules/passives/<id>.yaml`(与 `skills/` 平级,避免和主动技能混)。

```yaml
# ① 属性被动 —— 常驻 modifier
id: iron_skin
kind: passive
name: "铁壁"
description: "力量提升"
effect: stat_mod
modifiers: { 力量: 10 }              # 受 level 缩放(缩放方式见 §7)
```

```yaml
# ② 技能 patch 被动 —— 走合并管道
id: lingering_burn
kind: passive
name: "余烬"
description: "火球的灼烧多持续一回合"
effect: skill_patch
match: { skill: fireball }           # 命中条件:按 skill id,或按字段如 { damage.type: 法术 }
patch: { "debuff.data.remaining": +1 }   # 对已声明字段做 set/add;受 level 缩放
```

```yaml
# ③ 特殊效果被动 —— 逃生舱,挂战斗事件 JS
id: lifesteal_on_kill
kind: passive
name: "嗜血"
description: "击杀时回复生命"
effect: handler
handler: lifesteal_on_kill.js        # 对应 mods/base-rules/passives/lifesteal_on_kill.js
```

**archetype 边界**:`skill_patch` 只能动 **spec 已声明的字段**(伤害数值 / scaling / 类型、targeting 形状、debuff 时长层数、tag、改名、开关 enabled)。**无中生有的新行为**(额外弹射、命中回蓝、条件触发、结构性联动)= `handler` JS。

---

## 5. 技能 spec 合并管道(本 feature 架构核心)

### 5.1 插入点

`02_dispatch.js` 现状:

```js
var spec = Skill._toJs(rawSpec);
var ctx = Skill.context(event);
var results = Skill.resolveTargets(ctx, spec);   // 用 spec
var fn = Skill.effects[spec.effect];
fn(ctx, spec, results);                          // 用 spec
```

在 `_toJs` 之后、`resolveTargets` 之前**插一段**:

```js
var spec = Skill._toJs(rawSpec);
var ctx = Skill.context(event);
spec = Skill.resolveSpec(ctx, type, spec);       // ← 新增合并阶段
var results = Skill.resolveTargets(ctx, spec);
...
```

### 5.2 合并顺序(一次定死,防多源 patch 打架)

```
resolveSpec(ctx, baseId, baseSpec):
  spec = clone(baseSpec)
  spec = applyLevelScaling(spec, level)        # 本 feature 装(缩放钩子,曲线留口子)
  spec = applyNode(spec, node)                 # #7 预留槽,本 feature 空实现(node==null 直接返回)
  spec = applyPassivePatches(ctx, baseId, spec)# 本 feature 真实接入:施法者 owned passives 中 effect=skill_patch 且 match 命中的
  spec = applyEquipmentPatches(spec)           # Ch2 预留槽,本 feature 空实现
  spec = applySpecPatches(spec)                # #6 专精预留槽,本 feature 空实现
  return spec
```

按 priority 排序合并(base→level→node→被动→装备→专精)。helper **不知道有被动**,只执行被改过的 spec。这正是当初否决方案 b 要保的「防多源 conflict」。

`level` / `node` 来自施法者实例:在 `ctx.caster.Skillbook.known` 里找 `base == baseId` 的条目取 `node` / `level`;**找不到则 `node=null, level=1`**(怪物 / 临时技能)。

### 5.3 回归安全

- 施法者**无 Skillbook**(所有怪物)→ 无实例、无 owned passives → 所有 apply* 步骤空跑 → `resolveSpec` 返回 base spec 原样 → **行为与今天逐位一致**。
- 玩家持有但**无任何被动 / level 默认 / node null** → 同样 passthrough。
- 回归测试必须覆盖:现有所有技能(火球 / 横扫 / 治疗 / 普攻…)在「无被动」下 `resolveSpec` 前后输出一致。

---

## 6. 三条通路运行时

### 6.1 stat_mod(属性被动)

- 持有 / recalc 时注册**常驻 modifier**(`engine.addModifier`,`typeId: "passive"`,id 含被动 id + 实体 id),priority 排在 `derived`(300)**之前**,这样派生属性读到的是加成后的基础属性。
- **拥有即生效、无条件、不被压制**(母文档用户硬需求):不挂战斗状态、不可被禁用。
- 幂等:复用 `war_cry.js` 同构写法(注册前先 `removeModifier` 同 id),对 recalc / 持久化重载安全。
- `modifiers` 数值受 level 缩放(§7)。

### 6.2 skill_patch(技能 patch 被动)

- 不注册任何东西,**被动作为数据**存在 owned passives;由 §5 管道在施法时**主动拉取并应用**。
- `match` 求值:`{skill: <id>}` 比对 baseId;`{<字段路径>: <值>}` 比对 base spec 对应字段(如 `damage.type`)。
- `patch` 应用:对 spec 的字段路径做 set(标量)/ add(带 `+` 前缀的数值)/ 其它(改名 / 加 tag / 开关)。

### 6.3 handler(特殊效果被动)

- 新增**被动 handler 加载器**,仿 `buffs/` loader:扫 `mods/base-rules/passives/*.js`,注册其战斗事件订阅。
- 每个 handler 内部**守卫持有**:事件触发时检查相关实体的 `Skillbook.known` 是否含该被动 id(且非 disabled)。
- 可订阅已有战斗事件:`combat.unit_death`、`combat.damage_dealt`、`combat.damage_calc`、`combat.unit_action`、`combat.round_end`、`entity.hp_zero` 等。

---

## 7. level 轴 + 接缝

- **实例 `level` 字段**:主动被动统一,默认 1。
- **管道缩放(`applyLevelScaling`)**:本 feature 装一个**缩放钩子**。约定 spec 可声明哪些字段随 level 缩放(例:`level_scaling: { "damage.add": 2 }` 表示每级 +2),无声明则 level 不改 spec。**第一版钩子可极简**(线性 / 恒等),真曲线属内容。
- **人物升级 → 技能等级接缝**:角色升级事件(核实现有事件名,如 `character.level_up` / xp 涨级处)上留 hook 点,未来内容轮往里插 `charLevel → skillLevel` 映射(每技能不同曲线)。**本 feature 不写映射,level 跑默认值即可跑通**。
- **外部来源预留**:管道 `applyEquipmentPatches` 槽接受装备对 level 的加成(Ch2 接线)。

---

## 8. 前端被动 tab

- `SkillbookPanel.vue` 被动 tab(现渲染「暂未开放」):改为渲染持有被动列表——**名 / 图标 / 描述 / level / 效果摘要**。
- **只读**:无 equip 按钮、无出战计数(被动常驻)。
- snapshot 吐被动列表(每条含 `kind=passive` + 展示字段);主动 tab 不变。
- 遵循用户 UI 规范:**边框 2px+**(`feedback_thick_borders`);**只改这块面板内容,不动 4-panel 布局**(`project_color_ui_requirements`)。

---

## 9. demo 夹具

每条通路 1 个 demo 被动,通过 class 起始被动或 debug 播到玩家:

- **stat_mod**:`iron_skin`(+X 力量)→ 验证属性面板 / 派生强度变化。
- **skill_patch**:`lingering_burn`(火球灼烧 +1 回合)→ 验证施放火球时 debuff 时长被 patch。
- **handler**:`lifesteal_on_kill`(击杀回血)或简单概率追击 → 验证战斗事件触发 + 持有守卫。

demo 仅证「框架通」,非内容集;Ch1 内容轮会重做真被动。

---

## 10. 验收标准

**做到:**
- 三条通路各自 demo 被动在玩家身上生效:属性变(modifier)/ 技能 spec 被 patch / handler 触发。
- 被动「拥有即生效」、不占出战格、不进指令栏。
- 主动被动实例统一带 `level`,管道按 level 缩放(钩子在,默认值跑通)。
- `resolveSpec` 对无被动 / 无 Skillbook 施法者 passthrough,**现有技能行为零回归**(测试证明)。
- 前端被动 tab 渲染持有被动(只读)。
- debug 能给被动 / 设 level。
- `mvn test` 全绿、`npm run build` 通过。

**不做(本 feature 明确不碰):**
- 不设计真被动内容 / 真数值 / 真等级曲线。
- 不接装备来源(Ch2)、不接灵魂球进化 / node 变换(#7)、不接专精 patch(#6)。
- 不给怪物加被动内容、不动怪物 AI。
- node 变换、装备 patch、专精 patch 三个管道槽**空实现**(占位,不接线)。

---

## 11. 给实现(plan / Codex)的边界注记

- **修改边界**:`02_dispatch.js`(插 resolveSpec)、`00_skill_lib.js`(加 resolveSpec / applyPassivePatches / applyLevelScaling)、新增 `mods/base-rules/passives/`(spec + JS + loader)、`select.js`(起始被动播种 + 实例加 level)、`Skillbook` 组件 known 实例加 `level`、snapshot 吐被动 + kind、`SkillbookPanel.vue` 被动 tab、debug 端点、起始 demo 数据。
- **隐含产品偏好**:UI 边框 2px+;只改被动 tab 内容不动布局;demo 文案中文;空态(无被动)给「暂无被动」而非报错。
- **现有代码约束**:`resolveSpec` 必须回归安全(§5.3);属性被动 modifier 幂等(仿 `war_cry.js`);被动 handler loader 仿 `buffs/` loader;snapshot 实例最小、kind 后端查 spec 补。
- **指令生成 kind 感知**:`actions.js` 用 `known`(filter `equipped===true`)生成战斗指令——被动同住 `known` 后必须**只让主动进指令栏**(被动 `equipped` 忽略,绝不生成 command);最稳是显式按 `kind==active` 过滤,别只靠「被动没 equipped」侥幸。`03_skillbook.js` 的 equip/unequip 也应拒绝对被动操作。
- **验证方式**:后端单测(三通路 + resolveSpec 回归)、`mvn test`、`npm run build`、真人 verify 被动 tab + 战斗内效果。

---

## 12. 范围确认

本 feature = **纯地基,内容真空,机制完整**。后面 Ch1 内容轮填真被动、Ch2 装备插 level/给技能、#7 灵魂球进化被动、#6 专精 patch,各自往本 feature 预留的口子接线。
