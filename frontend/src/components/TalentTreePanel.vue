<template>
  <div class="talent-panel">
    <div class="talent-head">
      <div>
        <div class="talent-title">天赋树</div>
        <div class="talent-points">
          总 {{ points.total }} / 已用 {{ points.spent }} / 可用 {{ points.available }}
        </div>
      </div>
      <div v-if="readonly" class="talent-readonly">战斗中</div>
      <button
        v-if="!readonly && talentTree"
        class="respec-btn"
        type="button"
        @click="emit('action', { type: 'talent_respec', params: {} })"
      >
        洗点
      </button>
    </div>

    <div v-if="!talentTree" class="locked-entry">专精后解锁</div>

    <div v-else class="talent-layout">
      <div class="tree-zone">
        <div class="orb-strip">
          <span class="strip-label">灵魂球</span>
          <span v-if="orbs.length === 0" class="orb-empty">无</span>
          <span v-for="orb in orbs" :key="orb.type" class="orb-chip">{{ orb.type }} x{{ orb.count }}</span>
        </div>

        <div class="tree-canvas">
          <svg class="tree-lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            <line
              v-for="edge in edges"
              :key="edge.key"
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              class="tree-edge"
              :class="{ lit: edge.lit }"
            />
          </svg>
          <button
            v-for="node in placedNodes"
            :key="node.id"
            type="button"
            class="tree-node"
            :class="[node.state, { selected: selectedId === node.id }]"
            :style="{ left: node.x + '%', top: node.y + '%' }"
            :title="node.label"
            @click="selectedId = node.id"
          >
            <span class="node-core"></span>
          </button>
        </div>
      </div>

      <aside class="node-detail">
        <template v-if="selectedNode">
          <div class="detail-head">
            <div class="detail-icon">{{ selectedNode.icon || '?' }}</div>
            <div>
              <div class="detail-name">{{ selectedNode.label }}</div>
              <div class="detail-state">{{ stateLabel(selectedNode.state) }}</div>
            </div>
          </div>

          <div class="detail-desc">{{ selectedNode.effectSummary || '暂无说明' }}</div>

          <div class="detail-section">
            <div class="section-title">受影响</div>
            <div v-if="selectedNode.type === 'skill_slot' && selectedNode.slot" class="effect-list">
              <div>主动增强: {{ selectedNode.slot.skill }} -> {{ selectedNode.slot.evolution }}</div>
              <div>所需灵魂球: {{ selectedNode.slot.orbType }} x{{ selectedNode.slot.orbCount }}</div>
              <div>进化状态: {{ selectedNode.slot.filled ? '已插球' : '未插球' }}</div>
            </div>
            <div v-else class="effect-list">
              <div>{{ selectedNode.effectSummary || '被动效果' }}</div>
              <div v-if="selectedNode.requiresSpec">需要专精: {{ selectedNode.requiresSpec }}</div>
            </div>
          </div>

          <div v-if="!readonly" class="detail-actions">
            <button
              v-if="selectedNode.actions?.canUnlock"
              class="action-btn"
              type="button"
              @click="emit('action', { type: 'talent_unlock', params: { nodeId: selectedNode.id } })"
            >
              学习
            </button>
            <button
              v-if="selectedNode.actions?.canPlaceOrb"
              class="action-btn orb-action"
              type="button"
              @click="emit('action', { type: 'talent_place_orb', params: { nodeId: selectedNode.id } })"
            >
              插球
            </button>
            <span v-if="selectedNode.actions?.reason" class="action-reason">
              {{ selectedNode.actions.reason }}
            </span>
          </div>
        </template>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  talentTree: { type: Object, default: null },
  readonly: Boolean,
})
const emit = defineEmits(['action'])

const selectedId = ref(null)
const points = computed(() => props.talentTree?.points ?? { total: 0, spent: 0, available: 0 })
const orbs = computed(() => props.talentTree?.orbInventory ?? [])
const visibleNodes = computed(() => (props.talentTree?.nodes ?? []).filter(node => node.state !== 'hidden'))
const nodeById = computed(() => Object.fromEntries(visibleNodes.value.map(node => [node.id, node])))
const selectedNode = computed(() => nodeById.value[selectedId.value] ?? visibleNodes.value[0] ?? null)

