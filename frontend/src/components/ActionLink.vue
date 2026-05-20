<template>
  <span class="action-link" @click="$emit('execute')">
    {{ action.label }}
  </span>
</template>

<script setup>
defineProps({
    action: {
        type: Object,
        required: true
    }
})

defineEmits(['execute'])
</script>

<style scoped>
.action-link {
    display: inline-block;
    margin: 0.5rem 1rem 0.5rem 0;
    padding: 0.3rem 0.8rem;
    border: var(--panel-border-width) var(--panel-border-style) var(--link-color);
    border-radius: 4px;
    transition: background-color 0.2s;
}

.action-link:hover {
    background-color: rgba(233, 69, 96, 0.1);
}
</style>
