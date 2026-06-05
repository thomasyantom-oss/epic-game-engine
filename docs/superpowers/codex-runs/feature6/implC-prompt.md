role: 实现层 SDE。Feature #6 专精的核心(A 后端机制 + B 快照/前端面板)已在工作树里、189 测试通过。本轮是 PM verify 后的 **UI 传播 + 打磨**一批改动。不要 commit、不要碰 git。

## 权威背景(先读)
- spec：`docs/superpowers/specs/2026-06-04-feature6-specialization-design.md`
- 已实现：`Specialization{path}` 组件、`applySpec`/`effectiveGrowth`/`pathNodes`/`specNode`/`loadSpecTree`(`mods/base-rules/handlers/character/specialization.js`)、快照 `specialization` 块、`SpecializationPanel.vue`、`debug.set_level`。

## PM 决策(本轮按这个做,别自由发挥)
1. **怪物战斗显示种族 + 等级**:怪物**全预设 Lv.1**;"种族"= encounter 数据里可选 `race` 字段,**缺省回落怪物 name**。(完整怪物种族系统是后续 chapter,本轮只是占位字段。)
2. **职业/专精颜色暂不做**:职业名颜色**用现有边色**(玩家=player 色、敌人=enemy 色),不引入 per-class/专精色。
3. **选人界面属性显示该角色实时属性**(已反映专精成长),不是职业模板。

## 要做的 6 件(逐条验收)

### ① 选人界面反映专精(`CharacterSelect.vue` + 后端 CharacterInfo)
现状:详情区完全读 `previewFor(classId)`=基础职业模板,专精零反映。
- 后端给**每个角色**算出专精感知的有效数据,塞进 `WorldSnapshot.CharacterInfo`(改 record + `ScriptRuntime.newCharacterInfo` 桥 + `select.js` 的 `session.list_characters` 回填 + `SnapshotService.buildCharacterSelectSnapshot`):
  - `classLabel` → **有效职业名**(path 最深专精节点 `label`,回落 Character.classLabel,如"奥术法师")
  - 新增:`primaryStats`(该角色**持久化的实时 PrimaryStats** map —— 存档值已是专精后结果,直接读,不必重跑 modifier)、`growth`(`effectiveGrowth`)、`description`(最深专精节点 `description` 回落职业描述)、`portrait`(最深专精节点 `portrait` 回落职业 `portrait`)
- 前端 `CharacterSelect.vue`:详情区改用**角色自带的有效数据**(实时属性 / 有效成长 / 有效名 / 描述 / 立绘),不再 `previewFor(classId)`;卡片与详情头的 `classLabel` 用有效名。
- 后端加有效值 helper(放 `specialization.js`):`effectiveSpecNode(entity)`(最深节点或 null)、`effectiveClassLabel(entity)`、`effectiveDescription(entity)`、`effectivePortrait(entity)`。

### ② 人物属性 tab 职业名(`CharacterStatsTab.vue` 已显示 classBar.label)
- 改 `status_bars.js` 的 "class" 状态栏 → 用 `effectiveClassLabel(entity)`(而非存的 classLabel)。tab 自动跟着变,无需改 Vue。

### ③ 战斗状态栏显示 职业 + 等级(玩家)
- 玩家战斗状态栏(`StatusBars.vue`)格式:**名称 + 白色 `Lv.N` + 右对齐职业名(边色)**。参照 `CharacterStatsTab.vue` 现有的 `char-name`/`char-level`/`char-class` 写法对齐。职业名用有效专精名。

### ④ 战斗里怪物显示 种族 + 等级
- `CombatantInfo`(`WorldSnapshot` record + `start_combat.js` 回填 + 桥)新增 `level`(玩家=真等级;怪物=1)+ `typeLabel`(玩家=有效职业名;怪物=race 回落 name)。
- `start_combat.js`:spawn 怪物时,从 encounter 数据读可选 `race`,无则用 name;怪物 level 取 1(若怪物有 Experience/Character.level 则用之,否则 1)。
- `BattleGrid.vue`(战斗单位格):每个单位显示 **名称 + Lv.N + 种族/职业**(右对齐或紧凑排布,边色)。玩家与怪物都按此。
- demo:给 `entities/encounters/forest_goblin.yaml` 两个怪加 `race`(如"哥布林" / "哥布林"),让你能看到种族显示。

### ⑤ 打磨文案
- `SpecializationPanel.vue` 的 **"战斗中只读" → 红字 "战斗中"**(用 enemy/红色)。`SkillbookPanel.vue` **加同样的红字 "战斗中"** 指示(它现在只隐藏控件、没文字提示)。
- `SpecializationPanel.vue` 效果摘要:**删掉 "无即时效果"**,改成**始终列出该选项选定后的实际成长值**(用 option.effects.growth;若该选项没覆盖 growth,显示其继承的有效成长——可由后端在 effects.growth 里直接给"该选项生效后的最终成长 map",保证非空)。

### ⑥ 不可洗确认改游戏内 popup(`SpecializationPanel.vue`)
- **删掉 `window.confirm`**(破坏沉浸)。改成**游戏内 popup,直接复刻 `CharacterSelect.vue` 的删除确认**:`.confirm-overlay`(半透遮罩 absolute inset)+ `.confirm-dialog`(2px 边框)+ 确认/取消。文案点明"选定后不可更改"。粗边框 ≥2px,复用现有 CSS 变量/边色。

## 约束(5 件套)
- **修改边界**:上述文件 + 必要的桥/record/快照测试。别碰战斗流程/伤害/动画逻辑、ModifierChain.java、golden/SkillFidelityTest、sim 包、Phase A/B 已落的 spec modifier / applySpec / result 通路(复用,别重写)。
- **代码约束**:CharacterInfo/CombatantInfo 改 record 必须同步**所有**工厂/调用点(否则编译失败);桥工厂仿 `newSkillEntry`/`newSpecOption`;颜色用现有边色语义(player/enemy),别造新色键。
- **产品偏好**:粗边框 ≥2px;中文;不动 4 宫格布局;复用现有 CSS 变量/组件风格;战斗中只读项保持禁用。
- **验证**:`cd backend && mvn test` 全绿(更新/新增受影响快照测试:CharacterInfo 有效字段、CombatantInfo level/typeLabel、effectiveClassLabel);`cd frontend && npm run build` 通过(注:你沙箱里 vite 可能 `spawn EPERM`,那是沙箱不是代码问题——只要不是 Vue 编译错即可,我会自己复跑 build 确认)。

## 完成后(写进 -o 回话,别 commit)
- 改动文件清单(逐个)。
- `mvn test` 结果 + `npm run build` 结果(或注明 spawn EPERM)。
- 请重点 review:CharacterInfo/CombatantInfo record 调用点同步、effective* helper 回落正确、选人界面读实时属性、怪物 race 回落 name + Lv.1、文案/ popup 复刻删除确认、有效成长摘要非空。
- 任何就地偏离 + 理由。
