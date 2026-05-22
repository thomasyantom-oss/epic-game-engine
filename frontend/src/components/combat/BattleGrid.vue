<template>
  <div class="battle-layout">
    <div class="status-col player-col">
      <div v-for="(unit, idx) in playerUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
        <div class="unit-header" :class="{ active: unit.id === currentActorId }">
          <span class="unit-name player-name">{{ unit.name }}</span>
        </div>
        <div class="hp-row">
          <div class="hp-bar"><div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div></div>
          <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
        </div>
      </div>
    </div>

    <div class="center-col">
      <div class="battle-field">
        <div class="player-grid">
          <div
            v-for="(cell, idx) in playerCells"
            :key="'p'+idx"
            class="terrain-cell"
            :style="{ backgroundColor: terrainColor }"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker player">
              <span class="marker-num">{{ cell.marker.index }}</span>▶
            </span>
          </div>
        </div>
        <div class="field-gap"></div>
        <div class="enemy-grid">
          <div
            v-for="(cell, idx) in enemyCells"
            :key="'e'+idx"
            class="terrain-cell"
            :style="{ backgroundColor: terrainColor }"
            :class="{ 'target-cell': selectingTarget && cell.marker && cell.marker.alive }"
            @click="onCellClick(cell)"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker enemy">
              ◀<span class="marker-num">{{ cell.marker.index }}</span>
            </span>
          </div>
        </div>
      </div>

      <div class="command-area">
        <div class="actor-box">
          <div class="actor-avatar"></div>
        </div>
        <div class="cmd-section">
          <div class="cmd-line">
            <span class="actor-label">{{ currentActor?.name || '' }}</span>
          </div>
          <div class="cmd-line" v-if="step === 'command' && currentActor">
            <span class="cmd-item" @click="selectCommand('ATTACK')">攻击</span>
            <span class="cmd-item" @click="selectCommand('DEFEND')">防御</span>
            <span class="cmd-item" @click="selectCommand('SKILL')">技能</span>
            <span class="cmd-item" @click="selectCommand('ITEM')">道具</span>
            <span class="cmd-item" @click="selectCommand('FLEE')">逃跑</span>
          </div>
          <div class="cmd-line" v-else-if="step === 'target'">
            <span class="target-hint">选择目标</span>
            <span class="cmd-item cancel-item" @click="cancelSelect">取消</span>
          </div>
          <div class="cmd-line" v-else></div>
        </div>
      </div>
    </div>

    <div class="status-col enemy-col">
      <div v-for="(unit, idx) in enemyUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
        <div class="unit-header">
          <span class="unit-name enemy-name">{{ unit.name }}</span>
        </div>
        <div class="hp-row">
          <div class="hp-bar enemy-bar"><div class="hp-fill enemy-fill" :style="{ width: hpPercent(unit) + '%' }"></div></div>
          <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useMap } from '../../composables/useMap.js'

const props = defineProps({
  combatants: { type: Array, required: true },
  currentActorId: { type: String, default: '' },
  currentActor: { type: Object, default: null },
  targets: { type: Array, default: () => [] }
})

const emit = defineEmits(['command'])

const { mapState } = useMap()
const step = ref('command')
const selectedCommand = ref(null)

watch(() => props.currentActor, () => {
  step.value = 'command'
  selectedCommand.value = null
})

const selectingTarget = computed(() => step.value === 'target')

const playerUnits = computed(() =>
  props.combatants.filter(c => c.side === 'PLAYER')
)

const enemyUnits = computed(() =>
  props.combatants.filter(c => c.side === 'ENEMY')
)

function hpPercent(unit) {
  return Math.max(0, unit.hp / unit.maxHp * 100)
}

const terrainColor = computed(() => {
  const map = mapState.mapData
  if (!map) return '#333'
  const row = map.terrain[mapState.playerY]
  if (!row) return '#333'
  const ch = row.charAt(mapState.playerX)
  const terrain = map.terrains.find(t => t.char === ch)
  return terrain ? terrain.color : '#333'
})

