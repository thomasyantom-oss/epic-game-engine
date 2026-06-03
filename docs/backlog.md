# Backlog

格式：每条需求独立区块，包含描述、可行性分析（待填）、状态标签。

状态：`[backlog]` `[analysis]` `[planned]` `[in-progress]` `[done]` `[rejected]`

---

## UI / 前端

### BL-001 技能 hover tooltip 动态计算值
**状态：** `[backlog]`

**需求描述：**
技能按钮 hover 时显示 tooltip，内容由 mod 定义，但可以包含动态计算值。
例如：技能倍率 × 当前角色攻击力 = 预期伤害，实时带入角色属性和技能加成后显示。

当前行为：技能描述是静态文本（skill YAML 里写死的 `description` 字段）。

期望行为：
- tooltip 内容由 mod 控制（可以是静态文本，也可以是带占位符/计算表达式的模板）
- 前端拿到角色当前属性 + 技能元数据，能渲染出计算后的值
- 类似「顺劈斩：对前排造成 **42 × 1.2 = 50** 点物理伤害」

**可行性分析：** _待分析_
- 方案 A：后端在 snapshot 里预计算好 tooltip 内容字符串，前端直接渲染（简单，但每帧 snapshot 都要算）
- 方案 B：前端拿到 `damage_formula`（字段 + 倍率），客户端算（公式简单可行，但复杂公式难处理）
- 方案 C：专用 `/api/skill/preview?skillId=X&entityId=Y` 端点按需请求（延迟高，hover 体验差）

**依赖：** 需要 skill YAML 支持新的 `tooltip_template` 或 `formula` 字段定义格式。

---

## 战斗系统

### BL-002 战斗回合后端超时自动结算
**状态：** `[backlog]`

**需求描述：**
当前倒计时完全在前端，前端关闭或断连后战斗会永久挂起。需要后端维护战斗时钟，防止玩家利用关闭前端/重启服务器跳过战斗。

**期望行为：**
- 每次进入指令阶段时，后端记录 `roundStartTime` 时间戳
- 任何请求进来（GET /api/snapshot 或 POST /api/action）时，先检查是否超时
- 超时则循环自动结算（所有玩家角色自动攻击最近目标）直到战斗结束或回到正常回合
- 服务器重启后，第一次 snapshot 请求触发检查，离线期间的回合一次性补算完
- Session 长时间不活跃（15分钟）→ 战斗视为逃跑处理

**设计要点：**
- 零轮询：后端完全被动，只有请求进来才触发检查
- 补算是循环：`while (战斗未结束 && 有超时回合) { 自动结算一回合 }`，毫秒级
- `roundStartTime` 需要持久化（存入 CombatState 组件），服务器重启后可读

**可行性分析：** _待分析_
- 需要 `combat_flow.js` 在回合开始时写 `roundStartTime`
- 需要 `SnapshotService` 或 action handler 入口处加超时检查逻辑
- 自动结算复用现有 `auto_attack` 逻辑

---

## 地图 / 世界

_（待添加）_

---

## 引擎 / 后端

### BL-003 统一 bespoke 技能事件输出形状
**状态：** `[backlog]`

**需求描述：**
当前数据驱动技能与 bespoke 技能的 combat event 输出形状不统一：部分技能走 `engine.combatEvent`，部分直接 push `CombatEvents.queue`；`hp_change` 同时存在 flat 与 nested `data` 两种格式。后续应逐步统一，减少前端和测试适配成本。

**可行性分析：**
- V1 只适合先做 helper 和小范围迁移，保持 golden 不变。
- 大范围统一会影响 golden、CombatLog、前端动画播放，需要单独审查。
- 可以先保留兼容读取，再逐步把输出统一到单一 shape。

---

### BL-004 Buff round_end 扫描合并与生命周期 helper
**状态：** `[backlog]`

**需求描述：**
`burning`、`poison`、`cursed`、`defending` 都在 `combat.round_end` 独立扫描 combatants。当前规模下性能问题不明显，但未来 buff 数量增加后会产生重复扫描和重复 log/effect/animation 拼装。

