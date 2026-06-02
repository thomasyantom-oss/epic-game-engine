# Refactor V1 - Safe Skill/Equipment Authoring

本轮目标不是限制引擎扩展性，也不是设计新内容；目标是先建立一个小而明确的代码侧 authoring pattern，让未来技能、装备、buff、action 的生成更少复制、更容易验证、更不容易踩生命周期 bug。

## 保留前提

- 允许技能/装备保留自定义 JS 行为。复杂行为是项目核心能力，不能为了“全数据化”牺牲扩展性。
- `ScriptRuntime` 暴露较强 host 能力、事件名开放、handler 可自由注册，这些属于当前微内核设计的扩展性代价，本轮不收紧。
- 回合制 + 动画展示时间较长，buff 扫描和小地图寻路性能暂不作为 V1 优先项。
- V1 优先处理“不服务于扩展性、只是让未来内容更容易写错”的问题。

## 当前最佳模板

### 普通数据驱动技能

以 `fireball.yaml` / `poison_dart.yaml` 为模板：

- YAML 声明 `id/name/description/category/silenceable/effect/damage/targeting/debuff/log/animation`。
- `02_dispatch.js` 统一监听 `combat.unit_action`。
- `Skill.resolveTargets` 统一解析目标。
- `Skill.computeDamage` 统一计算伤害和 scaling。
- `01_effects.js` 统一处理伤害、buff、表现和死亡级联顺序。
- `Skill.present` 统一输出 combat event。

### 自定义技能

以 `piercing_ray.js` 的新顺序为模板，而不是 `cleave.js` / `cross_blast.js`：

- 仍从 YAML 读取 `damage` / `targeting` 等可共享数据。
- 仍调用 `Skill.context` / `Skill.resolveTargets` / `Skill.computeDamage`。
- 如果要自定义表现，允许 JS 自己 emit，但必须先 `Skill.applyDamage`，再 emit 技能表现，最后 `Skill.fireDamageDealt`，保证技能动画先于死亡事件。

### 装备

以 `items.yaml` + `equipment/equip.js` + `derived_stats.js` 为模板：

- 武器只声明 `weaponAttr/base`，最终武器伤害由 `derived_stats.js` 计算。
- 护甲/饰品使用全限定字段，如 `"CombatStats.defense": 5`。
- 装备槽是结构性状态，变更后必须 `engine.updateBase(entityId, "EquipmentSlots")`。

## V1 想做的改动

### 1. 增加内容验证测试

新增测试扫描现有 `mods/base-rules` 内容，不改变运行逻辑。

最低验证项：

- 每个 `skills/*.yaml` 必须有 `id/name/category/silenceable/targeting`。
- 有 `effect` 的技能，其 effect 必须在 `01_effects.js` 注册。
- 没有 `effect` 的技能，如果不是 `flee`，必须存在同名 `.js`，并显式标记为 bespoke。
- 数据驱动技能的 `animation` 必须是运行时可用字段；bespoke 技能的 decorative animation 必须有 NOTE，避免误以为 YAML 会生效。
- `damage.scaling` key 必须能命中允许的属性集合：`PrimaryStats` 五属性或 `DerivedStats` 三强度。
- `buff/debuff.id` 必须有对应 `buffs/<id>.js` 或 `<id>.yaml`。
- `items.yaml` 中非武器 stat key 必须是 `Component.field` 格式，值必须是数字。

价值：

- 不限制自定义 JS。
- 让生成内容在测试阶段失败，而不是打到战斗中才崩。
- 可以作为未来生成大量技能/装备前的安全网。

### 2. 修正 `EntityStore.clear()` 的 tag index 清理

当前 `EntityStore.clear()` 只清空 `entities`，没有清 `TagIndex`。测试 harness 已经用注释规避“不能复用 harness”，说明这是实际可见的问题。

建议：

