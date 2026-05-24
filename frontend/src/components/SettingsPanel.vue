<template>
  <div class="settings-panel">
    <div class="setting-row">
      <label>主题</label>
      <div class="theme-options">
        <span v-for="theme in themeList" :key="theme.id"
              class="theme-chip"
              :class="{ active: settings.theme === theme.id }"
              @click="applyTheme(theme.id)">
          {{ theme.label }}
        </span>
      </div>
    </div>
    <div class="setting-row">
      <label>字体大小</label>
      <input type="range" min="12" max="24" step="1"
             :value="parseInt(settings.fontSize)"
             @input="settings.fontSize = $event.target.value + 'px'" />
      <span>{{ settings.fontSize }}</span>
    </div>
    <div class="setting-row">
      <label>地图大小</label>
      <input type="range" min="8" max="20" step="1"
             :value="settings.mapSize"
             @input="settings.mapSize = parseInt($event.target.value)" />
      <span>{{ settings.mapSize }}&times;{{ settings.mapSize }}</span>
    </div>
    <div class="color-section">
      <div class="setting-row">
        <label>背景</label>
        <input type="color" v-model="settings.bgColor" />
      </div>
      <div class="setting-row">
        <label>文字</label>
        <input type="color" v-model="settings.textColor" />
      </div>
      <div class="setting-row">
        <label>面板</label>
        <input type="color" v-model="settings.panelBg" />
      </div>
      <div class="setting-row">
        <label>边框</label>
        <input type="color" v-model="settings.panelBorderColor" />
      </div>
      <div class="setting-row">
        <label>链接</label>
        <input type="color" v-model="settings.linkColor" />
      </div>
    </div>
    <button class="reset-btn" @click="resetDefaults">恢复默认</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useSettings } from '../composables/useSettings.js'

const { settings, defaults, themes, applyTheme } = useSettings()

const themeList = computed(() => Object.values(themes))

function resetDefaults() {
    applyTheme('dark')
    settings.fontSize = defaults.fontSize
    settings.mapSize = defaults.mapSize
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
    min-width: 60px;
    font-size: 0.9em;
}

.theme-options {
    display: flex;
    gap: 0.4rem;
    flex-wrap: wrap;
}

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

.theme-chip.active {
    opacity: 1;
    border-color: var(--link-color);
    color: var(--link-color);
}

.color-section {
    margin-top: 0.5rem;
    padding-top: 0.5rem;
    border-top: 1px solid var(--panel-border-color);
}

.reset-btn {
    margin-top: 0.8rem;
    padding: 0;
    background: none;
    border: none;
    color: var(--link-color);
    cursor: pointer;
    font-size: 0.85em;
}

.reset-btn:hover {
    opacity: 0.7;
}
</style>
