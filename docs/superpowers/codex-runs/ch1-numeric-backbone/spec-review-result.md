**Findings**

[阻塞] `entity.after_recalculate` 触发点与 spec 不吻合，且如果补错会造成被动等级时序问题。  
依据：spec §3.2/§4 写“`entity.after_recalculate` 与 `entity.level_up` 已调 `applySkillLevelCurve`”；但现状 [recalculate_hooks.js](/C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:10) 的 `after_recalculate` 只恢复 HP/MP，[同文件](/C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:99) 是 `entity.loaded` 调，[同文件](/C:/workplace/epic/mods/base-rules/handlers/character/recalculate_hooks.js:127) 是 `entity.level_up` 调。  
修改建议：spec 改成真实触发点，并在 plan 明确：技能等级写回必须发生在 `Passive.registerStatMods` 之前；如果新增 `after_recalculate` 调用，只能作为主动技能显示/持久化同步，不能替代被动 modifier 注册前的曲线应用。

[阻塞] `gain_xp` 的 cap 设计遗漏“大额 XP 多级升级”和 cap 后状态归零细节。  
依据：[leveling.js](/C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:69) 只加一次 XP，[leveling.js](/C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:73) 只 `if` 升一级，不是 `while`；spec §3.1 只说到 cap 不再升、不再积溢出 XP。  
修改建议：plan 明确 `gain_xp` 应循环升级直到 XP 不足或到 `level.cap`；到 cap 时同时把 `Experience.level` / `Character.level` clamp 到 cap，并把 `xp` 置 0 或固定不变，建议置 0。否则大额奖励会只升 1 级，cap 行为也不稳定。

[阻塞] `debug.set_level` 会绕过 cap，spec 验证还依赖它。  
依据：[leveling.js](/C:/workplace/epic/mods/base-rules/handlers/character/leveling.js:39) 的 `debug.set_level` 只拒绝 `<1`，没有上限；spec §5 要用 `debug.set_level` 验证满级封顶。  
修改建议：spec §4/§5 明确 `debug.set_level` 也读 `level.cap` 并 clamp 或拒绝超 cap，且同步 `Experience.level` 和 `Character.level`。

[建议] `applySkillLevelCurve -> known[i].level -> applyLevelScaling` 这个主动技能接缝成立，但 spec 应补充“不会被施法路径复位”的依据和边界。  
依据：[00_skill_lib.js](/C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:154) `ownerInstance` 确实从 `Skillbook.known` 读 `level`，[同文件](/C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:171) `resolveSpec` 把该 level 传给 [applyLevelScaling](/C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:181)。施法时没有重建 `Skillbook.known`。  
修改建议：保留该最小侵入策略，但 spec 加一句：战斗施法只读实例 level，不重算；曲线更新依赖 loaded / level_up / debug.set_level 等角色状态变化触发。

[阻塞] tier 读取途径没有写清楚，plan 阶段必须补。  
依据：当前技能定义通过 `engine.loadYaml("skills/" + id + ".yaml")` 读取，例如 [00_skill_lib.js](/C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:3)，但 spec §3.2 只说“读每条 base 技能定义的 tier”，没有指定用 `Skill.loadSpecAny`、`Skill._toJs` 还是直接 Java Map 读取。现有技能 YAML 搜索未发现 `tier` / `level_curve` 字段。  
修改建议：在 spec §4 加实现约定：`applySkillLevelCurve` 对每个 `known[i].base` 调 `Skill.loadSpecAny(base)`，用 `Skill._toJs` 转 JS 对象，读取 `spec.level_curve` 优先，其次 `spec.tier || progression.skill_level.default`；未知 tier fallback/default 或报错策略也要写明。

