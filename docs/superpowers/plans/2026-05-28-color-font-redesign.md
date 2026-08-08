# 配色与字体改动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将游戏 UI 的颜色系统重构为 One Dark 配色，所有颜色通过后端 colorMap 驱动，零前端 hardcode；战斗指令区改为暗刻按钮样式；战斗 token 支持阵营染色。

**Architecture:** `colors.yaml` 新增所有语义色 → `App.vue` 已有机制自动写 `--color-*` CSS 变量 → 各组件改用 CSS 变量，删除所有 hardcode 颜色字面量。按钮样式在 `BattleGrid.vue` 内局部重写，不新建组件。

**Tech Stack:** Vue 3, CSS 变量, YAML（SnakeYAML）

**约束：禁止改动任何布局、面板结构、尺寸、grid 定义**

---

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `mods/base-rules/colors.yaml` | 修改 | 新增全套语义色 |
| `mods/base-rules/handlers/ui/actions.js` | 修改 | skill action params 加 `description` 字段 |
| `mods/base-rules/skills/*.yaml` | 修改 | 各技能加 `description` 字段 |
| `frontend/src/styles/variables.css` | 修改 | 新增 CSS 变量默认值（fallback） |
| `frontend/src/components/CharacterStatsTab.vue` | 修改 | 去除 hardcode 颜色；能量条数字跟条色 |
| `frontend/src/components/EquipmentBagPanel.vue` | 修改 | 去除 hardcode 颜色 |
| `frontend/src/components/combat/BattleGrid.vue` | 修改 | token 阵营染色；按钮样式；去 hardcode |

---

## Task 1: 更新 colors.yaml — 新增全套语义色

**Files:**
- Modify: `mods/base-rules/colors.yaml`

- [ ] **Step 1: 替换 colors.yaml 全部内容**

```yaml
colors:
  # 基础文字
  text: "#c8d0dc"

  # 能量条
  hp: "#e84848"
  mp: "#2eb8cc"

  # 阵营
  player: "#66cc55"
  enemy: "#e84848"
  neutral: "#ffcc33"
  ally: "#55aaff"

  # 语义色
  damage: "#ffcc33"
  highlight: "#55aaff"
  mana: "#2eb8cc"

  # 稀有度
  rarity_common: "#c8d0dc"
  rarity_uncommon: "#55dd55"
  rarity_rare: "#55aaff"
  rarity_epic: "#cc66ff"
  rarity_legendary: "#ffcc33"

  # 元素属性
  element_fire: "#ff7733"
  element_ice: "#66ddff"
  element_lightning: "#ffee44"
```

- [ ] **Step 2: 验证 colors.yaml 格式正确**

启动后端，访问 `http://localhost:8080/api/debug/health`，确认返回 200。

- [ ] **Step 3: Commit**

```
git add mods/base-rules/colors.yaml
git commit -m "feat: 更新 colors.yaml — One Dark 配色 + 全套语义色"
```

---

## Task 2: 更新 CSS 变量默认值

**Files:**
- Modify: `frontend/src/styles/variables.css`

- [ ] **Step 1: 替换 variables.css 内容**

将现有 `:root` 块改为：

```css
:root {
    --bg-color: #0d1318;
    --text-color: #c8d0dc;
    --panel-bg: #141c24;
    --panel-border-color: #222c38;
    --panel-border-width: 2px;
    --panel-border-style: solid;
    --link-color: #55aaff;
    --link-hover-color: #88ccff;
    --font-size: 16px;
    --font-family: "LXGW WenKai", "Noto Serif SC", serif;

    /* Semantic color roles — overridden at runtime by snapshot.colors */
    --color-player: #66cc55;
    --color-enemy: #e84848;
    --color-neutral: #ffcc33;
    --color-ally: #55aaff;
    --color-damage: #ffcc33;
    --color-text: #c8d0dc;
    --color-highlight: #55aaff;
    --color-hp: #e84848;
    --color-mp: #2eb8cc;
    --color-rarity_common: #c8d0dc;
    --color-rarity_uncommon: #55dd55;
    --color-rarity_rare: #55aaff;
    --color-rarity_epic: #cc66ff;
    --color-rarity_legendary: #ffcc33;
    --color-element_fire: #ff7733;
    --color-element_ice: #66ddff;
    --color-element_lightning: #ffee44;
    --color-border: #222c38;
}
```

