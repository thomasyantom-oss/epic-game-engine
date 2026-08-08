<template>
  <div class="skillbook">
    <div class="sb-tabs">
      <FantasyButton
        v-for="item in tabs"
        :key="item.id"
        class="sb-tab"
        :active="tab === item.id"
        @click="tab = item.id"
      >
        {{ item.label }}
      </FantasyButton>
      <span v-if="tab === 'active'" class="sb-count">出战 {{ equippedCount }}/{{ slots }}</span>
      <span v-if="readonly" class="sb-readonly">战斗中</span>
    </div>

    <div class="sb-body">
      <div class="sb-list">
        <div
          v-for="skill in currentList"
          :key="skillKey(skill)"
          role="button"
          tabindex="0"
          class="sb-row"
          :class="{ equipped: skill.equipped, selected: skillKey(skill) === selectedKey }"
          @click="selectSkill(skill)"
          @keydown.enter.prevent="selectSkill(skill)"
          @keydown.space.prevent="selectSkill(skill)"
        >
          <div class="sb-icon">{{ skill.icon }}</div>
          <div class="sb-info">
            <div class="sb-name">
              {{ skill.name }}
              <span class="sb-lv">Lv{{ skill.level ?? 1 }}</span>
              <span v-if="skill.source === 'talent'" class="sb-source">天赋</span>
            </div>
            <div class="sb-desc">{{ skill.description }}</div>
          </div>
          <template v-if="tab === 'active'">
            <FantasyButton
              class="sb-tree"
              :title="talentTree ? '打开天赋树' : '专精后解锁'"
              @click.stop="emit('open-talent')"
            >
              树
            </FantasyButton>
            <template v-if="!readonly">
              <FantasyButton v-if="skill.equipped" class="sb-btn sb-remove" danger @click.stop="emitAction('skillbook_unequip', skill.base)">移除</FantasyButton>
              <FantasyButton v-else class="sb-btn" :disabled="equippedCount >= slots" @click.stop="emitAction('skillbook_equip', skill.base)">出战</FantasyButton>
            </template>
          </template>
        </div>
        <div v-if="currentList.length === 0" class="sb-empty">{{ emptyText }}</div>
      </div>

      <aside class="sb-detail">
        <template v-if="selectedSkill">
          <div class="detail-head">
            <div class="detail-icon">{{ selectedSkill.icon }}</div>
            <div class="detail-title-block">
              <div class="detail-name">{{ selectedSkill.name }}</div>
              <div class="detail-meta">
                <span>Lv{{ selectedSkill.level ?? 1 }}</span>
                <span v-if="selectedSkill.equipped">出战中</span>
                <span v-if="selectedSkill.source === 'talent'">天赋</span>
                <span>{{ tabLabel }}</span>
              </div>
            </div>
          </div>

          <div class="detail-rows">
            <template v-for="(row, i) in detailRows" :key="i">
              <div v-if="isDivider(row)" class="detail-divider"></div>
              <div v-else class="detail-row">
                <span class="detail-label">{{ row.label }}</span>
                <span class="detail-value" :style="{ color: row.valueColor || 'inherit' }">{{ row.value }}</span>
              </div>
            </template>
          </div>
        </template>
        <div v-else class="sb-empty">暂无可显示技能</div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FantasyButton from './ui/FantasyButton.vue'

const props = defineProps({
  skillbook: { type: Object, default: null },
  talentTree: { type: Object, default: null },
  readonly: Boolean,
})
const emit = defineEmits(['action', 'open-talent'])

const tab = ref('active')
const selectedKey = ref(null)
const tabs = [
  { id: 'active', label: '主动' },
  { id: 'passive', label: '被动' },
  { id: 'general', label: '通用' },
]

const slots = computed(() => props.skillbook?.slots ?? 6)
const known = computed(() => props.skillbook?.known ?? [])
const activeKnown = computed(() =>
  [...known.value].filter(skill => (skill.kind ?? 'active') === 'active')
    .sort((a, b) => Number(b.equipped === true) - Number(a.equipped === true))
)
const passiveKnown = computed(() =>
  known.value.filter(skill => skill.kind === 'passive')
)
const generalKnown = computed(() =>
  known.value.filter(skill => skill.kind === 'general')
)
const equippedCount = computed(() => props.skillbook?.equippedCount ?? activeKnown.value.filter(skill => skill.equipped).length)
const sortedKnown = activeKnown
const currentList = computed(() => {
  if (tab.value === 'active') return sortedKnown.value
  if (tab.value === 'passive') return passiveKnown.value
  return generalKnown.value
})
const selectedSkill = computed(() =>
  currentList.value.find(skill => skillKey(skill) === selectedKey.value) ?? currentList.value[0] ?? null
)
const tabLabel = computed(() => tabs.find(item => item.id === tab.value)?.label ?? '')
const emptyText = computed(() => {
  if (tab.value === 'active') return '暂无主动技能'
  if (tab.value === 'passive') return '暂无被动'
  return '暂无通用技能'
})
const detailRows = computed(() => {
  const skill = selectedSkill.value
  if (!skill) return []
  const rows = skill.tooltip?.rows
  return rows?.length ? rows : [{ label: skill.description || '', value: '', valueColor: null }]
})

