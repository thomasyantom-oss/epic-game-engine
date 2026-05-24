<template>
  <div class="animation-layer">
    <template v-for="anim in animations" :key="anim._id">
      <!-- Pulse: expanding ring from actor cell -->
      <div v-if="anim.type === 'pulse'"
           class="anim-pulse"
           :style="pulseStyle(anim)" />

      <!-- Flash sequence: cells along path flash white sequentially -->
      <div v-if="anim.type === 'flash_sequence'"
           class="anim-flash-seq"
           :style="flashSeqStyle(anim)" />

      <!-- Projectile: flies from source center to target center -->
      <div v-if="anim.type === 'projectile'"
           class="anim-projectile"
           :class="'shape-' + (anim.shape || 'circle')"
           :style="projectileStyle(anim)" />

      <!-- Beam: line extends from source to target -->
      <div v-if="anim.type === 'beam'"
           class="anim-beam"
           :style="beamStyle(anim)" />

      <!-- Slash: crescent arc on target cell -->
      <div v-if="anim.type === 'slash'"
           class="anim-slash"
           :style="slashStyle(anim)">
        <svg viewBox="0 0 40 40" width="100%" height="100%">
          <path class="slash-path" d="M 0,0 Q 38,20 0,40" fill="none" :stroke="anim.color || '#fff'" stroke-width="3" stroke-linecap="round"/>
        </svg>
      </div>

      <!-- Impact: flash on target cell -->
      <div v-if="anim.type === 'impact'"
           class="anim-impact"
           :style="impactStyle(anim)" />

      <!-- Damage number -->
      <div v-if="anim.type === 'damage_number'"
           class="anim-damage-number"
           :class="damageClass(anim)"
           :style="damageStyle(anim)">
        {{ formatValue(anim.value) }}
      </div>

      <!-- Buff up: triangles rising (2 for single, 4 for field) -->
      <div v-if="anim.type === 'buff_up'"
           class="anim-buff-up"
           :class="{ 'field-scope': anim.target === 'field' || anim.scope === 'field' }"
           :style="buffStyle(anim)">
        <span class="buff-tri" :style="{ color: anim.color || '#66bb6a' }"></span>
        <span class="buff-tri t2" :style="{ color: anim.color || '#66bb6a' }"></span>
        <template v-if="anim.target === 'field' || anim.scope === 'field'">
          <span class="buff-tri t3" :style="{ color: anim.color || '#66bb6a' }"></span>
          <span class="buff-tri t4" :style="{ color: anim.color || '#66bb6a' }"></span>
        </template>
      </div>

      <!-- Debuff down: triangles sinking (2 for single, 4 for field) -->
      <div v-if="anim.type === 'debuff_down'"
           class="anim-debuff-down"
           :class="{ 'field-scope': anim.target === 'field' || anim.scope === 'field' }"
           :style="buffStyle(anim)">
        <span class="debuff-tri" :style="{ color: anim.color || '#e57373' }"></span>
        <span class="debuff-tri t2" :style="{ color: anim.color || '#e57373' }"></span>
        <template v-if="anim.target === 'field' || anim.scope === 'field'">
          <span class="debuff-tri t3" :style="{ color: anim.color || '#e57373' }"></span>
          <span class="debuff-tri t4" :style="{ color: anim.color || '#e57373' }"></span>
        </template>
      </div>

      <!-- Mark dead: X overlay -->
      <div v-if="anim.type === 'mark_dead'"
           class="anim-mark-dead"
           :style="cellOverlayStyle(anim)">
        X
      </div>

      <!-- Indicator add: corner triangle flash -->
      <div v-if="anim.type === 'indicator_add'"
           class="anim-indicator-add"
           :style="indicatorStyle(anim)" />
    </template>
  </div>
</template>

<script setup>
const props = defineProps({
  animations: { type: Array, default: () => [] },
  cellPositions: { type: Object, default: () => ({}) }
})

function getCellPos(targetId) {
  return props.cellPositions[targetId] || { x: 0, y: 0, w: 32, h: 32 }
}

function pulseStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: (pos.x + pos.w / 2) + 'px',
    top: (pos.y + pos.h / 2) + 'px',
    '--pulse-color': anim.color || '#4fc3f7',
    '--pulse-size': pos.w + 'px',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function flashSeqStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function projectileStyle(anim) {
  const from = getCellPos(anim.from)
  const to = getCellPos(anim.to)
  const startX = from.x + from.w / 2
  const startY = from.y + from.h / 2
  const endX = to.x + to.w / 2
  const endY = to.y + to.h / 2
  const color = anim.color || '#fff'

  return {
    '--start-x': startX + 'px',
    '--start-y': startY + 'px',
    '--end-x': endX + 'px',
    '--end-y': endY + 'px',
    '--proj-color': color,
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function beamStyle(anim) {
  const from = getCellPos(anim.from)
  const to = getCellPos(anim.to)
  const startX = from.x + from.w / 2
  const startY = from.y + from.h / 2
  const endX = to.x + to.w / 2
  const endY = to.y + to.h / 2
  const dx = endX - startX
  const dy = endY - startY
  const length = Math.sqrt(dx * dx + dy * dy)
  const angle = Math.atan2(dy, dx) * 180 / Math.PI
  const color = anim.color || '#4fc3f7'

  return {
    left: startX + 'px',
    top: startY + 'px',
    '--beam-length': length + 'px',
    '--beam-color': color,
    transform: `rotate(${angle}deg)`,
    transformOrigin: '0 50%',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function slashStyle(anim) {
  if (anim.targets && anim.targets.length > 0) {
    let minX = Infinity, minY = Infinity, maxX = 0, maxY = 0
    for (const tid of anim.targets) {
      const p = getCellPos(tid)
      minX = Math.min(minX, p.x)
      minY = Math.min(minY, p.y)
      maxX = Math.max(maxX, p.x + p.w)
      maxY = Math.max(maxY, p.y + p.h)
    }
    return {
      left: minX + 'px',
      top: minY + 'px',
      width: (maxX - minX) + 'px',
      height: (maxY - minY) + 'px',
      animationDelay: anim.startDelay + 'ms',
      animationDuration: anim.duration + 'ms'
    }
  }
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function impactStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    '--impact-color': anim.color || '#fff',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function damageStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: (pos.x + pos.w / 2) + 'px',
    top: pos.y + 'px',
    animationDelay: anim.startDelay + 'ms'
  }
}

function buffStyle(anim) {
  if (anim.target === 'field' || anim.scope === 'field') {
    return {
      left: '0',
      top: '0',
      width: '100%',
      height: '100%',
      '--cell-w': '80px',
      animationDelay: anim.startDelay + 'ms'
    }
  }
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    '--cell-w': pos.w + 'px',
    animationDelay: anim.startDelay + 'ms'
  }
}

function cellOverlayStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: pos.x + 'px',
    top: pos.y + 'px',
    width: pos.w + 'px',
    height: pos.h + 'px',
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function indicatorStyle(anim) {
  const pos = getCellPos(anim.target)
  const corner = anim.corner || 0
  const color = anim.color || '#66bb6a'
  const offsets = [
    { top: pos.y + 'px', left: pos.x + 'px' },
    { top: pos.y + 'px', left: (pos.x + pos.w - 10) + 'px' },
    { top: (pos.y + pos.h - 10) + 'px', left: pos.x + 'px' },
    { top: (pos.y + pos.h - 10) + 'px', left: (pos.x + pos.w - 10) + 'px' }
  ]
  const o = offsets[corner] || offsets[0]
  return {
    ...o,
    '--ind-color': color,
    animationDelay: anim.startDelay + 'ms',
    animationDuration: anim.duration + 'ms'
  }
}

function damageClass(anim) {
  if (anim.color === 'heal') return 'heal'
  if (anim.color === 'miss') return 'miss'
  return 'damage'
}

function formatValue(value) {
  if (value === undefined || value === null) return ''
  return value > 0 ? '+' + value : '' + value
}
</script>

<style scoped>
.animation-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 10;
}

/* ========== Pulse ========== */
.anim-pulse {
  position: absolute;
  width: var(--pulse-size);
  height: var(--pulse-size);
  border: 2px solid var(--pulse-color);
  border-radius: 3px;
  transform: translate(-50%, -50%);
  animation: pulse-expand ease-out forwards;
  opacity: 0;
}

@keyframes pulse-expand {
  0% { transform: translate(-50%, -50%) scale(0.8); opacity: 0.8; }
  100% { transform: translate(-50%, -50%) scale(1.8); opacity: 0; }
}

/* ========== Flash Sequence ========== */
.anim-flash-seq {
  position: absolute;
  border-radius: 3px;
  animation: flash-seq ease-in-out forwards;
}

@keyframes flash-seq {
  0%, 100% { background: transparent; }
  30%, 60% { background: rgba(255, 255, 255, 0.3); }
}

/* ========== Projectile ========== */
.anim-projectile {
  position: absolute;
  left: var(--start-x);
  top: var(--start-y);
  transform: translate(-50%, -50%);
  animation: projectile-fly linear forwards;
  opacity: 0;
}

.anim-projectile.shape-circle {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--proj-color);
  box-shadow: 0 0 6px var(--proj-color), 0 0 12px var(--proj-color);
}

