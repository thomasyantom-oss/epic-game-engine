**总评：REQUEST-CHANGES**

plan 主体方向和 spec 一致，modifier 生命周期这条路也基本可落地。但有 2 个会让 headless 实现一次不过的阻塞：`action result` 失败通路现有代码并不存在，以及 `applySpec` 在建角/load 中的调用顺序描述不够精确，容易产生重复 recalc 或漏 derived/passive 的时序问题。另有几个需要收紧的实现细节。

**1. Task A2 effectiveGrowth + level_growth 改写：成立，有小风险**

依据：`leveling.js` 的 `registerLevelGrowthModifier(entityId, level)` 捕获 `capturedLevel`，apply 时从当前 entity 取 `classSchema.raw().get("growth")`，再对 `PrimaryStats` 做 `+= growth * (capturedLevel - 1)`。因为 `ModifierChain.recalculate()` 会先 restore base，再跑 modifier，把 apply 内 growth 替换成 `effectiveGrowth(entity)` 确实能让 recalc 自动 spec-aware。

修正：
- `applySpec` 必须重注册同 id `level_growth`，否则选择专精后 captured modifier 仍存在但 growth 改读如果已经改成动态函数，其实也会生效；重注册仍合理，用于 load/level 对称。
- `allocate_point` 分支现在只有 `pendingPoints <= 0` 早退，但一旦 pendingPoints 未来非 0，它会用同 id `level_growth` 覆盖 spec-aware 成长。plan 说“守卫”，但要更明确：本 feature 中 `action.allocate_point` 应保持休眠，不得注册同 id modifier；测试要钉 `pendingPoints=0` 不覆盖只是最低限，最好也测 `pendingPoints>0` 被拒绝或 no-op。

**2. Task A2/A3 spec modifier(220)+ weaponAttr 覆写：成立**

依据：`modifier_types.yaml` 现有 `class=180`、`derived=300`，`Modifier.compareTo` 升序，`ModifierChainService.addModifier` 会按 type 的 `exclusive` 清同 type modifier，再 add。`spec=220` 会在 class 后、derived 前执行，derived 能读到覆写后的 `PrimaryStats.weaponAttr`。

修正：
- `exclusive` 对单条 spec modifier 合适。
- `registerSpecModifier` 固定 id `spec_mod` 也合适。
- plan 里“对节点其它 flat 属性加值用 +=”过于危险，spec 没定义这类效果。建议删除，或明确只允许白名单 stat 字段，并说明落到哪个 component。否则实现者可能误处理 `tier/requires_level` 这类节点字段。

**3. Task A3 建角/load 接入：有风险**

依据：`select.js` 当前顺序是 stat base components → 写 class weaponAttr → `engine.setBase(charId)` → 添加 Character/Skillbook/EquipmentSlots/Experience → 注册 class/derived/passive → recalc → 满血 → save。`recalculate_hooks.js` load 时先复位 stat components、还原 class weaponAttr、`setBaseSelective(statCompTypes)`，再注册 class/equipment/level/derived/passive，最后 recalc 和满血。

修正：
- `Specialization` 不进 `baseComponents/setBaseSelective` 是正确的，plan 写到了。
- 建角时 `Specialization` 应在 `setBase` 之后作为结构组件添加；不要进入 base snapshot。
- `applySpec` 的调用点要明确放在 derived modifier 已注册之后。否则 applySpec 内部 recalc 时 derived 还没注册，虽然后续可能再 recalc 修回来，但会制造时序噪音。
- load 时补老存档空 `Specialization` 可行，但如果补了组件，是否立即 `persistence.save` 要明确；否则只在内存里兜底，下一次纯 load 仍要补。

**4. Task A4 resolveSpec 接缝：成立**

依据：`00_skill_lib.js` 当前 `resolveSpec` 是 `applyLevelScaling → applyNode → applyPassivePatches → return`。在 passive 后挂 `applySpecializationPatches` 不会改变 demo/golden，只要无 `Specialization` 或无 `skill_patches` 时 no-op。

