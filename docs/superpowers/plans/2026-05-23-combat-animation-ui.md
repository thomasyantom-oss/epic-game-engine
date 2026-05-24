# 战斗格子 UI 改造 + 基础动画播放器

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将战斗格子 UI 改造为 ITB 风格（深色底、粗边框、四角指示器、token 样式），并实现基础动画原语播放器（impact + shake + damage_number），使战斗结算有视觉反馈。

**Architecture:** 分两层实现——BattleGrid.vue 做纯视觉改造（CSS），新建 AnimationPlayer.vue 组件负责播放动画序列。后端 combat_events.js 扩展 animation 字段，前端 SnapshotRenderer 将动画序列传给播放器。

**Tech Stack:** Vue 3 (composition API), CSS animations, SVG (月牙斩等后续用)

---

## File Structure

| 文件 | 职责 |
|------|------|
| `frontend/src/components/combat/BattleGrid.vue` | 改造：深色格子、粗边框、token 样式、选中目标橙框、四角指示器 |
| `frontend/src/components/combat/AnimationLayer.vue` | 新建：覆盖在格子上的动画图层，播放原语序列 |
| `frontend/src/composables/useAnimationPlayer.js` | 新建：动画队列管理、时序控制逻辑 |
| `frontend/src/components/SnapshotRenderer.vue` | 改造：将 combat events 的 animation 字段传给 AnimationLayer |
| `mods/base-rules/handlers/combat/combat_events.js` | 改造：为攻击/防御/死亡事件添加 animation 字段 |

---

### Task 1: BattleGrid 格子视觉改造

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`

- [ ] **Step 1: 改造格子基础样式**

替换 `.terrain-cell` 和 `.marker` 相关样式为 ITB 风格：

```css
.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid #444;
  border-radius: 3px;
  background-color: #1a1a2e;
  position: relative;
  overflow: hidden;
}

.terrain-cell.target-cell {
  cursor: pointer;
  border: 4px solid var(--color-highlight);
  box-shadow: 0 0 10px rgba(255, 152, 0, 0.4), inset 0 0 6px rgba(255, 152, 0, 0.15);
}

.terrain-cell.target-cell:hover {
  box-shadow: 0 0 14px rgba(255, 152, 0, 0.5), inset 0 0 8px rgba(255, 152, 0, 0.2);
}

.marker {
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.75em;
}

.marker.player {
  color: var(--color-player);
  background: rgba(79, 195, 247, 0.15);
}

.marker.enemy {
  color: var(--color-enemy);
  background: rgba(229, 115, 115, 0.15);
}
```

- [ ] **Step 2: 移除旧的箭头标记符号，改为纯文本 token**

在 BattleGrid.vue 的 template 中，将 marker 显示从 `<span class="marker-num">{{ cell.marker.index }}</span>▶` 改为：

```html
<span v-if="cell.marker && cell.marker.alive" class="marker" :class="cell.marker.side">
  {{ cell.marker.side === 'player' ? 'P' : 'E' }}{{ cell.marker.index }}
</span>
```

同理对 enemy 侧也去掉 `◀` 符号。

- [ ] **Step 3: 添加四角指示器模板**

在每个 terrain-cell 内添加角标 slot（暂时只显示 HP）：

```html
<div
  v-for="(cell, idx) in playerCells"
  :key="'p'+idx"
  class="terrain-cell"
>
  <span v-if="cell.marker && cell.marker.alive" class="marker player">
    P{{ cell.marker.index }}
  </span>
  <span v-if="cell.marker && cell.marker.alive" class="corner-hp">
    {{ cell.marker.hp }}/{{ cell.marker.maxHp }}
  </span>
</div>
```

添加 corner-hp 样式：

```css
.corner-hp {
  position: absolute;
  bottom: 2px;
  right: 3px;
  font-size: 0.45em;
  color: #999;
  font-weight: bold;
}
```

- [ ] **Step 4: 修改 mapUnitsToGrid 传递 hp 数据**

修改 `mapUnitsToGrid` 函数使 marker 对象包含 hp/maxHp：

```javascript
function mapUnitsToGrid(units, side) {
  const markers = []
  const playerColMap = { BACK: 0, MID: 1, FRONT: 2 }
  const enemyColMap = { FRONT: 0, MID: 1, BACK: 2 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (playerColMap[unitRow] ?? 2) : (enemyColMap[unitRow] ?? 0)
    const row = unit.slot ?? idx % 3
    markers.push({
      col, row, side, index: idx + 1,
      alive: unit.alive, id: unit.id,
      hp: unit.hp, maxHp: unit.maxHp
    })
  })
  return markers
}
```

- [ ] **Step 5: 移除侧栏 HP bar 中的冗余信息，确认格子内 HP 可读**

在浏览器中启动 `cd frontend && npm run dev`，进入战斗确认：
- 格子深色底 `#1a1a2e`
- 边框粗 3px `#444`
- Token 显示 P1/E1 带对应颜色背景
- 右下角有 HP 数字
- 选中目标时粗橙色边框 + glow

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "feat: 战斗格子 UI 改造为 ITB 风格 — 深色底、粗边框、token 样式、角标 HP"
```

---

### Task 2: 动画播放器 composable

**Files:**
- Create: `frontend/src/composables/useAnimationPlayer.js`

- [ ] **Step 1: 创建 useAnimationPlayer composable**

```javascript
import { ref } from 'vue'

