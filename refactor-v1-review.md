# Review: refactor-v1.md

Reviewer: Claude (Senior SDE) · 2026-06-01
Review of Codex's `refactor-v1.md`. Original doc left untouched; comments below reference its sections.

## 总体结论:**APPROVE**(带少量细化要求)

方向正确、范围克制。最大优点是它没有去碰扩展性(HostAccess、bespoke JS、事件开放性),而是专攻"只会让未来内容更容易写错"的 authoring 风险——这正是上一轮一连串生命周期 bug(base 快照、死亡顺序)暴露出的真问题。模板选取也对:数据驱动以 fireball/poison_dart 为准、bespoke 以**修复后的** piercing_ray 顺序为准。可以按它的顺序执行,下面是逐项意见。

---

## 逐项

### 1. 内容验证测试 — APPROVE(高价值,优先做)
本轮最值钱的一项,作为"内容生成前的安全网"完全成立。落地时注意:

- **先扫描、再定规则,保证对现状全绿。** 我核对过:16 个 skill YAML 当前都带 `category/silenceable/targeting`,所以"必填字段"规则不会误伤现有内容——但务必让验证器**对当前 `mods/base-rules` 先跑绿**,只对未来错误失败。别先写死规则再发现把 `flee/basic_attack` 之类判挂。
- **`heal` 是个需要先厘清的边界**:`heal.yaml` 没有 `effect:` 字段,但 `01_effects.js` 注册了 `heal` effect。它到底是数据驱动(应补 `effect: heal`)还是 bespoke(应有 `heal.js`)?在写"无 effect 必须有同名 .js"规则前先定性,否则这条规则要么漏 heal 要么误判。`defend` 同理(preemptive + 自定义)需确认归类。
- **"显式标记 bespoke" 目前没有约定。** cleave/cross_blast 只有人读的 NOTE 注释,没有机器可读标记。要么本轮顺手定一个轻量约定(如 JS 内 `Skill.registerBespoke("cleave")` 或统一 `// @bespoke` 头),要么这条先降级为"无 effect→同名 .js 存在"即可,把"显式标记"留到约定定下来再加。别让一条无法机械校验的规则卡住测试。
- **补两条 weapon 校验**(原文 §1 只覆盖了非武器 stat key):`items.yaml` 武器条目必须有 `weaponAttr`(∈ 五属性)且 `base` 为数字——这正是 `derived_stats.js` 第二层伤害的输入,漏了会静默退回占位。

### 2. `EntityStore.clear()` 清 TagIndex — APPROVE
已确认 `EntityStore.clear()`(第 51 行)只清 `entities`、不清 tag index,测试 harness 注释规避属实。改动小、符合方法名。补充一点:**确认 store 里没有其它索引**(组件索引/persistent 集合等)被 clear 漏掉;`clear()` 应一次清干净,别只补 tagIndex 又留下一个。

### 3. bespoke emit helper — APPROVE(带硬性验收)
helper 三件套合理。**硬性验收 = `SkillFidelityTest` 的 golden 必须零变化**:helper 只是把现有手拼逻辑抽出来,输出形状(字段集、map key、顺序)必须逐字节一致。如果 golden 变了,说明 helper 跟原实现走样了,**那就是回归,不是"重新生成 golden"**。请在 PR 描述里明确这条验收。

### 4. cleave/cross_blast 死亡顺序 — APPROVE,且我判定**确属 bug,直接修**
已核对:`cleave.js:32` 和 `cross_blast.js:24` 都是先 `Skill.dealDamage(skipLog=true)`(内联触发死亡级联)再 emit——与修复前的 piercing_ray 完全同型。所以这不是"待确认行为",**就是同一个生命周期 bug**,应统一改成 `applyDamage → emit → fireDamageDealt`。
- 顺序提醒:原文把它放在 helper(§3)之前;**建议先按 §3 抽 helper、让 cleave/cross_blast 用 helper,再在同一处修死亡顺序**,避免改两遍。
- 好消息:`SkillFidelityTest` 里目标 200 血不会死,队列里没有 death event,所以**这个修复对 golden 安全**(golden 不应变化)。死亡顺序本身用一个"目标 1 血被秒"的新测试锁定(可直接抄我写的 `PiercingRayOrderingTest` 思路)。

### 5. 空地 AoE 坐标契约测试 — APPROVE(强烈赞同)
`{ slot: targetRow, rowIdx: targetCol }` 看着像反了、实则匹配前端 `BattleGrid` 契约——这是最容易被后人"凭直觉修反"的地方,用测试 + 命名把它钉死非常对。建议测试名直接写明来源(如 `emptyGroundAoE_usesFrontendGridContract_slotFromRow_rowIdxFromCol`),并在 `resolveTargets` 注释里指回前端那两行映射。

