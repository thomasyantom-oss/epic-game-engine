<template>
  <div class="stats-tab" @mousemove="moveTooltip">
    <!-- 角色信息行 -->
    <div class="char-header" v-if="nameBar">
      <span class="char-name" :style="{ color: nameBar.color }">{{ nameBar.label }}</span>
      <span class="char-level">Lv.{{ nameBar.current }}</span>
      <span class="char-class" v-if="classBar" :style="{ color: classBar.color }">{{ classBar.label }}</span>
    </div>

    <!-- 属性点横幅 -->
    <div v-if="pendingPoints" class="pending-banner">
      <span>✦ {{ pendingPoints }} 属性点待分配</span>
      <span class="alloc-link" @click="showAlloc = !showAlloc">
        {{ showAlloc ? '[收起]' : '[分配]' }}
      </span>
    </div>

    <!-- HP 条 -->
    <div class="bar-row" v-if="hpBar">
      <span class="bar-label" :style="{ color: hpBar.color }">{{ hpBar.label }}</span>
      <div class="bar-track">
        <div class="bar-fill hp-fill"
             :style="{ width: (hpBar.max > 0 ? hpBar.current / hpBar.max * 100 : 0) + '%' }"></div>
      </div>
      <span class="bar-nums" :style="{ color: hpBar.color }">{{ hpBar.current }}/{{ hpBar.max }}</span>
    </div>

    <!-- MP 条 -->
    <div class="bar-row" v-if="mpBar">
      <span class="bar-label" :style="{ color: mpBar.color }">{{ mpBar.label }}</span>
      <div class="bar-track">
        <div class="bar-fill mp-fill"
             :style="{ width: (mpBar.max > 0 ? mpBar.current / mpBar.max * 100 : 0) + '%' }"></div>
      </div>
      <span class="bar-nums" :style="{ color: mpBar.color }">{{ mpBar.current }}/{{ mpBar.max }}</span>
    </div>

    <div class="divider"></div>

    <!-- 属性行（hover tooltip） -->
    <div
      v-for="stat in statBars"
      :key="stat.id"
      class="stat-row"
      @mouseenter="onStatHover(stat, $event)"
      @mouseleave="hideTooltip"
    >
      <span class="stat-label">{{ stat.label }}</span>
      <span class="stat-value" :style="{ color: stat.color }">{{ stat.current }}</span>
    </div>

    <!-- Buff 列表 -->
    <div class="buff-list" v-if="buffs && buffs.length">
      <div class="divider"></div>
      <div v-for="buff in buffs" :key="buff.id" class="buff-item">
        {{ buff.name }} ({{ buff.remaining }})
      </div>
    </div>

    <!-- 属性点分配面板 -->
    <AttributeAllocPanel
      v-if="showAlloc && pendingPoints"
      :pending-points="pendingPoints"
      :stat-bars="statBars"
      :player-id="playerId"
      @action="$emit('action', $event)"
      @done="showAlloc = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useTooltip } from '../composables/useTooltip.js'
import { getCharacterStats } from '../api/client.js'
import AttributeAllocPanel from './AttributeAllocPanel.vue'

const props = defineProps({
  bars: Array,
  buffs: Array,
  pendingPoints: { type: Number, default: null },
  playerId: String
})
const emit = defineEmits(['action'])

const { showTooltip, moveTooltip, hideTooltip } = useTooltip()
const showAlloc = ref(false)
const statsBreakdown = ref(null)
const fetchingStats = ref(false)

const barById = computed(() => {
  const m = {}
  for (const b of (props.bars || [])) m[b.id] = b
  return m
})

const nameBar = computed(() => barById.value['name'])
const classBar = computed(() => barById.value['class'])
const hpBar = computed(() => barById.value['hp'])
const mpBar = computed(() => barById.value['mp'])
const statBars = computed(() =>
  (props.bars || []).filter(b => !['name','class','hp','mp'].includes(b.id))
    .sort((a, b) => a.priority - b.priority)
)