- [ ] **Step 2: 启动前端，检查页面背景色是否变为深蓝黑**

```
cd frontend && npm run dev
```

访问 `http://localhost:5173`，页面背景应为 `#0d1318`。

- [ ] **Step 3: Commit**

```
git add frontend/src/styles/variables.css
git commit -m "feat: variables.css — One Dark 色值 + 新增语义色变量"
```

---

## Task 3: CharacterStatsTab.vue — 去除 hardcode，能量条数字跟条色

**Files:**
- Modify: `frontend/src/components/CharacterStatsTab.vue`

当前问题：
- `hp-fill` hardcode `background: #e74c3c`
- `mp-fill` hardcode `background: #3498db`
- `.bar-nums` hardcode `color: rgba(255,255,255,0.75)`
- `.pending-banner` hardcode `color: #ffd93d` 和 border
- `.stat-value` 用后端传来的 `stat.color` 但 tooltip 里颜色 hardcode

- [ ] **Step 1: 修改 `<style scoped>` 中的能量条颜色**

找到并替换：
```css
.hp-fill { background: #e74c3c; }
.mp-fill { background: #3498db; }
```
改为：
```css
.hp-fill { background: var(--color-hp); }
.mp-fill { background: var(--color-mp); }
```

- [ ] **Step 2: 能量条数字颜色跟条色**

在 `<template>` 中，HP 的 `<span class="bar-nums">` 加 `:style`：

```html
<!-- HP 条 -->
<div class="bar-row" v-if="hpBar">
  <span class="bar-label" :style="{ color: hpBar.color }">{{ hpBar.label }}</span>
  <div class="bar-track">
    <div class="bar-fill hp-fill"
         :style="{ width: (hpBar.max > 0 ? hpBar.current / hpBar.max * 100 : 0) + '%' }"></div>
  </div>
  <span class="bar-nums" :style="{ color: hpBar.color }">{{ hpBar.current }}/{{ hpBar.max }}</span>
</div>

<!-- MP 条 -->
<div class="bar-row" v-if="mpBar">
  <span class="bar-label" :style="{ color: mpBar.color }">{{ mpBar.label }}</span>
  <div class="bar-track">
    <div class="bar-fill mp-fill"
         :style="{ width: (mpBar.max > 0 ? mpBar.current / mpBar.max * 100 : 0) + '%' }"></div>
  </div>
  <span class="bar-nums" :style="{ color: mpBar.color }">{{ mpBar.current }}/{{ mpBar.max }}</span>
</div>
```

- [ ] **Step 3: 去除 pending-banner 的 hardcode 颜色**

```css
.pending-banner {
  display: flex; justify-content: space-between; align-items: center;
  background: color-mix(in srgb, var(--color-damage) 12%, transparent);
  border: 2px solid color-mix(in srgb, var(--color-damage) 40%, transparent);
  border-radius: 3px;
  padding: 0.2rem 0.5rem;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
  color: var(--color-damage);
}
```

- [ ] **Step 4: 去除 .bar-nums hardcode opacity**

```css
.bar-nums { font-size: 0.78rem; min-width: 3rem; text-align: right; }
```

（颜色已由 `:style` 绑定，不需要 CSS 里写颜色）

- [ ] **Step 5: 属性值统一用 --color-text**

`stat-value` 的颜色来自后端传的 `stat.color`，这是正确的（后端定义语义），保持不变。

等级颜色：找到：
```html
<span class="char-level">Lv.{{ nameBar.current }}</span>
```
确认 `.char-level` 没有 hardcode 颜色（当前是 `opacity: 0.7`），改为：
```css
.char-level { font-size: 0.85rem; color: var(--color-text); }
```

