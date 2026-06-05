已完成 Feature #6 Phase A，未 commit，未做 Phase B 前端/专精快照 record。

**实际改动文件**
- `mods/base-rules/modifier_types.yaml`
- `mods/base-rules/specializations/mage.yaml`
- `mods/base-rules/handlers/character/specialization.js`
- `mods/base-rules/handlers/character/leveling.js`
- `mods/base-rules/handlers/character/select.js`
- `mods/base-rules/handlers/character/recalculate_hooks.js`
- `mods/base-rules/handlers/skill/00_skill_lib.js`
- `backend/src/main/java/com/epic/engine/snapshot/SnapshotController.java`
- `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`
- `backend/src/test/java/com/epic/engine/character/SpecializationTest.java`
- `backend/src/test/java/com/epic/engine/core/ModifierTypeRegistryTest.java`
- `backend/src/test/java/com/epic/engine/skill/ResolveSpecTest.java`

**测试**
- 定向：`mvn test "-Dtest=SpecializationTest,ResolveSpecTest,ModifierTypeRegistryTest"` 通过，21 tests / 0 failures / 0 errors。
- 全量：`cd backend && mvn test` 通过，186 tests / 0 failures / 0 errors / 0 skipped。

**请重点 review**
- `spec` modifier priority 是否正确落在 220。
- `main_attr` 只通过 modifier 覆写 `PrimaryStats.weaponAttr`，不写 base。
- `applySpec` 在 `registerDerivedModifier` 之后调用，并统一负责 spec modifier、spec-aware `level_growth`、grant passives、recalculate。
- load 往返无属性膨胀，含专精与未专精 L>1。
- `action.allocate_point` 未复活：pendingPoints > 0 也拒绝/no-op，不注册旧 `level_growth`。
- `SnapshotController`/`SnapshotService` action failure result 通路：拒绝 message 进入快照 `result`。
- `resolveSpec` 的 `applySpecializationPatches` 对怪物/无组件/无树/无 patches 安全 no-op。

**偏离/说明**
- `pyromancer` 按 plan/验收使用 `lifesteal_on_kill`，没有按 spec 示例里一处旧文案使用 `lingering_burn`，因为法师起手已带 `lingering_burn`。
- 测试里的 reload 往返为模拟真实重启，显式清理同 id modifier chain；这是测试 harness 处理，不是生产逻辑改动。