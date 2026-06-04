# Feature #4 — Skillbook 出战配置 + 指令栏外壳 · 设计

**日期:** 2026-06-03
**母文档:** `docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md`(§1 架构决策 / §2 分解表第 4 行)
**依赖:** Feature #1(技能架构重构,已合并)
**性质:** Ch1 第 4 个独立 feature。本轮做"技能怎么带、怎么出战" + 把战斗指令栏和右下角常驻菜单的**外壳**立起来。

---

## 0. 一句话

把角色技能组件从"扁平已知列表"升级成 §1.4 的 `Skillbook` 实例模型(`base/node/equipped` + 出战格),战斗指令栏只放**出战的**技能,再加一个**模态技能书**配出战、一个**右下角常驻菜单**当入口。`node`(进化节点)这轮只预留字段、不实现 —— 那是 Feature #7。

> **组件命名(2026-06-03 review 拍定):** 组件名从旧 `Skills` 改为 **`Skillbook`**(贴母文档 §1.4;旧 `Skills` 是"可执行技能列表",新语义是"拥有的技能实例 + 出战配置",#5 被动 / #7 skilltree 都挂这个边界)。已接受删档不兼容,直接改名。

---

## 1. 范围

### IN(本轮做)
- 数据模型:`Skillbook` 组件(旧名 `Skills`)升级为 `{ slots, known:[{base,node,equipped}] }`。
- 后端:snapshot 增 `skillbook` 块;新 action `skillbook.equip` / `skillbook.unequip`;`ui/actions.js` 改为"固定通用指令 + 出战技能"。
- 前端:
  - **通用模态外壳**(可复用组件)+ 技能书内容(`主动` tab 全功能,`被动`/`通用` tab 占位空壳)。
  - **战斗指令栏 command-row 重排**:固定 6 指令(3×2 缩窄)+ 子菜单 6 出战技能(3×2)+ 纵跨两行正方形取消键。
  - **右下角 panel-nav = 常驻菜单**(`背包`/`技能`/`专精`,技能唤出模态)。
- 测试:后端模型读写 + equip/unequip 校验 + actions 输出;golden 指令栏。

### OUT(不做,留给后续)
- 被动机制(#5)、技能进化树视图(#7)、专精(#6)。
- 移动(战斗内换位 + 怪物移动 AI)机制本体 —— 本轮只画**禁用占位按钮**。
- 道具(战斗内用道具)机制本体 —— 同上,禁用占位。
- 背包/专精 菜单内容 —— 禁用占位。
- 真技能图标资源 —— 用"技能名首字"占位。
- 出战取舍的数值/可玩性 validation —— 延到 #7 技能补全后一起测(known 主动技这轮就用职业 `starting_skills`,塞得进 6 格,不制造取舍压力)。

---

## 2. 数据模型(后端)

`Skillbook` 组件结构升级:

```
Skillbook {
  slots: 6,                                   # 出战格上限,从 class schema skill_slots 读,缺省 6
  known: [                                    # 玩家拥有的主动技能实例
    { base: "fireball", node: null, equipped: true },
    { base: "cleave",   node: null, equipped: true }
  ]
}
```

- `base` = 基础技能 id(对应 `skills/<base>.yaml`)。
- `node` = 进化节点 id(Feature #7 用)。**本轮恒为 `null`**,字段预留进数据结构 + 持久化,不读不写逻辑。
- `equipped` = 是否出战(进战斗指令栏)。
- **通用技能 `basic_attack` / `defend` / `flee` 不进 `known`** —— 它们是永远可用、不可卸、不占格的固定指令(见 §4)。
- 一个 `base` 全局只有一个实例(§1.4 已锁)。

### 2.1 创建角色(`select.js` 改动)

`action.confirm_character` 里构建技能组件的那段(当前 L108–129,`engine.newComponent("Skills")`)改写为 `engine.newComponent("Skillbook")`:
- 不再把 `basic_attack`/`defend`/`flee` 塞进列表 —— 它们移出 known,改由 actions.js 永远发(§4)。
- `known` 只放职业主动技:读 class schema `starting_skills`,每个建 `{ base, node:null, equipped:true }`(起手默认全出战,数量 ≤ 6)。
- `slots`:读 class schema `skill_slots`,缺省 6。这轮所有职业 schema 都不配 `skill_slots`,统一走缺省 6(留好"职业可改"口子,数值不分化)。

### 2.2 迁移

不做新旧格式转换。dev 阶段(系统构建期,转剧情关卡前)**允许删档重建**。实现时直接改 `select.js` 产出新 `Skillbook` 组件即可;已有持久化角色(旧 `Skills` 组件)重建,不必兼容。

---

## 3. 后端接口

### 3.1 snapshot 增 `skillbook` 块

`SnapshotService`(对应 mod 侧 snapshot 构建 handler)输出玩家的:

```
skillbook: {
  slots: 6,
  equippedCount: 4,
  known: [
    { base:"fireball", name:"火球", description:"...", icon:"火", equipped:true, node:null },
    { base:"ice_beam", name:"冰锥", description:"...", icon:"冰", equipped:false, node:null }
  ]
}
```

- `name` / `description` 从 `skills/<base>.yaml` 读(字段同名 `name` / `description`)。**字段名统一用 `description`**(与 YAML 及前端 `params.description` 对齐,不引入 `desc` 映射层)。
- `icon` = 占位:取 `name` 首字(前端也可自己截,但后端给一份省得前端重复逻辑)。YAML 留 `icon` 字段口子,有就用、没有 fallback 首字。
- 仅玩家(`player` tag)且有 `Skillbook` 组件的实体输出此块,否则 `skillbook` 为 null。

### 3.2 新 action:equip / unequip

- `action.skillbook_equip` { base } → 把对应 known 项 `equipped` 置 true。
  - 校验:① 当前 `equippedCount < slots`(满格拒绝);② **非战斗**(玩家无 `combat:*` tag);③ `base` 在 known 中。
- `action.skillbook_unequip` { base } → 置 false。校验:② 非战斗;③ 在 known 中。
- 改动后 `persistence.save`。

**失败语义 = 静默拒绝(silent reject)。** 校验不过时:不改状态、直接返回,**不**抛、**不**回错误信息。
- 理由:失败的可观测面 = **下一帧 snapshot 的 `known` 没变**(PM 可验证);UI 已挡掉所有失败场景(满格时「出战」禁用、战斗中不渲染按钮),后端校验是第二道防御闸,不是用户主路径。
- **不**接 error toast 通道:`SnapshotController.performAction`(L46–54)fire 完 action event 即丢弃,`buildInGameSnapshot` 又硬编码 `ActionResult(true,"ok")` —— 现成无传播。`ActionResult(success,message)` record 已存在,留作将来要做 toast 时的接缝(YAGNI,本轮不接)。

> 战斗中模态技能书只读 —— 前端不渲染出战/移除按钮(第一道);后端"非战斗"校验静默拒绝(第二道)。

### 3.3 `ui/actions.js`(战斗指令)改写

当前(L18–71)在战斗里把 `Skills.list` 所有技能平铺成 `combat_command`。改为(读 `Skillbook.known`):

1. **永远发三个通用指令**(不依赖 known):
   - `basic_attack` → label「攻击」,category `action`。
   - `defend` → label「防御」,category `action`。
   - `flee` → label「逃跑」,category `action`(沿用现有 flee 识别)。
   - 这三个的 spec 仍从各自 `skills/*.yaml` 读(targeting/prompt 等不变),只是来源不再是 known。
2. **发出战技能**:遍历 `Skillbook.known`,仅 `equipped===true` 的,按 `base` 读 yaml 发 `combat_command`,category `skill`(进"技能"子菜单)。`skill.can_use`(MP 等)逻辑沿用。
3. **移动/道具不由后端发** —— 它们是前端静态禁用占位按钮(见 §5.3),后端指令列表里没有。

> 注:「技能」按钮本身**不是**后端 action,是前端 toggle(见 §5.2)。后端只负责"哪些技能可出"。

非战斗分支(地图移动/POI/logout)不变。

---

## 4. 通用技能语义

- `basic_attack`(攻击)/ `defend`(防御)/ `flee`(逃跑)= **通用技能**:永远可用、不可卸、不占出战格。
- 它们不在 Skillbook `known` 里,玩家不能配置;技能书 `通用` tab(占位空壳)将来只读展示它们。
- 数据上仍是 `skills/*.yaml`,引擎照常 `loadSpec` 执行 —— 只是"谁拥有"这件事对它们恒为真。

---

## 5. 前端

### 5.1 通用模态外壳(可复用)

新组件 `Modal.vue`(或 `ModalOverlay.vue`):
- 浮层盖在 `game-grid` 上,背景压暗(半透明遮罩),居中内容框。
- 关闭:ESC、点遮罩空白、内置关闭按钮。
- 通过 App 级状态(如 `useModal` composable 或 SnapshotRenderer 的 ref)控制开/关 + 当前内容。
- **技能书是它的第一个租户**;背包/角色详情等后续 feature 复用同一外壳。
- 边框遵循项目规范:2px+,禁 1px(见用户偏好)。

### 5.2 技能书内容 `SkillbookPanel.vue`

放进模态。顶部 tab:`主动 | 被动 | 通用`(`专业` 远期不画)。
- **`主动` tab(全功能):**
  - 顶部标题行:`出战 N/6`。
  - 列出 `skillbook.known`:每条 = 占位图标方块〔name 首字〕+ 技能名 + 描述 + 右侧操作。
  - 出战的(`equipped`)高亮 + 置顶;未出战的在下。
  - 操作按钮:出战的显示「移除」(→ `skillbook_unequip`);未出战的显示「出战」(→ `skillbook_equip`);满格时「出战」禁用。
  - 每条留一个 `→树` 视觉接缝(灰/不可点),给 Feature #7 进化树入口占位。
  - **战斗中(snapshot.combat 存在)= 只读**:不渲染 出战/移除 按钮,仅展示。
- **`被动` / `通用` tab:** 占位空壳(一句"暂未开放"或空列表),把 tab 框架立起来。

### 5.3 战斗指令栏 command-row 重排(`BattleGrid.vue`)

当前 command-row 三列 `1fr 2fr 3fr`(头像 | 主指令 cmd-actions | 子菜单 cmd-sub)。重排:

- **`cmd-actions`(主指令)= 固定 6 按钮,3 列 × 2 行,缩窄**(每个 2 字):
  ```
  攻击  防御  技能
  移动  道具  逃跑
  ```
  - `攻击/防御/逃跑` = 对应通用指令(selectCommand)。
  - `技能` = 前端 toggle(沿用 `showSkills`),有出战技能时可点,展开子菜单。
  - `移动/道具` = **前端静态禁用占位**(后端不发指令,前端直接画两个置灰按钮)。
- **`cmd-sub`(子菜单)点"技能"后 = 6 出战技能 3 列 × 2 行 + 纵跨两行正方形取消键**:
  ```
  火球  冰锥  毒镖 │
  治疗  战吼  ──   │ 取消(正方形,grid-row 1/3)
  ```
  - 网格 `grid-template-columns: 1fr 1fr 1fr auto`;技能填前 3 列 2 行;取消键在第 4 列 `grid-row: 1 / 3`。
  - 出战不足 6 个 → 留空单元格。
  - 技能 hover tooltip(描述/MP)沿用现有 `onSkillHover`。
  - **同一布局位 + 样式**复用给将来"移动"/"道具"子菜单(本轮它们 disabled 不触发,但 cmd-sub 的网格规则按此设计,后续直接插内容)。
- `target` 选目标态的取消键沿用(已有 `cancelSelect`)。

### 5.4 右下角常驻菜单(`panel-nav`)

`SnapshotRenderer.vue` 的 `panel-nav` 当前是"快捷" TabPanel → `ActionPanel(filteredActions)`。改为**常驻菜单**,**不随战斗变化**(战斗内外都在),最终布局拍死如下:

```
[背包(disabled)]  [技能]  [专精(disabled)]
[退出角色]                                    ← dev 显示(import.meta.env.DEV)
```

- `技能` → 打开模态技能书(§5.2)。战斗中也能开,但内容只读。
- `背包` / `专精` = disabled 占位(各自 feature 再接)。
- **退出角色** = 直接 emit `{ type:'logout' }`(**不**走 `filteredActions`),由 `import.meta.env.DEV` 门控:dev 构建显示、prod 隐藏。理由:实测 `filteredActions` 只承载 logout 且**战斗中为空** —— 当前根本退不出战斗(卡死根因);改为独立按钮 + 直接 emit,战斗中也能退,解决卡死。
- **取消** = 预留位,**本轮不渲染**(等有"主菜单"层级结构再定;现在没有可取消的上层)。
- **撤掉旧 `ActionPanel(filteredActions)`**:它只承载 logout,已被"退出角色"按钮取代,不丢功能。

---

## 6. 测试

### 后端(JUnit)
- `Skillbook` 新模型:创建角色产出 `{slots, known:[{base,node,equipped}]}`,known 不含通用技能,slots=6,starting_skills 全 equipped。
- equip/unequip(失败 = 静默拒绝,断言**状态不变**):
  - unequip 后再 equip 回来,equipped 切换正确。
  - 满格(equippedCount==slots)再 equip → known 不变。
  - 战斗中(挂 `combat:*` tag)equip/unequip → known 不变。
  - base 不在 known → known 不变。
- `ui.render_actions`(战斗):永远含 攻击/防御/逃跑;技能项仅出 equipped 的;unequip 某技能后它从指令消失。指令列表里**无** move/item(前端占位)。
- snapshot `skillbook` 块:字段齐全(name/description/icon/equipped/node),仅玩家有。

### golden
- 本仓 golden = 技能保真(SkillFidelity,战斗结算/动画),**不含指令列表** —— 指令栏改动通常不触发 golden 重生。跑全量 `mvn test`;若意外有 golden 因指令变化而红,确认是有意变更后重生。

### 前端(手动 / tester 走查)
- PM 流程:进游戏 → 右下角点「技能」开模态 → 主动 tab 看到技能 + 出战 N/6 → 移除一个、再出战 → 满格时出战禁用 → 进战斗 → 指令栏 6 格正确、点「技能」出子菜单 + 取消键到位 → 移动/道具 置灰 → 战斗中开技能书为只读 → 战斗中点「退出角色」能退出(不卡死)。
- **后端守闸单独验**:战斗中直接打 `skillbook_equip`/`skillbook_unequip` API(绕过 UI)→ 再取 snapshot,`known` 不变(证明不是只靠 UI 藏按钮)。

---

## 7. 文件清单(预估,plan 阶段细化)

**后端 / mod:**
- `mods/base-rules/handlers/character/select.js` —— `Skillbook` 新模型构建(`newComponent("Skillbook")`)。
- `mods/base-rules/handlers/ui/actions.js` —— 战斗指令改写(通用指令 + 出战技能;读 `Skillbook.known`)。
- `mods/base-rules/handlers/ui/skillbook.js`(新)—— `ui.render_skillbook` 填 skillbook 块。
- `mods/base-rules/handlers/skill/03_skillbook.js`(新)—— `skillbook_equip` / `skillbook_unequip` handler(静默拒绝)。
- `backend/.../snapshot/WorldSnapshot.java` —— **新增 record** `SkillEntry(base,name,description,icon,equipped,node)` + `Skillbook(slots,equippedCount,known)`;`WorldSnapshot` record 加 `skillbook` 字段 + `inGame(...)` 形参。
- `backend/.../snapshot/SnapshotService.java` —— `buildSkillbook(playerId)`:fire `ui.render_skillbook`,组装传入 `inGame`。
- `backend/.../script/ScriptRuntime.java` —— 加 `newSkillEntry(...)` 工厂(暴露给 JS)。
- class schema(可选)—— `skill_slots` 字段口子,这轮不配值。

**前端:**
- `components/Modal.vue`(新,通用外壳)+ `composables/useModal.js`(新)。
- `components/SkillbookPanel.vue`(新,技能书内容,读 `s.description`)。
- `components/combat/BattleGrid.vue` —— command-row 重排。
- `components/SnapshotRenderer.vue` —— panel-nav 改常驻菜单(+ dev 门控退出角色)+ 挂模态;撤旧 `ActionPanel(filteredActions)`。

---

## 8. 与架构决策的一致性自查

- §1.4 技能实例模型 `{base,node,equipped}` + slots —— 本轮落地,`node` 预留。✓
- §1.5 6 出战格、职业可改、被动不占格 —— slots 默认 6 + schema 口子;被动这轮不碰(占位 tab)。✓
- §1.2 技能 spec ModifierChain —— `node` 字段是这条链的第一个挂点预留,本轮不实现变换。✓
- 母文档"Skillbook ≠ Skilltree" —— 本轮只做 Skillbook(带哪几个);Skilltree(加点/进化)留 #7,以 `→树` 接缝预留入口。✓
- UI hub 长期化 —— 模态外壳做成可复用,技能书首个租户,背包/专精/专业后续插 tab/按钮。✓
