<template>
  <div class="battle-layout">
    <div class="battle-content">
    <div class="status-col player-col">
      <div v-for="(unit, idx) in playerUnits" :key="unit.id"
           class="unit-status"
           :class="{ dead: !unit.alive, compact: playerCompressed }"
           @mouseenter="playerHovered = unit.id"
           @mouseleave="playerHovered = null">
        <!-- 压缩态：只显示名字和 HP 色条 -->
        <template v-if="playerCompressed && playerHovered !== unit.id">
          <div class="compact-bar" :style="{ background: hpColor('player', unit) }"></div>
          <span class="compact-name player-name">{{ unit.name }}</span>
        </template>
        <!-- 完整态 -->
        <template v-else>
          <div class="unit-header" :class="{ active: unit.id === currentActorId }">
            <span class="unit-name player-name">{{ unit.name }}</span>
          </div>
          <div class="stat-bar-row">
            <span class="stat-label" :style="{ color: unit.hpColor }">HP</span>
            <div class="stat-bar-track">
              <div class="stat-bar-fill" :style="{ width: hpPercent(unit) + '%', background: unit.hpColor }"></div>
            </div>
            <span class="stat-bar-num">{{ displayHp[unit.id] ?? unit.hp }}/{{ unit.maxHp }}</span>
          </div>
          <div class="stat-bar-row" :style="{ visibility: unit.maxMp > 0 ? 'visible' : 'hidden' }">
            <span class="stat-label" :style="{ color: unit.mpColor }">MP</span>
            <div class="stat-bar-track">
              <div class="stat-bar-fill" :style="{ width: mpPercent(unit) + '%', background: unit.mpColor }"></div>
            </div>
            <span class="stat-bar-num">{{ unit.mp }}/{{ unit.maxMp }}</span>
          </div>
          <div class="buff-status-row" :style="{ visibility: (unit.buffs && unit.buffs.length) ? 'visible' : 'hidden' }">
            <div class="buff-status-group">
              <div v-for="(b, bi) in unitBuffs(unit)" :key="bi" class="bstat-icon" :title="`${b.id} ×${b.stacks} · ${b.remaining ?? '?'}回合`">
                <div class="bstat-tri up" :style="{ background: b.color || 'var(--color-highlight)' }"></div>
                <span class="bstat-stack">{{ b.stacks }}</span>
                <span class="bstat-remain">{{ b.remaining ?? '' }}</span>
              </div>
            </div>
            <div class="bstat-divider"></div>
            <div class="buff-status-group">
              <div v-for="(b, bi) in unitDebuffs(unit)" :key="bi" class="bstat-icon" :title="`${b.id} ×${b.stacks} · ${b.remaining ?? '?'}回合`">
                <div class="bstat-tri down" :style="{ background: b.color || 'var(--color-enemy)' }"></div>
                <span class="bstat-stack bstat-stack-down">{{ b.stacks }}</span>
                <span class="bstat-remain">{{ b.remaining ?? '' }}</span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="center-col">
      <div class="round-top">第 {{ combat.round }} 回合</div>
      <div class="grid-area" ref="gridAreaRef">
        <div class="player-grid">
          <div
            v-for="(cell, idx) in playerCells"
            :key="'p'+idx"
            class="terrain-cell"
            :style="cellBgStyle"
            :data-unit-id="cell.marker?.id"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker"
                  :class="[markerSideClass(cell.marker.side), { 'shaking': isShaking(cell.marker?.id) }, lungingClass(cell.marker?.id)]">
              P{{ cell.marker.index }}
            </span>
            <span v-if="cell.marker?.alive" v-for="(buff, bi) in (cell.marker?.buffs || [])" :key="bi"
                  class="buff-indicator" :class="'corner-' + bi" :style="{ backgroundColor: buffColor(buff) }"></span>
          </div>
        </div>
        <div class="grid-gap">
          <span v-if="showTimer && !animating" class="mid-timer">{{ timerDisplay }}</span>
        </div>
        <div class="enemy-grid">
          <div
            v-for="(cell, idx) in enemyCells"
            :key="'e'+idx"
            class="terrain-cell"
            :class="{ 'target-cell': isValidTarget(cell), 'aoe-border': isInAoeZone(cell), 'aoe-hit': isInAoeZone(cell) && cell.marker && cell.marker.alive }"
            :style="cellBgStyle"
            :data-unit-id="cell.marker?.id"
            @click="onCellClick(cell)"
            @mouseenter="onCellHover(cell)"
            @mouseleave="hoveredCell = null"
          >
            <span v-if="cell.marker && cell.marker.alive" class="marker"
                  :class="[markerSideClass(cell.marker.side), { 'shaking': isShaking(cell.marker?.id) }, lungingClass(cell.marker?.id)]">
              E{{ cell.marker.index }}
            </span>
            <span v-if="cell.marker?.alive" v-for="(buff, bi) in (cell.marker?.buffs || [])" :key="bi"
                  class="buff-indicator" :class="'corner-' + bi" :style="{ backgroundColor: buffColor(buff) }"></span>
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
        <div class="cmd-col cmd-actions" v-if="currentActor && phase === 'COMMAND' && !animating" :class="{ locked: showSkills || step === 'target' }">
          <button v-if="firstMainAction"
                  class="cmd-btn"
                  :class="{ disabled: firstMainAction.style === 'disabled' }"
                  @click="selectCommand(firstMainAction)">
            {{ firstMainAction.label }}
          </button>
          <button v-if="skillActions.length > 0"
                  class="cmd-btn"
                  :class="{ active: showSkills }"
                  @click="showSkills = true">技能</button>
          <button v-for="cmd in otherMainActions" :key="cmd.params?.command"
                  class="cmd-btn"
                  :class="{ disabled: cmd.style === 'disabled' }"
                  @click="selectCommand(cmd)">
            {{ cmd.label }}
          </button>
          <button v-if="fleeAction"
                  class="cmd-btn"
                  @click="selectCommand(fleeAction)">
            {{ fleeAction.label }}
          </button>
        </div>
        <div class="cmd-col cmd-sub" v-if="currentActor && phase === 'COMMAND' && !animating">
          <template v-if="step === 'target'">
            <span class="target-hint">{{ targetPrompt }}</span>
            <button class="cmd-btn cancel-btn" @click="cancelSelect">取消</button>
          </template>
          <template v-else-if="showSkills">
            <button v-for="cmd in skillActions" :key="cmd.params?.command"
                    class="cmd-btn skill-cmd-btn"
                    :class="{ disabled: cmd.style === 'disabled' }"
                    @mouseenter="onSkillHover(cmd, $event)"
                    @mousemove="moveTooltip"
                    @mouseleave="hideTooltip"
                    @click="selectCommand(cmd)">
              {{ cmd.label }}
            </button>
            <button class="cmd-btn cancel-btn" @click="cancelSub">取消</button>
          </template>
        </div>
      </div>
    </div>

    <div class="status-col enemy-col">
      <div v-for="(unit, idx) in enemyUnits" :key="unit.id"
           class="unit-status"
           :class="{ dead: !unit.alive, compact: enemyCompressed }"
           @mouseenter="enemyHovered = unit.id"
           @mouseleave="enemyHovered = null">
        <!-- 压缩态 -->
        <template v-if="enemyCompressed && enemyHovered !== unit.id">
          <div class="compact-bar" :style="{ background: hpColor('enemy', unit) }"></div>
          <span class="compact-name enemy-name">{{ unit.name }}</span>
        </template>
        <!-- 完整态 -->
        <template v-else>
        <div class="unit-header">
          <span class="unit-name enemy-name">{{ unit.name }}</span>
        </div>
        <div class="stat-bar-row">
          <span class="stat-label" :style="{ color: unit.hpColor }">HP</span>
          <div class="stat-bar-track">
            <div class="stat-bar-fill" :style="{ width: hpPercent(unit) + '%', background: unit.hpColor }"></div>
          </div>
          <span class="stat-bar-num">{{ unit.hp }}/{{ unit.maxHp }}</span>
        </div>
        <div class="stat-bar-row" :style="{ visibility: unit.maxMp > 0 ? 'visible' : 'hidden' }">
          <span class="stat-label" :style="{ color: unit.mpColor }">MP</span>
          <div class="stat-bar-track">
            <div class="stat-bar-fill" :style="{ width: mpPercent(unit) + '%', background: unit.mpColor }"></div>
          </div>
          <span class="stat-bar-num">{{ unit.mp }}/{{ unit.maxMp }}</span>
        </div>
        <div class="buff-status-row" :style="{ visibility: (unit.buffs && unit.buffs.length) ? 'visible' : 'hidden' }">
          <div class="buff-status-group">
            <div v-for="(b, bi) in unitBuffs(unit)" :key="bi" class="bstat-icon" :title="`${b.id} ×${b.stacks} · ${b.remaining ?? '?'}回合`">
              <div class="bstat-tri up" :style="{ background: b.color || '#888' }"></div>
              <span class="bstat-stack">{{ b.stacks }}</span>
              <span class="bstat-remain">{{ b.remaining ?? '' }}</span>
            </div>
          </div>
          <div class="bstat-divider"></div>
          <div class="buff-status-group">
            <div v-for="(b, bi) in unitDebuffs(unit)" :key="bi" class="bstat-icon" :title="`${b.id} ×${b.stacks} · ${b.remaining ?? '?'}回合`">
              <div class="bstat-tri down" :style="{ background: b.color || '#888' }"></div>
              <span class="bstat-stack bstat-stack-down">{{ b.stacks }}</span>
              <span class="bstat-remain">{{ b.remaining ?? '' }}</span>
            </div>
          </div>
        </div>
        </template>
      </div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import AnimationLayer from './AnimationLayer.vue'
