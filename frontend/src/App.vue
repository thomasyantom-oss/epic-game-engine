<template>
  <div class="app-container">
    <div class="game-grid">
      <!-- 主窗口 (3×2) -->
      <div class="panel-main">
        <TabPanel :tabs="mainTabs" default-tab="scene">
          <template #scene>
            <ScenePanel />
          </template>
          <template #map>
            <div class="placeholder-content">大地图（待实现）</div>
          </template>
        </TabPanel>
      </div>

      <!-- 功能页面 (1×2) -->
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

      <!-- 日志 (3×1) -->
      <div class="panel-log">
        <TabPanel :tabs="logTabs" default-tab="all">
          <template #all>
            <GameLog :entries="logEntries" />
          </template>
        </TabPanel>
      </div>

      <!-- 额外区域 (1×1) -->
      <div class="panel-extra">
        <TabPanel :tabs="extraTabs" default-tab="placeholder">
          <template #placeholder>
            <div class="placeholder-content">待规划</div>
          </template>
        </TabPanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import TabPanel from './components/TabPanel.vue'
import ScenePanel from './components/ScenePanel.vue'
import StatusPanel from './components/StatusPanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import GameLog from './components/GameLog.vue'
import { useGameState } from './composables/useGameState.js'
import { useSettings } from './composables/useSettings.js'

const { state, initialize } = useGameState()
useSettings()

const logEntries = reactive([])

const mainTabs = [
  { id: 'scene', label: '场景' },
  { id: 'map', label: '地图' }
]

const funcTabs = [
  { id: 'status', label: '人物' },
  { id: 'settings', label: '设置' }
]

const logTabs = [
  { id: 'all', label: '全部' }
]

const extraTabs = [
  { id: 'placeholder', label: '...' }
]

onMounted(() => {
  initialize()
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

.panel-main {
  grid-column: 1;
  grid-row: 1;
}

.panel-func {
  grid-column: 2;
  grid-row: 1;
}

.panel-log {
  grid-column: 1;
  grid-row: 2;
}

.panel-extra {
  grid-column: 2;
  grid-row: 2;
}

.placeholder-content {
  color: #666;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
