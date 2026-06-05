<template>
  <div class="status-bars">
    <div v-for="bar in sorted" :key="bar.id" class="bar-item">
      <template v-if="bar.id === 'name'">
        <span class="char-name" :style="{ color: bar.color }">{{ bar.label }}</span>
        <span class="char-level">Lv.{{ bar.current }}</span>
        <span v-if="classBar" class="char-class" :style="{ color: classBar.color }">{{ classBar.label }}</span>
      </template>
      <template v-else-if="isBar(bar)">
        <div class="energy">
          <div class="energy-head">
            <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
            <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
          </div>
          <div class="energy-track">
            <div class="energy-fill" :style="{ width: pct(bar) + '%', background: bar.color }"></div>
          </div>
        </div>
      </template>
      <template v-else-if="bar.current === bar.max">
        <span class="stat-label">{{ bar.label }}</span>
        <span class="stat-value" :style="{ color: bar.color }">{{ bar.current }}</span>
      </template>
      <template v-else>
        <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
        <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ bars: Array })

const BAR_IDS = ['hp', 'mp', 'xp']

const sorted = computed(() =>
    [...(props.bars || [])]
      .filter(bar => bar.id !== 'class')
      .sort((a, b) => a.priority - b.priority)
)

const classBar = computed(() =>
  (props.bars || []).find(bar => bar.id === 'class') || null
)

function isBar(bar) {
  return BAR_IDS.includes(bar.id)
}

function pct(bar) {
  const max = Number(bar.max) || 0
  if (max <= 0) return 0
  return Math.max(0, Math.min(100, (Number(bar.current) / max) * 100))
}
</script>

<style scoped>
.bar-item { display: flex; align-items: baseline; gap: 0.4rem; justify-content: space-between; margin: 0.2rem 0; }
.bar-label { font-weight: bold; }
.bar-value { color: var(--text-color); }
.char-name { font-weight: bold; font-size: 1.1em; }
.char-level { color: var(--text-color); opacity: 0.8; }
.char-class { font-size: 0.9em; margin-left: auto; }
.stat-label { color: var(--text-color); opacity: 0.8; }
.stat-value { font-weight: bold; }

.energy { width: 100%; }
.energy-head { display: flex; justify-content: space-between; align-items: baseline; }
.energy-track {
  height: 0.7rem;
  margin-top: 0.18rem;
  border: 2px solid var(--color-border, #4a5568);
  border-radius: 3px;
  overflow: hidden;
  background: color-mix(in srgb, var(--text-color, #c8d0dc) 12%, transparent);
}
.energy-fill { height: 100%; transition: width 0.2s ease; }
</style>
