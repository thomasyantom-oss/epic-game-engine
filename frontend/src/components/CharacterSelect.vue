<template>
  <div class="character-select">
    <div class="theme-bar">
      <span v-for="theme in themeList" :key="theme.id"
            class="theme-chip"
            :class="{ active: settings.theme === theme.id }"
            @click="applyTheme(theme.id)">
        {{ theme.label }}
      </span>
    </div>
    <h2>选择角色</h2>
    <div class="card-grid">
      <div v-for="char in characters" :key="char.id"
           class="char-card" @click="$emit('select', char.id)">
        <div class="char-name">{{ char.name }}</div>
        <div class="char-info">Lv.{{ char.level }} {{ char.classLabel }}</div>
        <div class="delete-btn" @click.stop="confirmDelete(char)">x</div>
      </div>
      <div v-for="i in emptySlots" :key="'empty-'+i"
           class="char-card empty" @click="$emit('create')">
        <div class="plus">+</div>
        <div class="char-info">新建角色</div>
      </div>
    </div>
    <div v-if="deleting" class="confirm-overlay" @click="deleting = null">
      <div class="confirm-dialog" @click.stop>
        <div class="confirm-text">确认删除「{{ deleting.name }}」？</div>
        <div class="confirm-actions">
          <span class="confirm-btn yes" @click="doDelete">删除</span>
          <span class="confirm-btn no" @click="deleting = null">取消</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useSettings } from '../composables/useSettings.js'

const { settings, themes, applyTheme } = useSettings()
const themeList = computed(() => Object.values(themes))

const props = defineProps({ characters: Array, maxSlots: Number })
const emit = defineEmits(['select', 'create', 'delete'])

const deleting = ref(null)

function confirmDelete(char) {
  deleting.value = char
}

function doDelete() {
  emit('delete', deleting.value.id)
  deleting.value = null
}

const emptySlots = computed(() => {
    const used = (props.characters || []).length
    const max = props.maxSlots || 5
    return Math.max(0, max - used)
})
</script>

<style scoped>
.character-select { padding: 2rem; display: flex; flex-direction: column; align-items: center; height: 100%; position: relative; }
h2 { color: var(--text-color); margin-bottom: 1.5rem; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 1rem; width: 100%; max-width: 600px; }
.char-card { border: 2px solid var(--panel-border-color); padding: 1.5rem 1rem; cursor: pointer; text-align: center; position: relative; }
.char-card:hover { border-color: var(--link-color); }
.char-card.empty { border-style: dashed; opacity: 0.6; }
.char-card.empty:hover { opacity: 1; }
.char-name { color: var(--text-color); font-size: 1.1em; font-weight: bold; margin-bottom: 0.5rem; }
.char-info { color: var(--text-color); opacity: 0.7; font-size: 0.9em; }
.plus { font-size: 2rem; color: var(--link-color); }
.delete-btn {
  position: absolute;
  top: 4px; right: 6px;
  color: var(--color-enemy, #e57373);
  font-size: 0.8em;
  opacity: 0;
  transition: opacity 0.2s;
  padding: 2px 5px;
}
.char-card:hover .delete-btn { opacity: 0.7; }
.delete-btn:hover { opacity: 1 !important; }
.confirm-overlay {
  position: absolute; inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 10;
}
.confirm-dialog {
  background: var(--panel-bg, #1a1a2e);
  border: 2px solid var(--panel-border-color);
  padding: 1.5rem 2rem;
  text-align: center;
}
.confirm-text { color: var(--text-color); margin-bottom: 1rem; }
.confirm-actions { display: flex; gap: 1.5rem; justify-content: center; }
.confirm-btn { cursor: pointer; padding: 0.3rem 1rem; }
.confirm-btn.yes { color: var(--color-enemy, #e57373); }
.confirm-btn.yes:hover { text-decoration: underline; }
.confirm-btn.no { color: var(--text-color); opacity: 0.7; }
.confirm-btn.no:hover { opacity: 1; }
.theme-bar { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
.theme-chip {
  padding: 0.2rem 0.6rem;
  border: 1px solid var(--panel-border-color);
  border-radius: 3px;
  cursor: pointer;
  font-size: 0.8em;
  color: var(--text-color);
  opacity: 0.6;
  transition: opacity 0.2s, border-color 0.2s;
}
.theme-chip:hover { opacity: 0.9; }
.theme-chip.active { opacity: 1; border-color: var(--link-color); color: var(--link-color); }
</style>
