# Feature #7 — 专精天赋树(Skill Tree) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **本仓库实现层是 Codex(headless)。** 本 plan 同时是 `codex exec` 的 prompt。Codex 实现 + 跑测试,**不自己 commit**;Claude review diff → 用户 verify → Claude 提交。

**Goal:** 实现"专精天赋树"——Ch1 旗舰收官。天赋点(等级派生)点亮 per-专精的多叉树节点,灵魂球(分型材料)单独插技能槽把技能进化;节点效果全部复用 #5 被动 + #4 `known[i].node`,#7 只做获取/门控层。

**Architecture:** 先建**统一 owned-passive 聚合层**(#5 三处只扫 `Skillbook.known` 的 bug 是 #7 的地基,必须先修)。新增 `TalentTree{root,unlocked}` + `OrbPouch{type→count}` 两个结构性组件(像 `Specialization`,persistent tag 自动存)。天赋点不存真相源、按 `level` 派生。`talents/<spec>.yaml` 出树数据 + 全局进化索引;`applyNode` 查进化索引套内联 patch。追溯重推白嫖现有 `applySpec`→`registerStatMods` 链(改成走聚合层后,专精一变,三类被动自动重推)。强类型快照加 `talentTree` 块;前端新 `TalentTreePanel` 模态。

**Tech Stack:** Java 21 / Spring Boot(引擎 + 快照 record) · GraalJS(mods 逻辑) · SnakeYAML(数据) · Vue 3(前端) · JUnit(后端测试) · `mvn test` / `npm run build`。

**权威 spec:** `docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md`(含 Codex review 已纳的 §4.0/§6.1/§6.2/§8/§3.3/§10.1)。冲突以 spec 为准。

**Plan review:** 已纳 Codex plan review(`docs/superpowers/codex-runs/feature7/plan-review-reply.md`,REQUEST-CHANGES 6 阻塞项全解):① stat_mod modifier 残留清理(A1)/ ② `ownedSpecs` 统一契约(A1)/ ③ 进化索引改 manifest 不枚举目录(A3)/ ④ patch 增量语义写死测试(A3)/ ⑤ place_orb 拒未学技能(A5)/ ⑥ skillbook 快照回填进化名(新 Task A8)。另纳 nits:A7 拆 Java/JS 两步、快照态机用单一真相函数。

---

## 给实现者的 5 件套(Codex 必读)

### ① 验收标准(含边界 + 明确不做什么)
- **未专精**(`Specialization.path` 空):快照 `talentTree == null`;`action.talent_unlock` / `talent_place_orb` / `talent_respec` 全部后端拒绝(返回可读 message)。
- **专精后点根**:法师 L10 选 `elementalist` → 派生 `available=1` 天赋点 → `talent_unlock elem_root` 成功,授予的 `stat_mod` 被动生效(`PrimaryStats.法强 +5` 可见)。
- **连通性**:点与已点亮节点不相连的节点被拒;点亮一个节点后,其子节点变为 `unlockable`。
- **派生点数**:`total = level<10 ? 0 : floor((level-10)/2)+1`(L10→1,L12→2,L14→3…);`spent = unlocked.length`;`available = total - spent`。`debug.set_level` 跳级后 available 随 total 重算正确;**不存"可用点"当真相源**。
- **统一被动来源(阻塞项 1 核心回归)**:一个 `skill_patch` 类天赋被动(火球 `damage.add +5`)和一个 `handler` 类天赋被动(引用 `lifesteal_on_kill`)**都真生效**——证明 `Skill.ownedPassives` / `Passive.registerStatMods` / `Passive.owns` 三处都认 talent 来源,不只 `stat_mod` 生效。
- **技能槽 + 灵魂球**:`fireball_slot` 点亮后呈 `slot-empty`(黄);`debug.grant_orb 爆破 2` → `talent_place_orb fireball_slot` 成功 → `known[fireball].node = pyroblast`,节点呈 `slot-filled`(绿土星环),`OrbPouch.爆破` 扣 2;火球实战 `targeting.pattern` 变 AOE(`applyNode` 套进化 patch 生效)。
- **追溯重推**:`path` 推进到 `pyromancer` 后,`elem_root` 的授予被动按 `overrides.pyromancer` 替换(法强→火属性),**`unlocked` allocation 不变**;skill_patch / handler 类同样按新 path 重判生效。
- **三层可逆性**:`talent_respec` 退点(`unlocked` 清空、talent 被动全摘)、**`known[i].node` 进化记录不动**(火球仍是炎爆,见 §3.3 硬规则);灵魂球**无"取出"动作**(插了不可退);专精不可洗(#6 既定)。
- **load 往返**:点天赋 + 插球 + 推进专精后,持久化→重载,属性/被动/进化/点数**逐位一致**,无重启膨胀。
- **回归**:未专精角色、现有 #4/#5/#6 行为、战斗/sim 全不变。
- **不做**:真节点/数值/各职业各专精完整树(内容轮);二级专精完整加宽内容(只做最小覆写 demo);灵魂球真供给(Ch3,本期 debug 给);洗点收费(Ch5,本期免费);结构性技能改写(弹射等走 bespoke JS,不进 patch 管道);`applySkillLevelCurve` 维持 no-op;具名变换注册表(不建)。

### ② 修改边界
- **新增**:`mods/base-rules/talents/elementalist.yaml`(demo 树 + 进化索引)、`handlers/character/talent.js`(组件/派生点数/三 action/进化索引/`applyNode` 实现)、`handlers/ui/talent_tree.js`(快照回填)、`frontend/src/components/TalentTreePanel.vue`;后端测试类 `TalentTreeTest`、`TalentTreeSnapshotTest`。
- **可改**:`handlers/skill/04_passive_lib.js`(加 `ownedSpecs` 聚合层 + 三处改走它)、`handlers/skill/00_skill_lib.js`(`ownedPassives` 改走聚合层 + `applyNode` 实现)、`handlers/character/select.js`(建角挂 `TalentTree`/`OrbPouch`)、`handlers/character/recalculate_hooks.js`(`entity.loaded` ensure 两组件)、`WorldSnapshot.java`、`ScriptRuntime.java`、`SnapshotService.java`、`SnapshotRenderer.vue`、`SkillbookPanel.vue`(树入口)、`handlers/ui/skillbook.js`(进化名回填,A8)、`DebugController.java`(+`/orb`)。
- **别碰**:`ModifierChain.java` 引擎本体(只用现成 modifier type,不改 Java 链);战斗流程/动画/伤害管线;golden(`SkillFidelityTest` 不重生成);sim 包;`Specialization` 不可洗逻辑(#6);`allocate_point` 休眠语义。
- 允许就地微调不适配处,但偏离本 plan 的结构性决定要在回话里标注,等 review 判。

### ③ 验证方式
- 后端:`cd backend && mvn test`(全绿;新增测试类见 Task A8/A9 / B1)。
- 前端:`cd frontend && npm run build`(通过)。
- 测试环境无人工点击;前端以 build 通过 + 组件渲染态逻辑为准,真人 smoke 留用户。
- **分期闸**:阶段 1(后端,Task A1–A9)`mvn test` 全绿后,再上阶段 2(前端,Task B1–B3)。

### ④ 隐含产品偏好
- UI:复用 `Modal.vue` 外壳(继 #4/#5/#6 后又一租户);**粗边框 ≥2px,禁 1px 细边**(见 memory);不动 4 宫格布局;战斗中天赋面板**只读**。
- 树视觉:节点是**圆圈,不画 icon**(icon 只在选中弹出的说明面板里);颜色态——灰边=普通、黄边=技能槽空、**绿色"土星环"(同色环套圆、中间留空,非填充光晕)**=已插球;**双环(外再套一圈)=当前选中**。布局瘦高、从下往上、多叉。
- 说明面板:icon 左上 / 名字在 icon 右 / 下技能说明 / 再下"受影响"(被动·主动增强·所需灵魂球)/ 底部操作按钮。
- 文案:中文;插球前提示消耗;洗点不退灵魂球要说明;未专精入口提示"专精后解锁"或灰。
- 空/错态:`available=0` 时节点不可点且给 reason;`requires_spec` 不满足的节点 `hidden`;洗点后已进化技能仍显示进化名(§3.3)。

### ⑤ 现有代码约束(打开核对过,别只信描述)
- **#5 的 bug 实锤**:`Skill.ownedPassives`(`00_skill_lib.js:237`)、`Passive.registerStatMods`(`04_passive_lib.js:2`)、`Passive.owns`(`04_passive_lib.js:18`)**三处都只扫 `Skillbook.known`**。`handler` 被动(`lifesteal_on_kill`)靠 `Passive.owns` 守卫。talent 隐式被动不进 known,**不统一就会分裂**(stat 生效/patch/handler 失效)。**Task A1 先修这条,其它都依赖它。**
- **追溯重推白嫖现有链**:`Specialization.applySpec(entityId)`(`specialization.js:187`)已在 `choose_specialization`(`:272`)、`debug.set_level`(`leveling.js:48`)、`entity.loaded`(`recalculate_hooks.js:107`)三处调用,内部 `grantPassives → Passive.registerStatMods → engine.recalculate`。聚合层改造后,**专精一变,applySpec 重跑 registerStatMods(走 ownedSpecs)即自动重推 talent 被动**,无需新 re-derive 管道。skill_patch/handler 是 runtime derive(`resolveSpec`/`Passive.owns`),天然随 path 现算。
- **不要动态注册/注销 handler listener**:handler JS 全局常驻,是否生效**只由 `Passive.owns`(改走聚合层后)守卫**。专精变→旧 ref 不在 derive 集→自动失效,无残留。
- **组件持久化**:`TalentTree`/`OrbPouch` 是**非 base 结构性组件**(像 `Specialization`/`EquipmentSlots`),靠 `persistent` tag 自动存,**不进** `charSchema.baseComponents()` / `setBaseSelective`。建角(`select.js`)创建 + `entity.loaded` ensure(像 `specialization.js:151 ensureComponent` 那样防老存档缺组件)。
- **action 失败 result 通路已存在**(#6 已补):`SnapshotController.performAction` 把 event `success/message` 透成 `ActionResult`。talent action 用 `event.set("success",false); event.set("message",…)` 即可回显(对齐 `Specialization.reject`)。
- **JS↔Java 快照桥**:record 由 `ScriptRuntime` 暴露 `newXxx` 工厂(`@HostAccess.Export`,见 `newSpecPathNode`/`newSpecOption`),`SnapshotService.buildXxx` fire `ui.render_xxx` 事件 + JS 回填预置 list(见 `buildSpecialization`/`ui/specialization.js`)。**新块照抄此模式**,未专精 `buildTalentTree` 返回 `null`。
- **快照是强类型 record**:`WorldSnapshot`(`:6`)+ 末尾静态 `of(...)`(`:94`)要同步加 `talentTree` 字段。
- **modifier 幂等**:`ModifierChainService.addModifier` 每次自动 recalculate,按 id 先删后加。stat_mod talent 被动复用 `Passive._registerOne` 的 `passive` type(priority 70)。
- **`debug.grant_passive`/`set_level`/`grant_orb` 模式**:JS `engine.on("debug.xxx")` + `DebugController` 加 `@PostMapping`,fire 后 `persistence.save`。`/orb` 照 `/passive`(`DebugController:60`)抄。

---

## File Structure(决策锁定)

| 文件 | 职责 |
|------|------|
| `mods/base-rules/talents/index.yaml` | manifest:`roots:[elementalist]`(进化索引枚举入口,代目录遍历) |
| `mods/base-rules/talents/elementalist.yaml` | 法师元素 demo 树:`root` + `nodes[]`(id/label/icon/parents/tier/order/effect/overrides/requires_spec/skill_slot)+ `evolutions{}`(id→{name,icon,description,patch}) |
| `mods/base-rules/handlers/character/talent.js` | **核心**:`TalentTree`/`OrbPouch` ensure;`totalPoints(level)` 派生;`loadTalentTree`/`talentNode`/连通性;`resolveTalentEffects(entity)`(节点×path→被动 ref/inline)+ 进化索引 `evolutionPatch(id)`;`applyNode` 真实现;`action.talent_unlock/respec/place_orb`;`debug.grant_orb` |
| `mods/base-rules/handlers/skill/04_passive_lib.js` | **新增 `Passive.ownedSpecs(entity)` 聚合层**(known 被动 + talent derive 被动,带 id/level/effect);`registerStatMods`/`owns` 改走它 |
| `mods/base-rules/handlers/skill/00_skill_lib.js` | `ownedPassives` 改走 `Passive.ownedSpecs`;`applyNode(spec,node)` 查进化索引套 `_applyPatch` |
| `mods/base-rules/handlers/character/select.js` | 建角创建 `TalentTree{root:null,unlocked:[]}` + `OrbPouch{}` |
| `mods/base-rules/handlers/character/recalculate_hooks.js` | `entity.loaded` ensure 两组件(防老存档缺) |
| `mods/base-rules/handlers/ui/talent_tree.js` | `ui.render_talent_tree`:组 nodes/态/points 三元组/orbInventory/effectSummary(**调 `Talent.nodeState`/`derivedPassives` 单一真相,不复制规则**) |
| `mods/base-rules/handlers/ui/skillbook.js` | 改:`known[i].node!=null` 时用进化 `name/icon/description` 回填(阻塞项 6,§3.3 展示) |
| `backend/.../snapshot/WorldSnapshot.java` | +`TalentTree`/`TalentNode`/`TalentPoints`/`OrbStack`/`TalentSlot`/`TalentActions` records + `talentTree` 字段 + `of(...)` |
| `backend/.../script/ScriptRuntime.java` | +`newTalentTree*` 工厂桥 |
| `backend/.../snapshot/SnapshotService.java` | +`buildTalentTree(playerId)` fire `ui.render_talent_tree`;未专精返回 null |
| `backend/.../debug/DebugController.java` | +`POST /api/debug/orb/{token}?type=&count=` |
| `frontend/src/components/TalentTreePanel.vue` | 多叉树画布(圆节点/土星环/双环选中/连边/颜色态)+ 说明面板 + 点亮/插球/洗点按钮 |
| `frontend/src/components/SnapshotRenderer.vue` | `talent` 模态租户 + 入口 |
| `frontend/src/components/SkillbookPanel.vue` | `树` 灰占位升级为真入口(emit 开 talent 模态) |
| `backend/src/test/.../TalentTreeTest.java`(+`TalentTreeSnapshotTest`) | 见 Task A8 / B1 |

---

## 数据形状(锁定,供各 Task 引用)

### `talents/elementalist.yaml`(demo)
```yaml
root: elementalist            # 这棵树的 root 专精节点 id(= #6 path[0])
nodes:
  - id: elem_root
    label: "元素精通"
    icon: "★"
    parents: []
    tier: 1
    order: 0
    effect: { passive: { stat_mod: { 法强: 5 } } }
    overrides:
      pyromancer: { passive: { stat_mod: { 火属性: 8 } } }   # 追溯重推 demo

  - id: fireball_slot
    label: "火球术"
    icon: "🔥"
    parents: [elem_root]
    tier: 2
    order: 0
    skill_slot: { skill: fireball, orb: { type: 爆破, count: 2 }, evolution: pyroblast }

  - id: ember_passive
    label: "余烬精通"
    icon: "盾"
    parents: [elem_root]
    tier: 2
    order: 1
    requires_spec: pyromancer            # 门控 demo:非火专精 hidden
    effect: { passive: { ref: lifesteal_on_kill } }   # handler 类(走 #5 现成 .js)

  - id: fb_damage
    label: "火球强化"
    icon: "+"
    parents: [fireball_slot]
    tier: 3
    order: 0
    effect: { passive: { skill_patch: { match: { skill: fireball }, patch: { "damage.add": 5 } } } }

evolutions:
  pyroblast:
    name: "炎爆"
    icon: "🔥"
    description: "火球进化:一排 AOE,灼烧更强"
    patch: { name: "炎爆", "damage.add": 8, "targeting.pattern": [[0,0],[0,1],[0,-1]] }
```
- **节点 effect 三型**:`passive.stat_mod`(inline 数值)、`passive.skill_patch`(inline match/patch)、`passive.ref`(引用 `passives/<id>.yaml`,handler 走现成 .js)。
- **inline 被动合成 id** = `talent_<nodeId>`(给 modId / owns 用);`ref` 被动保留被引用 id。
- **patch 语义 = `_applyPatch` 现行语义(阻塞项 4):数值字段相加(增量)、非数值字段整体替换。** 故 `damage.add: 8` 作用在 base fireball 的 `damage.add=10` 上 → **结果 18**(非"设为 8");`targeting.pattern` 数组整体替换为 AOE;`name` 替换。测试期望按此写死(见 A3)。不引入"覆盖语法",避免扩面。

### 进化索引 manifest(阻塞项 3:现有 JS 无目录枚举 API)
`engine.loadYaml` 只能单文件读,**不能遍历 `talents/*.yaml`**。改用 manifest:
```yaml
# talents/index.yaml —— 列出所有 talent 树 root(= #6 各 tier1 专精)
roots: [elementalist]      # demo 只一棵;内容轮往这加
```
`Talent.buildEvolutionIndex()` 读 `index.yaml` 的 roots,逐个 `loadTalentTree(root)` 把其 `evolutions{}` 注册进全局表(load 期校验 id 不重复)。`applyNode` 首次用时惰性触发 `buildEvolutionIndex`(幂等缓存)。

### `TalentTree` 组件
```
TalentTree { root: <string|null>, unlocked: [nodeId...] }   # 唯一 allocation 真相源,不存 points
```
### `OrbPouch` 组件
```
OrbPouch { <orbType>: <count>, ... }   # 分型灵魂球库存
```
### 派生点数(纯函数,放 talent.js)
```
totalPoints(level) = level < 10 ? 0 : Math.floor((level - 10) / 2) + 1
spent = unlocked.length ; available = max(0, total - spent)
```

---

## 阶段 1 — 后端机制闭环(Task A1–A8)

### Task A1: 统一 owned-passive 聚合层(地基,先做)

**Files:** Modify `mods/base-rules/handlers/skill/04_passive_lib.js`、`mods/base-rules/handlers/skill/00_skill_lib.js`;Test `backend/src/test/.../TalentTreeTest.java`(新建,先放本任务用例)。

**统一契约(阻塞项 2):`ownedSpecs` 返回的每一项都是「已展开的扁平对象」,不要 `{spec:...}` wrapper:**
```
{ id, kind:'passive', effect:'stat_mod'|'skill_patch'|'handler', modifiers?, match?, patch?, level }
```
`applyPassivePatches`(`00_skill_lib.js:195`)直接读 `.effect/.match/.patch`,故 `Skill.ownedPassives` 必须返回这个扁平 shape。ref 类被动:`Skill.loadSpecAny(ref)` _toJs 后**取其 effect/modifiers/match/patch 摊平**(连同 ref 的 id)。

- [ ] **Step 1: 写失败测试** `TalentTreeTest.ownedSpecs_mergesKnownAndTalent`
  断言:实体 `Skillbook.known` 含一个 passive + 一个 talent-derived passive → `Passive.ownedSpecs(entity)` 返回**两者合并的扁平项**(各带 `id`/`effect`/`level`/`kind=passive`,skill_patch 项带 `.match/.patch`);`Skillbook.known.size` 不变(不污染)。

- [ ] **Step 2: 跑测试确认失败** Run: `cd backend && mvn test -Dtest=TalentTreeTest#ownedSpecs_mergesKnownAndTalent` → FAIL(`ownedSpecs` 未定义)。

- [ ] **Step 3: 实现 `Passive.ownedSpecs(entity)`**(`04_passive_lib.js`)
  - A) `Skillbook.known` 里 `kind==passive` 的 spec → 摊平成上述扁平项(带 `level`)。
  - B) talent 来源:`if (typeof Talent !== 'undefined') Talent.derivedPassives(entity)`(A4 提供;A1 先容错,未定义返回空)。`derivedPassives` 已返回扁平项(ref 类先 loadSpecAny 摊平,inline 合成 `id:'talent_'+nodeId`)。
  - 合并去重(同 id 取一)。
  - **三处 effect/ownership consumer 改走它**:`registerStatMods` 遍历 `ownedSpecs` 中 `effect=='stat_mod'` 调 `_registerOne`(+ 下方清理机制);`owns(entityId,id)` 判 `ownedSpecs(store.get(entityId))` 含该 id;`Skill.ownedPassives(ctx)` `return Passive.ownedSpecs(ctx.caster)`(扁平 shape,`applyPassivePatches` 消费不变)。
  - ⚠️ **`grant` / `setSkillLevel`(`:33`/`:53`)是 Skillbook mutator,不是 effect/ownership consumer——仍扫 `Skillbook.known`,不改走 ownedSpecs**(否则 debug grant 一个 talent-derived ref 会被误判"已拥有"而不写入 known)。
  - 保持 `Passive.owns` 签名 `(entityId, passiveId)`。

- [ ] **Step 3b: stat_mod modifier 残留清理(阻塞项 1,关键)**
  写失败测试 `talentStatMod_removedAfterRespec`:点亮 `elem_root`(法强+5 生效)→ 清空 `unlocked` 后调 `registerStatMods` → `PrimaryStats.法强` **回到基线**(modifier 被移除,不残留)。
  实现:`Passive` 内存维护 `_registered = { entityId: [modId...] }`。`registerStatMods(entityId)` 改成:先算 `next`(本次 ownedSpecs 的 stat_mod modId 集),`engine.removeModifier` 掉 `previous − next`,再对 `next` 调 `_registerOne`,存回 `_registered[entityId]=next`。这样 respec / requires_spec 失效 / inline→ref 切换 / derive 空集 全部不残留。`engine.recalculate` 由调用方(applySpec)兜。

- [ ] **Step 4: 跑测试确认通过** Run: `mvn test -Dtest=TalentTreeTest`(本任务用例)→ PASS。`mvn test -Dtest=PassiveTest`(或现有被动测试类)确认 #5 无回归。

- [ ] **Step 5:** 回话标注 A1 完成。

### Task A2: `TalentTree` / `OrbPouch` 组件 + 派生点数

**Files:** Create `handlers/character/talent.js`;Modify `handlers/character/select.js`、`recalculate_hooks.js`;Test `TalentTreeTest`。

- [ ] **Step 1: 失败测试** `points_derivedFromLevel`:实体 L10→`Talent.totalPoints(10)==1`;L12→2;L9→0;`available = total - unlocked.length`,clamp≥0。
- [ ] **Step 2: 确认失败** Run: `mvn test -Dtest=TalentTreeTest#points_derivedFromLevel` → FAIL。
- [ ] **Step 3: 实现** `talent.js` 起 `var Talent = {...}`:`ensureComponents(entity)`(无则 `engine.newComponent("TalentTree")` set root=null + `unlocked`=newList、`OrbPouch` 空);`totalPoints(level)`/`spent(entity)`/`available(entity)`;`loadTalentTree(specRoot)`(`engine.loadYaml("talents/"+specRoot+".yaml")` _toJs 缓存)、`talentNode(root,id)`。`select.js` 建角后调 `Talent.ensureComponents`;`recalculate_hooks.js` `entity.loaded` 末尾 `if (typeof Talent!=='undefined') Talent.ensureComponents(entity)`。
- [ ] **Step 4: 确认通过** Run: 同上 → PASS;`mvn test -Dtest=SpecializationTest` 无回归(load 路径动过)。
- [ ] **Step 5:** 回话标注 A2 完成。

### Task A3: 全局进化索引 + `applyNode` 实现

**Files:** Modify `handlers/character/talent.js`、`handlers/skill/00_skill_lib.js`;Create `talents/index.yaml`、`talents/elementalist.yaml`(先放 evolutions + fireball_slot 节点);Test `TalentTreeTest`。

- [ ] **Step 1: 失败测试** `applyNode_appliesEvolutionPatch`:base fireball spec(`damage.add=10`)经 `Skill.applyNode(spec, "pyroblast")` 后 —— `targeting.pattern == [[0,0],[0,1],[0,-1]]`、**`damage.add == 18`(增量 10+8,见数据形状段 patch 语义)**、`name=="炎爆"`;`applyNode(spec, null)` 原样返回。
- [ ] **Step 2: 确认失败** → FAIL(`applyNode` 仍 no-op)。
- [ ] **Step 3: 实现**
  - `Talent.buildEvolutionIndex()`(阻塞项 3):读 `talents/index.yaml` 的 `roots`,逐个 `loadTalentTree(root)` 收 `evolutions{}` 汇成全局表,**校验 id 不重复**(重复 log/throw)。惰性 + 幂等缓存。
  - `Talent.evolutionPatch(id)` 返回该进化的 `patch`(无则 null)。
  - `00_skill_lib.js` `applyNode(spec,node)`:`if(!node) return spec; if(typeof Talent==='undefined') return spec; Talent.buildEvolutionIndex(); var p = Talent.evolutionPatch(node); if(p) this._applyPatch(spec, p); return spec;`(复用现成 `_applyPatch`:`damage.add` 数值相加、`pattern` 数组整体替换、`name` 替换——已核对 `_applyPatchValue:281` 分支)。
- [ ] **Step 4: 确认通过** → PASS。
- [ ] **Step 5:** 回话标注 A3 完成。

### Task A4: `resolveTalentEffects` + derive 被动(接回 A1 聚合层)

**Files:** Modify `handlers/character/talent.js`、`talents/elementalist.yaml`(补全 nodes/overrides);Test `TalentTreeTest`。

- [ ] **Step 1: 失败测试** 三连:
  - `derive_statMod`:`unlocked=[elem_root]` + path=`[elementalist]` → `Talent.derivedPassives(entity)` 含合成 `{id:'talent_elem_root', effect:'stat_mod', modifiers:{法强:5}}`;经 `applySpec` 后 `PrimaryStats.法强` +5。
  - `derive_override`:path 推进到 `[elementalist,pyromancer]` → 同节点 derive 变 `{火属性:8}`(法强不再加),allocation 不变。
  - `derive_skillPatch_and_handler`:`unlocked` 含 `fb_damage`(skill_patch)→ fireball resolveSpec `damage.add` +5;`unlocked` 含 `ember_passive`(ref handler,需 path 含 pyromancer 满足 requires_spec)→ `Passive.owns(entity,'lifesteal_on_kill')==true`。
- [ ] **Step 2: 确认失败** → FAIL。
- [ ] **Step 3: 实现** `Talent.derivedPassives(entity)`:取 `TalentTree.unlocked` × `Specialization.path`;每节点 `effect.passive` 解析为**扁平项**(对齐 A1 契约):`ref`→`Skill.loadSpecAny(ref)` _toJs 后摊平 `{id:ref, kind:'passive', effect, modifiers/match/patch}`;`stat_mod`→`{id:'talent_'+nodeId, kind:'passive', effect:'stat_mod', modifiers}`;`skill_patch`→`{id:'talent_'+nodeId, kind:'passive', effect:'skill_patch', match, patch}`。**override 解析**:节点 `overrides[specId]` 命中当前 path 中某 specId 时,用覆写后的 effect。**requires_spec 不满足的节点跳过**(不 derive = 自动失效)。**`skill_slot` 节点不产被动**(它只 gate 插球,效果走 `known.node`+applyNode)。A1 的 `ownedSpecs` 调用本函数(去掉 A1 容错占位)。
  - **范围声明(阻塞项 5 第二点):spec §2.2 的"主动节点直接授予技能"本期 demo 不做**(`effect.active` 不实现);demo 只覆盖 passive 三型 + skill_slot。主动授技能留内容轮/后续(否则牵出"未学技能进 Skillbook"的获取流程)。
- [ ] **Step 4: 确认通过** → PASS;`mvn test -Dtest=TalentTreeTest,SpecializationTest,PassiveTest` 全绿。
- [ ] **Step 5:** 回话标注 A4 完成。

### Task A5: 三个 action + 校验链

**Files:** Modify `handlers/character/talent.js`;Test `TalentTreeTest`。

- [ ] **Step 1: 失败测试** `actions`:
  - `unlock_rejectsWhenUnspecialized` / `unlock_rejectsDisconnected` / `unlock_rejectsNoPoints` / `unlock_rejectsRequiresSpec`(各设 `success=false` + message,状态不变)。
  - `unlock_rejectsWhenSpentExceedsTotal`(阻塞项 4):降级致 `spent>total`(`available==0`)→ `talent_unlock` 拒,且与快照 `available==0` **一致**(同一个 `Talent.available`)。
  - `unlock_success`:满足条件 → `unlocked` 加 id、available −1、talent 被动生效。
  - `place_orb_success`:槽已点 + 技能**已在 known** + `OrbPouch` 够 → 扣球、`known[fireball].node='pyroblast'`;`place_orb_rejectsSlotNotUnlocked` / `rejectsNoOrb` / **`rejectsSkillNotLearned`**(技能不在 known → 拒"尚未学会该技能",不建条目)。
  - `respec_clearsPointsKeepsEvolution`:`talent_respec` → `unlocked` 空、talent 被动全摘、**`known[fireball].node` 仍 'pyroblast'**。
- [ ] **Step 2: 确认失败** → FAIL。
- [ ] **Step 3: 实现** 三个 `engine.on("action.talent_*")`。通用前置:`Specialization.path.size>0` 否则 reject;`TalentTree.root` 空则初始化为 `path[0]`,非空须 `==path[0]`。校验用统一 `Talent.available(entity)`(勿在 action 内另算)。
  - `talent_unlock`:校验 `available>0`、节点属 root 树、与 `unlocked`(或 root 无 parent)相连、`requires_spec` 在 path、未重复 → `unlocked.add(id)` + `applySpec`(重推被动)+ save。
  - `talent_respec`:`unlocked` 清空 + `applySpec`(走 A1 清理机制摘 talent modifier)+ save;**不动 known.node**。
  - `talent_place_orb`:校验槽节点已 unlocked、`OrbPouch[type]>=count`、**该 skill 已在 `Skillbook.known`(否则拒"尚未学会该技能",阻塞项 5)** → 扣球 + 写已存在 known 条目的 `node=evolution`(**不新建条目**)+ `applySpec`/recalc + save。
  - **前提**:demo 法师 `fireball` 是起始技能(在 `known`),`place_orb` 才有目标;核对 `select.js`/职业 `starting_skills` 含 fireball,否则 demo 数据补一个已学技能当槽目标。
- [ ] **Step 4: 确认通过** → PASS。
- [ ] **Step 5:** 回话标注 A5 完成。

### Task A6: debug 给球端点

**Files:** Modify `handlers/character/talent.js`(`debug.grant_orb`)、`backend/.../debug/DebugController.java`(`POST /orb`);Test 可并入 A5 的 place_orb。

- [ ] **Step 1:** `engine.on("debug.grant_orb")`:`OrbPouch[type] += count` + `event.set("ok",true)` + `persistence.save`。
- [ ] **Step 2:** `DebugController` 加 `@PostMapping("/orb/{token}")`(`@RequestParam String type, int count`),照 `/passive`(`:60`)抄 fire `debug.grant_orb`。注释标注"可删脚手架"。
- [ ] **Step 3:** Run: `mvn test -Dtest=TalentTreeTest` 全绿。
- [ ] **Step 4:** 回话标注 A6 完成。

### Task A7: 强类型快照原始数据(records + 工厂 + buildTalentTree + ui 回填)

**Files:** Modify `WorldSnapshot.java`、`ScriptRuntime.java`、`SnapshotService.java`;Create `handlers/ui/talent_tree.js`;Test `TalentTreeSnapshotTest`(新建)。

拆两步实现(nit:Java 骨架先编译过,再 JS 填),降 A7 体量风险。

- [ ] **Step 1: Java 骨架失败测试** `TalentTreeSnapshotTest.unspecialized_nullBlock`:未专精实体 → `snapshot.talentTree()==null`(此时 JS 回填还没写,块为 null 即可过)。
- [ ] **Step 2: 确认失败** → FAIL(字段不存在,编译错)。
- [ ] **Step 3a: 加 Java records + 工厂 + service 空块**
  - `WorldSnapshot.java`:加 records——`TalentTree(String root, TalentPoints points, List<TalentNode> nodes, List<OrbStack> orbInventory)`、`TalentPoints(int total,int spent,int available)`、`OrbStack(String type,int count)`、`TalentNode(String id,String label,String icon,String type,List<String> parents,List<String> children,int tier,int order,String state,boolean selected,String requiresSpec,String effectSummary,TalentSlot slot,TalentActions actions)`、`TalentSlot(String skill,String orbType,int orbCount,String evolution,boolean filled)`、`TalentActions(boolean canUnlock,boolean canPlaceOrb,String reason)`;`WorldSnapshot` 加 `TalentTree talentTree` 字段;**`characterSelect`/`characterCreate`/`inGame` 三个静态工厂全部同步补参,非 inGame 传 `null`**(阻塞项 7:漏一个就构造错位)。
  - `ScriptRuntime.java`:加 `@HostAccess.Export newTalentTree/newTalentPoints/newOrbStack/newTalentNode/newTalentSlot/newTalentActions`(照 `newSpec*:173-211` 抄)。
  - `SnapshotService.java`:`buildTalentTree(playerId)`——无 `TalentTree` 组件或 `Specialization.path` 空 → return null;否则 fire `ui.render_talent_tree`(预置 nodes 空 list)+ 回收;`buildInGameSnapshot` 调用并入参。
- [ ] **Step 3b: 确认 null 测试过 + 编译** Run: `mvn test -Dtest=TalentTreeSnapshotTest#unspecialized_nullBlock` → PASS。
- [ ] **Step 4a: JS 回填失败测试** `specialized_nodeStates`:专精 + 点根 → `points()=={total,spent,available}`、`nodes()` 含 `elem_root` 态 `unlocked`、`fireball_slot` 态 `slot-empty`、`fb_damage` 态 `locked`、`orbInventory()` 反映 `OrbPouch`。
- [ ] **Step 4b: 确认失败** → FAIL(块非 null 但 nodes 空)。
- [ ] **Step 5: 实现 `handlers/ui/talent_tree.js`**:`engine.on("ui.render_talent_tree")` 组装——`children` 反查 parents、`points` 三元组(`Talent.totalPoints/spent/available`)、`orbInventory`(读 `OrbPouch`)、`selected=false`(选中是前端态)。
  - **单一真相(nit + 阻塞项 6 第二点):节点 `state` 与 `effectSummary` 必须调 `Talent` 的公共函数,不在 UI 里复制规则。** 实现 `Talent.nodeState(entity,node)` 和 `Talent.effectiveNodeEffect(entity,node)`(后者复用 `derivedPassives` 的 override/requires 解析),UI handler 只调它们。
  - **state 枚举**:`hidden`(requires_spec 不满足)/`locked`(相连但 available=0 或父未点)/`unlockable`/`unlocked`/`slot-empty`/`slot-filled`/`filled-node-relocked`。**`slot-filled`/`filled-node-relocked` 以 `Skillbook.known[skill].node!=null` 判**(不是只看 `unlocked`):槽在 unlocked 且已插球=`slot-filled`;槽不在 unlocked 但 `known.node!=null`(洗点后)=`filled-node-relocked`。
- [ ] **Step 6: 确认通过** → PASS。
- [ ] **Step 7:** 回话标注 A7 完成。

### Task A8: skillbook 快照回填进化名(阻塞项 6)

> `ui.render_skillbook`(`ui/skillbook.js:22-28`)当前读 base yaml 的 name/description,**洗点后火球仍显示"火球术"而非"炎爆"**,§3.3 展示验收会挂。

**Files:** Modify `mods/base-rules/handlers/ui/skillbook.js`;Test `TalentTreeSnapshotTest`。

- [ ] **Step 1: 失败测试** `skillbook_showsEvolutionName`:`known[fireball].node=='pyroblast'` → skillbook 快照该条 `name=="炎爆"`、`icon`/`description` 用进化值;`node==null` 时维持 base"火球术"。
- [ ] **Step 2: 确认失败** → FAIL(显示 base 名)。
- [ ] **Step 3: 实现** `ui/skillbook.js`:组每条时 `if (node!=null && typeof Talent!=='undefined')` 取 `Talent.evolutionDisplay(node)`(返回 `{name,icon,description}`,从进化索引读)覆盖 base 显示值。`Talent.evolutionDisplay` 在 talent.js 提供(同进化索引)。
- [ ] **Step 4: 确认通过** → PASS;`mvn test -Dtest=SkillbookSnapshotTest`(若有)无回归。
- [ ] **Step 5:** 回话标注 A8 完成。

### Task A9: 阶段 1 集成回归

- [ ] **Step 1:** Run: `cd backend && mvn test`(全量)→ 全绿,**特别确认** `SkillFidelityTest`/sim/战斗/`SpecializationTest`/`PassiveTest` 无回归。
- [ ] **Step 2:** 自检验收 ①:派生点数(含 spent>total 拒)、统一被动来源(skill_patch+handler 都生效)、**洗点后 stat_mod 不残留**、追溯重推 override、respec 保进化、skillbook 显示进化名、load 往返。失败回对应 Task。
- [ ] **Step 3:** 回话报"阶段 1 后端闭环完成,mvn test 全绿",**等 Claude review 阶段 1 diff** 再上阶段 2。

---

## 阶段 2 — 前端树体验 + demo(Task B1–B3)

### Task B1: 快照契约前端可消费性(轻测)

**Files:** Test `TalentTreeSnapshotTest`(补 JSON 序列化断言)。

- [ ] **Step 1:** 断言 `talentTree` 块经 Jackson 序列化字段名/嵌套正确(points/nodes/orbInventory/slot/actions),前端能拿到布局所需 `tier/order/parents/children/state`。
- [ ] **Step 2:** Run: `mvn test -Dtest=TalentTreeSnapshotTest` → PASS。

### Task B2: `TalentTreePanel.vue` 多叉树 + 说明面板

**Files:** Create `frontend/src/components/TalentTreePanel.vue`;Modify `SnapshotRenderer.vue`、`SkillbookPanel.vue`。

- [ ] **Step 1:** `TalentTreePanel.vue`:props `talentTree`/`readonly`。布局——按 `tier`(纵,从下往上:tier 大在上或下,取"从下往上长"=root 在底)+ `order`(横)排圆形节点;连边按 `parents`。节点视觉:颜色态(灰/黄边/绿土星环)+ 选中双环;**不画 icon**。点节点→右侧/弹出说明面板(icon 左上、名右、说明、"受影响"区 from `effectSummary`/`slot`、按钮 from `actions`)。按钮 emit `action`:`talent_unlock`(nodeId)、`talent_place_orb`(nodeId)、`talent_respec`。`points` 三元组顶部显示;`orbInventory` 一栏。**边框 ≥2px**;`readonly`(战斗中)隐藏按钮。
- [ ] **Step 2:** `SnapshotRenderer.vue`:加 `<Modal :visible="modalActive==='talent'">` 租户 + `openTalent`;`SkillbookPanel.vue` 把 `树` 灰占位 `<span class="sb-tree">` 改成可点,emit 事件冒泡到 `SnapshotRenderer` 开 `talent` 模态。未专精(`talentTree==null`)入口提示"专精后解锁"或灰。
- [ ] **Step 3:** Run: `cd frontend && npm run build` → 通过。

### Task B3: demo 树数据收尾 + 全量验证

**Files:** `talents/elementalist.yaml`(确保覆盖全机器特性)。

- [ ] **Step 1:** 核对 demo 树覆盖:根 stat_mod 被动 + override(追溯)、`fireball_slot` 技能槽(爆破→炎爆 AOE)、`ember_passive`(requires_spec 门控 + handler ref)、`fb_damage`(skill_patch + 二级 tier 连通)、≥2 种球类型出现(爆破必有;`OrbPouch` 支持多重即可,节点不必都用)。
- [ ] **Step 2:** Run: `cd backend && mvn test` + `cd frontend && npm run build` 全绿/通过。
- [ ] **Step 3:** 回话报"阶段 2 完成",列改动文件清单(供 Claude 圈文件提交),**不自己 commit**。

---

## Self-Review(plan 对 spec 覆盖)

- spec §2 树形/视觉/说明面板 → B2;§2.3 颜色态/双环 → A7 state 枚举 + B2。
- §3 两货币/派生点数/三层可逆 → A2(点数)、A5(action+respec)、A6(球);§3.3 硬规则 → A5 respec 保 node + A7 `filled-node-relocked` 态。
- §4 节点效果走 #5 → A1 聚合层 + A4 derive;§4.0 统一层 → A1;§4.1 追溯重推 → A4 override + 白嫖 applySpec(约束⑤)。
- §5 门控/二级专精 → A4 requires_spec + override;未专精拒 → A5 + A7 null。
- §6 数据模型 → 组件 A2 + 进化索引 A3 + yaml(数据形状段)。
- §7 action/校验/debug → A5 + A6。
- §8 强类型快照 → A7 records/工厂/state 枚举/字段;§3.3 展示进化名 → A8。
- §9 demo → B3。§10.1 分两阶段 → 阶段 1(A1–A9)/阶段 2(B1–B3)+ A9 闸。§11 验收 → 5 件套①逐条。
- 占位扫描:无 TBD;类型一致(`ownedSpecs`/`derivedPassives`/`evolutionPatch`/`evolutionDisplay`/`nodeState`/`effectiveNodeEffect`/`totalPoints`/`available`/`ensureComponents` 全任务统一命名)。
- Codex plan review 6 阻塞项:① A1 Step 3b / ② A1 Step 3 契约 / ③ A3 manifest / ④ A3 测试 18 / ⑤ A5 拒未学 / ⑥ A8;nits:A7 拆步 + 单一真相函数。
