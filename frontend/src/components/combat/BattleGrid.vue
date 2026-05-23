<template>
  <div class="battle-layout">
    <div class="round-banner">第 {{ combat.round }} 回合</div>
    <div class="battle-content">
    <div class="status-col player-col">
      <div v-for="(unit, idx) in playerUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
        <div class="unit-header" :class="{ active: unit.id === currentActorId }">
          <span class="unit-name player-name">{{ unit.name }}</span>
          <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
        </div>
        <div class="hp-bar">
          <div class="hp-fill" :style="{ width: hpPercent(unit) + '%' }"></div>
        </div>
      </div>
    </div>

    <div class="center-col">
      <div class="grid-area">
        <div class="player-grid">
          <div
            v-for="(cell, idx) in playerCells"
            :key="'p'+idx"
            class="terrain-cell"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker player">
              <span class="marker-num">{{ cell.marker.index }}</span>▶
            </span>
          </div>
        </div>
        <div class="grid-gap"></div>
        <div class="enemy-grid">
          <div
            v-for="(cell, idx) in enemyCells"
            :key="'e'+idx"
            class="terrain-cell"
            :class="{ 'target-cell': selectingTarget && cell.marker && cell.marker.alive }"
            @click="onCellClick(cell)"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker enemy">
              ◀<span class="marker-num">{{ cell.marker.index }}</span>
            </span>
          </div>
        </div>
      </div>

      <div class="command-row">
        <div class="cmd-col cmd-actor">
          <template v-if="currentActor && phase === 'COMMAND'">
            <div class="actor-avatar"></div>
            <div class="actor-name">{{ currentActor.name }}</div>
          </template>
        </div>
        <div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND'">
          <span class="cmd-item" :class="{ active: selectedCommand === 'ATTACK' }" @click="selectCommand('ATTACK')">攻击</span>
          <span class="cmd-item" :class="{ active: selectedCommand === 'DEFEND' }" @click="selectCommand('DEFEND')">防御</span>
          <span class="cmd-item" :class="{ active: selectedCommand === 'SKILL' }" @click="selectCommand('SKILL')">技能</span>
          <span class="cmd-item" :class="{ active: selectedCommand === 'ITEM' }" @click="selectCommand('ITEM')">道具</span>
          <span class="cmd-item" @click="selectCommand('FLEE')">逃跑</span>
          <span v-if="step === 'target'" class="cmd-item cancel-item" @click="cancelSelect">取消</span>
        </div>
        <div class="cmd-col cmd-detail">
        </div>
      </div>
    </div>

    <div class="status-col enemy-col">
      <div v-for="(unit, idx) in enemyUnits" :key="unit.id" class="unit-status" :class="{ dead: !unit.alive }">
        <div class="unit-header">
          <span class="unit-name enemy-name">{{ unit.name }}</span>
          <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
        </div>
        <div class="hp-bar enemy-bar">
          <div class="hp-fill enemy-fill" :style="{ width: hpPercent(unit) + '%' }"></div>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  combat: { type: Object, required: true },
  playerId: { type: String, default: '' }
})

const emit = defineEmits(['command'])

const step = ref('command')
const selectedCommand = ref(null)

const phase = computed(() => props.combat?.phase || 'COMMAND')

const playerUnits = computed(() =>
  (props.combat?.combatants || []).filter(c => c.side === 'PLAYER')
)

const enemyUnits = computed(() =>
  (props.combat?.combatants || []).filter(c => c.side === 'ENEMY')
)

const currentActorId = computed(() => {
  const alive = playerUnits.value.find(u => u.alive)
  return alive ? alive.id : ''
})

const currentActor = computed(() => {
  return playerUnits.value.find(u => u.id === currentActorId.value) || null
})

watch(currentActor, () => {
  step.value = 'command'
  selectedCommand.value = null
})

const selectingTarget = computed(() => step.value === 'target')

function hpPercent(unit) {
  return Math.max(0, unit.hp / unit.maxHp * 100)
}

const playerCells = computed(() => {
  const cells = []
  const positions = mapUnitsToGrid(playerUnits.value, 'player')
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) {
      const marker = positions.find(p => p.col === col && p.row === row)
      cells.push({ marker })
    }
  }
  return cells
})

const enemyCells = computed(() => {
  const cells = []
  const positions = mapUnitsToGrid(enemyUnits.value, 'enemy')
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) {
      const marker = positions.find(p => p.col === col && p.row === row)
      cells.push({ marker })
    }
  }
  return cells
})

