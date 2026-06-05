你是资深游戏引擎 SDE,做一次**spec review**(只评审设计文档,不写实现)。

## 评审对象
`docs/superpowers/specs/2026-06-05-ch1-content-numeric-backbone-design.md`

这是一个单机回合制刷子养成游戏(text RPG)的 **Chapter 1 内容设计轮·数值骨架**设计文档。引擎是 Java 21 + Spring Boot + GraalJS,游戏规则全在 `mods/base-rules/` 的 JS handler + YAML 里。本轮**横切**:只拍跨职业、内容无关的数值骨架(progression.yaml 调参板 + 技能等级曲线接缝),不填具体职业内容。

## 必读的现有代码(核对 spec 接缝是否与现状吻合)
- `mods/base-rules/handlers/character/leveling.js` —— `xpForLevel`、`gain_xp`、`registerLevelGrowthModifier`、`debug.set_level`
- `mods/base-rules/handlers/character/recalculate_hooks.js` —— `applySkillLevelCurve`(现 no-op)及其触发点(after_recalculate / level_up)
- `mods/base-rules/handlers/skill/00_skill_lib.js` —— `resolveSpec`、`applyLevelScaling`、`ownerInstance`(读 Skillbook.known[i].level 的现状)
- `mods/base-rules/talents/elementalist.yaml` —— 现进化节点 `skill_slot.orb.count` 写法
- `mods/base-rules/colors.yaml` / `item_rarity.yaml` / `modifier_types.yaml` —— 现有「根目录独立 YAML 配置」惯例,确认 progression.yaml 的加载方式可行
- 任何加载 YAML 的入口(engine.loadYaml / ModuleLoader),确认 progression.yaml 怎么被读

## 重点核查
1. **接缝可行性**:spec §3.2/§4 说 `applySkillLevelCurve` 写回 `Skillbook.known[i].level`、下游 `applyLevelScaling(spec, inst.level)` 不改即可工作——核对 `ownerInstance` 读 level 的现状,这条接缝真的成立吗?有没有 recalc 时序问题(applySkillLevelCurve 在 after_recalculate 写 level,但施法 resolveSpec 在战斗时才读,中间 level 会不会被别处复位)?
2. **tier 读取**:applySkillLevelCurve 要读每个 known 技能 base 定义的 `tier` 字段——现有从 JS 读取技能定义的途径是什么?spec 没指明,plan 阶段要补吗?
3. **进化扣球**:spec §3.4 说默认读 `orb.cost_per_evolution`、节点可显式覆盖——找到现进化扣球的真实代码路径,确认这个接入点存在且 spec 描述准确(注意 demo 里 count:2,spec 说本轮默认改 1)。
4. **cap/曲线**:`xpForLevel` 改指数 + `gain_xp` 加 cap100,有无遗漏(如 debug.set_level 跳级是否绕过 cap、溢出 XP 处理)?
5. **数据语义变更**:`Skillbook.known[i].level` 从「存储值/debug 设」变「曲线派生(每次 recalc 重算)」——这会不会破坏现有 `debug.set_skill_level` 脚手架(它写 level 会被曲线覆写吗)?spec 该不该说明这点?
6. **内部一致性 / 漏洞 / 歧义**:公式、示例数(19/47/95 满级、人物 50 级三档示例)、范围(In/Out)有无自相矛盾或没覆盖的边界。

## 输出格式
列出 findings,每条标 **[阻塞] / [建议] / [疑问]**,给出依据(引用具体文件/行或 spec 章节)和修改建议。最后给一句总评:spec 能否进 plan 阶段。不要改任何文件,只输出评审。