import { useAnimationPlayer } from '../../composables/useAnimationPlayer.js'
import { useTooltip } from '../../composables/useTooltip.js'

const props = defineProps({
  combat: { type: Object, required: true },
  playerId: { type: String, default: '' },
  commands: { type: Array, default: () => [] },
  animating: { type: Boolean, default: false },
  terrainColor: { type: String, default: null }
})

const emit = defineEmits(['command'])

const { showTooltip, moveTooltip, hideTooltip } = useTooltip()

function onSkillHover(cmd, event) {
  if (!cmd.params?.description) return
  showTooltip({
    title: cmd.label,
    titleColor: 'var(--color-highlight)',
    rows: [
      { label: '消耗', value: cmd.params?.mpCost ? cmd.params.mpCost + ' MP' : '无', valueColor: 'var(--color-mp)' },
      { label: '──────', value: '', valueColor: null },
      { label: cmd.params.description, value: '', valueColor: null }
    ]
  }, event)
}

const { playing, activeAnimations, playedEventIndex, play } = useAnimationPlayer()

const gridAreaRef = ref(null)
const cellPositions = ref({})
const prevCombatants = ref(null)
const displayHp = ref({})

// When combat prop changes with events, cache the "before" state
watch(() => props.combat, (combat, oldCombat) => {
  if (!combat) return
  const events = combat.events || []
  if (events.length > 0 && oldCombat && oldCombat.combatants) {
    // Cache old HP values as pre-animation state
    const hp = {}
    for (const c of oldCombat.combatants) {
      hp[c.id] = c.hp
    }
    prevCombatants.value = oldCombat.combatants
    displayHp.value = hp
  } else if (events.length > 0) {
    // First render with events — reverse-engineer pre-animation HP
    const hp = {}
    for (const c of combat.combatants) {
      hp[c.id] = c.hp
    }
    for (let i = events.length - 1; i >= 0; i--) {
      const effs = events[i].effects || []
      for (const eff of effs) {
        if (eff.type === 'hp_change' && eff.target) {
          const amount = eff.data?.amount ?? eff.amount ?? 0
          hp[eff.target] = (hp[eff.target] || 0) - amount
        }
      }
    }
    displayHp.value = hp
  }
}, { flush: 'sync' })

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
  // Also index enemy grid cells by coordinate
  const enemyGridEl = gridAreaRef.value.querySelector('.enemy-grid')
  if (enemyGridEl) {
    const allCells = enemyGridEl.querySelectorAll('.terrain-cell')
    allCells.forEach((el, idx) => {
      const row = Math.floor(idx / 3)
      const col = idx % 3
      const rect = el.getBoundingClientRect()
      positions['cell_' + row + '_' + col] = {
        x: rect.left - gridRect.left,
        y: rect.top - gridRect.top,
        w: rect.width,
        h: rect.height
      }
    })
  }
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

