role: 你是这个仓库的实现层 SDE。按既定 plan 实现 **Feature #6 专精的 Phase A(后端机制 + 数据 + 测试)**。

## 权威文档(必须先完整读)
- plan：`docs/superpowers/plans/2026-06-04-feature6-specialization.md` —— 实现 **Phase A 的全部任务 A1→A2→A3→A4→A5**(含每个 Step、5 件套约束、File Structure)。**Phase B(快照/前端)本轮不做**。
- spec(冲突以它为准)：`docs/superpowers/specs/2026-06-04-feature6-specialization-design.md`

## 硬性纪律
1. **不要 commit、不要碰 git**（本仓库 git 归属问题 + 工作流规定由 Claude 提交)。plan 里的 "Commit" step 一律跳过。你只写文件 + 跑测试。
2. **严格按 plan 的修改边界**：只动 plan ②列出的"可动"文件;"别碰"清单(战斗/动画/伤害管线、ModifierChain.java 引擎逻辑、golden/SkillFidelityTest、sim 包)一律不动。
3. **TDD**：每个 Task 先写失败测试再实现(plan 已给断言要点;测试 harness 仿 plan 指名的现有测试类 `CharacterFlowTest`/`ModifierChainTest`/`PassiveStatModTest`/`ResolveSpecTest`/`SkillbookActionsTest`/`ReloadInflationBugTest`)。
4. **关键不踩的坑(plan ⑤ + review 已钉)**：
   - `spec` modifier priority 220(class180 后、derived300 前);weaponAttr 覆写**只能是 modifier 输出,绝不写 base**。
   - `applySpec` 必须在 `registerDerivedModifier` **之后**调用,替换原 `Passive.registerStatMods+recalculate` 段;`addModifier` 每次自动 recalc(幂等)。
   - `Specialization` 是非 base 结构组件,**不进** baseComponents/setBaseSelective。
   - `registerSpecModifier` 本轮**只处理 main_attr**,不实现 flat 属性 +=。
   - 校验含 `node.tier == path.size()+1`。
   - `action.allocate_point` 彻底休眠(pendingPoints>0 也 no-op,不注册 level_growth)。
   - **A5 Step 0 先补 action 失败 result 通路**(`SnapshotController.performAction` 透出 event 的 success/message;`SnapshotService.buildSnapshot(token, ActionResult)` 重载 + `buildInGameSnapshot` 用 override 替换 :88 硬编码),否则拒绝 message 进不了快照。
   - `applySpecializationPatches` 对怪物/无 Specialization/无 skill_patches **安全 no-op**。

## 验证(必须实际跑)
- `cd backend && mvn test`：全绿。新增 `SpecializationTest`(+ 视需要 ui/snapshot 包测试)覆盖 plan A5 列出的全部 case;现有 modifier/快照/战斗/passive/resolveSpec 测试不破、golden 未变。
- 跑测试时若环境问题(非你的代码 bug)导致个别失败,明确区分。

## 完成后(写进本 -o 回话,别 commit)
- 列**实际改动的文件清单**(逐个,别让我猜)。
- `mvn test` 最终结果(通过数/失败数;失败的话贴关键报错)。
- **"请重点 review"清单**:priority220 落位、weaponAttr 不写 base、applySpec 调用点与 recalc 时序、load 往返无膨胀、allocate_point 未复活、action result 通路改动、resolveSpec 接缝 no-op。
- 任何你就地偏离 plan 的地方 + 理由(等我 review 判)。
