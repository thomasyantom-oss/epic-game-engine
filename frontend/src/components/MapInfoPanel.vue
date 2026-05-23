<template>
  <div class="map-info-panel">
    <div class="info-row">
      <span class="info-label">地图:</span>
      <span class="info-value">{{ map.mapName || map.mapId }}</span>
    </div>
    <div class="info-row">
      <span class="info-label">坐标:</span>
      <span class="info-value">({{ map.playerX }}, {{ map.playerY }})</span>
    </div>
    <div class="info-row" v-if="map.currentTerrain">
      <span class="info-label">地形:</span>
      <span class="info-value terrain-badge" :style="terrainStyle">
        {{ map.currentTerrain }}
        <span v-if="map.currentTerrainName" class="terrain-name">{{ terrainLabel }}</span>
      </span>
    </div>
    <div class="poi-section" v-if="currentPois.length > 0">
      <div v-for="poi in currentPois" :key="poi.id" class="poi-button"
           @click="$emit('poiAction', poi)">
        {{ poi.label }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ map: Object })
defineEmits(['poiAction'])

const terrainNames = {
  forest: '森林', grass: '草地', road: '道路', sand: '沙地',
  water: '水域', town: '城镇', mountain: '山脉'
}

const terrainLabel = computed(() => {
  const name = props.map?.currentTerrainName
  return terrainNames[name] || name || ''
})

const terrainStyle = computed(() => {
  const ch = props.map?.currentTerrain
  const info = props.map?.terrains?.[ch]
  if (!info) return {}
  return { backgroundColor: info.color, color: info.textColor, padding: '0.1rem 0.4rem', borderRadius: '3px' }
})

const currentPois = computed(() => {
  if (!props.map?.pois) return []
  return props.map.pois.filter(p => p.x === props.map.playerX && p.y === props.map.playerY)
})
</script>

<style scoped>
.map-info-panel { padding: 0.5rem; line-height: 1.8; }
.info-row { margin-bottom: 0.2rem; }
.info-label { color: var(--text-color); opacity: 0.7; margin-right: 0.4rem; }
.info-value { color: var(--text-color); }
.terrain-badge { font-size: 0.9em; }
.terrain-name { margin-left: 0.3rem; font-size: 0.85em; opacity: 0.8; }
.poi-section { margin-top: 0.5rem; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 0.5rem; }
.poi-button {
  display: inline-block;
  padding: 0.3rem 0.8rem;
  margin: 0.2rem 0.3rem 0.2rem 0;
  background: color-mix(in srgb, var(--color-highlight) 15%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-highlight) 40%, transparent);
  border-radius: 4px;
  color: var(--color-highlight);
  cursor: pointer;
  font-size: 0.9em;
  transition: background 0.2s;
}
.poi-button:hover { background: color-mix(in srgb, var(--color-highlight) 30%, transparent); }
</style>