function mapUnitsToGrid(units, side) {
  const markers = []
  const playerColMap = { BACK: 0, MID: 1, FRONT: 2 }
  const enemyColMap = { FRONT: 0, MID: 1, BACK: 2 }

  units.forEach((unit, idx) => {
    const unitRow = unit.row || 'FRONT'
    const col = side === 'player' ? (playerColMap[unitRow] ?? 2) : (enemyColMap[unitRow] ?? 0)
    const row = unit.slot ?? idx % 3
    markers.push({ col, row, side, index: idx + 1, alive: unit.alive, id: unit.id })
  })
  return markers
}

function selectCommand(cmd) {
  if (!currentActor.value) return
  if (cmd === 'FLEE') {
    emit('command', { type: 'combat_command', params: { command: 'FLEE', targetId: null } })
    return
  }
  if (cmd === 'DEFEND') {
    emit('command', { type: 'combat_command', params: { command: 'DEFEND', targetId: null } })
    return
  }
  if (cmd === 'SKILL' || cmd === 'ITEM') {
    selectedCommand.value = 'ATTACK'
    step.value = 'target'
    return
  }
  selectedCommand.value = cmd
  step.value = 'target'
}

function onCellClick(cell) {
  if (step.value !== 'target') return
  if (!cell.marker || !cell.marker.alive) return
  emit('command', { type: 'combat_command', params: { command: selectedCommand.value, targetId: cell.marker.id } })
  step.value = 'command'
  selectedCommand.value = null
}

function cancelSelect() {
  step.value = 'command'
  selectedCommand.value = null
}
</script>

<style scoped>
.battle-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.round-banner {
  text-align: center;
  padding: 0.3rem;
  color: var(--color-highlight);
  font-weight: bold;
  border-bottom: 2px solid var(--panel-border-color);
}

.battle-content {
  display: grid;
  grid-template-columns: 15% 1fr 15%;
  flex: 1;
  min-height: 0;
}

.status-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.8rem;
  padding: 0.5rem;
  border-right: 2px solid var(--panel-border-color);
}

.enemy-col {
  border-right: none;
  border-left: 2px solid var(--panel-border-color);
}

.center-col {
  display: grid;
  grid-template-rows: 1fr 25%;
  min-height: 0;
  overflow: hidden;
}

.unit-status {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.unit-status.dead {
  opacity: 0.4;
}

.unit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.unit-header.active {
  text-shadow: 0 0 4px var(--link-color);
}

.player-name { color: var(--color-player); }
.enemy-name { color: var(--color-enemy); }

.hp-bar {
  height: 4px;
  background: #333;
  border-radius: 2px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  background: var(--color-player);
  transition: width 0.3s;
}

.enemy-bar .hp-fill, .enemy-fill {
  background: var(--color-enemy);
}

.grid-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5%;
  padding: 5%;
  min-width: 0;
  min-height: 0;
}

.player-grid, .enemy-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 2px;
  height: 90%;
  aspect-ratio: 1;
}

.grid-gap {
  display: none;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #222;
  border-radius: 2px;
  background-color: #333;
}

.terrain-cell.target-cell {
  cursor: pointer;
  border: 3px solid var(--color-enemy);
}

.terrain-cell.target-cell:hover {
  box-shadow: 0 0 4px var(--color-enemy);
}

.marker {
  font-weight: bold;
  display: flex;
  align-items: center;
}

.marker.player { color: var(--color-player); }
.marker.enemy { color: var(--color-enemy); }

.marker-num {
  color: #fff;
}

.command-row {
  border-top: 2px solid var(--panel-border-color);
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  height: 100%;
}

.cmd-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
}

.cmd-actor {
  border-right: 1px solid var(--panel-border-color);
}

.cmd-actions {
  border-right: 1px solid var(--panel-border-color);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.3rem;
  align-items: center;
  justify-items: center;
  padding: 0.3rem;
}

.actor-avatar {
  width: 2.2rem;
  height: 2.2rem;
  border: 1px solid var(--color-player);
  border-radius: 4px;
  background: #1a2a3a;
}

.actor-name {
  color: var(--color-player);
}

.cmd-item {
  cursor: pointer;
  transition: color 0.2s;
}

.cmd-item:hover {
  color: var(--link-color);
}

.cmd-item.active {
  color: var(--link-color);
}

.cancel-item {
  color: var(--color-enemy);
}
</style>
