<template>
  <div class="tab-panel">
    <div class="tab-bar">
      <span
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-item"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >{{ tab.label }}</span>
    </div>
    <div class="tab-content">
      <template v-if="keepAlive">
        <div v-for="tab in tabs" :key="tab.id" v-show="activeTab === tab.id" class="tab-pane">
          <slot :name="tab.id"></slot>
        </div>
      </template>
      <template v-else>
        <slot :name="activeTab"></slot>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  defaultTab: {
    type: String,
    default: ''
  },
  keepAlive: {
    type: Boolean,
    default: false
  }
})

const activeTab = ref(props.defaultTab || props.tabs[0]?.id || '')

watch(() => props.defaultTab, (newTab) => {
  if (newTab) activeTab.value = newTab
})
</script>

<style scoped>
.tab-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background-color: var(--panel-bg);
  border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
  border-radius: 4px;
  overflow: hidden;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--panel-border-color);
  background-color: rgba(0, 0, 0, 0.2);
  flex-shrink: 0;
}

.tab-item {
  padding: 0.4rem 0.8rem;
  cursor: pointer;
  font-size: 0.85em;
  color: var(--text-color);
  opacity: 0.6;
  transition: opacity 0.2s, border-color 0.2s;
  border-bottom: 2px solid transparent;
}

.tab-item:hover {
  opacity: 0.9;
}

.tab-item.active {
  opacity: 1;
  border-bottom-color: var(--link-color);
  color: var(--link-color);
}

.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
  min-height: 0;
}
</style>
