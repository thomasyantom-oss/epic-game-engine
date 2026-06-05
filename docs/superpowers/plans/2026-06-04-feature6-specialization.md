# Feature #6 — 专精(Specialization) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **本仓库实现层是 Codex（headless）。** 本 plan 同时是 `codex exec` 的 prompt。Codex 实现 + 跑测试，**不自己 commit**；Claude review diff → 用户 verify → Claude 提交。

**Goal:** 实现"专精选择机器"——分层不可洗的身份岔路：选定后改主属性 / R 追溯重推成长 / 授专属被动；只存玩家选择(path)，门控的升级树留给 #7。

**Architecture:** 套现成 modifier 生命周期(存数据、load 重注册、recalculate)，零新持久化机制。新增 `Specialization{path}` 组件当唯一真相源；`spec` modifier(priority 220)覆写 weaponAttr；`level_growth` 改读 spec-aware `effectiveGrowth`；专属被动走 #5 框架塞 `Skillbook.known`；快照加 `specialization` 块；前端点亮专精按钮开面板。

**Tech Stack:** Java 21 / Spring Boot(引擎 + 快照 record) · GraalJS(mods 逻辑) · SnakeYAML(数据) · Vue 3(前端) · JUnit(后端测试) · `mvn test` / `npm run build`。

**权威 spec:** `docs/superpowers/specs/2026-06-04-feature6-specialization-design.md`（含 Codex review 已纳的 §10）。冲突以 spec 为准。

---

## 给实现者的 5 件套（Codex 必读）

