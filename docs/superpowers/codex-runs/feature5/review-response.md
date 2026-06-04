**阻塞问题**

- 问题：plan 里的 `rt.eval("...")` 不存在，相关测试无法编译。
  涉及：[ScriptRuntime.java](</C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:68>)
  具体改法：`ScriptRuntime` 只暴露 `execute(String script, String sourceName)`。测试要么改成执行 JS 后通过 Java 检查 `store`/事件结果，要么本 feature 明确新增一个测试/生产可用的 `eval` API。最小写法例如：
  ```java
  rt.execute("""
      var e = engine.createEntity("probe");
      var c = engine.newComponent("Probe");
      c.set("ok", engine.loadYaml("passives/iron_skin.yaml") !== null);
      e.addComponent(c);
      store.add(e);
      """, "probe.js");
  assertThat(store.get("probe").getComponent("Probe").get("ok")).isEqualTo(true);
  ```

- 问题：`resolveSpec` 只接入 `02_dispatch.js` 不是真“统一管道”。bespoke 技能绕过 dispatch。
  涉及：[02_dispatch.js](</C:/workplace/epic/mods/base-rules/handlers/skill/02_dispatch.js:10>)、`mods/base-rules/skills/cleave.js`、`cross_blast.js`、`piercing_ray.js`
  具体改法：在这些 bespoke skill 中把 `var spec = Skill._toJs(rawSpec);` 改为 `spec = Skill.resolveSpec(ctx, "<skillId>", Skill._toJs(rawSpec));`，或在 plan 明确 `skill_patch` 本轮只覆盖 data-driven effect。否则被动 patch 对 `cleave/cross_blast/piercing_ray` 无效。

- 问题：属性被动测试如果沿 plan 的 `new ScriptRuntime(bus, store)` 装配，`engine.addModifier/removeModifier/recalculate/setBase` 都会 no-op，因为没有传 `ModifierChainService`。
  涉及：[ScriptRuntime.java](</C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:224>)、[ModifierChainService.java](</C:/workplace/epic/backend/src/main/java/com/epic/engine/core/ModifierChainService.java:21>)
  具体改法：`PassiveStatModTest` 按 `DerivedStatsTest` 装配：
  ```java
  typeReg = new ModifierTypeRegistry();
  typeReg.loadFromModPath(Path.of("../mods/base-rules"));
  chainService = new ModifierChainService(bus, store, typeReg);
  rt = new ScriptRuntime(bus, store, chainService, typeReg);
  ```
  `engine.recalculate` 真名就是 `recalculate(String entityId)`，存在；`setBase/addModifier/removeModifier` 签名也与 plan 一致。

- 问题：Task 11 写“character level_up / ready”不准确。
  涉及：[leveling.js](</C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:29>)、[recalculate_hooks.js](</C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:17>)
  具体改法：升级入口是 `action.gain_xp`，升级后 fire `entity.level_up`；重载入口是 `entity.loaded`。hook 应定义为 `applySkillLevelCurve(entityId)`，在 `entity.level_up` handler 或 `action.gain_xp` 升级分支调用，并在 `entity.loaded` 中重放一次。新建角色可在 `select.js` 创建 Skillbook 后调用。

**应修问题**

- 问题：`combat.unit_death` 击杀者字段写法需要收窄。
  涉及：[death_check.js](</C:/workplace/epic/mods/base-rules/handlers/combat/death_check.js:1>)、[combat_events.js](</C:/workplace/epic/mods/base-rules/handlers/combat/combat_events.js:145>)
  具体改法：`combat.damage_dealt` 有 `attackerId`；`death_check.js` 转成 `entity.hp_zero.killerId`，再 fire `combat.unit_death`，字段为 `deadId/killerId/combatId`。`combat.unit_death` 本身没有 `attackerId`。`lifesteal_on_kill.js` 应读 `event.get("killerId")`；fallback 到 `attackerId` 可以留作兼容，但不要在 plan 中声称实际存在。

- 问题：`SkillbookActionsTest` 结构不能只写“按 helper 补全”。
  涉及：[SkillbookActionsTest.java](</C:/workplace/epic/backend/src/test/java/com/epic/engine/ui/SkillbookActionsTest.java:29>)
  具体改法：最小骨架是在现有 `setUp` 手工 `Skillbook.known` 里加被动：
  ```java
  skillbook.set("known", new ArrayList<>(List.of(
      skill("fireball", true),
      skill("light_field", true),
      skill("ice_beam", false),
      skill("iron_skin", false)
  )));
  ```
  然后：
  ```java
  @Test
  void passive_doesNotRenderAsCombatAction_evenIfKnown() {
      store.get("player1").addTag("combat:c1");
      GameEvent event = new GameEvent("ui.render_actions");
      event.set("entityId", "player1");
      event.set("actions", new ArrayList<WorldSnapshot.ActionOption>());
      bus.fire("ui.render_actions", event);
      List<WorldSnapshot.ActionOption> actions = event.get("actions");
      assertThat(actions.stream().map(a -> String.valueOf(a.params().get("command"))))
          .doesNotContain("iron_skin");
  }
  ```
  如果 `03_skillbook.js` 要按 kind 拒绝 equip，被测装配还需加载能判别 kind 的 helper，或直接在 handler 内 `engine.loadYaml("skills/...")` / `passives/...` 判别。

