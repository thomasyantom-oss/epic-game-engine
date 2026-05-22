<template>
  <div class="app-container">
    <SnapshotRenderer :snapshot="snapshot" @action="handleAction" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SnapshotRenderer from './components/SnapshotRenderer.vue'
import { performAction, getSnapshot } from './api/client.js'

const PLAYER_ID = 'player1'
const snapshot = ref(null)

async function handleAction(action) {
    const result = await performAction(PLAYER_ID, action.type, action.params || {})
    snapshot.value = result
}

onMounted(async () => {
    snapshot.value = await getSnapshot(PLAYER_ID)
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}
</style>
