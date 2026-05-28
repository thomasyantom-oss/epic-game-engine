<template>
  <div class="equip-panel">
    <!-- 左栏：人形 + 装备槽 -->
    <div class="figure-col">
      <div class="figure-grid">

        <template v-for="def in SLOT_LAYOUT" :key="def.name">
          <!-- 人形 SVG -->
          <div v-if="def.name === 'figure'"
               class="figure-cell"
               :style="{ gridColumn: def.col, gridRow: def.row }">
            <svg viewBox="0 0 40 60" class="figure-svg">
              <ellipse cx="20" cy="8" rx="7" ry="7" fill="none" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="15" x2="20" y2="38" stroke="#8b949e" stroke-width="2"/>
              <line x1="8" y1="20" x2="32" y2="20" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="38" x2="12" y2="56" stroke="#8b949e" stroke-width="2"/>
              <line x1="20" y1="38" x2="28" y2="56" stroke="#8b949e" stroke-width="2"/>
            </svg>
          </div>

          <!-- 装备槽 -->
          <div v-else
               class="equip-slot"
               :class="{
                 equipped: !!slots[def.name],
                 disabled: def.disabled,
                 'drag-over': dragOverSlot === def.name
               }"
               :style="{
                 gridColumn: def.col,
                 gridRow: def.row,
                 borderColor: slots[def.name] ? slots[def.name].rarityColor : undefined
               }"
               @dragover.prevent="!def.disabled && (dragOverSlot = def.name)"
               @dragleave="dragOverSlot = null"
               @drop.prevent="!def.disabled && onDropToSlot(def.name, $event)"
               @mouseenter="slots[def.name] && showItemTooltip(slots[def.name], $event)"
               @mousemove="slots[def.name] && moveTooltip($event)"
               @mouseleave="hideTooltip"
          >
            <template v-if="slots[def.name]">
              <span class="slot-icon">{{ ICONS[slots[def.name].type] || '?' }}</span>
              <span class="slot-name" :style="{ color: slots[def.name].rarityColor }">
                {{ slots[def.name].name }}
              </span>
              <span v-if="!def.disabled" class="slot-remove"
                    @click.stop="emit('action', { type: 'unequip', params: { playerId, slot: def.name } })">
                ✕
              </span>
            </template>
            <template v-else>
              <span class="slot-label">{{ def.label }}</span>
            </template>
          </div>
        </template>

      </div>
    </div>

    <!-- 右栏：背包 -->
    <div class="bag-col">
      <div class="bag-header">
        <span class="bag-title">背包 ({{ inventory.length }}/{{ maxBag }})</span>
        <button class="sort-btn" @click="doSort">整理</button>
      </div>
      <div class="bag-grid">
        <div
          v-for="(item, i) in bagSlots"
          :key="i"
          class="bag-cell"
          :class="{ 'has-item': !!item, 'sorting': sortingAnim }"
          :style="item ? { borderColor: item.rarityColor + '99' } : {}"
          :draggable="!!item"
          @dragstart="item && onDragStart(item, $event)"
          @dragend="onDragEnd"
          @mouseenter="item && showItemTooltip(item, $event)"
          @mousemove="item && moveTooltip($event)"
          @mouseleave="hideTooltip"
          @click="item && onBagItemClick(item)"
        >
          <template v-if="item">
            <span class="item-icon">{{ ICONS[item.type] || '?' }}</span>
            <span class="item-name" :style="{ color: item.rarityColor }">{{ item.name }}</span>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useTooltip } from '../composables/useTooltip.js'

const ICONS = { weapon: '⚔', armor: '🛡', accessory: '💍', boots: '👢', head: '⛑', offhand: '🔰' }

const SLOT_LAYOUT = [
  { name: 'head',      col: 2, row: 1, label: '头盔', disabled: true  },
  { name: 'weapon',    col: 1, row: 2, label: '武器', disabled: false },
  { name: 'figure',    col: 2, row: 2, label: '',     disabled: true  },
  { name: 'offhand',   col: 3, row: 2, label: '副手', disabled: true  },
  { name: 'armor',     col: 2, row: 3, label: '护甲', disabled: false },
  { name: 'accessory', col: 1, row: 4, label: '饰品', disabled: false },
  { name: 'boots',     col: 3, row: 4, label: '靴子', disabled: true  },
]

const props = defineProps({ equipment: Object, playerId: String })
const emit = defineEmits(['action'])
const { showTooltip, moveTooltip, hideTooltip } = useTooltip()

const maxBag = 16
const sortingAnim = ref(false)
const dragOverSlot = ref(null)