- 问题：`actions.js` 只靠 `equipped === true` 过滤不够稳。
  涉及：[actions.js](</C:/workplace/epic/mods/base-rules/handlers/ui/actions.js:72>)
  具体改法：生成 command 前显式确认 `skills/<base>.yaml` 存在且 `kind != passive`。否则 debug 或坏数据把被动 `equipped:true` 写进 known 时仍可能进指令栏。

- 问题：`03_skillbook.js` 当前会把任何 known 条目 equip/unequip，包括未来被动。
  涉及：[03_skillbook.js](</C:/workplace/epic/mods/base-rules/handlers/skill/03_skillbook.js:29>)
  具体改法：找到 target 后加载 spec kind；如果不是 active，直接 return。`equippedCount` 也应只数 active equipped。

- 问题：`passives/*.js` 加载顺序总体可行，但 plan 需要写清约束。
  涉及：[ModuleLoader.java](</C:/workplace/epic/backend/src/main/java/com/epic/engine/module/ModuleLoader.java:42>)
  具体改法：`SCRIPT_DIRS = {"handlers", "skills", "buffs", "passives"}` 时，`handlers/skill/00_skill_lib.js` 和未来 `04_passive_lib.js` 会先于 `passives/*.js` 执行，所以 `Skill`/`Passive` 已定义。风险是 `passives` 目录同时放 yaml 和 js，loader 只扫 `.js` 没问题；但 handler JS 不能依赖尚未加载的其他 passive JS。

- 问题：Task 8 的 `Passive.owns` 只看 base，不校验 spec kind/effect。
  涉及：计划新增 `04_passive_lib.js`
  具体改法：至少在 `owns(entityId, passiveId)` 中确认 `Skill.loadSpecAny(passiveId)` 的 `kind === "passive"`，避免主动技能同名/坏数据误触发。spec 已要求 id 全局唯一，但防御性校验成本很低。

**建议**

- 行号锚点核对：
  `WorldSnapshot.java:61` 准确；`ScriptRuntime.java:161` 准确；`SnapshotService.java:116` 准确；`select.js:108-126` 准确；`actions.js:73-77` 基本准确，但完整修改范围应是 72-79；`ModuleLoader.java:46` 不准，实际 `SCRIPT_DIRS` 在 42 行。

- `passive` typeId `base_priority=70` 可工作：当前链是 buff 10、equipment 50、passive 70、level 90、class 180、derived 300，升序应用，70 确保早于 derived。现有 class schema 都是 `+N`，不会覆盖 passive；但如果未来 class/race 出现 set 语义，70 可能被后续 set 覆盖。更稳的规则是“属性被动在所有基础属性来源之后、derived 之前”，例如 250，但这会改变它和 buff/equipment/level 的相对设计含义，需要产品确认。

- `JSON.parse(JSON.stringify(baseSpec))` 对当前 `_toJs` 后的 plain object 可用，能避免污染 `_specs` 缓存。回归安全主要漏洞不是 clone，而是覆盖面：`via_damage_calc` 路径如果 spec 走 dispatch 会用 resolved spec；bespoke JS 不走 dispatch，因此会漏。

- 统一 known 列表会影响的实际点除 plan 已列外，测试里还有 `CharacterFlowTest` 直接断言 known 全部 equipped true；加 starting_passives 后要改断言只检查 active，或按 base 分支断言 passive 没有 equipped/忽略 equipped。

- TDD 切分还有跨 task 依赖：Task 5 的被动进入 snapshot 要等 Task 9 才能完整绿；Task 7 属性被动依赖 Task 1 的 `passive` typeId、Task 2/3 的 `Skill.loadSpecAny`；Task 11 的测试如果只加载 `00_skill_lib.js` 不会看到 hook，必须加载定义 hook 的 character 文件。建议在 plan 中把这些标成“预期暂红直到 X”。

结论：plan 改完上述阻塞和应修项后，可以交付实现；当前版本直接执行会在测试 API、属性 modifier 装配、bespoke 技能覆盖和升级入口上卡住。