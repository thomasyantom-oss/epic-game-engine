<template>
  <div class="action-panel">
    <div v-for="action in actions" :key="action.type + JSON.stringify(action.params)"
         class="action-item" @click="$emit('action', action)">
      {{ action.label }}
    </div>
    <div v-if="!actions || actions.length === 0" class="no-actions">
      无可用操作
    </div>
  </div>
</template>

<script setup>
defineProps({ actions: Array })
defineEmits(['action'])
</script>

<style scoped>
.action-item { cursor: pointer; color: var(--link-color); margin: 0.3rem 0; }
.action-item:hover { text-decoration: underline; }
.no-actions { color: var(--text-color); opacity: 0.5; }
</style>
