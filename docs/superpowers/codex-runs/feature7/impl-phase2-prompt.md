role: 你是这个仓库的实现 SDE(headless)。阶段 1(后端,A1–A9)已完成并通过 review + 全量 `mvn test` 绿。现在实现 **Feature #7 阶段 2(前端树体验 + demo 收尾)**。

## 唯一权威 plan(逐字遵守)
`docs/superpowers/plans/2026-06-04-feature7-skill-tree.md` —— 本轮做 **Task B1 → B3**。
配套 spec:`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md`(§2 树形/视觉/说明面板、§8 快照字段)。

## 本轮范围:仅 **阶段 2 = Task B1 → B3**
- B1:快照契约前端可消费性轻测(`TalentTreeSnapshotTest` 补 JSON 序列化断言)。
- B2:新建 `frontend/src/components/TalentTreePanel.vue`(多叉树画布 + 说明面板);改 `SnapshotRenderer.vue`(加 `talent` 模态租户 + 入口)、`SkillbookPanel.vue`(`树` 灰占位升级为真入口)。
- B3:核对 `talents/elementalist.yaml` demo 树覆盖全机器特性 + 全量验证。
- **不要改后端 Java / mods 后端逻辑**(阶段 1 已定稿);只动前端 + 必要的快照测试。若发现阶段 1 真 bug,**在回话标注、不要默默改**。

## 产品偏好(必须遵守,来自 plan ④ + 项目 memory)
- 复用 `Modal.vue` 外壳(继 #4/#5/#6)。**所有边框 ≥2px,禁 1px 细边。** 不动 4 宫格布局。战斗中(`readonly`)隐藏操作按钮。
- 树视觉:节点是**圆圈,不画 icon**(icon 只在选中弹出的说明面板出现)。颜色态——灰边=普通、黄边=技能槽空、**绿色"土星环"**(同色的环套圆、中间留空,非填充光晕)=已插球;**双环(外再套一圈)=当前选中节点**。布局**瘦高、从下往上、多叉**(用快照 `tier`/`order`/`parents` 排)。
- 说明面板布局自上而下:**icon 左上 / 节点名在 icon 右 / 技能说明 / "受影响"区(被动·主动增强·所需灵魂球,from `effectSummary`/`slot`)/ 底部操作按钮**。
- 按钮 emit action:`talent_unlock`(带 nodeId)、`talent_place_orb`(带 nodeId)、`talent_respec`。顶部显示 `points` 三元组(total/spent/available)+ `orbInventory` 分型球库存。
- 未专精(`talentTree==null`):入口提示"专精后解锁"或灰显。文案中文。

## 工作方式
- 工作目录 `/c/workplace/epic`,沙箱 workspace-write。
- 验证:`cd frontend && npm run build`(通过);`cd backend && mvn test`(B1 的快照测试 + 全量仍绿)。
- **不要自己 commit / git add / push**(Claude 负责)。
- 偏离 plan 结构性决定要在回话标注。

## 回话产出
1. B1–B3 完成情况 + 新增/修改文件全路径清单。
2. `npm run build` 结果 + `mvn test` 结果。
3. 偏离 plan 的决定 + 理由。
4. 前端态渲染自检:7 种节点态(尤其 slot-empty 黄/ slot-filled 绿土星环 / 选中双环 / hidden 不显)、未专精入口、说明面板布局、points/orb 展示。

开始 B1。