修正：
- 对怪物/无 `Specialization`/无 class tree 必须 no-op。
- 需要复用 `_matchSkill/_applyPatch`，不要另写匹配逻辑。

**5. Task A5 action + 校验：有风险**

校验链基本成立：节点存在、`parent == path` 末端、等级满足、同 tier 未选，能挡 spec §7 的三类拒绝。

修正：
- 增加 `node.tier == path.size() + 1`。只靠 parent 和 “tier 未选” 对坏数据不够严格。
- 失败 result 是阻塞点：现有 `SnapshotController.performAction()` fire action 后直接 `snapshotService.buildSnapshot(token)`，`SnapshotService.buildInGameSnapshot()` 固定 `new ActionResult(true, "ok")`。JS `event.set("success", false/message)` 不会进快照。plan 必须增加 `SnapshotController`/`SnapshotService` 的 result 传递步骤，或明确现有 action result 协议如何用。

**6. Task B1 快照 record 改动：有风险但可行**

依据：`WorldSnapshot` record 尾部新增字段可行，但所有 `new WorldSnapshot(...)` 静态工厂都要同步参数。`ScriptRuntime` 目前只有 `newSkillEntry` 等工厂，新增 `newSpec*` 桥模式可行。`SnapshotService.buildSkillbook()` 和 `ui.render_skillbook` 的事件回填模式也能照搬。

修正：
- plan 漏列 `SnapshotController`，如果要传 action result 必须改它。
- `characterSelect/characterCreate` 传 null 没问题。
- `inGame(...)` 参数和 `new WorldSnapshot(...)` 尾参必须全部同步，否则编译失败。
- `ui.render_specialization` 依赖 `loadSpecTree`，按当前 ModuleLoader 路径排序 `handlers/character` 会早于 `handlers/ui`，可行。

**7. 测试矩阵：基本覆盖，但缺几项**

已有覆盖：追溯 growth、elementalist 默认 growth、pyromancer grant passive、拒绝三连、load 往返、main_attr、weaponAttr 隔离、allocate_point、终态、被动查重，基本覆盖 spec §7。

缺/需加强：
- action result 失败 message 进入快照或 API 响应的测试。
- `node.tier == path.size()+1` 的坏数据/合成树测试。
- 老存档无 `Specialization` 组件 load 后 no-op 且快照可渲染。
- 未专精 L>1 load 往返保持旧行为，plan 验收写了，但测试矩阵里不够明确。

**8. 步骤完备性/可执行性：有阻塞**

最可能卡住的是 action result 通路：plan 要求“失败 message 进 action result”，但现有代码没有这个机制，且修改边界没列 `SnapshotController`。

其次是 `applySpec` 的 recalc 语义：`ModifierChainService.addModifier()` 每次 add 都会自动 recalculate，`applySpec` 内再显式 `engine.recalculate`，加上 select/load 现有 passive recalc，实际会多次 recalc。功能未必错，但实现者容易在 HP 初始化和 derived 注册前后踩时序。plan 应明确是替换现有 recalc 块，还是追加在某一步之后。

**9. 范围/切片：基本合理**

两 phase 后端机制+快照、再前端是合理的。commit 可以再拆：
- A1/A2/A3/A5 后端机制和测试。
- A4 skill resolve 接缝可独立。
- B1 snapshot 独立。
- B2 frontend 独立。

**实现前必须先解决**

1. 明确 action failure result 如何从 JS action event 进入 `WorldSnapshot.result`，并把 `SnapshotController` 加入修改范围。
2. 明确 `applySpec` 在 `select.js` 和 `entity.loaded` 中的精确调用位置，避免 derived/passive 未注册时 recalc 或重复 recalc 造成测试不稳定。
3. 删除或收紧 “节点其它 flat 属性 +=” 的模糊实现要求。
4. 强化选择校验：加入 `tier == path.size()+1`。
5. 明确 `allocate_point` 在本 feature 中是彻底 no-op/拒绝，不能在任何分支覆盖 `level_growth`。