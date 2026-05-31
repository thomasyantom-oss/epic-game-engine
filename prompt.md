# 下一轮 Prompt — Ch1 / Feature #2 收尾批次:实测 bug 修复 + 武器系统/技能/创建页

> 分支 `feature/ch1-f2-attribute-table`(**未合并**),后端 **108/108 绿**。Feature #2 核心(两层属性表)早已完成;本批是**实测后发现的 6 个问题**,前 3 个已修,后 3 个待建(其中武器/技能的设计已和用户敲定大半,**还差 3 个公式确认**,见下)。

新开会话贴这段:

---

```
继续 feature/ch1-f2-attribute-table 分支上的「实测修复批次」。读 prompt.md 全文。

走 subagent-driven-development(每个 task 派 fresh subagent TDD,task 间做 spec + code-quality 两段 review)。
后端测试基线 108/108 绿,每步后保持全绿。

已完成(批次 A,已提交+复核):#2 敌人重进 1 血、#4 位置不持久/传送、#1 属性面板显示新属性。
待做:#3 武器系统 → #5 职业技能(依赖#3)→ #6 创建页 UI。

#3/#5 开建前:prompt.md 末尾「待拍板的 3 个公式」需要用户先答(①武器伤害是否力量×2 ②初始武器基础值B ③技能加值吃裸属性还是强度)。
若用户已在新会话里给了答复就照答复走;没给就先问这 3 个,再用 brainstorming 敲定诅咒/背刺/震波细节,然后 writing-plans 写 #3 的 plan。

收尾:全部做完用 finishing-a-development-branch 收尾整个分支(批次 A+B 一起)。
```

---

## 6 个实测问题的状态

