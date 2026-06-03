# 下一轮 Prompt — Ch1 Feature #3「伤害类型/抗性」slice-2(数值平衡模拟器)

> `master`:后端 **147/147 绿**,golden 稳定。
> 已合并:F2(属性表/武器/技能/创建页)、V1 safe-authoring refactor、**#3 切片一:减伤地基/空跑框架**(`Skill.mitigate` 唯一收口已就位)。
> **本轮(2026-06-02)已产出:slice-2 的 spec v2 + 三份实现计划,待 Codex 执行。**

## 团队工作流(沿用)
- **Claude = Senior SDE**:design / plan / review。**仅 Claude APPROVE,Codex 才能 merge。** 不直接改 Codex 代码,只给署名 review。
- **用户 = PM + stakeholder + 真人 tester**。
- **Codex = 实现 SDE**:逐 task TDD、频繁提交;完成用 `requesting-code-review` 发 Claude。
- 记忆见 `~/.claude/.../memory/team_roles_codex_workflow.md`。

## slice-2 的方向(已脑暴定稿,见 spec)
slice-2 主题"配数值/做手感",但**先造一台 PM 可用的数值仪表盘**(无头批量战斗模拟器),再用它配数。方法论已与 PM 聊定:**TTK 反解属性 / 在比值空间设计 / 只调几个旋钮 / 护甲曲线 EHP 线性天然防爆、三抗必须 cap / 先造仪器再调数**。"将将击败"= `win_rate 50-60% + 胜利中位剩血 <20%`,确定性回合制下靠 `damage_variance` 方差源供能。
- **Spec(PM 已 review,纳入 Codex review):** `docs/superpowers/specs/2026-06-02-combat-sim-balance-instrument-design.md`。四个 PM use case + 三报告(A/B/C)+五曲线当验收闸;五条实现口径定死(timeout 单列不判负 / baseline 比值法 / policy 合法 command / 复用真 ModifierChain / damage 走真实路径 instrumentation)。

## 三份实现计划(本轮已写完,待 Codex 执行)
按 1→2→3 顺序(2/3 有前置依赖):
1. **Plan 1 仿真核心 → 报告 A**:`docs/superpowers/plans/2026-06-02-combat-sim-core.md`(6 task)。FightRunner / Scripted+Heuristic policy / BatchMetrics 三桶 / CombatantBuilder(复用真创建·派生·encounter)/ 端到端 / `early_class_health_check`。
2. **Plan 2 解释力 + 方差源(UC4+§7)**:`docs/superpowers/plans/2026-06-02-combat-sim-explain-variance.md`(5 task)。FightTrace+DamageTap / `combat.mitigation` 增量事件 / CombatTuning+`damage_variance`(种子) / BatchTrace。
3. **Plan 3 护甲曲线+cap+sweep+指数+报告 B/C(UC2+UC3+§2)**:`docs/superpowers/plans/2026-06-02-combat-sim-sweep-reports.md`(5 task)。护甲曲线/抗性 cap(旋钮驱动默认 FLAT)/ Sweep / sources 落地 / BaselineIndex offense·defense / 报告 B+曲线1/2/3 / 报告 C。

**贯穿铁律(计划里已钉死):** 薄层贴真引擎、零自写战斗数学;`combat.mitigation` 无监听者、`tuning` 未绑定时 `typeof` 守卫、护甲曲线**默认 FLAT + cap75 不触界 → 147 测试与真游戏数值全不变**;每 task 全量 `mvn test` 回归。**把 CombatTuning 默认翻成 CURVE + 重生 golden = PM 选定 K/cap 后的独立收尾提交,不在三计划内。**

## 起手(下一轮)
**派 Codex 按 Plan 1 开干**(逐 task TDD,完成发 Claude review,仅 Claude APPROVE 才 merge)。Claude 退回 Senior SDE review 位。Plan 1 落地后 PM 即可看报告 A(各职业前期体检)。

## 计划里记录的两处诚实缺口(Codex 实现时按可执行同构指令补)
- 曲线 4 `resist_cap_vs_ehp`:照 `ArmorKSweepReport` 复制改 setter(`tuning.setResistBounds`)成 `ResistCapSweepReport` + 一个 test。
- equipment 真实物品源:`sim_support.js` 已留注释占位;报告 C 用 stat modifier 已满足验收,equipment-真实物品留后续。

## 扩展位(spec §9,不需改仪器核心)
search policy、矩阵跑、power_index、LLM=scripted 喂线(LLM 当假设生成器、模拟器当裁判)、crit。

## Backlog(docs/backlog.md)
- BL-013 退役 `damage_calc.js`(现 inert)。BL-014 元素抗进装备池 + 元素注册表。BL-015 角色面板显示重构(抗性块 + 伤害数字按类型上色)。

## 后续 features(已存档,非本轮)
- 站位/宠物战斗(lane 前线 + 短中长射程 + 站位 buff + 穿透):`docs/future-positional-pets-combat-running-notes.md`。
- 控制/豁免/韧性(挂 `buff.apply`,不碰减伤地基):同上 future note。
