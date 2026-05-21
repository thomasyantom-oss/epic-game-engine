<template>
  <div class="combat-log" ref="logEl">
    <div class="round-header" v-if="round">第 {{ round }} 回合</div>
    <div v-for="(result, i) in results" :key="i" class="log-entry">
      <span v-if="result.action === 'attack'" :style="{ color: '#e0e0e0' }">
        <span style="color: #4ecdc4">{{ result.actorName }}</span>
        对
        <span style="color: #ffd93d">{{ result.targetName }}</span>
        发动攻击，造成
        <span style="color: #e94560">{{ result.damage }}</span>
        点伤害<span v-if="result.targetDefeated" style="color: #e94560">（击败！）</span>
      </span>
      <span v-else-if="result.action === 'defend'">
        <span style="color: #4ecdc4">{{ result.actorName }}</span>
        <span style="color: #999"> 进入防御姿态</span>
      </span>
    </div>
    <div v-if="phase === 'VICTORY'" class="result-msg victory">战斗胜利！</div>
    <div v-if="phase === 'DEFEAT'" class="result-msg defeat">战斗失败...</div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
    results: { type: Array, default: () => [] },
    round: { type: Number, default: 1 },
    phase: { type: String, default: 'COMMAND' }
})

const logEl = ref(null)

watch(() => props.results.length, async () => {
    await nextTick()
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
})
</script>

<style scoped>
.combat-log {
    height: 100%;
    overflow-y: auto;
    font-size: 0.85em;
    line-height: 1.8;
}

.round-header {
    color: #666;
    border-bottom: 1px solid var(--panel-border-color);
    margin-bottom: 0.3rem;
    padding-bottom: 0.2rem;
}

.log-entry { margin-bottom: 0.2rem; }

.result-msg {
    margin-top: 1rem;
    font-weight: bold;
    font-size: 1.1em;
}

.victory { color: #4ecdc4; }
.defeat { color: #e94560; }
</style>
