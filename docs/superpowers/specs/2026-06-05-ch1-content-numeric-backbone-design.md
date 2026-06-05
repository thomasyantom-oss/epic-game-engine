# Ch1 内容设计轮 · 数值骨架 — 设计文档

**日期:** 2026-06-05
**性质:** Chapter 1 内容设计轮的**第一刀(横切)**。Ch1 框架 7/7 已合并(见 `2026-05-29-game-roadmap-design.md` §5),但内容真空。本轮**不填任何具体职业内容**,只拍**跨职业共享、内容无关的数值骨架**:等级主轴 / 技能等级曲线 / 缩放预算 / 球成本 / 被动预算。之后每职业各开独立内容轮,往这套已定框架里即插即填。

---

## 1. 背景

母文档 `2026-05-30-chapter1-skill-growth-core-design.md` 与 memory `project-chapter1-decomposition` 记录:Ch1 七个 feature 全是「框架 + demo 夹具」。本轮锚点已在 brainstorm 拍死:

- **等级 = 尺子 / 门禁 + 成长发放节奏器**(roadmap「等级当尺子」、「RNG 在获取不在结果」)。升级偏快,长线功率压在天赋 / 球进化 / 装备(RNG 产出)上;等级成长只提供基础属性盘,占总功率小头。
- **技能等级随技能自带 `tier` + 当前人物级派生**,与玩家「何时学」无关。
- 所有可调常量集中到一张**中央调参板** `mods/base-rules/progression.yaml`,沿用 `colors.yaml` / `item_rarity.yaml` / `modifier_types.yaml` 既有「根目录独立配置」惯例。引擎 / handler 读常量,内容轮作者照文档化 rubric 填每技能 `level_scaling`。

### 设计红线(沿用母文档 §1)

- 数值层不受机制层扰动;骨架是参数化框架,不是写死的平衡值。
- 真平衡(玩家总功率 vs 怪物)是**带内容的 playtest + sim 包**的工作,不在本轮。本轮只交付**形状、接缝、带默认常量的 rubric**。
- 怪物成长 / 护甲成长本身是脚手架非最终值(memory `project-core-combat-formulas`),故本轮一切常量皆「占位但自洽」、可调。

---

## 2. 范围

### 本轮做(In)

1. 新建 `mods/base-rules/progression.yaml` 调参板(§3 全文)。
2. `xpForLevel` 改读 `level.xp_curve`(指数曲线),`gain_xp` 加 `level.cap` 封顶。
3. `applySkillLevelCurve` 从 no-op 填真:据 `skill_level` tier + 当前人物级,为 `Skillbook.known` 每条写 `level` 字段。
4. 技能 YAML 支持 `tier:` 字段(选 smooth/standard/chunky,默认 smooth)与 `level_curve:` 内联覆盖。
5. demo 技能(法师系)按新 rubric 补一两条 `level_scaling` 作**接缝验证样例**(非内容铺开)。
6. `orb.cost_per_evolution` 接入进化扣球路径(若现进化逻辑写死 count,改读常量)。

### 本轮不做(Out)

- **不填具体职业内容**:不写各职业专精树 / 天赋树节点 / 真被动 / 真技能数值表。那是后续每职业内容轮。
- 不做球的**真供给**(Ch3)、装备 patch 源(Ch2)、洗点收费(Ch5)。
- 不动 `passive_budget` 进引擎——它仅供 sim / 作者参照(§3.5)。
- 不做「球技能」(学习球里携带的技能)——见 §6 backlog。

---

## 3. `progression.yaml` 设计

