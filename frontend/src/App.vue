<template>
  <div class="app-container">
    <div class="game-grid">
      <div class="panel-main">
        <TabPanel :tabs="mainTabs" :default-tab="combat.active ? 'battle' : 'map'">
          <template #battle>
            <CombatView />
          </template>
          <template #map>
            <div class="map-split">
              <MapGrid :map-size="settings.mapSize || 10" :disabled="combat.active" />
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
        <TabPanel :tabs="logTabs" :default-tab="combat.active ? 'combat-log' : 'all'">
          <template #all>
            <div class="scene-log" ref="sceneLogEl">
              <div v-for="(entry, i) in sceneHistory" :key="i">
                <TextRenderer v-if="entry.type === 'scene'" :segments="entry.segments" />
                <div v-else-if="entry.type === 'action'" class="action-log">
                  <span>{{ entry.text }}</span>
                </div>
              </div>
            </div>
          </template>
          <template #combat-log>
            <div class="scene-log" ref="combatLogEl">
              <div
                v-for="(entry, i) in allCombatLog"
                :key="i"
                :ref="el => { if (i === currentRoundStart) currentRoundEl = el }"
              >
                <TextRenderer :segments="entry.segments" />
              </div>
              <div :style="{ height: padHeight + 'px' }"></div>
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
import { getCombatState } from './api/client.js'

const { state, initialize, doAction } = useGameState()
const { handleRefresh } = usePanelRefresh()
const { combat, enterCombat } = useCombat()
const { settings } = useSettings()
const { mapState, mapLog, loadMap, moveDirection, clearPoi } = useMap()

const sceneHistory = ref([])
const sceneLogEl = ref(null)
const allCombatLog = ref([])
const currentRoundStart = ref(0)
const combatLogEl = ref(null)
const padHeight = ref(0)
let currentRoundEl = null
let roundCounter = 0

const mainTabs = computed(() => {
  if (combat.active) {
    return [
      { id: 'battle', label: '战场' },
      { id: 'map', label: '地图' }
    ]
  }
  return [{ id: 'map', label: '地图' }]
})

const funcTabs = [
  { id: 'status', label: '人物' },
  { id: 'settings', label: '设置' }
]

const logTabs = computed(() => {
  if (combat.active || allCombatLog.value.length > 0) {
    return [
      { id: 'combat-log', label: '战斗' },
      { id: 'all', label: '事件' }
    ]
  }
  return [{ id: 'all', label: '事件' }]
})

const navTabs = [
  { id: 'actions', label: '快捷' }
]

const currentActions = computed(() => {
    return []
})

watch(() => combat.active, (active) => {
    if (active) {
        allCombatLog.value = []
        currentRoundStart.value = 0
        roundCounter = 0
    } else {
        sceneHistory.value = []
    }
})

let sceneWatchReady = false
watch(() => state.currentScene, (scene) => {
    if (!sceneWatchReady) {
        sceneWatchReady = true
        return
    }
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

watch(() => combat.state?.phase, (phase) => {
    if (phase === 'VICTORY') {
        allCombatLog.value.push({ segments: [{ text: '战斗胜利！', color: '#4ecdc4' }] })
    } else if (phase === 'DEFEAT') {
        allCombatLog.value.push({ segments: [{ text: '战斗失败...', color: '#e94560' }] })
    }
})

watch(() => combat.results, (results) => {
    if (results && results.length > 0) {
        roundCounter++
        currentRoundStart.value = allCombatLog.value.length
        allCombatLog.value.push({ segments: [{ text: `— 第 ${roundCounter} 回合 —`, color: '#666' }] })
        results.forEach(r => {
            if (r.action === 'defend') {
                allCombatLog.value.push({ segments: [
                    { text: r.actorName, color: r.actorId?.startsWith('player') || r.actorId === state.playerId ? '#4ecdc4' : '#e94560' },
                    { text: ' 进行防御。', color: '#ffffff' }
                ]})
                return
            }
            const actorColor = (r.actorId?.startsWith('player') || r.actorId === state.playerId) ? '#4ecdc4' : '#e94560'
            const targetColor = (r.targetId?.startsWith('player') || r.targetId === state.playerId) ? '#4ecdc4' : '#e94560'
            const segments = [
                { text: r.actorName, color: actorColor },
                { text: ' 对 ', color: '#ffffff' },
                { text: r.targetName, color: targetColor },
                { text: ' 造成 ', color: '#ffffff' },
                { text: `${r.damage}`, color: '#ffd93d' },
                { text: ' 点伤害', color: '#ffffff' }
            ]
            if (r.targetDefeated) segments.push({ text: '（击败）', color: '#e94560' })
            allCombatLog.value.push({ segments })
        })
        nextTick(() => {
            if (currentRoundEl && combatLogEl.value) {
                const containerH = combatLogEl.value.clientHeight
                const contentAfterMarker = combatLogEl.value.scrollHeight - currentRoundEl.offsetTop - containerH
                padHeight.value = contentAfterMarker < 0 ? Math.abs(contentAfterMarker) : 0
                nextTick(() => {
                    combatLogEl.value.scrollTop = currentRoundEl.offsetTop
                })
            }
        })
    }
}, { deep: true })

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

onMounted(async () => {
  initialize()
  await loadMap()
  const combatState = await getCombatState(state.playerId)
  if (combatState) {
    await enterCombat(state.playerId)
  }
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
  color: var(--text-color);
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
