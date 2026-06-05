# Ch1 内容轮 · 经验来源 + 经验条(可玩验证链路)— 设计文档

**日期:** 2026-06-05
**性质:** 数值骨架(`2026-06-05-ch1-content-numeric-backbone-design.md`)的**可玩化补丁**。骨架做完后 XP 曲线"有公式没来源、没 UI",无法玩着验。本轮补最小、前向兼容的两块:**怪死给经验** + **经验条**,让"打怪→涨经验→升级→技能等级随之爬升→满级封顶"端到端可测。

---

## 1. 背景与范围

骨架轮交付了 `xpForLevel`(指数曲线)+ `gain_xp`(多级/cap)+ `applySkillLevelCurve`,但:
- `action.gain_xp` 无任何触发者(怪死不发经验);
- 无经验条 UI(状态栏只有等级数字)。

故 XP 链路全程无法人验。本轮补齐来源与显示。

### 本轮做(In)
1. `progression.yaml` 加 `xp_reward { base, exp }`;`Progression.xpReward(monsterLevel)`。
2. 敌方单位死亡 → 给本场玩家发 `action.gain_xp(reward)`(新 handler)。
3. 经验条加入玩家状态栏。
4. 怪可带 **`xpReward` 覆盖**(显式 > 默认曲线),作未来扩展唯一落点。

### 本轮不做(Out,记 backlog)
- 怪**难度/精英/剧情**经验倍率 → 依赖怪物难度分层(Ch3)。
- 按**种族**给经验(哥布林少/龙多)→ 依赖怪物种族系统(Ch3,`2026-06-03-monster-race-subrace`)。
- **任务经验**来源 + **节奏分段**(前/后期靠刷怪、中期靠任务推主线)→ 依赖任务系统(Ch6 活动轮)。
- 这些都通过本轮留的两个接缝接入,不返工:① 怪 `xpReward` 覆盖(难度/种族/精英/剧情);② `action.gain_xp` 通用入口(任务直接调)。

---

## 2. 经验奖励曲线

- 怪经验 `reward = round(base × 怪等级^exp)`,默认 `base=14, exp=0.9`(< 升级开销指数 1.5,故"每级需击杀只数"随等级**上升**)。
- 每级需击杀同级怪 = `xpForLevel(L)/reward(L) = (100/14)×L^0.6 ≈ 7.1×L^0.6`:

| 等级 | ~每级击杀 |
|---|---|
| 1 | ~7 只(约 2-3 场) |
| 10 | ~28 |
| 50 | ~75 |
| 100 | ~113 |

- **每只怪单独结算**(`combat.unit_death` 每次死亡各发一份),非按场。
- `base`(整体快慢)/`exp`(后段变肝斜率)放 `progression.yaml`,随时调。
- 怪现为 Lv.1 占位 → 每只 `round(14×1^0.9)=14` 经验;真怪等级(Ch3)来了自动缩放。

### progression.yaml 追加块

```yaml
xp_reward:
  base: 14
  exp: 0.9
```

`Progression.xpReward(monsterLevel) = Math.round(base × monsterLevel^exp)`。

---

## 3. 怪死给经验(接缝)

新 handler `mods/base-rules/handlers/combat/xp_reward.js`,监听 `combat.unit_death`:

- 取 `deadId`;**仅当死者 `hasTag("enemy")`** 才给经验(玩家/宠物死不给)。
- 怪等级 = `CombatantMeta.level`(无则 1)。
- `reward = CombatantMeta.has("xpReward") ? meta.getInt("xpReward") : Progression.xpReward(monLevel)`(显式覆盖 > 默认曲线)。
- 找本场玩家:`store.getByTagAsList("combat:"+combatId)` 中 `hasTag("player")` 的实体。
- `engine.fire("action.gain_xp", {playerId, amount: reward})`。

**前向兼容点:** `start_combat.js:85` 造怪时,若 `enemyDef.get("xpReward") != null` 则 `metaComp.set("xpReward", ...)`。Ch3 精英/种族模板只需在 encounter/生成器里写 `xpReward`(或难度倍率算出的值),无需改 handler。

**触发语义:** 敌人无 `LifeState` → `death_check.js` 在 `hp_zero` 直接 fire `combat.unit_death`(每只一次);玩家有 LifeState(downed/dead),不进 enemy 分支。reward 每只一次,无重复。

---

## 4. 经验条(UI)

`status_bars.js` 在"等级/职业"条之后、"生命"之前,加一条经验进度条:

- 仅当实体有 `Experience`:`xp = Experience.xp`,`need = xpForLevel(Experience.level)`,满级(level≥cap)时 `need = xp`(显示满格)。
- `engine.newStatusBar("xp", "经验", xp, need, xpColor, 0)`,`xpColor` 读 `Colors.neutral`(金 `#ffcc33`)fallback `#ffcc33`。
- 快照驱动,前端 StatusBars 自动渲染,无前端改动。

---

## 5. 验证

- **单元:** `Progression.xpReward(1)=14`、`xpReward(10)=round(14×10^0.9)=round(111.something)`(断言精确值)。
- **集成:** 造带 `player` tag+`Experience` 的玩家 + 带 `enemy` tag+`CombatantMeta` 的怪,二者挂 `combat:c1`,fire `combat.unit_death(deadId=怪, combatId=c1)` → 断言玩家 `Experience.xp` 增加 reward;`xpReward` 覆盖时取覆盖值。
- **后端** `mvn test` 全绿;**前端** `npm run build` 通过。
- **手动(关键):** `./start.ps1` → 新建角色 → 打一场遭遇战 → 看经验条涨 + 够了升级 + 技能等级随之变 + 状态栏等级 +1。

---

## 6. 下一步

实现(TDD)→ 自验 → 真人 verify。backlog(怪难度/种族/精英/剧情 XP、任务 XP、节奏分段)记入 prompt.md 与 roadmap Ch3/Ch6 输入。