function buffColor(buff) {
  return buff.color || 'var(--color-text)'
}

const COMPACT_THRESHOLD = 5

const playerHovered = ref(null)
const enemyHovered = ref(null)

const playerCompressed = computed(() => playerUnits.value.length > COMPACT_THRESHOLD)
const enemyCompressed = computed(() => enemyUnits.value.length > COMPACT_THRESHOLD)

function hpColor(side, unit) {
  const sideMap = { player: 'var(--color-player)', enemy: 'var(--color-enemy)', neutral: 'var(--color-neutral)', ally: 'var(--color-ally)' }
  return sideMap[side] || 'var(--color-enemy)'
}

function markerSideClass(side) {
  const map = { PLAYER: 'player', ENEMY: 'enemy', NEUTRAL: 'neutral', ALLY: 'ally' }
  return map[side] || 'enemy'
}

function unitBuffs(unit) {
  if (!unit.alive) return []
  return (unit.buffs || []).filter(b => b.positive === true)
}

function unitDebuffs(unit) {
  if (!unit.alive) return []
  return (unit.buffs || []).filter(b => b.positive === false || b.positive === undefined)
}

const cellBgStyle = computed(() => {
  if (!props.terrainColor) return {}
  const hex = props.terrainColor.replace('#', '')
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)
  return { backgroundColor: `rgba(${r}, ${g}, ${b}, 0.3)` }
})

