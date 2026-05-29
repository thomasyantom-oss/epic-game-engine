# Backlog

格式：每条需求独立区块，包含描述、可行性分析（待填）、状态标签。

状态：`[backlog]` `[analysis]` `[planned]` `[in-progress]` `[done]` `[rejected]`

---

## UI / 前端

### BL-001 技能 hover tooltip 动态计算值
**状态：** `[backlog]`

**需求描述：**
技能按钮 hover 时显示 tooltip，内容由 mod 定义，但可以包含动态计算值。
例如：技能倍率 × 当前角色攻击力 = 预期伤害，实时带入角色属性和技能加成后显示。

当前行为：技能描述是静态文本（skill YAML 里写死的 `description` 字段）。

期望行为：
- tooltip 内容由 mod 控制（可以是静态文本，也可以是带占位符/计算表达式的模板）
- 前端拿到角色当前属性 + 技能元数据，能渲染出计算后的值
- 类似「顺劈斩：对前排造成 **42 × 1.2 = 50** 点物理伤害」

**可行性分析：** _待分析_
- 方案 A：后端在 snapshot 里预计算好 tooltip 内容字符串，前端直接渲染（简单，但每帧 snapshot 都要算）
- 方案 B：前端拿到 `damage_formula`（字段 + 倍率），客户端算（公式简单可行，但复杂公式难处理）
- 方案 C：专用 `/api/skill/preview?skillId=X&entityId=Y` 端点按需请求（延迟高，hover 体验差）

**依赖：** 需要 skill YAML 支持新的 `tooltip_template` 或 `formula` 字段定义格式。

---

## 战斗系统

### BL-002 战斗回合后端超时自动结算
**状态：** `[backlog]`

**需求描述：**
当前倒计时完全在前端，前端关闭或断连后战斗会永久挂起。需要后端维护战斗时钟，防止玩家利用关闭前端/重启服务器跳过战斗。

**期望行为：**
- 每次进入指令阶段时，后端记录 `roundStartTime` 时间戳
- 任何请求进来（GET /api/snapshot 或 POST /api/action）时，先检查是否超时
- 超时则循环自动结算（所有玩家角色自动攻击最近目标）直到战斗结束或回到正常回合
- 服务器重启后，第一次 snapshot 请求触发检查，离线期间的回合一次性补算完
- Session 长时间不活跃（15分钟）→ 战斗视为逃跑处理

**设计要点：**
- 零轮询：后端完全被动，只有请求进来才触发检查
- 补算是循环：`while (战斗未结束 && 有超时回合) { 自动结算一回合 }`，毫秒级
- `roundStartTime` 需要持久化（存入 CombatState 组件），服务器重启后可读

**可行性分析：** _待分析_
- 需要 `combat_flow.js` 在回合开始时写 `roundStartTime`
- 需要 `SnapshotService` 或 action handler 入口处加超时检查逻辑
- 自动结算复用现有 `auto_attack` 逻辑

---

## 地图 / 世界

_（待添加）_

---

## 引擎 / 后端

_（待添加）_

---

## 内容 / Mod

_（待添加）_
