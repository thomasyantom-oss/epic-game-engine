# 开发日志

## 2026-05-20

### 项目启动 & 设计

- 确定方向：单人文字 RPG，经典奇幻，网页端点击交互
- 核心哲学：少维度高交互产生涌现复杂度（11×11 > 1×100）
- 汉字 + 颜色作为核心表达方式
- 引擎与内容分离，所有内容通过 Mod 加载
- 技术栈：Java Spring Boot 后端 + Vue 3 前端 + H2 + REST API
- 多职业/转职/专精联动系统（具体规则待定，架构先到位）
- 克制系统预留（标签 + 关系表）
- Mod/DLC 三层能力：内容层、规则层、机制层

### 引擎基础实现

- 后端脚手架（Spring Boot 3, Java 21, H2, CORS）
- 前端脚手架（Vue 3, Vite, CSS 变量主题系统）
- Mod 加载系统（发现、排序、后加载覆盖先加载）
- 场景 API + 彩色文字渲染
- 可扩展 Action Handler 模式（目前有 move）
- 玩家状态持久化
- 多面板 UI + 跨面板刷新机制
- 玩家 UI 自定义（CSS 变量 + localStorage）
- 基础内容包：村庄广场、酒馆、幽暗森林

### 开发工具

- 游戏事件日志（自动记录每步操作）
- Debug 端点（/api/debug/state, /log, /health）
- 集成测试（@SpringBootTest 全链路验证）
- spring-boot-devtools 热更新
- 一键启动脚本 start.sh

### 前端布局

- 4×3 网格布局，所有面板带边框 + tab 切换，面板大小锁死（内容溢出滚动）
- 左上(3×2)：地图（待实现）
- 右上(1×2)：功能面板（人物/设置tab）
- 左下(3×1)：事件日志（场景描述流式累积，玩家操作记录，可滚动查看历史）
- 右下(1×1)：操作面板（移动方向、进副本等按钮）
- 战斗时整体切换为战斗布局（战场阵列/指令/战斗日志/角色状态）

### 战斗系统基础

- 战斗数据模型：Combatant, Position(3×3 grid), Side, CombatState
- 目标解析：普通攻击打最近的排（由近及远）
- 战斗引擎：速度排序统一结算，防御减伤，死亡单位跳过
- 战斗指令：攻击/防御，JRPG 式菜单
- 遭遇战通过 Mod YAML 定义，从场景动作触发
- 敌方 AI（基础版：随机攻击最近排的玩家）
- REST API：开始战斗、获取状态、提交指令、获取可选目标
- 战斗 UI：4 面板（战场阵列/指令菜单/战斗日志/角色状态）
- 战场阵列横向排列（我方前排在右靠近敌方，敌方前排在左靠近我方）
- 战斗日志保留所有回合历史（可滚动回看）
- 最后一个角色下令后自动结算（800ms 延迟作回合间隔）
- 探索↔战斗界面自动切换
- 集成测试覆盖全链路（20个测试）
- 基础遭遇战内容：森林哥布林（前排哥布林 + 后排弓手）

## 2026-05-21

### 世界地图系统

- 10×10 彩色网格地图，色块+汉字风格（林、草、路、沙、水、城、山）
- 地形标签通行系统（requires/abilities），可扩展（水需要 swim，山需要 fly）
- 移动速度字段预留（move-cost / move-speed）
- 三种移动方式：键盘方向键(WASD/箭头)、D-pad 按钮、点击自动寻路
- A* 寻路 + 逐格动画（200ms/步）
- 寻路中断：手动中断、POI 停止、威胁停止
- 玩家居中视口，地图大小可自定义（8~20，设置面板滑块）
- POI 交互：踩到特殊点操作面板出按钮（进入村庄等）
- 玩家 ★ 金色五角星标记
- 地形/地图数据通过 Mod YAML 加载
- REST API：获取地图、单步移动、寻路移动、获取位置
- 35 个后端测试全部通过

### 面板布局重构

- 删除方向键 D-pad（键盘移动足够）
- 地图信息栏：地域名 + 坐标 + 当前地形 + POI 按钮 + 怪物占位
- 战斗嵌入主面板（不再全屏替换），战斗/地图双 tab 切换
- 战斗中可切换查看大地图（只读，不可操作）
- 新战场 H 形布局：左右状态栏贯穿全高，中间分战场+指令
- 战场：3×3 vs 3×3 地形格子（当前脚下地形统一渲染）+ 绿▶/红◀三角标记
- 指令栏三等分：头像区 | 指令区（攻击/防御/技能/道具/逃跑）| 详情区（目标/技能列表）
- 逐角色操作流程：选指令→点击敌方格子选目标→下一角色→全部选完自动结算
- 取消键红色放在指令区内，目标选择边框 3px 红色高亮
- 右下角改为快捷行动栏

