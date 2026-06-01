<template>
  <div class="character-create">
    <h2 class="page-title">创建角色</h2>
    <form @submit.prevent="submit" class="create-form">
      <div v-for="field in form.fields" :key="field.name" class="form-field">
        <label>{{ field.label }}</label>
        <input v-if="field.type === 'text' || field.type === 'string'"
               v-model="values[field.name]"
               :required="field.required" class="field-input" />
        <div v-else-if="field.type === 'select'" class="select-options">
          <div v-for="opt in field.options" :key="opt.value"
               class="option-card"
               :class="{ selected: values[field.name] === opt.value }"
               @click="values[field.name] = opt.value">
            <div class="opt-label">{{ opt.label }}</div>
            <div class="opt-desc">{{ opt.description }}</div>
          </div>
        </div>
      </div>

      <!-- Live preview of the selected class -->
      <div v-if="preview" class="preview-panel">
        <div class="preview-header">{{ preview.label }}</div>
        <div class="preview-body">
          <div class="detail-left">
            <div class="block-title">属性</div>
            <table class="stat-table">
              <tbody>
                <tr v-for="stat in primaryStats(preview)" :key="stat.name">
                  <td class="stat-name">{{ stat.name }}</td>
                  <td class="stat-val">{{ stat.value }}</td>
                </tr>
              </tbody>
            </table>
            <div class="block-title">升级加成</div>
            <ul v-if="growthList(preview).length" class="growth-list">
              <li v-for="g in growthList(preview)" :key="g.name">
                {{ g.name }} <span class="growth-val">每级 +{{ g.value }}</span>
              </li>
            </ul>
            <div v-else class="muted">无成长加成</div>
          </div>
          <div class="detail-right">
            <div class="portrait">
              <img v-if="preview.portrait" :src="preview.portrait" :alt="preview.label" />
              <div v-else class="portrait-empty">
                <span class="portrait-glyph">立绘</span>
                <span class="portrait-hint">{{ preview.label }}</span>
              </div>
            </div>
            <div class="description">{{ preview.description }}</div>
          </div>
        </div>
      </div>

      <div class="form-actions">
        <span class="btn" @click="$emit('cancel')">取消</span>
        <button type="submit" class="btn btn-primary" :disabled="!isValid">确认创建</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({ form: Object, classPreviews: Array })
const emit = defineEmits(['confirm', 'cancel'])

const values = reactive({})

const isValid = computed(() => {
    if (!props.form || !props.form.fields) return false
    return props.form.fields.every(f => !f.required || values[f.name])
})

// Find the select field whose chosen value maps to a class preview.
function previewFor(classId) {
  return (props.classPreviews || []).find(p => p.id === classId) || null
}

const preview = computed(() => {
  for (const v of Object.values(values)) {
    const p = previewFor(v)
    if (p) return p
  }
  return null
})

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

function submit() {
    if (isValid.value) {
        emit('confirm', { ...values })
    }
}
</script>

<style scoped>
.character-create {
  padding: 1.5rem 2rem 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  box-sizing: border-box;
  overflow: auto;
}
.page-title { color: var(--text-color); font-size: 1.6em; letter-spacing: 0.15em; margin-bottom: 1.5rem; }
.create-form { width: 100%; max-width: 760px; }
.form-field { margin-bottom: 1.5rem; }
.form-field label { display: block; color: var(--text-color); margin-bottom: 0.5rem; }
.field-input {
  width: 100%;
  padding: 0.5rem;
  background: var(--bg-color);
  border: 2px solid var(--panel-border-color);
  color: var(--text-color);
  font-size: 1rem;
  font-family: inherit;
  box-sizing: border-box;
}
.field-input:focus { outline: none; border-color: var(--link-color); }
.select-options { display: flex; gap: 1rem; flex-wrap: wrap; }
.option-card {
  border: 2px solid var(--panel-border-color);
  background: var(--panel-bg);
  padding: 1rem;
  cursor: pointer;
  min-width: 120px;
  text-align: center;
  transition: border-color 0.2s;
}
.option-card:hover { border-color: var(--link-color); }
.option-card.selected { border-color: var(--link-color); box-shadow: 0 0 0 2px var(--link-color) inset; }
.opt-label { color: var(--text-color); font-weight: bold; margin-bottom: 0.3rem; }
.opt-desc { color: var(--text-color); opacity: 0.7; font-size: 0.85em; }

/* Live preview panel — mirrors select-page detail composition */
.preview-panel {
  border: 2px solid var(--panel-border-color);
  background: var(--panel-bg);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}
.preview-header {
  color: var(--text-color);
  font-size: 1.5em;
  font-weight: bold;
  letter-spacing: 0.05em;
  border-bottom: 2px solid var(--panel-border-color);
  padding-bottom: 0.75rem;
  margin-bottom: 1.25rem;
}
.preview-body {
  display: grid;
  grid-template-columns: 1fr minmax(180px, 240px);
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
.description { color: var(--text-color); opacity: 0.85; line-height: 1.6; font-size: 0.95em; }

.form-actions { display: flex; justify-content: space-between; margin-top: 1rem; }
.btn { cursor: pointer; color: var(--link-color); padding: 0.5rem 1rem; font-family: inherit; font-size: 1rem; }
.btn-primary { background: none; border: 2px solid var(--link-color); color: var(--link-color); transition: background 0.2s, color 0.2s; }
.btn-primary:hover:not(:disabled) { background: var(--link-color); color: var(--bg-color); }
.btn-primary:disabled { opacity: 0.3; cursor: not-allowed; }
</style>
