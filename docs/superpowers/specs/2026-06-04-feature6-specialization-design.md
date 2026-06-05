# Feature #6 — 专精(Specialization) · 设计 spec

**日期:** 2026-06-04
**性质:** Chapter 1 第 6 个 feature 的实现 spec。遵循母文档 `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md` §1 架构决策 + §2 分解表。
**依赖:** #2 属性表 / #4 Skillbook / #5 被动框架 / 等级系统(均已合并 master)。
**关系:** 本 feature 只造"专精这台选择机器"。它**门控**的升级树(#7)尚不存在——故 #6 不实现任何 gating,只把玩家的选择(一条路径)正确存下来,留给 #7 消费。

---

## 0. 一句话定位

专精 = 一串**分层、不可洗的身份岔路**。每到指定等级三选一,深层是浅层的子节点(细化),角色因此持有一条**路径**而非单值。选定的真副作用:**改主属性 + 追溯重算成长 + 授专属被动**。它解锁的升级树是 #7 的事;#6 只发钥匙(路径)。

母文档 §1.10 已拍死:专精=门 / 灵魂球=梯子 / 不可洗 / 不给独立天赋树。本 spec 落地它。

---

## 1. 模型(本轮 brainstorm 拍死)

### 1.1 专精 = 分层里程碑树(非一次性三选一)

- 每职业一棵专精树,按 **tier** 分层。
- 每个 tier 在**到达 `requires_level` 后**开放一次"N 选一"。
- 深层节点是浅层选择的**子节点**(refinement),`parent` 指向上层 id;根 tier 的 `parent = null`。
- 角色的"专精"= 一条**从根到当前深度的路径**(如 `[元素, 火]`),逐层不可洗,效果**向下累积**(浅层 + 深层都生效)。
- 到达 `requires_level` 但未选 = 该层选项可选但**不强制**(前期清爽,§1.10);角色停在当前路径,继续用基础技能组。

### 1.2 与"不给专精独立天赋树"(§1.10)自洽

天赋树 = 花点数/货币在一堆节点里 buy。专精树 = 一串**无货币、无点数、不可洗的门**。爬树的"梯子"仍只有 #7 + 灵魂球这一套。故专精是"多道门串联",不是第二条养成轨——不违反 §1.10。

### 1.3 可逆性(§1.7)

每层一旦选定**不可洗**;更深的 tier 在各自 `requires_level` 解锁。专精是确定性玩家选择、非 RNG,不可逆不违反 roadmap §4.4。

---

## 2. 数据模型

### 2.1 专精树数据(每职业一份)

新增数据目录 `mods/base-rules/specializations/<classId>.yaml`,模块加载期读入(挂 SpecializationRegistry,或最小实现走 `engine.loadYaml`——具体装载在 plan 定)。

节点 schema:

```yaml
# specializations/mage.yaml
class: mage
nodes:
  # ---- tier 1 (L10):元素 / 奥术 / 自然 ----
  - id: elementalist
    label: "元素法师"
    tier: 1
    requires_level: 10
    parent: null
    # growth 不覆盖 → 继承职业默认(法师 = 智力为主)
  - id: arcanist
    label: "奥术法师"
    tier: 1
    requires_level: 10
    parent: null
    growth: { 智力: 4, 体质: 1 }
  - id: naturalist
    label: "自然法师"
    tier: 1
    requires_level: 10
    parent: null
    growth: { 智力: 2, 敏捷: 2, 体质: 1 }

  # ---- tier 2 (L50):各大类下细化 ----
  - { id: pyromancer,  label: "火",   tier: 2, requires_level: 50, parent: elementalist, grant_passives: [lingering_burn] }
  - { id: cryomancer,  label: "冰",   tier: 2, requires_level: 50, parent: elementalist }
  - { id: stormmancer, label: "电/射线", tier: 2, requires_level: 50, parent: elementalist }
  - { id: aoe_arcane,  label: "范围", tier: 2, requires_level: 50, parent: arcanist }
  - { id: bolt_arcane, label: "投射/召唤", tier: 2, requires_level: 50, parent: arcanist }
  - { id: toxinist,    label: "毒性", tier: 2, requires_level: 50, parent: naturalist }
  - { id: psionicist,  label: "灵能", tier: 2, requires_level: 50, parent: naturalist }
```

**节点可选字段(无 = 纯"门"):**

| 字段 | 含义 | 落到哪 |
|------|------|--------|
| `main_attr` | 覆写 `PrimaryStats.weaponAttr`(物理强度吃哪条属性,§1.10 战士→盾战场景) | 走 spec modifier / load 期 weaponAttr 覆写 |
| `growth` | 覆写每级成长模板(R 追溯重推) | `effectiveGrowth()` 喂 `level_growth` modifier |
| `grant_passives` | 授予的专属被动 id 列表 | 选定时塞进 `Skillbook.known`,走 #5 框架 |

> **节点 id 本身就是 #7 的钥匙**(`requires_spec`),无需 `opens` 字段。

**demo 取值(本 feature 用):** 法师树,主属性三者都不变(均 `智力` 输出)、只覆 growth(元素不覆 / 奥术 `{智力4,体质1}` / 自然 `{智力2,敏捷2,体质1}`);`火` 授 `lingering_burn` 验证授被动通路。**`main_attr` 机制照建,但 demo 不用它(去掉盾战);改由单元测试用合成节点覆盖该通路。** 真专精分类 + 数值留「Ch1 内容设计轮」。

### 2.2 角色状态

新增组件 `Specialization { path: [<nodeId>, ...] }`——**持久化的唯一真相源**,与 `classId` 同级。空数组 = 未专精。属性值不是真相源(见 §3)。

---

## 3. 生命周期:套现成 modifier 路,零新机制

引擎既定:**modifier 从不持久化**;每次 `entity.loaded` 把属性组件复位成 schema 干净默认,再从数据(class/level/装备/被动 + 现加 `path`)**重新注册全部 modifier + recalculate**。这保证累加型 modifier 不会重启翻倍(注释里的 bug #2),且 `ModifierChain.addModifier` 按 id 先删后加 → 幂等。

专精接这条路,**加三样、无新持久化负担:**

1. **`registerSpecModifier(entityId)`** —— 读 `Specialization.path`,注册 `typeId:"spec"` modifier:对 path 上声明了 `main_attr` 的节点覆写 `PrimaryStats.weaponAttr`(+ 任何 flat 属性加值);与 `class` modifier 逐行同构。weaponAttr 覆写复用 load 期"按 class 还原 weaponAttr"的同一位置(class 先写默认 → 专精紧跟覆写)。
2. **成长追溯(R)** —— `level_growth` modifier 现读 `classSchema.growth`;改为读 `effectiveGrowth(entity)` = 职业 growth,被 path 上**最深**声明了 `growth` 的节点覆盖(整表替换,非合并)。因 modifier 从干净 base `level × 模板` 重推,**前 N 级自动按新模板长出**——无迁移、无脏数据。
3. **专属被动** —— 选定时把 `grant_passives` 塞进 `Skillbook.known`(kind=passive 条目)。它本就持久化 + load 期 `Passive.registerStatMods` 自动重注册,**专精搭便车,reload 不用管**。

**装配顺序**(在 `select.js` 建角 + `recalculate_hooks.js` load 两处接):`class → level_growth(spec-aware) → equipment → spec(weaponAttr/flat) → derived(300) → passive`,最后一次 `recalculate`。HP/MP 由现成 `before/after_recalculate` 的 scratch 钩子夹到新上限(选奥术体质涨→maxHp 升、当前血保留并夹顶,平滑不破)。

---

## 4. 选择动作(玩家选专精)

`action.choose_specialization { specId }`:

1. 校验:`specId` 存在于本职业树;其 `parent` == 当前 `path` 末端(根 tier 则 `parent==null` 且 path 空);`requires_level` ≤ 当前等级;该 tier **尚未选过**(path 中无同 tier 节点)。任一不满足 → 拒绝(返回错误,不改状态)。
2. `path.push(specId)`。
3. 跑 §3 的 `applySpec` codepath(注册 spec modifier + 重注册 spec-aware `level_growth` + 授 `grant_passives` → recalculate)。**选定与 load 共用同一段 `applySpec`**。
4. `persistence.save`。

不提供"洗点/重选"动作(§1.7 不可洗)。

---

## 5. #6 ↔ #7 契约(薄,选项 B)

- #6 **只**把 `Specialization.path` 存下来并持久化。
- #6 **不实现任何 gating**——升级入口在 #7 的技能树 UI 里,未专精则树不渲染、天然无入口;现在 #7 不存在,没有消费者可挡。
- #7 落地时读 `path`:① UI 决定显示哪棵对应技能树;② 升级 API 校验"请求的升级节点 `requires_spec` 是否在 `path` 内",不在则 reject(防伪造 call 作弊兜底,单机里就是防改包)。
- 规则(显示/校验)全长在 #7 那侧 → 专精天然只发钥匙(选项 B)。灵魂球纯 #7,#6 不碰。

---

## 6. 快照 + 前端

### 6.1 快照新增 `specialization` 块

```jsonc
specialization: {
  path: [ { id, label }, ... ],            // 已选路径
  pending: {                               // 当前可选的下一层;无可选则 null
    tier, requires_level,
    options: [ { id, label, description } ]
  } | null,
  locked: [ { tier, requires_level } ]     // 更深、尚未到级的层(灰显提示)
}
```

`pending` 计算:取 path 末端为 parent、`requires_level ≤ 等级` 且该 tier 未选的节点集;为空则 null。

### 6.2 前端

- 点亮 `SnapshotRenderer.vue` 右下角现禁用的 `专精` 按钮 → 开 Modal(复用 #4 的 `Modal.vue` 通用外壳)。
- 新 `SpecializationPanel.vue`:展示已选路径(面包屑)+ 当前 `pending` 的 N 选一卡片 + `locked` 层灰显("L50 解锁")。
- 选择走**不可洗二次确认**(选定即焊死,文案点明)。战斗中只读(对齐 #4/#5 readonly 约定)。
- 遵守用户 UI 规范:粗边框(≥2px)、只改既定面板内容不动布局。

---

## 7. 验收 / 测试(完全不依赖 #7)

**后端单元测试:**
- 法师 L10 选 `arcanist` → growth 追溯:PrimaryStats 按 `{智力4,体质1}×(level-1)` 重算(对比选前/选后同级),maxHp 随体质升、当前血夹顶。
- L10 选 `elementalist`(不覆 growth)→ 维持职业默认成长。
- L50 选 `pyromancer` → `lingering_burn` 进 `Skillbook.known` 且 `Passive.registerStatMods` 生效;`path == [elementalist, pyromancer]`。
- 校验拒绝:未到级选 tier2 / 跨大类选(parent 不匹配) / 同 tier 重选 → 全拒,状态不变。
- **load 往返**:选专精 → 持久化 → 重载,属性/成长/被动**逐位一致**(验证 `registerSpecModifier` 重注册 + 幂等,无重启膨胀)。
- **`main_attr` 通路**:用合成测试节点(声明 `main_attr`)验证 weaponAttr 覆写 + 物理强度改吃新属性(覆盖去掉盾战 demo 后的空白)。
- **回归**:未专精角色(path 空)行为与本 feature 前完全一致;现有 modifier/快照/战斗测试全绿。

**前端构建:** `npm run build` 通过;专精面板渲染路径/pending/locked 三态。

---

## 8. 范围边界

- **IN:** 专精树数据格式 + demo 法师树;`Specialization{path}` 组件 + 持久化;选择动作 + 校验 + 不可洗;`applySpec`(spec modifier / R 追溯成长 / 授被动)+ load 重注册;快照 `specialization` 块 + 前端面板;`main_attr` 机制(测试覆盖)。
- **OUT(延后):**
  - 升级树 gating / 显示 / 升级 API 校验 → **#7**(#6 只存 path)。
  - 真专精分类 + tier 等级阈值 + 各节点 growth/被动**数值** → Ch1 内容设计轮(本 feature 只给 demo 样例)。
  - 灵魂球 → #7 / Ch3。
  - 洗点经济 → 专精永不可洗,无此项。

---

## 9. 非阻塞 follow-up(记账)

- `applySkillLevelCurve`(人物 level→技能 level 曲线)仍是 no-op 接缝,内容轮填;本 feature 不动。
- 其它职业(战士/盗贼/守护/德鲁伊)的专精树 = 内容轮补;本 feature 只交付法师 demo + 机制。
