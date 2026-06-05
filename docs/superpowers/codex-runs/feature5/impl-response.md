已按 Feature #5 plan/R1-R11 实现，未 commit、未运行 git。

**Task 结果**
- Task 1-4: 加入 `passives/` loader、`passive` modifier type、三份 demo passive；实现 `Skill.resolveSpec`、level scaling、skill_patch；接入 `02_dispatch.js` 和 bespoke `cleave/cross_blast/piercing_ray/heal`。`ResolveSpecTest` 通过。
- Task 5-6: `Skillbook.known` 新实例带 `level`，法师起始被动；动作栏和 equip/unequip 显式过滤 passive；修正 `CharacterFlowTest`。`SkillbookActionsTest,CharacterFlowTest` 通过。
- Task 7-8: 新增 `04_passive_lib.js`，实现 stat_mod 常驻 modifier、`Passive.owns` kind 校验、handler demo `lifesteal_on_kill`。`PassiveStatModTest,PassiveHandlerTest` 通过。
- Task 9: 快照 `SkillEntry` 增加 `level/kind`，被动从 `passives/` 加载，`equippedCount` 只数 active。`SkillbookSnapshotTest` 通过。
- Task 10: `SkillbookPanel.vue` 渲染只读被动 tab 和 Lv 标签。
- Task 11: `applySkillLevelCurve(entityId)` no-op hook 接到 `entity.level_up`、`entity.loaded`、新建角色路径；属性被动注册也接这些路径。
- Task 12: 新增 debug POST 端点：`/api/debug/passive/{token}`、`/api/debug/skill-level/{token}`，并通过事件 handler 修改 Skillbook。
- Task 13: 后端全量完成；前端 build 被当前环境的 Node 子进程 pipe spawn 限制阻塞，见下方。

**改动文件**
- Backend Java: `ModuleLoader.java`, `ScriptRuntime.java`, `WorldSnapshot.java`, `SnapshotService.java`, `DebugController.java`
- Backend tests: `ResolveSpecTest.java`, `PassiveStatModTest.java`, `PassiveHandlerTest.java`, `SkillbookSnapshotTest.java`, `SkillbookActionsTest.java`, `CharacterFlowTest.java`
- Mods: `00_skill_lib.js`, `02_dispatch.js`, `03_skillbook.js`, `04_passive_lib.js`, `select.js`, `recalculate_hooks.js`, `actions.js`, `skillbook.js`, `cleave.js`, `cross_blast.js`, `piercing_ray.js`, `heal.js`, `modifier_types.yaml`, `class_mage.schema.yaml`, `passives/*`
- Frontend: `SkillbookPanel.vue`

**验证**
- `cd backend && mvn test`: PASS
- 重点回归包含 `CombatBugfixTest`, `SkillbookSnapshotTest`, `ResolveSpecTest` passthrough: PASS
- `SkillbookPanel.vue` SFC parse: PASS
- `cd frontend && npm run build`: BLOCKED by environment before code compilation:
  `Error: spawn EPERM` from Vite/esbuild. Direct `esbuild.exe --version` works, but Node spawning esbuild with piped stdio fails, which Vite requires.