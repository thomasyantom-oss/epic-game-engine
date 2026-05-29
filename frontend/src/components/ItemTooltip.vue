<template>
  <Teleport to="body">
    <div
      v-if="visible && content"
      class="item-tooltip"
      :style="{ left: position.x + 'px', top: position.y + 'px' }"
    >
      <div class="tt-title" :style="{ color: content.titleColor }">{{ content.title }}</div>
      <template v-for="(row, i) in content.rows" :key="i">
        <div v-if="row.label.startsWith('──')" class="tt-divider"></div>
        <div v-else class="tt-row">
          <span class="tt-label">{{ row.label }}</span>
          <span class="tt-value" :style="{ color: row.valueColor || 'inherit' }">{{ row.value }}</span>
        </div>
      </template>
      <div v-if="content.footer" class="tt-footer">{{ content.footer }}</div>
    </div>
  </Teleport>
</template>

<script setup>
import { useTooltip } from '../composables/useTooltip.js'
const { visible, position, content } = useTooltip()
</script>

<style scoped>
.item-tooltip {
  position: fixed;
  z-index: 9999;
  min-width: 160px;
  max-width: 240px;
  background: var(--panel-bg);
  border: 2px solid var(--panel-border-color);
  border-radius: 4px;
  padding: 0.5rem 0.65rem;
  pointer-events: none;
  font-family: var(--font-family, inherit);
  font-size: 0.85rem;
  color: var(--text-color);
  box-shadow: 0 4px 16px rgba(0,0,0,0.6);
}
.tt-title {
  font-weight: bold;
  font-size: 0.95rem;
  margin-bottom: 0.35rem;
}
.tt-divider {
  border-top: 1px solid var(--panel-border-color);
  margin: 0.3rem 0;
}
.tt-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  line-height: 1.7;
}
.tt-label { color: var(--text-color); opacity: 0.5; }
.tt-value { font-weight: bold; }
.tt-footer {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--text-color);
  opacity: 0.5;
}
</style>
