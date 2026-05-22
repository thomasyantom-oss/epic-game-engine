<template>
  <div class="quick-actions">
    <div v-if="actions.length > 0" class="action-list">
      <div
        v-for="action in actions"
        :key="action.id"
        class="action-btn"
        @click="$emit('action', action)"
      >{{ action.label }}</div>
    </div>
    <div v-else class="empty-hint">
      <span style="color: #666">快捷行动</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
    actions: { type: Array, default: () => [] }
})

defineEmits(['action'])
</script>

<style scoped>
.quick-actions {
    display: flex;
    flex-direction: column;
    height: 100%;
    gap: 0.4rem;
    padding: 0.3rem;
}

.action-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.action-btn {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.9em;
    text-align: center;
    transition: border-color 0.2s, background-color 0.2s;
}

.action-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.empty-hint {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
}
</style>
