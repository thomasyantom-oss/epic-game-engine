# Feature #7 — 专精天赋树(Chapter 1 旗舰收官)· 设计 spec

**日期:** 2026-06-04
**性质:** Ch1 第 7 个、也是最后一个 feature 的设计 spec。本文档定 *做什么 / 不做什么*,实现细节留 plan。
**母文档:** `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md`
**依赖:** #1 技能架构、#2 属性表、#4 Skillbook、#5 被动框架、#6 专精,全部已合并。
**Review:** 已纳 Codex spec review(`docs/superpowers/codex-runs/feature7/review-reply.md`,REQUEST-CHANGES 5 阻塞项全解:§4.0 统一 owned-passive 聚合层、§6.1 派生点数、§6.2 全局进化索引、§8 强类型快照契约、§3.3 槽点位/进化态硬规则)。

---

## 0. 一句话

把"专精"从一次性里程碑,长成一棵**玩家逐级点亮的天赋树**:天赋点点树决定 build,灵魂球插技能槽决定技能进化。这是 Ch1 build 空间的最后一块拼图。

---

## 1. 对母文档的改向(本轮 brainstorm 拍板,刻意覆盖)

母文档 §1.6 / §1.10 当初设想的是"**每个技能**挂一棵升级树、灵魂球当爬树的梯子"。本轮重新设计,**改成**:

- **树属于专精,不属于单个技能。** 一个专精一棵多叉树(瘦高,从下往上长)。
- **两条正交货币,各管一件事:**
  - **天赋点**(来自等级)→ 点亮树节点,走哪条路 = build 抉择。
  - **灵魂球**(分型材料,RNG)→ 单独插进树里的"技能槽"节点,把那个技能进化。
- 母文档"灵魂球=爬树的梯子"作废;**爬树的梯子改成天赋点**,灵魂球退化为"技能槽专用的进化材料"。

这次改向是有意的架构更新,后续内容轮以本文档为准。

---

## 2. 核心模型

### 2.1 树形