const playerCells = computed(() => {
  const cells = []
  const positions = mapUnitsToGrid(playerUnits.value, 'player')
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) {
      const marker = positions.find(p => p.col === col && p.row === row)
      cells.push({ marker })
    }
  }
  return cells
})

const enemyCells = computed(() => {
  const cells = []
  const positions = mapUnitsToGrid(enemyUnits.value, 'enemy')
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) {
      const marker = positions.find(p => p.col === col && p.row === row)
      cells.push({ marker })
    }
  }
  return cells
})

function mapUnitsToGrid(units, side) {
  const markers = []
  const playerColMap = { BACK: 0, MID: 1, FRONT: 2 }
  const enemyColMap = { FRONT: 0, MID: 1, BACK: 2 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (playerColMap[unitRow] ?? 2) : (enemyColMap[unitRow] ?? 0)
    const row = unit.slot ?? idx % 3
    markers.push({ col, row, side, index: idx + 1, alive: unit.alive, id: unit.id })
  })
  return markers
}

function selectCommand(cmd) {
  if (!props.currentActor) return
  if (cmd === 'DEFEND' || cmd === 'FLEE') {
    emit('command', { actorId: props.currentActor.id, type: cmd, targetId: null })
    return
  }
  if (cmd === 'SKILL' || cmd === 'ITEM') {
    selectedCommand.value = 'ATTACK'
    step.value = 'target'
    return
  }
  selectedCommand.value = cmd
  step.value = 'target'
}

function onCellClick(cell) {
  if (step.value !== 'target') return
  if (!cell.marker || !cell.marker.alive) return
  emit('command', { actorId: props.currentActor.id, type: selectedCommand.value, targetId: cell.marker.id })
  step.value = 'command'
  selectedCommand.value = null
}

function cancelSelect() {
  step.value = 'command'
  selectedCommand.value = null
}
</script>

<style scoped>
.battle-layout {
  display: flex;
  height: 100%;
  padding: 0.3rem;
  box-sizing: border-box;
  gap: 0.4rem;
}

.status-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.5rem;
  min-width: 5.5rem;
}

.unit-status {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.unit-status.dead {
  opacity: 0.4;
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

.player-name { color: #4ecdc4; }
.enemy-name { color: #e94560; }

.hp-row {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.hp-bar {
  flex: 1;
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

.hp-text {
  font-size: 0.65em;
  color: var(--text-color);
  white-space: nowrap;
}

.center-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.battle-field {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
}

.player-grid, .enemy-grid {
  display: grid;
  grid-template-columns: repeat(3, 2.5rem);
  grid-template-rows: repeat(3, 2.5rem);
  gap: 2px;
}

.field-gap {
  width: 1.2rem;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #222;
  border-radius: 2px;
}

.terrain-cell.target-cell {
  cursor: pointer;
  border-color: #e94560;
}

.terrain-cell.target-cell:hover {
  box-shadow: 0 0 4px #e94560;
}

.marker {
  font-size: 1.1rem;
  font-weight: bold;
  display: flex;
  align-items: center;
}

.marker.player { color: #4ecdc4; }
.marker.enemy { color: #e94560; }

.marker-num {
  font-size: 0.6em;
  color: #fff;
}

.command-area {
  border-top: 1px solid var(--panel-border-color);
  height: 3.6rem;
  display: flex;
  align-items: stretch;
  gap: 0.5rem;
  padding: 0.2rem 0.5rem;
  box-sizing: border-box;
}

.actor-box {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.actor-avatar {
  width: 3rem;
  height: 3rem;
  border: 1px solid #4ecdc4;
  border-radius: 4px;
  background: #1a2a3a;
}

.cmd-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
}

.cmd-line {
  height: 1.2rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.actor-label {
  font-size: 0.8em;
  color: #4ecdc4;
}

.cmd-item {
  font-size: 0.75em;
  cursor: pointer;
  padding: 0 0.3rem;
  border-radius: 3px;
  transition: color 0.2s;
  color: var(--text-color);
}

.cmd-item:hover {
  color: var(--link-color);
}

.cancel-item {
  color: var(--text-color);
}

.target-hint {
  font-size: 0.75em;
  color: #e94560;
}
</style>
