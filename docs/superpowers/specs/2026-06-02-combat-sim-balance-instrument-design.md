# Feature #3(切片二):数值平衡模拟器 / 战斗仪器 — 设计

作者:Claude (Senior SDE) · 2026-06-02
脑暴出处:`docs/feature3-damage-types-running-notes.md`(减伤地基基调)+ 本轮对话(数值方法论 / 模拟器输入输出 / AI 策略)。
实现:Codex。我(Senior SDE)review,**仅我 APPROVE 方可 merge**。
上游约束:#3 切片一(`Skill.mitigate` 唯一减伤收口已就位、空跑);F2 §1 安全/高级属性红线。

---

## 0. 一句话

slice-2 的主题是"配数值 / 做手感"。**但在配任何数值之前,先造一台测量仪器**:一个无头批量战斗模拟器,跑真引擎战斗 N 次,吐出胜率 / TTK / 剩血 / 相对 baseline 功率。**本 slice 只交付仪器 + 用它定出护甲 K、抗性 cap、各职业前期 baseline 体检。** 技能组 / 被动 / 专精 / 装备 / 新怪的内容设计是后续用这台仪器驱动的独立 slice,不在本刀内。

---

## 1. 方法论锚点(贯穿,决定一切设计取舍)

1. **属性是 TTK 方程的解,不是输入。** 先定体验(击杀回合数 TTK / 存活回合数),反解出 HP/DPS。不手拍裸数值。
2. **在比值空间设计,绝对尺度最后乘。** 一切用"相对基准玩家"表达(怪血="N 下普攻"、抗性="×EHP")。手感全在比值;绝对尺度 `N` 只为 ceil 分辨率,不影响手感。
3. **降维 = 只调几个旋钮。** 个体属性由公式算(class+level),不手填。全局只有少数旋钮:护甲 `K`、抗性 `cap`、易伤 `floor`、暴击率/暴击倍率、伤害浮动带、尺度 `N`。
4. **基线压平,高光放离散乘区。** 成长曲线本身温和(现有 class `growth` 已是线性);爽感来自稀有乘区(套装/暗金/元素),不靠基线膨胀。→ 满足"后期不爆炸"。
5. **护甲曲线天然防爆。** `减伤%=护甲/(护甲+K)` ⟹ `EHP=HP×(1+护甲/K)`,EHP 对护甲线性、永不破 100%,**无需硬 cap**。对照 flat 三抗会 hyperbolic 爆炸,**故三抗必须硬 cap 75%**。两层防御用两套数学,各司其职。
6. **先造仪器,再调数。** spreadsheet/模拟器先行(几千场/秒),真人后测(验手感)。"将将击败"有量化定义:`win_rate ~50-60%` 且 `胜利时中位剩血 < 20%`。

---

## 2. 体验目标(三阶段 TTK,比值空间)

| 阶段 | 玩家 TTK(杀怪) | 敌人 TTK(杀你) | 手感 |
|---|---|---|---|
| 前期(≤10级,无专精) | 1~2 | 6+(高度不对称偏玩家) | 流畅、低压、靠 1~2 技能 |
| 中期(选专精后) | 拉长 | 与玩家 TTK 拉近 | 卡墙、稍受苦、去定向刷/换 build |
| 后期(毕业向) | 靠 build 压低 | 靠 EHP 顶高 | 重新拿回安全边际,机制乘区主导 |

- **墙(道馆/拦路怪)= stat check**:把墙要求功率设在"自然走到这里"之上 ~10%(刷怪垫一下过=量变沟)或 ~30-50%(换 build 过=质变沟)。沟宽 = 受苦程度旋钮。
- **唯一横向不变量:前期各职业都要"流畅"**(低等级 baseline 够强)。满级 baseline 故意参差,各职业成长曲线各走各的,**不强行校准**。

---

## 3. 架构:薄测量层贴真引擎

**模拟器跑真引擎战斗,不另写数学。** 给定配置 → 用真实体/组件/ModifierChain 构造双方 → 跑真回合循环 / 真技能 handler / 真 `Skill.mitigate` 收口 → 收集结果。

- **保真**:测的是真公式(含 ceil、方差、buff),永不与游戏漂移。
- **扩展性继承自引擎**:build 的每个维度 = 一个真引擎部件(modifier/装备/buff/专精)。模拟器只是"开打前把部件挂上、然后量"。**新 build 机制本来就要在引擎里加部件,模拟器自动吃到,核心不改。**
- **代价**:暴击/伤害浮动这些"引擎目前没有"的旋钮,要先在真减伤/出伤路径里实装(很小),保证单一事实源。见 §7。

