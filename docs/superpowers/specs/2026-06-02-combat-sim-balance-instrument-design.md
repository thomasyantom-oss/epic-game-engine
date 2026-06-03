# Feature #3(切片二):数值平衡模拟器 / 战斗仪表盘 — 设计

作者:Claude (Senior SDE) · 2026-06-02(v2,纳入 Codex review)
脑暴出处:`docs/feature3-damage-types-running-notes.md` + 本轮对话(数值方法论 / 输入输出 / AI 策略)。
实现:Codex。我(Senior SDE)review,**仅我 APPROVE 方可 merge**。
上游约束:#3 切片一(`Skill.mitigate` 唯一减伤收口已就位、空跑);F2 §1 安全/高级属性红线。

---

## 0. 这一版是什么(给 PM 看的一句话)

slice-2 主题是"配数值 / 做手感"。**配数值之前先造一台 PM 能直接用的数值仪表盘**:无头批量跑真引擎战斗,输出报表,让 PM **不看代码**就能回答四件事——**前期顺不顺、护甲 K / 抗性 cap 大概多少、一个 build 强多少、一场仗为什么赢/输**。

**这不是"完美战斗 AI 平台"的第一版,是"PM 能拿来调数值的仪表盘"。** 技能组 / 被动 / 专精 / 装备 / 新怪的内容设计是后续用它驱动的独立 slice,不在本刀。

---

## 1. PM 要回答的四个问题(本版围绕它们组织)

| # | PM 问题 | 输入 | 关键输出 |
|---|---|---|---|
| UC1 前期职业体检 | 五职业 1-10 级、裸装 baseline 打前期怪顺不顺? | class, level, encounter, iterations, policy | win_rate / ttk_median / win_hp_remaining / loss_enemy_hp_remaining |
| UC2 K/cap 校准 | 护甲 K、抗性 cap 设多少,生存曲线才达标? | K 或 cap 的 sweep | 每参数下 win_rate / player_survival_turns / enemy_ttk / mitigation_saved |
| UC3 build 对比 | 某装备/属性 modifier 让角色强了多少? | baseline build vs variant build | offense_index / defense_index(+ delta 摘要) |
| UC4 输赢解释 | 为什么这场 43%?伤害不够、太脆、还是策略? | 一场配置 | damage_by_skill / damage_taken_by_source / mitigation_saved / 平均死亡回合 / 主要击杀来源 |

**验收的硬标准:PM 看报表就能发现"某职业前期坐牢/明显过强"、据 sweep 选一个初始 K/cap、说出"这件装备约 +18% 输出 / +5% 生存"、解释一场仗为什么输赢——全程不读代码。**

---

## 2. PM Acceptance Criteria(★第一版必须能跑出这三份报告 + 五条曲线)

这是本 slice 的**验收闸**。Codex 实现完,以下产物能一键跑出 = 通过。

### 报告 A — `early_class_health_check`(对应 UC1)
- 维度:五职业 × 1–10 级 × 前期怪物(现有 encounter)。
- 每格输出:`win_rate / ttk_median / win_hp_remaining / loss_enemy_hp_remaining`。
- 用途:发现"某职业前期坐牢"或"某职业明显过强"。

### 报告 B — `armor_k_sweep`(对应 UC2)
- 扫:`K = 5, 10, 20, 40, 60`(固定一组玩家/怪配置)。
- 每个 K 输出:`win_rate / player_survival_turns / enemy_ttk / mitigation_saved`。
- 用途:PM 据此选一个初始 K。
- (抗性 cap 同形态:`resist_cap_sweep`,复用同机制,扫 cap。)

### 报告 C — `baseline_vs_variant`(对应 UC3)
- 比:baseline(空 sources)vs 一个加了**装备或属性 modifier** 的 variant。
- 输出:`offense_index / defense_index` + delta 摘要(如 "输出 +18% / 生存 +5%")。
- 用途:量化单个 build 部件的价值。

### 五条曲线(report 的可视化/表格数据,UC2/UC1 用)
1. `armor_K_vs_ttk` — x: armor_K;y: player_ttk_median, enemy_ttk_median。判断节奏。
2. `armor_K_vs_win_rate` — x: armor_K;y: win_rate。判断是否落目标胜率区间。
3. `armor_K_vs_win_hp_remaining` — x: armor_K;y: win_hp_remaining_median_percent。判断是否"险胜"。
4. `resist_cap_vs_ehp` — x: resist_cap;y: effective_hp_multiplier / skill_ttk。判断抗性上限是否防后期防御爆炸。
5. `class_level_1_10_health_check` — x: level;series: class;y: win_rate / ttk_median / win_hp_remaining。判断前期各职业流畅。

> 输出形态(表格/CSV/JSON 给前端画图)是 §6 开放实现细节,但**上述字段必须齐**。

---

## 3. INPUT(收窄到 v1 实际支持)

