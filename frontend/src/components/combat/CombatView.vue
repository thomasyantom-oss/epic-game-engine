<template>
  <div class="combat-grid">
    <div class="panel-main">
      <TabPanel :tabs="[{ id: 'battle', label: '战场' }]" default-tab="battle">
        <template #battle>
          <BattleField
            :combatants="combat.state?.combatants || []"
            :current-actor-id="currentActor?.id || ''"
            :target-ids="combat.validTargets.map(t => t.id)"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-cmd">
      <TabPanel :tabs="[{ id: 'command', label: '指令' }]" default-tab="command">
        <template #command>
          <CommandPanel
            :current-actor="currentActor"
            :targets="combat.validTargets"
            @command="onCommand"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-log">
      <TabPanel :tabs="[{ id: 'log', label: '战斗' }]" default-tab="log">
        <template #log>
          <CombatLogPanel
            :results="combat.results"
            :round="combat.state?.round || 1"
            :phase="combat.state?.phase || 'COMMAND'"
          />
        </template>
      </TabPanel>
    </div>

    <div class="panel-status">
      <TabPanel :tabs="[{ id: 'status', label: '状态' }]" default-tab="status">
        <template #status>
          <CombatStatusPanel :actor="currentActor" />
        </template>
      </TabPanel>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import TabPanel from '../TabPanel.vue'
import BattleField from './BattleField.vue'
import CommandPanel from './CommandPanel.vue'
import CombatLogPanel from './CombatLogPanel.vue'
import CombatStatusPanel from './CombatStatusPanel.vue'
import { useCombat } from '../../composables/useCombat.js'
import { useGameState } from '../../composables/useGameState.js'

const { combat, getCurrentActor, addCommand, allCommandsIssued, executeRound, loadTargets, exitCombat } = useCombat()
const { state, initialize } = useGameState()

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
</script>

<style scoped>
.combat-grid {
    display: grid;
    grid-template-columns: 3fr 1fr;
    grid-template-rows: 2fr 1fr;
    gap: 0.5rem;
    height: 100%;
}

.panel-main { grid-column: 1; grid-row: 1; }
.panel-cmd { grid-column: 2; grid-row: 1; }
.panel-log { grid-column: 1; grid-row: 2; }
.panel-status { grid-column: 2; grid-row: 2; }
</style>