function playWithCallback(events, onEvent) {
  return play(events, (idx) => {
    // Apply this event's effects to displayHp
    const event = events[idx]
    if (event && event.effects) {
      const hp = { ...displayHp.value }
      for (const eff of event.effects) {
        if (eff.type === 'hp_change' && eff.target) {
          const amount = eff.data?.amount ?? eff.amount ?? 0
          hp[eff.target] = (hp[eff.target] || 0) + amount
        }
      }
      displayHp.value = hp
    }
    if (onEvent) onEvent(idx)
  })
}

function animationDone() {
  displayHp.value = {}
}

defineExpose({ play: playWithCallback, updateCellPositions, animationDone })

const step = ref('command')
const selectedCommand = ref(null)
const showSkills = ref(false)

const firstMainAction = computed(() =>
  props.commands.find(c => (c.params?.category || 'action') !== 'skill' && c.params?.command !== 'flee') || null
)
const otherMainActions = computed(() => {
  const first = firstMainAction.value
  return props.commands.filter(c => (c.params?.category || 'action') !== 'skill' && c.params?.command !== 'flee' && c !== first)
})
const fleeAction = computed(() =>
  props.commands.find(c => c.params?.command === 'flee') || null
)
const skillActions = computed(() =>
  props.commands.filter(c => c.params?.category === 'skill')
)

const phase = computed(() => props.combat?.phase || 'COMMAND')

const displayCombatants = computed(() => {
  const combatants = props.combat?.combatants || []
  if (Object.keys(displayHp.value).length === 0) return combatants
  return combatants.map(c => {
    const hp = displayHp.value[c.id]
    if (hp !== undefined) {
      return { ...c, hp: Math.max(0, hp), alive: hp > 0 }
    }
    return c
  })
})

const playerUnits = computed(() =>
  displayCombatants.value.filter(c => c.side === 'PLAYER')
)

const enemyUnits = computed(() =>
  displayCombatants.value.filter(c => c.side === 'ENEMY')
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
  showSkills.value = false
})

const selectingTarget = computed(() => step.value === 'target')

function hpPercent(unit) {
  return Math.max(0, unit.hp / unit.maxHp * 100)
}