- [ ] **Step 6: tooltip breakdown 颜色去 hardcode**

在 `onStatHover` 函数中：
```js
const rows = data.breakdown.map(b => ({
  label: b.label,
  value: (b.value > 0 && b.type !== 'base' ? '+' : '') + b.value,
  valueColor: b.type === 'base' ? null : (b.value > 0 ? 'var(--color-rarity_uncommon)' : 'var(--color-enemy)')
}))
```

- [ ] **Step 7: 验证**

启动前端，进入角色，检查：
- HP 条红色、MP 条青色，数字颜色与条相同
- 属性 hover tooltip 出现，加成绿色、减益红色
- 等级显示普通白色

- [ ] **Step 8: Commit**

```
git add frontend/src/components/CharacterStatsTab.vue
git commit -m "feat: CharacterStatsTab — 颜色全走 CSS 变量，能量条数字跟条色"
```

---

## Task 4: EquipmentBagPanel.vue — 去除 hardcode 颜色

**Files:**
- Modify: `frontend/src/components/EquipmentBagPanel.vue`

当前 hardcode 问题：
- `.equip-slot` border `#30363d`
- `.equip-slot.drag-over` border `#60a5fa`、background `rgba(96,165,250,0.1)`
- `.slot-label` color `#444`
- `.slot-remove` color `#888`、hover `#f87171`
- `.bag-cell` border `rgba(255,255,255,0.1)`

- [ ] **Step 1: 修改装备槽 border**

```css
.equip-slot {
  /* 其余不变 */
  border: 2px solid var(--color-border);
}
.equip-slot.drag-over {
  border-color: var(--color-highlight) !important;
  background: color-mix(in srgb, var(--color-highlight) 10%, transparent);
}
```

- [ ] **Step 2: 修改 slot 标签和移除按钮颜色**

```css
.slot-label { font-size: 0.75rem; color: var(--color-text); opacity: 0.3; }
.slot-remove { position: absolute; top: 1px; right: 2px; font-size: 0.6rem; color: var(--color-text); opacity: 0.5; cursor: pointer; line-height: 1; }
.slot-remove:hover { color: var(--color-enemy); opacity: 1; }
```

- [ ] **Step 3: 修改背包格子 border**

```css
.bag-cell {
  /* 其余不变 */
  border: 2px solid color-mix(in srgb, var(--color-text) 10%, transparent);
}
.bag-cell.has-item:hover {
  border-color: var(--color-highlight);
}
```

- [ ] **Step 4: showItemTooltip 中 rows 里的 hardcode 颜色**

```js
function showItemTooltip(item, event) {
  const rows = Object.entries(item.stats || {}).map(([k, v]) => ({
    label: STAT_LABELS[k] || k,
    value: '+' + v,
    valueColor: 'var(--color-rarity_uncommon)'
  }))
  rows.push({ label: '──────', value: '', valueColor: null })
  rows.push({
    label: (RARITY_LABELS[item.rarity] || item.rarity) + ' · ' + (TYPE_LABELS[item.type] || item.type),
    value: '', valueColor: 'var(--color-text)'
  })
  showTooltip({ title: item.name, titleColor: item.rarityColor || 'var(--color-text)', rows }, event)
}
```

- [ ] **Step 5: sort-btn 去 hardcode**

```css
.sort-btn {
  font-size: 0.75rem;
  background: color-mix(in srgb, var(--color-text) 8%, transparent);
  border: 2px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  color: var(--color-text);
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
  cursor: pointer;
}
.sort-btn:hover { background: color-mix(in srgb, var(--color-text) 14%, transparent); }
```

- [ ] **Step 6: 验证**

打开装备面板，确认所有边框、颜色随主题 CSS 变量变化，无白屏/乱色。

- [ ] **Step 7: Commit**

```
git add frontend/src/components/EquipmentBagPanel.vue
git commit -m "feat: EquipmentBagPanel — 颜色全走 CSS 变量"
```

