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

    <h2 class="page-title">选择角色</h2>

    <!-- Top: horizontal row of character cards + empty new-slots -->
    <div class="card-row">
      <div v-for="char in characters" :key="char.id"
           class="char-card"
           :class="{ selected: selectedId === char.id }"
           @click="selectedId = char.id">
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

    <!-- Bottom: detail for the selected character -->
    <div v-if="selectedChar" class="detail-panel">
      <div class="detail-header">
        <div class="detail-name">{{ selectedChar.name }}</div>
        <div class="detail-sub">Lv.{{ selectedChar.level }} {{ selectedChar.classLabel }}</div>
        <button class="enter-btn" @click="$emit('select', selectedChar.id)">进入游戏</button>
      </div>

      <div class="detail-body">
        <!-- Left: attributes + growth -->
        <div class="detail-left">
          <div class="block-title">属性</div>
          <table v-if="preview" class="stat-table">
            <tbody>
              <tr v-for="stat in primaryStats(preview)" :key="stat.name">
                <td class="stat-name">{{ stat.name }}</td>
                <td class="stat-val">{{ stat.value }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="muted">无职业数据</div>

          <div class="block-title">升级加成</div>
          <ul v-if="preview && growthList(preview).length" class="growth-list">
            <li v-for="g in growthList(preview)" :key="g.name">
              {{ g.name }} <span class="growth-val">每级 +{{ g.value }}</span>
            </li>
          </ul>
          <div v-else class="muted">无成长加成</div>
        </div>

        <!-- Right: portrait + description -->
        <div class="detail-right">
          <div class="portrait">
            <img v-if="preview && preview.portrait" :src="preview.portrait" :alt="preview.label" />
            <div v-else class="portrait-empty">
              <span class="portrait-glyph">立绘</span>
              <span class="portrait-hint">{{ preview ? preview.label : selectedChar.classLabel }}</span>
            </div>
          </div>
          <div class="description">{{ preview ? preview.description : '' }}</div>
        </div>
      </div>
    </div>
    <div v-else class="detail-placeholder">点击上方角色查看详情</div>

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
import { computed, ref, watch } from 'vue'
import { useSettings } from '../composables/useSettings.js'

const { settings, themes, applyTheme } = useSettings()
const themeList = computed(() => Object.values(themes))

const props = defineProps({ characters: Array, maxSlots: Number, classPreviews: Array })
const emit = defineEmits(['select', 'create', 'delete'])

const deleting = ref(null)
const selectedId = ref(null)

// Auto-select first character so the detail area isn't empty on load.
watch(() => props.characters, (chars) => {
  if (selectedId.value && (chars || []).some(c => c.id === selectedId.value)) return
  selectedId.value = (chars && chars.length) ? chars[0].id : null
}, { immediate: true })

const selectedChar = computed(() =>
  (props.characters || []).find(c => c.id === selectedId.value) || null
)

function previewFor(classId) {
  return (props.classPreviews || []).find(p => p.id === classId) || null
}

const preview = computed(() =>
  selectedChar.value ? previewFor(selectedChar.value.classId) : null
)

function primaryStats(p) {
  return Object.entries(p.modifiers || {})
    .filter(([k]) => k.startsWith('PrimaryStats.'))
    .map(([k, v]) => ({
      name: k.slice('PrimaryStats.'.length),
      value: parseInt(String(v).replace('+', ''), 10) || 0
    }))
}

function growthList(p) {
  return Object.entries(p.growth || {}).map(([name, value]) => ({ name, value }))
}

function confirmDelete(char) {
  deleting.value = char
}

function doDelete() {
  emit('delete', deleting.value.id)
  deleting.value = null
}

const emptySlots = computed(() => {
  const used = (props.characters || []).length
  const max = props.maxSlots || 9
  return Math.max(0, max - used)
})
</script>

<style scoped>
.character-select {
  padding: 1.5rem 2rem 2rem;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  box-sizing: border-box;
  gap: 1.25rem;
}
.page-title { color: var(--text-color); font-size: 1.6em; letter-spacing: 0.15em; }

/* Top character row */
.card-row {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}
.char-card {
  border: 2px solid var(--panel-border-color);
  background: var(--panel-bg);
  padding: 1rem 1.25rem;
  cursor: pointer;
  text-align: center;
  position: relative;
  min-width: 130px;
  transition: border-color 0.2s, transform 0.1s;
}
.char-card:hover { border-color: var(--link-color); }
.char-card.selected { border-color: var(--link-color); box-shadow: 0 0 0 2px var(--link-color) inset; }
.char-card.empty { border-style: dashed; opacity: 0.6; }
.char-card.empty:hover { opacity: 1; }
.char-name { color: var(--text-color); font-size: 1.1em; font-weight: bold; margin-bottom: 0.35rem; }
.char-info { color: var(--text-color); opacity: 0.7; font-size: 0.85em; }
.plus { font-size: 1.8rem; color: var(--link-color); line-height: 1.2; }
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

/* Detail panel */
.detail-panel {
  border: 2px solid var(--panel-border-color);
  background: var(--panel-bg);
  padding: 1.5rem;
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.detail-placeholder {
  border: 2px dashed var(--panel-border-color);
  color: var(--text-color);
  opacity: 0.5;
  padding: 2rem;
  text-align: center;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-header {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  border-bottom: 2px solid var(--panel-border-color);
  padding-bottom: 0.75rem;
  margin-bottom: 1.25rem;
  flex-wrap: wrap;
}
.detail-name { color: var(--text-color); font-size: 1.8em; font-weight: bold; letter-spacing: 0.05em; }
.detail-sub { color: var(--text-color); opacity: 0.65; font-size: 0.95em; }
.enter-btn {
  margin-left: auto;
  background: none;
  border: 2px solid var(--link-color);
  color: var(--link-color);
  padding: 0.45rem 1.4rem;
  font-size: 1em;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.enter-btn:hover { background: var(--link-color); color: var(--bg-color); }

.detail-body {
  display: grid;
  grid-template-columns: 1fr minmax(200px, 280px);
  gap: 2rem;
  align-items: start;
}
.block-title {
  color: var(--link-color);
  font-size: 0.85em;
  letter-spacing: 0.2em;
  border-bottom: 2px solid var(--panel-border-color);
  padding-bottom: 0.3rem;
  margin-bottom: 0.75rem;
  margin-top: 1.25rem;
}
.block-title:first-child { margin-top: 0; }
.stat-table { width: 100%; border-collapse: collapse; }
.stat-table td {
  padding: 0.3rem 0.5rem;
  color: var(--text-color);
  font-family: "Consolas", "Menlo", monospace;
}
.stat-table tr { border-bottom: 2px solid var(--panel-border-color); }
.stat-name { opacity: 0.85; }
.stat-val { text-align: right; font-weight: bold; }
.growth-list { list-style: none; }
.growth-list li {
  color: var(--text-color);
  padding: 0.25rem 0.5rem;
  font-family: "Consolas", "Menlo", monospace;
  display: flex;
  justify-content: space-between;
}
.growth-val { color: var(--color-player, #66cc55); font-weight: bold; }
.muted { color: var(--text-color); opacity: 0.5; font-size: 0.9em; padding: 0.25rem 0.5rem; }

.detail-right { display: flex; flex-direction: column; gap: 1rem; }
.portrait {
  width: 100%;
  aspect-ratio: 3 / 4;
  border: 2px solid var(--panel-border-color);
  background: var(--bg-color);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.portrait img { width: 100%; height: 100%; object-fit: cover; }
.portrait-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-color);
  opacity: 0.4;
}
.portrait-glyph { font-size: 1.4em; letter-spacing: 0.3em; }
.portrait-hint { font-size: 0.85em; }
.description {
  color: var(--text-color);
  opacity: 0.85;
  line-height: 1.6;
  font-size: 0.95em;
}

/* Confirm dialog */
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

/* Theme chips — unobtrusive top corner */
.theme-bar { position: absolute; top: 1rem; right: 1.5rem; display: flex; gap: 0.5rem; z-index: 5; }
.theme-chip {
  padding: 0.2rem 0.6rem;
  border: 2px solid var(--panel-border-color);
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