| # | 问题 | 状态 |
|---|---|---|
| 2 | 敌人重进遭遇战血量变 1 | ✅ `78b54b4` 修(ModifierChain 缓存未清,已暴露 `engine.clearChain` + endCombat 调用)Approved |
| 4 | 战斗后传送回出生点 / 位置不随重启持久 | ✅ `cd8c986` 修(movement.js 移动成功后 `setBaseSelective(["Position"])`+`persistence.save`)Approved |
| 1 | 属性面板还显示攻防、没新属性 | ✅ `ba7d780`(status_bars.js 加五基础+三强度+先攻;CharacterStatsTab 映射扩展)Approved |
| 3 | 装备/初始武器还是攻防 | ⬜ 待建(设计已敲定大半,见下) |
| 5 | 5 职业没差异化技能(只法师有) | ⬜ 待建(依赖#3,技能 spec 已给) |
| 6 | 创建页太丑 | ⬜ 待建(布局已描述) |

> 还有更早的修复也在本分支:`cd62ac6` 创建角色 pendingPoints 设 0(手动加点路径会挤掉等级成长 modifier,且加到被 derived 覆盖的 CombatStats);`31b837b` 存档位 5→9。

## #3 武器系统 — 已敲定的设计(用户原话提炼)

**两层「武器强度」:**
- **面板物理强度(第一层)** = 主属性值 × 倍率(力量×2,其余×1)。强度系技能/buff 吃它。**已实现(derived_stats.js),不动。**
- **武器最终伤害(第二层)** = 由**手上武器绑定的属性**算,与主属性无关。武器类型→吃的属性:双手剑=力量、匕首=敏捷、法杖=智力、拳套=体质、图腾=意志。
  - 例:法师(主智力,面板物理强度低)拿双手剑,双手剑伤害按他的**力量**算;主力量职业拿好敏捷匕首,只能吃自己(低)敏捷 → 防止用不顺手武器白嫖主属性强度。
- **关键改动**:`CombatStats.attack` 从此 = **当前装备武器的最终伤害**(由武器属性算),不再是面板物理强度算的占位。建议把"读装备武器属性→算 attack"**挪进 `derived_stats.js`**(它 priority 300 最后跑、能拿到装备信息,避免装备 modifier 设 attack 后被 derived 覆盖的顺序冲突);无武器时 fallback 到物理强度占位。
- **装备 modifier 改造**:`mods/base-rules/handlers/equipment/equip.js` 的 `registerEquipmentModifier` 现在硬编码只往 `CombatStats` 加;改成支持**全限定字段** `"PrimaryStats.力量": +N` / `"DerivedStats.物理强度": +N` / `"CombatStats.defense": +N`(解析 `组件.字段`)。`items.yaml` 同步改格式。
- **给每个职业发 5 把初始武器**(双手剑/匕首/法杖/拳套/图腾),用户要逐一测试不同武器在不同职业手上的表现。

## #5 技能 — 用户给的分配 + 数值(逐条实现)

`base` = 武器最终伤害(即 `CombatStats.attack`,装备武器后的值);scaling 加值见各条。`computeDamage`(00_skill_lib.js)要**泛化 scaling**:按 key 名先查 DerivedStats 再查 PrimaryStats(`{力量:0.1}` 读裸力量、`{物理强度:x}` 读强度),全程 Math.ceil。

- **战士**:顺劈斩(cleave)= 武器最终伤害 + 0.1×力量;战吼(war_cry)= 提高力量值 = ⌈0.5×物理强度⌉。
  - ⚠️ 用户想测**无限迭代**(力量→物理强度→战吼加力量→…)。我的预判:**不会爆炸**——ModifierChain 每次 recalc 先 restoreBaseState 再按 priority 跑一遍,单次不迭代到不动点;战吼更可能"加不上或只加一次"。**如实按 spec 实现让用户实测**,再决定要不要改成快照式一次性加值。
- **法师**:火球(fireball)= 基础10 + 0.2×智力,灼烧 debuff 3 + 0.1×智力;光击阵(light_field)= 基础15 + 0.1×智力。(注意:现有 fireball 是 基础8+0.5×法术强度,要按新数值改。智力≡法术强度,数值等价。)
- **盗贼**:毒镖(poison_dart)= 武器最终伤害 + 0.1×敏捷,中毒 3 + 0.1×敏捷;**背刺(新建)**= 单体,武器最终伤害 + 0.2×敏捷。
- **德鲁伊**:诅咒(curse,重做)= 降敌全属性 -2;**体质降低只影响 maxHp 上限,当前 hp 不超过新上限但效果结束回调 maxHp 不补 hp**;主属性 clamp ≥0(不能为负)。贯穿射线(piercing_ray)= 10 + 0.2×意志。
- **护卫**:治愈(heal)= 10 + 0.1×体质;**震波**(由 pulse_wave 改名)= 武器最终伤害 + 0.1×体质。
- 全员保留:攻击(basic_attack)/ 防御(defend)/ 逃跑(flee)。
- 落地方式:职业 schema 加 `starting_skills: [...]`,`select.js` 读取(现在是硬编码只给法师 fireball/light_field,line ~108-128)。

## ⚠️ 待用户拍板的 3 个公式(#3/#5 开建前必须先确认)

1. **武器最终伤害公式**:提议 `⌈B × (1 + 武器属性值 × 倍率 / 100)⌉`。**力量武器要不要也 ×2**(沿用面板倍率)?Claude 倾向 ×2。
2. **5 把初始武器的基础值 B**:提议**统一 B=5**(测试时差异纯来自属性,干净可比);以后再分化。
3. **技能「+0.1力量」吃裸属性还是强度**:Claude 提议按**裸 PrimaryStats**(0.1×力量),并把 computeDamage scaling 泛化成 DerivedStats→PrimaryStats 两级查找,两种都支持。

## #6 创建/选择页 UI(待建)

- **布局**(用户描述):上面一排角色列表;下面选中角色详情——第一行角色名,下方左边列属性+升级加成(growth),右边放头像、头像下写角色描述。
- **头像**:用户选「**预留图片位不填**」——后端下发 portrait 字段、前端预留 `<img>` 占位框,不放图(以后丢图进 assets 即显示)。
- **后端缺口**:`buildCharacterSelectSnapshot`(SnapshotService.java)目前**不下发**职业属性预览/growth/description;需补下发。职业 schema 已有 label/description/growth/modifiers,`Schema.raw()` 可读自定义字段。
- 前端组件:`CharacterSelect.vue` / `CharacterCreate.vue`(用 frontend-design skill)。
- **约束**:这是用户**明确要求改布局**的页面,不受"只改颜色/字体禁改布局"那条记忆约束(那条针对的是另一次配色 redesign)。

## 关键技术事实(沿用,别重踩)

- **ModifierChain 升序应用**(priority 小先跑);`derived`=300 最后跑读累加后属性。
- **持久化存 base 快照**(`getBaseState`)→ 加性 modifier 重载不翻倍;要持久化的字段须在 `setBase` 前写入。Position 现在靠移动后 `setBaseSelective(["Position"])` 同步 base 才不被 restore 拉回。
- **`engine.clearChain(entityId)`** 现已暴露(ScriptRuntime),实体移除时清 ModifierChain 缓存(否则同 ID 复用旧 chain 绑旧 entity)。
- **读 schema 自定义字段**:`classSchema.raw().get("weapon_attr"/"growth"/...)`。
- **跨 handler 文件全局函数可见**(character/ 与 combat/ 同 GraalJS 上下文):registerDerivedModifier / registerEquipmentModifier。
- **golden 重生成**:删 `golden/*.json` → 跑 SkillFidelityTest 自动 capture → 再跑确认绿。火球数值若改,golden 要重生成;且 SkillFidelityHarness 给单位补的 DerivedStats/装备需对应。
- **数值基调**:maxHp=30+体质×10(SET 幂等,底盘 30);法术/精神强度=智力/意志(×1);全程 Math.ceil。

## start.ps1(未提交改动,工作区里)

本轮顺手改了 `start.ps1`(还没 commit):①启动前自检杀残留 `EpicApplication`(H2 单连接,旧实例锁库导致今天启动失败);②前端改后台跑+输出进 `frontend/frontend.log`(不再占用终端切进 vite 界面),Ctrl+C 经 finally `taskkill /T` 连前后端进程树一起清。要保留就 `git add start.ps1` 一起提交。

## 收尾

- 批次 A(#1/#2/#4)+ 批次 B(#3/#5/#6)都在 `feature/ch1-f2-attribute-table` 上,全部做完用 finishing-a-development-branch 收尾整个分支。
- 之后才是 Feature #3「伤害类型/抗性」(母文档 §1.9 + F2 spec §8 延后项);本批的武器/技能其实已把部分 Ch2 武器 + #3 伤害公式提前了,#3 接手时注意接缝。