**可行性分析：**
- 回合制游戏且动画展示时间较长，当前优先级低。
- 后续可考虑增加 `Buff.tickEach(combatId, buffId, fn)` 或集中 round_end dispatcher。
- 需要注意不同 buff 的 tick 顺序、移除时机和死亡级联。

---

### BL-005 Hot reload 后重新绑定实体 modifier 生命周期
**状态：** `[backlog]`

**需求描述：**
`HotReloader` 重新加载 JS handler 时会清空并重建 EventBus，但已有实体上的 Modifier 可能仍保留旧 JS 闭包。修改 derived/equipment/buff modifier 逻辑后，可能需要重启才能完全生效。

**可行性分析：**
- 可在 reload 后对持久实体重新触发 `entity.loaded` 或新增 `entity.scripts_reloaded`。
- 必须避免重复注册 modifier 导致膨胀；当前 addModifier id 幂等有帮助。
- 需要针对已有战斗中实体、临时敌人和持久角色分别定义策略。

---

### BL-006 Component/base 快照深拷贝
**状态：** `[backlog]`

**需求描述：**
`Component.copy()` 当前是浅拷贝。未来如果 base 快照组件里包含 List/Map 等嵌套结构，运行时修改可能污染 baseState，导致 recalculate 无法恢复干净状态。

**可行性分析：**
- 当前主要数值组件风险较低。
- 如果 Inventory/Skills/EquipmentSlots 等结构性组件进入 base，风险会上升。
- 可用 Jackson 或手写有限 deep copy，但要避免引入不必要复杂度。

---

### BL-007 `setBaseSelective` 危险 API 重命名或收口
**状态：** `[backlog]`

**需求描述：**
`setBaseSelective` 是“整体替换 base 快照”的底层 API，不是“更新单个组件”。历史 bug 已证明误用会导致属性膨胀。当前保留该能力，但后续应考虑重命名、文档化或限制生成代码使用。

**可行性分析：**
- V1 可先加测试和注释，不删除 API。
- 后续可新增 `replaceBaseComponents`，逐步替换现有调用，再把旧名标记 deprecated。

---

## 内容 / Mod

### BL-008 后端技能 authoring schema / manifest
**状态：** `[backlog]`

**需求描述：**
为技能建立更明确的 authoring manifest，声明普通数据驱动技能与 bespoke 技能的必需字段、触发、条件、cost、cooldown、targeting、effect、duration、stacking、cleanup、UI 显示数据和测试 fixture。

**可行性分析：**
- 不要求所有技能纯 YAML。
- 可以先作为测试 fixture/schema 文档存在，再逐步让验证测试读取。
- 需要和未来 skill upgrade / cooldown / resource 系统一起演进。

---

### BL-009 自定义装备行为的 canonical pattern
**状态：** `[backlog]`

**需求描述：**
当前装备主要是静态 stat modifier。未来装备可能有触发型、状态型、战斗内行为型逻辑，需要一套 canonical pattern：装备元数据、装备槽生命周期、modifier 注册/移除、触发事件、cleanup、持久化和测试 fixture。

**可行性分析：**
- 普通装备继续走 `items.yaml` 全限定 stat key。
- 自定义装备可采用 `equipment/<itemId>.js` 或 handler registry，但必须通过统一 equip lifecycle 注册/撤销。
- 需要避免直接裸改组件绕过 `updateBase`、modifier chain 和 persistence。

---

### BL-010 大量 buff 下的性能与表现策略
**状态：** `[backlog]`

**需求描述：**
未来如果每个单位同时挂大量 buff/debuff，round_end tick、UI buff 展示、动画队列长度、CombatLog 规模都可能膨胀。当前不急，但需要作为内容规模扩大前的议题。

**可行性分析：**
- 当前回合制节奏下优先级低。
- 可在后续通过 buff batching、合并日志、状态图标折叠、tick 索引优化。

---