.anim-projectile.shape-diamond {
  width: 8px; height: 8px;
  background: var(--proj-color);
  box-shadow: 0 0 6px var(--proj-color), 0 0 12px var(--proj-color);
  transform: translate(-50%, -50%) rotate(45deg);
}

.anim-projectile.shape-crescent {
  width: 12px; height: 12px;
  border: 3px solid var(--proj-color);
  border-color: var(--proj-color) transparent transparent transparent;
  border-radius: 50%;
  box-shadow: 0 0 4px var(--proj-color);
  background: transparent;
}

@keyframes projectile-fly {
  0% { left: var(--start-x); top: var(--start-y); opacity: 0; }
  10% { opacity: 1; }
  85% { left: var(--end-x); top: var(--end-y); opacity: 1; }
  100% { left: var(--end-x); top: var(--end-y); opacity: 0; }
}

/* ========== Beam ========== */
.anim-beam {
  position: absolute;
  height: 3px;
  width: 0;
  background: linear-gradient(to right, var(--beam-color), #fff);
  box-shadow: 0 0 6px var(--beam-color);
  border-radius: 2px;
  animation: beam-extend ease-out forwards;
}

@keyframes beam-extend {
  0% { width: 0; opacity: 1; }
  40% { width: var(--beam-length); opacity: 1; }
  70% { width: var(--beam-length); opacity: 0.5; }
  100% { width: var(--beam-length); opacity: 0; }
}

/* ========== Slash ========== */
.anim-slash {
  position: absolute;
  overflow: hidden;
  animation: slash-fade ease-out forwards;
}

.anim-slash .slash-path {
  stroke-dasharray: 100;
  stroke-dashoffset: 100;
  animation: slash-draw 400ms ease-out forwards;
  filter: drop-shadow(0 0 3px currentColor);
}

@keyframes slash-draw {
  0% { stroke-dashoffset: 100; }
  60% { stroke-dashoffset: 0; }
  100% { stroke-dashoffset: 0; }
}

@keyframes slash-fade {
  0%, 70% { opacity: 1; }
  100% { opacity: 0; }
}

/* ========== Impact ========== */
.anim-impact {
  position: absolute;
  border-radius: 3px;
  animation: impact-flash ease-out forwards;
}

@keyframes impact-flash {
  0% { background: rgba(255, 255, 255, 0.5); }
  100% { background: transparent; }
}

/* ========== Damage Number ========== */
.anim-damage-number {
  position: absolute;
  transform: translateX(-50%);
  font-size: 1.1em;
  font-weight: 900;
  white-space: nowrap;
  animation: damage-pop 500ms ease-out forwards;
}

.anim-damage-number.damage {
  color: #ff5252;
  text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 6px rgba(255, 82, 82, 0.5);
}

.anim-damage-number.heal {
  color: #66bb6a;
  text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 6px rgba(102, 187, 106, 0.5);
}

.anim-damage-number.miss {
  color: #999;
  font-size: 0.9em;
  text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000;
}

@keyframes damage-pop {
  0% { transform: translateX(-50%) translateY(4px) scale(0.8); opacity: 0; }
  15% { transform: translateX(-50%) translateY(0) scale(1.1); opacity: 1; }
  30% { transform: translateX(-50%) translateY(0) scale(1); opacity: 1; }
  70% { transform: translateX(-50%) translateY(0); opacity: 1; }
  100% { transform: translateX(-50%) translateY(-8px); opacity: 0; }
}

/* ========== Buff Up ========== */
.anim-buff-up {
  position: absolute;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}

.buff-tri {
  display: block;
  width: 0; height: 0;
  border-left: calc(var(--cell-w, 32px) / 2) solid transparent;
  border-right: calc(var(--cell-w, 32px) / 2) solid transparent;
  border-bottom: calc(var(--cell-w, 32px) * 0.4) solid currentColor;
  filter: drop-shadow(0 0 4px currentColor);
  animation: buff-rise 500ms ease-out forwards;
  opacity: 0;
}

.buff-tri.t2 {
  margin-top: -6px;
  animation-delay: 80ms;
}

.anim-buff-up.field-scope {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-around;
  align-items: flex-end;
}

.field-scope .buff-tri {
  animation-duration: 700ms;
}
.field-scope .buff-tri.t3 { animation-delay: 150ms; }
.field-scope .buff-tri.t4 { animation-delay: 250ms; }

@keyframes buff-rise {
  0% { transform: translateY(4px); opacity: 0; }
  15% { opacity: 1; }
  100% { transform: translateY(calc(var(--cell-w, 32px) * -0.8)); opacity: 0; }
}

/* ========== Debuff Down ========== */
.anim-debuff-down {
  position: absolute;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
}

.debuff-tri {
  display: block;
  width: 0; height: 0;
  border-left: calc(var(--cell-w, 32px) / 2) solid transparent;
  border-right: calc(var(--cell-w, 32px) / 2) solid transparent;
  border-top: calc(var(--cell-w, 32px) * 0.4) solid currentColor;
  filter: drop-shadow(0 0 4px currentColor);
  animation: debuff-sink 500ms ease-out forwards;
  opacity: 0;
}

.debuff-tri.t2 {
  margin-top: -6px;
  animation-delay: 80ms;
}

.anim-debuff-down.field-scope {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-around;
  align-items: flex-start;
}

.field-scope .debuff-tri {
  animation-duration: 700ms;
}
.field-scope .debuff-tri.t3 { animation-delay: 150ms; }
.field-scope .debuff-tri.t4 { animation-delay: 250ms; }

@keyframes debuff-sink {
  0% { transform: translateY(-4px); opacity: 0; }
  15% { opacity: 1; }
  100% { transform: translateY(calc(var(--cell-w, 32px) * 0.8)); opacity: 0; }
}

/* ========== Mark Dead ========== */
.anim-mark-dead {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5em;
  font-weight: bold;
  color: #e57373;
  animation: mark-dead-appear ease-out forwards;
  opacity: 0;
}

@keyframes mark-dead-appear {
  0% { transform: scale(0.5); opacity: 0; }
  30% { transform: scale(1.2); opacity: 1; }
  60% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(1); opacity: 0.5; }
}

/* ========== Indicator Add ========== */
.anim-indicator-add {
  position: absolute;
  width: 10px; height: 10px;
  background: var(--ind-color);
  clip-path: polygon(0 0, 100% 0, 0 100%);
  animation: indicator-flash ease-out forwards;
  opacity: 0;
}

@keyframes indicator-flash {
  0% { transform: scale(0.5); opacity: 0; }
  30% { transform: scale(1.5); opacity: 1; }
  60% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1); opacity: 0.7; }
}
</style>
