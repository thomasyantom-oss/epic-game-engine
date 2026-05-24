<template>
  <span class="action-link" :style="{ color: resolveColor(action.color) }" @click="$emit('action', action)">
    <span class="action-prefix">▸</span>{{ action.label }}
  </span>
</template>

<script setup>
defineProps({ action: Object })
defineEmits(['action'])

function resolveColor(color) {
    if (!color) return 'var(--color-text)'
    if (color.startsWith('#')) return color
    return `var(--color-${color})`
}
</script>

<style scoped>
.action-link {
  cursor: pointer;
  display: inline-block;
  line-height: 1.8;
  transition: opacity 0.15s;
}
.action-link:hover {
  opacity: 0.7;
}
.action-prefix {
  margin-right: 0.3em;
}
</style>
