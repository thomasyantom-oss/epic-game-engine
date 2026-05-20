<template>
  <span class="text-renderer">
    <span
      v-for="(segment, index) in segments"
      :key="index"
      :style="{ color: segment.color }"
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
</script>

<style scoped>
.text-renderer {
    line-height: 1.8;
    white-space: pre-wrap;
}
</style>
