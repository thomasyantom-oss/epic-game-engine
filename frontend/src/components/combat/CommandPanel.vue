<template>
  <div class="command-panel">
    <div v-if="resolving" class="resolving">
      <span style="color: #999">结算中...</span>
    </div>
    <div v-else-if="currentActor" class="commands">
      <div class="actor-name">{{ currentActor.name }} 的行动：</div>
      <div v-if="!choosingTarget" class="command-list">
        <div class="command-item" @click="startAttack">攻击</div>
        <div class="command-item" @click="defend">防御</div>
      </div>
      <div v-else class="target-list">
        <div class="target-header">选择目标：</div>
        <div
          v-for="target in targets"
          :key="target.id"
          class="command-item target-item"
          @click="selectTarget(target.id)"
        >{{ target.name }} ({{ target.hp }}/{{ target.maxHp }})</div>
        <div class="command-item cancel" @click="choosingTarget = false">取消</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
    currentActor: { type: Object, default: null },
    targets: { type: Array, default: () => [] },
    resolving: { type: Boolean, default: false }
})

const emit = defineEmits(['command'])
const choosingTarget = ref(false)

function startAttack() {
    choosingTarget.value = true
}

function selectTarget(targetId) {
    emit('command', { actorId: props.currentActor.id, type: 'ATTACK', targetId })
    choosingTarget.value = false
}

function defend() {
    emit('command', { actorId: props.currentActor.id, type: 'DEFEND', targetId: null })
}
</script>

<style scoped>
.command-panel { height: 100%; }

.actor-name {
    color: var(--link-color);
    margin-bottom: 0.8rem;
    font-size: 0.9em;
}

.command-list, .target-list {
    display: flex;
    flex-direction: column;
    gap: 0.4rem;
}

.target-header {
    color: #999;
    font-size: 0.85em;
    margin-bottom: 0.3rem;
}

.command-item {
    padding: 0.4rem 0.8rem;
    border: 1px solid var(--panel-border-color);
    border-radius: 4px;
    cursor: pointer;
    transition: border-color 0.2s, background-color 0.2s;
}

.command-item:hover {
    border-color: var(--link-color);
    background-color: rgba(233, 69, 96, 0.1);
}

.command-item.cancel {
    color: #999;
    border-color: #555;
}

.resolving {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
}
</style>
