# 后端代码审查

> 审查日期：2026-05-26

## 实际 Bug

### 1. EntityStore.clear() 漏清 TagIndex

**文件**：`EntityStore.java:50-52`

`clear()` 只清了 `entities` map，但 `tagIndex` 里的引用全部变成悬空指针。TagIndex 本身也没有 `clear()` 方法。之后 `getByTag()` 会返回已不在 store 里的 Entity，行为不一致。

**修复方向**：TagIndex 添加 `clear()` 方法，EntityStore.clear() 同时调用 `tagIndex.clear()`。

### 2. Component.getInt() / getDouble() 对 null 不安全

**文件**：`Component.java:23-25`

如果 key 不存在，`data.get(key)` 返回 null，`((Number) null).intValue()` 直接 NPE。`has()` 检查是可选的，调用方很容易漏掉。

**修复方向**：提供默认值版本 `getInt(key, defaultValue)`，或在 getter 里做 null 检查。

---

## 设计问题

### 3. PersistenceService 用 setter 注入 ModifierChainService

**文件**：`PersistenceService.java:26-28`

其他所有依赖走构造函数注入，这个 setter 注入不一致。`save()` 里需要判断 `modifierChainService != null`，增加了复杂度。

**建议**：改为构造函数注入。

### 4. SessionService.restoreSession() 并没有真正恢复

**文件**：`SessionService.java:32-35`

方法只是用旧 token 创建新 session 覆盖，名字有误导性。真正的 session 恢复应从持久存储读取。

### 5. SnapshotService.buildCombatSnapshot() 太长

**文件**：`SnapshotService.java:221-322`

100+ 行，深嵌套，大量 unchecked cast。combat event parsing、combatant info 构建、buff info 构建应各自抽成 private 方法。

### 6. HotReloader.reloadAll() 不是原子操作

**文件**：`HotReloader.java:81-114`

`bus.clear()` 之后如果某个 JS 文件加载失败，引擎处于部分加载状态，没有回滚机制。开发环境可接受，但会导致间歇性 handler 丢失。

### 7. ScriptRuntime.EngineApi 类职责过重

**文件**：`ScriptRuntime.java:82-273`

20+ 个方法混在同一个 inner class，factory methods（newComponent、newMap、newList）和业务逻辑（combatEvent、addModifier）混在一起。可按职责拆分。

### 8. EngineBootstrap 构造函数参数过多（10 个）

**文件**：`EngineBootstrap.java:35-49`

构造函数 10 个参数，说明该类承担了太多职责。可考虑拆分启动流程或引入 Builder / 配置对象。

### 9. WorldSnapshot record 有 14 个字段

**文件**：`WorldSnapshot.java:6-21`

大量 nullable 字段，用 factory methods 缓解了部分问题，但 JSON 序列化时仍会输出所有 null 字段。可考虑用 sealed interface + 子 record 按 phase 区分。
