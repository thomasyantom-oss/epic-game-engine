<template>
  <div class="combat-log" ref="logEl">
    <div v-for="(entry, i) in history" :key="i">
      <div v-if="entry.type === 'round-header'" class="round-header">第 {{ entry.round }} 回合</div>
      <div v-else-if="entry.type === 'attack'" class="log-entry">
        <span style="color: #4ecdc4">{{ entry.actorName }}</span>
        对
        <span style="color: #ffd93d">{{ entry.targetName }}</span>
        发动攻击，造成
        <span style="color: #e94560">{{ entry.damage }}</span>
        点伤害<span v-if="entry.targetDefeated" style="color: #e94560">（击败！）</span>
      </div>
      <div v-else-if="entry.type === 'defend'" class="log-entry">
        <span style="color: #4ecdc4">{{ entry.actorName }}</span>
        <span style="color: #999"> 进入防御姿态</span>
      </div>
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
const history = ref([])

watch(() => props.results, (newResults) => {
    if (newResults && newResults.length > 0) {
        history.value.push({ type: 'round-header', round: props.round })
        for (const r of newResults) {
            history.value.push({ type: r.action, ...r })
        }
    }
}, { deep: true })

watch(() => history.value.length, async () => {
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
    margin-top: 0.5rem;
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