export function useAnimationPlayer() {
  const playing = ref(false)
  const activeAnimations = ref([])
  let queue = []
  let currentResolve = null

  function play(events) {
    if (!events || events.length === 0) return Promise.resolve()
    playing.value = true
    queue = [...events]
    return new Promise(resolve => {
      currentResolve = resolve
      playNext()
    })
  }

  function playNext() {
    if (queue.length === 0) {
      playing.value = false
      activeAnimations.value = []
      if (currentResolve) currentResolve()
      return
    }

    const event = queue.shift()
    const anims = event.animation || []
    if (anims.length === 0) {
      setTimeout(playNext, 400)
      return
    }

    playAnimationSequence(anims)
  }

  function playAnimationSequence(anims) {
    let delay = 0
    const active = []

    for (const anim of anims) {
      const duration = getAnimDuration(anim)
      active.push({ ...anim, startDelay: delay, duration })

      if (anim.type === 'impact' || anim.type === 'shake' || anim.type === 'damage_number') {
        // 命中类并行播放，不增加 delay
      } else {
        delay += duration
      }
    }

    activeAnimations.value = active
    const totalDuration = Math.max(...active.map(a => a.startDelay + a.duration))

    setTimeout(() => {
      activeAnimations.value = []
      setTimeout(playNext, 100)
    }, totalDuration)
  }

  function getAnimDuration(anim) {
    switch (anim.type) {
      case 'impact': return 150
      case 'shake': return 250
      case 'damage_number': return 500
      default: return 300
    }
  }

  return { playing, activeAnimations, play }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/composables/useAnimationPlayer.js
git commit -m "feat: useAnimationPlayer composable — 动画队列管理和时序控制"
```

---

### Task 3: AnimationLayer 组件

**Files:**
- Create: `frontend/src/components/combat/AnimationLayer.vue`

- [ ] **Step 1: 创建 AnimationLayer 组件**

该组件作为绝对定位图层覆盖在格子区域上，根据 activeAnimations 渲染各原语效果：

```vue
<template>
  <div class="animation-layer">
    <template v-for="(anim, idx) in animations" :key="idx">
      <!-- Impact: 目标格白闪 -->
      <div v-if="anim.type === 'impact'"
           class="anim-impact"
           :style="impactStyle(anim)" />

      <!-- Shake: 让 BattleGrid 通过 class 处理，这里只是占位触发 -->

      <!-- Damage number -->
      <div v-if="anim.type === 'damage_number'"
           class="anim-damage-number"
           :class="damageClass(anim)"
           :style="damageStyle(anim)">
        {{ formatDamage(anim.value) }}
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  animations: { type: Array, default: () => [] },
  cellPositions: { type: Object, default: () => ({}) }
})

function getCellPos(targetId) {
  return props.cellPositions[targetId] || { x: 0, y: 0 }
}

function impactStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function damageStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: (pos.x + pos.w / 2) + 'px',
    top: pos.y + 'px',
    animationDelay: anim.startDelay + 'ms'
  }
}

function damageClass(anim) {
  if (anim.color === 'heal') return 'heal'
  if (anim.color === 'miss') return 'miss'
  return 'damage'
}

function formatDamage(value) {
  if (value === undefined || value === null) return ''
  return value > 0 ? '+' + value : '' + value
}
</script>

<style scoped>
.animation-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 10;
}

.anim-impact {
  position: absolute;
  border-radius: 3px;
  animation: impact-flash ease-out forwards;
}

@keyframes impact-flash {
  0% { background: rgba(255, 255, 255, 0.5); }
  100% { background: transparent; }
}

.anim-damage-number {
  position: absolute;
  transform: translateX(-50%);
  font-size: 1.1em;
  font-weight: 900;
  text-shadow:
    -1px -1px 0 #000, 1px -1px 0 #000,
    -1px 1px 0 #000, 1px 1px 0 #000,
    0 0 6px rgba(255, 82, 82, 0.5);
  animation: damage-pop 500ms ease-out forwards;
  white-space: nowrap;
}

