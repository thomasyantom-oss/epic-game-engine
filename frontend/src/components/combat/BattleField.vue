<template>
  <div class="battlefield">
    <div class="side player-side">
      <div class="side-label">【我方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['BACK', 'MID', 'FRONT']" :key="'p-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('PLAYER', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, active: unit.id === currentActorId }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
    <div class="versus">⚔</div>
    <div class="side enemy-side">
      <div class="side-label">【敌方】</div>
      <div class="formation">
        <div class="row" v-for="row in ['FRONT', 'MID', 'BACK']" :key="'e-'+row">
          <span class="row-label">{{ rowLabel(row) }}</span>
          <span
            v-for="unit in getUnits('ENEMY', row)"
            :key="unit.id"
            class="unit"
            :class="{ dead: !unit.alive, targeted: targetIds.includes(unit.id) }"
            :style="{ color: unitColor(unit) }"
          >{{ unit.name.charAt(0) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
    combatants: { type: Array, required: true },
    currentActorId: { type: String, default: '' },
    targetIds: { type: Array, default: () => [] }
})

function getUnits(side, row) {
    return props.combatants.filter(c => c.side === side && c.row === row)
}

function rowLabel(row) {
    return { FRONT: '前', MID: '中', BACK: '后' }[row]
}

function unitColor(unit) {
    if (!unit.alive) return '#555'
    const ratio = unit.hp / unit.maxHp
    if (ratio > 0.6) return '#4ecdc4'
    if (ratio > 0.3) return '#ffd93d'
    return '#e94560'
}
</script>

<style scoped>
.battlefield {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 2rem;
}

.side { flex: 1; }

.side-label {
    text-align: center;
    margin-bottom: 0.8rem;
    font-size: 0.9em;
    color: #999;
}

.formation {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.row-label {
    color: #666;
    font-size: 0.8em;
    width: 1.5em;
}

.unit {
    display: inline-block;
    width: 2em;
    height: 2em;
    line-height: 2em;
    text-align: center;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    font-weight: bold;
}

.unit.dead { opacity: 0.3; text-decoration: line-through; }
.unit.active { border-color: var(--link-color); box-shadow: 0 0 6px rgba(233, 69, 96, 0.4); }
.unit.targeted { border-color: #ffd93d; }
.versus { font-size: 1.5rem; color: #666; }
</style>
