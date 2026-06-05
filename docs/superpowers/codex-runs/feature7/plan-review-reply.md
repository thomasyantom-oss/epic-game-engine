**总评: REQUEST-CHANGES**

这个 plan 大方向忠于 spec，但按当前代码直接实现会踩两个阻塞坑：`stat_mod` talent 洗点/门控后残留，以及 `talents/*.yaml` 全局进化索引在现有 JS API 下无法按 plan 字面遍历。另有几处契约不清会导致测试写着过、实际 UI/战斗行为漂移。

**1. A1 聚合层重构完整性: 有风险**

依据：
`Passive.registerStatMods`、`Passive.owns` 当前只扫 `Skillbook.known`，分别在 `mods/base-rules/handlers/skill/04_passive_lib.js:2`、`:18`。`Skill.ownedPassives` 也只扫 known，在 `mods/base-rules/handlers/skill/00_skill_lib.js:237`。这三处必须改走统一聚合层，plan 判断成立。

但 plan 对 `ownedSpecs` 返回契约写得不一致：A1 Step 1 说返回项带 `id/level/effect/kind`，Step 3 又说 talent 返回 `{ id, level:1, spec }`。`applyPassivePatches` 在 `00_skill_lib.js:195-203` 直接读 `.effect/.match/.patch`，所以 `Skill.ownedPassives(ctx)` 不能返回 wrapper，必须返回“已展开 spec + level 元数据”的同一层对象，或 `applyPassivePatches` 要显式解包。

`grant`/`setSkillLevel` 在 `04_passive_lib.js:33`、`:53` 仍应扫 known，不应盲目改走 `ownedSpecs`。它们是 Skillbook mutator，不是 ownership/effect consumer；否则 debug grant 一个 talent-derived ref 时会被误判“已拥有”，反而不写入 known。

加载顺序风险低：`ModuleLoader` 按 `handlers/skills/buffs/passives` 加载，且 `.js` 排序执行，见 `backend/.../ModuleLoader.java:46-60`。`character/talent.js` 会早于 `skill/00`/`04`，`Skill` 早于 `Passive`。只要引用发生在函数运行时，`Passive.ownedSpecs -> Talent.derivedPassives -> Skill.loadSpecAny` 没有硬循环；A1 阶段需保留 `typeof Talent !== 'undefined'` 容错。

修改建议：
统一 `ownedSpecs` item 契约，例如 `{id, kind:'passive', effect, modifiers/match/patch, level}`，不要 `{spec: ...}` wrapper 直接流入 `Skill.ownedPassives`。A1 测试要覆盖 `applyPassivePatches` 消费 shape。

**2. 洗点后 stat_mod modifier 残留: 错误 / 阻塞**

依据：
`_registerOne` 只删除同一个当前 `spec.id` 对应的 modifier，再加新 modifier：`04_passive_lib.js:69-72`。`registerStatMods` 只遍历当前存在的 stat_mod spec：`:2-15`。如果洗点后 `Talent.derivedPassives` 不再返回 `talent_elem_root`，就不会调用 `_registerOne`，旧 `passive_talent_elem_root_<entity>` 永远不会被 remove。

`applySpec` 只调用 `Passive.registerStatMods` + `engine.recalculate`，在 `specialization.js:187-201`。`recalculate` 只能重算已注册 modifier，不能知道哪个旧 passive modifier 应删除。`entity.loaded` 虽然先复位 base 并重注册，见 `recalculate_hooks.js:23`、`:56`、`:107-111`，但运行时洗点不会走 load 复位，也不会清 modifier chain。

追溯 override 同 nodeId 且同 `spec.id=talent_elem_root` 时，先删后加能兜住。但 inline -> ref、`requires_spec` 不满足、节点从 derive 集消失、respec 清空，都会残留。

