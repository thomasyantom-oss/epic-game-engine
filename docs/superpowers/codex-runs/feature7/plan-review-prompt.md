role: 你是这个仓库的 Senior SDE,对一份**实现 plan(尚未实现)**做实现前 review。不要写代码、不要改文件——只输出结构化评审,挑"按这个 plan 写下去会踩的坑"。

## 要 review 的 plan
`docs/superpowers/plans/2026-06-04-feature7-skill-tree.md`(Feature #7 专精天赋树,两阶段)

## 权威 spec(plan 须忠于它;已纳上一轮 Codex spec review)
`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md`

## 必须打开核对的现有代码(判断 plan 的复用声称/约束是否真成立)
- `mods/base-rules/handlers/skill/04_passive_lib.js` —— `registerStatMods`/`owns`/`grant`/`_registerOne`(modId 先删后加);**重点:Task A1 要新增 `ownedSpecs` 并让三处改走它,核对重构是否完整、有没有遗漏的 known 扫描点**
- `mods/base-rules/handlers/skill/00_skill_lib.js` —— `ownedPassives`(`:237`)、`applyPassivePatches`(读 `.effect/.match/.patch`)、`applyNode`(`:191` no-op)、`_applyPatch`/`_applyPatchValue`(`:267`/`:273`,**核对它能否正确做 `targeting.pattern` 整数组替换、`damage.add` 数值相加**)、`resolveSpec` 顺序
- `mods/base-rules/handlers/character/specialization.js` —— `applySpec`(`:187`:grantPassives→registerStatMods→recalculate)、`ensureComponent`、`choose_specialization`
- `mods/base-rules/handlers/character/leveling.js` —— `debug.set_level`(`:39` 调 applySpec)、`registerLevelGrowthModifier`
- `mods/base-rules/handlers/character/recalculate_hooks.js` —— `entity.loaded`(`:23`)复位+重注册顺序;ensure 组件位置
- `backend/.../snapshot/{WorldSnapshot,SnapshotService}.java` + `script/ScriptRuntime.java` —— record + `newXxx` 工厂 + `buildXxx`/`ui.render_xxx` 回填模式(`buildSpecialization`/`newSpecPathNode`)
- `backend/.../debug/DebugController.java` —— `/passive` 端点模式

## 逐项给 verdict(成立/有风险/错误 + 依据行号 + 修改建议)
1. **A1 聚合层重构完整性**:`Passive.ownedSpecs` 合并 known + talent,三处(`registerStatMods`/`owns`/`Skill.ownedPassives`)改走它——有没有**遗漏的 known 直扫点**(如 `grant`/`setSkillLevel`/别处)?`Skill.ownedPassives` 返回契约从"known 的 spec 数组"变成"含 talent 的 spec 数组",`applyPassivePatches` 消费是否仍正确?**`Passive.ownedSpecs`→`Talent.derivedPassives`→读组件,与 `Skill.ownedPassives`→`Passive.ownedSpecs` 有没有加载顺序/循环依赖风险**(talent.js vs 04/00 lib 的 ModuleLoader 加载序)?
2. **洗点后 stat_mod modifier 残留(我最担心的)**:`registerStatMods` 只"注册当前存在的",`_registerOne` 按 modId 先删后加。**洗点后 `derivedPassives` 返回空,registerStatMods 不会去 remove 之前注册的 `passive_talent_<nodeId>_<entity>` modifier → 残留加成不消失**。plan 的 A5 respec 只说"applySpec 摘被动",这够吗?是否需要显式 remove 旧 talent modifier(记录上一次注册的 id 集 / 或 recalculate 复位机制能否兜住)?同理**追溯重推**:override 后旧 modId(`talent_elem_root`)与新的是否同 id(同 nodeId→同 modId,先删后加可兜)?但 inline→ref 切换、节点从 derive 集消失的场景呢?
3. **applyNode 与 _applyPatch**:`_applyPatchValue` 对 `targeting.pattern`(数组)走"整体赋值"分支、对 `damage.add`(数值)走"相加"分支——**进化 patch 里 `damage.add:8` 是想 +8 还是设为 8?** base fireball `damage.add=10`,patch 后期望值是多少?plan/spec 有没有说清"patch 语义=增量还是覆盖",会不会和 skill_patch 语义不一致导致 demo 数值意外?
4. **派生点数边界**:`total=f(level)`、`available=total-spent`。debug 降级致 `spent>total` 时 plan 说"clamp≥0 + allocation 保留"——unlock 校验 `available>0` 能否防止超额?快照 `available` clamp 与 unlock 闸是否一致?
5. **action 校验链**:连通性(`parents` 任一在 unlocked,或 root 无 parent)、`requires_spec in path`、`TalentTree.root==path[0]` 首次初始化——有没有漏的拒绝态?`talent_place_orb` 写 `known[skill].node` 时,该技能若不在 known(未学)如何处理(建条目还是拒)?
6. **快照态机**:7 态枚举(尤其 `filled-node-relocked`)后端可判性;`children` 反查、`effectSummary` 随 override 变——`ui.render_talent_tree` 要不要调 `Talent.derivedPassives` 保持与运行时一致?有没有"快照算的态"和"action 校验的态"两套逻辑漂移的风险(建议单一真相函数)?
7. **快照强类型扩展**:records 嵌套 + `of(...)` + 工厂桥 + `buildInGameSnapshot` 串接,照 #6 模式有没有漏的接线(如 `WorldSnapshot.of` 重载、非 inGame 传 null)?
8. **任务粒度/顺序/TDD**:A1 当地基先行是否正确?有没有任务隐含依赖未点明、或某步过大(尤其 A7 Java 量)?阶段闸(A8 后再上 B)是否合理?
9. **范围/回归**:plan 边界(别碰 ModifierChain/golden/sim/#6 不可洗)是否守住?有没有破坏 #4/#5/#6/战斗回归的隐患被 plan 漏掉?

最后总评:APPROVE / APPROVE-with-nits / REQUEST-CHANGES + "实现前必须先解决"的阻塞项清单。