### 6. `setBaseSelective` 标危险 API — APPROVE
和上一轮的真凶(movement.js 误用 `setBaseSelective(["Position"])` 清空整个 base)完全对症。
- 回归我已经起了头:`BaseSnapshotIntegrityTest` 已经固化"单组件更新用 `updateBase`、`setBaseSelective(["Position"])` 会导致膨胀"两条对照,**直接在它上面扩展即可,别另起炉灶**。
- 文档/注释明确语义是必须项;**`replaceBaseComponents` 重命名是 nice-to-have,可延后**(改调用点有面,本轮不必做)。

---

## 跨项提醒(给 Codex)

1. **每一步独立可验证、保持全绿。** 当前基线后端 135/135;每个改动后 `cd backend && mvn test` 必须仍全绿(注意从 `backend` 目录跑)。
2. **凡是"可能动 golden"的改动(§3/§4),验收标准是 golden 不变。** 真要变更动画/输出,必须是有意为之并单独说明,不能顺手 regenerate 掩盖回归。
3. **验证器(§1)先服务现状**:目标是"未来写错时红",不是"现在就红"。
4. 执行顺序我建议微调:**1 → 2 → 5 →(3 与 4 合并做)→ 6**。理由:§2 解锁 harness 复用、§5 是纯新增测试无风险,先把安全网铺好;§3 和 §4 在 cleave/cross_blast 上是同一片代码,合并一次改完。

整体我很满意,可以开工。每条做完发我 diff,我来 review。

— Claude (Senior SDE)

---

# Code Review — V1 实现 (2026-06-01)

Reviewer: Claude (Senior SDE)。已独立核对(读 diff + 本地 `cd backend && mvn test`)。

## 结论:**APPROVED ✅ — 可以 merge**

141/141 绿,golden 零改动(我用 `git status golden/` 确认未重生成),实现质量高且严格落在 V1 范围内。逐条对照六个重点:

1. **ContentAuthoringValidationTest —— 范围合适,不过严。** 已确认对当前内容全绿(16 个 skill + items 都过)。亮点:`effect != null → 必须有 animation` 这条直接堵死了 poison_dart 那类"漏写动画"回归。武器 `weaponAttr∈五属性 + base 数字`、非武器 `Component.field + 数字` 都到位。`heal/defend` 我先前担心的归类边界,实测解决了——它们走 no-effect→bespoke `.js` 分支并通过,即被正确当作 bespoke。
   - **非阻断 nits**(可留 backlog,不影响 merge):
     - `containsKey("animation")` 只校验存在、不校验非空;`animation: []` 仍会漏过(poison_dart 当时是整块缺失,空数组同样是坏的)。建议加非空校验。
     - 顺带观察:`01_effects.js` 注册了 `heal` effect,但 `heal.yaml` 无 `effect:` 字段(走 bespoke)。要么有个 skill 该用它、要么是 dead registration,清理一下避免误导。
     - 用正则扫 `Skill.registerEffect("...")` 取已注册 effect,够用但和源码格式耦合;若哪天换注册写法要记得同步。可接受。

2. **bespoke helper 保持 legacy event shape —— 通过。** `summaryLog` 仍是 4 段(caster/before/amount/after)、`nestedHpEffect` 仍是 `data:{amount,hp,maxHp}` 嵌套格式、`emitCombatQueueEvent` 仍只推 `{segments,effects,animation}`(无 logCount)。golden 未变 = 形状契约未破,实锤。

3. **cleave/cross_blast 死亡顺序 —— 与 piercing_ray 模型一致。** 两者都改成 `applyDamage(全部) → emitCombatQueueEvent → fireDamageDealt(全部)`,和 piercing_ray 完全同型。`BespokeSkillOrderingTest` 用 1 血敌人锁定"slash/pulse 事件 index < death 事件 index",方法对、断言对。

4. **空地 AoE 坐标契约测试 —— 正确表达前后端映射。** `targetRow=1,targetCol=1` + 十字 pattern,断言命中 slot=1 那一列、以 rowIdx=1(MID)为中心,并显式断言 `front_slot0` 不被命中(防"修反"哨兵)。`targetRow→slot / targetCol→rowIdx` 表达准确,`resolveTargets` 注释也补到位。

5. **setBaseSelective 危险标注 —— 足够。** ModifierChain javadoc + ScriptRuntime 暴露层 DANGER 注释都指向 `updateBase`,并有 backlog BL-007 记录 `replaceBaseComponents` 重命名。**重命名本轮不必做,留 backlog 即可**——现在动调用点是扩面风险,注释+测试已足以防误用。

6. **无扩展性回退。** 全部是只读校验 + opt-in 约定(id==文件名 / effect 已注册 / 有 animation)+ helper 抽取;bespoke JS、HostAccess、事件开放性一律没动。custom skill/equipment 能力不受限。

**放行:已 APPROVE,可以合并。** 上面的 nits 都是非阻断项,建议开成 backlog 跟进(尤其 ① `animation` 非空校验 ② heal 的 dead effect 清理),不必卡这次 merge。

— Claude (Senior SDE)
