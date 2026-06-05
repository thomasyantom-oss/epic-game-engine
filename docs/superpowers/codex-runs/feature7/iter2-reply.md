B 已完成并通过目标测试。

改动文件：
- [talent.js](C:/workplace/epic/mods/base-rules/handlers/character/talent.js)：新增 `TalentTree.evolved`，插球记录永久进化，洗点清空槽技能 `known.node`，重点槽节点按 `evolved` 免费恢复当前进化；`nodeState` 改用 `evolved` 判定 `filled-node-relocked`。
- [TalentTreeTest.java](C:/workplace/epic/backend/src/test/java/com/epic/engine/character/TalentTreeTest.java)：新增/调整 B 验收测试，覆盖插球、洗点、重点免费恢复、`resolveSpec` 原始/炎爆切换。
- [TalentTreeSnapshotTest.java](C:/workplace/epic/backend/src/test/java/com/epic/engine/snapshot/TalentTreeSnapshotTest.java)：新增 relocked 快照态测试，确认槽未点亮但已花球时为 `filled-node-relocked` 且 `slot.filled=false`。

验证：
- 已跑 `cd backend && mvn -q "-Dtest=TalentTreeTest,TalentTreeSnapshotTest" test`
- 结果：通过。

偏离 §12：
- 无结构性偏离。
- 测试里“未消耗球”按重点前后库存值不变验证，不强制库存键保留为 `0`；这是因为当前组件 map 可能没有零值键，但行为上确实没有再次扣球。

B 自检：
- 洗点后 `known.node=null`：已覆盖。
- `evolved` 保留 `pyroblast`：已覆盖。
- 重点 `fireball_slot` 免费恢复 `known.node=pyroblast`：已覆盖。
- 洗点后火球回原始单体，重点后恢复炎爆 AOE：已覆盖。