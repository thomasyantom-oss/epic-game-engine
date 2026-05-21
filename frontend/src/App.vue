<template>
  <div class="app-container">
    <CombatView v-if="combat.active" />
    <div v-else class="game-grid">
      <div class="panel-main">
        <TabPanel :tabs="mainTabs" default-tab="map">
          <template #map>
            <MapGrid :map-size="settings.mapSize || 10" />
          </template>
        </TabPanel>
      </div>

      <div class="panel-func">
        <TabPanel :tabs="funcTabs" default-tab="status">
          <template #status>
            <StatusPanel :current-scene="state.currentScene?.id" />
          </template>
          <template #settings>
            <SettingsPanel :visible="true" />
          </template>
        </TabPanel>
      </div>

      <div class="panel-log">
        <TabPanel :tabs="logTabs" default-tab="all">
          <template #all>
            <div class="scene-log" ref="sceneLogEl">
              <div v-for="(entry, i) in sceneHistory" :key="i">
                <TextRenderer v-if="entry.type === 'scene'" :segments="entry.segments" />
                <div v-else-if="entry.type === 'action'" class="action-log">
                  <span style="color: #999">→ {{ entry.text }}</span>
                </div>
              </div>
            </div>
          </template>
        </TabPanel>
      </div>

      <div class="panel-nav">
        <TabPanel :tabs="navTabs" default-tab="actions">
          <template #actions>
            <NavigationPanel
              :actions="currentActions"
              @action="handleAction"
            />
          </template>
        </TabPanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import TabPanel from './components/TabPanel.vue'
import TextRenderer from './components/TextRenderer.vue'
import StatusPanel from './components/StatusPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import NavigationPanel from './components/NavigationPanel.vue'
import MapGrid from './components/MapGrid.vue'
import CombatView from './components/combat/CombatView.vue'
import { useGameState } from './composables/useGameState.js'
import { useSettings } from './composables/useSettings.js'
import { usePanelRefresh } from './composables/usePanelRefresh.js'
import { useCombat } from './composables/useCombat.js'
import { useMap } from './composables/useMap.js'

const { state, initialize, doAction } = useGameState()
const { handleRefresh } = usePanelRefresh()
const { combat, enterCombat } = useCombat()
const { settings } = useSettings()
const { mapState, loadMap, moveDirection, clearPoi } = useMap()

const sceneHistory = ref([])
const sceneLogEl = ref(null)

const mainTabs = [
  { id: 'map', label: '地图' }
]

const funcTabs = [
  { id: 'status', label: '人物' },
  { id: 'settings', label: '设置' }
]

const logTabs = [
  { id: 'all', label: '事件' }
]

const navTabs = [
  { id: 'actions', label: '操作' }
]

const currentActions = computed(() => {
    const actions = []
    // Direction buttons for map movement
    actions.push(
        { id: 'move-up', label: '↑ 北', type: 'mapMove', params: { direction: 'UP' } },
        { id: 'move-down', label: '↓ 南', type: 'mapMove', params: { direction: 'DOWN' } },
        { id: 'move-left', label: '← 西', type: 'mapMove', params: { direction: 'LEFT' } },
        { id: 'move-right', label: '→ 东', type: 'mapMove', params: { direction: 'RIGHT' } }
    )
    // POI action when standing on a point of interest
    if (mapState.poi) {
        actions.push({
            id: 'poi-' + mapState.poi.id,
            label: mapState.poi.label,
            type: 'move',
            params: { target: mapState.poi.target }
        })
    }
    return actions
})

watch(() => state.currentScene, (scene) => {
    if (scene && scene.description) {
        sceneHistory.value.push({ type: 'scene', segments: scene.description })
        nextTick(() => {
            if (sceneLogEl.value) sceneLogEl.value.scrollTop = sceneLogEl.value.scrollHeight
        })
    }
})

async function handleAction(action) {
    sceneHistory.value.push({ type: 'action', text: action.label })
    if (action.type === 'mapMove') {
        await moveDirection(action.params.direction)
        return
    }
    if (mapState.poi && action.type === 'move') {
        clearPoi()
    }
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}

onMounted(() => {
  initialize()
  loadMap()
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}

.game-grid {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}

.panel-main { grid-column: 1; grid-row: 1; min-height: 0; }
.panel-func { grid-column: 2; grid-row: 1; min-height: 0; }
.panel-log { grid-column: 1; grid-row: 2; min-height: 0; }
.panel-nav { grid-column: 2; grid-row: 2; min-height: 0; }

.placeholder-content {
  color: #666;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.scene-log {
  height: 100%;
  overflow-y: auto;
  line-height: 1.8;
}

.action-log {
  margin: 0.3rem 0;
}
</style>