const placedNodes = computed(() => {
  const nodes = visibleNodes.value
  if (nodes.length === 0) return []
  const tiers = [...new Set(nodes.map(node => Number(node.tier) || 1))].sort((a, b) => a - b)
  const minTier = tiers[0]
  const maxTier = tiers[tiers.length - 1]
  const tierSpan = Math.max(1, maxTier - minTier)
  const tierGroups = new Map()
  for (const node of nodes) {
    const tier = Number(node.tier) || 1
    if (!tierGroups.has(tier)) tierGroups.set(tier, [])
    tierGroups.get(tier).push(node)
  }
  for (const group of tierGroups.values()) {
    group.sort((a, b) => (Number(a.order) || 0) - (Number(b.order) || 0))
  }
  return nodes.map(node => {
    const tier = Number(node.tier) || 1
    const group = tierGroups.get(tier) || []
    const index = group.findIndex(item => item.id === node.id)
    const width = Math.max(1, group.length)
    const x = 50 + (index - (width - 1) / 2) * Math.min(28, 64 / width)
    const y = 88 - ((tier - minTier) / tierSpan) * 76
    return { ...node, x, y }
  })
})

const placedById = computed(() => Object.fromEntries(placedNodes.value.map(node => [node.id, node])))
const POINTED = new Set(['unlocked', 'slot-empty', 'slot-filled'])
const edges = computed(() => {
  const out = []
  for (const node of placedNodes.value) {
    for (const parentId of node.parents || []) {
      const parent = placedById.value[parentId]
      if (!parent) continue
      const lit = POINTED.has(parent.state) && POINTED.has(node.state)
      out.push({ key: `${parentId}-${node.id}`, x1: parent.x, y1: parent.y, x2: node.x, y2: node.y, lit })
    }
  }
  return out
})

watch(visibleNodes, (nodes) => {
  if (nodes.length === 0) {
    selectedId.value = null
    return
  }
  if (!nodes.some(node => node.id === selectedId.value)) {
    selectedId.value = nodes[0].id
  }
}, { immediate: true })

function stateLabel(state) {
  return {
    locked: '未学会',
    unlockable: '可学习',
    unlocked: '已学习',
    'slot-empty': '技能槽空',
    'slot-filled': '已插球',
    'filled-node-relocked': '已进化但槽未点亮',
  }[state] || state
}
</script>

<style scoped>
.talent-panel {
  width: min(39.5rem, calc(86vw - 2rem));
  min-height: 28rem;
  color: var(--color-text);
}

.talent-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding-right: 2.2rem;
  padding-bottom: 0.6rem;
  margin-bottom: 0.7rem;
  border-bottom: 2px solid var(--panel-border-color);
}

.talent-title {
  font-weight: 700;
}

.talent-points {
  margin-top: 0.2rem;
  font-size: 0.84rem;
  color: var(--color-highlight);
}

.talent-readonly,
.respec-btn,
.action-btn {
  padding: 0.25rem 0.65rem;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  background: transparent;
  color: var(--color-text);
  font-family: inherit;
  font-weight: 700;
}

.talent-readonly {
  margin-left: auto;
  border-color: var(--color-enemy);
  color: var(--color-enemy);
}

.respec-btn {
  margin-left: auto;
  cursor: pointer;
}

.talent-readonly + .respec-btn {
  margin-left: 0;
}

.respec-btn:hover,
.action-btn:hover {
  border-color: var(--color-highlight);
  color: var(--color-highlight);
}

.locked-entry {
  min-height: 16rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  opacity: 0.55;
  font-weight: 700;
}

.talent-layout {
  display: grid;
  grid-template-columns: minmax(15rem, 1fr) 15rem;
  gap: 0.75rem;
  min-height: 26rem;
}

.tree-zone,
.node-detail {
  min-width: 0;
  border: 2px solid var(--color-border);
  border-radius: 4px;
}

.tree-zone {
  display: flex;
  flex-direction: column;
}

.orb-strip {
  min-height: 2.2rem;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0.4rem 0.55rem;
  border-bottom: 2px solid var(--panel-border-color);
}

