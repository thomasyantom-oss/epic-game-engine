# Epic Engine

单人文字 RPG 引擎，经典奇幻设定，网页端点击链接交互。

## 启动

```bash
./start.sh
```

浏览器打开 http://localhost:5173，`Ctrl+C` 停止。

## 手动启动

```bash
# 后端 (port 8080)
cd backend && mvn spring-boot:run

# 前端 (port 5173)
cd frontend && npm run dev
```

## 调试

```bash
# 最近操作日志
curl http://localhost:8080/api/debug/log

# 某个玩家的操作历史
curl http://localhost:8080/api/debug/log/player1

# 玩家当前状态
curl http://localhost:8080/api/debug/state/player1

# 系统健康检查（已加载场景数、玩家数）
curl http://localhost:8080/api/debug/health
```

后端控制台日志格式：
```
[14:03:22] player1 | move → {target=tavern} | SUCCESS
[14:03:25] player1 | move → {target=cave} | FAIL: scene not found
```

## 项目结构

```
epic/
├── backend/          Java Spring Boot 后端（游戏逻辑）
├── frontend/         Vue 3 前端（纯展示层）
├── mods/
│   └── base/         基础内容包（也是一个 Mod）
│       ├── mod.yaml  Mod 描述文件
│       └── scenes/   场景 YAML 文件
├── docs/             设计文档和实现计划
└── start.sh          一键启动脚本
```

## 技术栈

- 后端：Java 21, Spring Boot 3, H2, SnakeYAML
- 前端：Vue 3, Vite
- 通信：REST API（Vite 代理转发 /api → localhost:8080）

## Mod 系统

所有游戏内容通过 Mod 加载，引擎本身不含内容。

- Mod 放在 `mods/` 目录下，每个 Mod 一个文件夹
- 每个 Mod 必须有 `mod.yaml` 描述文件
- 按 `load-order` 排序加载，后加载的覆盖先加载的同名内容

```yaml
# mods/my-mod/mod.yaml
id: my-mod
name: "我的Mod"
version: "1.0.0"
description: "自定义内容包"
load-order: 10
dependencies: [base]
```

## 添加场景

在 Mod 的 `scenes/` 目录下创建 YAML 文件：

```yaml
id: my_scene
description:
  - text: "你来到了"
    color: "#e0e0e0"
  - text: "神秘之地"
    color: "#9b59b6"
actions:
  - id: go-back
    label: "离开"
    type: move
    target: village_square
```

文字颜色通过 `color` 字段控制，每个文字片段可以有不同颜色。
