# 下一轮 Prompt — Ch1 内容设计轮:数值骨架 + XP 链路已落,继续填内容

> `master`:后端绿(全量 `mvn test`,219)、前端 `npm run build` 通过。
> 本轮(2026-06-05)产出:**Ch1 框架 7/7 收官后,开"内容设计轮",先横切数值骨架 + 补 XP 可玩链路 + 一串真人 verify 修正,全部已合并并真人玩测通过。**

## 团队工作流(沿用,详见 memory `team-roles-codex-workflow`)
- **用户只在头尾**:brainstorm 定需求 + 真人 verify & approve。中间 Claude orchestrate Codex,不当传话筒。
- **Claude=orchestrator**:brainstorm→spec→`codex review`→plan→`codex review`→`codex exec` 派实现→review diff(独立跑 mvn/npm,别只信 Codex 报告)→报用户 verify→approve→Claude 提交(显式圈文件)。
- **Codex=headless 实现**:`codex exec --sandbox workspace-write --cd /c/workplace/epic -o <绝对路径file> "$(cat <绝对路径prompt>)" </dev/null`。
  - ⚠️ `cat`/`-o` 用绝对路径(Bash cwd 会残留)。⚠️ Codex 沙箱 `npm run build` 会 EPERM,前端 build 由 Claude 沙箱外验。**⚠️ 本轮发现:Codex 沙箱 `.git` 只读,无法 commit——它写完工作区,由 Claude review 后提交。**
  - plan 必含 5 件(验收含不做什么/边界/验证/产品偏好/代码约束)。每 feature 落 `docs/superpowers/codex-runs/<feature>/`。

## 本轮做了什么(2026-06-05)
- **roadmap 更新**(`2026-05-29-game-roadmap-design.md` §5/§7):Ch1 框架收官,进内容设计轮。
- **数值骨架(横切,spec `2026-06-05-ch1-content-numeric-backbone-design.md`,Codex run `codex-runs/ch1-numeric-backbone/`)**:新建中央调参板 `mods/base-rules/progression.yaml` + `Progression` 懒加载库;`xpForLevel` 指数化(`round(100*L^1.5)`,cap100,`gain_xp` 多级+封顶);`applySkillLevelCurve` 填真(据技能 `tier`+当前人物级派生写全部 `known[i].level`,主动+被动);`debug.set_level` cap clamp + 在 `applySpec` 前调曲线;技能 `tier`/`level_curve` 约定;进化成本缺省读 `orb.cost_per_evolution`(修 missing→0 免费 bug)。测试类 `ProgressionBackboneTest`。
- **XP 可玩链路(spec `2026-06-05-ch1-xp-source-and-bar-design.md`)**:`combat/xp_reward.js` 敌方死→给玩家发经验(`reward=round(14*怪等级^0.9)`,怪 `CombatantMeta.xpReward` 可显式覆盖=难度/种族/精英/剧情唯一落点);经验条进角色面板(`CharacterStatsTab.vue`,HP/MP 下,真进度条)。
- **真人 verify 修正一串**:主动技能列表显示 LvN;火球初始技能改 `tier:smooth`(原误标 standard 20 级才长);光击阵补 `level_scaling`;战斗指令栏技能名/描述/范围预览跟随进化(火球→炎爆,一排 AOE);**修了引擎 `pattern`[rowIdx,slot] 与前端 `aoeOffsets`[row,col] 轴序转置 bug**(对称十字一直没暴露)。

## 锁定的数值(详见 memory `project-xp-leveling-design`)
等级=尺子;XP `100*L^1.5` cap100;技能等级 tier 派生(smooth start1/per2、standard 20/3、chunky 50/5,cap10);缩放预算×5;球成本1;怪经验 `14*L^0.9`。全在 `progression.yaml`,热重载可调。

## 起手(下一轮)= 三选一,先 brainstorm 定方向
- **A. 继续 Ch1 内容(把空槽填真,推荐)**:骨架/链路已通,现在填真内容——各职业**专精树**(现仅 `specializations/mage.yaml`)、**天赋树节点/数值**(现仅 `talents/elementalist.yaml` 法师元素 demo)、**真被动**(现仅铁壁/余烬/嗜血 demo)、各技能 `tier`/`level_scaling` 真数值。强数值/可玩性设计,需大量真人调试(脚手架 `debug.set_level`/`grant_orb`/`grant_passive`/`set_skill_level`、balance-check/sim 包都在)。建议**纵切单职业(法师)到底**先验一条完整体验,再 clone。
- **B. Chapter 2 装备系统**:随机词缀 + "改写技能"高级词缀(`Skill.resolveSpec` 留了装备 patch 空槽)。另起 roadmap 章节 brainstorm。
- **C. #3 收尾遗留**:装备三抗词缀(`items.yaml` flat % 三抗,零公式存%/显示%、负抗=易伤)。单独小轮可做掉。

## Backlog / 延后项(本轮新增,记 memory `project-xp-leveling-design`)
- 怪经验按**难度/精英/剧情**倍率、按**种族**(哥布林少/龙多)→ 依赖 Ch3 怪物难度/种族系统,走 `CombatantMeta.xpReward` 钩子。
- **任务经验**来源 + **节奏分段**(前/后期靠刷怪、中期靠任务推主线)→ Ch6 活动/任务轮。
- **球技能**(学习球里携带的技能,而非只进化已有槽)。
- `applySkillLevelCurve` 单技能功率走指数缩放(现线性叠加)。
- `passive_budget` 接入 sim 自动校验(现只定义常量)。
- 进化改 `targeting` 的范围预览已修(轴序),但走的是"node 才 resolve"的特化路径;若将来普通技能也需 resolve 预览,再一般化。
- 之前列的:`applySkillLevelCurve` 已不再空;装备 patch 源→Ch2;灵魂球真供给→Ch3;洗点收费→Ch5;怪种族系统→`2026-06-03-monster-race-subrace` 那轮。

## debug 脚手架(verify 用,发布前可删,均有注释)
`debug.set_level`(`/api/debug/level`)、`debug.grant_orb`(`/api/debug/orb`)、`debug.grant_passive`、`debug.set_skill_level`(注:本轮后 `set_skill_level` 会被派生曲线覆写,仅临时覆盖)。

## 非阻塞 follow-up(记账,随手清)
- `StatusBars.vue` 是**死组件**(无人 import,角色面板用 `CharacterStatsTab.vue`)——确认后可删(同 `ActionPanel.vue` 死文件)。
- prod 登出入口缺失(logout 仅 DEV 可见);BL-013 退役 `damage_calc.js`;BL-011 animation 非空校验;BL-014 元素抗进装备池;BL-015 角色面板抗性块 + 伤害数字按类型上色。
- #7 `talent_respec` in-place clear + Codex restore 双保险(功能正确,将来想清爽可二选一,见 `reference-engine-recalculate-list-refs`)。

## 不要扫进 commit 的工作区残留
`AGENT.md`、`doc/`、`set-env.ps1`、`frontend/src/assets/`、`docs/superpowers/{plans,specs}/2026-05-2[78]-*` —— 一直 untracked,非任何 feature 产出,别圈进提交。
