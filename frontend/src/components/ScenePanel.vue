<template>
  <div class="scene-panel" v-if="scene">
    <div class="scene-description">
      <TextRenderer :segments="scene.description" />
    </div>
    <div class="scene-actions">
      <ActionLink
        v-for="action in scene.actions"
        :key="action.id"
        :action="action"
        @execute="handleAction(action)"
      />
    </div>
  </div>
  <div class="scene-panel loading" v-else>
    <span style="color: #666">加载中...</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TextRenderer from './TextRenderer.vue'
import ActionLink from './ActionLink.vue'
import { useGameState } from '../composables/useGameState.js'
import { usePanelRefresh } from '../composables/usePanelRefresh.js'
import { useCombat } from '../composables/useCombat.js'

const { state, doAction } = useGameState()
const { handleRefresh } = usePanelRefresh()
const { enterCombat } = useCombat()

const scene = computed(() => state.currentScene)

async function handleAction(action) {
    const response = await doAction(action.type, action.params)
    if (response.success && response.refreshPanels) {
        if (action.type === 'combat') {
            await enterCombat(state.playerId)
        } else {
            await handleRefresh(response.refreshPanels)
        }
    }
}
</script>

<style scoped>
.scene-panel { height: 100%; }
.scene-description { margin-bottom: 1.5rem; }
.scene-actions { border-top: 1px solid var(--panel-border-color); padding-top: 1rem; }
</style>
