<template>
  <div class="command-bar">
    <div v-if="currentActor" class="command-flow">
      <div class="actor-label">{{ currentActor.name }} 的回合</div>
      <div v-if="step === 'command'" class="options">
        <div class="cmd-btn" @click="selectCommand('ATTACK')">攻击</div>
        <div class="cmd-btn" @click="selectCommand('DEFEND')">防御</div>
        <div class="cmd-btn" @click="selectCommand('SKILL')">技能</div>
        <div class="cmd-btn" @click="selectCommand('ITEM')">道具</div>
        <div class="cmd-btn" @click="selectCommand('FLEE')">逃跑</div>
      </div>
      <div v-else-if="step === 'target'" class="options">
        <div class="target-header">选择目标：</div>
        <div
          v-for="(target, idx) in targets"
          :key="target.id"
          class="cmd-btn target-btn"
          @click="selectTarget(target.id)"
        >
          <span class="target-idx">{{ idx + 1 }}</span> {{ target.name }}
          <span class="target-hp">({{ target.hp }}/{{ target.maxHp }})</span>
        </div>
      </div>
      <div v-if="step === 'target'" class="cmd-btn cancel-btn" @click="cancel">取消</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  currentActor: { type: Object, default: null },
  targets: { type: Array, default: () => [] }
})

const emit = defineEmits(['command', 'cancel'])
const step = ref('command')
const selectedCommand = ref(null)

watch(() => props.currentActor, () => {
  step.value = 'command'
  selectedCommand.value = null
})

function selectCommand(cmd) {
  if (cmd === 'DEFEND' || cmd === 'FLEE') {
    emit('command', { actorId: props.currentActor.id, type: cmd, targetId: null })
    return
  }
  if (cmd === 'SKILL' || cmd === 'ITEM') {
    selectedCommand.value = 'ATTACK'
    step.value = 'target'
    return
  }
  selectedCommand.value = cmd
  step.value = 'target'
}

function selectTarget(targetId) {
  emit('command', { actorId: props.currentActor.id, type: selectedCommand.value, targetId })
  step.value = 'command'
  selectedCommand.value = null
}

function cancel() {
  if (step.value === 'target') {
    step.value = 'command'
    selectedCommand.value = null
  } else {
    emit('cancel')
  }
}

defineExpose({ cancel })
</script>

<style scoped>
.command-bar {
  padding: 0.4rem 0.5rem;
  border-top: 1px solid var(--panel-border-color);
  min-height: 2.5rem;
}

.command-flow {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  flex-wrap: wrap;
}

.actor-label {
  font-size: 0.85em;
  color: #4ecdc4;
  white-space: nowrap;
}

.options {
  display: flex;
  gap: 0.3rem;
  align-items: center;
  flex-wrap: wrap;
}

.target-header {
  font-size: 0.8em;
  color: #999;
  margin-right: 0.3rem;
}

.cmd-btn {
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--panel-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8em;
  transition: border-color 0.2s, background-color 0.2s;
}

.cmd-btn:hover {
  border-color: var(--link-color);
  background-color: rgba(233, 69, 96, 0.1);
}

.target-btn {
  border-color: #e94560;
}

.target-idx {
  color: #e94560;
  font-weight: bold;
}

.target-hp {
  color: #888;
  font-size: 0.9em;
}

.cancel-btn {
  border-color: #666;
  color: #999;
}
</style>
