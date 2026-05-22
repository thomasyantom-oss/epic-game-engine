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

const terrainCells = computed(() => {
  const cells = []
  const map = mapState.mapData
  const px = mapState.playerX
  const py = mapState.playerY

  // 3x6 grid: left 3 cols = player side, right 3 cols = enemy side
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 6; col++) {
      // Map terrain from surrounding area
      const dx = col - 3
      const dy = row - 1
      const x = px + dx
      const y = py + dy
      let color = '#333'
      if (map && y >= 0 && y < map.height && x >= 0 && x < map.width) {
        const ch = map.terrain[y].charAt(x)
        const terrain = map.terrains.find(t => t.char === ch)
        if (terrain) color = terrain.color
      }

      const marker = getMarkerAt(col, row)
      cells.push({ color, marker })
    }
  }
  return cells
})

function getMarkerAt(col, row) {
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
  // Player: cols 0-2 (back=0, mid=1, front=2), front is rightmost (near enemy)
  // Enemy: cols 3-5 (front=3, mid=4, back=5), front is leftmost (near player)
  const playerColMap = { BACK: 0, MID: 1, FRONT: 2 }
  const enemyColMap = { FRONT: 3, MID: 4, BACK: 5 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (playerColMap[unitRow] ?? 2) : (enemyColMap[unitRow] ?? 3)
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
  grid-template-columns: repeat(6, 1fr);
  gap: 2px;
  width: 18rem;
  height: 9rem;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #222;
  border-radius: 2px;
}

.marker {
  font-size: 1.2rem;
  font-weight: bold;
}

.unit-status.dead {
  opacity: 0.4;
}

.marker.player { color: #4ecdc4; }
.marker.enemy { color: #e94560; }

.marker-idx {
  font-size: 0.5em;
  vertical-align: super;
  color: #fff;
}
</style>
