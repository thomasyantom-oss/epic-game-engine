# Unified Panel Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the main panel to switch between exploration (map + info sidebar) and combat (3×3 terrain battlefield + status bars + command flow) without full-screen replacement. Remove D-pad, remove terrain legend, move POI interactions to the info sidebar.

**Architecture:** The `App.vue` game grid stays as 4 panels. The top-left panel's content switches based on `combat.active`: exploration shows MapGrid + MapInfoPanel side-by-side; combat shows a new BattleView with 3×3 terrain grid, triangular markers, flanking status bars, and a step-based command UI. The right-bottom panel becomes a quick-action bar (cancel button during combat).

**Tech Stack:** Vue 3, CSS Grid/Flexbox, existing backend APIs

---

## Phase 1: Exploration Panel Cleanup

### Task 1: Remove D-pad and simplify NavigationPanel

**Files:**
- Modify: `frontend/src/components/NavigationPanel.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Replace NavigationPanel with empty quick-action shell**

Replace `frontend/src/components/NavigationPanel.vue` entirely:

```vue
<template>
  <div class="quick-actions">
    <div v-if="actions.length > 0" class="action-list">
      <div
        v-for="action in actions"
        :key="action.id"
        class="action-btn"
        @click="$emit('action', action)"
      >{{ action.label }}</div>
    </div>
    <div v-else class="empty-hint">
      <span style="color: #666">快捷行动</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
    actions: { type: Array, default: () => [] }
})

defineEmits(['action'])
</script>

<style scoped>
.quick-actions {
    display: flex;
    flex-direction: column;
    height: 100%;
    gap: 0.4rem;
    padding: 0.3rem;
}

.action-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.action-btn {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.9em;
    text-align: center;
    transition: border-color 0.2s, background-color 0.2s;
}

.action-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.empty-hint {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
}
</style>
```

- [ ] **Step 2: Remove mapMove actions from App.vue currentActions**

In `frontend/src/App.vue`, replace the `currentActions` computed:

```javascript
const currentActions = computed(() => {
    return []
})
```

- [ ] **Step 3: Remove handleAction's mapMove branch**

In `frontend/src/App.vue`, replace handleAction:

```javascript
async function handleAction(action) {
    sceneHistory.value = [{ type: 'action', text: action.label }]
    if (mapState.poi && action.type === 'move') {
        clearPoi()
    }
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
```

- [ ] **Step 4: Rename navTabs label**

```javascript
const navTabs = [
  { id: 'actions', label: '快捷' }
]
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/NavigationPanel.vue frontend/src/App.vue
git commit -m "feat: replace D-pad with quick-action panel, remove direction buttons"
```

---

### Task 2: Rework MapInfoPanel content

**Files:**
- Modify: `frontend/src/components/MapInfoPanel.vue`

- [ ] **Step 1: Replace MapInfoPanel with new content**

Replace `frontend/src/components/MapInfoPanel.vue` entirely:

```vue
<template>
  <div class="map-info">
    <div class="info-section">
      <div class="region-name">{{ mapName }}</div>
      <div class="coordinates">({{ playerX }}, {{ playerY }})</div>
    </div>

    <div class="info-section" v-if="currentTerrain">
      <div class="section-label">地形</div>
      <div class="terrain-display">
        <span class="terrain-char" :style="{ backgroundColor: currentTerrain.color, color: currentTerrain.textColor }">{{ currentTerrain.char }}</span>
        <span class="terrain-name">{{ currentTerrain.name }}</span>
      </div>
    </div>

    <div class="info-section" v-if="poiAction">
      <div class="section-label">交互</div>
      <div class="poi-btn" @click="$emit('poi-action', poiAction)">
        {{ poiAction.label }}
      </div>
    </div>

    <div class="info-section">
      <div class="section-label">怪物</div>
      <div class="monster-list">
        <span class="no-data">暂无</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useMap } from '../composables/useMap.js'

const { mapState } = useMap()

defineEmits(['poi-action'])

const TERRAIN_NAMES = { forest: '密林', grass: '草地', road: '道路', sand: '沙地', water: '水域', town: '城镇', mountain: '山地' }

const mapName = computed(() => mapState.mapData?.name || '未知区域')
const playerX = computed(() => mapState.playerX)
const playerY = computed(() => mapState.playerY)

