<template>
  <div class="game-grid">
    <div class="panel-main">
      <TabPanel :tabs="mainTabs" :default-tab="snapshot.combat ? 'battle' : 'map'">
        <template #map>
          <MapGrid :map="snapshot.map" :map-size="settings.mapSize || 10"
                   :disabled="!!snapshot.combat"
                   @move="onMove" @moveTo="onMoveTo" />
        </template>
        <template #battle v-if="snapshot.combat">
          <BattleGrid :combat="snapshot.combat" :player-id="snapshot.playerId"
                      @command="$emit('action', $event)" />
        </template>
      </TabPanel>
    </div>

    <div class="panel-func">
      <TabPanel :tabs="funcTabs" default-tab="status">
        <template #status>
          <StatusBars :bars="snapshot.statusBars" />
          <div class="buffs" v-if="snapshot.buffs && snapshot.buffs.length">
            <div v-for="buff in snapshot.buffs" :key="buff.id" class="buff-item">
              {{ buff.name }} ({{ buff.remaining }})
            </div>
          </div>
        </template>
        <template #mapinfo v-if="snapshot.map">
          <MapInfoPanel :map="snapshot.map" @poi-action="onPoiAction" />
        </template>
        <template #settings>
          <SettingsPanel />
        </template>
      </TabPanel>
    </div>

    <div class="panel-log">
      <TabPanel :tabs="logTabs" default-tab="events">
        <template #events>
          <div class="log-scroll">
            <div v-for="(entry, i) in snapshot.log" :key="i">
              <TextRenderer :segments="entry.segments" />
            </div>
            <div v-if="!snapshot.log || snapshot.log.length === 0" class="empty-log">暂无事件</div>
          </div>
        </template>
      </TabPanel>
    </div>

    <div class="panel-nav">
      <TabPanel :tabs="navTabs" default-tab="actions">
        <template #actions>
          <ActionPanel :actions="filteredActions" @action="$emit('action', $event)" />
        </template>
      </TabPanel>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TabPanel from './TabPanel.vue'
import TextRenderer from './TextRenderer.vue'
import StatusBars from './StatusBars.vue'
import ActionPanel from './ActionPanel.vue'
import SettingsPanel from './SettingsPanel.vue'
import MapGrid from './MapGrid.vue'
import MapInfoPanel from './MapInfoPanel.vue'
import BattleGrid from './combat/BattleGrid.vue'
import { useSettings } from '../composables/useSettings.js'

const props = defineProps({ snapshot: Object })
const emit = defineEmits(['action'])
const { settings } = useSettings()

const mainTabs = computed(() => {
  if (props.snapshot?.combat) {
    return [{ id: 'battle', label: '战场' }, { id: 'map', label: '地图' }]
  }
  return [{ id: 'map', label: '地图' }]
})

const funcTabs = computed(() => {
  const tabs = [{ id: 'status', label: '人物' }]
  if (props.snapshot?.map) {
    tabs.push({ id: 'mapinfo', label: '地图' })
  }
  tabs.push({ id: 'settings', label: '设置' })
  return tabs
})

const logTabs = [{ id: 'events', label: '事件' }]
const navTabs = [{ id: 'actions', label: '快捷' }]

// Filter out map_move actions (keyboard handles movement)
const filteredActions = computed(() => {
  return (props.snapshot?.actions || []).filter(a => a.type !== 'map_move')
})

function onMove(direction) {
  emit('action', { type: 'map_move', params: { direction, entityId: props.snapshot?.playerId } })
}

function onMoveTo(x, y) {
  emit('action', { type: 'map_moveto', params: { targetX: x, targetY: y, entityId: props.snapshot?.playerId } })
}

function onPoiAction(poi) {
  emit('action', { type: 'poi_interact', params: { poiId: poi.id, poiType: poi.type, target: poi.target } })
}
</script>

<style scoped>
.game-grid {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}
.panel-main { grid-column: 1; grid-row: 1; min-height: 0; overflow: hidden; }
.panel-func { grid-column: 2; grid-row: 1; min-height: 0; }
.panel-log { grid-column: 1; grid-row: 2; min-height: 0; }
.panel-nav { grid-column: 2; grid-row: 2; min-height: 0; }
.combat-info { line-height: 1.8; padding: 0.5rem; }
.combatant-row { margin-left: 1rem; }
.log-scroll { height: 100%; overflow-y: auto; line-height: 1.8; }
.empty-log { color: var(--text-color); opacity: 0.5; }
.buffs { margin-top: 0.5rem; }
.buff-item { font-size: 0.9em; margin: 0.2rem 0; color: var(--text-color); }
</style>
