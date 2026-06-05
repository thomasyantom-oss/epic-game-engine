已完成本轮 UI 传播 + 打磨，未 commit，未碰 git。

改动文件清单：
- `backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java`
- `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java`
- `backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java`
- `backend/src/test/java/com/epic/engine/snapshot/CharacterSelectPreviewTest.java`
- `backend/src/test/java/com/epic/engine/snapshot/SpecializationSnapshotTest.java`
- `mods/base-rules/handlers/character/specialization.js`
- `mods/base-rules/handlers/character/select.js`
- `mods/base-rules/handlers/ui/status_bars.js`
- `mods/base-rules/handlers/ui/specialization.js`
- `mods/base-rules/handlers/combat/start_combat.js`
- `mods/base-rules/entities/encounters/forest_goblin.yaml`
- `frontend/src/components/CharacterSelect.vue`
- `frontend/src/components/StatusBars.vue`
- `frontend/src/components/combat/BattleGrid.vue`
- `frontend/src/components/SpecializationPanel.vue`
- `frontend/src/components/SkillbookPanel.vue`

验证结果：
- `cd backend && mvn test`：通过，189 tests，0 failures/errors。
- `cd frontend && npm run build`：失败于 Vite 加载配置前的 `Error: spawn EPERM`，符合你提到的沙箱问题，不是 Vue 编译错误。
- 额外跑了 Vue SFC parser 检查改动的 5 个 `.vue`：全部 `ok`。

重点核对：
- `CharacterInfo`/`CombatantInfo` record 和工厂/调用点已同步。
- `effectiveClassLabel/effectiveDescription/effectivePortrait/effectiveSpecNode` 已加，回落到原职业字段/schema。
- 选人详情区不再读 `previewFor(classId)`，改读角色自身实时 `primaryStats`、有效 `growth`、有效名/描述/立绘。
- 怪物 `race` 从 encounter 读，缺省回落 name；怪物 level 默认 1；战斗快照带 `level/typeLabel`。
- `SpecializationPanel`/`SkillbookPanel` 战斗中提示改红字“战斗中”。
- 专精选项摘要不再出现“无即时效果”，后端给出选择后的最终成长 map。
- 不可洗确认已从 `window.confirm` 改为游戏内遮罩 popup，复用删除确认风格。

就地偏离：`session.list_characters` 优先读取 `EntityStore` 中同 id 的 live entity，再回退 persistence 返回实体；这是为了保证选人界面拿到“实时属性”，不重跑 modifier。