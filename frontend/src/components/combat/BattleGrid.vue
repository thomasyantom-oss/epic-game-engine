<template>
  <div class="battle-layout">
    <div class="round-banner">
      <span>第 {{ combat.round }} 回合</span>
      <span v-if="showTimer && !animating" class="turn-timer">{{ timerDisplay }}</span>
    </div>
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
      <div class="grid-area" ref="gridAreaRef">
        <div class="player-grid">
          <div
            v-for="(cell, idx) in playerCells"
            :key="'p'+idx"
            class="terrain-cell"
            :class="[{ 'shaking': isShaking(cell.marker?.id) }, lungingClass(cell.marker?.id)]"
            :data-unit-id="cell.marker?.id"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker player">
              P{{ cell.marker.index }}
            </span>
            <span v-if="cell.marker && cell.marker.alive" class="corner-hp">
              {{ cell.marker.hp }}/{{ cell.marker.maxHp }}
            </span>
          </div>
        </div>
        <div class="grid-gap"></div>
        <div class="enemy-grid">
          <div
            v-for="(cell, idx) in enemyCells"
            :key="'e'+idx"
            class="terrain-cell"
            :class="[{ 'target-cell': selectingTarget && cell.marker && cell.marker.alive, 'shaking': isShaking(cell.marker?.id) }, lungingClass(cell.marker?.id)]"
            :data-unit-id="cell.marker?.id"
            @click="onCellClick(cell)"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker enemy">
              E{{ cell.marker.index }}
            </span>
            <span v-if="cell.marker && cell.marker.alive" class="corner-hp">
              {{ cell.marker.hp }}/{{ cell.marker.maxHp }}
            </span>
          </div>
        </div>
        <AnimationLayer :animations="activeAnimations" :cell-positions="cellPositions" />
      </div>

      <div class="command-row">
        <div class="cmd-col cmd-actor">
          <template v-if="currentActor && phase === 'COMMAND' && !animating">
            <div class="actor-avatar"></div>
            <div class="actor-name">{{ currentActor.name }}</div>
          </template>
        </div>
        <div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND' && !animating">
          <ActionLink v-for="cmd in commands" :key="cmd.params?.command"
                      :action="cmd"
                      :class="{ active: selectedCommand === cmd.params?.command }"
                      @action="selectCommand(cmd)" />
          <span v-if="step === 'target'" class="cmd-item cancel-item" @click="cancelSelect">▸ 取消</span>
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
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import ActionLink from '../ActionLink.vue'
import AnimationLayer from './AnimationLayer.vue'
import { useAnimationPlayer } from '../../composables/useAnimationPlayer.js'

const props = defineProps({
  combat: { type: Object, required: true },
  playerId: { type: String, default: '' },
  commands: { type: Array, default: () => [] },
  animating: { type: Boolean, default: false }
})

const emit = defineEmits(['command'])

const { playing, activeAnimations, play } = useAnimationPlayer()

const gridAreaRef = ref(null)
const cellPositions = ref({})

function updateCellPositions() {
  if (!gridAreaRef.value) return
  const gridRect = gridAreaRef.value.getBoundingClientRect()
  const positions = {}
  const cells = gridAreaRef.value.querySelectorAll('[data-unit-id]')
  cells.forEach(el => {
    const id = el.dataset.unitId
    const rect = el.getBoundingClientRect()
    positions[id] = {
      x: rect.left - gridRect.left,
      y: rect.top - gridRect.top,
      w: rect.width,
      h: rect.height
    }
  })
  cellPositions.value = positions
}

function isShaking(unitId) {
  if (!unitId) return false
  return activeAnimations.value.some(a => a.type === 'shake' && a.target === unitId)
}

function lungingClass(unitId) {
  if (!unitId) return ''
  const anim = activeAnimations.value.find(a => a.type === 'lunge' && a.target === unitId)
  if (!anim) return ''
  return anim.side === 'player' ? 'lunge-right' : 'lunge-left'
}

