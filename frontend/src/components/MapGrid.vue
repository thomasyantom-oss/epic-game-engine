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
import { computed, ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useMap } from '../composables/useMap.js'

const { mapState, moveDirection, moveToTarget } = useMap()
const containerEl = ref(null)
const cellSize = ref(32)

const props = defineProps({
  mapSize: { type: Number, default: 10 },
  disabled: { type: Boolean, default: false }
})

const playerX = computed(() => mapState.playerX)
const playerY = computed(() => mapState.playerY)

const viewportSize = computed(() => props.mapSize)

function calcCellSize() {
  if (!containerEl.value) return
  const rect = containerEl.value.getBoundingClientRect()
  const rows = viewportSize.value
  const cols = viewportSize.value
  const byHeight = Math.floor((rect.height - 8) / rows)
  const byWidth = Math.floor((rect.width - 8) / cols)
  cellSize.value = Math.max(20, Math.min(byHeight, byWidth))
}

const visibleCells = computed(() => {
  if (!mapState.mapData) return []
  const map = mapState.mapData
  const cells = []
  const halfView = Math.floor(viewportSize.value / 2)

  let offsetX = playerX.value - halfView
  let offsetY = playerY.value - halfView

  offsetX = Math.max(0, Math.min(offsetX, map.width - viewportSize.value))
  offsetY = Math.max(0, Math.min(offsetY, map.height - viewportSize.value))

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
    gridTemplateColumns: `repeat(${cols}, ${cellSize.value}px)`,
    gap: '0px'
  }
})

function cellStyle(cell) {
  const bg = cell.terrain?.color || '#333'
  const color = cell.terrain?.textColor || '#fff'
  return {
    backgroundColor: bg,
    color: color,
    border: '1px solid #333',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    width: cellSize.value + 'px',
    height: cellSize.value + 'px',
    fontSize: Math.max(10, cellSize.value * 0.45) + 'px'
  }
}

function onCellClick(cell) {
  if (props.disabled) return
  if (cell.x === playerX.value && cell.y === playerY.value) return
  moveToTarget(cell.x, cell.y)
}

function onKeyDown(e) {
  const dirMap = {
    ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
    w: 'UP', W: 'UP', s: 'DOWN', S: 'DOWN', a: 'LEFT', A: 'LEFT', d: 'RIGHT', D: 'RIGHT'
  }
  const dir = dirMap[e.key]
  if (dir && !props.disabled && !e.target.closest('input, textarea, select')) {
    e.preventDefault()
    moveDirection(dir)
  }
}

let resizeObserver = null

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  nextTick(() => calcCellSize())
  resizeObserver = new ResizeObserver(() => calcCellSize())
  if (containerEl.value) resizeObserver.observe(containerEl.value)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  overflow: hidden;
  padding: 4px;
  box-sizing: border-box;
}

.map-grid {
  width: fit-content;
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
  text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 8px #ffd700;
  font-size: 1.2em;
}
</style>
