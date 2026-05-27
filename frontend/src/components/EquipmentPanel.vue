<template>
  <div class="equipment-panel">
    <div v-for="slot in slots" :key="slot.id" class="slot-row">
      <span class="slot-label">{{ slot.label }}</span>
      <span class="slot-item" :class="slot.itemId ? 'equipped' : 'empty'">
        {{ slot.itemId ? slot.itemName : '空' }}
      </span>
      <button v-if="slot.itemId" class="unequip-btn" @click="$emit('unequip', slot.id)">卸</button>
    </div>
    <div v-if="!slots || slots.length === 0" class="no-equipment">暂无装备栏</div>
  </div>
</template>

<script setup>
defineProps({ slots: Array })
defineEmits(['unequip'])
</script>

<style scoped>
.equipment-panel { padding: 0.2rem 0; }
.slot-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0.3rem 0;
  min-height: 1.6rem;
}
.slot-label {
  width: 2.5rem;
  flex-shrink: 0;
  font-size: 0.85em;
  opacity: 0.7;
}
.slot-item {
  flex: 1;
  font-size: 0.9em;
  padding: 0.1rem 0.3rem;
  border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
  border-radius: 3px;
  min-height: 1.4rem;
}
.slot-item.empty { opacity: 0.4; font-style: italic; }
.slot-item.equipped { color: var(--link-color); }
.unequip-btn {
  background: none;
  border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
  color: var(--text-color);
  cursor: pointer;
  font-size: 0.75em;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  opacity: 0.6;
  flex-shrink: 0;
}
.unequip-btn:hover { opacity: 1; }
.no-equipment { opacity: 0.5; font-size: 0.85em; }
</style>
