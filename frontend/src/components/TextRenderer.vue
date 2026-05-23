<template>
  <span class="text-renderer">
    <span
      v-for="(segment, index) in segments"
      :key="index"
      :style="{ color: resolveColor(segment.color) }"
      v-text="segment.text"
    ></span>
  </span>
</template>

<script setup>
defineProps({
    segments: {
        type: Array,
        required: true
    }
})

function resolveColor(color) {
    if (!color) return undefined
    if (color.startsWith('#')) return color
    return `var(--color-${color})`
}
</script>

<style scoped>
.text-renderer {
    line-height: 1.8;
    white-space: pre-wrap;
}
</style>
