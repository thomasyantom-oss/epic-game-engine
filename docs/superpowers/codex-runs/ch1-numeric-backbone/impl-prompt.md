你是资深 SDE,按实现 plan **逐 task 实现** Ch1 数值骨架。

## 权威 plan(逐字照做)
`docs/superpowers/plans/2026-06-05-ch1-content-numeric-backbone.md`
配套 spec(背景):`docs/superpowers/specs/2026-06-05-ch1-content-numeric-backbone-design.md`

## 工作方式
- **严格按 plan 的 Task 1 → Task 6 顺序**,每个 Task 内按 Step 走(TDD:先写失败测试 → 跑确认失败 → 实现 → 跑确认通过 → commit)。
- plan 里给的代码片段/测试是**权威**,逐字落地;若与现网代码有出入(行号漂移等),以语义为准,但不要偏离 plan 的设计。
- **Task 7(前端 npm build + 手动 verify)不要做** —— 前端 build 由人在沙箱外验,你只做 Task 1–6(后端 + JS + YAML + Java 测试)。
- 每个 Task 结束按 plan 的 commit step 提交(commit message 用 plan 里给的)。**只 git add plan 中该 Task 列出的文件,别扫工作区残留**(`AGENT.md`/`doc/`/`set-env.ps1`/`frontend/src/assets/` 等 untracked 是历史残留,绝不提交)。
- 每个 Task 的实现步后,跑该 Task 指定的 `mvn test -Dtest=...`;Task 6 末尾跑全量 `cd backend && mvn test` 确认全绿。
- 测试用现有 idiom(`backend/src/test/.../character/TalentTreeTest.java` 是范本)。API 已核实:`engine.createEntity`(只 new 不入 store,需 `store.add`)、`engine.newComponent/newList/newMap/newEvent`、`store.remove`、`Component.getInt`;`Skillbook.known` 是 `List<Map>`,读 Map 值要 cast。

## 关键约束(plan 已写,重点提醒)
- `applySkillLevelCurve` 作用于**全部 known**(主动+被动),写 `known[i].level`;tier 读取走 `Skill.loadSpecAny(base)`+`Skill._toJs`,优先级 `level_curve > tier > default`。
- `debug.set_level`:`applySkillLevelCurve` 必须在 `applySpec` **之前**调(被动闭包烤 level,recalculate 不重建)。
- 进化成本:`count = orb.count != null ? parseInt(orb.count) : Progression.orbCost()`,三处读点(`talent.js:387/484`、`talent_tree.js:53`)都改,抽 `Talent.orbCount(orb)` 共享。
- 默认成本 2→1 会打挂既有 `TalentTreeTest`/`TalentTreeSnapshotTest`,按 plan Task6 Step6 同步这两个测试(背包存量断言保持 2,只改成本断言)。
- `progression.yaml` 走 `engine.loadYaml` 按需读;`Progression` 懒加载缓存。

## 完成后输出
逐 Task 报告:改了哪些文件、跑了哪条 mvn 命令、结果(贴关键输出:测试数/通过数)。最后报全量 `mvn test` 是否全绿(贴 BUILD SUCCESS/失败摘要)。如某步卡住,说明卡在哪、你的判断,不要瞎改偏离 plan。
