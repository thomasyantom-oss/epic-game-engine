<template>
  <div class="battle-grid-container">
    <div class="status-col player-col">
      <div v-for="(unit, idx) in playerUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
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
      <div class="player-grid">
        <div
          v-for="(cell, idx) in playerCells"
          :key="'p'+idx"
          class="terrain-cell"
          :style="{ backgroundColor: terrainColor }"
        >
          <span v-if="cell.marker && cell.marker.alive" class="marker player">
            ▶<span class="marker-idx">{{ cell.marker.index }}</span>
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
        >
          <span v-if="cell.marker && cell.marker.alive" class="marker enemy">
            ◀<span class="marker-idx">{{ cell.marker.index }}</span>
          </span>
        </div>
      </div>
    </div>

    <div class="status-col enemy-col">
      <div v-for="(unit, idx) in enemyUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
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
  props.combatants.filter(c => c.side === 'PLAYER')
)

const enemyUnits = computed(() =>
  props.combatants.filter(c => c.side === 'ENEMY')
)

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
  // Player: col 0=back, 1=mid, 2=front (front nearest enemy)
  // Enemy: col 0=front (nearest player), 1=mid, 2=back
  const playerColMap = { BACK: 0, MID: 1, FRONT: 2 }
  const enemyColMap = { FRONT: 0, MID: 1, BACK: 2 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (playerColMap[unitRow] ?? 2) : (enemyColMap[unitRow] ?? 0)
    const row = unit.slot ?? idx % 3
    markers.push({ col, row, side, index: idx + 1, alive: unit.alive })
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
  gap: 0;
}

.player-grid, .enemy-grid {
  display: grid;
  grid-template-columns: repeat(3, 2.5rem);
  grid-template-rows: repeat(3, 2.5rem);
  gap: 2px;
}

.field-gap {
  width: 1.5rem;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #222;
  border-radius: 2px;
}

.marker {
  font-size: 1.1rem;
  font-weight: bold;
}

.marker.player { color: #4ecdc4; }
.marker.enemy { color: #e94560; }

.marker-idx {
  font-size: 0.5em;
  vertical-align: super;
  color: #fff;
}
</style>
