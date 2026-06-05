**Findings**

- **[阻塞] Plan 测试片段会直接编译/运行失败。**  
  [ScriptRuntime.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:103) 暴露的是 `engine.createEntity(id)`，没有 `engine.newEntity`；但 plan 多处使用 `engine.newEntity`（Task1 Step3b、Task2 `makeHero`、Task3 `makeHeroWithSkill`、Task5 probe）。统一改为 `engine.createEntity`。  
  另外 [ScriptRuntime.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:68) 没有 `runtime.evalInt(...)`，Task1 Step3 的版本应从 plan 删除，避免实现者误抄。  
  `skillLevelOf()` 里 `store.get(...).getComponent("Skillbook").get("known").get(0).getInt("level")` 也不成立：Java 侧 `known` 是 `List<Map<String,Object>>`，Map 没有 `getInt`。修正为显式 cast：
  ```java
  @SuppressWarnings("unchecked")
  long skillLevelOf(String id) {
      List<Map<String, Object>> known =
          store.get(id).getComponent("Skillbook").get("known");
      return ((Number) known.get(0).get("level")).longValue();
  }
  ```

- **[阻塞] Task4 `debug.set_level` 的时序会让被动等级仍按旧 level 生效。**  
  当前 [leveling.js](C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:39) 的 `debug.set_level` 先调 `applySpec`；而 [specialization.js](C:/workplace/epic/mods/base-rules/handlers/character/specialization.js:190) 的 `applySpec` 内部会 `Passive.registerStatMods`。被动注册在 [04_passive_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:135) 把 `level` 乘进 `deltas` 闭包，之后单纯 `engine.recalculate` 不会重建这些 modifier。  
  修正：在 `debug.set_level` 写完 `Experience/Character.level` 后，先 `applySkillLevelCurve(entityId)`，再调用 `applySpec(entityId)`；如果走 fallback 分支，则曲线后再 `registerLevelGrowthModifier` / `Passive.registerStatMods` / `engine.recalculate`。

- **[阻塞] Task6 会打挂既有回归测试，plan 没把测试更新纳入边界。**  
  删掉 [elementalist.yaml](C:/workplace/epic/mods/base-rules/talents/elementalist.yaml:19) 的 `count: 2` 后，火球槽成本从 2 变 1。现有 [TalentTreeTest.java](C:/workplace/epic/backend/src/test/java/com/epic/engine/character/TalentTreeTest.java:350) 断言 `orbLeft == 0`，会变成剩 1。  
  [TalentTreeSnapshotTest.java](C:/workplace/epic/backend/src/test/java/com/epic/engine/snapshot/TalentTreeSnapshotTest.java:123) 也断言 UI `orbCount == 2`，改默认后应为 1。需要把这些既有测试加入修改边界，或调整测试输入为只给 1 颗球。

- **[建议] TDD “先失败”有假绿点。**  
  Task3 的 `curve_smoothSkill_level1AtStart` 在 no-op 现状下也会过，因为初始 level 就是 1。Task5 的 `fireball_tierStandard_maxesAt50` 也可能假绿，因为无 `tier` 时 fallback `smooth` 在 50 级同样满级。建议加一个能区分 tier 的断言，例如用 `Skill._specs['probe_chunky'] = { id:'probe_chunky', tier:'chunky' }` 后断言 50 级为 1，或 `standard` 在 20/47 的边界值。

- **[建议] `Progression.resolveCurve` 少了 spec 要求的 unknown tier warn。**  
  spec 明确说未知 tier fallback default 并 warn；plan 代码只 fallback。建议补一条轻量日志/事件，至少不要静默吞坏 YAML。

- **[建议] stub 隔离说明需要更精确。**  
  `registerLevelGrowthModifier = function(){}` 能覆盖全局函数声明，测试可行。`if (typeof persistence === 'undefined' ... ) { var persistence = ... }` 只在未绑定时创建 stub；如果测试/运行时已有 Java 注入的非空 `persistence`，它不会覆盖。当前新测试未 bind persistence，所以可行，但 plan 里“覆盖 Java 注入 binding”的说法不要写死；需要强隔离时直接赋值并验证不会影响其他测试 runtime。

- **[疑问] `Progression.cfg()` 的 Skill fallback 是否要保留。**  
  `progression_lib.js` 会按文件名在 `handlers/character/leveling.js` 后、`handlers/skill/00_skill_lib.js` 前加载，但 `cfg()` 通常到事件触发时才调用，届时 `Skill` 已存在。若要支持极早调用，建议不要依赖 Java Map 的属性访问 fallback，直接写一个本地 `_toJs` 或要求测试/生产先加载 `00_skill_lib.js` 后再调用。

**总评:** 这个 plan 还不能进 `codex exec`。先修正测试 API、`debug.set_level` 被动时序、以及 Task6 对既有测试的回归边界后，再派实现。