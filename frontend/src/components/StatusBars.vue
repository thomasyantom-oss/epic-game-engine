<template>
  <div class="status-bars">
    <div v-for="bar in sorted" :key="bar.id" class="bar-item">
      <template v-if="bar.id === 'name'">
        <span class="char-name" :style="{ color: bar.color }">{{ bar.label }}</span>
        <span class="char-level">Lv.{{ bar.current }}</span>
        <span v-if="classBar" class="char-class" :style="{ color: classBar.color }">{{ classBar.label }}</span>
      </template>
      <template v-else-if="bar.current === bar.max && bar.id !== 'hp' && bar.id !== 'mp'">
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

const sorted = computed(() =>
    [...(props.bars || [])]
      .filter(bar => bar.id !== 'class')
      .sort((a, b) => a.priority - b.priority)
)

const classBar = computed(() =>
  (props.bars || []).find(bar => bar.id === 'class') || null
)
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
</style>