const slots = computed(() => props.equipment?.slots || {})
const inventory = computed(() => props.equipment?.inventory || [])

const bagSlots = computed(() => {
  const arr = [...inventory.value]
  while (arr.length < maxBag) arr.push(null)
  return arr
})

function onDragStart(item, event) {
  event.dataTransfer.setData('itemId', item.id)
  event.dataTransfer.effectAllowed = 'move'
}

function onDragEnd() {
  dragOverSlot.value = null
}

function onDropToSlot(slotName, event) {
  dragOverSlot.value = null
  const itemId = event.dataTransfer.getData('itemId')
  if (itemId) emit('action', { type: 'equip', params: { playerId: props.playerId, itemId } })
}

function onBagItemClick(item) {
  emit('action', { type: 'equip', params: { playerId: props.playerId, itemId: item.id } })
}

function doSort() {
  sortingAnim.value = true
  setTimeout(() => { sortingAnim.value = false }, 400)
}

function showItemTooltip(item, event) {
  const rows = Object.entries(item.stats || {}).map(([k, v]) => ({
    label: STAT_LABELS[k] || k,
    value: '+' + v,
    valueColor: '#4ade80'
  }))
  rows.push({ label: '──────', value: '', valueColor: null })
  rows.push({
    label: (RARITY_LABELS[item.rarity] || item.rarity) + ' · ' + (TYPE_LABELS[item.type] || item.type),
    value: '', valueColor: '#8b949e'
  })
  showTooltip({ title: item.name, titleColor: item.rarityColor || '#fff', rows }, event)
}

const STAT_LABELS = { attack: '攻击', defense: '防御', speed: '速度', magic: '魔法' }
const RARITY_LABELS = { common: '普通', uncommon: '优质', rare: '稀有', epic: '史诗', legendary: '传说' }
const TYPE_LABELS = { weapon: '武器', armor: '护甲', accessory: '饰品' }
</script>

<style scoped>
.equip-panel {
  display: flex;
  height: 100%;
  gap: 0.5rem;
  padding: 0.5rem;
  overflow: hidden;
}

.figure-col {
  width: 38%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.figure-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: auto auto auto auto;
  gap: 0.3rem;
  width: 100%;
}

.figure-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.figure-svg { width: 100%; max-width: 60px; opacity: 0.5; }

.equip-slot {
  width: 52px;
  height: 52px;
  border: 2px solid #30363d;
  border-radius: 3px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  position: relative;
  cursor: default;
  background: rgba(255,255,255,0.03);
  transition: border-color 0.2s;
  overflow: hidden;
  user-select: none;
  justify-self: center;
}
.equip-slot.equipped { background: rgba(255,255,255,0.06); }
.equip-slot.disabled { opacity: 0.3; }
.equip-slot.drag-over { border-color: #60a5fa !important; background: rgba(96,165,250,0.1); }
.slot-icon { font-size: 1.2rem; line-height: 1; }
.slot-name { font-size: 0.6rem; text-align: center; line-height: 1.1; margin-top: 2px; max-width: 50px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slot-label { font-size: 0.65rem; color: #444; }
.slot-remove {
  position: absolute; top: 1px; right: 2px;
  font-size: 0.6rem; color: #888; cursor: pointer; line-height: 1;
}
.slot-remove:hover { color: #f87171; }

.bag-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.bag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.4rem;
  flex-shrink: 0;
}
.bag-title { font-size: 0.85rem; opacity: 0.7; }
.sort-btn {
  font-size: 0.75rem;
  background: rgba(255,255,255,0.08);
  border: 2px solid rgba(255,255,255,0.2);
  color: var(--text-color);
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
  cursor: pointer;
}
.sort-btn:hover { background: rgba(255,255,255,0.14); }

.bag-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.25rem;
  overflow-y: auto;
  flex: 1;
}
.bag-cell {
  aspect-ratio: 1;
  border: 2px solid rgba(255,255,255,0.1);
  border-radius: 3px;
  background: rgba(255,255,255,0.03);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  cursor: default;
  overflow: hidden;
  user-select: none;
  transition: border-color 0.2s, opacity 0.2s;
}
.bag-cell.has-item { cursor: grab; }
.bag-cell.has-item:hover { background: rgba(255,255,255,0.08); }
.bag-cell.sorting { opacity: 0.5; }
.item-icon { font-size: 1.1rem; line-height: 1; }
.item-name { font-size: 0.6rem; text-align: center; line-height: 1.2; overflow: hidden; text-overflow: ellipsis; width: 100%; padding: 0 2px; }
</style>
