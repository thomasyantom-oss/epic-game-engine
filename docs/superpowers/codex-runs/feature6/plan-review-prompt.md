role: 你是这个仓库的 Senior SDE,对一份**实现 plan(尚未实现)**做 review。不写代码、不改文件——只输出结构化评审。这份 plan 接下来会原样喂给 Codex(你)headless 实现,所以重点是:照它做能不能一次过、有没有会踩现有管线的坑、有没有缺步骤导致返工。

## 要 review 的 plan
`docs/superpowers/plans/2026-06-04-feature6-specialization.md`

## 权威 spec(冲突以它为准,已纳上一轮 review)
`docs/superpowers/specs/2026-06-04-feature6-specialization-design.md`(尤其 §3 生命周期 / §6 快照 / §10 已纳项)

## 必须打开核对的现有代码(别只信 plan 描述)
- `mods/base-rules/modifier_types.yaml`(priority) + `backend/.../core/ModifierChain.java`(addModifier 幂等 / recalculate)
- `mods/base-rules/handlers/character/leveling.js`(registerLevelGrowthModifier / allocate_point)
- `mods/base-rules/handlers/character/select.js` + `recalculate_hooks.js`(建角 / entity.loaded 装配)
- `mods/base-rules/handlers/skill/00_skill_lib.js`(resolveSpec / applyPassivePatches / _matchSkill / _applyPatch)
- `mods/base-rules/handlers/skill/04_passive_lib.js`(Passive.registerStatMods / Skillbook.known 形状)
- `backend/.../snapshot/WorldSnapshot.java` + `SnapshotService.java`(buildSkillbook) + `script/ScriptRuntime.java`(newSkillEntry 桥)
- `mods/base-rules/handlers/ui/skillbook.js`(ui.render_skillbook 回填模式)
- 测试样板:`backend/src/test/.../character/CharacterFlowTest.java`、`core/ModifierChainTest.java`、`skill/PassiveStatModTest.java`、`skill/ResolveSpecTest.java`、`snapshot/SkillbookSnapshotTest.java`、`ui/SkillbookActionsTest.java`、`combat/ReloadInflationBugTest.java`

## 逐项给 verdict(成立/有风险/错误 + 依据 + 修正)
1. **Task A2 effectiveGrowth + level_growth 改写**:把 apply 改读 `effectiveGrowth(entity)` 是否真能让 recalc 自动 spec-aware?`registerLevelGrowthModifier` 现在的 apply 闭包结构(capturedLevel、从 PrimaryStats 当前值 += )改这一处够不够?有没有漏(如 allocate_point 分支也注册 level_growth)?
2. **Task A2/A3 spec modifier(220)+ weaponAttr 覆写**:作为 modifier 输出在 recalc 时 set weaponAttr,与 load 期"还原 class weaponAttr 到 base"的时序,priority 220 在 class180/derived300 之间是否真能让 derived 读到覆写值?exclusive stack_rule 对单条 spec modifier 合不合适?
3. **Task A3 建角/load 接入**:`applySpec` 在 select.js 与 recalculate_hooks.js 两处调用点是否正确(尤其 setBase/setBaseSelective 与 recalculate 的先后)?老存档无 Specialization 组件的兜底够不够?Specialization 不进 baseComponents/setBaseSelective 是否在 plan 里真的保证了?
4. **Task A4 resolveSpec 接缝**:镜像 applyPassivePatches 的 applySpecializationPatches 挂在 passive 之后,会不会改变现有 level/node/passive 的结果或 golden?施法者无 Specialization 组件(怪物)时是否安全 no-op?
5. **Task A5 action + 校验**:校验链(parent==path 末端 / requires_level / tier 未选)有没有逻辑洞?action result 失败通路是否符合现有 action 处理?
6. **Task B1 快照 record 改动**:给 `inGame` record 尾加字段 + characterSelect/characterCreate 传 null,会不会漏改调用点导致编译失败?桥工厂 + ui.render_specialization 回填模式是否跟 skillbook 一致可行?
7. **测试矩阵**:Task A5/B1 列的 case 有没有覆盖 spec §7 全部验收?有没有缺(load 往返/main_attr 合成节点/allocate_point 休眠/被动查重/终态)?
8. **步骤完备性/可执行性**:有没有"描述了做什么但没说怎么做"的步骤、跨任务函数名/record 名不一致、或缺失的依赖顺序?作为 headless 一次实现,哪一步最可能卡住或返工?
9. **范围/切片**:2 phase 切法合理吗?要不要进一步拆 commit?

最后总评:APPROVE / APPROVE-with-nits / REQUEST-CHANGES,列"实现前必须先解决"的阻塞项(若有)。
