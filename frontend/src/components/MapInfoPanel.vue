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
    type: mapState.poi.type,
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
  cursor: pointer;
  font-size: 0.85em;
  color: #ccc;
  transition: color 0.2s;
}

.poi-btn:hover {
  color: var(--link-color);
}

.monster-list {
  font-size: 0.85em;
}

.no-data {
  color: #555;
  font-style: italic;
}
</style>
