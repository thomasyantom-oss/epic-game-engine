# 下一轮 Prompt — Chapter 1 / Feature #3「伤害类型 / 抗性」**开局**

> Feature #2「属性表扩展」已全部完成(Task 1–9 + 4.5),分支 `feature/ch1-f2-attribute-table`,后端 **106/106 绿**,待收尾(合并/PR,见本文件末「Feature #2 收尾状态」)。

新开会话贴下面这段:

---

```
开始 Chapter 1 / Feature #3「伤害类型 / 抗性」。先 brainstorm 再 writing-plans,不要直接写码。

先读:
- 母文档 docs/superpowers/specs/2026-05-30-chapter1-skill-growth-core-design.md
  —— §1.9「伤害类型层」是 #3 的主轴;§2 依赖序(#1✓ #2✓ → #3)。
- Feature #2 spec docs/superpowers/specs/2026-05-31-feature2-attribute-table-design.md
  —— §8「明确延后」表列了 #3 要接手的项;§1 设计原则(尤其 #1 安全/高级属性分治、#5 scaling 吃强度)是 #3 不得违反的约束。

#3 接手的核心(spec §8 + §1):
  1. 减伤结算公式(攻防曲线 / 护甲 / 抗性)—— 替换 #2 留的临时基线
     mods/base-rules/handlers/combat/damage_calc.js(当前是线性 attack - defense)。
  2. 伤害类型(物理 / 法术 / 精神 等)+ 按类型的抗性。
  3. 精神强度的效果:免控判定 / 技能加值结算(德鲁伊施法源)。

#3 不碰(继续延后):暴击/暴伤/命中/穿甲/格挡/闪避数值(→ Ch2 装备 / #6 专精)、
最终武器伤害真公式 + 武器声明吃哪条属性(→ Ch2 装备)、主属性第二层专属加成(→ #6 专精)。

Feature #2 留给 #3 的接缝:
- computeDamage(00_skill_lib.js)已支持 scaling 吃三强度(物理/法术/精神强度,DerivedStats),
  Math.ceil。#3 在此基础上加「伤害类型 + 抗性」结算层。
- 占位 attack = ⌈5×(1+物理强度/100)⌉,是 #2 没有武器时的占位,Ch2 真武器替换 —— #3 设计减伤
  公式时按「最终武器伤害」抽象,别把占位公式焊死。
- damage_calc.js 的线性 attack-defense 是 #2 临时基线,#3 换成正经攻防曲线 / 护甲。
```

---

## Feature #2 完成总结(交棒事实,别重踩)

**全套后端测试 106/106 绿。** 分支 `feature/ch1-f2-attribute-table`,commit 序:
- Task 1–5 + 4.5:见上一轮(派生 modifier、computeDamage scaling、火球迁移、5 职业 schema、Schema.raw()、创建/加载注册派生)。
- **Task 6**(`712b48f`)等级成长改读职业 `growth` 模板往 PrimaryStats 加点(`gained=level-1`,level modifier priority 90 < derived 300 先加点后派生);移除 gain_xp 的 `pendingPoints+3`;`allocate_point` handler 留存但已休眠。
- **Task 7**(`7526246` + review nit `bc2e5af`)敌人 spawn 走同一派生管道:start_combat.js 建 PrimaryStats+DerivedStats+占位 Health/CombatStats → `setBase` → `registerDerivedModifier` → 满血;8 个 encounter YAML 迁移到 `力量/敏捷/智力/体质/意志 + weaponAttr + defense`(换算:体质=max(0,⌈(原hp−30)/10⌉)、敏捷=原attack、defense 保留)。无测试断言需改。
- **Task 8**(`31b837b`)存档位 5→9:真实来源是后端 `application.yaml max-character-slots`(snapshot.maxSlots 下发),三处同步改(yaml 配置 + SessionService @Value 默认 + 前端兜底)。

**关键技术事实(沿用):**
- **ModifierChain 升序应用**(priority 小先跑);`derived`=300 最后跑,读累加后基础属性。
- **持久化存 base 快照**(`getBaseState`)→ 加性 modifier 重载不翻倍;要持久化字段须在 `setBase` 前写入。
- **读 schema 自定义字段**:`classSchema.raw().get("weapon_attr"/"growth")`(Task 4.5 的通用 `Schema.raw()`)。
- **跨 handler 文件全局函数可见**(character/ 与 combat/ 同 GraalJS 上下文):`registerDerivedModifier` / `registerEquipmentModifier`。
- **golden 重生成**:删 `golden/*.json` → 跑 SkillFidelityTest(缺文件自动 capture)→ 再跑确认绿。
- **数值基调**:maxHp=30+体质×10(SET 幂等,底盘 30);占位 attack=⌈5×(1+物理强度%)⌉;法术 scaling 系数 0.5;全程 Math.ceil。

## Feature #2 收尾状态

- Task 9 全量回归(106/106)+ 注释核实 + 本 prompt.md 交棒已完成。
- **浏览器全链路冒烟(5 职业各创建/打一场/升级看成长/存档 9 位)未在上一轮自动跑** —— 自动化已覆盖关键路径(EngineBoot/WorldBootstrap 加载、CharacterFlow 创建、3 个 combat 集成跑真 encounter spawn、DerivedStats 派生)。若要人工确认,起 `./start.ps1` 走一遍。
- **分支尚未合并/PR**:下一轮开始 #3 前(或本轮)用 superpowers:finishing-a-development-branch 收尾 `feature/ch1-f2-attribute-table`。
