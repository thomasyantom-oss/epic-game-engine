<template>
  <div class="battle-grid">
    <div class="battle-header">
      <span class="round-info">第 {{ combat.round }} 回合</span>
      <span class="phase-info">{{ phaseLabel }}</span>
    </div>

    <div class="battle-field">
      <!-- Player side -->
      <div class="side player-side">
        <div class="side-label">我方</div>
        <div v-for="unit in playerUnits" :key="unit.id" class="unit-card player-unit">
          <div class="unit-name">{{ unit.name }}</div>
          <div class="hp-bar-container">
            <div class="hp-bar" :style="hpBarStyle(unit)"></div>
            <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
          </div>
          <div v-if="!unit.alive" class="unit-dead">击败</div>
        </div>
      </div>

      <!-- VS divider -->
      <div class="vs-divider">VS</div>

      <!-- Enemy side -->
      <div class="side enemy-side">
        <div class="side-label">敌方</div>
        <div v-for="unit in enemyUnits" :key="unit.id"
             class="unit-card enemy-unit"
             :class="{ selected: selectedTarget === unit.id, targetable: canSelectTarget }"
             @click="selectTarget(unit)">
          <div class="unit-name">{{ unit.name }}</div>
          <div class="hp-bar-container">
            <div class="hp-bar enemy-bar" :style="hpBarStyle(unit)"></div>
            <span class="hp-text">{{ unit.hp }}/{{ unit.maxHp }}</span>
          </div>
          <div v-if="!unit.alive" class="unit-dead">击败</div>
        </div>
      </div>
    </div>

    <!-- Command menu -->
    <div class="command-menu">
      <button class="cmd-btn cmd-attack" @click="doCommand('ATTACK')"
              :disabled="!canAct">攻击</button>
      <button class="cmd-btn cmd-defend" @click="doCommand('DEFEND')"
              :disabled="!canAct">防御</button>
      <button class="cmd-btn cmd-flee" @click="doCommand('FLEE')"
              :disabled="!canAct">逃跑</button>
    </div>

    <div v-if="needsTarget" class="target-hint">
      请点击一个敌方目标
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({ combat: Object, playerId: String })
const emit = defineEmits(['command'])

const selectedTarget = ref(null)
const pendingCommand = ref(null)

const playerUnits = computed(() =>
  (props.combat?.combatants || []).filter(c => c.side === 'PLAYER')
)

const enemyUnits = computed(() =>
  (props.combat?.combatants || []).filter(c => c.side === 'ENEMY')
)

const phaseLabel = computed(() => {
  const p = props.combat?.phase
  if (p === 'AWAITING_INPUT') return '等待指令'
  if (p === 'RESOLVING') return '回合结算中'
  if (p === 'FINISHED') return '战斗结束'
  return p || ''
})

const canAct = computed(() => props.combat?.phase === 'AWAITING_INPUT')
const canSelectTarget = computed(() => canAct.value)
const needsTarget = computed(() => pendingCommand.value === 'ATTACK' && !selectedTarget.value)

function hpBarStyle(unit) {
  const pct = unit.maxHp > 0 ? (unit.hp / unit.maxHp) * 100 : 0
  const color = unit.side === 'PLAYER' ? '#4ecdc4' : '#e94560'
  return { width: pct + '%', backgroundColor: color }
}

function selectTarget(unit) {
  if (!canSelectTarget.value) return
  if (!unit.alive) return
  selectedTarget.value = unit.id
  if (pendingCommand.value === 'ATTACK') {
    submitCommand('ATTACK', unit.id)
    pendingCommand.value = null
  }
}

