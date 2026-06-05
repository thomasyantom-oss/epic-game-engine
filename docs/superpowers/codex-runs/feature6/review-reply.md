**总评: REQUEST-CHANGES**

设计方向基本对，但实现前有几个会直接踩现有管线的阻塞点，尤其是 modifier priority / `weaponAttr` 生命周期 / 旧 `allocate_point` 覆盖 `level_growth`。

**1. R 追溯成长是否真成立：有风险**

依据：当前 [ModifierChain.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/core/ModifierChain.java:66) 的 `addModifier` 同 id 先删后加，`recalculate` 每次 `restoreBaseState` 后重放 modifier；[leveling.js](C:/workplace/epic/mods/base-rules/handlers/character/leveling.js) 的 `registerLevelGrowthModifier` 用固定 id `level_growth`，按 `growth × (level-1)` 从当前 `PrimaryStats` 叠加。

结论：如果把 `registerLevelGrowthModifier` 改为 apply 时读取 `effectiveGrowth(entity)`，并且选择专精后重注册同 id `level_growth`，免迁移追溯成立。旧成长不会残留，重启也不会膨胀，因为 load 会复位 base stat 再重注册 modifier。

风险：
- `action.allocate_point` 仍然会用同一个 `level_growth` id 注册旧逻辑，能覆盖掉 spec-aware 成长。
- spec 需要明确 `effectiveGrowth` 是“最深 growth 节点整表替换”，不是 merge。
- 选择专精时如果只注册 spec modifier、不重注册/触发 level_growth，则追溯不生效。

修改建议：spec 加阻塞项：禁用或同步改造 `action.allocate_point`；`choose_specialization` 必须重注册 `level_growth` 后 recalculate；load 和 choose 共用同一个 apply path。

**2. modifier 生命周期 + 幂等：有风险**

依据：[recalculate_hooks.js](C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js) load 期先把 stat comps 复位，再把职业 `weapon_attr` 写进 `PrimaryStats.weaponAttr`，然后 `setBaseSelective`；[derived_stats.js](C:/workplace/epic/mods/base-rules/handlers/character/derived_stats.js) 的 derived modifier priority 300，读取 `PrimaryStats.weaponAttr` 算 `DerivedStats.物理强度`。

实际 priority 是 `equipment=50, passive=70, level=90, class=180, derived=300`，由 [modifier_types.yaml](C:/workplace/epic/mods/base-rules/modifier_types.yaml) 控制，不是注册顺序。

风险：
- spec 写“class → level_growth → equipment → spec → derived → passive”容易误导；执行顺序实际由 priority 决定。
- 新 `spec` modifier 没定义 type/priority。若 priority 默认 0 或晚于 derived，`main_attr` 会不生效或被 derived 读不到。
- “weaponAttr 覆写复用 load 期按 class 还原 weaponAttr 的同一位置”表述危险。专精的 `weaponAttr` 更适合做 modifier 输出，而不是写进 base，否则 load/choose/setBase 的语义会混乱。

修改建议：新增 `modifier_types.yaml` 中 `spec` type，priority 明确放在 derived 前、class/base weaponAttr 后，例如 220。spec 文档改成“class weaponAttr 是 base 默认，specialization main_attr 是 priority <300 的 modifier 覆写”。

**3. #6 ↔ #7 薄契约：有风险**

#6 只存 path、不做升级 gating，在 #7 未实现时可以自洽。但母文档 §1.10 说专精是升级总闸，所以 #7 必须在 UI 和 API 两侧读取 `Specialization.path` 校验。

遗漏接缝：当前 [00_skill_lib.js](C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js) 的 `resolveSpec` 是 level → node → passive patches，没有 specialization patch 源。若未来专精要改技能 scaling/name/enabled，#7 会返工技能 spec 管线。

修改建议：#6 至少预留 `applySpecializationPatches(ctx, baseId, spec)` 空接缝，哪怕本轮不放真实 patch。

**4. 持久化：成立**

`Specialization { path }` 作为非 base structural component，靠 persistent tag 自动保存，和 [PersistenceService.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/persistence/PersistenceService.java) 现有模型吻合。`List<String>` 经 Jackson 存取为 list，JS 侧 `size/get` 可用。

注意：不要把 `Specialization` 放进 `setBaseSelective` 的 stat comp 列表。它应是非 base 组件，避免 recalculate restore 干扰真实选择状态。

**5. 快照 / 前端契约：有风险**

`path / pending / locked` 三态基本够渲染，但当前 [WorldSnapshot.java](C:/workplace/epic/backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java) 没有 `specialization` 字段，需要改 API contract。

缺口：
- `pending.options` 只有 label/description，做不可洗确认时信息偏少。至少应暴露该选项的效果摘要：`main_attr/growth/grant_passives`。
- “已到最深层且无 pending”可由 `pending=null && locked=[]` 推断，但建议前端文案明确支持 terminal 状态。
- `action.choose_specialization` 的失败结果/错误 message 要进现有 action result 通路，否则前端无法展示拒绝原因。

**6. 测试覆盖：有风险**

§7 覆盖了主路径，但建议补：
- `allocate_point` 被禁用或不会覆盖 spec-aware `level_growth`。
- `spec` priority：`main_attr` 覆写后 derived 读到新 `weaponAttr`。
- 装备武器存在时，`weaponAttr` 只影响 `物理强度` 查找，不误改武器自身 `weaponAttr/base` 元数据。
- 未专精但 L>1 的 load 往返回归。
- terminal snapshot：最深层已选，`pending=null, locked=[]`。
- `grant_passives` 重复 choose/load 不产生 Skillbook duplicate。

**7. 与母文档 / 既有约定冲突：有风险但可修**

不违反 §1.7 不可洗，也不构成 §1.10 独立天赋树。等级仍是尺子，专精改成长模板属于身份分支后的尺子变化，可以接受。

主要冲突是表述层：母文档说“选专精 = 升级树解锁”，本 spec 又说 #6 不做 gating。这个可以接受，但必须明确“#6 不实现消费者；#7 是唯一 gating 执行点”。

**8. 范围：偏大但可做**

后端机制、数据、action、load、snapshot、前端 modal、测试都在一轮里，偏大但不是不可吞。更稳的切法是先交后端机制 + snapshot + tests，再做前端 panel。

**实现前必须先解决**

1. 定义 `spec` modifier type 和 priority，保证 `main_attr` 在 derived 前生效。
2. 明确 `weaponAttr` 专精覆写是 modifier 输出，不写入 base 真相。
3. 禁用或改造 `action.allocate_point`，避免覆盖 `level_growth`。
4. 给 `resolveSpec` 预留 specialization patch 接缝，避免 #7 返工。
5. 扩展 `WorldSnapshot` 的 specialization contract，并补 terminal/effect summary 状态。