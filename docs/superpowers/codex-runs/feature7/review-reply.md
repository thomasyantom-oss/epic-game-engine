**总评: REQUEST-CHANGES**

这份 spec 的方向成立，但把“天赋授予隐式被动”和“追溯重推”说得过于轻。现有代码没有一个真正的“owned passive 多来源聚合层”，而 #7 正好依赖这个接缝。实现前必须先把被动 ownership / stat_mod 注册 / skill_patch 枚举 / handler 守卫统一掉，否则会出现 stat 生效、skill patch 不生效，或 handler 仍按 `Skillbook.known` 判断而失效的分裂。

**1. §4 “天赋节点效果走 #5 被动”**

Verdict: **有风险，当前声称不成立**

依据：`Skill.ownedPassives` 只扫 `Skillbook.known`，见 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:237)。`Passive.registerStatMods`、`Passive.owns` 也只扫 `known`，见 [04_passive_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:2) 和 [04_passive_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:18)。现有 handler 被动 `lifesteal_on_kill` 通过 `Passive.owns(killerId, "lifesteal_on_kill")` 守卫，所以 talent 隐式被动如果不进 `known`，handler 类不会生效。

修改建议：spec 要明确新增一个统一 `Passive.sources(entity)` / `Passive.ownedSpecs(entity)` 聚合层，包含 `Skillbook.known` + talent-derived passives。`Skill.ownedPassives`、`Passive.registerStatMods`、`Passive.owns` 必须全部改走它。更省的做法不是把 talent passive 塞进 `Skillbook.known`，因为会污染技能书 UI；可以存在 `TalentTree.unlocked` 派生来源，但对 #5 暴露成同一 owned passive 集合。

**2. §4.1 追溯重推**

Verdict: **有风险**

依据：`entity.loaded` 会复位 base，再 `applySpec`，然后注册 passive stat mods 并 `engine.recalculate`，见 [recalculate_hooks.js](C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:23)、[recalculate_hooks.js](C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:107)。stat_mod 只要 modifier id 稳定且旧 modifier 被 remove，能自洽。但 skill_patch 是运行时 `resolveSpec` 动态扫 owned passives，顺序在 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:171)。handler 类当前是全局 listener + `Passive.owns` 守卫，不存在“摘旧挂新”注册过程，但 ownership 守卫若不改会残留旧效果或新效果不生效。

修改建议：不要设计成“授予/摘除被动实例”来驱动 handler 注册；应设计成“TalentTree 只存 allocation，owned passive 每次 derive”。专精变化后只需要 recalc stat mods + 持久化，不需要动态 unregister JS listener。关键是 `Passive.owns` 必须判断当前 derive 后的有效 passive，而不是 `known`。

**3. §3.3 三层可逆性解耦**

Verdict: **成立但有边界风险**

依据：`Skillbook.known[i].node` 已是技能实例永久状态，`Skill.ownerInstance` 从 `known` 读 node，见 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:151)。洗点不动 `known[i].node` 在数据上可行。

风险：如果洗掉技能槽点位，技能仍保持进化态，这和“技能槽节点是插球入口”不矛盾，但 UI/规则必须明确：槽点只 gate “插球动作”，不 gate “已获得进化是否可用”。否则玩家会困惑“树里没点这个槽，火球为什么还是炎爆”。

修改建议：spec 明确 `skill_slot.unlocked` 只控制 place_orb availability；`known.node != null` 控制技能运行态和 slot-filled 展示。洗点后树节点可回 locked，但技能书仍显示进化名/效果。

**4. `applyNode` 落地**

Verdict: **基本成立，有顺序决策风险**

依据：`resolveSpec` 顺序是 level → node → passive patch → specialization patch，见 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:171)。`applyNode` 目前 no-op，见 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:191)。`_applyPatch`/`_matchSkill` 可复用，见 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:253)、[00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:267)。

风险：spec 说“进化 patch 挂 talents/<spec>.yaml skill_slot 节点上”，但 `applyNode(spec,node)` 只有 evolution id，没有 talent tree/root/path 上下文。仅凭 `node = pyroblast` 找不到它在哪棵专精树定义，除非建立 evolution id 全局索引或把 patch 放到技能 yaml。

修改建议：实现前二选一写死：要么 `Skillbook.known[i].node` 存 `{tree, evolution}`，要么 `applyNode` 通过 `Specialization.path[0]` 推断 tree 并查 `talents/<root>.yaml`。推荐把 evolution patch 做成全局唯一 id 索引，并在 load 时校验重复。

**5. 天赋点来源**

Verdict: **有风险，spec 不够清楚**