[阻塞] 进化扣球接入点存在，但 spec 描述“默认读常量，节点可覆盖”需要落到真实代码语义。  
依据：现进化真实路径是 [talent.js](/C:/workplace/epic/mods/base-rules/handlers/character/talent.js:460) `action.talent_place_orb`，[talent.js](/C:/workplace/epic/mods/base-rules/handlers/character/talent.js:482) 读取 `slot.orb`，[talent.js](/C:/workplace/epic/mods/base-rules/handlers/character/talent.js:484) `parseInt(orb.count || 0)`，扣除在 [talent.js](/C:/workplace/epic/mods/base-rules/handlers/character/talent.js:497)。demo 节点确实有 `count: 2`：[elementalist.yaml](/C:/workplace/epic/mods/base-rules/talents/elementalist.yaml:19)。  
修改建议：spec 改为精确规则：`count = orb.count != null ? parseInt(orb.count) : progression.orb.cost_per_evolution`，并同步 `effectSummary` / UI snapshot 的 count 展示，否则 UI 仍可能显示空或旧 count；demo 默认改 1 时要决定是删 `count` 使用默认，还是显式 `count: 1`。

[建议] `progression.yaml` 根目录读取可行，但“走 ModuleLoader 加载”表述不准。  
依据：`engine.loadYaml(relativePath)` 在 [ScriptRuntime.java](/C:/workplace/epic/backend/src/main/java/com/epic/engine/script/ScriptRuntime.java:113) 用当前 module context resolve；`colors.yaml`、`item_rarity.yaml` 已这样读：[bootstrap.js](/C:/workplace/epic/mods/base-rules/handlers/world/bootstrap.js:24)、[equip.js](/C:/workplace/epic/mods/base-rules/handlers/equipment/equip.js:4)。`ModuleLoader` 只自动执行 JS 目录：[ModuleLoader.java](/C:/workplace/epic/backend/src/main/java/com/epic/engine/module/ModuleLoader.java:46)。HotReloader 会监听 yaml 变更重放脚本：[HotReloader.java](/C:/workplace/epic/backend/src/main/java/com/epic/engine/script/HotReloader.java:76)。  
修改建议：spec 写成“handler 通过 `engine.loadYaml("progression.yaml")` 按需读取；yaml 变更触发脚本热重载，但不是 ModuleLoader 自动加载数据表”。

[建议] `debug.set_skill_level` 语义会被曲线覆盖，spec 应明确废弃或改名。  
依据：[04_passive_lib.js](/C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:109) 直接写 `known[i].level`，随后 [同文件](/C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:117) 注册被动并 recalc。若新曲线在 recalc/level_up/loaded 写回，它会覆盖 debug 设值。  
修改建议：spec §3.2/§5 增加兼容说明：`debug.set_skill_level` 在本轮后不再是持久等级调试入口；要么只作为临时覆盖且下一次曲线刷新失效，要么改成 `debug.preview_skill_level` / 保留但文档标注会被派生曲线覆盖。

[疑问] `applySkillLevelCurve` 是否应覆盖被动技能等级？  
依据：`Skillbook.known` 同时包含主动和被动，初始被动在 [select.js](/C:/workplace/epic/mods/base-rules/handlers/character/select.js:155) 也写 `level: 1`；`Passive.ownedSpecs` 会读 known level 影响被动 modifier：[04_passive_lib.js](/C:/workplace/epic/mods/base-rules/handlers/skill/04_passive_lib.js:31)。spec 只讨论“技能等级曲线”和主动 `applyLevelScaling`，但实现写“遍历 `Skillbook.known` 每条”。  
修改建议：明确范围：只对 `kind !== passive` 的主动技能生效，还是主动/被动都走同一曲线。若包括被动，需要把 `Passive.registerStatMods` 时序作为硬约束。

[建议] scaling budget 的“×5 使每级增量整除不出小数”不严谨。  
依据：线性 `level_scaling` 是 `delta * (lv - 1)`：[00_skill_lib.js](/C:/workplace/epic/mods/base-rules/handlers/skill/00_skill_lib.js:181)。若 lv1 到 lv10 目标 ×5，则 `delta = (5B - B) / 9 = 4B/9`，并不天然为整数，只有 base 是 9 的倍数才整除。  
修改建议：改成“作者选择可整除的 base/delta，×5 是目标倍率而非自动整除保证”。

**总评**

不能直接进 plan 阶段。主接缝方向成立，但需要先修正文档里的触发点、cap/XP/debug 边界、tier 读取方式、进化默认 count 语义和 `debug.set_skill_level` 语义，否则 plan 会把实现拆到错误接缝上。