---

## Task 5: BattleGrid.vue — token 阵营染色 + 去 hardcode

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`

当前问题：
- `.marker.player` hardcode `background: rgba(79, 195, 247, 0.15)` — 应用 player 色
- `.marker.enemy` hardcode `background: rgba(229, 115, 115, 0.15)` — 应用 enemy 色
- `.terrain-cell` hardcode `background-color: #1a1a2e`
- `.terrain-cell.aoe-hit` hardcode `background-color: rgba(229, 69, 96, 0.25)` — 应改用 enemy 色
- `.actor-avatar` hardcode `background: #1a2a3a`
- `.stat-bar-num` hardcode `color: rgba(255, 255, 255, 0.75)`
- `hpColor()` 函数里 hardcode `rgba(46,125,50,...)` / `rgba(198,40,40,...)`

阵营扩展：当前只有 PLAYER/ENEMY 两种 side，需要支持 NEUTRAL/ALLY。

- [ ] **Step 1: 修改 marker 阵营染色用 CSS 变量**

找到：
```css
.marker.player { color: var(--color-player); background: rgba(79, 195, 247, 0.15); }
.marker.enemy { color: var(--color-enemy); background: rgba(229, 115, 115, 0.15); }
```
改为：
```css
.marker.player  { color: var(--color-player);  background: color-mix(in srgb, var(--color-player)  15%, transparent); border: 2px solid var(--color-player); }
.marker.enemy   { color: var(--color-enemy);   background: color-mix(in srgb, var(--color-enemy)   15%, transparent); border: 2px solid var(--color-enemy); }
.marker.neutral { color: var(--color-neutral); background: color-mix(in srgb, var(--color-neutral) 15%, transparent); border: 2px solid var(--color-neutral); }
.marker.ally    { color: var(--color-ally);    background: color-mix(in srgb, var(--color-ally)    15%, transparent); border: 2px solid var(--color-ally); }
```

- [ ] **Step 2: marker 的 class 绑定加入 side**

现在格子里 marker 的 class 是固定 `.player` 或 `.enemy`，需要从 `cell.marker.side` 动态绑定。

找到 player-grid 里的 marker span：
```html
<span v-if="cell.marker && cell.marker.alive" class="marker player"
```
改为：
```html
<span v-if="cell.marker && cell.marker.alive" class="marker"
      :class="markerSideClass(cell.marker.side)"
```

找到 enemy-grid 里的 marker span：
```html
<span v-if="cell.marker && cell.marker.alive" class="marker enemy"
```
改为：
```html
<span v-if="cell.marker && cell.marker.alive" class="marker"
      :class="markerSideClass(cell.marker.side)"
```

在 `<script setup>` 中添加辅助函数：
```js
function markerSideClass(side) {
  const map = { PLAYER: 'player', ENEMY: 'enemy', NEUTRAL: 'neutral', ALLY: 'ally' }
  return map[side] || 'enemy'
}
```

- [ ] **Step 3: mapUnitsToGrid 中传入 side 字段**

找到 `mapUnitsToGrid` 函数，确认 push 的 marker 对象包含 `side` 字段（来自原始 unit）：

```js
markers.push({
  col, row,
  side: unit.side || (side === 'player' ? 'PLAYER' : 'ENEMY'),  // 保留原始 side
  index: idx + 1,
  alive: unit.alive,
  id: unit.id,
  hp: unit.hp,
  maxHp: unit.maxHp,
  buffs: unit.buffs || [],
  unitRow: unitRow,
  slot: unit.slot ?? idx % 3
})
```

- [ ] **Step 4: 修复 terrain-cell hardcode 背景色**

找到：
```css
.terrain-cell {
  /* ... */
  background-color: #1a1a2e;
}
```
改为：
```css
.terrain-cell {
  /* ... */
  background-color: var(--panel-bg);
}
```

- [ ] **Step 5: 修复 aoe-hit hardcode 颜色**

