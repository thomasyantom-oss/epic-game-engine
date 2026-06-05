已完成 C、D，未 `git add` / commit。

**C 完成**
- `C:\workplace\epic\frontend\src\components\SnapshotRenderer.vue`
  - 移除右下角单独“专精”入口。
  - “天赋”成为统一入口。
  - 打开统一 modal 时：`specialization.pending` 优先渲染 `SpecializationPanel`，否则渲染 `TalentTreePanel`，无树时显示现有“专精后解锁”。
- `C:\workplace\epic\frontend\src\components\SkillbookPanel.vue`
  - 技能书“树”入口改为打开统一面板，未解锁也可打开看到锁定态。

**D 完成**
- `C:\workplace\epic\mods\base-rules\handlers\ui\skillbook.js`
  - 在 `ui.render_skillbook` 的 passive 快照里追加 `Talent.derivedPassives(entity)` 中 `skill_patch` / `handler` 条目。
  - `stat_mod` 不追加到被动列表。
  - talent 条目标 `source: "talent"`，`equipped=false`，`node=null`。
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\snapshot\WorldSnapshot.java`
  - `SkillEntry` 增加 `source` 字段。
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\script\ScriptRuntime.java`
  - 保留原 `newSkillEntry(...)`，新增 `newSkillEntryWithSource(...)`。
- `C:\workplace\epic\mods\base-rules\handlers\character\talent.js`
  - `ref` 派生被动补带 `description/icon` 展示元数据。
- `C:\workplace\epic\backend\src\test\java\com\epic\engine\snapshot\TalentTreeSnapshotTest.java`
  - 新增 D 回归测试：`skill_patch` + `handler` talent 被动进入被动列表且只读；`stat_mod` 不进列表但属性已加。

**验证**
- `cd backend && mvn test`：通过，退出码 0。
- 前端未跑 `npm run build`，按约束用 `@vue/compiler-sfc` 对改动的：
  - `src/components/SnapshotRenderer.vue`
  - `src/components/SkillbookPanel.vue`
  做了 parse + compileScript + compileTemplate + compileStyle：均 `ok`。

**自检**
- 单一入口：右下角只剩“天赋”，无单独“专精”按钮。
- 有 pending 时：打开统一面板立即显示专精选择；确认后下一次快照无 pending 则回到天赋树。
- 无 pending 有 `talentTree`：直接显示树。
- 无 pending 且无树：显示“专精后解锁”。
- 显示被动：`skill_patch` / `handler` 进被动 tab，来源 `talent`，前端不提供出战/移除。
- 隐式被动：`stat_mod` 不进被动 tab，属性仍通过现有管线体现。