### 战斗日志系统

- 战斗日志独立 tab，与事件栏分开
- 当前回合始终可见，历史回合可折叠查看
- 日志染色：我方名青色、敌方名红色、伤害数字金色、击败标记红色
- 战斗结束后日志保留可回看，下场战斗才清空
- 页面刷新恢复战斗状态（后端保持战斗 + 前端重连）

### UI 规范化

- 颜色系统：所有灰色统一为白色，每种颜色有唯一语义
- 去掉所有带边框按钮，统一纯文字交互样式
- 面板边框加粗至 2px
- 战场与指令区用 2px 分割线分隔
- 逃跑/战斗结束正确回到大地图
- 刷新页面保持 POI 状态和战斗状态

## 2026-05-22 ~ 2026-05-23

### 引擎重构：事件驱动微内核

完全推倒重写引擎。从硬编码 Java 架构改为事件驱动微内核，所有游戏规则由 JS 模块实现。

**Engine Core（Java）：**
- EventBus — fire/on/cancel/priority 事件总线
- Entity + Component — ECS 实体组件系统
- ModifierChain — 效果叠加/撤销（priority 排序，base state 快照恢复）
- TagIndex + EntityStore — 标签索引 O(1) 查询
- ScriptRuntime — GraalJS 沙箱，暴露 engine/store API 给 JS
- ModuleLoader — 发现 mods 目录，加载 JS handlers
- SchemaRegistry — 加载/校验 main/sub schema YAML
- HotReloader — 文件监听，JS handler 修改后自动重载无需重启
- PersistenceService — persistent 标签实体存 H2 数据库
- GeneratorService — schema-driven 实体生成器

**模块系统：**
- 所有游戏规则在 `mods/base-rules/handlers/*.js`
- 战斗：combat_flow / initiative / damage_calc / death_check / combat_log / start_combat
- 地图：movement（地形通行检查）/ pathfinding（A* 跳过不可通行）
- UI：status_bars / actions（动态生成可用操作）
- 世界：bootstrap（从 YAML 加载地图+地形+颜色）
- 角色：select（创建/选择/登出，schema 驱动表单）

**前端架构：**
- 无状态快照渲染器 — 一个 snapshot JSON 驱动全部 UI
- 单一 API：POST /api/action + GET /api/snapshot
- Session token（localStorage）+ 可配置超时
- 角色选择（卡片式）→ 角色创建（schema 驱动表单）→ 游戏中

### 角色系统

- 多存档（可配置槽位数）
- Session token 持久化 — 重启后端角色不丢失
- 角色 = Object（Main: Character + Sub: Class）
- 创建时表单由 schema 动态生成（required_subs 决定必选项）
- 职业选择影响属性（modifiers 定义加成）
- 战斗死亡满血复活

### UI 系统

- 4 面板 tab 布局恢复（地图+地图信息分割栏 | 人物/设置 | 事件/战斗日志 | 快捷操作）
- 统一 ActionLink 组件 — 所有可交互元素用 `▸前缀` + 链接色，不用按钮
- 颜色系统模块化 — `colors.yaml` 定义语义色，snapshot 传给前端动态应用 CSS 变量
- 战斗指令后端驱动 — 模块控制可用操作，前端不硬编码
- 错误 toast — API 失败时顶部红色提示 5s 消失
- 设置面板 — 字体大小/背景/文字/边框/链接颜色/地图大小，纯前端 localStorage

### 战斗系统

- H 形布局恢复：左右状态栏 + 中间 3×3 格子 + 底部指令栏三等分
- 回合数顶部 banner 显示
- 战斗日志：当前回合可见，历史回合可折叠，染色（我方/敌方/伤害语义色）
- 逃跑直接结束战斗
- 玩家/怪物对称设计 — 相同 Object 结构，只是控制方式不同

### 地图系统

- 10×10 彩色网格恢复，地形数据从 YAML 加载
- 键盘 WASD/方向键移动
- 点击自动寻路（逐步 200ms/步，可中断）
- 地形通行检查（水需要 swim，山需要 fly）
- 地图信息面板（分割栏显示地名/坐标/地形/POI）
- POI 交互触发战斗

### 设计原则

- **众生平等** — 玩家和怪物完全对称结构，地点也是属性对象
- **不 hardcode** — 颜色走 CSS 变量（模块定义），文本/数据走模块/schema
- **每种颜色唯一语义** — 不滥用，交互统一链接色
- **前端是纯渲染器** — 不含游戏知识，所有逻辑在后端模块

### 战报系统 + 回合节奏

