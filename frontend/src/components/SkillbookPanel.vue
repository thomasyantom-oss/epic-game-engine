<template>
  <div class="skillbook">
    <div class="sb-tabs">
      <button
        v-for="item in tabs"
        :key="item.id"
        class="sb-tab"
        :class="{ active: tab === item.id }"
        @click="tab = item.id"
      >
        {{ item.label }}
      </button>
      <span v-if="tab === 'active'" class="sb-count">出战 {{ equippedCount }}/{{ slots }}</span>
      <span v-if="readonly" class="sb-readonly">战斗中</span>
    </div>

    <div v-if="tab === 'active'" class="sb-list">
      <div
        v-for="skill in sortedKnown"
        :key="skill.base"
        class="sb-row"
        :class="{ equipped: skill.equipped }"
      >
        <div class="sb-icon">{{ skill.icon }}</div>
        <div class="sb-info">
          <div class="sb-name">
            {{ skill.name }}
            <span class="sb-lv">Lv{{ skill.level ?? 1 }}</span>
          </div>
          <div class="sb-desc">{{ skill.description }}</div>
        </div>
        <button
          class="sb-tree"
          type="button"
          :title="talentTree ? '打开天赋树' : '专精后解锁'"
          @click="emit('open-talent')"
        >
          树
        </button>
        <template v-if="!readonly">
          <button v-if="skill.equipped" class="sb-btn sb-remove" @click="emitAction('skillbook_unequip', skill.base)">移除</button>
          <button v-else class="sb-btn" :disabled="equippedCount >= slots" @click="emitAction('skillbook_equip', skill.base)">出战</button>
        </template>
      </div>
      <div v-if="activeKnown.length === 0" class="sb-empty">暂无主动技能</div>
    </div>

    <div v-else-if="tab === 'passive'" class="sb-list">
      <div v-for="p in passiveKnown" :key="passiveKey(p)" class="sb-row">
        <div class="sb-icon">{{ p.icon }}</div>
        <div class="sb-info">
          <div class="sb-name">
            {{ p.name }}
            <span class="sb-lv">Lv{{ p.level ?? 1 }}</span>
            <span v-if="p.source === 'talent'" class="sb-source">天赋</span>
          </div>
          <div class="sb-desc">{{ p.description }}</div>
        </div>
      </div>
      <div v-if="passiveKnown.length === 0" class="sb-empty">暂无被动</div>
    </div>

    <div v-else class="sb-empty">暂未开放</div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  skillbook: { type: Object, default: null },
  talentTree: { type: Object, default: null },
  readonly: Boolean,
})
const emit = defineEmits(['action', 'open-talent'])

const tab = ref('active')
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
const equippedCount = computed(() => props.skillbook?.equippedCount ?? activeKnown.value.filter(skill => skill.equipped).length)
const sortedKnown = activeKnown

function emitAction(type, base) {
  emit('action', { type, params: { base } })
}

function passiveKey(passive) {
  return `${passive.source ?? 'skill'}:${passive.base}`
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

.sb-tab,
.sb-btn {
  padding: 0.25rem 0.7rem;
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text);
  cursor: pointer;
  font-family: inherit;
}

.sb-tab.active {
  border-color: var(--color-highlight);
  color: var(--color-highlight);
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

.sb-list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.sb-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 3.7rem;
  padding: 0.45rem 0.6rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
}

.sb-row.equipped {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 8%, transparent);
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
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-highlight);
  cursor: pointer;
  font-family: inherit;
  font-weight: 700;
}

.sb-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.sb-remove {
  border-color: color-mix(in srgb, var(--color-enemy) 50%, transparent);
  color: var(--color-enemy);
}

.sb-empty {
  padding: 1rem;
  text-align: center;
  opacity: 0.55;
}
</style>
