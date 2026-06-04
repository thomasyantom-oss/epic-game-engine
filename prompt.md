# 下一轮 Prompt — Ch1 收尾期:挑 #5 被动 / #6 专精 / #7 升级树(或补 #3 装备三抗)

> `master`:后端绿(全量 `mvn test` 通过)、前端 `npm run build` 通过。
> 本轮(2026-06-04)产出:**#4 Skillbook 出战配置已合并**(`0ccb859` 实现 + `9b53a88` 文档),**#3 slice-2 锁定公式/模拟器/tester 也补提交了**(`8a4532f`)。

## 团队工作流(2026-06-04 升级为 Claude orchestrate Codex,详见 memory `team-roles-codex-workflow`)
- **用户只在头尾出现**:brainstorm 定需求(头)+ 真人 verify & approve(尾)。中间不当传话筒。
- **Claude = orchestrator**:brainstorm → 写 spec →`codex review`→ 写 plan →`codex exec` 派实现 → review diff → 报用户 verify → 用户 approve → **Claude 提交**(显式圈文件,别扫残留)。
- **Codex = headless 实现层**:`codex exec --sandbox workspace-write --cd /c/workplace/epic -o <file> "<plan>" </dev/null`;续轮 `codex exec resume --last`;**Codex 不自己 commit**。
  - ⚠️ 实现前先做 workspace-write dry-run 验证 mvn/npm/git 不被 execpolicy `.rules` 挡(可能要 `--ignore-rules`)。
  - **plan 必含 5 件**:验收标准(含不做什么)/ 修改边界 / 验证方式 / 隐含产品偏好(UI密度/文案/空错态)/ 现有代码约束。
  - 每 feature 落 `docs/superpowers/codex-runs/<feature>/`(prompt + Codex 回话)当永久查证记录;`codex resume` 事后查 session。
- 记忆见 `~/.claude/.../memory/`(team_roles / 模拟器解读口径 / 核心公式 / 怪物种族子种族 / chapter1 分解 等)。

## Ch1 进度(7 feature 依赖序)
1. ✅ **#1 技能架构重构**(`dfce6f1`)
2. ✅ **#2 属性表扩展**
3. ✅ **#3 伤害类型/抗性**:slice-1 减伤地基 + slice-2 锁定公式落真默认(PoE 护甲曲线 + 抗性 cap/floor 75/−50 + variance + `combat.mitigation` 事件)+ 数值模拟器包 `com.epic.engine.sim`(REST `/api/sim/reports/*`)+ tester 遭遇战(`forest_goblin` sim_scaling、`mitigation_high_resist_test` 法抗90)。
4. ✅ **#4 Skillbook 出战配置**:`Skillbook` 组件(改名自 `Skills`)`{slots,known:[{base,node,equipped}]}`;通用技能(攻击/防御/逃跑)移出 known、战斗永远发;模态技能书(`Modal.vue` 通用外壳=首租户 + `SkillbookPanel` 主动 tab)+ 右下角常驻菜单 + BattleGrid 指令栏固定 3×2 + 技能子菜单 3×2 + 纵跨取消键(移动/道具前端占位)。`node` 预留给 #7。spec/plan:`docs/superpowers/{specs,plans}/2026-06-03-feature4-skillbook-loadout*`。
5. ⬜ **#5 被动技能**(依赖 1,4):被动轨 + 纯属性被动(走 ModifierChain)+ 特殊效果被动(挂战斗事件专属 JS)。技能书 `被动` tab 现是占位空壳,等这轮填。
6. ⬜ **#6 专精**(依赖 2,4,等级):选专精 + 改主属性 + gating 升级树 + 不可洗。右下角常驻菜单 `专精` 按钮现禁用占位。
7. ⬜ **#7 技能升级树(旗舰)**(依赖 1–6):升级树数据 + 灵魂球材料 + 节点变换 patch + 花钱重置 + debug 给球。技能书主动 tab 每条留了 `树` 接缝入口。skillbook(列表)≠ skilltree(加点)。

## 起手(下一轮)挑一个
- **推荐 #5 被动**:与 #4 衔接最顺(技能书被动 tab 已占好位、ModifierChain 现成),前后端双改但 case 清楚。先 brainstorm → spec → plan → 派 Codex。
- 或 **#6 专精**(影响最大,联动职业/等级/技能,值得先把 design 拍透)。
- 或 **#7 升级树**(旗舰本体,可能再拆)。
- 或 **补 #3 收尾遗留**:slice-2 做了怪物抗性内容(高抗 tester 怪)+ 锁定公式,但 **prompt 原列的"装备三抗词缀(items.yaml flat % 三抗)"没做** —— 若想让"穿法抗戒指打火法怪"手感落地,这块仍 open(零公式,存%/显示%,负抗=易伤)。可单独小轮做掉。

## 非阻塞 follow-up(记账,随手清)
- **prod 登出入口缺失**:#4 把 logout 改成右下角 `退出角色` 按钮但只 `import.meta.env.DEV` 可见;正式发布前要给 logout 常驻家(设置面板最自然)。
- **`ActionPanel.vue` 成死文件**:#4 后 SnapshotRenderer 不再用它,确认无其他引用后可删。
- BL-013 退役 `damage_calc.js`:现已复用 `Skill.mitigate`,且 sim harness 仍 load 它,删前圈清。BL-011 animation 非空校验 vs `fireball_fixed.yaml`。BL-014 元素抗进装备池。BL-015 角色面板抗性块 + 伤害数字按类型上色。

## 已存档的前瞻 design(待排期)
- **怪物种族/子种族/随机 modifier**:`docs/superpowers/specs/2026-06-03-monster-race-subrace-design.md`。怪抗性内容的最终结构家;落地时替模拟器脚手架成长。真做时另起 brainstorm→spec→plan。
- 站位/宠物战斗、控制/豁免/韧性:`docs/future-positional-pets-combat-running-notes.md`(#4 把"移动/道具"指令做成了前端占位,真机制留这条轨)。

## 不要扫进 commit 的工作区残留
`AGENT.md`、`doc/`、`set-env.ps1`、`frontend/src/assets/`、`docs/superpowers/*/2026-05-2[78]-*` —— 一直 untracked,非任何 feature 产出,别圈进提交。