---

## 4. INPUT(配置面)

```yaml
# ── 全局旋钮(要拧的那几个)──
knobs:
  armor_K:         20     # 护甲曲线 护甲/(护甲+K)
  resist_cap:      75     # 三抗上限 %
  resist_floor:   -50     # 负抗(易伤)下限 %
  crit_chance:     0.0    # 暴击率   ┐ 方差源(§7 新实装)
  crit_mult:       1.5    # 暴击倍率 │
  damage_variance: 0.0    # ±伤害浮动带 ┘

# ── 尺度(比值→绝对,只为 ceil 分辨率)──
scale: { N: 1 }

# ── 双方(combatant = class baseline + sources[];可 inline 或引用现有实体)──
sides:
  player:
    units:
      - class:  warrior      # 读 class schema → base属性 + growth + weapon_attr + 初始技能
        level:  10           # 满级=100;stats = base + growth×(level−1)
        sources: []          # ★空 = 裸装无专精 baseline。扩展全进这里:
        #  - { kind: weapon, base: 12, attr: 力量 }
        #  - { kind: armor,  defense: 40 }
        #  - { kind: affix,  stat: 法抗, value: 15 }   # 加法
        #  - { kind: set,    mult: { 火: 0.30 } }       # 乘区
        #  - { kind: passive, ... }  - { kind: spec, ... }  - { kind: buff, ... }
        skills_override: null         # 默认用 starting_skills;可覆盖测特定配招
        pos: { row: FRONT, slot: 0 }
        policy: { kind: heuristic, target: lowest_hp, mp_aware: true, defend_below: 0.0 }
  enemy:
    units:
      - ref: forest_goblin   # 直接吃现有 encounter 实体
      # 或 inline,同上格式;敌方 policy 逐 encounter 编写(=设计杠杆,见 §6)

# ── 跑批 ──
run:
  iterations: 1000
  seed: 0                  # 固定→可复现;或 seed_sweep 跑分布
  max_turns: 50            # 超时判负(防互锁)

# ── sweep:扫任意一个入口出曲线(定 K/cap 的主力姿势)──
sweep:
  param:  knobs.armor_K           # 也可 sides.player.units[0].level / .sources[...] 等
  range:  { from: 5, to: 60, step: 5 }
  metric: [win_rate, ttk_median, hp_remaining_median]
```

- **baseline = `sources: []`**:裸装无专精不是特例,是空列表自然落出的那一档,天然成为测量零点。
- **新增 build 维度 = 往 `sources[]` 加一个 kind**:INPUT 结构与模拟器核心都不动。

---

## 5. OUTPUT(指标)

**A. 单场战斗指标(绝对)**
```
win_rate
ttk                  { mean, median, p10, p90 }
hp_remaining_on_win  { median, p10 }          ★"将将击败"量化(目标中位 <20%)
damage_by_source     { 每技能: raw vs final }
mitigation_breakdown { 每单位: 挡掉多少 / 占比 }   # 验证抗性梯度真生效
dps / ehp                                       # 反推回比值空间核对
```

**B. 相对 baseline 的功率指数(★裸装基线的真正用途)**
```
offense_index = 本 build DPS / 同职业 baseline DPS
defense_index = 本 build EHP / 同职业 baseline EHP
power_index   = offense × defense
```
内容/墙全用 baseline 表达:"墙=baseline×1.2"、"套装=+35% 功率"。

**C. 边际归因(扩展位,以后做)**:`sources[]` 非空时,每个 source 各贡献多少功率("这把武器 +18% 攻")。output 结构预留,等真有装备/专精再实装。

---

## 6. 行动准则 / 双方 AI(policy 是一等输入)

胜率/TTK 只在"某出招策略"下有意义。AI 蠢则数据蠢。故 policy 可插拔,**每回合本质 = 给可选行动打分、选最高**;那个打分函数 = "准则"。

**默认 heuristic 准则(从高到低):**
1. 保送人头(本回合能击杀 → 优先,杀掉=减少后续挨伤)。
2. 集火:`lowest_hp`(最快摘人)| `highest_threat`(先掐最高输出)—— policy 参数。
3. AOE 阈值:命中 ≥N 个才放群体。
4. 续航:HP < 阈值则防御/治疗。
5. 资源:尊重蓝耗/CD,留爆发别 overkill。

