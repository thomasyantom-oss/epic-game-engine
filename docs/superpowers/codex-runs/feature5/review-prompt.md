# Codex Review Request — Feature #5 被动技能 实现 plan

你是资深 SDE,review 一份实现 plan(**只 review,别改任何文件**)。

## 要 review 的文档
- Plan: `docs/superpowers/plans/2026-06-04-feature5-passive-skills.md`
- Spec: `docs/superpowers/specs/2026-06-04-feature5-passive-skills-design.md`

## 背景
单机回合制 RPG,Java 21 + Spring Boot + GraalJS mod(`mods/base-rules`)+ Vue3 前端。#5 = 给技能加被动子类 + 技能 spec 合并管道。只搭框架不做内容。

## 重点核实(plan 里我标了"核实"的接缝,请逐一去代码里确认真名/真签名,指出 plan 哪里写错了)
1. **`ScriptRuntime` 求值入口**:plan 测试用了 `rt.eval("...")`。核实 `backend/.../script/ScriptRuntime.java` 实际暴露的 JS 求值方法名/签名(可能不是 `eval`)。给出正确调用法。
2. **`combat.unit_death` 事件字段**:plan 的 `lifesteal_on_kill.js` 读 `killerId`/`attackerId`。核实 `handlers/combat/death_check.js` 和 `combat_events.js` fire `combat.unit_death` 时实际带哪些字段,击杀者怎么拿。
3. **角色升级事件入口**:plan Task 11 要在"人物 level_up / ready"处挂 `applySkillLevelCurve`。核实 `handlers/character/*.js` 实际的升级/重算入口事件名与文件。
4. **`SkillbookActionsTest` 现有结构**:plan Task 6 说"按现有 helper 补全测试体"。核实该测试类实际怎么构造 player+Skillbook、怎么触发 action,给出能直接用的最小测试骨架。
5. **ModifierChain API**:plan 用 `engine.addModifier/removeModifier` + `engine.setBase` + `engine.recalculate`。核实这些在 `ScriptRuntime`/引擎里真实存在且签名一致(参照 `derived_stats.js`、`war_cry.js`、`select.js` 用法)。`engine.recalculate` 真名是什么?
6. **行号锚点**:plan 列的 `WorldSnapshot.java:61`、`ScriptRuntime.java:161`、`SnapshotService.java:116`、`select.js:108-126`、`actions.js:73-77` 是否准确。

## 也请评判
7. **回归安全**:`02_dispatch.js` 插入 `resolveSpec`,对无 Skillbook 施法者 passthrough。这个 clone(`JSON.parse(JSON.stringify)`)+ 空槽设计真能保证现有技能零行为变化吗?有没有遗漏(如 `via_damage_calc` 路径、bespoke JS 技能不走 dispatch 的 effect 分支)?
8. **mod 加载顺序**:`passives/*.js`(handler 被动)在触发时依赖全局 `Passive`(`handlers/skill/04_passive_lib.js`)和 `Skill`。`ModuleLoader` 按 `{handlers, skills, buffs, passives}` 目录序加载——`passives/*.js` 执行(注册 engine.on)时 `Passive` 一定已定义吗?有无加载序风险?
9. **typeId 优先级**:新增 `passive` typeId base_priority=70,目标是"属性被动改 PrimaryStats 在 derived(300) 之前跑"。70<300 够吗?和 `level(90)`/`equipment(50)`/`buff(10)` 的相对序有无问题?
10. **统一 known 列表(主动+被动同住)** 这个数据决策有没有踩到现有遍历 `known` 的地方(除了 plan 已点的 actions.js / 03_skillbook.js / ui/skillbook.js)?全局 grep `getComponent("Skillbook")` / `.get("known")` 看还有谁遍历、会不会被被动条目搞坏。
11. **TDD 顺序 / task 切分** 合理吗?有没有哪个 task 的测试在它自己阶段不可能转绿(plan Task 5 已自承需 Task 8 才整体绿——这种跨 task 依赖还有别的吗)?

## 输出格式
按"阻塞问题 / 应修问题 / 建议"三档列。每条给:问题、涉及文件:行、具体改法。最后给一句"plan 是否可交付实现(改完上述后)"的判断。
