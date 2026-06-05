# 下一轮 Prompt — Chapter 1 已收官,选下一程:内容设计轮 / Ch2 / #3 收尾

> `master`:后端绿(全量 `mvn test`)、前端 `npm run build` 通过。
> 本轮(2026-06-05)产出:**#7 专精天赋树已合并(`9d32979`feat;spec `4326265`+§12 迭代、plan `8efd75a`;Codex run 全程 `docs/superpowers/codex-runs/feature7/`)—— Ch1 7/7 全部收官。**
> #7 **改向母文档 §1.6/§1.10**(树属于专精、天赋点+灵魂球两货币;详见 spec §1+§12 与 memory `project-chapter1-decomposition`)。

## 团队工作流(沿用,详见 memory `team-roles-codex-workflow`)
- **用户只在头尾出现**:brainstorm 定需求(头)+ 真人 verify & approve(尾)。中间 Claude orchestrate Codex,不当传话筒。
- **Claude = orchestrator**:brainstorm → 写 spec →`codex review`→ 写 plan →`codex review`→`codex exec` 派实现 → review diff(独立跑 mvn/npm 验,别只信 Codex 报告)→ 报用户 verify → approve → **Claude 提交(显式圈文件,别扫残留)**。
- **Codex = headless 实现层**:`codex exec --sandbox workspace-write --cd /c/workplace/epic -o <绝对路径file> "$(cat <绝对路径prompt>)" </dev/null`。
  - ⚠️ **`cat`/`-o` 用绝对路径**:Bash 工具 cwd 会从上条命令(如 `cd frontend`)残留,相对路径会喂空 prompt → Codex 空跑。
  - ⚠️ Codex 沙箱内 `npm run build` 会 `spawn EPERM`(挡 esbuild 子进程),**前端 build 由 Claude 沙箱外验**;Codex 只编译校验 .vue。
  - **plan 必含 5 件**:验收(含不做什么)/ 修改边界 / 验证方式 / 隐含产品偏好 / 现有代码约束。每 feature 落 `docs/superpowers/codex-runs/<feature>/`。
- 记忆见 `~/.claude/.../memory/`(新增 `feedback-no-filler-text` 别写"无可用操作"废话、`reference-engine-recalculate-list-refs` 引擎 List 引用坑)。

## Ch1 进度:7/7 全部合并 ✅
1–6 略(见 memory `project-chapter1-decomposition`)。**7 专精天赋树**(`9d32979`):天赋点(等级派生)点专精多叉树 + 分型灵魂球(debug 给)插技能槽进化;统一 `Passive.ownedSpecs` 聚合层 + stat_mod 残留清理;`TalentTree{root,unlocked,evolved}`+`OrbPouch`;`talents/index.yaml`+全局进化索引;override 追溯重推;球解耦(进化只在槽点亮时生效,evolved 永久→重学免费);专精+天赋合并单一入口(pending 自动弹);隐式被动(stat_mod)vs 显示被动(skill_patch/handler 进列表只读)。

## 起手(下一轮)= 三选一,先 brainstorm 定方向
- **A. Ch1 内容设计轮(推荐,把空槽填真)**:Ch1 七个 feature 全是**框架+demo 夹具,内容真空**。这轮填真:真技能升级树节点/数值(`talents/<spec>.yaml`,现仅法师元素 demo)、真被动(`passives/*`,现仅铁壁/余烬/嗜血 demo)、真专精分类与数值(`specializations/<class>.yaml`,现法师 demo)、`applySkillLevelCurve`(charLevel→skillLevel 曲线,仍 no-op)。**强数值/可玩性设计,需大量真人调试**——`debug.set_level`/`grant_orb`/`grant_passive`/`set_skill_level` 脚手架都在,balance-check / sim 包可用。
- **B. Chapter 2 装备系统**:装备随机词缀 + "改写技能"高级词缀(#7 spec ModifierChain 已留装备 patch 源空槽)。另起 roadmap 章节 brainstorm。
- **C. #3 收尾遗留**:prompt 原列的"**装备三抗词缀(items.yaml flat % 三抗)**"一直没做——想让"穿法抗戒指打火法怪"手感落地这块仍 open(零公式,存%/显示%,负抗=易伤)。单独小轮可做掉。

## 仍开放的空槽 / 延后项
- `applySkillLevelCurve(entityId)`(`recalculate_hooks.js`)no-op —— 内容轮填。
- 装备 patch 源(`Skill.resolveSpec` 留的空槽)→ Ch2。
- 灵魂球**真供给**(特殊词缀灵魂怪掉落)→ Ch3(Ch1 全程 debug 给球)。
- 洗点 money sink 收费 → Ch5(Ch1 免费洗)。
- 怪物种族/子种族系统(战斗状态栏现"种族+Lv.1"占位)→ `docs/superpowers/specs/2026-06-03-monster-race-subrace-design.md` 那轮。
- **结构性技能改写**(弹射/追击)→ 仍走 bespoke JS,不进 patch 管道(母文档 §1.2)。

## debug 脚手架(verify 用,正式发布前可删,均有注释标注)
`debug.set_level`(`/api/debug/level`)、`debug.grant_orb`(`/api/debug/orb`)、`debug.grant_passive`、`debug.set_skill_level`。

## 非阻塞 follow-up(记账,随手清)
- **prod 登出入口缺失**:logout 只 `import.meta.env.DEV` 可见(右下角"退出角色"),正式发布前给 logout 常驻家(设置面板)。
- **`ActionPanel.vue` 死文件**:SnapshotRenderer 不再用,确认无引用可删。
- BL-013 退役 `damage_calc.js`(sim harness 仍 load,删前圈清)。BL-011 animation 非空校验。BL-014 元素抗进装备池。BL-015 角色面板抗性块 + 伤害数字按类型上色。
- #7 `talent_respec` 用了 in-place clear + Codex 的 restore 双保险(belt-and-suspenders,功能正确),将来想清爽可二选一(见 `reference-engine-recalculate-list-refs`)。

## 不要扫进 commit 的工作区残留
`AGENT.md`、`doc/`、`set-env.ps1`、`frontend/src/assets/`、`docs/superpowers/{plans,specs}/2026-05-2[78]-*` —— 一直 untracked,非任何 feature 产出,别圈进提交。
