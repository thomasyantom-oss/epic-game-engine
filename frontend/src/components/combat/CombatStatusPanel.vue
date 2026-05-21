<template>
  <div class="combat-status" v-if="actor">
    <div class="status-name">{{ actor.name }}</div>
    <div class="status-row">
      <span class="label">HP</span>
      <span class="value" :style="{ color: hpColor }">{{ actor.hp }} / {{ actor.maxHp }}</span>
    </div>
    <div class="status-row">
      <span class="label">攻击</span>
      <span class="value">{{ actor.attack }}</span>
    </div>
    <div class="status-row">
      <span class="label">防御</span>
      <span class="value">{{ actor.defense }}</span>
    </div>
    <div class="status-row">
      <span class="label">速度</span>
      <span class="value">{{ actor.speed }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
    actor: { type: Object, default: null }
})

const hpColor = computed(() => {
    if (!props.actor) return '#e0e0e0'
    const ratio = props.actor.hp / props.actor.maxHp
    if (ratio > 0.6) return '#4ecdc4'
    if (ratio > 0.3) return '#ffd93d'
    return '#e94560'
})
</script>

<style scoped>
.combat-status { font-size: 0.9em; }

.status-name {
    color: var(--link-color);
    font-weight: bold;
    margin-bottom: 0.6rem;
}

.status-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 0.3rem;
    padding: 0.2rem 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.label { color: #999; }
.value { color: var(--text-color); }
</style>
