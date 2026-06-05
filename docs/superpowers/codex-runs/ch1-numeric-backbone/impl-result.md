已按 Task 1–6 实现并验证。Task 7 未做。

**重要阻塞:** commit 没有成功执行。首次 Task 1 commit 时失败：
`fatal: Unable to create 'C:/workplace/epic/.git/index.lock': Permission denied`
当前沙箱对 `.git` 只有读权限，且 approval policy 是 `never`，所以后续无法 `git add/commit`。代码和测试改动都已在工作区。

**Task 1**
改动：
`mods/base-rules/progression.yaml`
`mods/base-rules/handlers/character/progression_lib.js`
`backend/src/test/java/com/epic/engine/progression/ProgressionBackboneTest.java`

命令：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`

结果：
`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

应提交：
`feat(ch1-backbone): progression.yaml 调参板 + Progression 访问库`

**Task 2**
改动：
`mods/base-rules/handlers/character/leveling.js`
`ProgressionBackboneTest.java`

红灯确认：
`gainXp_multiLevel_singleCall`: expected 3, was 2  
`gainXp_clampsAtCap`: expected xp 0, was 999990099

通过命令：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`

结果：
`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

应提交：
`feat(ch1-backbone): xpForLevel 指数化 + gain_xp 多级升级与 cap 封顶`

**Task 3**
改动：
`mods/base-rules/handlers/character/recalculate_hooks.js`
`ProgressionBackboneTest.java`

红灯确认：
chunky/smooth 两个测试均仍为 level 1。

通过命令：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`

结果：
`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

应提交：
`feat(ch1-backbone): applySkillLevelCurve 据 tier+人物级派生写 known level`

**Task 4**
改动：
`mods/base-rules/handlers/character/leveling.js`
`ProgressionBackboneTest.java`

红灯确认：
`debug.set_level` expected 100, was 250

通过命令：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`

结果：
`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

应提交：
`feat(ch1-backbone): debug.set_level cap clamp + 跳级后刷新技能等级曲线`

**Task 5**
改动：
`mods/base-rules/skills/fireball.yaml`
`ProgressionBackboneTest.java`

红灯确认：
fireball tier expected 4, was 10  
damage expected 55, was 10

通过命令：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`

结果：
`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

应提交：
`feat(ch1-backbone): 技能 tier/level_curve 约定 + fireball level_scaling 接缝样例`

**Task 6**
改动：
`mods/base-rules/handlers/character/talent.js`
`mods/base-rules/handlers/ui/talent_tree.js`
`mods/base-rules/talents/elementalist.yaml`
`ProgressionBackboneTest.java`
`backend/src/test/java/com/epic/engine/character/TalentTreeTest.java`
`backend/src/test/java/com/epic/engine/snapshot/TalentTreeSnapshotTest.java`

红灯确认：
`Talent.orbCount is not a function`

专项通过：
`cd backend && mvn test -Dtest=ProgressionBackboneTest`
`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

全量通过：
`cd backend && mvn test`
Maven exit code 0 / `BUILD SUCCESS`
Surefire 汇总：`Tests run: 216, Failures: 0, Errors: 0, Skipped: 0`

应提交：
`feat(ch1-backbone): 进化成本缺省读 orb.cost_per_evolution（修 missing→0 免费）`

额外 sanity：
`git diff --check` 通过，仅有既有 Windows 行尾提示。历史 untracked 如 `AGENT.md`、`doc/`、`frontend/src/assets/`、`set-env.ps1` 等未触碰、未暂存。