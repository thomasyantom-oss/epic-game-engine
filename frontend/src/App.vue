<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Epic</h1>
      <button class="settings-toggle" @click="showSettings = !showSettings">
        ⚙ 设置
      </button>
    </header>
    <main class="app-main">
      <SettingsPanel :visible="showSettings" />
      <ScenePanel />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ScenePanel from './components/ScenePanel.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import { useGameState } from './composables/useGameState.js'
import { useSettings } from './composables/useSettings.js'

const { initialize } = useGameState()
useSettings()

const showSettings = ref(false)

onMounted(() => {
    initialize()
})
</script>

<style scoped>
.app-container {
    padding: 2rem;
    max-width: 1000px;
    margin: 0 auto;
}

.app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
}

.app-header h1 {
    color: var(--link-color);
    font-size: 1.5rem;
}

.settings-toggle {
    background: none;
    border: 1px solid var(--panel-border-color);
    color: var(--text-color);
    padding: 0.4rem 0.8rem;
    border-radius: 4px;
    cursor: pointer;
}

.settings-toggle:hover {
    border-color: var(--link-color);
}
</style>
