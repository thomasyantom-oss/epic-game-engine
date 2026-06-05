<template>
  <div class="spec-panel">
    <div class="spec-head">
      <div class="spec-title">专精</div>
      <div v-if="readonly" class="spec-readonly">战斗中</div>
    </div>

    <div class="spec-path">
      <template v-if="path.length > 0">
        <span v-for="(node, index) in path" :key="node.id" class="path-node">
          {{ node.label || node.id }}
          <span v-if="index < path.length - 1" class="path-arrow">/</span>
        </span>
      </template>
      <span v-else class="path-empty">未专精</span>
    </div>

    <section v-if="pending" class="spec-section">
      <div class="section-label">第 {{ pending.tier }} 层可选</div>
      <div class="option-grid">
        <button
          v-for="option in pending.options || []"
          :key="option.id"
          class="spec-option"
          :disabled="readonly"
          @click="choose(option)"
        >
          <span class="option-name">{{ option.label || option.id }}</span>
          <span v-if="option.description" class="option-desc">{{ option.description }}</span>
          <span class="effect-list">
            <span v-for="line in effectLines(option.effects)" :key="line" class="effect-line">{{ line }}</span>
          </span>
        </button>
      </div>
    </section>

    <section v-if="locked.length > 0" class="spec-section">
      <div class="section-label">后续层级</div>
      <div class="locked-list">
        <div v-for="item in locked" :key="item.tier" class="locked-row">
          <span>第 {{ item.tier }} 层</span>
          <span>L{{ item.requiresLevel }} 解锁</span>
        </div>
      </div>
    </section>

    <div v-if="!pending && locked.length === 0" class="spec-terminal">已达最深专精</div>

    <div v-if="confirmOption" class="confirm-overlay" @click="confirmOption = null">
      <div class="confirm-dialog" @click.stop>
        <div class="confirm-text">
          选择「{{ confirmOption.label || confirmOption.id }}」后不可更改。确认选择该专精？
        </div>
        <div class="confirm-actions">
          <span class="confirm-btn yes" @click="confirmChoose">确认</span>
          <span class="confirm-btn no" @click="confirmOption = null">取消</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  specialization: { type: Object, default: null },
  readonly: Boolean,
})
const emit = defineEmits(['action'])

const path = computed(() => props.specialization?.path ?? [])
const pending = computed(() => props.specialization?.pending ?? null)
const locked = computed(() => props.specialization?.locked ?? [])
const confirmOption = ref(null)

function choose(option) {
  if (props.readonly) return
  confirmOption.value = option
}

function confirmChoose() {
  const option = confirmOption.value
  if (!option) return
  emit('action', { type: 'choose_specialization', params: { specId: option.id } })
  confirmOption.value = null
}

function effectLines(effects) {
  const lines = []
  if (!effects) return ['成长 未知']
  if (effects.mainAttr) lines.push(`主属性改为 ${effects.mainAttr}`)
  if (effects.growth && Object.keys(effects.growth).length > 0) {
    const growth = Object.entries(effects.growth).map(([name, value]) => `${name}+${value}`).join(' / ')
    lines.push(`成长 ${growth}`)
  } else {
    lines.push('成长 未知')
  }
  if (effects.grantPassives && effects.grantPassives.length > 0) {
    lines.push(`授予被动 ${effects.grantPassives.join(' / ')}`)
  }
  return lines
}
</script>

<style scoped>
.spec-panel {
  min-height: 16rem;
  color: var(--color-text);
  position: relative;
}

.spec-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding-right: 2.2rem;
  padding-bottom: 0.5rem;
  margin-bottom: 0.7rem;
  border-bottom: 2px solid var(--panel-border-color);
}

.spec-title {
  font-weight: 700;
}

.spec-readonly {
  margin-left: auto;
  padding: 0.1rem 0.4rem;
  border: 2px solid var(--color-enemy);
  border-radius: 3px;
  font-size: 0.8rem;
  color: var(--color-enemy);
  font-weight: 700;
}

.spec-path {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.3rem;
  min-height: 2rem;
  padding: 0.35rem 0.5rem;
  margin-bottom: 0.75rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
}

.path-node {
  font-weight: 700;
  color: var(--color-highlight);
}

.path-arrow {
  margin-left: 0.3rem;
  color: var(--color-text);
  opacity: 0.55;
}

.path-empty {
  opacity: 0.6;
}

.spec-section {
  margin-top: 0.75rem;
}

.section-label {
  margin-bottom: 0.45rem;
  font-size: 0.85rem;
  font-weight: 700;
  opacity: 0.78;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 0.5rem;
}

.spec-option {
  min-height: 8rem;
  padding: 0.6rem;
  text-align: left;
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  color: var(--color-text);
  cursor: pointer;
  font-family: inherit;
}

.spec-option:hover:not(:disabled) {
  border-color: var(--color-highlight);
  background: color-mix(in srgb, var(--color-highlight) 8%, transparent);
}

.spec-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.option-name {
  display: block;
  margin-bottom: 0.35rem;
  font-weight: 700;
  color: var(--color-highlight);
}

.option-desc {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.82rem;
  line-height: 1.35;
  opacity: 0.72;
}

.effect-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.effect-line {
  font-size: 0.82rem;
  line-height: 1.35;
}

.locked-list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.locked-row {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.45rem 0.6rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  opacity: 0.48;
}

.spec-terminal {
  padding: 1rem;
  margin-top: 0.75rem;
  text-align: center;
  border: 2px solid var(--panel-border-color);
  border-radius: 3px;
  color: var(--color-highlight);
  font-weight: 700;
}

.confirm-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.confirm-dialog {
  background: var(--panel-bg, #1a1a2e);
  border: 2px solid var(--panel-border-color);
  padding: 1.5rem 2rem;
  text-align: center;
  max-width: 22rem;
}

.confirm-text {
  color: var(--color-text);
  margin-bottom: 1rem;
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  gap: 1.5rem;
  justify-content: center;
}

.confirm-btn {
  cursor: pointer;
  padding: 0.3rem 1rem;
}

.confirm-btn.yes {
  color: var(--color-enemy);
}

.confirm-btn.yes:hover {
  text-decoration: underline;
}

.confirm-btn.no {
  color: var(--color-text);
  opacity: 0.7;
}

.confirm-btn.no:hover {
  opacity: 1;
}
</style>