function mpPercent(unit) {
  if (!unit.maxMp || unit.maxMp === 0) return 0
  return Math.max(0, unit.mp / unit.maxMp * 100)
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
      cells.push({ marker, row, col })
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
    markers.push({
      col, row,
      side: unit.side || (side === 'player' ? 'PLAYER' : 'ENEMY'),
      index: idx + 1,
      alive: unit.alive,
      id: unit.id,
      hp: unit.hp,
      maxHp: unit.maxHp,
      buffs: unit.buffs || [],
      unitRow: unitRow,
      slot: unit.slot ?? idx % 3
    })
  })
  return markers
}

function selectCommand(action) {
  if (!currentActor.value) return
  if (showSkills.value && action.params?.category !== 'skill') return
  if (step.value === 'target') return
  var style = action.style
  if (style === 'disabled') return
  var cmd = action.params?.command
  hideTooltip()  // 选完技能立即关闭 tooltip

  if (style === 'instant') {
    stopTimer()
    showSkills.value = false
    emit('command', { type: 'combat_command', params: { command: cmd, targetId: null } })
    return
  }
  // requires_target
  selectedCommand.value = cmd
  step.value = 'target'
}

function cancelSub() {
  showSkills.value = false
  step.value = 'command'
  selectedCommand.value = null
}

const targetPrompt = computed(() => {
  if (!selectedCommand.value) return '选择目标...'
  const cmd = props.commands.find(c => c.params?.command === selectedCommand.value)
  return cmd?.params?.prompt || '选择目标...'
})

const hoveredCell = ref(null)

function onCellHover(cell) {
  if (step.value !== 'target') return
  if (!currentAllowEmpty() && (!cell.marker || !cell.marker.alive)) {
    hoveredCell.value = null
    return
  }
  hoveredCell.value = cell
}

function currentAoeOffsets() {
  if (!selectedCommand.value) return null
  const cmd = props.commands.find(c => c.params?.command === selectedCommand.value)
  return cmd?.params?.aoeOffsets || null
}

function currentAllowEmpty() {
  if (!selectedCommand.value) return false
  const cmd = props.commands.find(c => c.params?.command === selectedCommand.value)
  return cmd?.params?.allowEmpty || false
}

function isValidTarget(cell) {
  if (!selectingTarget.value) return false
  if (!cell.marker || !cell.marker.alive) return false
  return true
}

function isClickable(cell) {
  if (!selectingTarget.value) return false
  if (currentAllowEmpty()) return true
  return !!(cell.marker && cell.marker.alive)
}

function isInAoeZone(cell) {
  if (step.value !== 'target' || !hoveredCell.value) return false
  const hovered = hoveredCell.value

  const offsets = currentAoeOffsets()
  if (!offsets) {
    return cell.row === hovered.row && cell.col === hovered.col
  }

  for (let i = 0; i < offsets.length; i++) {
    const o = offsets[i]
    const dr = o[0] !== undefined ? o[0] : (o.get ? o.get(0) : 0)
    const dc = o[1] !== undefined ? o[1] : (o.get ? o.get(1) : 0)
    const targetRow = hovered.row + dr
    const targetCol = hovered.col + dc
    if (targetRow < 0 || targetRow > 2 || targetCol < 0 || targetCol > 2) continue
    if (cell.row === targetRow && cell.col === targetCol) return true
  }
  return false
}

function onCellClick(cell) {
  if (step.value !== 'target') return
  if (!isClickable(cell)) return
  stopTimer()
  var targetId = cell.marker?.id || null
  emit('command', { type: 'combat_command', params: { command: selectedCommand.value, targetId: targetId, targetRow: cell.row, targetCol: cell.col } })
  step.value = 'command'
  selectedCommand.value = null
  showSkills.value = false
}

function cancelSelect() {
  step.value = 'command'
  selectedCommand.value = null
}

// Turn timer — counts down from turnTimer seconds, auto-attacks on timeout
const timeLeft = ref(30)
const showTimer = computed(() => phase.value === 'COMMAND' && !props.animating)
const timerDisplay = computed(() => '' + timeLeft.value)
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

.battle-content {
  display: grid;
  grid-template-columns: 15% 1fr 15%;
  flex: 1;
  min-height: 0;
}

.status-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 0.3rem;
  border-right: 2px solid var(--panel-border-color);
  overflow: hidden;
  height: 100%;
}