- **多叉树**(不强制二叉),**瘦高**(树高 > 树宽),从下往上生长,**单根**。
- 节点 = 圆形。**只能点亮与已点亮节点相连的邻居**(连通性约束)。
- **门控**:未专精 → 整棵树不渲染(读 #6 的 `Specialization.path` 当总闸)。专精后树出现,根节点用天赋点点亮。

### 2.2 节点三类效果

| 类 | 例 | 点亮后给什么 |
|---|---|---|
| 被动节点 | +10 智力、火球伤害+5、火盾 | 授予一个**隐式被动**(走 #5) |
| 主动节点 | 直接获得某主动技能 | 授予一个主动(进 Skillbook.known) |
| 技能槽节点 | 火球术(可插球) | 一个**可插灵魂球的槽**,插球→技能进化(走 #4 `known[i].node` + `applyNode`) |

### 2.3 节点视觉态(颜色编码)

**树上的节点只是圆圈,不画 icon**(icon 只在说明面板里出现,见 §2.4)。圆圈用颜色 + 环编码状态:

- **灰边实心**:普通节点(不可插球)。
- **黄边实心**:可插球的技能槽 · 空。
- **绿色"土星环"**(同色的环套圆、中间留空,非填充光晕):已插球的技能槽。
- **双环(外面再套一圈)= 当前选中的节点**(叠加在上述任一态之上,标记"你正看着这个")。

### 2.4 节点说明面板(点击节点弹出)

**icon 在这里才出现**(不在树上)。布局自上而下:① 节点 icon 在左上、节点名在 icon 右侧 → ② 技能/效果说明 → ③ "受影响"区(列出该节点触及的被动 / 主动增强 / 所需灵魂球)→ ④ 操作按钮。

---

## 3. 货币与可逆性

### 3.1 天赋点

- **来源:等级。** Lv.10 给第一个,之后**每 2 级 +1**(具体速率属数值,留内容轮微调;本期落默认值跑通)。
- **免费洗点(respec)**:整树退点重分,Ch1 免费(金币 money sink 留 Chapter 5,本期只要数据可逆)。
- 技能槽节点的天赋点**也可洗**。

### 3.2 灵魂球

- **分型材料**(demo:爆破 / 多重 …)。每个技能槽声明它吃哪种球、几颗,**一槽一种球、对应一个固定进化**(树的点法本身已是抉择,不在单技能内再叠抉择)。
- **本期供给 = debug 给球**(真供给 = 特殊词缀灵魂怪掉落,Chapter 3)。
- **插球行为本身:消耗灵魂球,产生进化。**

### 3.3 可逆性(三层,刻意非对称)

1. **天赋点:可逆。** 随便洗。
2. **技能进化:永久,但与点位解耦。** 插球 → `Skillbook.known[i].node = 进化id`,这是**永久记录**,洗点 / 退技能槽节点都不回退;再点回来仍是进化态。"升一次就是升了。"
3. **专精(#6):不可洗。** 身份级抉择。

**槽点位 vs 进化态的硬规则(Codex review 阻塞项 5,必须写死):**
- **技能槽节点的"已点亮"只 gate「插球这个动作」**——没点亮/洗掉点位 = 不能再往这个槽插新球,**仅此而已**。
- **技能是否运行成进化态,只看 `known[i].node != null`**,与槽点位当前是否点亮**无关**。
- 因此洗点后:树上该槽节点回到 locked 态,**但技能书 / 战斗里火球仍是炎爆**(进化名、AOE 效果照常)。前端展示以 `known[i].node` 为准,不以槽点位态为准。这条要在快照字段和文案上明确,避免"树里没点这个槽、火球为什么还是炎爆"的困惑。

---

## 4. 节点效果如何落地(本轮最关键的架构决策)

**原则:#7 = 获取 / 门控层;效果执行全部复用 #5(被动)+ #4(技能进化),不另造执行器。**

- **点亮被动/主动节点 = 授予一个 `source: talent` 的隐式被动。** 由现成 #5 管道执行,三条通路全可用:
  - `stat_mod` → 属性 ModifierChain(纯数值,如 +10 智力);
  - `skill_patch` → `applyPassivePatches` 改 spec 已声明字段(如火球伤害+5);
  - **`handler` → `passives/*.js` 的 bespoke JS**(spec 描述不了的特殊效果被动,挂战斗事件,如火盾/嗜血那种真逻辑)。**天赋节点效果不限于改数值**——需要代码的就走这条。handler JS 仍是**全局加载 + ownership 守卫**(不动态注册/注销 listener),是否生效由下面的统一 owned-passive 集合决定。
  - **这些天赋授予的被动不占被动栏、不进 `Skillbook.known`、不在技能书列表露出**(`source: talent`)。

#### 4.0 必须先建:统一 owned-passive 聚合层(Codex review 阻塞项 1)

> **现状问题(Codex 打开代码核对):** `Skill.ownedPassives`、`Passive.registerStatMods`、`Passive.owns` **三处都只扫 `Skillbook.known`**(`00_skill_lib.js:237`、`04_passive_lib.js`)。handler 被动(如 `lifesteal_on_kill`)靠 `Passive.owns(id)` 守卫。**若 talent 隐式被动不进 known,则 stat_mod 可能生效、但 skill_patch / handler 类失效** —— 分裂 bug。

**所以 #7 第一步先做统一聚合层(其它都依赖它):**
- 新增 `Passive.ownedSpecs(entity)`(或同义聚合函数):返回 **`Skillbook.known` 的被动 + talent-derived 被动**的合并集。
- talent-derived 被动**不塞进 known**(否则污染技能书 UI),而是由 `TalentTree.unlocked` × 当前 `Specialization.path` **derive** 出来(见 §4.1)。
- `Skill.ownedPassives`、`Passive.registerStatMods`、`Passive.owns` **三处全部改走这个聚合层**,确保三类被动对 talent 来源一视同仁。

- **技能槽插球 = 写 `Skillbook.known[i].node`**,运行时 `applyNode`(填现有空槽)读 node、套该进化的**内联 patch**(brainstorm 拍的"方案1:节点直接挂 patch",复用 `_applyPatch`/`_matchSkill`,不建具名变换注册表——每个升级独特、职业专属,无复用,养不活抽象)。
- **`applyNode` 如何从 node 找到 patch(Codex review 阻塞项 3):** `applyNode(spec, node)` 只拿到进化 id(如 `pyroblast`),没有树/path 上下文。落地方案:**建一个全局进化索引**——所有进化定义(`{id, patch}`)按全局唯一 id 收进一张表(load 期校验 id 不重复),`applyNode` 直接按 `node` 查表取 patch。`known[i].node` 保持纯字符串(不破坏 #4 数据形状)。

### 4.1 节点效果随专精**追溯重推**(复用 #6 模式)

二级专精会**改写已点出节点的效果**(节点保留、天赋点不退,但**授予的被动被换掉**)。例:元素法师的"法强+X"节点,转火后变"法强+火属性",甚至根本性翻转(防护者→进攻型)。

实现哲学 = #6 的 R 追溯成长同构:

- **`TalentTree` 组件只存 allocation(点了哪些节点),不存效果。**
- 效果是 **每次 derive,不存被动实例**:`resolveTalentEffects(entity)` 遍历已点节点,每个节点的有效效果 = `resolve(节点, 当前 Specialization.path)`(二级专精 yaml 可覆写某节点效果)。这套 derive 喂给 §4.0 的统一 owned-passive 聚合层。
- **不要设计成"授予/摘除被动实例"来驱动 handler 注册**(Codex review):handler JS 全局常驻,`Passive.owns` 改成判断"当前 derive 后的有效 talent 被动集合",自然就"专精变了→旧效果不再被守卫认可→新效果生效",无需动态 unregister JS listener、无残留。
- **stat_mod 类**:沿用 #6 recalculate 既定路——modifier id 稳定、旧 modifier 先 remove 再按新 derive 注册,`engine.recalculate` 复位重叠即自洽。
- **例外:技能进化不重推。** 球不可退,进化形态(§3.3 第 2 层)锁死,不随专精改写;只有"授予被动"类节点的效果会重推。

---

## 5. 门控与二级专精

- **总闸**:`Specialization.path` 为空 → 树不渲染、无任何入口(后端同样校验,防作弊)。
- **节点级门控**:节点可声明 `requires_spec`,反查玩家 path 决定可见 / 可点。
- **二级专精(Lv.50)只扩树、不加新根**:在现有树上**加宽**(前面的技能 / 节点获得进一步强化的新节点),并按 §4.1 **改写已点节点效果**。

---

## 6. 数据模型

### 6.1 新组件 `TalentTree`(挂角色)

```
TalentTree {
  root: <tier1 专精节点 id>,   # 哪棵树(= 你在 #6 选的一级专精;首次专精时初始化)
  unlocked: [nodeId, ...]      # 已点亮的节点(唯一 allocation 真相源)
}
```

**天赋点不存"可用点"当真相源(Codex review 阻塞项 2)。** 改为派生:
- `totalPoints = f(level)`(Lv.10 起每 2 级 +1;纯函数,等级是唯一来源)。
- `spentPoints = unlocked.length`。
- `availablePoints = totalPoints − spentPoints`。
- 这样 `debug.set_level` 跳级/降级、洗点都不产生歧义。降级导致 `spent > total` 的边界:实现时定义为"超额点位自动失效但保留 allocation,available 夹到 0"(纯展示层处理,不破坏数据)。本期 demo 不主动触发降级,但规则写明。

### 6.2 树数据 `talents/<spec>.yaml` + 全局进化索引

```
nodes:
  - id: elem_root
    label: "元素精通"
    icon: "★"                   # 仅说明面板用,不画在树上
    parents: []                 # 根
    tier: 1
    order: 0                    # 同层排序(给前端布局)
    effect: { passive: { stat_mod: { 法强: 5 } } }
    overrides:                  # 二级专精覆写已点节点效果
      pyromancer: { passive: { stat_mod: { 火属性: 8 } } }

  - id: fireball_slot
    label: "火球术"
    parents: [elem_root]
    tier: 2
    order: 0
    requires_spec: null         # 或 pyromancer 等
    skill_slot:
      skill: fireball
      orb: { type: 爆破, count: 2 }
      evolution: pyroblast      # 全局唯一进化 id;插球时写进 known[i].node
```

**进化定义独立成全局索引(支撑阻塞项 3 的 applyNode 查表):** 所有进化按全局唯一 id 收进一张表,patch 沿用 #6 专精 `skill_patches` 同款 `_applyPatch` 格式。load 期**校验进化 id 不重复**。

```
# evolutions(全局索引,可放 talents/_evolutions.yaml 或各 spec 文件汇总)
pyroblast:
  name: "炎爆"
  patch:
    name: "炎爆"
    damage.add: 8
    targeting.pattern: [[0,0],[0,1],[0,-1]]   # 单体 → 一排 AOE
```

### 6.3 复用的现成接缝

- `Skill.applyNode(spec, node)`(`00_skill_lib.js`,当前 no-op)→ #7 在此读 node、套进化 patch。
- `Skillbook.known[i].node`(#4 预留)→ 进化记录落点。
- #5 owned-passive 管道 → 天赋授予被动的执行。
- #6 `Specialization.path` + 追溯重推模式 → 门控 + 效果重推。

---

## 7. 后端动作与校验

完整校验链(阻塞项 6):每个 action 先校验 `Specialization.path.size > 0`,且 `TalentTree.root == path[0]`(或首次初始化 root)。

- **点亮节点** `talent_unlock`:校验 `availablePoints > 0`、节点属于 root 树、节点与已点亮节点相连、`requires_spec` 在 path 中、未重复 unlock → 写 `unlocked`(不写 points,available 自动 −1)+ 重推 talent 被动。
- **洗点** `talent_respec`:清 `unlocked`、摘掉 talent 被动;**不动** `known[i].node` 进化记录。`availablePoints` 自动回满。
- **插球** `talent_place_orb`:校验该技能槽**已点亮**、库存有对应球 → 扣球 + 写 `known[i].node = evolution`(永久)+ 节点转绿态。
- **未专精**:以上 action 全部后端拒绝。
- **debug 给球** `POST /api/debug/orb/{token}?type=爆破&count=N`:仿 `debug.set_level` 加端点,给分型灵魂球(可删,注释标注)。

## 8. 快照与前端

**快照是强类型 `WorldSnapshot` record + `SnapshotService` 固定字段构造 + `ScriptRuntime` newXXX factory(Codex review 阻塞项 4)**——`talentTree` 不是 JS handler 随便加的 JSON 块,要做成 typed 结构。未专精时 `talentTree = null`。

`talentTree` 块字段(给前端足够数据自行画"瘦高多叉树" + 说明面板,不在前端硬推):
- 顶层:`root`、`points: {total, spent, available}`、`orbInventory: [{type, count}]`。
- `nodes[]`:每节点 `{id, label, icon, type(passive|active|skill_slot), parents, children, tier, order, state, selected, requiresSpec, effectSummary, slot:{skill, orb:{type,count}, evolution, filled}, actions:{canUnlock, canPlaceOrb, reason}}`。
  - `tier/order`(或显式 x/y)给前端稳定布局;`reason` 给禁用态文案。
- `state` 枚举要覆盖全(不止 5 种):`hidden`(requires_spec 不满足)/ `locked`(相连但没点/点不够)/ `unlockable` / `unlocked`(普通已点)/ `slot-empty`(技能槽已点未插球·黄)/ `slot-filled`(已插球·绿土星环)/ `filled-node-relocked`(洗点后槽 locked 但技能仍进化,见 §3.3 硬规则)。
- `effectSummary` 由 derive 后的有效效果生成(随专精变),供说明面板"受影响"区。

- **新 skilltree 模态**(`Modal.vue` 又一租户,≠ skillbook 列表):多叉树画布、圆形节点(不画 icon)+ 土星环 + 双环选中态、连边、颜色态;点节点弹 §2.4 说明面板(此处才出 icon);面板内"点亮 / 插球 / 洗点"按钮发对应 action。
- 入口:`SkillbookPanel` 现有"树"灰占位升级为真入口 / 右下角常驻菜单。未专精态:入口提示"专精后解锁"或灰。

## 9. Ch1 demo 范围(一棵 demo 树,像 #5 那仨示例被动)

做法师·**元素 demo 树一棵**(节点/数值都是占位,只验证机制),覆盖**全部机器特性**:
- 根(被动)→ 属性/被动节点 → 技能槽(火球术,插爆破×2 → 炎爆,单体变 AOE 看得见)→ 上层锁住节点。
- 至少一个 `requires_spec` 节点(门控反查)。
- 至少演示一次**二级专精改写已点节点效果**(元素根节点转火后效果替换)+ 加宽占位。
- 一种以上分型球(爆破 + 多重)。

**真节点 / 数值 / 各专精完整树 / 二级专精完整内容 → Ch1 内容设计轮填。**

---

## 10. 范围边界

**IN:** 树机制 + 两货币 + 追溯重推 + 数据模型 + 后端校验 + 快照 + 新树前端 + debug 给球 + 法师元素 demo 树。

**OUT(延后):**
- 灵魂球真供给(灵魂怪掉落)→ Chapter 3。
- 洗点 money sink 经济 → Chapter 5(本期免费)。
- 各职业 / 各专精真树、真数值、二级专精完整内容 → Ch1 内容设计轮。
- 结构性技能改写(弹射等需改流程的)→ 仍走 bespoke JS,不进 patch 管道(母文档 §1.2)。
- `applySkillLevelCurve`(charLevel→skillLevel 曲线)仍空槽,本期不填。

### 10.1 实现分期(单 feature 一轮交付,内部分两阶段——Codex review 阻塞项 9)

范围偏大但**不拆成两个 feature**(用户决策:一套 UI、一轮端到端)。Codex 实现时**内部分两阶段**(仿 #6 implA/B/C 分步),都在本轮、一并合并:
- **阶段 1 后端机制闭环:** §4.0 统一 owned-passive 聚合层(先行,其它都依赖它)→ `TalentTree` 组件 + 派生点数 → `talents` loader + 全局进化索引 → 三个 action + 校验链 → `applyNode` → 追溯重推 → 强类型快照原始数据 → 后端测试。
- **阶段 2 前端树体验 + demo 内容:** skilltree 模态、多叉树布局、说明面板、土星环/双环/颜色态、orb 展示、demo 树数据。
- 阶段 1 绿了再上阶段 2,降低单次 diff 体量与 review 难度。

---

## 11. 验收标准

1. 未专精:树不渲染、`talent_unlock` 被后端拒。
2. 专精后:根节点可点,花 1 天赋点点亮,授予被动生效(属性/技能 spec 可见变化)。
3. 连通性:点不相连的节点被拒;点亮后其邻居变为可点。
4. 天赋点:Lv.10 第一个、每 2 级 +1(随 `debug.set_level` 跳级验证)。
5. 技能槽:点亮后黄态;debug 给爆破球→插球→转绿土星环态,`known[i].node=pyroblast`,火球实战变 AOE。
6. 洗点:退回全部天赋点、摘掉天赋被动;**已进化的火球仍是炎爆**(进化不回退)。
7. 追溯重推:把专精推进到火,元素根节点的授予被动按 `overrides` 替换,已点 allocation 不变。
8. 球不可退:无"取出球"动作;插球后再洗点,进化保留。
9. **统一被动来源(阻塞项 1 回归):** 一个 `skill_patch` 类天赋被动(火球+5)和一个 `handler` 类天赋被动(如嗜血)**都真生效**——证明 `ownedPassives`/`registerStatMods`/`Passive.owns` 三处都认 talent 来源,不只 stat_mod 生效。
10. **派生点数(阻塞项 2):** 点数不存"可用"真相源;`debug.set_level` 跳级后 available 随 total 重算正确;洗点后 available 回满。
11. **未专精快照:** `talentTree == null`,前端入口呈未解锁态。
12. 全量 `mvn test` 绿、前端 `npm run build` 通过、无回归。

**不做什么:** 不填真内容数值;不做灵魂球掉落;不做洗点收费;不碰结构性技能改写;不填 skill-level 曲线;不加具名变换注册表。

---

## 12. 迭代 2 修订(2026-06-05 真人 verify 反馈,覆盖前述冲突处)

**A. 树视觉(已落地前端)**:节点+连线染色——已点亮节点亮白边、未点亮灰;两端都点亮的连线亮白加粗、否则灰。插球图标=**平面 checkbox 式**(外圈仅边框、中间空白环、内圈仅填充),非 3D。洗点后的槽节点=灰外环 + **灰内圆仍在**(标记"球已花、重点免费")。

**B. 球/进化与点位解耦改写(覆盖 §3.3 第 2 层 + §11 验收 5/6/8)**:
- 技能槽节点就是**普通天赋点**:洗点照常退还该点。
- 插球=一次性消耗不可退,但**进化只在该槽节点当前点亮时生效**。
- **洗点后:槽节点退回未点亮态,进化效果不生效(技能回到原始形态,如炎爆→火球)。**
- "球已花"是**永久记忆**(新增 `TalentTree.evolved` 集):重新点这个槽节点时,因已在 `evolved` 中 → **免费直接恢复进化态(无需再插球)**。
- 数据落法:`known[i].node` = **当前激活**的进化(点亮且已花球→evolution;否则 null);`TalentTree.evolved` = 永久已花球记录。`place_orb`:扣球 + 加入 `evolved` + 写 `known.node`;`talent_respec`:清 unlocked + 对所有槽 `known.node=null`(保留 `evolved`);`talent_unlock` 槽节点:若其 evolution ∈ `evolved` → 直接写 `known.node`(免费恢复)。
- `applyNode` 仍读 `known.node`(现在它=激活态),无需改。

**C. 专精 + 天赋合并为单一面板/入口(覆盖 §8 入口 + #6 SpecializationPanel 定位)**:
- **一个入口**(撤掉单独的"专精"按钮)。打开统一面板时:`specialization.pending != null`(L10 三选一 / L50 二选一)→ **自动弹专精选择**;`确定`(choose_specialization)后 → 面板变天赋树(L50 后含二级专精扩展)。无 pending → 直接天赋树;path 空且无 pending → "专精后解锁"。
- 即:统一面板 = `pending ? 专精选择 : (talentTree ? 树 : 锁)`。复用 #6 的 `choose_specialization` action 与选择卡片 UI 当面板内步骤。

**D. 隐式被动 vs 显示被动(细化 §4 的"全部隐式"决定)**:
- **隐式被动 = 只加固定属性**(`stat_mod`)→ 折进属性、**不在被动列表显示**(维持现状)。
- **显示被动 = 需要计算的**(`skill_patch` 改技能 / `handler` 特殊效果 / 吃加成的效果)→ **作为只读条目出现在被动列表(被动 tab)**,来源标 talent、不可卸不可洗(除非 respec)。
- 落法:被动列表快照(`ui.render_skillbook` 的 passive 部分)追加 talent 的**显示类**派生被动(`effect ∈ {skill_patch, handler}`);`stat_mod` 类不追加。
