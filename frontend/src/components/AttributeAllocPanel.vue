<template>
  <div class="alloc-panel">
    <div class="alloc-header">
      <span>─── 分配属性点 ───</span>
      <span class="remaining" :class="{ warn: remaining === 0 }">剩余: {{ remaining }}</span>
    </div>

    <div v-for="stat in statBars" :key="stat.id" class="alloc-row">
      <span class="alloc-label">{{ stat.label }}</span>
      <span class="alloc-current">{{ stat.current }}</span>
      <span class="alloc-arrow" v-if="allocated[stat.id] > 0">→</span>
      <span class="alloc-preview" v-if="allocated[stat.id] > 0" :style="{ color: stat.color }">
        {{ stat.current + allocated[stat.id] }}
      </span>
      <span class="alloc-diff" v-if="allocated[stat.id] > 0">+{{ allocated[stat.id] }}</span>
      <div class="alloc-btns">
        <button @click="decr(stat.id)" :disabled="!allocated[stat.id]">-</button>
        <button @click="incr(stat.id)" :disabled="remaining === 0">+</button>
      </div>
    </div>

    <div class="alloc-actions">
      <button class="confirm-btn" @click="confirm" :disabled="totalAllocated === 0">确认</button>
      <button class="reset-btn" @click="reset">重置</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'

const props = defineProps({
  pendingPoints: Number,
  statBars: Array,
  playerId: String
})
const emit = defineEmits(['action', 'done'])

const allocated = reactive({})
for (const s of (props.statBars || [])) allocated[s.id] = 0

const totalAllocated = computed(() => Object.values(allocated).reduce((a, b) => a + b, 0))
const remaining = computed(() => props.pendingPoints - totalAllocated.value)

function incr(id) {
  if (remaining.value <= 0) return
  allocated[id] = (allocated[id] || 0) + 1
}

function decr(id) {
  if ((allocated[id] || 0) <= 0) return
  allocated[id]--
}

function reset() {
  for (const k of Object.keys(allocated)) allocated[k] = 0
}

const statParamMap = {
  atk: 'attack',
  def: 'defense',
  spd: 'speed'
}

async function confirm() {
  for (const [id, pts] of Object.entries(allocated)) {
    const stat = statParamMap[id] || id
    for (let i = 0; i < pts; i++) {
      emit('action', {
        type: 'allocate_point',
        params: { playerId: props.playerId, stat }
      })
    }
  }
  emit('done')
}
</script>

<style scoped>
.alloc-panel {
  border-top: 2px solid var(--panel-border-color, #333);
  margin-top: 0.4rem;
  padding-top: 0.4rem;
}
.alloc-header {
  display: flex; justify-content: space-between;
  font-size: 0.8rem; margin-bottom: 0.4rem; opacity: 0.7;
}
.remaining.warn { color: var(--color-enemy); }

.alloc-row {
  display: flex; align-items: center; gap: 0.3rem;
  margin-bottom: 0.25rem; font-size: 0.85rem;
}
.alloc-label { width: 2rem; opacity: 0.8; }
.alloc-current { width: 2rem; text-align: right; }
.alloc-arrow { color: var(--color-highlight); }
.alloc-preview { width: 2rem; text-align: right; font-weight: bold; }
.alloc-diff { font-size: 0.75rem; color: var(--color-rarity_uncommon); width: 2rem; }
.alloc-btns { margin-left: auto; display: flex; gap: 0.2rem; }
.alloc-btns button {
  width: 1.4rem; height: 1.4rem;
  background: color-mix(in srgb, var(--color-text) 8%, transparent);
  border: 2px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  color: var(--text-color);
  border-radius: 2px; cursor: pointer;
  font-size: 0.9rem; line-height: 1;
  display: flex; align-items: center; justify-content: center;
}
.alloc-btns button:hover:not(:disabled) { background: color-mix(in srgb, var(--color-text) 18%, transparent); }
.alloc-btns button:disabled { opacity: 0.3; cursor: not-allowed; }

.alloc-actions {
  display: flex; gap: 0.4rem; margin-top: 0.5rem;
}
.confirm-btn, .reset-btn {
  flex: 1; padding: 0.2rem; font-size: 0.82rem;
  border-radius: 2px; cursor: pointer;
  border: 2px solid;
}
.confirm-btn {
  background: color-mix(in srgb, var(--color-rarity_uncommon) 12%, transparent);
  border-color: color-mix(in srgb, var(--color-rarity_uncommon) 40%, transparent);
  color: var(--color-rarity_uncommon);
}
.confirm-btn:hover:not(:disabled) { background: color-mix(in srgb, var(--color-rarity_uncommon) 22%, transparent); }
.confirm-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.reset-btn {
  background: color-mix(in srgb, var(--color-text) 5%, transparent);
  border-color: color-mix(in srgb, var(--color-text) 20%, transparent);
  color: var(--text-color);
}
.reset-btn:hover { background: color-mix(in srgb, var(--color-text) 10%, transparent); }
</style>
