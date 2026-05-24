<template>
  <div class="animation-layer">
    <template v-for="(anim, idx) in animations" :key="idx">
      <div v-if="anim.type === 'impact'"
           class="anim-impact"
           :style="impactStyle(anim)" />

      <div v-if="anim.type === 'damage_number'"
           class="anim-damage-number"
           :class="damageClass(anim)"
           :style="damageStyle(anim)">
        {{ formatValue(anim.value) }}
      </div>
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

function impactStyle(anim) {
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

function damageStyle(anim) {
  const pos = getCellPos(anim.target)
  return {
    left: (pos.x + pos.w / 2) + 'px',
    top: pos.y + 'px',
    animationDelay: anim.startDelay + 'ms'
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

.anim-impact {
  position: absolute;
  border-radius: 3px;
  animation: impact-flash ease-out forwards;
}

@keyframes impact-flash {
  0% { background: rgba(255, 255, 255, 0.5); }
  100% { background: transparent; }
}

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
  text-shadow:
    -1px -1px 0 #000, 1px -1px 0 #000,
    -1px 1px 0 #000, 1px 1px 0 #000,
    0 0 6px rgba(255, 82, 82, 0.5);
}

.anim-damage-number.heal {
  color: #66bb6a;
  text-shadow:
    -1px -1px 0 #000, 1px -1px 0 #000,
    -1px 1px 0 #000, 1px 1px 0 #000,
    0 0 6px rgba(102, 187, 106, 0.5);
}

.anim-damage-number.miss {
  color: #999;
  font-size: 0.9em;
  text-shadow:
    -1px -1px 0 #000, 1px -1px 0 #000,
    -1px 1px 0 #000, 1px 1px 0 #000;
}

@keyframes damage-pop {
  0% { transform: translateX(-50%) translateY(4px) scale(0.8); opacity: 0; }
  15% { transform: translateX(-50%) translateY(0) scale(1.1); opacity: 1; }
  30% { transform: translateX(-50%) translateY(0) scale(1); opacity: 1; }
  70% { transform: translateX(-50%) translateY(0); opacity: 1; }
  100% { transform: translateX(-50%) translateY(-8px); opacity: 0; }
}
</style>