```yaml
# progression.yaml — Ch1 数值骨架调参板（内容无关，跨职业共享）

# === 1. 等级主轴 ===
level:
  cap: 100
  xp_curve:                # 到 L+1 所需 = round(base * L^exp)，低指数
    base: 100
    exp: 1.5               # 前期快、中期缓、后期更慢。唯一旋钮

# === 2. 技能等级曲线（看技能自带 tier + 当前人物级，不看学习时机）===
skill_level:
  cap: 10                  # 所有技能最高 10 级
  default: smooth
  tiers:                   # start=该档起始/锚点人物级，per=每多少人物级 +1 技能级
    smooth:   { start: 1,  per: 2 }   # 前期技能，约 19 级满
    standard: { start: 20, per: 3 }   # 中期技能，约 47 级满
    chunky:   { start: 50, per: 5 }   # 后期技能，约 95 级满
  # skillLevel = clamp(1 + floor((charLevel - start)/per), 1, cap)
  # 技能在 YAML 里 tier: standard 选档（默认 smooth）

# === 3. 技能数值缩放预算（rubric，作者反推 level_scaling 用）===
scaling_budget:
  baseline_multiplier: 5.0   # 标准输出技能 lv1→lv10 裸值约 ×5
  # 选 ×5 使每级增量在 10 级内整除不出小数；基础值主要影响前期，精确值 playtest 调
  # 注：仅单技能裸值参照；玩家总功率 vs 怪物 由 sim/playtest 另调

# === 4. 灵魂球进化成本 ===
orb:
  cost_per_evolution: 1      # 每次进化固定 1 颗对应类型的球

# === 5. 被动功率预算基准（仅 sim/作者参照，引擎不读）===
passive_budget:
  unit_primary_stat: 10
  tiers: { minor: 1, normal: 2, major: 4 }   # 小/中/大 ≈ 10/20/40 点属性
```

### 3.1 等级主轴

- `xpForLevel(L) = round(base * L^exp)` 取代现 `level*100`。`exp=1.5`(低指数):每升一级所需单调变多 → 前期蹭涨、后期一级磨久。直观(base=100):L1→2≈100,L10≈3160,L50≈35000,L99≈98500。
- **唯一旋钮 = `exp`**:嫌后段太肝调 1.3,嫌太快调 1.7,公式不动。
- `gain_xp`(`leveling.js:60`)现状是 `if` 单次升级——大额 XP 只升一级。改为 **`while` 循环**:连续扣阈值升级直到 XP 不足或到 `level.cap`。到 cap 时:`Experience.level`/`Character.level` clamp 到 cap、`xp` 置 0、不再积溢出。
- `debug.set_level`(`leveling.js:39`)现只拒 `<1`,**须加 `level.cap` 上限 clamp**(超 cap 取 cap),并同步 `Experience.level` 与 `Character.level`(§5 验证满级封顶依赖它)。

### 3.2 技能等级曲线(核心接缝)

- 技能等级**不依赖玩家何时学**,只看技能自带 `tier`(内置起始/锚点人物级 + 成长率)与**当前人物级**:
  ```
  skillLevel = clamp(1 + floor((charLevel - tier.start) / tier.per), 1, skill_level.cap)
  ```
- 三档:smooth(start1,前期)/ standard(start20,中期)/ chunky(start50,后期)。技能在自己 YAML 里 `tier: standard` 选档,默认 smooth;亦可 `level_curve: { start, per }` 内联覆盖。
- **行为示例(校验):** 人物 50 级时
  - smooth 技能:`1+floor(49/2)=25 → cap 10`(直接满级)
  - standard 技能:`1+floor(30/3)=11 → cap 10`(直接满级)
  - chunky 技能:`1+floor(0/5)=1`(从 1 级养起)
