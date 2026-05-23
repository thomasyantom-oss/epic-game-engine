# 角色系统 + Session + 游戏启动

## 概述

实现角色创建/选择流程、Session 管理、以及让游戏世界能运行起来所需的启动逻辑（地图加载、UI 数据、玩家初始化）。

## Session 模型

- 首次访问 → 服务器生成 token → 前端存 localStorage
- Token 永不过期 → 角色数据永远能找回
- "当前活跃角色"有超时（可配置，默认 30 分钟无操作）→ 超时后回到角色选择界面
- 刷新页面不超时（有 token + 未超时 → 直接恢复游戏状态）
- 手动"退出角色"也回到角色选择
- Session owner 拥有多个角色（存档），数量可配置

### 状态机

```
[无 token] → 分配 token → [角色选择]
[有 token, 无活跃角色] → [角色选择]
[有 token, 有活跃角色, 未超时] → [游戏中]
[有 token, 有活跃角色, 已超时] → 清除活跃角色 → [角色选择]
```

## 角色创建

角色是一个 Object（Main: Character + 必选 Sub Components）。创建时需要选的内容由 schema 驱动：

- `character.schema.yaml` 定义 Main 字段（name 必填）
- Schema 中声明 `required_subs: [class]` → 创建时必须选择一个 class sub
- 后端读 schema 构造表单描述返回给前端

### 创建流程

```
前端 POST action.create_character
  → 后端 JS handler 读 character schema + 所有 required sub schemas
  → 构造 form 描述放入快照返回
前端渲染表单 → 玩家填写 → POST action.confirm_character { name, class, ... }
  → 后端创建 Entity（Main: Character + Sub: Class）
  → 标记 persistent + session owner
  → 自动选为活跃角色
  → 返回 in_game 快照
```

## 快照结构

### phase: character_select

```json
{
  "phase": "character_select",
  "characters": [
    { "id": "char_1", "name": "张三", "level": 5, "class": "warrior", "classLabel": "战士" }
  ],
  "maxSlots": 5
}
```

### phase: character_create

```json
{
  "phase": "character_create",
  "form": {
    "fields": [
      { "name": "name", "label": "名字", "type": "text", "required": true },
      { "name": "class", "label": "职业", "type": "select", "required": true,
        "options": [
          { "value": "warrior", "label": "战士", "description": "近战物理" },
          { "value": "mage", "label": "法师", "description": "远程魔法" }
        ]
      }
    ]
  }
}
```

### phase: in_game

```json
{
  "phase": "in_game",
  "playerId": "char_1",
  "statusBars": [
    { "id": "hp", "label": "生命", "current": 100, "max": 100, "color": "#e74c3c", "priority": 1 },
    { "id": "mp", "label": "法力", "current": 50, "max": 50, "color": "#3498db", "priority": 2 }
  ],
  "buffs": [],
  "map": { "mapId": "world_map", "playerX": 4, "playerY": 3, "width": 10, "height": 10 },
  "combat": null,
  "actions": [
    { "type": "map_move", "label": "向北", "params": { "direction": "NORTH" } },
    { "type": "map_move", "label": "向南", "params": { "direction": "SOUTH" } },
    { "type": "map_move", "label": "向西", "params": { "direction": "WEST" } },
    { "type": "map_move", "label": "向东", "params": { "direction": "EAST" } }
  ],
  "log": []
}
```

## 前端

```
App.vue 根据 snapshot.phase 渲染：
  character_select → CharacterSelect.vue（卡片列表 + 新建按钮）
  character_create → CharacterCreate.vue（动态表单渲染器）
  in_game → SnapshotRenderer.vue（已有）
```

### CharacterSelect.vue

- 卡片式布局，每张卡片显示角色名/等级/职业
- 点击卡片 → POST action.select_character { characterId }
- "新建角色"按钮 → POST action.create_character
- 空位显示为虚线卡片

### CharacterCreate.vue

- 根据 snapshot.form.fields 动态渲染输入控件
- text → input 框
- select → 选项卡/下拉
- 提交 → POST action.confirm_character { ...fieldValues }
- 取消 → POST action.cancel_create → 回到角色选择

## 后端

### SessionService（Java）