**三档 policy(同"先基础后扩展"套路):**
- `scripted`:固定出招表 —— 回归/边界测试;**也是 LLM 建议线的承载体**(§8)。
- `heuristic`:上面这套 —— **默认档**,realistic+便宜+透明。
- `search`:浅层前瞻/minimax —— **扩展位,本 slice 不做**,以后测 build 操作天花板。

**双方角色不对称:**
- **玩家侧 policy = "打得好的真人"代理**(真玩家练几次会变强);若按蠢人建模会把内容调得过软。
- **敌人侧 policy = encounter 设计的一部分**("专点后排""血 30% 狂暴")——**怪的行为本身是不靠数值的难度旋钮**,逐 encounter 编写。

**白送诊断:策略胜率差。** 同一场在 naive/heuristic/search 下各跑,看胜率差:naive 低但 search 高 = 高度吃操作(技巧表达强);两者都 ~50% = 纯数值检查。**这就是"这场仗奖励技巧还是只奖励数值"的量化读数**,接上"将将击败的险从哪来"。

---

## 7. 方差源(本 slice 新实装的引擎小改)

确定性回合制下,"将将击败、再来一次"的循环无法靠重复(同 build 同结果)。需要一个**方差源**,把"险胜"变成概率胜(输几次再一丝血过)。

- 在真出伤/减伤路径加 **暴击(crit_chance/crit_mult)** 与/或 **±伤害浮动带(damage_variance)**,受 `knobs` 控制(默认 0 = 关闭,行为同现状)。
- 实装在真引擎(非模拟器私有),保证单一事实源;模拟器只是把旋钮设非 0。
- **取舍待用仪器定**:暴击 vs 浮动带、幅度多大,留到 §9 校准时扫。本 slice 只建机制 + 默认关。

---

## 8. LLM 的角色(设计时人工步骤,非跑批循环)

**靠谱,但只在一个角色:LLM=假设生成器,模拟器=裁判。**
- LLM 擅长**创造性提候选打法 / 阴间 combo**(新技能/新 build 时)。
- **LLM 的数字一律不可信**;把它提的线**编成 `scripted` policy 喂模拟器**,拿精确 win%/TTK。
- 零新架构:LLM 线 = `scripted` 槽里的一个值。顺带是**退化 combo 探测器**(LLM 线碾压 heuristic → 该削)。
- **手动、设计时用**,不塞进跑批循环(慢/不确定/贵)。

---

## 9. 本 slice 范围与交付物

**✅ In scope —— 仪器:**
- 无头批量模拟器:跑真引擎战斗 N 次,输出 §5.A + §5.B。
- INPUT §4:`class+level+sources(可空)` + heuristic/scripted policy + knobs + scale + run + **单参数 sweep**。
- 方差源机制(§7,默认关)。

**✅ In scope —— 用仪器做的眼前校准(交付数值初值):**
- **UC2:100 级等级 sweep** → 定 **护甲 K** 与 **抗性 cap** 初值(看 EHP/TTK 曲线落在 §2 目标带)。
- **UC1:各职业 ≤10 级 baseline 体检** → TTK / 对单 / 对群,守住"前期各职业都流畅"。
- 站位:**只建模当前机制深度**(摆 row/slot,`resolveTargets` 已处理 AOE/可达性)。完整站位系统是未来独立 feature,模拟器届时自动继承,**本期不预造**。

**⏸ Out of scope(扩展位,留接口不实现):**
- `search` policy、矩阵跑(build×encounter 表)、§5.C 边际归因、LLM 集成(=scripted 用法)。

**⏸ Future(独立 feature,用本仪器驱动,零仪器改动):**
- 技能组 / 被动 / 专精 / 装备 / 新怪的**内容设计**(全是 `sources[]` 新 kind 或 enemy-config,核心不改)。
- 完整站位 / lane 射程 / 宠物(见 `docs/future-positional-pets-combat-running-notes.md`)。
- 元素真实内容 + rider。

---

## 10. 开放问题(实现期定,不阻塞结构)

- 模拟器形态:独立 JUnit 入口 / debug REST endpoint / CLI?(倾向先 test 入口,跑批快、可断言。)
- `power_index` 的 offense×defense 合成式是否够用,还是分开看两轴更可读。
- 方差源先做暴击还是浮动带(留到校准扫)。
- baseline DPS/EHP 的精确测法(对固定参照假人 N 场取均值?还是闭式估算?)。