```yaml
knobs:
  armor_K:         20     # 护甲曲线 护甲/(护甲+K)
  resist_cap:      75     # 三抗上限 %
  resist_floor:   -50     # 负抗(易伤)下限 %
  damage_variance: 0.0    # ±伤害浮动带(★v1 唯一方差源,默认 0=关;crit 留后)

scale: { N: 1 }           # 比值→绝对,只为 ceil 分辨率

sides:
  player:
    units:
      - class:  warrior        # 读 class schema → base属性 + growth + weapon_attr + 初始技能
        level:  10             # 满级=100;stats = base + growth×(level−1)
        sources: []            # ★空 = 裸装无专精 baseline(测量零点)
        #  v1 只支持一种 source kind:简单属性/装备 modifier(给报告 C 用)
        #  - { kind: modifier, field: "PrimaryStats.力量", value: "+20" }
        #  - { kind: equipment, slot: weapon, base: 12, attr: 力量 }
        skills_override: null  # 默认用 starting_skills;可覆盖测特定配招
        pos: { row: FRONT, slot: 0 }
        policy: { kind: heuristic, target: lowest_hp, mp_aware: true, defend_below: 0.0 }
  enemy:
    units:
      - ref: forest_goblin     # 直接吃现有 encounter 实体(也可 inline)
        # 敌方 policy 逐 encounter 编写(=设计杠杆)

run:
  iterations: 1000
  seed: 0                  # 固定→可复现;或 seed_sweep 跑分布
  max_turns: 50            # 到顶 → 记 timeout(不判负,见 §6)

sweep:                     # 单参数扫(给报告 B 用)
  param:  knobs.armor_K    # 也可扫 .level / sources / cap 等单一入口
  range:  { from: 5, to: 60, step: 5 }
  metric: [win_rate, player_survival_turns, enemy_ttk, mitigation_saved]
```

**v1 支持矩阵(明确边界):**
- ✅ `class + level` baseline(读现有 class schema + 线性 growth)。
- ✅ `encounter ref`(吃现有 encounter)。
- ✅ 一种 source:简单属性/装备 modifier(报告 C 必需)。
- ✅ `scripted` policy + `simple heuristic` policy。
- ✅ 单参数 sweep。
- ❌ 不做:search/minimax policy、LLM 集成、其余 source kind(被动/专精/套装乘区)、矩阵跑、power_index 当头号指标。

---

## 4. OUTPUT(指标口径)

**A. 单场战斗指标(绝对)**
```
win_rate / loss_rate / timeout_rate         # 三者和=100%(timeout 不并入输,见 §6)
ttk                  { mean, median, p10, p90 }
win_hp_remaining     { median, p10 }         ★"将将击败"量化(目标中位 <20%)
loss_enemy_hp_remaining { median }           # 输的时候差多少(UC1 体检看坐牢程度)
player_survival_turns{ median }              # 玩家能撑几回合
damage_by_skill      { 每技能: raw / final } # 来自真实伤害路径 instrumentation
damage_taken_by_source { 每来源: final }
mitigation_saved     { 每单位: raw−final 累计 }  # 真 mitigate 处捕获
kill_source          { 主要击杀来源占比 }
```

**B. 相对 baseline 的两轴指数(UC3 主指标)**
```
offense_index = DPS(build) / DPS(同职业 baseline)      # 对固定参照,口径见 §6
defense_index = survival_turns(build) / survival_turns(同职业 baseline)
```
- **本版重点是 offense / defense 两轴。** `power_index = offense × defense` 保留为 **experimental**,不作头号验收指标。
- 边际归因(每个 source 各贡献多少)= future,output 结构预留。

---

## 5. 行动准则 / policy(scripted + heuristic 两档)

胜率/TTK 只在某出招策略下有意义。**每回合本质 = 给合法行动打分、选最高。** 那个打分函数 = "准则"。

**默认 heuristic 准则(从高到低):**
1. 保送人头(本回合能击杀 → 优先)。
2. 集火:`lowest_hp` | `highest_threat`(policy 参数)。
3. AOE 阈值:命中 ≥N 个才放群体。
4. 续航:HP < `defend_below` 则防御。
5. 资源:`mp_aware` 时尊重蓝耗;留爆发别 overkill。

**两档(v1):**
- `scripted`:固定出招表(回归/边界测试;也是未来 LLM 建议线的承载体)。
- `heuristic`:上面这套,**默认档**。
- (`search` = future,不在 v1。)

**双方角色不对称:** 玩家侧 policy = "打得好的真人"代理(否则内容会被调过软);敌人侧 policy 逐 encounter 编写 = 不靠数值的难度旋钮。

**白送诊断:策略胜率差**(同场跑 scripted/heuristic 看差值)= "这场奖励技巧还是只奖励数值"的读数。

---

## 6. 已定实现口径(把原"开放问题"定死,防漂移)

