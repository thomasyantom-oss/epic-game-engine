<template>
  <div class="combat-view">
    <BattleGrid
      :combatants="combat.state?.combatants || []"
      :current-actor-id="currentActor?.id || ''"
      :current-actor="currentActor"
      :targets="combat.validTargets"
      @command="onCommand"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import BattleGrid from './BattleGrid.vue'
import { useCombat } from '../../composables/useCombat.js'
import { useGameState } from '../../composables/useGameState.js'
import { useMap } from '../../composables/useMap.js'

const { combat, getCurrentActor, addCommand, allCommandsIssued, executeRound, loadTargets, exitCombat } = useCombat()
const { state } = useGameState()
const { loadMap } = useMap()

const currentActor = computed(() => getCurrentActor())

async function onCommand(cmd) {
    if (cmd.type === 'FLEE') {
        await new Promise(r => setTimeout(r, 800))
        exitCombat(state.playerId)
        loadMap()
        return
    }
    addCommand(cmd.actorId, cmd.type, cmd.targetId)
    if (allCommandsIssued()) {
        await new Promise(r => setTimeout(r, 800))
        await executeRound(state.playerId)

        if (combat.state?.phase === 'VICTORY' || combat.state?.phase === 'DEFEAT') {
            setTimeout(() => {
                exitCombat(state.playerId)
                loadMap()
            }, 2000)
        }
    } else {
        await loadTargets(state.playerId)
    }
}
</script>

<style scoped>
.combat-view {
  height: 100%;
}
</style>
