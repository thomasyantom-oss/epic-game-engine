<template>
  <div class="game-log" ref="logContainer">
    <div v-for="(entry, index) in entries" :key="index" class="log-entry">
      <span class="log-time">{{ entry.time }}</span>
      <span :style="{ color: entry.color || 'var(--text-color)' }">{{ entry.text }}</span>
    </div>
    <div v-if="entries.length === 0" class="log-empty">暂无事件记录</div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

const props = defineProps({
  entries: {
    type: Array,
    default: () => []
  }
})

const logContainer = ref(null)

watch(() => props.entries.length, async () => {
  await nextTick()
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
})
</script>

<style scoped>
.game-log {
  height: 100%;
  overflow-y: auto;
  font-size: 0.85em;
  line-height: 1.6;
}

.log-entry {
  margin-bottom: 0.3rem;
}

.log-time {
  color: var(--text-color);
  margin-right: 0.5rem;
  font-size: 0.8em;
}

.log-empty {
  color: var(--text-color);
  font-style: italic;
}
</style>
