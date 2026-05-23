<template>
  <div class="character-create">
    <h2>创建角色</h2>
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
      <div class="form-actions">
        <span class="btn" @click="$emit('cancel')">取消</span>
        <button type="submit" class="btn btn-primary" :disabled="!isValid">确认创建</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({ form: Object })
const emit = defineEmits(['confirm', 'cancel'])

const values = reactive({})

const isValid = computed(() => {
    if (!props.form || !props.form.fields) return false
    return props.form.fields.every(f => !f.required || values[f.name])
})

function submit() {
    if (isValid.value) {
        emit('confirm', { ...values })
    }
}
</script>

<style scoped>
.character-create { padding: 2rem; display: flex; flex-direction: column; align-items: center; height: 100%; }
h2 { color: var(--text-color); margin-bottom: 1.5rem; }
.create-form { width: 100%; max-width: 500px; }
.form-field { margin-bottom: 1.5rem; }
.form-field label { display: block; color: var(--text-color); margin-bottom: 0.5rem; }
.field-input { width: 100%; padding: 0.5rem; background: var(--bg-color); border: 1px solid var(--panel-border-color); color: var(--text-color); font-size: 1rem; box-sizing: border-box; }
.select-options { display: flex; gap: 1rem; flex-wrap: wrap; }
.option-card { border: 2px solid var(--panel-border-color); padding: 1rem; cursor: pointer; min-width: 120px; text-align: center; }
.option-card:hover { border-color: var(--link-color); }
.option-card.selected { border-color: var(--link-color); background: rgba(78, 205, 196, 0.1); }
.opt-label { color: var(--text-color); font-weight: bold; margin-bottom: 0.3rem; }
.opt-desc { color: var(--text-color); opacity: 0.7; font-size: 0.85em; }
.form-actions { display: flex; justify-content: space-between; margin-top: 2rem; }
.btn { cursor: pointer; color: var(--link-color); padding: 0.5rem 1rem; }
.btn-primary { background: none; border: 1px solid var(--link-color); color: var(--link-color); }
.btn-primary:disabled { opacity: 0.3; cursor: not-allowed; }
</style>