依据：等级来源同时写 `Experience.level` 和 `Character.level`，`debug.set_level` 两边都改并调用 `applySpec`，见 [leveling.js](C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:39)。普通升级触发 `entity.level_up`，见 [leveling.js](C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:84)。

问题：`TalentTree.points` 如果存“可用点”，它不是等级派生真相源；debug 降级、跳级、洗点都会产生歧义。正确模型应是 `totalPoints = f(level)` 派生，`spent = unlocked.length`，`available = total - spent`。除非支持点数奖励道具，否则不要存 available 作为真相源。

修改建议：`TalentTree` 存 `unlocked`，快照吐 `totalPoints/spentPoints/availablePoints`。如果保留 `points` 字段，spec 必须定义它是缓存还是 truth，并规定 debug 降级时已花点超过总点的处理。

**6. 门控 + 持久化**

Verdict: **部分成立，有缺口**

依据：`Specialization.path` 是 list，选择时只 append 且不可洗，见 [specialization.js](C:/workplace/epic/mods/base-rules/handlers/character/specialization.js:221)、[specialization.js](C:/workplace/epic/mods/base-rules/handlers/character/specialization.js:271)。组件持久化方向与现有 `Skillbook`/`Specialization` 一致，但 spec 没说明 schema/创建角色时如何挂 `TalentTree`，也没说明 load 时 root 与 `Specialization.path[0]` 不一致如何修复/拒绝。

修改建议：后端 action 校验必须包括：`path.size > 0`、`TalentTree.root == path[0]` 或首次初始化 root、node 属于 root tree、`requires_spec` 在 path 中、连通性、重复 unlock。未专精前快照可返回 `talentTree: null`，action 必须拒绝。

**7. 快照/前端契约**

Verdict: **有风险，§8 不够实现级**

依据：当前快照是强类型 `WorldSnapshot` record，已有 `Skillbook` 和 `Specialization` 字段；不是 JS handler 随便加 JSON 块。`SnapshotService` 通过 `buildSkillbook`/`buildSpecialization` 构造固定字段；`ScriptRuntime` 也需要 newXXX factory。前端 `SnapshotRenderer` 目前只开 `skillbook`/`spec` 两个 modal，`SkillbookPanel` 的“树”只是灰态占位。

缺口：5 种态不够。至少还需要 `hidden`/`blocked-by-spec`/`insufficient-points`/`unlocked-slot-missing-orb`/`filled-but-node-locked-after-respec` 这类 UI 决策态，或给前端足够字段自行推导。树布局也缺坐标或层级/排序；只有 nodes+edges 前端很难稳定画“瘦高多叉树”。

修改建议：快照节点至少给：`id,label,icon,type,parents,children,x/y或tier/order,state,selected?,requiresSpec,effectSummary,actions{canUnlock,canPlaceOrb,reason},slot{skill,orb,evolution,filled}`，并给 `orbInventory`、`points` 三元组。

**8. 与母文档/既有约定冲突**

Verdict: **基本不冲突，但要收紧措辞**

§1 覆盖母文档 §1.6/§1.10 是有意改向，OK。§1.2 “结构性改写仍走 bespoke JS、不进 patch 管道”也在 spec §10 保留，OK。但 §4 说 handler 被动“三通路全可用”容易被误读成 talent handler 动态注册；现有模式是全局注册 + ownership 守卫。建议改成“handler JS 仍全局加载，是否生效由统一 owned passive 集合守卫”。

**9. 范围**

Verdict: **有风险，建议切片**

作为单 feature 包含：新组件、新 yaml loader、天赋 action、orb inventory/debug、隐式被动来源重构、applyNode、专精 override 重推、强类型快照扩展、全新树画布前端、demo 树和回归测试。这个范围偏大。

建议切成两步：  
1. 后端机制闭环：TalentTree allocation、点数派生、隐式 passive 聚合、applyNode、actions、快照原始数据、测试。  
2. 前端树体验 + demo 内容：modal、布局、说明面板、orb 展示、视觉态。

**实现前必须解决的阻塞项**

1. 定义统一 passive ownership 聚合层，明确 talent-derived passive 不进 `Skillbook.known` 但被 `ownedPassives/registerStatMods/owns` 同时承认。  
2. 明确 `TalentTree.points` 是否为 truth；推荐不存 available，按等级派生总点。  
3. 明确 `applyNode` 如何从 `known[i].node` 找到 evolution patch，避免只有 evolution id 但没有 tree 上下文。  
4. 扩充快照契约到强类型 Java record/factory 级别，并补足布局/状态/reason 字段。  
5. 明确洗点后“槽未点但技能已进化”的后端规则和前端展示规则。