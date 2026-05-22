<template>
  <div class="app-container">
    <div class="game-grid">
      <div class="panel-main">
        <TabPanel :tabs="mainTabs" default-tab="map">
          <template #map>
            <CombatView v-if="combat.active" />
            <div v-else class="map-split">
              <MapGrid :map-size="settings.mapSize || 10" />
              <MapInfoPanel @poi-action="handleAction" />
            </div>
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
import MapInfoPanel from './components/MapInfoPanel.vue'
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
const { mapState, mapLog, loadMap, moveDirection, clearPoi } = useMap()

const sceneHistory = ref([])
const sceneLogEl = ref(null)

const mainTabs = computed(() => [
  { id: 'map', label: combat.active ? '战场' : '地图' }
])

const funcTabs = [
  { id: 'status', label: '人物' },
  { id: 'settings', label: '设置' }
]

const logTabs = [
  { id: 'all', label: '事件' }
]

const navTabs = [
  { id: 'actions', label: '快捷' }
]

const currentActions = computed(() => {
    if (combat.active) {
        return [{ id: 'cancel', label: '取消', type: 'combatCancel', params: {} }]
    }
    return []
})

watch(() => state.currentScene, (scene) => {
    if (scene && scene.description) {
        sceneHistory.value = [{ type: 'scene', segments: scene.description }]
    }
})

watch(() => mapLog.value.length, () => {
    const latest = mapLog.value[mapLog.value.length - 1]
    if (latest) {
        sceneHistory.value = [latest]
    }
})

async function handleAction(action) {
    if (action.type === 'combatCancel') {
        return
    }
    sceneHistory.value = [{ type: 'action', text: action.label }]
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

.panel-main { grid-column: 1; grid-row: 1; min-height: 0; overflow: hidden; }

.map-split {
  display: flex;
  height: 100%;
  gap: 0.5rem;
}

.map-split > :first-child {
  flex: 1;
  min-width: 0;
}

.map-split > :last-child {
  width: 8rem;
  flex-shrink: 0;
  border-left: 1px solid var(--panel-border-color);
}
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