修改建议：
实现前必须给 `Passive.registerStatMods` 加“上一次注册的 passive modifier id 集”清理机制。可在 `Passive` 内存维护 `entityId -> Set(modId)`：每次注册前计算 next ids，remove previous - next，再对 next 做 `_registerOne`。或者提供一个按 source/prefix 清除 passive talent modifier 的明确 API。A5 的 `talent_respec` 不能只“applySpec 摘被动”。

**3. applyNode 与 _applyPatch: 有风险**

依据：
`resolveSpec` 顺序是 level scaling → node → passive patches → specialization patches，见 `00_skill_lib.js:171-178`。`applyNode` 当前 no-op 在 `:191`。`_applyPatchValue` 对 number + number 做加法，否则整体赋值，见 `:273-282`。

所以：
`targeting.pattern` 是数组，patch 会整体替换，符合 AOE 预期。
`damage.add: 8` 会把 base fireball 的 `damage.add=10` 加到 18，因为 `fireball.yaml:7-14` 已有 `add:10`。这也符合 plan A3 写的“damage.add +8”，但 evolution yaml 示例里 `patch: { name:"炎爆", "damage.add":8 }` 很容易被实现者读成“设为 8”。

修改建议：
plan/spec 要明确 patch 语义是“数值增量、非数值覆盖”，并把测试期望写死为 fireball `damage.add == 18`。如果炎爆期望是 8，当前 `_applyPatch` 不能表达，需要另设覆盖语法，但这会扩大范围，不建议。

**4. 派生点数边界: 成立但需补测试**

依据：
点数按 `Experience.level` 派生，`debug.set_level` 会更新 Experience/Character 并调 `applySpec`，见 `leveling.js:39-51`。`available=max(0,total-spent)` 能正确展示降级超额为 0。

风险：
unlock action 必须用同一个 `Talent.available(entity)`，不能自己算 `total - spent > 0` 后又在快照 clamp，否则快照和拒绝态会漂移。

修改建议：
A5 增加 `spent > total` 时 `talent_unlock` 拒绝测试，断言 message 和快照 `available=0` 一致。

**5. action 校验链: 有风险**

依据：
plan 的未专精、root 初始化、连通性、`requires_spec`、点数校验方向正确，和 spec §7 一致。

主要坑：
`talent_place_orb` 计划写“找/建 known 条目”。这与 spec “技能槽节点火球术，插球进 `known[skill].node`”不完全等价。若技能未学，建条目会绕过获取技能流程，把未学 active 塞进 Skillbook。现有 `ownerInstance` 只从 known 找 node/level，见 `00_skill_lib.js:154-168`；技能书 UI 也只渲染 known，见 `ui/skillbook.js:1-31`。除非 spec 明确“主动节点授予技能”，技能槽插球应拒绝未学技能，而不是建条目。

另一个缺口：spec §2.2 包含“主动节点”，但 plan 的 `resolveTalentEffects` 只覆盖 passive 三型和 skill_slot，没有主动授予实现。若 Feature #7 范围真要求主动节点，这是漏项；若 demo 不做，应在 plan 明确 active node out-of-demo 或延后。

修改建议：
`talent_place_orb` 改为：槽已 unlock、orb 足够、`Skillbook.known` 中已存在该 skill，否则拒绝“尚未学会该技能”。另补 active node 的范围声明或任务。

**6. 快照态机: 有风险**

依据：
7 态枚举覆盖 spec §3.3/§8，尤其 `filled-node-relocked` 是必要的。`known[i].node` 永久运行，和槽点位解耦，spec 要求很明确。

风险：
`effectSummary` 如果在 `ui.render_talent_tree` 里另写一套 override/requires 逻辑，很容易和 `Talent.derivedPassives` 运行时逻辑漂移。`slot-filled`/`filled-node-relocked` 也必须以 `Skillbook.known[skill].node != null` 判，而不是只看 `unlocked`。

修改建议：
抽公共函数：`Talent.nodeState(entity,node)`、`Talent.effectiveNodeEffect(entity,node)` 或至少 `ui.render_talent_tree` 调 `Talent.derivedPassives` / 同源 resolver。不要在 UI handler 复制一套规则。