- **实现策略(最小侵入):** `applySkillLevelCurve(entityId)` 遍历 `Skillbook.known`,对每条 `base` 用现有 `Skill.loadSpecAny(base)` 载入技能定义、`Skill._toJs` 转 JS 对象,**按优先级读曲线参数:`spec.level_curve`(内联覆盖)> `spec.tier`(选档)> `progression.skill_level.default`**;再结合实体当前 level 算出 skillLevel,**写回 `known[i].level`**。下游 `Skill.applyLevelScaling(spec, inst.level)` 与 `ownerInstance`(`00_skill_lib.js:154/181`)**完全不改**——照旧读 `known[i].level`,只是该值现在由曲线算出。未知 tier 名 fallback 到 `default` 并打一条 warn(不报错,避免一个坏 YAML 卡全表)。
- **作用范围 = 全部 `known` 条目(主动 + 被动统一)。** 被动也消费 `level`(`04_passive_lib.js:38` flattenSpec 带 level),故曲线对主动/被动一视同仁写 `level`;某被动是否真的随 level 变强,取决于它自己有没有 `level_scaling`(多数 demo 被动无,故实际 level 无关)。
- **触发点(实测,纠正):** 现 `applySkillLevelCurve` 在 `entity.loaded`(`recalculate_hooks.js:99`)与 `entity.level_up`(同文件 `:127`)被调,`after_recalculate`(`:10`)只复位 HP——本轮只填这两处已就位调用的函数体。**硬时序约束:曲线写 `level` 必须发生在 `Passive.registerStatMods`/`applySpec` 之前**(loaded 路径现已是此序:`:99` 曲线 → `:110/112` 被动注册,保持不变)。
- **`debug.set_level` 也要触发曲线:** 该跳级脚手架(`leveling.js:39`)走 `applySpec` 不发 `level_up`,故须在其末尾(recalc 后)显式调一次 `applySkillLevelCurve`,否则跳级后技能等级不刷新。
- **施法路径不重算:** 战斗 `resolveSpec` 只读实例 `known[i].level`、不重建 known(`00_skill_lib.js` 施法路径无 Skillbook 重建),故曲线值在一次角色状态变化(loaded/level_up/debug)后稳定到下次变化,不会被施法复位。
- **不再需要 `learnedAt` 字段**(brainstorm 中一度引入,因改为 tier 派生而撤回)。`Skillbook.known` 数据模型不增字段;`level` 字段从「debug/实例存储」语义变为「曲线派生(每次 recalc 重算)」。

### 3.3 技能数值缩放预算(rubric)

- `level_scaling` 引擎机制不变:`某级数值 = base + delta×(skillLevel-1)`,线性叠加(`00_skill_lib.js` `applyLevelScaling`)。
- `baseline_multiplier: 5.0` 是**目标比值**:标准输出技能裸值 lv1→lv10 约 ×5。作者据此反推每级 delta 后填进各技能 `level_scaling`。引擎线性叠加 `delta×(lv-1)`,9 个增量到满级,故 `delta = 4×base/9` ——**×5 是目标倍率,不天然整除**;作者自行选可整除的 base/delta(基础值主要影响前期),精确值 playtest 调。
- **明确边界:** 此预算仅约束**单技能裸值**,不解决「玩家总功率追不追得上怪物」——后者是 base + 属性缩放 + 天赋/球/被动 + 装备的总和,且依赖怪物成长(脚手架),由 sim/playtest 在内容轮另调。

### 3.4 灵魂球进化成本

- `orb.cost_per_evolution: 1`:每次技能槽进化固定消耗 1 颗对应类型的球。
- 接入点(实测):`action.talent_place_orb`(`talent.js:460`)读 `slot.orb`(`:482`)、`count = parseInt(orb.count || 0)`(`:484`)、扣球(`:497`)。**现状 missing count → 0(免费 bug)。** 改为精确规则:
  ```
  count = (orb.count != null) ? parseInt(orb.count) : progression.orb.cost_per_evolution
  ```
- **同步展示:** 进化成本在 `effectSummary` / 快照里若有展示,须一并改读同一规则,避免 UI 显示空或旧 count。
- **demo 决策:** 把 `elementalist.yaml` 进化节点的显式 `count:` 删掉、改吃默认 1(顺便验证默认路径),不写 `count: 1`。
- 球在 Ch1 全程 debug 给(`debug.grant_orb`),真供给 Ch3,故本值占位但自洽。

### 3.5 被动功率预算基准(纯 rubric)

- **引擎不读 `passive_budget`。** 它是给内容轮作者 + sim 包的平衡公分母,杜绝各调各的失衡。
- 通货 = 等价主属性点(主属性是所有派生强度之源,最适合当公分母):`unit_primary_stat: 10`,即「1 档 ≈ 10 点主属性的战力」。
- 三档 minor/normal/major = 1/2/4 档 ≈ 10/20/40 点。作者调新被动时对照「这被动该多强」,sim 可据此标记超/欠预算的被动。

---

## 4. 接缝与改动清单