const currentTerrain = computed(() => {
  if (!mapState.mapData) return null
  const row = mapState.mapData.terrain[mapState.playerY]
  if (!row) return null
  const ch = row.charAt(mapState.playerX)
  const t = mapState.mapData.terrains.find(t => t.char === ch)
  if (!t) return null
  return { ...t, name: TERRAIN_NAMES[t.id] || t.id }
})

const poiAction = computed(() => {
  if (!mapState.poi) return null
  return {
    id: 'poi-' + mapState.poi.id,
    label: mapState.poi.label,
    type: 'move',
    params: { target: mapState.poi.target }
  }
})
</script>

<style scoped>
.map-info {
  height: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  overflow-y: auto;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.region-name {
  font-size: 1em;
  font-weight: bold;
  color: var(--text-color);
}

.coordinates {
  font-size: 0.8em;
  color: #888;
}

.section-label {
  font-size: 0.75em;
  color: #666;
  letter-spacing: 0.05em;
}

.terrain-display {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.terrain-char {
  width: 1.6em;
  height: 1.6em;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  font-size: 0.85em;
  border: 1px solid #333;
}

.terrain-name {
  font-size: 0.85em;
  color: #ccc;
}

.poi-btn {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--panel-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85em;
  text-align: center;
  transition: border-color 0.2s, background-color 0.2s;
}

.poi-btn:hover {
  border-color: var(--link-color);
  background-color: rgba(233, 69, 96, 0.1);
}

.monster-list {
  font-size: 0.85em;
}

.no-data {
  color: #555;
  font-style: italic;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/MapInfoPanel.vue
git commit -m "feat: rework map info panel with terrain, POI, and monster placeholder"
```

---

## Phase 2: Battle View Rebuild

### Task 3: Create new BattleField component (3×3 terrain grid + triangles)

**Files:**
- Create: `frontend/src/components/combat/BattleGrid.vue`

- [ ] **Step 1: Create BattleGrid component**

```vue
<template>
  <div class="battle-grid-container">
    <div class="status-col player-col">
      <div v-for="(unit, idx) in playerUnits" :key="unit.id" class="unit-status">
        <div class="unit-header" :class="{ active: unit.id === currentActorId }">
          <span class="unit-idx">{{ idx + 1 }}</span>
          <span class="unit-name player-name">{{ unit.name }}</span>
        </div>
        <div class="hp-bar">
          <div class="hp-fill" :style="{ width: (unit.hp / unit.maxHp * 100) + '%' }"></div>
        </div>
      </div>
    </div>

    <div class="battle-field">
      <div class="terrain-grid">
        <div
          v-for="(cell, idx) in terrainCells"
          :key="idx"
          class="terrain-cell"
          :style="{ backgroundColor: cell.color }"
        >
          <span v-if="cell.marker" class="marker" :class="cell.marker.side">
            {{ cell.marker.side === 'player' ? '▶' : '◀' }}
            <span class="marker-idx">{{ cell.marker.index }}</span>
          </span>
        </div>
      </div>
    </div>

    <div class="status-col enemy-col">
      <div v-for="(unit, idx) in enemyUnits" :key="unit.id" class="unit-status">
        <div class="unit-header">
          <span class="unit-idx">{{ idx + 1 }}</span>
          <span class="unit-name enemy-name">{{ unit.name }}</span>
        </div>
        <div class="hp-bar enemy-bar">
          <div class="hp-fill enemy-fill" :style="{ width: (unit.hp / unit.maxHp * 100) + '%' }"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useMap } from '../../composables/useMap.js'

const props = defineProps({
  combatants: { type: Array, required: true },
  currentActorId: { type: String, default: '' }
})

const { mapState } = useMap()

const playerUnits = computed(() =>
  props.combatants.filter(c => c.side === 'PLAYER' && c.alive)
)

const enemyUnits = computed(() =>
  props.combatants.filter(c => c.side === 'ENEMY' && c.alive)
)

const terrainCells = computed(() => {
  const cells = []
  const map = mapState.mapData
  const px = mapState.playerX
  const py = mapState.playerY

  for (let dy = -1; dy <= 1; dy++) {
    for (let dx = -1; dx <= 1; dx++) {
      const x = px + dx
      const y = py + dy
      let color = '#333'
      if (map && y >= 0 && y < map.height && x >= 0 && x < map.width) {
        const ch = map.terrain[y].charAt(x)
        const terrain = map.terrains.find(t => t.char === ch)
        if (terrain) color = terrain.color
      }

      const marker = getMarkerAt(dx + 1, dy + 1)
      cells.push({ color, marker })
    }
  }
  return cells
})

function getMarkerAt(col, row) {
  // Map combatants to 3x3 grid positions
  // Players: col 0 = back, col 1 = mid, col 2 = front (right side)
  // Enemies: col 2 = front (left side), col 1 = mid, col 0 = back
  const playerPositions = mapUnitsToGrid(playerUnits.value, 'player')
  const enemyPositions = mapUnitsToGrid(enemyUnits.value, 'enemy')

  const pMarker = playerPositions.find(p => p.col === col && p.row === row)
  if (pMarker) return pMarker

  const eMarker = enemyPositions.find(p => p.col === col && p.row === row)
  if (eMarker) return eMarker

  return null
}

function mapUnitsToGrid(units, side) {
  const markers = []
  const rowMap = { FRONT: 2, MID: 1, BACK: 0 }
  const enemyRowMap = { FRONT: 0, MID: 1, BACK: 2 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (rowMap[unitRow] ?? 2) : (enemyRowMap[unitRow] ?? 0)
    const row = unit.slot ?? idx % 3
    markers.push({ col, row, side, index: idx + 1 })
  })
  return markers
}
</script>

<style scoped>
.battle-grid-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  height: 100%;
  padding: 0.5rem;
  box-sizing: border-box;
}

