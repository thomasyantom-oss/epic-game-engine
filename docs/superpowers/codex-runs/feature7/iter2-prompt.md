role: 你是这个仓库的实现 SDE(headless)。Feature #7 已实现并通过测试,现在按真人 verify 反馈做**迭代 2** 的三处行为修改(B/C/D)。视觉项 A 已由他人改完,**不要动 `TalentTreePanel.vue` 的 CSS / 节点染色 / 图标**。

## 权威依据
`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md` **§12 迭代 2 修订**(B/C/D 的确切要求)。冲突以 §12 为准;§12 覆盖前文冲突处(尤其 §3.3 第 2 层、§11 验收 5/6/8、§4)。
现有实现:`mods/base-rules/handlers/character/talent.js`、`handlers/skill/{00,04}_*.js`、`handlers/ui/{talent_tree,skillbook}.js`、`talents/elementalist.yaml`、`frontend/src/components/{TalentTreePanel,SnapshotRenderer,SkillbookPanel,SpecializationPanel}.vue`、`WorldSnapshot.java` 等。

## 本轮范围:B / C / D 三项(TDD,先写失败测试)

### B. 球/进化与点位解耦(后端)
当前:`place_orb` 写 `known.node`=进化且永久;`applyNode` 总应用;`respec` 保留 `known.node`。**改为**:
- 新增永久记录 `TalentTree.evolved`(已花球的进化 id 集合,list)。
- `place_orb`:扣球 + 把 evolution 加进 `evolved` + 写 `known.node = evolution`(当前激活)。
- `talent_respec`:清 `unlocked` 之外,**把所有技能槽对应 skill 的 `known.node` 置 null**(进化效果不再生效,技能回原始形态);**保留 `evolved`**。
- `talent_unlock` 命中技能槽节点时:若该槽 `evolution ∈ evolved` → **直接写 `known.node = evolution`(免费恢复,不需要球)**;否则保持 slot-empty(待插球)。
- `applyNode` 不改(仍读 `known.node`,它现在=当前激活进化)。
- 快照态:槽点亮+`known.node`set→`slot-filled`;槽点亮+未花球→`slot-empty`;**槽未点亮+evolution∈evolved→`filled-node-relocked`**;槽未点亮+从未花球→`locked`/`unlockable`。`nodeState` 据此调整(用 `evolved` 判 relocked,而非只看 `known.node`)。
- 验收测试(`TalentTreeTest`):插球→`known.node=pyroblast`+`evolved`含它;**洗点→`known.node=null`但`evolved`仍含**;重点槽节点→`known.node` 免费恢复=pyroblast、未消耗球;实战 `resolveSpec` 在洗点后火球=原始(单体),重点后=炎爆(AOE)。

### C. 专精 + 天赋合并为单一面板(前端为主)
- **撤掉单独的"专精"入口**,只留一个入口(天赋/专精合一)。打开统一面板逻辑:`snapshot.specialization.pending != null` → 渲染**专精选择**(复用 #6 `SpecializationPanel` 的三选一/二选一卡片 + `choose_specialization` action),**自动呈现**(一打开就弹);否则 `snapshot.talentTree` 存在 → 渲染天赋树(`TalentTreePanel`);都没有(path 空且无 pending)→ "专精后解锁"。
- L50 二级专精:因 `pending` 在到级后自动出现,"一打开就弹"自然满足;选完确定后回到天赋树(此时含二级专精扩展节点)。
- 不改 `TalentTreePanel.vue` 的树渲染/CSS(A 已定);本项只动 `SnapshotRenderer.vue`(入口合并 + 条件渲染)、必要时 `SkillbookPanel.vue` 入口、和把 `SpecializationPanel` 当子步骤嵌入。保持 `Modal.vue` 外壳、边框≥2px、战斗只读。

### D. 隐式 vs 显示被动(后端快照 + 前端列表)
- **隐式被动**(`effect == stat_mod`,只加固定属性):维持现状,折进属性,**不进被动列表**。
- **显示被动**(`effect ∈ {skill_patch, handler}`,需计算/特殊效果/吃加成):**作为只读条目出现在被动列表**(被动 tab),来源标 `talent`、不可卸载/出战(沿用被动只读 UI)。
- 落法:被动列表快照(`ui.render_skillbook` 的 passive 构造)追加 talent 的显示类派生被动(从 `Talent.derivedPassives` 取 `effect ∈ {skill_patch,handler}`),给只读标记;`stat_mod` 类不追加。前端被动 tab 照常渲染这些只读条目(标注来源/不可操作)。
- 验收测试:点亮一个 `skill_patch` 天赋节点 + 一个 `handler` 天赋节点 → 被动列表快照含这两条(只读);点亮一个 `stat_mod` 节点 → 被动列表**不含**它,但属性已加。

## 工作方式 / 约束
- 工作目录 `/c/workplace/epic`,沙箱 workspace-write。TDD:先失败测试→实现→通过。
- 验证:`cd backend && mvn test` 全绿;前端**仅编译校验**(`npm run build` 在本沙箱会 `spawn EPERM`,跑不了——用 `@vue/compiler-sfc` 解析校验改动的 .vue,或确保模板语法正确即可;构建由 Claude 在沙箱外验)。
- **别动 `TalentTreePanel.vue` 的 `<style>` 与节点/连线/图标渲染**(A 已完成)。别碰 `ModifierChain.java`、golden、sim、战斗管线。
- **不要自己 commit / git add**。偏离 §12 的结构性决定在回话标注。

## 回话产出
1. B/C/D 各自改动文件全路径 + 完成情况。
2. `mvn test` 结果(全绿?新增/改动测试)。
3. 前端改动的编译校验方式与结果。
4. 偏离 §12 的决定 + 理由。
5. 自检:洗点后火球回原始 / 重点免费恢复炎爆 / 单一入口自动弹专精 / 显示被动进列表而隐式不进。

开始 B。
