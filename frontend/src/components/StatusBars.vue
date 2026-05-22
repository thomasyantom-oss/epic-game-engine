<template>
  <div class="status-bars">
    <div v-for="bar in visibleBars" :key="bar.id" class="bar-item">
      <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
      <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
    </div>
    <div v-if="hiddenCount > 0" class="bar-overflow" @click="expanded = !expanded">
      {{ expanded ? '收起' : `+${hiddenCount} 更多` }}
    </div>
    <div v-if="expanded">
      <div v-for="bar in hiddenBars" :key="bar.id" class="bar-item">
        <span class="bar-label" :style="{ color: bar.color }">{{ bar.label }}</span>
        <span class="bar-value">{{ bar.current }}/{{ bar.max }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({ bars: Array })
const expanded = ref(false)
const MAX_VISIBLE = 3

const sorted = computed(() =>
    [...(props.bars || [])].sort((a, b) => a.priority - b.priority)
)
const visibleBars = computed(() => sorted.value.slice(0, MAX_VISIBLE))
const hiddenBars = computed(() => sorted.value.slice(MAX_VISIBLE))
const hiddenCount = computed(() => hiddenBars.value.length)
</script>

<style scoped>
.bar-item { display: flex; justify-content: space-between; margin: 0.2rem 0; }
.bar-label { font-weight: bold; }
.bar-value { color: var(--text-color); }
.bar-overflow { cursor: pointer; color: var(--link-color); font-size: 0.9em; }
</style>
