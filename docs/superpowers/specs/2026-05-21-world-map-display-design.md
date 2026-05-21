# 世界地图展示系统设计

## 概述

在左上角地图面板中渲染彩色网格世界地图。玩家居中显示，支持键盘/按钮/点击三种移动方式。地图大小可由玩家自定义。

## 视觉设计

### 网格风格
- 每个格子 = 背景色 + 居中汉字，表达地形类型
- 网格线用 1px border 分隔
- 暗色背景（与现有 UI 主题一致）

### 地形类型与配色

| 地形 | 汉字 | 背景色 | 文字色 |
|------|------|--------|--------|
| 密林 | 林 | #2d6a4f | #a0d0b0 |
| 草地 | 草 | #40916c | #c0e8d0 |
| 道路 | 路 | #6b705c | #b0b5a0 |
| 沙地 | 沙 | #a68a64 | #d4c4a0 |
| 水域 | 水 | #4a90d9 | #cce4ff |
| 城镇 | 城 | #ffd93d | #333333 |
| 山地 | 山 | #8b7355 | #d4c4a0 |

地形类型可通过 Mod 扩展，颜色定义在地形数据中。

### 角色显示
- **玩家**：金色五角星 ★（#ffd700），覆盖地形字，带发光效果
- **NPC**：彩色圆形背景 + 身份汉字（商、兵、民等）
  - 友好 NPC：蓝色（#66aaff）
  - 敌对 NPC：红色（#ff6666）
  - 中立 NPC：白色（#cccccc）

角色标记覆盖地形汉字，地形信息通过背景色仍可辨认。

## 视口与滚动

- 视口大小 = 地图大小（默认 10×10），玩家居中
- 当地图大于视口时（将来扩展），玩家移动视口跟随滚动
- 边缘处理：玩家靠近地图边缘时，视口停止滚动，玩家偏离中心
- 地图大小可在设置面板自定义（范围 8×8 ~ 20×20），存储在 localStorage

## 移动系统

### 三种输入方式
1. **键盘方向键**：WASD 或箭头键，按下立即移动一格
2. **操作面板按钮**：右下角显示上下左右四个方向按钮
3. **点击格子**：点击目标格子，自动寻路并逐格移动

### 自动寻路
- A* 算法计算最短路径（考虑不可通行地形如水域、山地）
- 逐格移动，每步间隔约 200ms，模拟真实行走
- 移动过程中可以再次点击或按键中断当前路径

### 格子交互
- **交互点**（城镇、NPC位置等）：踩上去后操作面板出现交互按钮（进入、对话等）
- **移动事件**（随机遭遇等）：移动到触发格时直接触发，不需要确认（本次暂不实现）

## 数据模型

### 地图 YAML 格式（在 Mod 中定义）

```yaml
id: world_map
name: "主世界"
width: 10
height: 10
terrain:
  - "林林草草路路草水水林"
  - "林草草路路路草水水水"
  - "草草路路城城路草水草"
  - "草路路沙城城路草草草"
  - "草草沙沙沙沙草草林林"
  - "沙沙沙草草草草林林林"
  - "草草草草林林林林水水"
  - "草林林林林山山水水水"
  - "林林山山山山水水水水"
  - "林山山山水水水水水水"
pois:
  - id: village
    x: 4
    y: 2
    type: enter
    target: village_square
    label: "进入村庄"
  - id: dungeon_entrance
    x: 5
    y: 7
    type: enter
    target: dungeon_floor1
    label: "进入地城"
spawn:
  x: 4
  y: 3
```

- `terrain`：每行一个字符串，每个汉字对应一个格子的地形类型
- `pois`（Points of Interest）：特殊交互点，踩上去操作面板出按钮
- `spawn`：玩家初始位置

### 地形定义（在 Mod 中）

通行性用标签系统，不是简单的 true/false：
- 地形的 `requires` 列出通行所需能力标签（空 = 任何人都能走）
- 角色的 `abilities` 列出拥有的能力标签
- 通行判断：角色 abilities 是否包含地形的全部 requires

```yaml
# mods/base/terrains.yaml
terrains:
  林:
    id: forest
    requires: []
    color: "#2d6a4f"
    text-color: "#a0d0b0"
  水:
    id: water
    requires: [swim]
    color: "#4a90d9"
    text-color: "#cce4ff"
  山:
    id: mountain
    requires: [fly]
    color: "#8b7355"
    text-color: "#d4c4a0"
  岩:
    id: lava
    requires: [fly, fire-resist]
    color: "#cc3300"
    text-color: "#ffaa66"
  # ...
```

将来角色可通过装备、技能、状态获得能力标签（如穿水靴获得 swim），即可通行对应地形。

## 后端 API

### GET /api/map/{mapId}
返回地图完整数据（地形网格 + POI 列表 + 地形定义）。

### POST /api/map/move
请求体：`{ playerId, direction }` (UP/DOWN/LEFT/RIGHT)
返回：`{ success, newPosition, poi?, event? }`

### POST /api/map/moveTo
请求体：`{ playerId, targetX, targetY }`
返回：`{ success, path: [{x,y}...], poi?, event? }`
后端计算寻路路径返回给前端，前端逐格播放动画。

### GET /api/map/position/{playerId}
返回玩家当前地图位置。

## 前端组件

### MapGrid.vue
- 接收地图数据，渲染 CSS Grid 网格
- 格子大小根据面板尺寸和地图维度自适应计算
- 支持点击格子触发移动
- 玩家/NPC 标记作为 overlay 渲染

### 键盘监听
- 全局监听 WASD / 方向键
- 仅在非战斗、非输入框聚焦时响应
- 按键防抖：移动动画未完成时忽略输入

### 操作面板
- 地图模式下显示四方向按钮（上下左右）
- 踩到 POI 时额外显示交互按钮

### 移动动画
- 点击远处格子 → 前端收到路径数组 → 每 200ms 移动一格
- 移动中玩家五角星平滑过渡到下一格（CSS transition）
- 中断条件：
  - 玩家主动中断：移动中按方向键或再次点击 → 取消剩余路径，响应新指令
  - POI 中断：踩到交互点 → 停下，操作面板出按钮
  - 威胁中断：下一格存在威胁（敌对 NPC、危险标记等）→ 自动停止，操作面板提示"前方有危险"，玩家决定是否继续前进

## 设置面板扩展

在现有设置面板（SettingsPanel.vue）中添加：
- 地图大小调节（8~20 滑块，默认 10）
- 存储在 localStorage，与现有设置机制一致

## 与现有系统的集成

- 地图数据通过 Mod 系统加载（ModRegistry 新增 `maps` 和 `terrains`）
- 玩家地图位置存入 PlayerState（新增 mapId、mapX、mapY 字段）
- POI 交互走现有 ActionHandler 模式（新增 EnterActionHandler）
- 移动方向按钮复用 NavigationPanel 组件

## 本次实现范围

1. 后端：地形定义加载、地图数据加载、移动 API（单步 + 寻路）、位置持久化
2. 前端：MapGrid 组件、键盘/按钮/点击移动、逐格动画、设置面板地图大小
3. 内容：一张 10×10 示例世界地图 + 地形定义 + 一个城镇 POI

## 暂不实现

- NPC 在地图上移动
- 区域地图 / 地城地图生成
- 移动事件 / 随机遭遇触发
- 迷雾 / 战争迷雾 / 探索可见性
- 地形移动消耗差异（森林走得慢等）