| 位置 | 改动 | 性质 |
|---|---|---|
| `mods/base-rules/progression.yaml` | 新建调参板 | 新增 |
| `leveling.js` `xpForLevel`(`:1`) | `level*100` → `round(base*L^exp)`,读 progression.yaml | 改 |
| `leveling.js` `gain_xp`(`:60`) | `if`→`while` 多级升级 + `level.cap` clamp + 溢出归零 | 改 |
| `leveling.js` `debug.set_level`(`:39`) | 加 `level.cap` 上限 clamp + 同步 Exp/Char level | 改 |
| `recalculate_hooks.js` `applySkillLevelCurve`(`:123`) | no-op → 据 tier+charLevel 写全部 `known[i].level`(主动+被动) | 填真 |
| `leveling.js` `debug.set_level` 末尾 | recalc 后显式调一次 `applySkillLevelCurve` | 改 |
| 技能/被动 YAML(约定) | 支持 `tier:` 与 `level_curve:` 覆盖字段 | 约定新增 |
| 法师 demo 技能 | 补 1~2 条 `level_scaling` 作接缝样例 | 验证用 |
| `talent.js` `action.talent_place_orb`(`:484`) | count 缺省读 `orb.cost_per_evolution`(否则免费 bug) | 改 |
| `elementalist.yaml` 进化节点 | 删显式 `count:`,吃默认 1 | 验证用 |

- **progression.yaml 加载(纠正):** handler 通过 `engine.loadYaml("progression.yaml")` **按需读取**(同 `colors.yaml`/`item_rarity.yaml` 在 `bootstrap.js`/`equip.js` 的读法);它**不是** ModuleLoader 自动加载的数据表(ModuleLoader 只自动执行 JS 目录)。yaml 变更由 HotReloader 触发脚本热重载从而重读,改值即时生效。建议在 skill 库里缓存一次解析结果(类似 `Skill._specs`),热重载时清缓存。
- 无 schema 迁移:`Skillbook.known` 不增字段;`level` 语义从存储值变派生值,旧存档 `level` 会在首次 recalc 被曲线覆写(可接受,dev build)。
- **`debug.set_skill_level` 兼容性:** 该脚手架(`04_passive_lib.js:109` 直接写 `known[i].level`)在本轮后**不再是持久技能等级入口**——任何 loaded/level_up/debug.set_level 触发的曲线刷新都会覆写它。spec 立场:保留该入口作**临时覆盖**(下次曲线刷新即失效),并在其注释标注「值会被派生曲线覆盖」;不改名。

---

## 5. 验证方式

- **单元 / 接缝:** `applySkillLevelCurve` 的 clamp 公式针对 §3.2 三个示例点(smooth/standard/chunky @ 人物 50 级)断言;`xpForLevel` 对 L=1/10/50/99 断言曲线单调与量级。
- **后端:** `cd backend && mvn test` 全绿。
- **前端:** `npm run build` 通过(无前端逻辑改动,仅回归)。
- **手动 verify(脚手架):** `debug.set_level` 跳级 → 观察某技能 `level` 随人物级按 tier 爬升、满级封顶;`grant_orb` + 进化 → 扣 1 球生效。
- **sim:** balance-check / sim 包可读 progression.yaml 常量,对 demo 技能跑 lv1→lv10 裸值是否落在 ×5 预算附近(非硬断言,观测用)。

---

## 6. Backlog / 延后项

- **球技能(orb skills):** 灵魂球除「进化已有技能槽」外,还能**让玩家学习球里携带的技能**(获取新主动技能的另一条路)。本轮不设计,待后续单议。
- `passive_budget` 接入 sim 自动校验(本轮只定义常量,不写校验器)。
- 单技能功率走指数(非线性)缩放:目前引擎 `level_scaling` 线性叠加;若日后想要单技能也指数成长,再加 curve 形状字段。
- 进化节点显式 count 覆盖语义(本轮保留逃生舱但默认 1,未铺开多球进化设计)。

---

## 7. 下一步

落实现 plan(`writing-plans`),拆成可独立验证的步骤,纳 Codex spec/plan review(团队工作流),再派实现。