### ① 验收标准（含边界 + 明确不做什么）
- 法师 L10 选 `arcanist`：成长**追溯**为 `{智力4,体质1}×(level-1)`（敏捷成长归零，整表替换非 merge），maxHp 随体质升、当前血夹顶不溢出。
- 法师 L10 选 `elementalist`（不覆 growth）：维持职业默认成长 `{智力3,敏捷1,体质1}`。
- 法师 L50 选 `pyromancer`：`lifesteal_on_kill` 进 `Skillbook.known`(kind=passive) 且 `Passive.registerStatMods` 生效；`path == [elementalist, pyromancer]`。
- 校验拒绝（状态不变 + 可读 message 进 action result）：未到级选 tier2 / 跨大类(parent 不匹配) / 同 tier 重选。
- **load 往返**：选专精→持久化→重载，属性/成长/被动**逐位一致**，无重启膨胀。
- 合成 `main_attr` 节点：`spec`(220) 覆写 weaponAttr 后 derived(300) 读到新值 → `物理强度` 改吃新属性；不误改武器元数据。
- 未专精角色(path 空，含 L>1)行为与本 feature 前完全一致。
- **不做**：升级树 gating / 显示 / 升级 API 校验(全归 #7)；真专精分类与数值(内容轮)；灵魂球(#7/Ch3)；洗点(永不可洗)；其它职业专精树(内容轮)；`applySkillLevelCurve` 维持 no-op。

### ② 修改边界
- **可动**：新增 `specializations/mage.yaml`、`handlers/character/specialization.js`、`handlers/ui/specialization.js`、`frontend/.../SpecializationPanel.vue`；改 `modifier_types.yaml`、`leveling.js`、`select.js`、`recalculate_hooks.js`、`00_skill_lib.js`、`WorldSnapshot.java`、`ScriptRuntime.java`、`SnapshotService.java`、`SnapshotRenderer.vue`。
- **别碰**：战斗流程/动画/伤害管线；`ModifierChain.java` 引擎本体（只新增 modifier type 数据，不改 Java 链逻辑）；golden（`SkillFidelityTest` 不重生成）；sim 包；`allocate_point` 的休眠语义（只守卫不复活）。
- 允许就地微调不适配处，但偏离本 plan 的结构性决定要在回话里标注，等 review 判。

### ③ 验证方式
- 后端：`cd backend && mvn test`（全绿；新增测试类见 Task A5）。
- 前端：`cd frontend && npm run build`（通过）。
- 测试环境无人工点击；前端行为以 build 通过 + 组件渲染三态逻辑（path/pending/locked）为准，真人 smoke 留用户。

### ④ 隐含产品偏好
- UI：复用 #4 `Modal.vue` 外壳；**粗边框 ≥2px，禁 1px 细边**；只改既定面板内容**不动 4 宫格布局**；战斗中专精面板只读（对齐 #4/#5）。
- 文案：中文；不可洗选择要**二次确认**且文案点明"不可更改"；终态显示"已达最深专精"；拒绝原因可读。
- 空/错态：未专精显示根 tier 可选；未到级的层灰显"L50 解锁"；选项卡片展示效果摘要(main_attr/growth/grant_passives)。

### ⑤ 现有代码约束
- **priority 由 `modifier_types.yaml` 决定，不是注册顺序**：equipment50/passive70/level90/class180/derived300。新 `spec` 取 **220**。
- modifier 从不持久化：`entity.loaded` 复位 stat 组件 → 重注册全部 modifier → recalculate。`ModifierChain.addModifier` 按 id 先删后加（幂等）。
- `Specialization` 是**非 base 结构性组件**（像 EquipmentSlots），靠 `persistent` tag 自动存，**不进** `charSchema.baseComponents()` / `setBaseSelective`。
- weaponAttr 是 PrimaryStats 的 base 字段（select.js 在 setBase 前写 class 默认；load 期还原 class 默认）。专精覆写**只能是 modifier 输出**，绝不写 base。
- JS↔Java 桥：快照 record 由 `ScriptRuntime` 暴露 `newXxx` 工厂 + 事件回填（参考 `newSkillEntry` / `ui.render_skillbook` / `SnapshotService.buildSkillbook`）。
- 被动框架(#5)：`Passive.registerStatMods(id)` + `Skillbook.known` 的 `{base,node,level}` 条目(kind 由 passives/*.yaml 决定)。

---

## File Structure（决策锁定）

| 文件 | 职责 |
|------|------|
| `mods/base-rules/specializations/mage.yaml` | 法师专精树 demo 数据（tier/requires_level/parent/可选 main_attr,growth,grant_passives） |
| `mods/base-rules/modifier_types.yaml` | +`spec` type，priority 220 |
| `mods/base-rules/handlers/character/specialization.js` | 专精核心：树加载 `loadSpecTree` / `effectiveGrowth` / `registerSpecModifier` / `applySpec` / `action.choose_specialization` |
| `mods/base-rules/handlers/character/leveling.js` | 改 `registerLevelGrowthModifier` apply 读 `effectiveGrowth(entity)`；守卫 `allocate_point` |
| `mods/base-rules/handlers/character/select.js` | 建角创建 `Specialization{path:[]}` 组件 + 调 `applySpec` |
| `mods/base-rules/handlers/character/recalculate_hooks.js` | `entity.loaded` 调 `applySpec`（重注册 spec modifier + spec-aware level_growth） |
| `mods/base-rules/handlers/skill/00_skill_lib.js` | `resolveSpec` += `applySpecializationPatches`（空接缝，读 path 节点 `skill_patches`） |
| `mods/base-rules/handlers/ui/specialization.js` | `ui.render_specialization`：组 path/pending/locked |
| `backend/.../snapshot/WorldSnapshot.java` | +`Specialization` records + `inGame` 字段 |
| `backend/.../script/ScriptRuntime.java` | +`newSpecialization*` 工厂桥 |
| `backend/.../snapshot/SnapshotService.java` | +`buildSpecialization(playerId)` fire `ui.render_specialization` |
| `frontend/src/components/SpecializationPanel.vue` | 面包屑 path + pending N 选一卡片 + locked 灰显 + 不可洗确认 |
| `frontend/src/components/SnapshotRenderer.vue` | 点亮专精按钮 + 接 Modal |
| `backend/src/test/.../SpecializationTest.java`(+`SpecializationSnapshotTest`) | 见 Task A5 / B1 |

---

# Phase A — 后端机制 + 数据 + 测试

## Task A1: `spec` modifier type + 法师专精树数据

**Files:**
- Modify: `mods/base-rules/modifier_types.yaml`
- Create: `mods/base-rules/specializations/mage.yaml`

- [ ] **Step 1: 加 `spec` modifier type**（priority 220，class 180 后、derived 300 前；exclusive：同一实体专精 modifier 唯一、整体替换）

```yaml
  - id: spec
    label: 专精
    stack_rule: exclusive
    base_priority: 220        # class(180) 之后覆写 weaponAttr,derived(300) 之前被读到
```

- [ ] **Step 2: 写法师专精树 demo**（`specializations/mage.yaml`，节点形状见 spec §2.1）

```yaml
class: mage
nodes:
  - { id: elementalist, label: "元素法师", tier: 1, requires_level: 10, parent: null }
  - { id: arcanist,     label: "奥术法师", tier: 1, requires_level: 10, parent: null, growth: { 智力: 4, 体质: 1 } }
  - { id: naturalist,   label: "自然法师", tier: 1, requires_level: 10, parent: null, growth: { 智力: 2, 敏捷: 2, 体质: 1 } }
  - { id: pyromancer,  label: "火",       tier: 2, requires_level: 50, parent: elementalist, grant_passives: [lifesteal_on_kill] }
  - { id: cryomancer,  label: "冰",       tier: 2, requires_level: 50, parent: elementalist }
  - { id: stormmancer, label: "电/射线",  tier: 2, requires_level: 50, parent: elementalist }
  - { id: aoe_arcane,  label: "范围",     tier: 2, requires_level: 50, parent: arcanist }
  - { id: bolt_arcane, label: "投射/召唤", tier: 2, requires_level: 50, parent: arcanist }
  - { id: toxinist,    label: "毒性",     tier: 2, requires_level: 50, parent: naturalist }
  - { id: psionicist,  label: "灵能",     tier: 2, requires_level: 50, parent: naturalist }
```

- [ ] **Step 3: 验证加载** — `cd backend && mvn test -Dtest=ModifierTypeRegistryTest`（确认 `spec` type 注册、priority 220 解析）。若该测试枚举 type 数量需同步更新断言。
- [ ] **Step 4: Commit** `feat(ch1-f6): spec modifier type(220) + 法师专精树 demo 数据`

## Task A2: `Specialization` 组件 + `effectiveGrowth` + spec modifier + `applySpec`

**Files:**
- Create: `mods/base-rules/handlers/character/specialization.js`
- Modify: `mods/base-rules/handlers/character/leveling.js`

**`specialization.js` 必含函数：**

- [ ] **Step 1（测试先行）** 在 `SpecializationTest.java`（Task A5 建类，可先放本任务相关 case）写："建法师 L1→无 Specialization 影响；手动 set path=[arcanist] 后 `applySpec` + recalculate → PrimaryStats 智力 = base+classMod+`4×(level-1)`、敏捷成长=0"。先跑红。

- [ ] **Step 2: `loadSpecTree(classId)`** — `engine.loadYaml("specializations/" + classId + ".yaml")`，缓存；提供 `specNode(classId, id)` 查节点、`pathNodes(entity)`（按 path 顺序返回节点对象）。无树(其它职业)返回 null/空，全部下游 no-op。

- [ ] **Step 3: `effectiveGrowth(entity)`** — 取 `classSchema.growth` 为基；遍历 `pathNodes(entity)`，**最深**一个声明了 `growth` 的节点**整表替换**（不 merge）；返回普通 JS map。无 path/无覆写 → 职业 growth 原样。

- [ ] **Step 4: 改 `leveling.js` 的 `registerLevelGrowthModifier`** — apply 内把 `var growth = classSchema.raw().get("growth")` 换成 `var growth = effectiveGrowth(entity)`（apply 时按当前 entity 实时算，故 recalc 自动 spec-aware）。保留 id `level_growth`、`× (capturedLevel-1)` 语义不变。

- [ ] **Step 5: `registerSpecModifier(entityId)`** — `engine.addModifier(id, {typeId:"spec", id:"spec_mod", label:"专精", apply:function(ent){...}})`：遍历 `pathNodes(ent)`，对声明 `main_attr` 的节点 `ent.getComponent("PrimaryStats").set("weaponAttr", node.main_attr)`（**覆写，不读 base**）；对节点其它 flat 属性加值用 `+=`。空 path → apply 内什么都不做（modifier 仍注册，幂等）。

- [ ] **Step 6: `applySpec(entityId)`（choose 与 load 共用的唯一 codepath）**：
  1. `registerSpecModifier(entityId)`
  2. `registerLevelGrowthModifier(entityId, currentLevel)`（重注册，确保 spec-aware）
  3. 对 path 上每个节点的 `grant_passives`：若 `Skillbook.known` 无同 base 条目则 push `{base, node:null, level:1}`（**查重防 dup**），然后 `Passive.registerStatMods(entityId)`
  4. `engine.recalculate(entityId)`

- [ ] **Step 7: 跑 Step 1 测试转绿** `mvn test -Dtest=SpecializationTest`。
- [ ] **Step 8: Commit** `feat(ch1-f6): Specialization 组件 + effectiveGrowth(R 追溯) + spec modifier + applySpec`

## Task A3: 建角 + load 接入 `applySpec`

**Files:**
- Modify: `mods/base-rules/handlers/character/select.js`
- Modify: `mods/base-rules/handlers/character/recalculate_hooks.js`

- [ ] **Step 1（测试）** 在 `SpecializationTest` 加："建法师角色→有 `Specialization` 组件且 `path` 为空 List；未专精属性与本 feature 前一致（拿一条基准断言，如 L1 法师智力 = base11+class）"。

- [ ] **Step 2: `select.js` 建角** — 在创建其它非 stat 组件处加：
```js
var specComp = engine.newComponent("Specialization");
specComp.set("path", engine.newList());
entity.addComponent(specComp);
```
并在现有 `Passive.registerStatMods` + recalculate 之前/之后调 `applySpec(charId)`（空 path → 注册空 spec modifier，无副作用，保证 load 对称）。

- [ ] **Step 3: `recalculate_hooks.js` 的 `entity.loaded`** — 在现有 `Passive.registerStatMods` 一段附近调 `applySpec(entity.getId())`（重注册 spec modifier + spec-aware level_growth + 重发 grant_passives 查重 + recalculate）。注意：若实体无 `Specialization` 组件（老存档），先补一个空 path 组件再 applySpec（向后兼容）。

- [ ] **Step 4: load 往返测试** — 仿 `ReloadInflationBugTest`：建法师→set path=[arcanist]→applySpec→save→clear store→load→断言属性/成长**逐位等于** reload 前（无膨胀）。跑红→绿。

- [ ] **Step 5: Commit** `feat(ch1-f6): 建角创建 Specialization + load 重注册(applySpec 对称)`

## Task A4: `resolveSpec` 专精 patch 空接缝

**Files:**
- Modify: `mods/base-rules/handlers/skill/00_skill_lib.js`

- [ ] **Step 1（测试）** 在 `ResolveSpecTest` 加："带 path 的法师施法，技能 spec 经 `resolveSpec` 后字段不变（demo 节点无 skill_patches）；管线含 specialization 阶段不破坏 level/node/passive 既有结果"。

- [ ] **Step 2: 加 `applySpecializationPatches(ctx, baseId, spec)`** — 镜像 `applyPassivePatches`：遍历施法者 `Specialization.path` 节点，读节点可选 `skill_patches`(数组，元素 `{match, patch}`)，复用现成 `_matchSkill`/`_applyPatch`。demo 节点无此字段 → pass-through。

- [ ] **Step 3: 挂进 `resolveSpec`** — 在 `applyPassivePatches` 之后：
```js
spec = this.applyPassivePatches(ctx, baseId, spec);
spec = this.applySpecializationPatches(ctx, baseId, spec);
return spec;
```

- [ ] **Step 4: 回归** `mvn test -Dtest=ResolveSpecTest,SkillFidelityTest,SkillLibTest`（golden 不变）。
- [ ] **Step 5: Commit** `feat(ch1-f6): resolveSpec 补 applySpecializationPatches 空接缝(落 §1.2 专精 patch 源)`

## Task A5: `action.choose_specialization` + 校验 + 全量后端测试

**Files:**
- Modify: `mods/base-rules/handlers/character/specialization.js`
- Test: `backend/src/test/java/com/epic/engine/character/SpecializationTest.java`

- [ ] **Step 1: `action.choose_specialization` handler**（mirror `SkillbookActionsTest` 驱动的 action 形状）：取 token→active char、`specId`；`loadSpecTree(classId)` 找节点；**校验**：节点存在 ∧ `parent == path 末端`(根则 parent==null ∧ path 空) ∧ `requires_level ≤ 等级` ∧ 该 tier 未在 path。任一失败 → 设 action result `success=false` + 中文 message，**不改状态**、不 save。通过 → `path.push(specId)` → `applySpec` → `persistence.save`。

- [ ] **Step 2: 补齐测试矩阵**（全部在 `SpecializationTest`，harness 仿 `CharacterFlowTest`/`PassiveStatModTest`）：
  - `arcanist` 追溯成长（智力 ×4、敏捷 ×0、体质 ×1 per (level-1)）+ maxHp 升 + 当前血夹顶。
  - `elementalist` 维持职业默认成长。
  - `pyromancer`(需先 elementalist + L≥50) → `lifesteal_on_kill` 入 known + `Passive.registerStatMods` 生效 + `path==[elementalist,pyromancer]`。
  - 拒绝三连：未到级 tier2 / 跨大类 parent 不匹配 / 同 tier 重选 → `success=false`、状态不变。
  - **合成 `main_attr` 节点**（测试内临时树或在 mage.yaml 加一个隐藏测试节点声明 `main_attr:体质`）：`spec`(220) 覆写 weaponAttr → derived(300) 后 `物理强度` 改吃体质；装备武器在身时不误改武器 `weaponAttr/base` 元数据。
  - `allocate_point` 休眠（`pendingPoints=0`）下 spec-aware `level_growth` 不被覆盖。
  - 终态：选到 tier2 后 `pending=null`（此断言可放 Task B1 快照测试）。
  - 被动 grant 重复 choose/load 不产生 duplicate。

- [ ] **Step 3: 全量回归** `cd backend && mvn test`（全绿；现有 modifier/快照/战斗/passive/resolveSpec 测试不破）。
- [ ] **Step 4: Commit** `feat(ch1-f6): choose_specialization action + 校验 + 全量后端测试`

---

# Phase B — 快照 + 前端

## Task B1: 快照 `specialization` 块（Java record + 桥 + builder + JS handler）

**Files:**
- Modify: `backend/.../snapshot/WorldSnapshot.java`
- Modify: `backend/.../script/ScriptRuntime.java`
- Modify: `backend/.../snapshot/SnapshotService.java`
- Create: `mods/base-rules/handlers/ui/specialization.js`
- Test: `backend/src/test/java/com/epic/engine/snapshot/SpecializationSnapshotTest.java`

- [ ] **Step 1: WorldSnapshot records**（仿 `Skillbook`/`SkillEntry`）：
```java
public record SpecOption(String id, String label, String description, SpecEffects effects) {}
public record SpecEffects(String mainAttr, Map<String,Object> growth, List<String> grantPassives) {}
public record SpecPending(int tier, int requiresLevel, List<SpecOption> options) {}
public record SpecPathNode(String id, String label) {}
public record SpecLocked(int tier, int requiresLevel) {}
public record Specialization(List<SpecPathNode> path, SpecPending pending, List<SpecLocked> locked) {}
```
加进 `inGame` record 尾部字段 `Specialization specialization` + 工厂参数 + `null` 占位处同步（characterSelect/characterCreate 传 null）。

- [ ] **Step 2: ScriptRuntime 桥** — 加 `newSpecOption/newSpecEffects/newSpecPending/newSpecPathNode/newSpecLocked/newSpecialization` 工厂（仿 `newSkillEntry`）。

- [ ] **Step 3: SnapshotService.buildSpecialization(playerId)** — fire `ui.render_specialization` 事件回填 path/pending/locked（仿 `buildSkillbook` + `ui.render_skillbook`），塞进 `inGame(...)`。

- [ ] **Step 4: `ui/specialization.js` handler** — 读 `Specialization.path` + `loadSpecTree(classId)` + 等级：
  - `path` → [{id,label}]
  - `pending`：parent==path 末端 ∧ requires_level≤等级 ∧ tier 未选的节点；每个带 `effects{mainAttr,growth,grantPassives}`；为空 → null
  - `locked`：parent 在 path 链上但 requires_level>等级的层 → [{tier,requiresLevel}]

- [ ] **Step 5: 快照测试**（`SpecializationSnapshotTest`，仿 `SkillbookSnapshotTest`）：未专精 L10→pending 给三个 tier1 选项含 effects；选 elementalist 后 L10→pending=null（tier2 要 L50）、locked 含 {2,50}；L50 选 pyromancer→终态 pending=null ∧ locked=[]。
- [ ] **Step 6: Commit** `feat(ch1-f6): 快照 specialization 块(record+桥+builder+JS handler)`

## Task B2: 前端专精面板 + 点亮按钮

**Files:**
- Create: `frontend/src/components/SpecializationPanel.vue`
- Modify: `frontend/src/components/SnapshotRenderer.vue`

- [ ] **Step 1: `SpecializationPanel.vue`** — props `{ specialization, readonly }`，emit `action`。渲染：① 已选 path 面包屑；② `pending` 非空 → N 张选项卡片（label + description + effects 摘要：主属性变更/成长/授被动），点击 → **不可洗二次确认**（确认文案含"不可更改"）→ emit `{type:'choose_specialization', params:{specId}}`；③ `locked` 灰显"L{requiresLevel} 解锁"；④ 终态（pending=null ∧ locked=[]）→ "已达最深专精"。`readonly`(战斗中)禁用选择。**粗边框 ≥2px**，复用既有 CSS 变量/配色，不动布局。

- [ ] **Step 2: 点亮按钮 + Modal**（`SnapshotRenderer.vue`）— 把 `<button class="menu-btn" disabled title="待后续开放">专精</button>` 改为 `@click="openSpec"`（仿 `openSkillbook`）；加 `modalActive==='spec'` 的 `<Modal>` 包 `SpecializationPanel`，传 `:specialization="snapshot.specialization"` `:readonly="!!snapshot.combat"`，转发 `@action`。

- [ ] **Step 3: 构建验证** `cd frontend && npm run build`（通过）。
- [ ] **Step 4: Commit** `feat(ch1-f6): 前端专精面板 + 点亮专精按钮`

## Task B3: 收尾验证

- [ ] **Step 1: 后端全量** `cd backend && mvn test`（全绿）。
- [ ] **Step 2: 前端** `cd frontend && npm run build`（通过）。
- [ ] **Step 3: 自检清单** — 5 件套①验收逐条对照；未专精回归；golden 未变；工作区无意外文件（spec/plan/codex-runs 之外别动）。
- [ ] **Step 4: 交付 Codex 回话**（`-o` 文件）：自陈改了哪些文件 + 测试结果 + "请重点 review"列表（priority 落位 / weaponAttr 不写 base / load 往返无膨胀 / allocate_point 未被复活 / 快照 record 改动 / 前端不可洗确认）。**不 commit**。

---

## Self-Review（写完对 spec 核对）

- **spec 覆盖**：§1 模型→A1 数据；§2.1 节点字段→A1+A2;§2.2 组件→A3；§3 生命周期(spec modifier/effectiveGrowth/allocate_point/applySpec/priority220)→A2+A3；§5 resolveSpec 接缝→A4；§6 快照+前端→B1+B2；§7 测试→A5+B1；§10 Codex 5 项全部落到任务。✅ 无遗漏。
- **占位扫描**：无 TBD；test step 给了具体断言数值(智力×4 等)与 mirror 测试类名，Java 测试体由 Codex 按 harness 补全（团队约定 Codex 实现+测试）。
- **类型一致**：`applySpec`/`effectiveGrowth`/`registerSpecModifier`/`registerLevelGrowthModifier`/`applySpecializationPatches`/`loadSpecTree` 跨任务同名;快照 record 名(`Specialization`/`SpecPending`/`SpecOption`/`SpecEffects`/`SpecPathNode`/`SpecLocked`)与桥工厂、JS handler 字段对齐。
- **顺序**:A1→A2(依赖 spec type)→A3(依赖 applySpec)→A4(独立)→A5(依赖 action)→B1(依赖组件/树)→B2(依赖快照)→B3。