### BL-011 ContentAuthoringValidationTest:animation 需校验非空
**状态：** `[backlog]` ⚠️ **暂冻结(与 slice-2 模拟器计划冲突)**:Plan 3 新增的测试技能 `skills/fireball_fixed.yaml` 用 `animation: []`。本条上线前需先让该技能用非空动画、或把测试专用技能排除出 content 校验,否则两边互相搞红。待模拟器三计划落地后再做。
**来源：** V1 refactor code review(Claude, Senior SDE)

**需求描述：**
当前数据驱动技能的 animation 校验只检查 `containsKey("animation")`,空数组 `animation: []` 仍会通过。但空动画和缺失动画一样是坏内容(poison_dart 当时是整块缺失,空数组同样无飞射/无伤害数字)。

**可行性分析：**
- 小改:把校验从"键存在"升级为"键存在且为非空 list"。
- 注意 bespoke 自带 JS 发动画的技能不受此规则约束(它们无 `effect`,走另一分支)。

---

### BL-012 清理 01_effects.js 的 `heal` dead registration
**状态：** `[done]`(2026-06-02,Claude)

**需求描述：**
`01_effects.js` 注册了 `heal` effect,但 `heal.yaml` 没有 `effect:` 字段(走 bespoke `heal.js`),导致该注册无人使用。要么让某技能改用 `effect: heal`、要么删除这条 dead registration,避免后续作者误以为 heal 是数据驱动。

**处理：**
- grep 确认:无技能声明 `effect: heal`;`SkillFidelityTest`/`golden/heal.json` 测的是 bespoke `heal.js`,不依赖该注册。
- 删除 `01_effects.js` 的 `registerEffect("heal", ...)` 整块。全量 147/147 绿。

---

### BL-013 彻底删除 / 退役 `damage_calc.js`
**状态：** `[backlog]` ⚠️ **暂冻结(与 slice-2 模拟器计划冲突)**:Codex 三计划新增的纯 runtime 测试 harness 仍在 setUp 显式 load `damage_calc.js`。在途期间删除会断这些 harness。待三计划落地后,连同所有 harness 的 load 数组一并清。
**来源：** Feature #3 切片一 plan review

**需求描述：**
#3 切片一让 basic_attack 改走 `Skill.mitigate`,`combat.damage_calc` 不再被触发,`damage_calc.js` 变 inert。但本轮**保留不删**——多处测试 harness 仍在 setUp 显式 load 它,删除会断 setUp。

**可行性分析：**
- 删除前需同时 grep:YAML(`via_damage_calc`)、JS、**所有测试 harness 的 load 数组**、事件触发路径。
- 清理时把仍 load 它的测试 harness 一并改掉,或确认 inert 无副作用再移除。

---

### BL-014 元素抗性进装备词缀池 + 元素注册表
**状态：** `[backlog]`
**来源：** Feature #3 切片一(元素轴已建框架,本轮空跑)

**需求描述：**
当前元素抗性是"最高贵"稀有属性,不进常规装备词缀池;#3 切片一的 validation 只允许 `Resistances.{物理,法术,精神}`。以后若要让元素抗(火/冰…)也能从装备/内容来,需要:元素注册表(合法元素集合,纯数据)、放开 validation 的 element key、装备池规则。

**可行性分析：**
- `registerEquipmentModifier` 已能解析 `Resistances.火` 全限定 key,但需组件含该 key(目前只 3 类)。
- 元素抗仍应保持"稀有",别变成人人必堆的常规词缀。

---

### BL-015 角色面板显示重构(含抗性块 + 伤害数字按类型上色)
**状态：** `[backlog]`
**来源：** Feature #3 切片一 UI 降级(Codex review #7)

**需求描述：**
#3 切片一只做后端减伤地基,**不碰前端/snapshot**。角色面板展示抗性(物/法/精,全 0 折叠)+ 伤害数字按伤害类型上色(走 colorMap),并入之后的"角色面板显示重构"——需要足够的面板骨架,且在装备系统铺开、真有抗性数值之后做才有意义。

**可行性分析：**
- 依赖:面板骨架 + 装备/抗性数值落地。
- 伤害数字 type 字段可在那时一起加进 combat event。

---
