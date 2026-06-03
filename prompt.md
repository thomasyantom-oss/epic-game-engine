# 下一轮 Prompt — Ch1 Feature #3「伤害类型/抗性」slice-2(收尾:抗性内容 + tester)

> `master`:后端绿。已合并:F2、V1 safe-authoring、**#3 切片一(减伤地基)**、BL-012(删 dead heal)。
> 本轮(2026-06-03)产出:**slice-2 数值模拟器(Codex 实现)+ Track A 锁定公式落成真默认(已 APPROVE,待 Codex 圈准 commit 合)**。

## 团队工作流(沿用)
- **Claude = Senior SDE**:design / plan / review。**仅 Claude APPROVE,Codex 才能 merge。** 不直接改 Codex 代码,只给署名 review。
- **用户 = PM + stakeholder + 真人 tester**。
- **Codex = 实现 SDE**:逐 task TDD、频繁提交;完成发 `requesting-code-review`。
- 记忆见 `~/.claude/.../memory/`(team_roles / 模拟器解读口径 / 核心公式 / 怪物种族子种族 等)。

## slice-2 现状:护甲/武器那半已闭环
- **数值模拟器已造好**(Codex):`com.epic.engine.sim`,真引擎跑批,产 `balance-model.csv` / `balance-check.csv`(REST:`/api/sim/reports/...`)。**forest_goblin = 承伤/护甲基准压测怪,不是实战验收**(口径见 memory `project-sim-balance-interpretation`)。
- **锁定核心公式(Track A 已 APPROVE)**——见 memory `project-core-combat-formulas`:
  - 护甲减伤 PoE 式 `ceil(raw²/(armor+raw))`(K=1),已成真 `mitigate` 默认(FLAT 仅留 sweep 对照)。
  - 武器伤害 sqrt 式 `ceil(base*(1+√(attr*weaponMult)/10))`,已进真 `derived_stats`。
  - 抗性 cap/floor 75/−50 真默认;basic_attack golden 有意重生 + 6 测试精确重算 + B1 正确性测试。
- ⚠️ **Codex 合并时圈准 commit**:进 Track A 真实路径 + 6 测试 + golden + 整个 sim 包;**别扫** `AGENT.md`/`doc/`/`frontend/src/assets/`/`set-env.ps1`/`docs/superpowers/*/2026-05-2[78]-*`。

## #3 收尾还剩(抗性轴 + tester)
护甲/武器闭环了,但"穿法抗戒指打火法怪明显抗"的可感手感还差:
1. **装备三抗词缀**:`items.yaml` 加 flat % 三抗(存%/显示%,零公式;负抗=易伤,见 memory `project-monster-race-subrace` §7)。
2. **怪抗性内容**:给部分怪配 flat % 抗。**它的最终结构家 = 怪物种族/子种族系统**(见下),但收尾这轮可先手配少量怪抗性把手感做出来,不必等整套种族系统。
3. **tester 脚手架**:抗性装进 debug 背包 + `mitigation_resist_test`/`mitigation_high_resist_test` 挂地图 POI,让 PM 真进游戏戳。
4. **UI 抗性显示** = BL-015(面板重构,装备铺开后做)。

## 已存档的前瞻 design(待排期,非本轮)
- **怪物种族/子种族/随机 modifier**:`docs/superpowers/specs/2026-06-03-monster-race-subrace-design.md`。怪=种族(main schema)+子种族(sub schema)+随机 roll modifier(自带 p(level)、适用范围)+正交 tag(灵魂/宝宝)。复用引擎 schema/ModifierChain/TagIndex。落地时替模拟器脚手架成长 + 安置 #3 怪物抗性内容 + 喂宠物初始基调。**真做时另起 brainstorm→spec→plan。**

## 起手(下一轮)
开 #3 收尾这轮:**装备三抗词缀 + 少量怪抗性 + tester 脚手架**,让 PM 第一次真上手玩测减伤+抗性手感。要 brainstorm 词缀池/数值再派 Codex。或:若 PM 想先推怪物种族系统(把怪抗性一步到位),则先 brainstorm 那个 design 的实现 slice。

## Backlog(docs/backlog.md)
- BL-013 退役 `damage_calc.js`:**注意现已复用 `Skill.mitigate`(非 inert),退役条件变了**;且 sim harness 仍 load 它,删前圈清。
- BL-011 animation 非空校验:与 `fireball_fixed.yaml(animation:[])` 冲突,先协调。
- BL-014 元素抗进装备池 + 元素注册表。BL-015 角色面板显示重构(抗性块 + 伤害数字按类型上色)。

## 后续 features(已存档,非本轮)
- 站位/宠物战斗:`docs/future-positional-pets-combat-running-notes.md`。
- 控制/豁免/韧性(挂 `buff.apply`,不碰减伤地基):同上。
- 怪物种族/子种族(见上,前瞻 design 已存档)。
