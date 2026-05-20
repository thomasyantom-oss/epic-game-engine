<template>
  <div class="app-container">
    <header class="app-header">
      <h1>Epic</h1>
    </header>
    <main class="app-main">
      <ScenePanel />
    </main>
  </div>
</template>

<script setup>
import ScenePanel from './components/ScenePanel.vue'
import { useGameState } from './composables/useGameState.js'
import { onMounted } from 'vue'

const { initialize } = useGameState()

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

.app-header h1 {
    color: var(--link-color);
    margin-bottom: 2rem;
    font-size: 1.5rem;
}
</style>
