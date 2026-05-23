<template>
  <div class="character-select">
    <h2>选择角色</h2>
    <div class="card-grid">
      <div v-for="char in characters" :key="char.id"
           class="char-card" @click="$emit('select', char.id)">
        <div class="char-name">{{ char.name }}</div>
        <div class="char-info">Lv.{{ char.level }} {{ char.classLabel }}</div>
      </div>
      <div v-for="i in emptySlots" :key="'empty-'+i"
           class="char-card empty" @click="$emit('create')">
        <div class="plus">+</div>
        <div class="char-info">新建角色</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ characters: Array, maxSlots: Number })
defineEmits(['select', 'create'])

const emptySlots = computed(() => {
    const used = (props.characters || []).length
    const max = props.maxSlots || 5
    return Math.max(0, max - used)
})
</script>

<style scoped>
.character-select { padding: 2rem; display: flex; flex-direction: column; align-items: center; height: 100%; }
h2 { color: var(--text-color); margin-bottom: 1.5rem; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 1rem; width: 100%; max-width: 600px; }
.char-card { border: 2px solid var(--panel-border-color); padding: 1.5rem 1rem; cursor: pointer; text-align: center; }
.char-card:hover { border-color: var(--link-color); }
.char-card.empty { border-style: dashed; opacity: 0.6; }
.char-card.empty:hover { opacity: 1; }
.char-name { color: var(--text-color); font-size: 1.1em; font-weight: bold; margin-bottom: 0.5rem; }
.char-info { color: var(--text-color); opacity: 0.7; font-size: 0.9em; }
.plus { font-size: 2rem; color: var(--link-color); }
</style>