```css
.terrain-cell.aoe-hit {
  background-color: color-mix(in srgb, var(--color-enemy) 25%, transparent) !important;
  border-color: var(--color-enemy);
}
```

- [ ] **Step 6: 修复 actor-avatar hardcode**

```css
.actor-avatar {
  width: 2.2rem; height: 2.2rem;
  border: 2px solid var(--color-player);
  border-radius: 4px;
  background: color-mix(in srgb, var(--color-player) 10%, transparent);
}
```

- [ ] **Step 7: 修复 stat-bar-num hardcode**

```css
.stat-bar-num {
  font-size: 0.6em;
  color: var(--color-text);
  opacity: 0.85;
  white-space: nowrap;
  flex-shrink: 0;
  width: 42px;
  text-align: right;
  line-height: 1;
}
```

- [ ] **Step 8: 修复压缩态 hpColor() 函数 hardcode**

找到：
```js
function hpColor(side, unit) {
  const pct = hpPercent(unit)
  if (side === 'player') return `rgba(46,125,50,${0.3 + pct / 100 * 0.5})`
  return `rgba(198,40,40,${0.3 + pct / 100 * 0.5})`
}
```

这个函数返回内联 style，改为返回对应阵营的 CSS 变量表达式无法直接传 rgba 计算。最简单的做法是给 `.compact-bar` 用 CSS 变量颜色固定色：

```js
function hpColor(side, unit) {
  const sideMap = { player: 'var(--color-player)', enemy: 'var(--color-enemy)', neutral: 'var(--color-neutral)', ally: 'var(--color-ally)' }
  return sideMap[side] || 'var(--color-enemy)'
}
```

注意 `hpColor` 调用方传的是小写 `'player'/'enemy'`，和上面 markerSideClass 映射是兼容的。

- [ ] **Step 9: 验证**

启动前端，进入战斗，确认：
- 我方 token 绿色边框 + 绿色背景
- 敌方 token 红色边框 + 红色背景
- AOE 范围标识（黄框/红框）仍然正常
- buff 角标显示正常

- [ ] **Step 10: Commit**

```
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "feat: BattleGrid — token 阵营染色走变量，去除 hardcode 颜色"
```

---

## Task 6: BattleGrid.vue — 战斗指令区暗刻按钮样式

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`

当前战斗指令区用 `ActionLink` 组件（▸前缀链接）。本任务在 BattleGrid 内局部改为暗刻按钮，不影响其他地方的 ActionLink。

- [ ] **Step 1: 替换指令区 ActionLink 为 button 元素**

找到 `cmd-actions` 区域（约 107-116 行）：

```html
<div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND' && !animating" :class="{ locked: showSkills || step === 'target' }">
  <ActionLink v-if="firstMainAction" :action="firstMainAction" @action="selectCommand(firstMainAction)" />
  <span v-if="skillActions.length > 0" class="cmd-item skill-btn"
        :class="{ active: showSkills }"
        @click="showSkills = true">▸技能</span>
  <ActionLink v-for="cmd in otherMainActions" :key="cmd.params?.command"
              :action="cmd"
              @action="selectCommand(cmd)" />
  <ActionLink v-if="fleeAction" :action="fleeAction" @action="selectCommand(fleeAction)" />
</div>
```

改为：

```html
<div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND' && !animating" :class="{ locked: showSkills || step === 'target' }">
  <button v-if="firstMainAction"
          class="cmd-btn"
          :class="{ disabled: firstMainAction.style === 'disabled' }"
          @click="selectCommand(firstMainAction)">
    {{ firstMainAction.label }}
  </button>
  <button v-if="skillActions.length > 0"
          class="cmd-btn"
          :class="{ active: showSkills }"
          @click="showSkills = true">技能</button>
  <button v-for="cmd in otherMainActions" :key="cmd.params?.command"
          class="cmd-btn"
          :class="{ disabled: cmd.style === 'disabled' }"
          @click="selectCommand(cmd)">
    {{ cmd.label }}
  </button>
  <button v-if="fleeAction"
          class="cmd-btn"
          @click="selectCommand(fleeAction)">
    {{ fleeAction.label }}
  </button>