.anim-damage-number.damage { color: #ff5252; }
.anim-damage-number.heal { color: #66bb6a; text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 6px rgba(102, 187, 106, 0.5); }
.anim-damage-number.miss { color: #999; font-size: 0.9em; }

@keyframes damage-pop {
  0% { transform: translateX(-50%) translateY(4px) scale(0.8); opacity: 0; }
  15% { transform: translateX(-50%) translateY(0) scale(1.1); opacity: 1; }
  30% { transform: translateX(-50%) translateY(0) scale(1); opacity: 1; }
  70% { transform: translateX(-50%) translateY(0); opacity: 1; }
  100% { transform: translateX(-50%) translateY(-8px); opacity: 0; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/combat/AnimationLayer.vue
git commit -m "feat: AnimationLayer 组件 — impact 白闪 + damage_number 弹出"
```

---

### Task 4: 集成 AnimationLayer 到 BattleGrid

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`
- Modify: `frontend/src/components/SnapshotRenderer.vue`

- [ ] **Step 1: BattleGrid 添加 AnimationLayer 和 shake class**

在 BattleGrid.vue 的 template 中，在 `.grid-area` 内添加 AnimationLayer：

```html
<div class="grid-area" ref="gridAreaRef">
  <!-- 现有的 player-grid / enemy-grid -->
  ...
  <!-- 动画图层 -->
  <AnimationLayer :animations="activeAnimations" :cell-positions="cellPositions" />
</div>
```

在 script 中导入并连接：

```javascript
import AnimationLayer from './AnimationLayer.vue'
import { useAnimationPlayer } from '../../composables/useAnimationPlayer.js'
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'

const { playing, activeAnimations, play } = useAnimationPlayer()

const gridAreaRef = ref(null)
const cellPositions = ref({})
```

添加 `cellPositions` 的计算（在 grid 渲染后从 DOM 读取格子位置）：

```javascript
function updateCellPositions() {
  if (!gridAreaRef.value) return
  const gridRect = gridAreaRef.value.getBoundingClientRect()
  const positions = {}
  const cells = gridAreaRef.value.querySelectorAll('[data-unit-id]')
  cells.forEach(el => {
    const id = el.dataset.unitId
    const rect = el.getBoundingClientRect()
    positions[id] = {
      x: rect.left - gridRect.left,
      y: rect.top - gridRect.top,
      w: rect.width,
      h: rect.height
    }
  })
  cellPositions.value = positions
}
```

给 terrain-cell 添加 `data-unit-id` 属性：

```html
<div
  v-for="(cell, idx) in enemyCells"
  :key="'e'+idx"
  class="terrain-cell"
  :class="{
    'target-cell': selectingTarget && cell.marker && cell.marker.alive,
    'shaking': isShaking(cell.marker?.id)
  }"
  :data-unit-id="cell.marker?.id"
  @click="onCellClick(cell)"
>
```

添加 shake 检测和样式：

```javascript
function isShaking(unitId) {
  if (!unitId) return false
  return activeAnimations.value.some(a => a.type === 'shake' && a.target === unitId)
}
```

```css
.terrain-cell.shaking {
  animation: cell-shake 250ms ease-in-out;
}

@keyframes cell-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-3px); }
  40% { transform: translateX(3px); }
  60% { transform: translateX(-2px); }
  80% { transform: translateX(2px); }
}
```

- [ ] **Step 2: 暴露 play 函数给父组件，同步 animating 状态**

在 BattleGrid.vue 中用 `defineExpose` 暴露 play，并同步 playing 到 emit：

```javascript
watch(playing, (val) => {
  emit('update:animating', val)
})

defineExpose({ play, updateCellPositions })
```

- [ ] **Step 3: SnapshotRenderer 调用 BattleGrid.play()**

在 SnapshotRenderer.vue 中修改动画播放逻辑。替换现有的 `setInterval` 方式：

```javascript
const battleGridRef = ref(null)

watch(() => props.snapshot?.combat?.events, async (events) => {
  if (!events || events.length === 0) {
    isAnimating.value = false
    return
  }
  isAnimating.value = true
  await nextTick()
  if (battleGridRef.value) {
    battleGridRef.value.updateCellPositions()
    await battleGridRef.value.play(events)
  }
  isAnimating.value = false
}, { immediate: true })
```

在 template 中给 BattleGrid 添加 ref：

```html
<BattleGrid ref="battleGridRef" :combat="snapshot.combat" :player-id="snapshot.playerId"
            :commands="combatActions" :animating="isAnimating"
            @command="$emit('action', $event)" />
```

- [ ] **Step 4: 在浏览器中测试**

启动 `./start.sh`，进入战斗，发起攻击。确认：
- 格子样式已改造（深色底、粗边框）
- 攻击后目标格有白闪效果
- Token 抖动可见
- 伤害数字大而清晰弹出
- 动画播放期间命令区域隐藏

（注意：后端尚未添加 animation 字段，这步需要先做 Task 5 才能看到效果）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/combat/BattleGrid.vue frontend/src/components/SnapshotRenderer.vue
git commit -m "feat: 集成 AnimationLayer 到 BattleGrid — shake/impact/damage_number 播放"
```

---

### Task 5: 后端 combat_events.js 添加 animation 字段

**Files:**
- Modify: `mods/base-rules/handlers/combat/combat_events.js`

- [ ] **Step 1: 为攻击事件添加 animation 序列**

在 `combat.damage_dealt` 事件处理中，在 `evt.put("effects", effects)` 后添加 animation 字段：

```javascript
// Animation sequence
var animation = engine.newList();

var impactAnim = engine.newMap();
impactAnim.put("type", "impact");
impactAnim.put("target", targetId);
animation.add(impactAnim);

var shakeAnim = engine.newMap();
shakeAnim.put("type", "shake");
shakeAnim.put("target", targetId);
shakeAnim.put("intensity", "normal");
animation.add(shakeAnim);

var dmgAnim = engine.newMap();
dmgAnim.put("type", "damage_number");
dmgAnim.put("target", targetId);
dmgAnim.put("value", -damage);
dmgAnim.put("color", "damage");
animation.add(dmgAnim);

evt.put("animation", animation);
```

- [ ] **Step 2: 为死亡事件添加 animation**

在 `combat.unit_death` 事件处理中添加：

```javascript
var animation = engine.newList();

var shakeAnim = engine.newMap();
shakeAnim.put("type", "shake");
shakeAnim.put("target", deadId);
shakeAnim.put("intensity", "heavy");
animation.add(shakeAnim);

evt.put("animation", animation);
```

- [ ] **Step 3: 后端 SnapshotService 传递 animation 字段**

查看 `SnapshotService.java:246-272`，当前只读取 segments 和 effects。需要也读取 animation。

修改 `WorldSnapshot.java`，在 CombatEvent record 中添加 animation：

```java
public record CombatEvent(List<TextSegment> segments, List<Effect> effects, List<Map<String, Object>> animation) {}
```

修改 `SnapshotService.java` 中 buildCombatSnapshot 的事件读取部分，在读取 effects 之后添加：

```java
List<Map<String, Object>> animation = new ArrayList<>();
List<Object> anims = (List<Object>) evt.get("animation");
if (anims != null) {
    for (Object animObj : anims) {
        animation.add((Map<String, Object>) animObj);
    }
}
events.add(new WorldSnapshot.CombatEvent(segments, effects, animation));
```

- [ ] **Step 4: 在浏览器中完整测试**

启动 `./start.sh`，进入战斗，攻击敌人：
- 攻击后目标格白闪
- Token 抖动
- 伤害数字弹出 "-X"
- 动画播完后才回到命令选择

- [ ] **Step 5: Commit**

```bash
git add mods/base-rules/handlers/combat/combat_events.js backend/src/main/java/com/epic/engine/snapshot/WorldSnapshot.java backend/src/main/java/com/epic/engine/snapshot/SnapshotService.java
git commit -m "feat: combat events 添加 animation 字段 — 攻击产生 impact+shake+damage_number"
```

---

### Task 6: 格子区域的 style 调整确保 grid-area position:relative

**Files:**
- Modify: `frontend/src/components/combat/BattleGrid.vue`

- [ ] **Step 1: 确保 .grid-area 有 position:relative**

AnimationLayer 需要 `position: absolute` 定位在格子区域上。确认 `.grid-area` 样式中有：

```css
.grid-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5%;
  padding: 5%;
  min-width: 0;
  min-height: 0;
  position: relative;
}
```

- [ ] **Step 2: 移除旧的 .marker-num 和箭头相关样式**

删除不再使用的 CSS：

```css
/* 删除以下样式 */
.marker-num { ... }
```

- [ ] **Step 3: 最终浏览器验证**

确认所有视觉效果完整：
- 深色格子 + 粗边框
- P1/E1 token 带颜色背景
- 右下角 HP 数字
- 选中目标粗橙框 + glow
- 攻击动画完整流程：白闪 → 抖动 → 数字弹出
- 动画结束后回到 COMMAND 状态

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "fix: grid-area position:relative + 清理旧 marker 样式"
```
