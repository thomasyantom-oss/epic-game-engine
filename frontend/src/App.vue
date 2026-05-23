<template>
  <div class="app-container">
    <CharacterSelect v-if="phase === 'character_select'"
      :characters="snapshot.characters"
      :max-slots="snapshot.maxSlots"
      @select="selectCharacter"
      @create="createCharacter" />
    <CharacterCreate v-else-if="phase === 'character_create'"
      :form="snapshot.form"
      @confirm="confirmCharacter"
      @cancel="cancelCreate" />
    <SnapshotRenderer v-else-if="phase === 'in_game'"
      :snapshot="snapshot"
      @action="handleAction" />
    <div v-else class="loading">加载中...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import CharacterSelect from './components/CharacterSelect.vue'
import CharacterCreate from './components/CharacterCreate.vue'
import SnapshotRenderer from './components/SnapshotRenderer.vue'
import { performAction, getSnapshot } from './api/client.js'

const snapshot = ref(null)
const phase = computed(() => snapshot.value?.phase || 'loading')

async function selectCharacter(characterId) {
    snapshot.value = await performAction('select_character', { characterId })
}

async function createCharacter() {
    snapshot.value = await performAction('create_character')
}

async function confirmCharacter(values) {
    snapshot.value = await performAction('confirm_character', values)
}

async function cancelCreate() {
    snapshot.value = await getSnapshot()
}

let pathfindingActive = false

async function handleAction(action) {
    if (action.type === '_interrupt') {
        pathfindingActive = false
        return
    }
    if (action.type === 'map_moveto') {
        await handlePathfind(action.params)
    } else {
        pathfindingActive = false
        snapshot.value = await performAction(action.type, action.params || {})
    }
}

async function handlePathfind(params) {
    pathfindingActive = true
    const targetX = params.targetX
    const targetY = params.targetY
    const entityId = params.entityId
    let lastX = null, lastY = null

    while (pathfindingActive) {
        const result = await performAction('map_moveto', { targetX, targetY, entityId })
        snapshot.value = result

        if (result.combat || result.phase !== 'in_game') break
        const map = result.map
        if (!map) break
        if (map.playerX === targetX && map.playerY === targetY) break
        // If player didn't move, path is blocked — stop
        if (map.playerX === lastX && map.playerY === lastY) break
        lastX = map.playerX
        lastY = map.playerY

        await new Promise(r => setTimeout(r, 200))
    }
    pathfindingActive = false
}

onMounted(async () => {
    snapshot.value = await getSnapshot()
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  padding: 0.5rem;
  box-sizing: border-box;
}
.loading { display: flex; align-items: center; justify-content: center; height: 100%; color: var(--text-color); }
</style>