```java
@Service
public class SessionService {
    // token → SessionData { ownerId, activeCharacterId, lastActivity }
    // 生成 token、校验 token、检查超时、更新活跃时间
}
```

- Token 生成：UUID
- 存储：内存 ConcurrentHashMap（重启后 token 失效 → 回到角色选择 → 但角色数据在数据库不丢）
- 超时时间从配置读取（`epic.session-timeout-minutes: 30`）

### SnapshotController 改造

- 请求头带 `X-Session-Token`（或查询参数 `?token=`）
- 没 token → 生成新 token，返回 character_select 快照
- 有 token + 有活跃角色 + 未超时 → 返回 in_game 快照
- 有 token + 超时/无角色 → 返回 character_select 快照

### JS Handlers

#### `handlers/character/select.js`

- `action.list_characters` → 查 PersistenceService 拿该 session 的所有角色
- `action.select_character` → 设为活跃角色
- `action.create_character` → 读 schema，构造 form
- `action.confirm_character` → 创建 Entity，存盘，设为活跃
- `action.logout` → 清除活跃角色

#### `handlers/world/bootstrap.js`

监听 `world.init`（引擎启动时触发）：
- 调用 `engine.loadYaml("entities/maps/world_map.yaml")` 
- 用返回的数据创建地图 Entity（MapData 组件）放入 store

#### `handlers/ui/status_bars.js`

监听 `ui.render_status`：
- 读取玩家 Entity 的 Health 组件 → 往 bars 列表塞 HP 条
- 如果有 Mana 组件 → 塞 MP 条
- 如果有其他资源组件 → 按 priority 塞

#### `handlers/ui/actions.js`

监听 `ui.render_actions`：
- 如果玩家在地图上（有 Position 组件）→ 塞四个方向移动按钮
- 如果旁边有 POI → 塞交互按钮
- 如果在战斗中 → 塞攻击/防御按钮

### ScriptRuntime 新增 API

```java
// EngineApi 新方法
@HostAccess.Export
public Object loadYaml(String relativePath) {
    // 从当前模块目录读取 YAML 文件，返回解析后的 Map/List
}
```

需要 ScriptRuntime 知道"当前执行的脚本属于哪个模块"以解析相对路径。方案：执行脚本时设置一个 context（模块路径），`loadYaml` 读取相对于该路径的文件。

## 数据模板

### `mods/base-rules/entities/maps/world_map.yaml`

```yaml
id: world_map
name: "世界地图"
width: 10
height: 10
terrain:
  - "林林林草草草草山山山"
  - "林林草草路路草草山山"
  - "林草草路路路路草草山"
  - "草草路路城路路路草草"
  - "草路路路路路路路草沙"
  - "草草路路路路水水水沙"
  - "草草草路路水水水沙沙"
  - "草草草草路草水沙沙沙"
  - "林林草草路草草沙沙沙"
  - "林林林草路草草草沙沙"
spawn:
  x: 4
  y: 3
```

### `mods/base-rules/schemas/main/character.schema.yaml`

```yaml
id: character
type: main
required_subs: [class]
fields:
  - name: name
    type: string
    required: true
  - name: level
    type: int
    default: 1
base_components:
  Health: { hp: 100, maxHp: 100 }
  Mana: { mp: 50, maxMp: 50 }
  CombatStats: { attack: 5, defense: 3, speed: 5 }
  Position: { map: "world_map", x: 4, y: 3 }
```

### `mods/base-rules/schemas/sub/class_warrior.schema.yaml`

```yaml
id: warrior
type: sub
category: class
compatible_with: [character]
label: "战士"
description: "近战物理攻击者"
modifiers:
  - field: "Health.maxHp"
    value: "+50"
  - field: "CombatStats.attack"
    value: "+3"
  - field: "CombatStats.defense"
    value: "+2"
```

### `mods/base-rules/schemas/sub/class_mage.schema.yaml`

```yaml
id: mage
type: sub
category: class
compatible_with: [character]
label: "法师"
description: "远程魔法攻击者"
modifiers:
  - field: "Mana.maxMp"
    value: "+30"
  - field: "CombatStats.attack"
    value: "+5"
  - field: "CombatStats.speed"
    value: "+2"
```

## 配置

`application.yaml` 新增：
```yaml
epic:
  session-timeout-minutes: 30
  max-character-slots: 5
```
