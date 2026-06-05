role: 实现层 SDE。Phase A(后端机制)已完成并通过 186 测试(未 commit,在工作树里)。现在实现 **Feature #6 专精的 Phase B(快照 specialization 块 + 前端面板)**。

## 权威文档(先读)
- plan：`docs/superpowers/plans/2026-06-04-feature6-specialization.md` 的 **Phase B 全部任务 B1→B2→B3**。
- spec：`docs/superpowers/specs/2026-06-04-feature6-specialization-design.md` §6(快照/前端契约)。

## Phase A 已就位的东西(别重做)
- `Specialization` 组件(path)、`applySpec`/`loadSpecTree`/`specNode`/`pathNodes`/`effectiveGrowth`(在 `handlers/character/specialization.js`)。
- `action.choose_specialization`(校验 + reject)已能用。
- **action 失败 result 通路已通**(`SnapshotController` 透出 success/message;`SnapshotService.buildSnapshot(token, ActionResult)` 重载 + `buildInGameSnapshot` 用 override)。**Phase B 别动这条,直接复用。**

## 硬性纪律
1. **不要 commit、不要碰 git**。plan 的 Commit step 跳过。只写文件 + 跑验证。
2. 严格按 plan 修改边界;"别碰"清单不动。
3. **B1 改 `WorldSnapshot` record**：给 `inGame` record **尾部加 `Specialization specialization` 字段** + 加 record 类型(`SpecOption`/`SpecEffects`/`SpecPending`/`SpecPathNode`/`SpecLocked`/`Specialization`,字段见 plan B1 Step1)。**必须同步所有 `WorldSnapshot.inGame(...)` 工厂签名 + 调用点**(`SnapshotService.buildInGameSnapshot`)+ `characterSelect`/`characterCreate` 传 `null`,否则编译失败。注意 `Specialization` 这个 record 名与引擎组件名重名无妨(不同包/语境),但若担心混淆可命名 `SpecializationView`——**自行决定并在回话说明**。
4. **B1 桥**：`ScriptRuntime` 加 `newSpec*` 工厂(仿 `newSkillEntry`);`SnapshotService.buildSpecialization(playerId)` fire `ui.render_specialization`(仿 `buildSkillbook`/`ui.render_skillbook`);新 `mods/base-rules/handlers/ui/specialization.js` 回填 path/pending/locked(含 `pending.options[].effects{mainAttr,growth,grantPassives}`、终态 `pending=null && locked=[]`)。
5. **B2 前端**:新 `frontend/src/components/SpecializationPanel.vue`(props `{specialization, readonly}`,emit `action`);`SnapshotRenderer.vue` 点亮现 `disabled` 的"专精"按钮(`@click="openSpec"` 仿 `openSkillbook`)+ `Modal` 包面板,传 `:specialization="snapshot.specialization"` `:readonly="!!snapshot.combat"`。
   - **产品偏好**:粗边框 ≥2px;复用现有 CSS 变量/配色;不动 4 宫格布局;不可洗选择**二次确认**(文案含"不可更改");三态可辨(未专精给根 tier / 有可选 / 终态"已达最深专精");`locked` 灰显"L50 解锁";选项卡展示效果摘要;战斗中只读。

## 验证(必须实际跑)
- `cd backend && mvn test`:全绿(Phase A 186 + 新增快照测试 `SpecializationSnapshotTest`,仿 `SkillbookSnapshotTest`,覆盖 plan B1 Step5:未专精 L10→3 选项含 effects / 选 elementalist 后 pending=null+locked 含{2,50} / L50 选 pyromancer 终态)。
- `cd frontend && npm run build`:通过。

## 完成后(写进本 -o 回话,别 commit)
- 实际改动文件清单(逐个)。
- `mvn test` + `npm run build` 结果。
- **请重点 review**:WorldSnapshot record 改动 + 所有调用点同步、桥工厂、ui.render_specialization 三态回填、前端不可洗确认 + 三态 + readonly、是否复用(没重做)Phase A 的 result 通路。
- 任何就地偏离 plan + 理由。
