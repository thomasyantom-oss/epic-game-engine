你是资深游戏引擎 SDE,做一次**plan review**(只评审实现 plan,不写实现)。

## 评审对象
`docs/superpowers/plans/2026-06-05-ch1-content-numeric-backbone.md`
配套 spec:`docs/superpowers/specs/2026-06-05-ch1-content-numeric-backbone-design.md`(已纳一轮 review)

这是单机回合制刷子养成游戏(Java 21 + Spring Boot + GraalJS,规则在 `mods/base-rules/` JS+YAML)的 Ch1 数值骨架实现 plan。本轮横切:只拍内容无关数值骨架,不填职业内容。

## 必读的现有代码(核对 plan 的代码片段/接缝/测试 idiom 是否与现状吻合)
- `mods/base-rules/handlers/character/leveling.js` —— `xpForLevel`/`gain_xp`/`debug.set_level`/`registerLevelGrowthModifier` 现状,核对 plan 的替换片段行号与逻辑
- `mods/base-rules/handlers/character/recalculate_hooks.js` —— `applySkillLevelCurve`(:123)与触发点(:99 loaded、:127 level_up)
- `mods/base-rules/handlers/skill/00_skill_lib.js` —— `Skill.loadSpecAny`/`_toJs`/`resolveSpec`/`applyLevelScaling`/`ownerInstance`/`_applyPatchValue`,核对 plan 用法
- `mods/base-rules/handlers/skill/04_passive_lib.js` —— 被动读 `known[i].level`(:38)
- `mods/base-rules/handlers/character/talent.js` —— `action.talent_place_orb`(:460)、orb count 读点(:387 summary、:484 扣除)
- `mods/base-rules/handlers/ui/talent_tree.js` —— orb count 快照(:53)
- `mods/base-rules/talents/elementalist.yaml` / `skills/fireball.yaml` —— demo 改动目标
- `backend/src/test/java/com/epic/engine/character/TalentTreeTest.java` —— 测试 idiom 范本(plan 的测试照它写)
- `backend/src/main/java/com/epic/engine/script/ScriptRuntime.java` —— 确认 plan 测试里用的 API 真实存在:`engine.newEntity`/`engine.newComponent`/`engine.newList`/`engine.newMap`/`store.add`/`store.remove`/`store.get`、Component `.getInt`/`.set`、以及 plan Task1 提到的可疑 `runtime.evalInt(...)` 到底存不存在(plan 已用 Probe 版兜底,确认兜底可行)
- `backend/src/main/java/com/epic/engine/core/EntityStore.java` / `Entity.java` / `Component.java` —— 构造实体/组件、List/Map 组件值的真实 API

## 重点核查
1. **测试可编译可跑:** plan 里的 Java 测试用到的 `engine.newEntity/newList/newMap`、`store.remove`、`Component.getInt` 读 Map value 等 API 是否真实存在?Probe idiom 是否与 `TalentTreeTest` 一致可行?哪些会编译失败/运行抛错,给出修正。
2. **JS 替换片段准确性:** 各 Task 给的「现为/改为」片段是否与现网代码逐字吻合(行号、变量名)?`gain_xp`/`debug.set_level`/`applySkillLevelCurve`/`talent.js` 的替换有没有漏改、改错、破坏其它分支。
3. **时序正确性:** Task4 在 `debug.set_level` 补 `applySkillLevelCurve`+`recalculate` 的位置是否会与内部 `applySpec`(含被动注册)冲突/重复/顺序错?被动吃 level 的时序约束有没有被满足。
4. **stub 隔离是否成立:** Task2/4 测试 stub `registerLevelGrowthModifier`/`persistence`/`applySpec` 的写法在 GraalJS 全局作用域里是否真能覆盖 handler 引用的同名全局?`persistence` 若是 Java 注入 binding,`var persistence={...}` 能否覆盖?
5. **Progression 懒加载:** `engine.loadYaml("progression.yaml")` 路径解析(module context)、`Skill._toJs` 未加载时的 fallback、热重载缓存清除,有没有坑。
6. **TDD「先失败」真成立:** 每个「确认失败」步骤,按现状真的会失败吗?有没有假绿(如 Task3 smooth fallback 导致断言在未改时也过)。plan 已自标 Task3 可独立过,核对其余。
7. **回归风险:** 改 `talent.js`/`talent_tree.js`/`leveling.js`/`fireball.yaml`/`elementalist.yaml` 会不会打挂现有 `TalentTreeTest`/`SpecializationTest`/战斗测试(如 fireball 加 level_scaling 后 caster 无 Skillbook 时 resolveSpec 行为)。
8. **遗漏:** 有没有 spec 要求但 plan 没覆盖的;有没有 plan 改了但漏改的同源读点。

## 输出格式
列 findings,每条标 **[阻塞]/[建议]/[疑问]**,引用具体文件:行 或 plan Task/Step,给修正建议。最后一句总评:plan 能否进实现(`codex exec`)。不要改任何文件,只输出评审。
