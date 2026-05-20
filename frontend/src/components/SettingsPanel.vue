<template>
  <div class="settings-panel" v-if="visible">
    <h3>显示设置</h3>
    <div class="setting-row">
      <label>字体大小</label>
      <input type="range" min="12" max="24" step="1"
             :value="parseInt(settings.fontSize)"
             @input="settings.fontSize = $event.target.value + 'px'" />
      <span>{{ settings.fontSize }}</span>
    </div>
    <div class="setting-row">
      <label>背景颜色</label>
      <input type="color" v-model="settings.bgColor" />
    </div>
    <div class="setting-row">
      <label>文字颜色</label>
      <input type="color" v-model="settings.textColor" />
    </div>
    <div class="setting-row">
      <label>面板背景</label>
      <input type="color" v-model="settings.panelBg" />
    </div>
    <div class="setting-row">
      <label>边框颜色</label>
      <input type="color" v-model="settings.panelBorderColor" />
    </div>
    <div class="setting-row">
      <label>链接颜色</label>
      <input type="color" v-model="settings.linkColor" />
    </div>
    <button class="reset-btn" @click="resetDefaults">恢复默认</button>
  </div>
</template>

<script setup>
import { useSettings } from '../composables/useSettings.js'

defineProps({
    visible: {
        type: Boolean,
        default: false
    }
})

const { settings, defaults } = useSettings()

function resetDefaults() {
    Object.assign(settings, defaults)
}
</script>

<style scoped>
.settings-panel {
    background-color: var(--panel-bg);
    border: var(--panel-border-width) var(--panel-border-style) var(--panel-border-color);
    padding: 1.5rem;
    border-radius: 8px;
    max-width: 400px;
}

.settings-panel h3 {
    margin-bottom: 1rem;
    color: var(--link-color);
}

.setting-row {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 0.8rem;
}

.setting-row label {
    min-width: 80px;
}

.reset-btn {
    margin-top: 1rem;
    padding: 0.4rem 1rem;
    background: none;
    border: 1px solid var(--link-color);
    color: var(--link-color);
    border-radius: 4px;
    cursor: pointer;
}

.reset-btn:hover {
    background-color: rgba(233, 69, 96, 0.1);
}
</style>