- 给 `TagIndex` 增加 `clear()`。
- `EntityStore.clear()` 同时清空 `entities` 和 `tagIndex`。
- 加一个 `EntityStoreTest` 覆盖 clear 后 `getByTag` 为空。

价值：

- 小改动，行为更符合方法名。
- 避免测试和未来运行时工具产生幽灵 tag 引用。

### 3. 给 bespoke 技能抽最小 emit helper

不立刻把 `cleave/cross_blast/piercing_ray/heal/defend` 全部改成数据驱动。

建议先在 `00_skill_lib.js` 增加小 helper：

- `Skill.summaryLog(ctx, templateOrParts, amount, count)`
- `Skill.nestedHpEffect(target, amount)`
- `Skill.emitCombatQueueEvent(combatId, segments, effects, animation)`

然后只迁移 `cleave.js` 和 `cross_blast.js` 使用 helper，保持 golden 输出不变。

价值：

- 保留 bespoke 表现自由。
- 减少直接拼 `CombatEvents.queue` 的重复代码。
- 后续要统一事件形状时，有一个集中入口可改。

### 4. 补 `cleave/cross_blast` 死亡顺序测试

`piercing_ray.js` 已经修成“先技能动画，后死亡事件”。`cleave.js` / `cross_blast.js` 仍直接 `Skill.dealDamage`，可能让死亡事件排在技能事件前。

建议：

- 先加测试锁定当前问题或期望行为。
- 如果确认应统一为 piercing_ray 的顺序，再小改为 `applyDamage -> emit -> fireDamageDealt`。

价值：

- 这是生命周期 bug，不是扩展性自由。
- 测试先行，避免误改动画 golden。

### 5. 补空地 AoE 坐标契约测试

`Skill.resolveTargets` 对没有 `targetId` 的情况使用 `targetRow/targetCol`。这里表面看起来像行列反了，但和当前前端 `BattleGrid` 的坐标契约是匹配的：

- 前端 enemy grid 的 `cell.row` 对应后端 `CombatPosition.slot`。
- 前端 enemy grid 的 `cell.col` 对应后端 `CombatPosition.row` 转出的 `rowIdx`。
- 因此后端 fallback `{ slot: targetRow, rowIdx: targetCol }` 是当前正确行为。

建议：

- 加 `light_field` 空地施法测试，锁定 `targetRow/targetCol` 与 `slot/rowIdx` 的映射。
- 在 `resolveTargets` 或测试名里说明这个映射来自前端 grid 坐标，避免后续按直觉“修反”。

价值：

- 保护已实测可用的空地 AoE 行为。
- 防止未来重构 targeting 时破坏前后端坐标契约。
- 不影响自定义技能能力。

### 6. 将 `setBaseSelective` 标成危险 API，并加误用测试

不删除 `setBaseSelective`，因为它仍是底层能力。

建议：

- 测试层面保留并强化“单组件更新必须用 `updateBase`”的回归。
- 在 JS 暴露层或文档中明确：`setBaseSelective` 只用于重建 base 快照，不用于更新单个结构性组件。
- 可选：新增更明确命名的 API，如 `replaceBaseComponents`，后续逐步替换调用点。

价值：

- 保留扩展性。
- 降低未来生成代码误用概率。

## V1 不做的事

- 不收紧 `HostAccess.ALL`。
- 不禁止 bespoke skill JS。
- 不把所有技能强行迁到纯 YAML。
- 不优化小地图寻路。
- 不优化每个 buff 独立扫描 combatants。
- 不重做 buff 生命周期系统。
- 不统一所有 combat event shape，先只建立 helper 和测试。

## 建议执行顺序

1. 内容验证测试。
2. `EntityStore.clear()` 修复。
3. `cleave/cross_blast` 死亡顺序测试。
4. 空地 AoE 坐标契约测试。
5. bespoke emit helper，小范围迁移 `cleave/cross_blast`。
6. `setBaseSelective` 危险 API 标记和测试补强。
