<template>
  <div class="combat-view">
    <BattleGrid
      :combatants="combat.state?.combatants || []"
      :current-actor-id="currentActor?.id || ''"
    />
    <CommandBar
      ref="commandBarRef"
      :current-actor="currentActor"
      :targets="combat.validTargets"
      @command="onCommand"
      @cancel="onCancel"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import BattleGrid from './BattleGrid.vue'
import CommandBar from './CommandBar.vue'
import { useCombat } from '../../composables/useCombat.js'
import { useGameState } from '../../composables/useGameState.js'

const { combat, getCurrentActor, addCommand, allCommandsIssued, executeRound, loadTargets, exitCombat } = useCombat()
const { state, initialize } = useGameState()

const commandBarRef = ref(null)
const currentActor = computed(() => getCurrentActor())

async function onCommand(cmd) {
    addCommand(cmd.actorId, cmd.type, cmd.targetId)
    if (allCommandsIssued()) {
        await new Promise(r => setTimeout(r, 800))
        await executeRound(state.playerId)

        if (combat.state?.phase === 'VICTORY' || combat.state?.phase === 'DEFEAT') {
            setTimeout(() => {
                exitCombat()
                initialize()
            }, 2000)
        }
    } else {
        await loadTargets(state.playerId)
    }
}

function onCancel() {
    // Future: undo last command
}
</script>

<style scoped>
.combat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.combat-view > :first-child {
  flex: 1;
  min-height: 0;
}
</style>
