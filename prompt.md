# 下一轮 Prompt — Ch1 / Feature #2 收尾:实测第二轮 bug 全修完,待提交+收尾分支

> 分支 `feature/ch1-f2-attribute-table`(**未合并**),后端 **135/135 绿**,前端构建通过。
> Feature #2(两层属性表)+ 批次 A/B(武器系统#3 / 职业技能#5 / 创建页#6)**都已完成并提交**。
> 本轮做的是**实测第二轮**发现的一串引擎 bug,**全部修完且加了回归测试,但还没 commit**(见下"未提交改动")。

新开会话贴这段:

```
继续 feature/ch1-f2-attribute-table 分支。读 prompt.md 全文。
本轮工作区有一批未提交的实测修复(引擎 base 快照完整性 + 两个技能动画),后端 135/135 绿。
第一步:把这批未提交改动按主题分几个 commit 提交(列表见 prompt.md「未提交改动」)。
然后用 finishing-a-development-branch 收尾整个分支(批次 A+B+实测两轮一起)。
之后才是 Feature #3「伤害类型/抗性」。
```

---

## 本轮根因大主题:**base 快照完整性**(别再踩)

ModifierChain 的 base 快照只该装「modifier 会重建的派生属性」;**结构性/运行时状态(标签、Position、EquipmentSlots)绝不能被 base 快照机制清掉或拉回**。本轮一连串"属性暴涨/战吼退战斗/换装叠属性"全是这一类问题的不同表现:

1. **`restoreBaseState` 不再复位标签**(`ModifierChain.java`)。原来每次 recalc 都把实体标签重置回 base 快照,而 base 是进战斗前拍的(没有 `combat:xxx`)→ 战斗中任何触发 recalc 的技能(战吼施 buff)都把 combat 标签抹掉 → 单位"退出战斗" + buff 泄漏 + 跨场滚雪球。标签是运行时状态,modifier 从不动标签,故不复位。
2. **`setBaseSelective` 是「整体替换」不是「更新单项」**——它 `baseState.clear()` 后只装传入的组件。`movement.js` 原来每走一步调 `setBaseSelective(["Position"])`,把 PrimaryStats/CombatStats 等**全部踢出 base** → 之后 recalc 不再复位它们 → 累加型 modifier(职业+11/装备)每次 recalc 无限叠加(**"移动后换装/战斗属性暴涨"的真凶**)。
3. **新引擎 API `engine.updateBase(entityId, componentType)`**:只把单个组件的当前值刷进现有 base、保留其余。`movement.js`(Position)和 `equip.js`(EquipmentSlots)现在都改用它。装备槽同理:原来 `slots.set` 后被 recalc 的 restoreBaseState 拉回 null(武器伤害不变的真凶之一)。
4. **`addModifier` 按 id 幂等**(`ModifierChain.java`):同 id 重复注册=替换而非追加(防 entity.loaded/重复注册导致累加型 modifier 叠加)。
5. **`entity.loaded` 重载先复位再快照**(`recalculate_hooks.js`):持久化存的是 modifier 应用后的"脏"属性值;重载前先把可重建的属性组件复位成 schema `base_components` 默认值(**保留 Position**,按职业还原 `weaponAttr`),再 setBaseSelective 快照 → 累加型 modifier 重启不翻倍。坏档**重启自愈**。
6. **武器最终伤害**已在 `derived_stats.js`:装备武器时读 `EquipmentSlots.weapon → ItemStats.weaponAttr+base`,`攻击=⌈B×(1+裸属性×倍率/100)⌉`;无武器走物理强度占位。用户已确认手感 OK(B 全 5、力量×2)。

> 还有个 stale-build 大坑教训:**旧后端 JVM 还活着就会一直用旧字节码服务**,改了 Java 重编也不生效(排查耗了很久)。现在 `EngineBootstrap` 启动会打一行 `=== EPIC BUILD: base-snapshot 修复已加载 ...` banner 确认跑的是新字节码;`start.ps1` 也加了按端口 8080/5173 杀残留 + `mvn clean` 全量重编 + 轮询等 banner。

