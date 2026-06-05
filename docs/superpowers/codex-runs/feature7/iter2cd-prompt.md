role: 你是这个仓库的实现 SDE(headless)。Feature #7 迭代 2 的 **B(球解耦)已完成并测试通过**。本轮只做剩下的 **C 和 D**。

## 权威依据
`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md` **§12**(C、D 的确切要求)。
现有相关文件:`frontend/src/components/{SnapshotRenderer,SkillbookPanel,SpecializationPanel,TalentTreePanel,Modal}.vue`、`mods/base-rules/handlers/ui/skillbook.js`、`mods/base-rules/handlers/character/talent.js`(`Talent.derivedPassives` 已存在,返回扁平被动项,每项带 `effect`)。

## C. 专精 + 天赋合并为单一面板(前端)
- **撤掉单独的"专精"入口按钮**,只保留一个入口(天赋/专精合一;技能书"树"入口或右下角菜单一处即可)。
- 统一面板渲染逻辑:
  - `snapshot.specialization && snapshot.specialization.pending` 非空 → 渲染**专精选择**(复用 `SpecializationPanel` 的三选一/二选一卡片 + `choose_specialization` action),**打开即呈现**(一打开就弹,无需额外点击)。
  - 否则 `snapshot.talentTree` 非空 → 渲染 `TalentTreePanel`(天赋树)。
  - 否则(path 空且无 pending,如未到 L10)→ 显示"专精后解锁"。
- L50 二级专精:`pending` 到级自动出现 → 打开面板即弹二选一;确定后回天赋树(此时含二级专精扩展节点,数据已支持)。
- **不要改 `TalentTreePanel.vue` 的 `<style>` / 树节点 / 连线 / 图标**(已定稿)。保持 `Modal.vue` 外壳、边框 ≥2px、战斗中只读。
- 主要改 `SnapshotRenderer.vue`(入口合并 + 条件渲染选择 vs 树),必要时 `SkillbookPanel.vue` 入口。`SpecializationPanel` 当子步骤复用,不必重写其卡片逻辑。

## D. 隐式 vs 显示被动(后端快照 + 前端列表)
- **隐式被动**(`effect == "stat_mod"`,只加固定属性):维持现状——折进属性、**不进被动列表**。
- **显示被动**(`effect ∈ {"skill_patch","handler"}`,需计算/特殊效果/吃加成):**作为只读条目出现在被动列表(被动 tab)**,来源标 talent、不可出战/卸载。
- 落法:被动列表快照构造处(`ui.render_skillbook` 里组 passive 条目的地方)**追加** `Talent.derivedPassives(entity)` 中 `effect ∈ {skill_patch,handler}` 的项,作为只读被动条目(给个 `source: "talent"` 或等价只读标记,名称可用节点/被动名);`stat_mod` 项不追加。前端被动 tab 照常渲染,这些 talent 来源的条目**不显示出战/移除按钮**(只读)。
- 注意:`derivedPassives` 已按 `requires_spec`/override 过滤,直接用其输出即可,保持与运行时一致(单一真相)。

## 工作方式 / 约束
- 工作目录 `/c/workplace/epic`,沙箱 workspace-write。TDD(D 有后端可测点:点亮 skill_patch + handler 节点 → 被动列表快照含这两条只读;点亮 stat_mod 节点 → 列表不含但属性已加)。
- 验证:`cd backend && mvn test` 全绿。前端**仅编译校验**(本沙箱 `npm run build` 会 `spawn EPERM`,跑不了——用 `@vue/compiler-sfc` 解析改动的 .vue 或确保模板语法正确;构建由 Claude 沙箱外验)。
- 别动 `TalentTreePanel.vue` 的样式/渲染;别碰 `ModifierChain.java`、golden、sim、战斗管线、B 已完成的 talent.js 球逻辑。
- **不要自己 commit / git add**。偏离 §12 在回话标注。

## 回话产出
1. C、D 各自改动文件全路径 + 完成情况。
2. `mvn test` 结果。
3. 前端改动的编译校验方式与结果。
4. 自检:单一入口、打开自动弹专精(有 pending 时)、确定后变树;显示被动进列表只读、隐式被动不进。

开始 C。
