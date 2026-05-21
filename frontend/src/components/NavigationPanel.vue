<template>
  <div class="navigation-panel">
    <div class="direction-pad" v-if="hasDirections">
      <div class="dir-row">
        <div class="dir-spacer"></div>
        <div class="dir-btn" @click="emitDirection('UP')">↑ 北</div>
        <div class="dir-spacer"></div>
      </div>
      <div class="dir-row">
        <div class="dir-btn" @click="emitDirection('LEFT')">← 西</div>
        <div class="dir-btn dir-center">·</div>
        <div class="dir-btn" @click="emitDirection('RIGHT')">→ 东</div>
      </div>
      <div class="dir-row">
        <div class="dir-spacer"></div>
        <div class="dir-btn" @click="emitDirection('DOWN')">↓ 南</div>
        <div class="dir-spacer"></div>
      </div>
    </div>
    <div class="action-list" v-if="poiActions.length > 0">
      <div
        v-for="action in poiActions"
        :key="action.id"
        class="nav-btn"
        @click="$emit('action', action)"
      >{{ action.label }}</div>
    </div>
    <div v-if="!hasDirections && poiActions.length === 0" class="no-actions">
      <span style="color: #666">无可用操作</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
    actions: { type: Array, default: () => [] }
})

const emit = defineEmits(['action'])

const hasDirections = computed(() => {
    return props.actions.some(a => a.type === 'mapMove')
})

const poiActions = computed(() => {
    return props.actions.filter(a => a.type !== 'mapMove')
})

function emitDirection(dir) {
    const action = props.actions.find(a => a.type === 'mapMove' && a.params?.direction === dir)
    if (action) emit('action', action)
}
</script>

<style scoped>
.navigation-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    gap: 0.5rem;
}

.direction-pad {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
}

.dir-row {
    display: flex;
    gap: 2px;
}

.dir-btn {
    width: 3.5rem;
    height: 2rem;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.8em;
    transition: border-color 0.2s, background-color 0.2s;
}

.dir-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.dir-center {
    cursor: default;
    border-color: transparent;
}

.dir-center:hover {
    border-color: transparent;
    background-color: transparent;
}

.dir-spacer {
    width: 3.5rem;
    height: 2rem;
}

.action-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.nav-btn {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.9em;
    text-align: center;
    transition: border-color 0.2s, background-color 0.2s;
}

.nav-btn:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.no-actions {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
}
</style>