## 本轮两个技能动画修复

- **毒镖无动画无伤害数字**:`poison_dart.yaml` 缺 `animation:` 块(数据驱动技能的动画由 `present()→resolveAnimation` 读该块;`damage_number` 是动画原语)。照 `fireball`(同 effect/单体/数据驱动)补了飞射→命中→抖动→伤害数字→中毒图标。
- **贯穿射线死亡结算顺序错**:`piercing_ray.js` 原来先 `dealDamage`(立即触发死亡级联入队)再推 beam 动画 → 死亡排在动画前。改成 `applyDamage` 只扣血 → 推 beam 事件 → **最后** `fireDamageDealt`(死亡级联),与所有 present() 路径一致。

## 未提交改动(本轮,先分主题 commit)

**引擎核心(base 快照完整性)** — `ModifierChain.java`(updateBase + 去标签复位 + addModifier 幂等)、`ModifierChainService.java`(updateBase)、`ScriptRuntime.java`(暴露 updateBase)、`EngineBootstrap.java`(build banner)、`recalculate_hooks.js`(entity.loaded 复位)、`movement.js`(改 updateBase)、`equip.js`(改 updateBase + recalc 后重取 slots 引用)。
回归测试:`BaseSnapshotIntegrityTest`、`ReloadInflationBugTest`、`CombatLifecycleInflationTest`、`EquipWeaponBugTest`、`WarCryCombatBugTest`。

**技能动画** — `poison_dart.yaml`(加 animation)、`piercing_ray.js`(死亡顺序)、`golden/poison_dart.json`(已重生成)。
回归测试:`PoisonDartAnimationTest`、`PiercingRayOrderingTest`。

**创建页 UI** — `CharacterCreate.vue`(删职业按钮下的描述、按钮等宽 96×44,描述仍在右侧预览面板)。

**工具** — `start.ps1`(杀残留进程 by 端口 + clean 重编 + 等 banner)。

> 另有历史未跟踪文件:`doc/`、`docs/superpowers/plans|specs/2026-05-2x-*.md`、`set-env.ps1` —— 按需决定是否纳入提交。

## 关键技术事实(沿用,别重踩)

- **ModifierChain 升序应用**(priority 小先跑);`derived`=300 最后跑读累加后属性、并 SET attack/speed/maxHp(覆盖前面加性的同字段——所以装备的 `CombatStats.speed` 加成会被 derived 的 speed=敏捷 覆盖,注意)。
- **base 快照**:只装派生属性;结构性状态(标签/Position/EquipmentSlots)用 `engine.updateBase` 单项刷新,**别用 setBaseSelective 当"更新单项"**(它清空重建)。
- **`engine.clearChain(entityId)`** 实体移除时清缓存;`engine.updateBase(id, comp)` 单项刷 base。
- **跨 handler 全局函数可见**(同 GraalJS 上下文):registerDerivedModifier / registerEquipmentModifier。
- **golden 重生成**:删 `golden/<skill>.json` → 跑 SkillFidelityTest 自动 capture → 再跑确认绿。数值/动画改了就要重生成。
- **战吼当前无持续回合数**:整场战斗在身上(无 `remaining`/无 round_end tick,与 cursed/burning/poison/defending 不同)。用户问过,暂未决定要不要加时限——若下轮要改,照 cursed.js 的 round_end 递减 remaining 模式加即可。
- **数值基调**:maxHp=30+体质×10(SET 幂等,底盘 30);物理强度=主属性×倍率(力量×2 其余×1);法术/精神强度=智力/意志;全程 Math.ceil。

## 收尾 & 下一步

1. 先提交本轮未提交改动(分主题)。
2. `finishing-a-development-branch` 收尾整个 `feature/ch1-f2-attribute-table`(Feature #2 + 武器/技能/创建页 + 实测两轮)。
3. 之后是 **Feature #3「伤害类型/抗性」**(母文档 §1.9 + F2 spec §8 延后项)。本批已把部分 Ch2 武器 + 伤害公式提前,#3 接手注意接缝。