watch(() => props.playerId, () => { statsBreakdown.value = null; fetchingStats.value = false })
watch(() => props.bars, () => { statsBreakdown.value = null; fetchingStats.value = false })

async function onStatHover(stat, event) {
  if (!statsBreakdown.value && !fetchingStats.value) {
    fetchingStats.value = true
    statsBreakdown.value = await getCharacterStats()
    fetchingStats.value = false
  }
  const key = statIdToKey(stat.id)
  const data = statsBreakdown.value?.[key]
  if (!data) {
    showTooltip({
      title: stat.label,
      titleColor: stat.color,
      rows: [{ label: '暂无明细', value: '', valueColor: 'var(--color-text)' }]
    }, event)
    return
  }
  const rows = data.breakdown.map(b => ({
    label: b.label,
    value: (b.value > 0 && b.type !== 'base' ? '+' : '') + b.value,
    valueColor: b.type === 'base' ? null : (b.value > 0 ? 'var(--color-rarity_uncommon)' : 'var(--color-enemy)')
  }))
  rows.push({ label: '──────', value: '', valueColor: null })
  rows.push({ label: '合计', value: String(data.final), valueColor: stat.color })
  showTooltip({ title: stat.label, titleColor: stat.color, rows }, event)
}

function statIdToKey(id) {
  const map = {
    atk: 'CombatStats.attack',
    def: 'CombatStats.defense',
    spd: 'CombatStats.speed',
    hp: 'Health.maxHp',
    mp: 'Mana.maxMp'
  }
  return map[id] || null
}
</script>

<style scoped>
.stats-tab { padding: 0.4rem 0.5rem; }
.char-header { display: flex; align-items: baseline; gap: 0.4rem; margin-bottom: 0.4rem; flex-wrap: wrap; }
.char-name { font-weight: bold; font-size: 1.05rem; }
.char-level { font-size: 0.85rem; color: var(--color-text); }
.char-class { font-size: 0.85rem; margin-left: auto; }

.pending-banner {
  display: flex; justify-content: space-between; align-items: center;
  background: color-mix(in srgb, var(--color-damage) 12%, transparent);
  border: 2px solid color-mix(in srgb, var(--color-damage) 40%, transparent);
  border-radius: 3px;
  padding: 0.2rem 0.5rem;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
  color: var(--color-damage);
}
.alloc-link { cursor: pointer; text-decoration: underline; }
.alloc-link:hover { opacity: 0.8; }

.bar-row {
  display: flex; align-items: center; gap: 0.4rem;
  margin-bottom: 0.25rem;
}
.bar-label { width: 2rem; font-size: 0.8rem; font-weight: bold; flex-shrink: 0; }
.bar-track {
  flex: 1; height: 8px;
  background: rgba(255,255,255,0.1);
  border: 2px solid rgba(255,255,255,0.15);
  border-radius: 2px;
  overflow: hidden;
}
.bar-fill { height: 100%; border-radius: 0; transition: width 0.3s; }
.hp-fill { background: var(--color-hp); }
.mp-fill { background: var(--color-mp); }
.bar-nums { font-size: 0.78rem; min-width: 3rem; text-align: right; }

.divider { border-top: 2px solid var(--panel-border-color, #333); margin: 0.4rem 0; }

.stat-row {
  display: flex; justify-content: space-between;
  padding: 0.1rem 0.2rem;
  border-radius: 2px;
  cursor: default;
  margin-bottom: 0.1rem;
}
.stat-row:hover { background: rgba(255,255,255,0.06); }
.stat-label { font-size: 0.85rem; opacity: 0.8; }
.stat-value { font-weight: bold; font-size: 0.9rem; }

.buff-list { font-size: 0.82rem; }
.buff-item { margin: 0.1rem 0; opacity: 0.8; }
</style>
