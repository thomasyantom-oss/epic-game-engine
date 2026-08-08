# 前端代码审查

> 审查日期：2026-05-26

## Bug / 风险

### 1. client.js 里 getSnapshot() 没有错误处理

**文件**：`api/client.js:47-54`

`performAction` 有 try/catch，但 `getSnapshot` 没有。网络错误会抛出未捕获的异常，前端不会有任何提示。

**修复方向**：加上 try/catch，复用 `onError` handler。

### 2. useAnimationPlayer 没有 cleanup 机制

**文件**：`composables/useAnimationPlayer.js`

内部用 `setTimeout` 嵌套驱动动画队列，但没有提供 `stop()` / `cancel()` 方法。如果组件卸载时动画还在播放，timeout 不会被清除，可能导致内存泄漏或操作已卸载的 DOM。

**修复方向**：添加 `stop()` 方法清理所有 pending timeout，暴露给调用方在 `onUnmounted` 时调用。

### 3. BattleGrid.vue 里 .unit-status.dead CSS 选择器重复定义

**文件**：`components/combat/BattleGrid.vue:639` 和 `724`

两处定义了 `.unit-status.dead`，opacity 值不同（0.35 vs 0.4），后面的会覆盖前面的。应删除其中一个。

### 4. isInAoeZone 里的兼容代码

**文件**：`components/combat/BattleGrid.vue:515-518`

```js
const dr = o[0] !== undefined ? o[0] : (o.get ? o.get(0) : 0)
```

这种写法说明 `aoeOffsets` 的数据格式在 JS（数组）和 Java（GraalJS proxy）之间不一致，存在隐性的跨语言序列化问题。应该在数据入口处统一格式，而不是在读取时兼容。

---

## 设计问题

### 5. BattleGrid.vue 是巨型组件（1013 行）

**文件**：`components/combat/BattleGrid.vue`

该组件承担了太多职责：
- 3×3 网格渲染（player + enemy）
- 角色状态卡片（HP/MP 条、buff 展示、compact 模式）—— 两侧代码几乎完全重复
- 命令选择（普通/技能切换、目标选择）
- 倒计时 timer
- 动画播放协调

**建议**：
- 抽取 `UnitStatusCard` 子组件，消除 player/enemy 两侧的重复代码
- 命令选择逻辑抽取为 composable（`useCombatCommands`）
- 倒计时逻辑抽取为 composable（`useTurnTimer`）

### 6. SnapshotRenderer.vue 里重复的 round 查找逻辑

**文件**：`components/SnapshotRenderer.vue:146-157` 和 `159-169`

`historyEntries` 和 `historyRounds` 里有完全相同的"找最后有内容的 round"逻辑，应抽成一个函数。

### 7. App.vue 里 handlePathfind 轮询间隔过短

**文件**：`App.vue:84-98`

寻路循环间隔只有 200ms，每秒向后端发 5 个请求。对于单人游戏可以接受，但建议加个 abort controller 或者用更大的间隔（400-500ms），减少服务端压力。

### 8. ActionPanel.vue 用 JSON.stringify 做 key

**文件**：`components/ActionPanel.vue:3`

```vue
:key="action.type + JSON.stringify(action.params)"
```

每次渲染都对每个 action 的 params 做 JSON 序列化，对象较深时有性能开销，且序列化结果不稳定（key 顺序不确定）。建议后端为每个 action 生成唯一 id。

### 9. TabPanel.vue 的 activeTab 不跟随 tabs 变化

**文件**：`components/TabPanel.vue:32`

`activeTab` 只在组件创建时初始化一次，且只 watch `defaultTab` prop。如果 `tabs` 列表内容变化（比如从战斗退回地图，battle tab 消失），`activeTab` 可能指向一个不存在的 tab，导致 slot 内容为空。

**修复方向**：watch `tabs`，当 `activeTab` 不在新 tabs 列表中时自动切回第一个 tab。

### 10. MapGrid.vue 的 cellStyle 每次调用创建新对象

**文件**：`components/MapGrid.vue:85-102`

`cellStyle(cell)` 每次渲染都为每个 cell 创建新的 style 对象（10×10 = 100 个对象）。terrain 颜色是静态数据，可以缓存。不过对于 100 个 cell 的规模，实际性能影响不大。