.enemy-col {
  border-right: none;
  border-left: 2px solid var(--panel-border-color);
}

.center-col {
  display: grid;
  grid-template-rows: auto 1fr 25%;
  min-height: 0;
  overflow: hidden;
}

.unit-status {
  display: flex;
  flex-direction: column;
  flex: 0 0 calc((100% - 8px) / 5);
  overflow: hidden;
  border: 2px solid var(--panel-border-color);
  padding: 2px 4px;
  background: rgba(255,255,255,0.03);
  box-sizing: border-box;
  transition: flex 0.15s ease, border-color 0.15s;
}
.unit-status.dead { opacity: 0.35; }

/* 压缩态：固定 1/10，扣除 gap（8×2px÷10）= (100%-16px)/10 */
.unit-status.compact {
  flex: 0 0 calc((100% - 16px) / 10);
}
/* 压缩态 hover：展开到 2×compact，填满同比高度 */
.unit-status.compact:hover {
  flex: 0 0 calc((100% - 16px) / 5);
  border-color: var(--link-color);
  position: relative;
  z-index: 1;
}

/* 压缩态内容 */
.compact-bar {
  height: 3px;
  width: 100%;
  flex-shrink: 0;
}
.compact-name {
  font-size: 9px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

/* Buff 状态行 */
.buff-status-row {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-top: 2px;
}
.buff-status-group {
  display: flex;
  gap: 2px;
  align-items: center;
  flex: 1;
}
.buff-status-group:last-child {
  justify-content: flex-end;
}
.bstat-divider {
  width: 1px;
  background: var(--color-border);
  align-self: stretch;
  flex-shrink: 0;
}
.bstat-icon {
  position: relative;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  cursor: default;
}
.bstat-tri {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
}
.bstat-tri.up   { clip-path: polygon(50% 0%, 0% 100%, 100% 100%); }
.bstat-tri.down { clip-path: polygon(0% 0%, 100% 0%, 50% 100%); }
.bstat-stack {
  position: absolute;
  left: 50%; top: 40%;
  transform: translate(-50%, -50%);
  font-size: 10px;
  font-weight: bold;
  color: rgba(255,255,255,0.9);
  line-height: 1;
  pointer-events: none;
}
.bstat-stack-down { top: 30%; }
.bstat-remain {
  position: absolute;
  bottom: 0; right: 0;
  font-size: 8px;
  color: rgba(255,255,255,0.6);
  line-height: 1;
  pointer-events: none;
}

.unit-status.dead {
  opacity: 0.4;
}

.unit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  line-height: 1.2;
}

.unit-header.active {
  text-shadow: 0 0 4px var(--link-color);
}

.player-name { color: var(--color-player); }
.enemy-name { color: var(--color-enemy); }

/* ── 能量条：标签(固定宽) + 条(flex:1) + 数值(固定宽) ── */
.stat-bar-row {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-top: 4px;
  gap: 4px;
}

.stat-label {
  font-size: 0.6em;
  width: 16px;
  text-align: right;
  flex-shrink: 0;
  font-weight: bold;
  line-height: 1;
}

.stat-bar-track {
  flex: 1;
  height: 5px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 2px;
  overflow: hidden;
  min-width: 0;
}

.stat-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

/* 固定宽度：不同数字长度不会影响条的宽度 */
.stat-bar-num {
  font-size: 0.6em;
  color: var(--color-text);
  opacity: 0.85;
  white-space: nowrap;
  flex-shrink: 0;
  width: 42px;
  text-align: right;
  line-height: 1;
}

.grid-area {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2%;
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  flex-shrink: 0;
}

.round-top {
  text-align: center;
  font-size: 0.75em;
  color: var(--color-highlight);
  font-weight: bold;
  opacity: 0.7;
  padding: 2px 0;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.mid-timer {
  font-size: 1.4em;
  font-weight: bold;
  color: var(--color-text);
  opacity: 0.8;
}

.terrain-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--panel-border-color);
  border-radius: 3px;
  background-color: var(--panel-bg);
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

