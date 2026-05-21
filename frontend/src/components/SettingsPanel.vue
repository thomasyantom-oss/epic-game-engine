<template>
  <div class="settings-panel" v-if="visible">
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
    <div class="setting-row">
      <label>地图大小</label>
      <input type="range" min="8" max="20" step="1"
             :value="settings.mapSize"
             @input="settings.mapSize = parseInt($event.target.value)" />
      <span>{{ settings.mapSize }}×{{ settings.mapSize }}</span>
    </div>
    <button class="reset-btn" @click="resetDefaults">恢复默认</button>
  </div>
</template>

<script setup>
import { useSettings } from '../composables/useSettings.js'

defineProps({
    visible: {
        type: Boolean,
        default: true
    }
})

const { settings, defaults } = useSettings()

function resetDefaults() {
    Object.assign(settings, defaults)
}
</script>

<style scoped>
.settings-panel {
    font-size: 0.9em;
}

.setting-row {
    display: flex;
    align-items: center;
    gap: 0.8rem;
    margin-bottom: 0.7rem;
}

.setting-row label {
    min-width: 70px;
    font-size: 0.9em;
}

.reset-btn {
    margin-top: 0.8rem;
    padding: 0.3rem 0.8rem;
    background: none;
    border: 1px solid var(--link-color);
    color: var(--link-color);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85em;
}

.reset-btn:hover {
    background-color: rgba(233, 69, 96, 0.1);
}
</style>