1. **timeout 不判负。** `max_turns` 到顶 = 记 `timeout`,单列 `timeout_rate`;`win_rate = wins/total`,`win/loss/timeout` 三桶和为 100%。timeout 通常代表"打不动/互锁",是信号不是输。
2. **baseline DPS/EHP 口径固定**(比值法,尺度自动抵消):
   - 两个**固定参照档**:`ref_dummy_offense`(0 护甲/0 抗、HP 极大永不死)、`ref_attacker_defense`(固定属性+固定伤害类型的标准攻击者)。
   - `DPS(x)` = x 对 `ref_dummy_offense` 的总伤害 ÷ 回合数,固定 seed 集取均值。
   - `survival_turns(x)` = x 面对 `ref_attacker_defense` 撑过的回合数,固定 seed 集取均值。
   - `offense_index = DPS(build)/DPS(baseline)`、`defense_index = survival_turns(build)/survival_turns(baseline)`,**同职业同 level**,baseline = 空 sources。
3. **policy 如何生成合法 command 写死:** policy 输出 `{ skillId, target: {targetId} | {targetRow,targetCol} }`,**经与前端同一条 action/skill 解析路径**下发。约束:技能须为该单位已知技能;`mp_aware` 时蓝不足不可选;被沉默不可选;`resolveTargets` 必须非空。`heuristic` 只在合法 (skill,target) 候选里打分;`scripted` 步骤非法时**回退 basic_attack**(并在报告里计一次 `illegal_step`)。
4. **simulator builder 必须复用真实路径:** 用现有角色/实体创建 + `ModifierChain` + `derived_stats` 构造双方,sources 经真 modifier/装备/buff API 挂上。**禁止另写一套数学。**
5. **damage breakdown 必须来自真实伤害路径 instrumentation:** 订阅真 `combat.damage_dealt` / 在 `Skill.mitigate` 处记录 `{attacker,target,skillId,type,raw,final,saved}` 聚合。**禁止事后旁路重算。**

---

## 7. 方差源(v1 引擎小改,只做一种)

确定性回合制下"输几次再一丝血过"无法靠重复(同 build 同结果)。需要一个方差源。
- **v1 只做 `damage_variance`(±伤害浮动带)**,受 `knobs.damage_variance` 控制,默认 0 = 关、行为同现状。
- 实装在**真出伤/减伤路径**(非模拟器私有),单一事实源;模拟器只把旋钮设非 0。
- **crit(暴击)= future**,本版不做。幅度多大留到用仪表盘校准时扫。

---

## 8. 方法论锚点(rationale,设计取舍的依据)

1. **属性是 TTK 方程的解,不是输入**:先定体验(TTK/存活回合),反解 HP/DPS。
2. **比值空间设计,绝对尺度最后乘**:手感全在比值;`N` 只为 ceil 分辨率。
3. **降维 = 只调几个旋钮**:个体属性由 class+level 公式算,全局只有 `K / cap / floor / damage_variance / N`。
4. **基线压平、高光放离散乘区**:成长温和(class `growth` 已线性),爽感来自稀有乘区 → 后期不爆炸。
5. **护甲曲线天然防爆**:`EHP=HP×(1+护甲/K)` 对护甲线性、永不破 100% → 无需硬 cap;flat 三抗会 hyperbolic 爆炸 → 必须硬 cap 75%。两层防御两套数学。
6. **先造仪器再调数**:模拟器几千场/秒先行,真人后验手感。"将将击败"= `win_rate 50-60%` 且 `win_hp_remaining 中位 <20%`。

**三阶段 TTK 目标:** 前期玩家 TTK 1-2 / 敌 6+(偏玩家、流畅);中期两边 TTK 拉近(卡墙受苦);后期靠 build 压低 TTK、顶高 EHP。墙=stat check,沟宽(高出自然功率 ~10% 量变 / ~30-50% 质变)= 受苦旋钮。唯一横向不变量:前期各职业都流畅;满级 baseline 故意参差,不强行校准。

---

## 9. 范围

**✅ v1 in:** §2 三报告 + 五曲线;§3 输入支持矩阵;§4 输出;§5 scripted+heuristic;§6 五条口径;§7 damage_variance(默认关)。交付物含 UC1 前期体检 + UC2 K/cap 初值。

**⏸ v1 out(留接口不实现):** search policy、矩阵跑、power_index 头号化、§4.B 边际归因、其余 source kind、LLM 集成。

**⏸ Future(独立 feature,用本仪表盘驱动,零仪器核心改动):** 技能组/被动/专精/装备/新怪的**内容设计**(全是新 source kind 或 enemy-config);完整站位/lane/宠物(见 future note);元素真实内容 + rider;crit;LLM=假设生成器(设计时人工,编成 scripted 喂模拟器验,数字不可信)。

---

## 10. 仍属实现期细节(不阻塞结构,Codex 定)

- 模拟器形态:JUnit 入口 / debug REST endpoint / CLI(倾向 test 入口,跑批快可断言)。
- 报告输出格式(CSV / JSON / 控制台表)。
- `ref_dummy_offense` / `ref_attacker_defense` 的具体属性数值。
- heuristic 打分函数的具体权重(先能跑出合理 baseline,后调)。
