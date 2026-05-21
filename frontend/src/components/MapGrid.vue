<template>
  <div class="map-container" ref="containerEl">
    <div
      class="map-grid"
      :style="gridStyle"
    >
      <div
        v-for="(cell, idx) in visibleCells"
        :key="idx"
        class="map-cell"
        :style="cellStyle(cell)"
        @click="onCellClick(cell)"
      >
        <template v-if="cell.x === playerX && cell.y === playerY">
          <span class="player-marker">★</span>
        </template>
        <template v-else>
          {{ cell.char }}
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useMap } from '../composables/useMap.js'
import { useSettings } from '../composables/useSettings.js'

const { mapState, moveDirection, moveToTarget } = useMap()
const { settings } = useSettings()
const containerEl = ref(null)

const props = defineProps({
  mapSize: { type: Number, default: 10 }
})

const playerX = computed(() => mapState.playerX)
const playerY = computed(() => mapState.playerY)

const viewportSize = computed(() => props.mapSize)

const visibleCells = computed(() => {
  if (!mapState.mapData) return []
  const map = mapState.mapData
  const cells = []
  const halfView = Math.floor(viewportSize.value / 2)

  let offsetX = playerX.value - halfView
  let offsetY = playerY.value - halfView

  // Clamp to map edges
  offsetX = Math.max(0, Math.min(offsetX, map.width - viewportSize.value))
  offsetY = Math.max(0, Math.min(offsetY, map.height - viewportSize.value))

  // If map smaller than viewport, start at 0
  if (map.width <= viewportSize.value) offsetX = 0
  if (map.height <= viewportSize.value) offsetY = 0

  const maxX = Math.min(offsetX + viewportSize.value, map.width)
  const maxY = Math.min(offsetY + viewportSize.value, map.height)

  for (let y = offsetY; y < maxY; y++) {
    const row = map.terrain[y]
    for (let x = offsetX; x < maxX; x++) {
      const ch = row.charAt(x)
      const terrain = map.terrains.find(t => t.char === ch)
      const poi = map.pois.find(p => p.x === x && p.y === y)
      cells.push({ x, y, char: ch, terrain, poi })
    }
  }
  return cells
})

const gridStyle = computed(() => {
  const cols = Math.min(viewportSize.value, mapState.mapData?.width || viewportSize.value)
  return {
    display: 'grid',
    gridTemplateColumns: `repeat(${cols}, 1fr)`,
    gap: '0px',
    width: '100%',
    height: '100%',
    aspectRatio: '1'
  }
})

function cellStyle(cell) {
  const bg = cell.terrain?.color || '#333'
  const color = cell.terrain?.textColor || '#ccc'
  return {
    backgroundColor: bg,
    color: color,
    border: '1px solid #333',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 'clamp(10px, 2vw, 16px)',
    cursor: 'pointer',
    aspectRatio: '1'
  }
}

function onCellClick(cell) {
  if (cell.x === playerX.value && cell.y === playerY.value) return
  moveToTarget(cell.x, cell.y)
}

function onKeyDown(e) {
  const dirMap = {
    ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
    w: 'UP', W: 'UP', s: 'DOWN', S: 'DOWN', a: 'LEFT', A: 'LEFT', d: 'RIGHT', D: 'RIGHT'
  }
  const dir = dirMap[e.key]
  if (dir && !e.target.closest('input, textarea, select')) {
    e.preventDefault()
    moveDirection(dir)
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.map-grid {
  max-width: 100%;
  max-height: 100%;
}

.map-cell {
  user-select: none;
  transition: transform 0.1s;
}

.map-cell:hover {
  transform: scale(1.05);
  z-index: 1;
}

.player-marker {
  color: #ffd700;
  font-weight: bold;
  text-shadow: 0 0 6px #ffd700;
  font-size: 1.2em;
}
</style>