defineExpose({ play, updateCellPositions })

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
    markers.push({ col, row, side, index: idx + 1, alive: unit.alive, id: unit.id, hp: unit.hp, maxHp: unit.maxHp })
  })
  return markers
}

function selectCommand(action) {
  if (!currentActor.value) return
  var cmd = action.params?.command
  var style = action.style

  if (style === 'instant' || cmd === 'FLEE' || cmd === 'DEFEND') {
    stopTimer()
    emit('command', { type: 'combat_command', params: { command: cmd, targetId: null } })
    return
  }
  // requires_target
  selectedCommand.value = cmd
  step.value = 'target'
}

function onCellClick(cell) {
  if (step.value !== 'target') return
  if (!cell.marker || !cell.marker.alive) return
  stopTimer()
  emit('command', { type: 'combat_command', params: { command: selectedCommand.value, targetId: cell.marker.id } })
  step.value = 'command'
  selectedCommand.value = null
}

function cancelSelect() {
  step.value = 'command'
  selectedCommand.value = null
}

// Turn timer — counts down from turnTimer seconds, auto-attacks on timeout
const timeLeft = ref(30)
const showTimer = computed(() => phase.value === 'COMMAND' && !props.animating)
const timerDisplay = computed(() => timeLeft.value + 's')
let timerInterval = null

function startTimer() {
  stopTimer()
  timeLeft.value = props.combat?.turnTimer || 30
  timerInterval = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      stopTimer()
      autoAttack()
    }
  }, 1000)
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

function autoAttack() {
  var firstAliveEnemy = enemyUnits.value.find(u => u.alive)
  if (firstAliveEnemy) {
    emit('command', { type: 'combat_command', params: { command: 'ATTACK', targetId: firstAliveEnemy.id } })
  }
}

watch(() => props.combat?.round, () => {
  if (phase.value === 'COMMAND' && !props.animating) {
    startTimer()
  }
}, { immediate: true })

watch(() => props.animating, (anim) => {
  if (anim) {
    stopTimer()
  } else if (phase.value === 'COMMAND') {
    startTimer()
  }
})

onUnmounted(() => stopTimer())
</script>

<style scoped>
.battle-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.round-banner {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 0.3rem;
  color: var(--color-highlight);
  font-weight: bold;
  border-bottom: 2px solid var(--panel-border-color);
}

.turn-timer {
  color: var(--color-text);
  font-weight: normal;
  opacity: 0.8;
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
  position: relative;
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
  border: 3px solid #444;
  border-radius: 3px;
  background-color: #1a1a2e;
  position: relative;
  overflow: hidden;
}

.terrain-cell.target-cell {
  cursor: pointer;
  border: 4px solid var(--color-highlight);
  box-shadow: 0 0 10px rgba(255, 152, 0, 0.4), inset 0 0 6px rgba(255, 152, 0, 0.15);
}

.terrain-cell.target-cell:hover {
  box-shadow: 0 0 14px rgba(255, 152, 0, 0.5), inset 0 0 8px rgba(255, 152, 0, 0.2);
}

.marker {
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.75em;
}

.marker.player { color: var(--color-player); background: rgba(79, 195, 247, 0.15); }
.marker.enemy { color: var(--color-enemy); background: rgba(229, 115, 115, 0.15); }

.corner-hp {
  position: absolute;
  bottom: 2px;
  right: 3px;
  font-size: 0.45em;
  color: #999;
  font-weight: bold;
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

.terrain-cell.shaking {
  animation: cell-shake 250ms ease-in-out;
}

@keyframes cell-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-3px); }
  40% { transform: translateX(3px); }
  60% { transform: translateX(-2px); }
  80% { transform: translateX(2px); }
}

.terrain-cell.lunge-right {
  animation: lunge-right 250ms ease-in-out;
}

.terrain-cell.lunge-left {
  animation: lunge-left 250ms ease-in-out;
}

@keyframes lunge-right {
  0%, 100% { transform: translateX(0); }
  40% { transform: translateX(6px); }
}

@keyframes lunge-left {
  0%, 100% { transform: translateX(0); }
  40% { transform: translateX(-6px); }
}
</style>