.strip-label {
  font-weight: 700;
  opacity: 0.78;
}

.orb-chip {
  padding: 0.12rem 0.42rem;
  border: 2px solid var(--color-highlight);
  border-radius: 3px;
  color: var(--color-highlight);
  font-size: 0.82rem;
}

.orb-empty {
  opacity: 0.55;
}

.tree-canvas {
  position: relative;
  flex: 1;
  min-height: 23rem;
  overflow: hidden;
}

.tree-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.tree-edge {
  stroke: #5b6470;            /* 未点亮连线:灰 */
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
}

.tree-edge.lit {
  stroke: #f5f7fa;           /* 两端都已点亮:亮白 */
  stroke-width: 3;
}

.tree-node {
  position: absolute;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  transform: translate(-50%, -50%);
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.node-core {
  position: absolute;
  inset: 0.3rem;
  border: 3px solid #5b6470;        /* 默认/未点亮:灰边 */
  border-radius: 50%;
  background: var(--panel-bg);
}

/* 已点亮的普通天赋点:亮白边 */
.tree-node.unlocked .node-core {
  border-color: #f5f7fa;
}

/* 可点亮(尚未点):灰边(略亮以示可点) */
.tree-node.unlockable .node-core {
  border-color: #7d8793;
}

/* 技能槽·已点未插球:黄边 */
.tree-node.slot-empty .node-core {
  border-color: #ffcc33;
}

/* 技能槽·已插球:平面 checkbox 式 —— 外圈只有边框、内圈只有填充、中间留空白环 */
.tree-node.slot-filled .node-core {
  border-color: #55dd55;
  background: transparent;          /* 外圈:仅边框,中空 */
}

.tree-node.slot-filled .node-core::after {
  content: "";
  position: absolute;
  inset: 0.26rem;                   /* 空白环间距 */
  border-radius: 50%;
  background: #55dd55;              /* 内圈:仅填充 */
}

/* 洗点后(球已花、效果不生效、天赋点已退):灰外环 + 灰内圆(中间仍有圆,标记"球已花、重点免费") */
.tree-node.filled-node-relocked .node-core {
  border-color: #5b6470;
  background: transparent;
}

.tree-node.filled-node-relocked .node-core::after {
  content: "";
  position: absolute;
  inset: 0.26rem;
  border-radius: 50%;
  background: #5b6470;
}

/* 选中:外面再套一圈(平面双环) */
.tree-node.selected::after {
  content: "";
  position: absolute;
  inset: -0.15rem;
  border: 3px solid var(--color-highlight);
  border-radius: 50%;
}

.tree-node.locked,
.tree-node.filled-node-relocked {
  opacity: 0.6;
}

.node-detail {
  display: flex;
  flex-direction: column;
  padding: 0.75rem;
  gap: 0.75rem;
}

.detail-head {
  display: grid;
  grid-template-columns: 2.8rem minmax(0, 1fr);
  gap: 0.6rem;
  align-items: center;
}

.detail-icon {
  width: 2.6rem;
  height: 2.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  font-weight: 700;
}

.detail-name {
  font-weight: 700;
  color: var(--color-highlight);
  overflow-wrap: anywhere;
}

.detail-state {
  margin-top: 0.2rem;
  font-size: 0.8rem;
  opacity: 0.68;
}

.detail-desc,
.effect-list {
  line-height: 1.45;
  font-size: 0.88rem;
}

.detail-section {
  padding-top: 0.65rem;
  border-top: 2px solid var(--panel-border-color);
}

.section-title {
  margin-bottom: 0.35rem;
  font-weight: 700;
  opacity: 0.78;
}

.effect-list {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  opacity: 0.8;
}

.detail-actions {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.2rem;
}

.orb-action {
  border-color: #ffcc33;
  color: #ffcc33;
}

.action-reason {
  font-size: 0.84rem;
  opacity: 0.58;
}

@media (max-width: 760px) {
  .talent-layout {
    grid-template-columns: 1fr;
  }

  .talent-panel {
    width: min(34rem, calc(86vw - 2rem));
  }
}
</style>
