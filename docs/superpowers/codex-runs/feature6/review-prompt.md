role: 你是这个仓库的 Senior SDE,被要求对一份**设计 spec(尚未实现)**做架构 review。不要写任何代码、不要改任何文件——只输出一份结构化评审。

## 要 review 的文档
`docs/superpowers/specs/2026-06-04-feature6-specialization-design.md`(Chapter 1 Feature #6 专精)

## 背景母文档(架构决策来源,遵守即可,别重新推导)
`docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md` §1.7 / §1.8 / §1.10

## 必须核对的现有代码(别只信 spec 的描述,打开读了再判断声称是否成立)
- `backend/src/main/java/com/epic/engine/core/ModifierChain.java` —— 重点 `addModifier`(id 先删后加幂等)、`recalculate`(restoreBaseState 复位再叠)、`setBaseSelective`
- `mods/base-rules/handlers/character/recalculate_hooks.js` —— `entity.loaded` 复位+重注册全部 modifier 的既定路;`before/after_recalculate` 的 hp/mp scratch 钩子
- `mods/base-rules/handlers/character/leveling.js` —— `registerLevelGrowthModifier`(读 classSchema.growth × (level-1))
- `mods/base-rules/handlers/character/select.js` —— 建角装配顺序、weaponAttr 在 setBase 前写入
- `mods/base-rules/schemas/sub/class_warrior.schema.yaml` —— 现有 growth / weapon_attr / starting_skills 形状
- `mods/base-rules/handlers/skill/00_skill_lib.js` —— `lookupStat`(物理强度吃 weaponAttr 的查找)、resolveSpec 管道
- `mods/base-rules/handlers/skill/04_passive_lib.js` —— `Passive.registerStatMods`、被动如何挂 Skillbook.known

## 请按这些维度逐项给 verdict(每项:成立/有风险/错误 + 依据 + 修改建议)
1. **R 追溯成长是否真成立**:spec 声称"把 level_growth 改读 effectiveGrowth(职业 growth 被 path 最深 growth 节点整表替换),靠 restoreBaseState 从干净 base 重推 → 前 N 级自动追溯"。结合 ModifierChain.recalculate + registerLevelGrowthModifier 实际逻辑,这个"免迁移追溯"是否真的成立?有没有漏算的累加点 / 旧值残留 / 重启膨胀风险?
2. **modifier 生命周期 + 幂等**:`registerSpecModifier` 套 class modifier 那条路,load 期重注册。weaponAttr 覆写复用"load 期按 class 还原 weaponAttr 的同一位置"——装配顺序(class→level_growth(spec-aware)→equipment→spec→derived→passive)有没有 ordering 坑(尤其 weaponAttr 是 base 字段 vs modifier 输出、derived 300 读 weaponAttr 的时机)?
3. **#6↔#7 薄契约(选项 B)**:#6 只存 path、不做任何 gating,是否真的自洽、无悬空依赖?有没有 #6 现在就必须立的接缝被漏掉(否则 #7 落地要返工改 #6)?
4. **持久化**:`Specialization{path}` 当唯一真相源 + load 重建,与现有 persistence(persistent tag 自动存)是否吻合?path 是 List<String>,持久化/快照序列化有没有坑?
5. **快照/前端契约**:`specialization` 块(path/pending/locked 三态)+ `action.choose_specialization` 校验链,够不够前端渲染面板?有没有漏的状态(如未专精、已到最深层无 pending)?
6. **测试覆盖**:§7 的验收用例有没有缺口(尤其 load 往返、main_attr 合成节点、未专精回归)?
7. **与母文档/既有约定的冲突**:有没有违反 §1.7 不可洗 / §1.8 等级=尺子 / §1.10 不给独立天赋树,或破坏现有 modifier/快照/战斗回归的隐患?
8. **范围**:作为单个 feature 是否过大需再切片?还是一轮 Codex 实现可吞下?

最后给总评 verdict:APPROVE / APPROVE-with-nits / REQUEST-CHANGES,并列出"实现前必须先解决"的阻塞项(若有)。
