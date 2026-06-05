# 下一轮 Prompt — Ch1 收官:#7 技能升级树(旗舰本体,最后一块)

> `master`:后端绿(全量 `mvn test` 191 通过)、前端 `npm run build` 通过。
> 本轮(2026-06-04)产出:**#6 专精系统已合并**(`809f283`;spec/plan `docs/superpowers/{specs,plans}/2026-06-04-feature6-specialization*`;Codex run 全程 `docs/superpowers/codex-runs/feature6/`)。分层不可洗里程碑树(`Specialization{path}`)+ R 追溯成长(`effectiveGrowth` 整表替换、`level_growth` 干净 base 重推)+ spec modifier(priority 220 覆写 weaponAttr,不写 base)+ 专属被动走 #5 框架 + 快照 `specialization` 块 + `SpecializationPanel` 三态 + UI 传播(选人界面实时属性/有效职业名/立绘、战斗状态栏职业+Lv、怪物种族+Lv.1 占位)。**demo 法师树(元素/奥术/自然→火/冰/电…),真分类+数值留「Ch1 内容设计轮」。**
> **新增调试脚手架:`debug.set_level`**(`POST /api/debug/level/{token}?level=N`)跳级,verify #6/#7 用;不需要可删(`DebugController` + `leveling.js` 两处,均有注释标注)。

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
5. ✅ **#5 被动技能(框架)**(`7124678`+`0f83580`):父类技能+active/passive 子类(统一 `known`,实体无关)。`Skill.resolveSpec` 合并管道接 dispatch + bespoke 技能,passthrough 保回归。三通路:stat_mod→常驻 modifier(typeId `passive`=70,早于 derived);skill_patch→按 match/patch 改 spec 已声明字段;handler→`mods/base-rules/passives/*.js`+kind 守卫。快照吐 `kind`/`level`、equippedCount 只数主动;前端被动 tab 只读;debug `/api/debug/{passive,skill-level}/{token}`。**node/装备/专精/灵魂球各留空槽**。3 个 demo 被动(铁壁/余烬/嗜血)当夹具。**内容真空,留 Ch1 内容轮填。**
6. ✅ **#6 专精**(`809f283`):分层不可洗里程碑树(`Specialization{path}` 路径而非单值,逐层三选一、向下累积);选专精改主属性(spec modifier 220 覆写 weaponAttr)+ R 追溯成长 + 授专属被动(走 #5 框架);右下角专精按钮已点亮 + `SpecializationPanel` 三态。**给 #7 留的接缝**:`Skill.resolveSpec` 里 `applySpecializationPatches`(读 path 节点 `skill_patches`,demo 空)已落地;`Specialization.path` 就是 #7 要读的钥匙(#7 的升级节点声明 `requires_spec`,反查 path 决定显示/可升;UI 不渲染未解锁的树=天然 gating,后端校验防作弊)。
7. ⬜ **#7 技能升级树(旗舰,Ch1 最后一块)**(依赖 1–6):升级树数据 + 灵魂球材料 + 节点变换 patch(填 `applyNode` 空槽)+ 花钱重置 + debug 给球。技能书主动 tab 每条留了 `树` 接缝入口。skillbook(列表)≠ skilltree(加点)。**门控读 #6 的 `Specialization.path`**(未专精→树不渲染→无升级入口;`requires_spec` 节点反查 path)。

## 起手(下一轮)= #7 技能升级树(Ch1 收官,旗舰本体)
- 依赖 1–6 全就位。**可能再拆**(树数据格式 / 灵魂球材料 / 节点变换 patch / 花钱重置 / debug 给球 / 前端加点 UI)——真做时先 brainstorm 切片。skilltree(加点)≠ skillbook(列表),两套前端+数据形状。
- **接现成空槽/接缝**:① `Skill.resolveSpec` 里 `applyNode(spec, node)`(`00_skill_lib.js`)是 no-op,#7 在此填节点变换 patch(引用可复用变换积木,见母文档 §1.6)。② 升级 = 把 `Skillbook.known[i].node` 从 null 推进到节点 id(数据模型 #4 已留)。③ **门控读 `Specialization.path`**:升级节点声明 `requires_spec`,反查玩家 path;未专精→树不渲染。④ 灵魂球 Ch1 用 debug 给(真供给=Ch3);可仿 `debug.set_level`/`debug.grant_passive` 加 `debug` 给球端点。⑤ 重置 = node 退回 null(money sink 经济留 Ch5,Ch1 只要数据可逆)。
- ⚠️ **仍开放的空槽/接缝**:`applySkillLevelCurve(entityId)`(`recalculate_hooks.js`)no-op,内容轮填 charLevel→skillLevel 曲线;装备 patch 源(Ch2);真被动/技能数值+真专精分类留「Ch1 内容设计轮」。
- Ch1 收官后:**Ch1 内容设计轮**(真技能树节点/被动/专精分类/数值曲线全填)或进 Ch2。
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
