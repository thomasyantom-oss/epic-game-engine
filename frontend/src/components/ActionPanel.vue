<template>
  <div class="action-panel">
    <ActionLink v-for="action in actions" :key="action.type + JSON.stringify(action.params)"
                :action="action" @action="$emit('action', $event)" />
    <div v-if="!actions || actions.length === 0" class="no-actions">
      无可用操作
    </div>
  </div>
</template>

<script setup>
import ActionLink from './ActionLink.vue'

defineProps({ actions: Array })
defineEmits(['action'])
</script>

<style scoped>
.action-panel { display: flex; flex-direction: column; gap: 0.1rem; }
.no-actions { color: var(--text-color); opacity: 0.5; }
</style>