watch([tab, currentList], () => {
  if (!currentList.value.some(skill => skillKey(skill) === selectedKey.value)) {
    selectedKey.value = currentList.value.length ? skillKey(currentList.value[0]) : null
  }
}, { immediate: true })

function emitAction(type, base) {
  emit('action', { type, params: { base } })
}

function skillKey(skill) {
  return `${skill.source ?? 'skill'}:${skill.kind ?? 'active'}:${skill.base}`
}

function selectSkill(skill) {
  selectedKey.value = skillKey(skill)
}

function isDivider(row) {
  return String(row?.label ?? '').startsWith('──')
}
</script>

<style scoped>
.skillbook {
  min-height: 16rem;
  color: var(--color-text);
}

.sb-tabs {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-right: 2.2rem;
  padding-bottom: 0.5rem;
  margin-bottom: 0.7rem;
  border-bottom: 2px solid var(--panel-border-color);
}

.sb-btn {
  min-width: 4rem;
}

.sb-tab {
  min-width: 4.5rem;
}

.sb-count {
  margin-left: auto;
  color: var(--color-highlight);
  font-weight: 700;
}

.sb-readonly {
  margin-left: auto;
  padding: 0.1rem 0.4rem;
  border: 2px solid var(--color-enemy);
  border-radius: 3px;
  color: var(--color-enemy);
  font-size: 0.8rem;
  font-weight: 700;
}

.sb-count + .sb-readonly {
  margin-left: 0;
}

.sb-body {
  display: grid;
  grid-template-columns: minmax(16rem, 1fr) minmax(14rem, 0.9fr);
  gap: 0.75rem;
  align-items: stretch;
}

.sb-list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  min-width: 0;
}

.sb-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 3.7rem;
  padding: 0.45rem 0.6rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  width: 100%;
}

.sb-row.equipped {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 8%, transparent);
}

.sb-row.selected {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 14%, transparent);
}

.sb-icon {
  width: 2.2rem;
  height: 2.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  font-weight: 700;
}

.sb-info {
  flex: 1;
  min-width: 0;
}

.sb-name {
  font-weight: 700;
}

.sb-desc {
  font-size: 0.8rem;
  line-height: 1.35;
  opacity: 0.72;
}

.sb-lv {
  font-size: 0.75rem;
  color: var(--color-highlight);
  border: 2px solid var(--color-highlight);
  border-radius: 3px;
  padding: 0 0.3rem;
  margin-left: 0.3rem;
}

.sb-source {
  margin-left: 0.3rem;
  padding: 0 0.3rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  font-size: 0.75rem;
  opacity: 0.75;
}

.sb-tree {
  flex-shrink: 0;
  font-size: 0.75rem;
  min-width: 2rem;
  min-height: 1.8rem;
}

.sb-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.sb-remove {
  color: var(--color-enemy);
}

.sb-empty {
  padding: 1rem;
  text-align: center;
  opacity: 0.55;
}

.sb-detail {
  min-width: 0;
  padding: 0.65rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding-bottom: 0.55rem;
  margin-bottom: 0.55rem;
  border-bottom: 2px solid var(--panel-border-color);
}

.detail-icon {
  width: 2.4rem;
  height: 2.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid var(--color-highlight);
  border-radius: 4px;
  color: var(--color-highlight);
  font-weight: 700;
}

.detail-title-block {
  min-width: 0;
}

.detail-name {
  font-weight: 700;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.2rem;
  font-size: 0.75rem;
  opacity: 0.75;
}

.detail-rows {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-row {
  display: grid;
  grid-template-columns: minmax(5rem, auto) 1fr;
  gap: 0.7rem;
  align-items: start;
  line-height: 1.45;
  font-size: 0.86rem;
}

.detail-label {
  opacity: 0.62;
}

.detail-value {
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 700;
  text-align: right;
}

.detail-row:has(.detail-value:empty) {
  grid-template-columns: 1fr;
}

.detail-row:has(.detail-value:empty) .detail-label {
  opacity: 0.78;
}

.detail-divider {
  border-top: 1px solid var(--panel-border-color);
  margin: 0.25rem 0;
}

@media (max-width: 700px) {
  .sb-body {
    grid-template-columns: 1fr;
  }
}
</style>
