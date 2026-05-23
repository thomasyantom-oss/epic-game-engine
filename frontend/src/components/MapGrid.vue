<template>
  <div class="map-container" ref="containerEl">
    <div class="map-grid" :style="gridStyle">
      <div v-for="(cell, idx) in visibleCells" :key="idx"
           class="map-cell" :style="cellStyle(cell)"
           @click="onCellClick(cell)">
        <template v-if="cell.x === playerX && cell.y === playerY">
          <span class="player-marker">★</span>
        </template>
        <template v-else>{{ cell.char }}</template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  map: Object,
  mapSize: { type: Number, default: 10 },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['move', 'moveTo', 'interrupt'])

const containerEl = ref(null)
const cellSize = ref(32)

const playerX = computed(() => props.map?.playerX ?? 0)
const playerY = computed(() => props.map?.playerY ?? 0)
const viewportSize = computed(() => props.mapSize)

function calcCellSize() {
  if (!containerEl.value) return
  const rect = containerEl.value.getBoundingClientRect()
  const size = viewportSize.value
  const byHeight = Math.floor((rect.height - 8) / size)
  const byWidth = Math.floor((rect.width - 8) / size)
  cellSize.value = Math.max(20, Math.min(byHeight, byWidth))
}

const visibleCells = computed(() => {
  if (!props.map || !props.map.terrain) return []
  const terrain = props.map.terrain
  const width = props.map.width
  const height = props.map.height
  const cells = []
  const halfView = Math.floor(viewportSize.value / 2)

  let offsetX = playerX.value - halfView
  let offsetY = playerY.value - halfView
  offsetX = Math.max(0, Math.min(offsetX, width - viewportSize.value))
  offsetY = Math.max(0, Math.min(offsetY, height - viewportSize.value))
  if (width <= viewportSize.value) offsetX = 0
  if (height <= viewportSize.value) offsetY = 0

  const maxX = Math.min(offsetX + viewportSize.value, width)
  const maxY = Math.min(offsetY + viewportSize.value, height)

  for (let y = offsetY; y < maxY; y++) {
    const row = terrain[y]
    if (!row) continue
    for (let x = offsetX; x < maxX; x++) {
      const ch = row.charAt(x)
      cells.push({ x, y, char: ch })
    }
  }
  return cells
})

const gridStyle = computed(() => {
  const cols = Math.min(viewportSize.value, props.map?.width || viewportSize.value)
  return {
    display: 'grid',
    gridTemplateColumns: `repeat(${cols}, ${cellSize.value}px)`,
    gap: '0px'
  }
})

function cellStyle(cell) {
  const terrains = props.map?.terrains || {}
  const info = terrains[cell.char]
  return {
    backgroundColor: info?.color || '#333',
    color: info?.textColor || '#fff',
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
  emit('moveTo', cell.x, cell.y)
}

function onKeyDown(e) {
  if (props.disabled) return
  if (e.target.closest('input, textarea, select')) return
  const dirMap = {
    ArrowUp: 'NORTH', ArrowDown: 'SOUTH', ArrowLeft: 'WEST', ArrowRight: 'EAST',
    w: 'NORTH', W: 'NORTH', s: 'SOUTH', S: 'SOUTH', a: 'WEST', A: 'WEST', d: 'EAST', D: 'EAST'
  }
  const dir = dirMap[e.key]
  if (dir) {
    e.preventDefault()
    emit('interrupt')
    emit('move', dir)
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
.map-container { width: 100%; height: 100%; overflow: hidden; padding: 4px; box-sizing: border-box; }
.map-grid { width: fit-content; }
.map-cell { user-select: none; transition: transform 0.1s; }
.map-cell:hover { transform: scale(1.05); z-index: 1; }
.player-marker { color: #ffd700; font-weight: bold; text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 8px #ffd700; font-size: 1.2em; }
</style>
