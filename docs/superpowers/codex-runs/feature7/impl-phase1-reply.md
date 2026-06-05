阶段 1 后端闭环已完成；未做前端、未 commit、未 git add。

**Task 完成情况**
- A1 完成：统一 `Passive.ownedSpecs`，`Skill.ownedPassives`/`Passive.registerStatMods`/`Passive.owns` 接入；stat_mod modifier 清理防残留。
- A2 完成：`TalentTree`/`OrbPouch` 组件 ensure、等级派生点数。
- A3 完成：`talents/index.yaml` manifest、全局进化索引、`Skill.applyNode`。
- A4 完成：`derivedPassives` 三型 passive、requires_spec、override 追溯重推。
- A5 完成：`talent_unlock` / `talent_place_orb` / `talent_respec` 校验链。
- A6 完成：`debug.grant_orb` + `/api/debug/orb/{token}`。
- A7 完成：强类型 `talentTree` 快照 records、Java 工厂、JS 回填。
- A8 完成：skillbook 进化名/icon/description 回填。
- A9 完成：全量后端回归 + load 往返测试。

**本轮改动文件**
- `C:\workplace\epic\mods\base-rules\handlers\skill\04_passive_lib.js`
- `C:\workplace\epic\mods\base-rules\handlers\skill\00_skill_lib.js`
- `C:\workplace\epic\mods\base-rules\handlers\character\talent.js`
- `C:\workplace\epic\mods\base-rules\handlers\character\select.js`
- `C:\workplace\epic\mods\base-rules\handlers\character\recalculate_hooks.js`
- `C:\workplace\epic\mods\base-rules\handlers\ui\talent_tree.js`
- `C:\workplace\epic\mods\base-rules\handlers\ui\skillbook.js`
- `C:\workplace\epic\mods\base-rules\talents\index.yaml`
- `C:\workplace\epic\mods\base-rules\talents\elementalist.yaml`
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\snapshot\WorldSnapshot.java`
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\script\ScriptRuntime.java`
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\snapshot\SnapshotService.java`
- `C:\workplace\epic\backend\src\main\java\com\epic\engine\debug\DebugController.java`
- `C:\workplace\epic\backend\src\test\java\com\epic\engine\character\TalentTreeTest.java`
- `C:\workplace\epic\backend\src\test\java\com\epic\engine\snapshot\TalentTreeSnapshotTest.java`

**验证**
- `cd backend && mvn test`
- 结果：`202` tests，`0` failures，`0` errors，`0` skipped，全绿。
- 新增测试类：`TalentTreeTest` 7 个用例，`TalentTreeSnapshotTest` 4 个用例。

**偏离 / 小适配**
- `Skill.ownedPassives` 保留了 `Passive` 未加载时的旧 Skillbook fallback，用于兼容现有只加载 `00_skill_lib.js` 的低层测试路径。
- `talent_place_orb` 先重算再写 `OrbPouch`/`known.node`，`talent_respec` 在最终空 allocation 后额外跑一次 stat_mod 清理；原因是 recalc 可能替换组件引用，必须保护结构性永久状态不被覆盖。
- `Passive._registerOne` 对缺失的 `PrimaryStats` 字段按 `0` 初始化后加值；原因是 demo override 的 `火属性` 不在基础 schema，但验收要求可见。
- 主动节点直接授予技能未实现，按 plan A4 明确留后续内容轮；本轮只覆盖 passive 三型 + skill_slot。

**验收①自检**
- 派生点数含 `spent > total` 拒绝：已测。
- 统一被动来源：stat_mod、skill_patch、handler 都已测。
- 洗点 stat_mod 不残留：已测。
- 追溯重推 override：已测，`pyromancer` 后 `火属性` 生效。
- respec 保进化：已测，`known.fireball.node=pyroblast` 保留。
- skillbook 显示进化名：已测，显示“炎爆”。
- load 往返：已测，专精路径、已点节点、进化 node、override 属性一致。