</div>
```

- [ ] **Step 2: 替换技能展开区 ActionLink 为 button**

找到 `cmd-sub` 区域中技能列表（约 122-126 行）：

```html
<template v-else-if="showSkills">
  <ActionLink v-for="cmd in skillActions" :key="cmd.params?.command"
              :action="cmd"
              @action="selectCommand(cmd)" />
  <span class="cmd-item cancel-item" @click="cancelSub">▸取消</span>
</template>
```

改为：

```html
<template v-else-if="showSkills">
  <button v-for="cmd in skillActions" :key="cmd.params?.command"
          class="cmd-btn skill-cmd-btn"
          :class="{ disabled: cmd.style === 'disabled' }"
          :title="cmd.params?.description || ''"
          @click="selectCommand(cmd)">
    {{ cmd.label }}
  </button>
  <button class="cmd-btn cancel-btn" @click="cancelSub">取消</button>
</template>
```

注意 `:title="cmd.params?.description || ''"` — 浏览器原生 tooltip，等 Task 7 加 description 字段后生效。

- [ ] **Step 3: 替换取消选目标的 cancel 按钮**

找到（约 119-120 行）：
```html
<span class="cmd-item target-hint">{{ targetPrompt }}</span>
<span class="cmd-item cancel-item" @click="cancelSelect">▸取消</span>
```
改为：
```html
<span class="target-hint">{{ targetPrompt }}</span>
<button class="cmd-btn cancel-btn" @click="cancelSelect">取消</button>
```

- [ ] **Step 4: 添加暗刻按钮 CSS**

在 `<style scoped>` 中，**删除**以下旧样式：
```css
.skill-btn { ... }
.skill-btn.active { ... }
.cmd-item { ... }
.cmd-item:hover { ... }
.cmd-item.active { ... }
.cancel-item { ... }
.target-hint { ... }
```

**添加**新样式：
```css
.cmd-btn {
  font-family: inherit;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 0.3rem 0.6rem;
  background: color-mix(in srgb, var(--color-text) 4%, transparent);
  border: 2px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text);
  cursor: pointer;
  text-align: center;
  box-shadow: inset 0 2px 6px rgba(0,0,0,0.5), inset 0 -1px 0 rgba(255,255,255,0.04);
  transition: border-color 0.15s, background 0.15s, color 0.15s;
  white-space: nowrap;
  user-select: none;
}
.cmd-btn:hover {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 12%, transparent);
  color: var(--color-highlight);
}
.cmd-btn.active {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 18%, transparent);
  color: var(--color-highlight);
}
.cmd-btn.disabled {
  opacity: 0.35;
  pointer-events: none;
}
.cancel-btn {
  border-color: color-mix(in srgb, var(--color-enemy) 50%, transparent);
  color: var(--color-enemy);
}
.cancel-btn:hover {
  border-color: var(--color-enemy);
  background: color-mix(in srgb, var(--color-enemy) 12%, transparent);
  color: var(--color-enemy);
}
.target-hint {
  font-size: 0.82rem;
  color: var(--color-text);
  opacity: 0.5;
  cursor: default;
  padding: 0.3rem 0;
}
```

- [ ] **Step 5: 验证按钮效果**

启动前端，进入战斗：
- 指令区显示「攻击」「技能」「防御」「逃跑」四个暗刻按钮，无 ▸ 前缀
- hover 变蓝色高亮
- 点「技能」展开技能列表，技能按钮相同样式
- 「取消」按钮红色边框
- 技能 `disabled` 时（MP 不足）显示半透明

- [ ] **Step 6: Commit**

```
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "feat: 战斗指令区改为暗刻按钮，去 ▸ 前缀"
```

---

## Task 7: 技能 YAML 添加 description 字段 + actions.js 传给前端

**Files:**
- Modify: `mods/base-rules/skills/*.yaml`（共 15 个文件）
- Modify: `mods/base-rules/handlers/ui/actions.js`

- [ ] **Step 1: 在 actions.js 中传 description 到 params**

找到约 47 行位置，在 `params.put("prompt", prompt)` 后面加：

```js
// Skill description for tooltip
var description = skillDef.get("description");
if (description !== null) params.put("description", description);
```

- [ ] **Step 2: 给每个技能 YAML 加 description**

依次编辑以下文件，在 `id:` 之后、`name:` 之前或之后加一行 `description:`：

**`basic_attack.yaml`** — 在 `category:` 后加：
```yaml
description: "对目标造成 100% 物理伤害"
```

**`defend.yaml`** — 加：
```yaml
description: "进入防御姿态，减少本回合受到的伤害"
```

**`flee.yaml`** — 加：
```yaml
description: "尝试逃离战斗"
```

**`fireball.yaml`** — 加：
```yaml
description: "对目标造成火焰魔法伤害，附带灼烧效果"
```

**`ice_beam.yaml`** — 加：
```yaml
description: "冰霜射线，对目标造成冰系伤害"
```

**`crescent_slash.yaml`** — 加：
```yaml
description: "月牙斩，对目标造成大范围物理伤害"
```

**`cleave.yaml`** — 加：
```yaml
description: "顺劈斩，横扫前排多个目标"
```

**`pulse_wave.yaml`** — 加：
```yaml
description: "脉冲波，对范围内敌人造成伤害"
```

**`poison_dart.yaml`** — 加：
```yaml
description: "毒镖，命中目标并附加中毒状态"
```

**`heal.yaml`** — 加：
```yaml
description: "治愈术，恢复目标生命值"
```

**`war_cry.yaml`** — 加：
```yaml
description: "战吼，提升我方战斗力"
```

**`curse.yaml`** — 加：
```yaml
description: "诅咒，降低目标属性"
```

**`piercing_ray.yaml`** — 加：
```yaml
description: "贯穿射线，穿透目标列造成伤害"
```

**`cross_blast.yaml`** — 加：
```yaml
description: "十字爆裂，以目标为中心十字范围爆炸"
```

**`light_field.yaml`** — 加：
```yaml
description: "光击阵，在指定区域布置光能陷阱"
```

- [ ] **Step 3: 验证 tooltip 出现**

启动前端（或热加载），进入战斗，hover 技能按钮，浏览器原生 tooltip 显示技能描述文字。

- [ ] **Step 4: Commit**

```
git add mods/base-rules/skills/*.yaml mods/base-rules/handlers/ui/actions.js
git commit -m "feat: 技能 YAML 加 description 字段，actions.js 传给前端 tooltip"
```

---

## 自检

**Spec 覆盖检查：**
- [x] colors.yaml 更新 → Task 1
- [x] CSS 变量零 hardcode → Task 2~6
- [x] 能量条数字跟条色 → Task 3
- [x] 普通文字走 --color-text → Task 2（variables.css），Task 3、4、5 的 stat-bar-num
- [x] 战斗 token 阵营染色（四种） → Task 5
- [x] 不改格子/AOE/buff 标识 → Task 5 只改 .marker，格子仅修 terrain-cell hardcode bg
- [x] 暗刻按钮样式 → Task 6
- [x] 技能按钮去 ▸ → Task 6
- [x] 技能描述移 tooltip → Task 6 + Task 7

**Placeholder 扫描：** 无 TBD / TODO / 模糊描述。

**类型一致性：** `markerSideClass()` 函数在 Task 5 Step 2 定义，Task 5 Step 3 中 marker 对象的 `side` 字段值（大写 PLAYER/ENEMY/NEUTRAL/ALLY）与函数映射 key 一致。`cmd-btn` 类名在 Task 6 Step 1-3 添加，Task 6 Step 4 定义样式。

**布局约束检查：** 所有改动均为颜色/样式属性，无 grid、flex、width、height、position 变更。
