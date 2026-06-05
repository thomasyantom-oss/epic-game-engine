# Codex 实现请求 — Feature #5 被动技能(框架)

你是资深 SDE,**按 plan 实现** Feature #5。

## 权威文档(按顺序读)
1. Plan: `docs/superpowers/plans/2026-06-04-feature5-passive-skills.md`
2. Spec: `docs/superpowers/specs/2026-06-04-feature5-passive-skills-design.md`
3. Review: `docs/superpowers/codex-runs/feature5/review-response.md`

## 铁律
- **plan 顶部的「⚠️ Review 修订(R1-R11)」段优先级最高**——task 正文与之冲突时,以 R 段为准。尤其:
  - **R1**:测试用 `rt.execute(...)` + 读 `store` 组件断言,**没有 `rt.eval`**。
  - **R2**:`cleave.js`/`cross_blast.js`/`piercing_ray.js` 也要接 `Skill.resolveSpec`。
  - **R3**:`PassiveStatModTest` 按 `DerivedStatsTest` 四参装配(`ModifierTypeRegistry`+`ModifierChainService`)。
  - **R4**:升级 hook 挂 `entity.level_up` + `entity.loaded` + `select.js` 新建处;属性被动注册也挂这三点。
  - **R5**:`combat.unit_death` 读 `killerId`(无 `attackerId`)。
  - **R8**:改 `CharacterFlowTest` 的"known 全 equipped"断言。
- **TDD**:每 task 先写失败测试 → 跑挂 → 最小实现 → 跑过。按 task 顺序 1→13。
- **不要 commit、不要碰 git**(由 Claude 提交)。沙箱里 git 有 dubious-ownership,正常,忽略。
- **不要改这些工作区残留**(与本 feature 无关):`AGENT.md`、`doc/`、`set-env.ps1`、`frontend/src/assets/`、`docs/superpowers/*/2026-05-2[78]-*`。
- 只动 plan「文件结构」列出的文件 + 必要的回归测试修正(如 R8 的 `CharacterFlowTest`)。

## 验证
- 后端:`cd backend && mvn test`(沙箱里 Maven 启动慢,**耐心等,别因慢就判失败**;可先 `-Dtest=<单类>` 跑当前 task 的测试,最后再全量)。最终全量必须绿。
- 前端:`cd frontend && npm run build` 必须成功。
- 把"现有技能零回归"当硬指标:`ResolveSpecTest` 的 passthrough、`CombatBugfixTest`、`SkillbookSnapshotTest` 原用例全绿。

## 报告
每个 task 实现完,简述:改了哪些文件、测试结果。遇到 plan 与真实代码不符(如签名/事件字段对不上),**停下说清楚**,别瞎猜硬写。全部完成后给一份总结(改动文件清单 + 最终 mvn test / npm build 结果)。
