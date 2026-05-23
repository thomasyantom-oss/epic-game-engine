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

async function handleAction(action) {
    snapshot.value = await performAction(action.type, action.params || {})
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
