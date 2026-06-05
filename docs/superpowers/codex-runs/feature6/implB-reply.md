已完成 Feature #6 Phase B，未 commit、未碰 git。

**改动文件**
- [WorldSnapshot.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java): `inGame` 尾部新增 `Specialization specialization`，同步 `characterSelect`/`characterCreate` `null` 占位；新增 `SpecOption`/`SpecEffects`/`SpecPending`/`SpecPathNode`/`SpecLocked`/`Specialization` records。使用 `Specialization` record 名，保持和 plan/spec 一致。
- [ScriptRuntime.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java): 新增 `newSpec*` 桥工厂。
- [SnapshotService.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java): 新增 `buildSpecialization(playerId)`，fire `ui.render_specialization` 并塞进 `inGame(...)`。
- [specialization.js](C:/workplace/epic/mods/base-rules/handlers/ui/specialization.js): 新增 UI handler，回填 path/pending/locked 和 effects 摘要。
- [SpecializationSnapshotTest.java](C:/workplace/epic/backend/src/test/java/com/epic/engine/snapshot/SpecializationSnapshotTest.java): 新增 3 个快照测试。
- [SpecializationPanel.vue](C:/workplace/epic/frontend/src/components/SpecializationPanel.vue): 新增专精面板，含不可更改二次确认、effects 摘要、locked、终态、readonly。
- [SnapshotRenderer.vue](C:/workplace/epic/frontend/src/components/SnapshotRenderer.vue): 点亮“专精”按钮并接 Modal。

**验证结果**
- `cd backend && mvn test`: 通过，`189 tests, 0 failures, 0 errors, 0 skipped`。
- `cd frontend && npm run build`: 已实际运行，但当前环境失败在 Vite 加载配置前的 esbuild 子进程启动：`Error: spawn EPERM`。直接执行 `esbuild.exe --version` 正常，`require('esbuild')` 正常，但 esbuild JS API/Vite 创建子进程被环境权限拦截；这不是 Vue 编译错误。也尝试了 `cmd /c npm run build`、`npx vite build --configLoader runner`、显式 `ESBUILD_BINARY_PATH`，仍是 `spawn EPERM`。

**请重点 review**
- `WorldSnapshot` record 追加字段和所有工厂/调用点同步。
- `ScriptRuntime` `newSpec*` 桥工厂签名和 JS handler 使用是否对齐。
- `ui.render_specialization` 三态：L10 根 pending、已选 L10 locked `{2,50}`、L50 tier2 终态。
- 前端不可洗确认文案含“不可更改”，三态和 readonly 已接。
- Phase A 的 action result 通路没有重做，只复用现有 `buildSnapshot(token, ActionResult)`。

**偏离 plan**
- 没有结构性偏离。
- `newSpecialization(...)` 工厂按要求添加，但当前 Java builder 直接 `new WorldSnapshot.Specialization(...)` 汇总事件结果；JS handler 只创建子 records。