- 战斗事件队列（CombatEvents 组件）— 每回合生成有序事件列表
- 每条事件包含：segments（文字显示）+ effects（数据变化，为动画预留）
- effect 类型：hp_change、death（后续扩展 add_buff、mp_change 等）
- 前端按 600ms/条逐步播放事件，播放期间指令不可用
- 30 秒回合倒计时，超时自动攻击最近目标
- 倒计时在结算期间暂停，下回合重新开始

### 交互统一

- ActionLink 组件 — 所有可交互元素统一 `▸前缀` + 链接色
- 战斗指令后端驱动（攻击/防御/逃跑由 handler 动态生成，不前端硬编码）
- 颜色不指定时默认用 `--link-color`（设置面板可调）
- 特殊颜色由模块指定（如未来史诗装备 `color: "epic"`）

### 颜色系统模块化

- `colors.yaml` 定义语义色（player/enemy/damage/highlight/text）
- 启动时加载到 `_config` entity 的 Colors 组件
- 每次 snapshot 携带完整 colorMap
- 前端 watch snapshot.colors 动态设置 CSS 变量
- 模块可自由扩展新语义色（如 `epic`、`blood`）

### Bug 修复

- 角色持久化 — 重启后端不丢角色（session token 恢复 + loadAllPersistent）
- 启动时清理残留 combat tag — 防止重启后 ghost combat
- 战斗死亡满血复活
- 寻路不可通过时立即停止（不死循环）
- API 错误前端 toast 提示（不白屏）
- 战斗中隐藏退出角色按钮
- 战斗日志战斗结束后保留，新战斗清空
- JS 热加载 — 文件监听自动重载 handler，不用重启

## 2026-05-23（续）

### 战斗动画系统

设计 + 实现第一批。参考 Into the Breach 风格，原语组合系统。

**设计成果（spec: `docs/superpowers/specs/2026-05-23-combat-animation-design.md`）：**
- 12 个动画原语：pulse / flash_sequence / projectile / beam / slash / impact / shake / damage_number / buff_up / debuff_down / mark_dead / indicator_add
- 架构：引擎实现原语播放器，Mod 在技能 YAML 中组合原语定义动画序列
- 约束：所有动画严格在格子/3x3 边界内，投射物中心到中心，到达后才触发受击
- 月牙斩：端点从左上/左下角出发，弧边不超右边框，随角度旋转
- Buff/Debuff：单体双三角（底边=格子宽）在格子内上升/下沉，全场大三角重叠在3x3内

**实现（第一批原语）：**
- BattleGrid ITB 视觉改造 — 深色底 `#1a1a2e`、粗 3px 边框、P1/E1 token 彩色背景、角标 HP、选中目标 4px 橙框 + glow
- useAnimationPlayer composable — 动画队列管理，按事件逐个播放，命中类并行触发
- AnimationLayer 组件 — 绝对定位图层渲染 impact 白闪 + damage_number 数字弹出
- 后端 combat_events.js 生成 animation 序列，SnapshotService 传递到前端
- 已实现原语：lunge（攻击者前顿）、impact（白闪）、shake（颤抖）、damage_number
- 动画只动 token 不动格子（地形不该抖）
- 倒计时移到两个格子中间放大显示，去掉 `s` 后缀
- 战场格子染上当前地形颜色（30% 透明度）

### Bug 修复

- 逃跑后显示"战斗胜利" → 改为"逃跑成功"（endCombat 改用 result 字符串）
- GraalJS 多线程错误 → ScriptRuntime.execute 和 handler 回调加 synchronized
- 寻路中点击其他地点 → 停止当前寻路，不重新寻路

### 新功能

- **删除存档** — 角色选择界面角色卡右上角 `x` 按钮，确认对话框后删除
- **YAML 热加载** — HotReloader 监听 .yaml 变更，重新触发 world.init（幂等），改地图/地形/颜色不用重启
- **主题切换** — 夜间模式 / 摸鱼模式 / 亮色 / 多巴胺 四套预设，角色选择页+设置面板可切
- 新增三处遭遇战 POI 用于测试不同地形战斗底色

### 待做

- **动画系统后续** — projectile / beam / slash / buff_up / debuff_down / mark_dead 等剩余原语
- Mod 动画手册（YAML 格式、参数说明、示例）
- 技能系统（技能定义、MP/资源消耗、多种范围）
- 联动系统：状态标签 / 资源累积 / 技能链
- 高级 AI（行为模式、按遭遇战配置）
- 装备系统 + 物品品质颜色
- 多角色队伍
- NPC 在地图上移动
- 区域地图 / 地城随机生成
- 随机遭遇触发
- 迷雾 / 战争迷雾
- 打造系统 / Web 编辑器
- 血魔法等机制改写模块验证