function doCommand(cmd) {
  if (!canAct.value) return
  if (cmd === 'ATTACK') {
    // If target already selected, use it; otherwise prompt
    if (selectedTarget.value) {
      submitCommand('ATTACK', selectedTarget.value)
    } else {
      // Auto-select first alive enemy
      const firstAlive = enemyUnits.value.find(u => u.alive)
      if (firstAlive) {
        selectedTarget.value = firstAlive.id
        submitCommand('ATTACK', firstAlive.id)
      } else {
        pendingCommand.value = 'ATTACK'
      }
    }
  } else {
    submitCommand(cmd, null)
  }
}

function submitCommand(command, targetId) {
  emit('command', {
    type: 'combat_command',
    params: {
      combatId: props.combat.combatId,
      entityId: props.playerId,
      command: command,
      targetId: targetId
    }
  })
  selectedTarget.value = null
}
</script>

<style scoped>
.battle-grid { display: flex; flex-direction: column; height: 100%; padding: 0.5rem; gap: 0.5rem; }
.battle-header { display: flex; justify-content: space-between; align-items: center; padding: 0.3rem 0.5rem; border-bottom: 1px solid rgba(255,255,255,0.15); }
.round-info { color: #ffd93d; font-weight: bold; }
.phase-info { color: var(--text-color); opacity: 0.8; font-size: 0.9em; }

.battle-field { display: flex; flex: 1; align-items: center; justify-content: center; gap: 1rem; min-height: 0; }
.side { display: flex; flex-direction: column; gap: 0.4rem; flex: 1; }
.side-label { font-size: 0.85em; opacity: 0.6; margin-bottom: 0.2rem; text-align: center; }
.vs-divider { font-size: 1.2em; font-weight: bold; color: #ffd93d; padding: 0 0.5rem; }

.unit-card {
  padding: 0.4rem 0.6rem;
  border-radius: 4px;
  border: 1px solid rgba(255,255,255,0.1);
  background: rgba(255,255,255,0.03);
}
.unit-card.targetable { cursor: pointer; }
.unit-card.targetable:hover { border-color: #e94560; background: rgba(233, 69, 96, 0.1); }
.unit-card.selected { border-color: #e94560; background: rgba(233, 69, 96, 0.15); box-shadow: 0 0 6px rgba(233, 69, 96, 0.3); }

.unit-name { font-size: 0.9em; margin-bottom: 0.2rem; color: var(--text-color); }
.hp-bar-container { position: relative; height: 16px; background: rgba(255,255,255,0.1); border-radius: 3px; overflow: hidden; }
.hp-bar { height: 100%; transition: width 0.3s ease; border-radius: 3px; }
.hp-text { position: absolute; top: 0; left: 50%; transform: translateX(-50%); font-size: 0.7em; line-height: 16px; color: #fff; text-shadow: 0 0 2px #000; }
.unit-dead { color: #e94560; font-size: 0.8em; margin-top: 0.2rem; }

.command-menu { display: flex; gap: 0.5rem; justify-content: center; padding: 0.5rem 0; border-top: 1px solid rgba(255,255,255,0.15); }
.cmd-btn {
  padding: 0.4rem 1.2rem;
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 4px;
  background: rgba(255,255,255,0.05);
  color: var(--text-color);
  cursor: pointer;
  font-size: 0.9em;
  transition: all 0.2s;
}
.cmd-btn:hover:not(:disabled) { background: rgba(255,255,255,0.1); }
.cmd-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.cmd-attack { border-color: rgba(233, 69, 96, 0.5); color: #e94560; }
.cmd-attack:hover:not(:disabled) { background: rgba(233, 69, 96, 0.1); }
.cmd-defend { border-color: rgba(78, 205, 196, 0.5); color: #4ecdc4; }
.cmd-defend:hover:not(:disabled) { background: rgba(78, 205, 196, 0.1); }
.cmd-flee { border-color: rgba(255, 217, 61, 0.5); color: #ffd93d; }
.cmd-flee:hover:not(:disabled) { background: rgba(255, 217, 61, 0.1); }

.target-hint { text-align: center; color: #e94560; font-size: 0.85em; animation: blink 1.5s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