.marker.player  { color: var(--color-player);  background: color-mix(in srgb, var(--color-player)  15%, transparent); border: 2px solid var(--color-player); }
.marker.enemy   { color: var(--color-enemy);   background: color-mix(in srgb, var(--color-enemy)   15%, transparent); border: 2px solid var(--color-enemy); }
.marker.neutral { color: var(--color-neutral); background: color-mix(in srgb, var(--color-neutral) 15%, transparent); border: 2px solid var(--color-neutral); }
.marker.ally    { color: var(--color-ally);    background: color-mix(in srgb, var(--color-ally)    15%, transparent); border: 2px solid var(--color-ally); }

.corner-hp {
  position: absolute;
  bottom: 2px;
  right: 3px;
  font-size: 0.45em;
  color: var(--color-text);
}

.buff-indicator {
  position: absolute;
  width: 10px; height: 10px;
}
.buff-indicator.corner-0 { top: 0; left: 0; clip-path: polygon(0 0, 100% 0, 0 100%); }
.buff-indicator.corner-1 { top: 0; right: 0; clip-path: polygon(0 0, 100% 0, 100% 100%); }
.buff-indicator.corner-2 { bottom: 0; left: 0; clip-path: polygon(0 0, 0 100%, 100% 100%); }
.buff-indicator.corner-3 { bottom: 0; right: 0; clip-path: polygon(100% 0, 0 100%, 100% 100%); }

.command-row {
  border-top: 2px solid var(--panel-border-color);
  display: grid;
  grid-template-columns: 1fr 2fr 3fr;
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
  border-right: 2px solid var(--panel-border-color);
}

.cmd-actions {
  border-right: 2px solid var(--panel-border-color);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.3rem;
  align-items: center;
  justify-items: stretch;
  padding: 0.3rem 0.6rem;
}

.cmd-actions.locked {
  opacity: 0.35;
  pointer-events: none;
}

.cmd-sub {
  border-left: 2px solid var(--panel-border-color);
  overflow-y: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.3rem;
  align-items: center;
  justify-items: stretch;
  padding: 0.3rem 0.6rem;
}

.actor-avatar {
  width: 2.2rem;
  height: 2.2rem;
  border: 2px solid var(--color-player);
  border-radius: 4px;
  background: color-mix(in srgb, var(--color-player) 10%, transparent);
}

.actor-name {
  color: var(--color-player);
}

.cmd-btn {
  font-family: inherit;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 0.3rem 0.6rem;
  background: color-mix(in srgb, var(--color-text) 4%, transparent);
  border: 2px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text);
  cursor: pointer;
  text-align: center;
  box-shadow: inset 0 2px 6px rgba(0,0,0,0.5), inset 0 -1px 0 rgba(255,255,255,0.04);
  transition: border-color 0.15s, background 0.15s, color 0.15s;
  white-space: nowrap;
  user-select: none;
}
.cmd-btn:hover {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 12%, transparent);
  color: var(--color-highlight);
}
.cmd-btn.active {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 18%, transparent);
  color: var(--color-highlight);
}
.cmd-btn.disabled {
  opacity: 0.35;
  pointer-events: none;
}
.cancel-btn {
  border-color: color-mix(in srgb, var(--color-enemy) 50%, transparent);
  color: var(--color-enemy);
}
.cancel-btn:hover {
  border-color: var(--color-enemy);
  background: color-mix(in srgb, var(--color-enemy) 12%, transparent);
  color: var(--color-enemy);
}
.target-hint {
  font-size: 0.82rem;
  color: var(--color-text);
  opacity: 0.5;
  cursor: default;
  padding: 0.3rem 0;
}

.terrain-cell.aoe-border {
  border-color: var(--color-enemy);
}

.terrain-cell.aoe-hit {
  background-color: color-mix(in srgb, var(--color-enemy) 25%, transparent) !important;
  border-color: var(--color-enemy);
}


.marker.shaking {
  animation: marker-shake 250ms ease-in-out;
}

@keyframes marker-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-3px); }
  40% { transform: translateX(3px); }
  60% { transform: translateX(-2px); }
  80% { transform: translateX(2px); }
}

.marker.lunge-right {
  animation: lunge-right 250ms ease-in-out;
}

.marker.lunge-left {
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
