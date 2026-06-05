role: 你是这个仓库的实现 SDE(headless)。按已批准的 plan 实现 **Feature #7 阶段 1(后端机制闭环)**。

## 唯一权威 plan(逐字遵守,冲突以它为准)
`docs/superpowers/plans/2026-06-04-feature7-skill-tree.md` —— 已纳两轮 Codex review。
配套 spec:`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md`。

## 本轮范围:仅 **阶段 1 = Task A1 → A9**(后端)
- **不做阶段 2(前端 B1–B3)**。本轮只到"`cd backend && mvn test` 全量绿 + 后端机制闭环"。
- 严格按 plan 的 Task 顺序与 TDD 步骤(先写失败测试→确认失败→实现→确认通过)。**A1 是地基必须最先**(统一 `Passive.ownedSpecs` + stat_mod modifier 清理机制)。
- 遵守 plan「5 件套」的全部边界:**别碰** `ModifierChain.java` 引擎本体 / golden(`SkillFidelityTest` 不重生成)/ sim 包 / 战斗动画伤害管线 / `Specialization` 不可洗逻辑。
- 守住 plan 的验收①(含"不做什么")与 6 个 Codex 阻塞项的解法(尤其:洗点 stat_mod 不残留、`ownedSpecs` 扁平契约、进化索引走 `talents/index.yaml` manifest、`damage.add` 增量=18、`place_orb` 拒未学技能、skillbook 回填进化名)。

## 工作方式
- 工作目录 `/c/workplace/epic`。沙箱 workspace-write。
- 验证:`cd backend && mvn test`(全量须绿;新增 `TalentTreeTest`/`TalentTreeSnapshotTest`)。本轮不跑前端。
- **不要自己 commit、不要 git add/push**(Claude 负责提交)。
- 若某处与 plan 不符需偏离结构性决定,**在回话里显式标注**(改了什么、为什么),等 review 判,别默默改。
- 允许就地微调 plan 没预见的小适配,但记录在回话。

## 回话产出(给 Claude review 用)
1. 逐 Task(A1–A9)完成情况 + 每个新增/修改文件清单(全路径)。
2. `mvn test` 最终结果(通过数 / 是否全绿;若有跳过或新增测试数,点明)。
3. 所有偏离 plan 的决定 + 理由。
4. 阶段 1 自检验收①勾稽:派生点数(含 spent>total 拒)/ 统一被动来源(skill_patch+handler 都生效)/ 洗点 stat_mod 不残留 / 追溯重推 override / respec 保进化 / skillbook 显示进化名 / load 往返。

开始 A1。
