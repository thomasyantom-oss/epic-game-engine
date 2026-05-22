<template>
  <div class="map-info">
    <div class="info-section">
      <div class="map-name">{{ mapName }}</div>
      <div class="coordinates">坐标 ({{ playerX }}, {{ playerY }})</div>
    </div>

    <div class="info-section" v-if="poiAction">
      <div class="section-label">可交互</div>
      <div class="poi-btn" @click="$emit('poi-action', poiAction)">
        {{ poiAction.label }}
      </div>
    </div>

    <div class="info-section">
      <div class="section-label">地形图例</div>
      <div class="legend">
        <div v-for="t in terrains" :key="t.id" class="legend-item">
          <span class="legend-color" :style="{ backgroundColor: t.color }">{{ t.char }}</span>
          <span class="legend-name">{{ t.name }}</span>
        </div>
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

const terrains = computed(() => {
  if (!mapState.mapData) return []
  return mapState.mapData.terrains.map(t => ({
    ...t,
    name: TERRAIN_NAMES[t.id] || t.id
  }))
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

.map-name {
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
  text-transform: uppercase;
  letter-spacing: 0.05em;
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

.legend {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8em;
}

.legend-color {
  width: 1.4em;
  height: 1.4em;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 2px;
  font-size: 0.8em;
  border: 1px solid #333;
}

.legend-name {
  color: #aaa;
}
</style>
