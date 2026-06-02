# 下一轮 Prompt — Ch1 Feature #3「伤害类型/抗性」slice-2(配数值/做手感)

> `master` 已推 origin。后端 **147/147 绿**,golden 稳定。
> 已合并:Feature #2(属性表/武器/技能/创建页 + 两轮实测修复)、V1 safe-authoring refactor、**#3 切片一:减伤地基/空跑框架**。

## 团队工作流(沿用)
- **Claude = Senior SDE**:design / plan / review。**仅 Claude APPROVE,Codex 才能 merge。** 不直接改 Codex 代码,只给署名 review。
- **用户 = PM + stakeholder + 真人 tester**。
- **Codex = 实现 SDE**:逐 task TDD、频繁提交;完成用 `requesting-code-review` 发 Claude。
- 记忆见 `~/.claude/.../memory/team_roles_codex_workflow.md`。

## #3 现状:切片一已落地(空跑)
减伤已收口到 `Skill.mitigate`(`00_skill_lib.js`):**普攻→护甲(flat,=旧 damage_calc 逐位)**;**技能→三抗%(物/法/精)+ 元素独立乘区**;**DoT→ignoreDefend(防御不影响 DoT)+ 吃类型抗**。`Resistances{物,法,精}` 组件:schema 默认 0 / 装备全限定 key / encounter 配置;`derived_stats` 不写(红线)。`damage_calc.js` 保留 inert。**当前抗性/元素全 0 → 数值与旧版一致(空跑)。**
- 完整设计基调:`docs/feature3-damage-types-running-notes.md`。
- slice-1 spec/plan:`docs/superpowers/specs/2026-06-01-feature3-damage-mitigation-foundation-design.md` / `docs/superpowers/plans/2026-06-02-feature3-damage-mitigation-foundation.md`。

## 下一步:#3 slice-2「配数值 / 做手感」(PM 实测的主战场)
这是真正能上手玩测的一轮。要 brainstorm + 定数值,大致包含:
- **护甲曲线**:把普攻减伤从 flat 换成曲线 `护甲/(护甲+K)`,定 K(随等级/区域)。
- **抗性 cap/floor**:cap 75% / 负抗=易伤 下限。
- **真实抗性数值落地**:给装备 roll 三抗(`items.yaml` 加抗性词缀)、给部分敌人 encounter 配抗性,做出"穿法抗戒指打火法怪明显抗"的可感差异。
- **tester 脚手架**(让 PM 能进游戏验证):debug 抗性装备进初始背包 + 把 `mitigation_resist_test` encounter 挂到地图 POI;伤害数字已在战斗日志可见。
- **UI 抗性显示** = BL-015(并入"角色面板显示重构",要面板骨架 + 装备铺开后做)。

## Backlog(新增,见 docs/backlog.md)
- BL-013 退役 `damage_calc.js`(现 inert);顺带清 `computeDamage` 的 dead `via_damage_calc` 分支。
- BL-014 元素抗进装备池 + 元素注册表 + 放开 validation 的 element key。
- BL-015 角色面板显示重构(抗性块 + 伤害数字按类型上色)。

## 后续 features(已存档,非本轮)
- 站位 / 宠物战斗(lane 前线 + 短中长射程 + 站位 buff + 穿透):`docs/future-positional-pets-combat-running-notes.md`。
- 控制 / 豁免 / 韧性(挂 `buff.apply`,不碰减伤地基):同上 future note。

## 起手
开 #3 slice-2 先 brainstorm 数值/手感(数值是大议题,PM 有感觉但无设计经验,慢慢一条一条聊),再 writing-plans 派 Codex。或先做 tester 脚手架让 PM 上手戳一下空跑管线。
