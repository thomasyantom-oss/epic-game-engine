# Codex 清单 — 把 slice-2 锁定公式落成真游戏默认 + golden + 测试(Track A)

> 背景:slice-2 模拟跑下来,**护甲减伤(PoE 式)与武器伤害(sqrt 式)已锁定**(口径见 memory `project-core-combat-formulas`)。当前这俩主要活在模拟器;本清单把它们变成**真游戏默认行为**,有意重生 golden,并补公式正确性测试(我上轮 review 的 B1)。
> 作者:Claude (Senior SDE)。完成发 `requesting-code-review`,仅我 APPROVE 方可 merge。

## 1. 真武器伤害公式 → sqrt
- 文件:`mods/base-rules/handlers/character/derived_stats.js`
- 现状(线性):`attack = ceil(B * (1 + wAttrVal*weaponMult/100))`
- 改成(sqrt):`attack = ceil(B * (1 + sqrt(wAttrVal*weaponMult) / 10))`
  - `B` = 武器 base(无武器走 `PLACEHOLDER_WEAPON_BASE` 分支,同步改公式);`wAttrVal` = 武器绑定属性值;`weaponMult` 同现有(力量×2、余×1)。
- 影响:所有攻击力变 → 武器/属性相关 golden + 断言重生(见 §4)。

## 2. 真护甲减伤 → PoE 曲线默认(K=1)
- 文件:`mods/base-rules/handlers/skill/00_skill_lib.js`(`Skill.mitigate` 普攻分支)
- 现状:默认 FLAT(`raw-armor`);曲线是 WoW 式 `armor/(armor+K)` 且 gated 在 tuning 后。
- 改成:**默认 PoE 式** `fin = ceil(raw * (1 - armor/(armor + K*raw)))`,`K` 默认 **1**。
  - `tuning` 仍可覆盖 K / 切 FLAT 做 sweep 对照;但**未绑 tuning(真游戏)→ PoE K=1**(即把默认从 FLAT 翻成 PoE)。
  - `CombatTuning` 的 armorK 默认值也设 1,FLAT 模式保留只为 sweep 基线对照。
- 影响:所有普攻伤害变 → 战斗 golden + 硬编码数字断言重生(例:10atk vs 2def,旧 FLAT=8 → 新 PoE=`ceil(10*(1-2/12))`=9)。

## 3. 抗性 cap/floor → 真默认
- 同文件技能分支:`cap 75 / floor -50` 钳制改成**始终生效**(不再 gated 在 tuning 绑定后)。当前无内容超界 → 数值不变,但成为真机制单一事实源。

## 4. 重生 golden(有意)
- 跑受影响 golden(`SkillFidelityTest` / damage 相关 + `NewCombatIntegrationTest` / `MitigationTest` 等硬编码断言)。
- **逐个核对几个手算点**(见 §5),确认新数字是公式产物、不是 bug,再更新基线。别盲目刷新 golden 掩盖错误。

## 5. 补公式正确性测试(B1,必须)
- 护甲 PoE:`raw=10, armor=10, K=1 → ceil(10*(1-10/20))=5`;`armor=raw` 时约减半 ✓。再取 `raw=10,armor=2 → 9` 钉一个点。
- 武器 sqrt:手算一个点钉死(如 `B=20, 力量值=x, mult=2 → ceil(20*(1+sqrt(2x)/10))`)。
- `damage_variance`:variance=0 恒等;>0 有波动;**同 seed 复现**(我 Plan 2 的 `VarianceTest` 现成,直接拿)。

## 6. 清理 dead code(B2 残留)
- `BatchTrace` / `BatchRunner.runSimulationWithTrace` 目前**无人调用**(grep 证实)。要么接进一个 explain 报告(把 `damage_by_skill/kill_source/平均死亡回合` 出成 CSV,真正交付 UC4),要么删掉,别留死代码。

## 验收
- `mvn test` 全绿(golden 已**有意**重生)。
- 新增测试覆盖护甲 PoE / 武器 sqrt / 方差三条。
- 真游戏跑一场普攻,数字符合 PoE 曲线(debug/log 核对一个点)。

## 注意
- commit **圈准范围**:工作树里有无关 untracked(`AGENT.md`、`doc/`、`frontend/src/assets/`、`set-env.ps1`),别扫进去。