.status-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.5rem;
  min-width: 5rem;
}

.unit-status {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.unit-header {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8em;
}

.unit-header.active {
  text-shadow: 0 0 4px var(--link-color);
}

.unit-idx {
  font-size: 0.7em;
  color: #888;
  width: 1em;
}

.player-name { color: #4ecdc4; }
.enemy-name { color: #e94560; }

.hp-bar {
  height: 4px;
  background: #333;
  border-radius: 2px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  background: #4ecdc4;
  transition: width 0.3s;
}

.enemy-bar .hp-fill, .enemy-fill {
  background: #e94560;
}

.battle-field {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.terrain-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  width: 12rem;
  height: 12rem;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #222;
  border-radius: 2px;
  position: relative;
}

.marker {
  font-size: 1.4rem;
  font-weight: bold;
  position: relative;
}

.marker.player { color: #4ecdc4; }
.marker.enemy { color: #e94560; }

.marker-idx {
  position: absolute;
  top: -0.3em;
  right: -0.6em;
  font-size: 0.45em;
  color: #fff;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/combat/BattleGrid.vue
git commit -m "feat: add BattleGrid with 3x3 terrain cells and triangle markers"
```

---

### Task 4: Create step-based CommandBar component

**Files:**
- Create: `frontend/src/components/combat/CommandBar.vue`

- [ ] **Step 1: Create CommandBar component**

```vue
<template>
  <div class="command-bar">
    <div v-if="currentActor" class="command-flow">
      <div class="actor-label">{{ currentActor.name }} 的回合</div>
      <div v-if="step === 'command'" class="options">
        <div class="cmd-btn" @click="selectCommand('ATTACK')">攻击</div>
        <div class="cmd-btn" @click="selectCommand('DEFEND')">防御</div>
        <div class="cmd-btn" @click="selectCommand('SKILL')">技能</div>
        <div class="cmd-btn" @click="selectCommand('ITEM')">道具</div>
        <div class="cmd-btn" @click="selectCommand('FLEE')">逃跑</div>
      </div>
      <div v-else-if="step === 'target'" class="options">
        <div class="target-header">选择目标：</div>
        <div
          v-for="(target, idx) in targets"
          :key="target.id"
          class="cmd-btn target-btn"
          @click="selectTarget(target.id)"
        >
          <span class="target-idx">{{ idx + 1 }}</span> {{ target.name }}
          <span class="target-hp">({{ target.hp }}/{{ target.maxHp }})</span>
        </div>
      </div>
    </div>
    <div v-else class="waiting">
      结算中...
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  currentActor: { type: Object, default: null },
  targets: { type: Array, default: () => [] }
})

const emit = defineEmits(['command', 'cancel'])
const step = ref('command')
const selectedCommand = ref(null)

watch(() => props.currentActor, () => {
  step.value = 'command'
  selectedCommand.value = null
})

function selectCommand(cmd) {
  if (cmd === 'DEFEND' || cmd === 'FLEE') {
    emit('command', { actorId: props.currentActor.id, type: cmd, targetId: null })
    return
  }
  if (cmd === 'SKILL' || cmd === 'ITEM') {
    // Placeholder — for now treat as attack
    selectedCommand.value = 'ATTACK'
    step.value = 'target'
    return
  }
  selectedCommand.value = cmd
  step.value = 'target'
}

function selectTarget(targetId) {
  emit('command', { actorId: props.currentActor.id, type: selectedCommand.value, targetId })
  step.value = 'command'
  selectedCommand.value = null
}

function cancel() {
  if (step.value === 'target') {
    step.value = 'command'
    selectedCommand.value = null
  } else {
    emit('cancel')
  }
}

defineExpose({ cancel })
</script>

<style scoped>
.command-bar {
  padding: 0.4rem 0.5rem;
  border-top: 1px solid var(--panel-border-color);
}

.command-flow {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  flex-wrap: wrap;
}

.actor-label {
  font-size: 0.85em;
  color: #4ecdc4;
  white-space: nowrap;
}

.options {
  display: flex;
  gap: 0.3rem;
  align-items: center;
  flex-wrap: wrap;
}

.target-header {
  font-size: 0.8em;
  color: #999;
  margin-right: 0.3rem;
}

.cmd-btn {
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--panel-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8em;
  transition: border-color 0.2s, background-color 0.2s;
}

.cmd-btn:hover {
  border-color: var(--link-color);
  background-color: rgba(233, 69, 96, 0.1);
}

.target-btn {
  border-color: #e94560;
}

.target-idx {
  color: #e94560;
  font-weight: bold;
}

.target-hp {
  color: #888;
  font-size: 0.9em;
}

.waiting {
  color: #666;
  font-style: italic;
  font-size: 0.85em;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/combat/CommandBar.vue
git commit -m "feat: add step-based CommandBar with command → target flow"
```

---

### Task 5: Rebuild CombatView to embed in main panel

**Files:**
- Modify: `frontend/src/components/combat/CombatView.vue`

- [ ] **Step 1: Rewrite CombatView to use BattleGrid + CommandBar**

Replace `frontend/src/components/combat/CombatView.vue` entirely:

```vue
<template>
  <div class="combat-view">
    <BattleGrid
      :combatants="combat.state?.combatants || []"
      :current-actor-id="currentActor?.id || ''"
    />
    <CommandBar
      ref="commandBarRef"
      :current-actor="currentActor"
      :targets="combat.validTargets"
      @command="onCommand"
      @cancel="onCancel"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import BattleGrid from './BattleGrid.vue'
import CommandBar from './CommandBar.vue'
import { useCombat } from '../../composables/useCombat.js'
import { useGameState } from '../../composables/useGameState.js'

const { combat, getCurrentActor, addCommand, allCommandsIssued, executeRound, loadTargets, exitCombat } = useCombat()
const { state, initialize } = useGameState()

const commandBarRef = ref(null)
const currentActor = computed(() => getCurrentActor())

async function onCommand(cmd) {
    addCommand(cmd.actorId, cmd.type, cmd.targetId)
    if (allCommandsIssued()) {
        await new Promise(r => setTimeout(r, 800))
        await executeRound(state.playerId)

        if (combat.state?.phase === 'VICTORY' || combat.state?.phase === 'DEFEAT') {
            setTimeout(() => {
                exitCombat()
                initialize()
            }, 2000)
        }
    } else {
        await loadTargets(state.playerId)
    }
}

function onCancel() {
    // Future: undo last command
}
</script>

<style scoped>
.combat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.combat-view > :first-child {
  flex: 1;
  min-height: 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/combat/CombatView.vue
git commit -m "feat: rebuild CombatView with BattleGrid and CommandBar"
```

---

### Task 6: Embed combat in main panel (remove full-screen replacement)

**Files:**
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Move combat inside main panel**

In `frontend/src/App.vue`, change the template. Replace:

```html
<CombatView v-if="combat.active" />
<div v-else class="game-grid">
```

With:

```html
<div class="game-grid">
```

And change the map tab template from:

```html
<template #map>
  <div class="map-split">
    <MapGrid :map-size="settings.mapSize || 10" />
    <MapInfoPanel @poi-action="handleAction" />
  </div>
</template>
```

To:

```html
<template #map>
  <CombatView v-if="combat.active" />
  <div v-else class="map-split">
    <MapGrid :map-size="settings.mapSize || 10" />
    <MapInfoPanel @poi-action="handleAction" />
  </div>
</template>
```

- [ ] **Step 2: Add cancel button to quick-actions during combat**

Update `currentActions` computed:

```javascript
const currentActions = computed(() => {
    if (combat.active) {
        return [{ id: 'cancel', label: '取消', type: 'combatCancel', params: {} }]
    }
    return []
})
```

Add combatCancel handling in handleAction:

```javascript
async function handleAction(action) {
    if (action.type === 'combatCancel') {
        // Future: implement undo
        return
    }
    sceneHistory.value = [{ type: 'action', text: action.label }]
    if (mapState.poi && action.type === 'move') {
        clearPoi()
    }
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
```

- [ ] **Step 3: Update mainTabs label to change during combat**

Replace the static mainTabs with a computed:

```javascript
const mainTabs = computed(() => [
  { id: 'map', label: combat.active ? '战场' : '地图' }
])
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat: embed combat in main panel instead of full-screen replacement"
```

---

### Task 7: Wire combat log to event panel

**Files:**
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Add combat results watcher**

In `frontend/src/App.vue`, add a watcher for combat results after the existing watchers:

```javascript
watch(() => combat.state?.phase, (phase) => {
    if (phase === 'VICTORY') {
        sceneHistory.value = [{ type: 'scene', segments: [{ text: '战斗胜利！', color: '#4ecdc4' }] }]
    } else if (phase === 'DEFEAT') {
        sceneHistory.value = [{ type: 'scene', segments: [{ text: '战斗失败...', color: '#e94560' }] }]
    }
})

watch(() => combat.results, (results) => {
    if (results && results.length > 0) {
        const entries = results.map(r => ({
            type: 'scene',
            segments: [{ text: r.description || `${r.attackerName} 对 ${r.targetName} 造成 ${r.damage} 点伤害`, color: '#e0e0e0' }]
        }))
        sceneHistory.value = entries
    }
}, { deep: true })
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat: display combat results in event panel"
```

---

### Task 8: Clean up old combat components

**Files:**
- Delete: `frontend/src/components/combat/BattleField.vue`
- Delete: `frontend/src/components/combat/CommandPanel.vue`
- Delete: `frontend/src/components/combat/CombatStatusPanel.vue`
- Delete: `frontend/src/components/combat/CombatLogPanel.vue`

- [ ] **Step 1: Remove old combat component files**

```bash
rm frontend/src/components/combat/BattleField.vue
rm frontend/src/components/combat/CommandPanel.vue
rm frontend/src/components/combat/CombatStatusPanel.vue
rm frontend/src/components/combat/CombatLogPanel.vue
```

- [ ] **Step 2: Commit**

```bash
git add -A frontend/src/components/combat/
git commit -m "chore: remove old combat UI components replaced by BattleGrid/CommandBar"
```

---

### Task 9: Manual verification and devlog update

- [ ] **Step 1: Start app and verify exploration**

Run: `./start.sh`

Verify at http://localhost:5173:
1. Map renders with auto-sized cells
2. Right sidebar shows: region name, coordinates, current terrain, POI button when on POI
3. No D-pad anywhere
4. WASD/arrow keys move player
5. Clicking map cells triggers pathfinding
6. Right-bottom panel shows "快捷行动" (empty for now)

- [ ] **Step 2: Verify combat**

Walk to forest → trigger combat:
1. Main panel switches to battle view (3×3 terrain grid with colored cells)
2. Player units shown as green ▶ triangles with index numbers
3. Enemy units shown as red ◀ triangles
4. Left/right status bars show names + HP bars
5. Bottom command bar shows "[name] 的回合" + command buttons
6. Click 攻击 → target list appears
7. Click target → next actor's turn
8. All actors done → round resolves
9. Tab label shows "战场" during combat, "地图" during exploration
10. Event panel shows combat results

- [ ] **Step 3: Update devlog**

Add to `docs/devlog.md` under 2026-05-21:

```markdown
### 面板布局重构

- 删除方向键 D-pad（键盘移动足够）
- 地图信息栏：地域名 + 坐标 + 当前地形 + POI 按钮 + 怪物占位
- 战斗嵌入主面板（不再全屏替换）
- 新战场：3×3 地形格子（取周围地形颜色）+ 绿/红三角标记 + 序号
- 左右状态栏：角色名 + HP 条
- 指令栏：逐角色操作流程（指令→目标→下一角色→结算）
- 右下角改为快捷行动栏
```

```bash
git add docs/devlog.md
git commit -m "docs: update devlog with panel layout refactoring"
```