**7. 快照强类型扩展: 基本成立，但接线量大**

依据：
`WorldSnapshot` 当前字段到 `specialization` 结束，见 `WorldSnapshot.java:6-25`；静态工厂 `characterSelect`、`characterCreate`、`inGame` 在 `:76-99` 都要同步补 `talentTree`。`SnapshotService.buildInGameSnapshot` 在 `SnapshotService.java:91-103` 串接 skillbook/specialization；`buildSpecialization` 事件回填模式在 `:127-146`。`ScriptRuntime` 工厂模式在 `ScriptRuntime.java:173-211`。

风险：
非 inGame 工厂必须传 `null`，否则 record 构造参数错位。Java record 嵌套多，A7 容易漏 `WorldSnapshot.inGame` 参数、Jackson 字段、`newTalent*` 工厂。

修改建议：
A7 拆成两个小任务更稳：先 Java records/factories/service 空块编译通过，再 JS `ui.render_talent_tree` 填 nodes。测试先断 `talentTree=null` 和 JSON 字段名，再断节点态。

**8. 任务粒度/顺序/TDD: 有风险**

A1 先做是正确的；A8 阶段闸也合理。

但 A3 有隐藏依赖错误：plan 说 `Talent.buildEvolutionIndex()` 遍历所有 `talents/*.yaml`。现有 JS 只有 `engine.loadYaml(relativePath)` 单文件读取，见 `ScriptRuntime.java:113-126`，没有 list directory API。`ModuleLoader` 只加载 `.js` 脚本，见 `ModuleLoader.java:46-60`；hot reload 监听 yaml 但也只重新执行 JS，见 `HotReloader.java:76`、`:100-118`。

修改建议：
实现前改 plan：本期直接 `Talent.loadTalentTree(root)` 时把该 root 的 `evolutions` 注册进全局索引；或新增一个显式 manifest，比如 `talents/index.yaml` 列出 roots。不要写“遍历所有 yaml”除非同时扩 `EngineApi`，但这会扩大 Java surface。

**9. 范围/回归: 有风险**

守住了不碰 `ModifierChain`、golden、sim、战斗管线的大方向。`_applyPatch` 复用现有行为，也避免结构性技能改写。

遗漏回归点：
`ui.render_skillbook` 当前直接读 base yaml name/description，不会显示进化名“炎爆”，见 `ui/skillbook.js:22-28`。spec §3.3 要求洗点后技能书/战斗里火球仍显示进化名。即使 `resolveSpec` 战斗时能 applyNode，Skillbook 快照仍会显示“火球术”。plan 只说 `SkillbookPanel.vue` 树入口，没要求 `ui.render_skillbook` 用 `Skill.applyNode` 或 `Talent.evolutionPatch` 回填进化名。

修改建议：
新增任务：skillbook 快照对 `known[i].node` 应展示进化后的 name/description/icon（至少 name），或者提供 evolution display 字段。否则验收“洗点后已进化技能仍显示进化名”过不了。

**实现前必须先解决的阻塞项**

1. 给 `Passive.registerStatMods` 设计旧 talent stat modifier 清理机制，覆盖 respec、requires_spec 失效、inline/ref 切换、derive 空集。
2. 明确 `Passive.ownedSpecs` 返回契约，保证 `registerStatMods`、`Passive.owns`、`Skill.ownedPassives/applyPassivePatches` 消费同一种展开对象。
3. 修正“遍历 `talents/*.yaml`”方案；现有 `engine.loadYaml` 不支持目录枚举。
4. 明确 evolution patch 数值语义，并把 fireball `damage.add` 期望写进测试。
5. `talent_place_orb` 不应自动创建未学技能的 known 条目，除非同时实现主动节点授予技能并写清校验。
6. 补 skillbook 快照进化名/显示回填，否则 §3.3 展示验收会失败。