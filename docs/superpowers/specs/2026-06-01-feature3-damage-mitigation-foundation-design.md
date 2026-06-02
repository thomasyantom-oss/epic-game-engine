# Feature #3(切片一):减伤地基 / 空跑框架 — 设计

作者:Claude (Senior SDE) · 2026-06-01
脑暴出处:`docs/feature3-damage-types-running-notes.md`(基调 + 全部决策的过程记录)。
实现:Codex。我(Senior SDE)review,**仅我 APPROVE 方可 merge**。
上游约束:F2 spec §1(安全 vs 高级属性红线)、§8(减伤推给 #3)。

---

## 1. 目标与范围

**这一刀只做一件事:把"减伤"建成一个唯一收口,所有伤害(普攻/技能/DoT)都经过它;新防御轴(三抗/元素)先空跑(值全 0、行为≈不变)。** 这样把"统一减伤管线"这个高风险结构改动,和"配数值做手感"的平衡工作**分开**,前者用 golden 兜底安全落地。

**In scope:**
- 伤害携带**投放标记**(普攻 / 技能)、**类型**(物理/法术/精神)、**可选元素**。
- **唯一减伤收口** `Skill.mitigate(...)`:普攻→护甲;技能→类型抗% + 元素乘区;defendMult;下限。
- **三抗组件**(物/法/精,默认 0)+ **元素框架**(可选标签 + 加成% + 元素抗,全部空跑)。
- 现有伤害链路全部改道经过收口;修掉"技能完全绕过减伤"的结构缺陷(数值上 #3 空跑仍≈不变,但管线就位,以后配抗即生效)。
- 敌我同轴:怪也能配护甲/三抗(本期默认 0/沿用现值)。

**Out of scope(parked,明确不做):**
- 护甲**曲线**公式 + K、抗性 cap/floor —— 数值手感轮。本期普攻沿用现行 flat 算法保证数值不变。
- 真实元素内容(火/冰/毒)+ rider 效果。本期只建框架、零元素配置。
- 控制 / 豁免 / 韧性(挂 `buff.apply`,见 future note)。
- 站位 / lane 射程 / 宠物(future note)。
- 成长曲线 / 内容分段。

---

## 2. 伤害模型(两条正交轴)

- **投放方式(delivery)= 普攻 | 技能**:决定走哪条减伤。**普攻只被护甲减**;**技能只被三抗减**。普攻是独立护甲通道,**不带伤害类型**。
- **伤害类型(type)= 物理 | 法术 | 精神**:只活在**技能**上,决定吃哪条抗(物抗/法抗/精抗)。默认 `物理`。
- **元素(element)= 可选 0/1**:独立乘区,与类型解耦。攻方 `×(1+元素加成%)`、守方 `×(1−元素抗%)`;无元素 → ×1。本期无元素实体配置。

> 注:护甲 与 物抗 是**两条独立防御**——护甲挡普攻,物抗挡物理技能(顺劈/背刺)。故意分开(为后续站位玩法)。

---

## 3. 组件与数据

- **新组件 `Resistances { 物理:0, 法术:0, 精神:0 }`**(百分比;元素抗为稀疏可选 key,如 `火:N`)。
  - 进 `character` schema `base_components`,默认全 0。
  - **只来自装备/buff/专精**(红线):装备走全限定 key `"Resistances.法术": N`(复用 F2 的 `组件.字段` 解析)。`derived_stats.js` **不写** Resistances。`entity.loaded` 复位时按现有 base_components 流程处理(默认 0,装备 modifier 叠加)。
- **护甲 = 复用现有 `CombatStats.defense`**(不新增字段、不改名,避免 churn);语义="挡普攻的护甲"。皮甲/锁甲继续给它。
- **技能 YAML 新增字段**(都有默认值,现有技能多数零改动):
  - `delivery: 普攻 | 技能`(默认 `技能`;仅 `basic_attack` 设 `普攻`)。
  - `damage.type: 物理 | 法术 | 精神`(默认 `物理`;fireball/ice_beam/light_field 等显式标 `法术`)。
  - `damage.element: <字符串>`(可选,默认无;本期不配)。
- **encounter YAML**:可选 `resistances: {...}` 与护甲(默认 0/沿用现值)。本期不强配。

---

## 4. 减伤收口 `Skill.mitigate`

唯一定义处(`00_skill_lib.js`)。签名:`Skill.mitigate(target, raw, opts)`,`opts = { delivery, type, element, elementAmp }`,返回最终伤害(int)。

算法(**空跑版**):
```
defendMult = target 有 Buff_defending ? 0.5 : 1
if delivery == "普攻":
    dmg = max(0, raw − target.CombatStats.defense)        // 本期 flat(=现行);曲线 parked
else:  // 技能
    typeResist = target.Resistances[type]  || 0           // %
    elemResist = element ? (target.Resistances[element] || 0) : 0
    dmg = raw × (1 + (elementAmp||0)/100) × (1 − typeResist/100) × (1 − elemResist/100)
final = max(1, round(dmg, 与现行一致的取整))  并施加 defendMult
```
**硬性:`basic_attack` 经收口后的数值必须与现行 `damage_calc.js`(`max(1, attack−defense)` + 防御 ×0.5,含其 floor 取整)逐位一致**(golden / 现有战斗测试不变)。

空跑结果:普攻 = 现行;技能(三抗 0、无元素)= `max(1, raw)`,**唯一新增行为 = 技能现在也吃防御姿态 ×0.5**(这是正确化,fidelity harness 目标不防御 → golden 不受影响)。

---

## 5. 链路改造

1. `damage_calc.js`:把 `attack−defense`+防御逻辑**搬进** `Skill.mitigate` 的普攻分支;`combat.damage_calc` 改为调用收口(保持 `basic_attack` 输出不变)。
2. `01_effects.js`(present 路径)+ bespoke 技能(cleave/cross_blast/piercing_ray):`applyDamage` 之前先 `raw = Skill.mitigate(target, raw, {delivery:"技能", type, element, elementAmp})`。注意沿用上轮已修的顺序(applyDamage → 表现 → fireDamageDealt)。
3. DoT tick(burning/poison):tick 伤害也过收口(本期 type 先按其 buff 定;元素 0)。
4. `basic_attack.yaml` 设 `delivery: 普攻`;其余技能默认 `技能`。给现有技能补 `damage.type`(物理系不写=默认物理;法系显式 `法术`)。
5. **快照/UI**:面板加"抗性/护甲"块(全 0 可折叠);伤害数字预留 `type`(为以后上色,本期颜色可后补)。UI 细节我可在 review 时定,不阻塞。

---

## 6. 红线(实现 + review 把关)

基础属性 / 等级 / `derived_stats.js` **绝不**写 `Resistances` 或护甲增量;只允许装备/buff/专精来源。这是 build 体系不崩的红线,review 重点查。

---

## 7. 测试网

- **`Skill.mitigate` 单测**:普攻走护甲(=现行)、技能走类型抗、元素乘区、defendMult、`max(1)` 下限、抗性 0 = 不减、(预留)负抗=易伤、稀疏元素抗缺省 0。
- **敌我对称**:同一收口对敌我一致。
- **golden 稳定**:`SkillFidelityTest` **不应变化**(空跑);若变,必须逐项可解释(预期只有"技能+防御姿态"这一处,且 harness 不触发)。**不得无脑重生成 golden 掩盖回归。**
- 现有战斗 / `basic_attack` 数值回归保持绿。
- `ContentAuthoringValidationTest` 扩展:`damage.type` ∈ {物理,法术,精神};`delivery` ∈ {普攻,技能};`Resistances.<key>` 数字、key 合法。

---

## 8. 实现拆解(给 Codex 的 task 序列)

1. `Resistances` 组件 + schema base_components(全 0)+ 装备全限定 key 解析复用;确认不进 derived。
2. `Skill.mitigate` + 单测(§7 各分支),纯函数先行。
3. `damage_calc.js` 改道收口(普攻),**逐位对齐 basic_attack 现值**。
4. 技能链路(present + 3 个 bespoke + DoT)接入收口;技能补 `delivery/type` 字段。
5. ContentAuthoringValidationTest 扩展。
6. 快照面板抗性块 + 伤害数字预留 type(颜色可后补)。
7. 全量回归:`cd backend && mvn test` 全绿;golden 不变(变了要逐项解释)。

**每步独立保持全绿;凡碰 golden 的步骤,验收 = golden 不变(空跑),有意变更须 PR 注明。**

---

## 9. 风险

- **最大风险=把普攻数值改了**。务必逐位对齐现行 `basic_attack`,golden + 战斗测试是安全网。
- **红线失守**(抗性从基础属性白来)= build 体系崩。review 死守。
- 元素/三抗虽空跑,字段一旦定下要稳(以后纯加数据);本期把字段/组件形状定对,别留歧义。
