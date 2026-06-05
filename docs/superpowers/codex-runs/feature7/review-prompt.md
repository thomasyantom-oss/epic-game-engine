role: 你是这个仓库的 Senior SDE,被要求对一份**设计 spec(尚未实现)**做架构 review。不要写任何代码、不要改任何文件——只输出一份结构化评审。

## 要 review 的文档
`docs/superpowers/specs/2026-06-04-feature7-skill-tree-design.md`(Chapter 1 Feature #7 专精天赋树,旗舰收官)

## 背景母文档(架构决策来源;注意:本 spec §1 有意覆盖母文档 §1.6/§1.10,以本 spec 为准)
`docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md`(§1.2 技能 spec ModifierChain、§1.5 被动、§1.6/§1.10 被本 spec 改向)

## 必须核对的现有代码(别只信 spec 的描述,打开读了再判断声称是否成立)
- `mods/base-rules/handlers/skill/00_skill_lib.js` —— `resolveSpec` 管道、`applyNode`(当前 no-op,#7 要填)、`ownedPassives`(读 `Skillbook.known`)、`applyPassivePatches`、`applySpecializationPatches`、`_applyPatch`/`_matchSkill`
- `mods/base-rules/handlers/skill/04_passive_lib.js`(或 passives 相关 lib)—— `Passive.*`:被动如何挂 `Skillbook.known`、`registerStatMods`、kind 守卫;**重点:被动来源是否只认 known,能否注入 `source:talent` 的"隐式被动"(不进 known/不在技能书露出)而仍走同一执行管道**
- `mods/base-rules/passives/*.js` —— handler 被动(bespoke JS)如何挂战斗事件(#7 的特殊效果天赋节点要走这条)
- `mods/base-rules/handlers/character/recalculate_hooks.js` —— `entity.loaded` 复位+重注册;被动 stat_mod 何时注册;#6 追溯重推走的同一条路
- `mods/base-rules/specializations/mage.yaml` + 读它的 `Specialization` 组件/handler —— `Specialization.path` 形状、`specNode`、grant_passives;#7 门控与"二级专精覆写追溯重推"要复用 #6 同款
- `mods/base-rules/handlers/character/leveling.js` + `debug` 端点 —— 等级来源、`debug.set_level`;#7 天赋点要从等级派生(Lv.10 起每 2 级+1)
- `frontend/src/components/Modal.vue` / `SkillbookPanel.vue` / 快照构建 —— 模态外壳复用、`树` 入口占位、快照如何加 `talentTree` 块

## 请按这些维度逐项给 verdict(每项:成立/有风险/错误 + 依据 + 修改建议)
1. **§4 "天赋节点效果走 #5 被动" 是否真成立**:spec 要把天赋点亮 = 授予一个 `source:talent` 隐式被动(不进技能书列表/不占被动栏),但仍走 #5 的 stat_mod/skill_patch/handler 三通路执行。现有 `ownedPassives` 只从 `Skillbook.known` 取——要支持"隐式被动注入"是否有干净接缝?还是 spec 低估了这里要改的量(known 之外再开一个被动来源)?有没有更省的做法?
2. **§4.1 追溯重推**:spec 称复用 #6"整表替换/从干净 base 重推"模式,让二级专精改写**已点节点**的授予被动(allocation 不变、被动换掉)。结合 recalculate_hooks 实际逻辑,stat_mod 类被动重推是否真自洽?skill_patch / handler 类被动随专精切换"摘旧挂新"有没有残留/幂等坑?
3. **§3.3 三层可逆性解耦**:天赋点可洗 / `known[i].node` 进化永久 / 专精不可洗。洗点清 `unlocked` 但**不动** `known[i].node`,这个解耦在数据/快照/重算上是否干净?"技能槽节点天赋点可洗、但已插球的进化永不回退"有没有自相矛盾的边界(如洗掉技能槽点位后该技能是否还算"在树里")?
4. **`applyNode` 落地**:#7 在此读 `known[i].node`→套进化内联 patch(方案1,复用 `_applyPatch`)。进化 patch 挂在 `talents/<spec>.yaml` 的 skill_slot 节点上——`resolveSpec` 调 applyNode 的时机、与 passive/spec patch 的叠加顺序有没有坑?
5. **天赋点来源**:Lv.10 起每 2 级+1,从等级派生。是存成 `TalentTree.points` 快照真相源,还是每次从 level 重算?与 `debug.set_level` 跳级、洗点退点如何不打架(已花点 vs 总点)?spec 这块够不够清楚,会不会实现时歧义?
6. **门控 + 持久化**:`TalentTree{root,points,unlocked}` 当真相源 + load 重建,与现有 persistence(persistent tag 自动存)吻合吗?未专精→树不渲染 + 后端拒 unlock,校验链够不够?
7. **快照/前端契约**:§8 的 `talentTree` 块(节点+连边+5 种态、双环选中、土星环、说明面板数据、分型球库存)够不够前端渲染一棵多叉树 + 说明面板?有没有漏的态。
8. **与母文档/既有约定冲突**:有没有违反 §1.2(结构性改写仍走 bespoke JS、不进 patch 管道)、§1.7 专精不可洗、§1.8 等级=尺子;或破坏现有 modifier/快照/战斗回归的隐患?
9. **范围**:作为单个 feature(两货币+多叉树+追溯重推+全新树前端+demo 树)是否过大需再切片?还是一轮 Codex 实现可吞下?spec §9 的 demo 范围够不够"覆盖全机器特性又不溢出"?

最后给总评 verdict:APPROVE / APPROVE-with-nits / REQUEST-CHANGES,并列出"实现前必须先解决"的阻塞项(若有)。
