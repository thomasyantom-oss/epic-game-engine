import { reactive, watch } from 'vue'

const STORAGE_KEY = 'epic-settings'

const defaults = {
    fontSize: '16px',
    bgColor: '#1a1a2e',
    textColor: '#e0e0e0',
    panelBg: '#16213e',
    panelBorderColor: '#0f3460',
    linkColor: '#e94560',
    mapSize: 10
}

function loadSettings() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
        return { ...defaults, ...JSON.parse(saved) }
    }
    return { ...defaults }
}

const settings = reactive(loadSettings())

function applySettings() {
    const root = document.documentElement
    root.style.setProperty('--font-size', settings.fontSize)
    root.style.setProperty('--bg-color', settings.bgColor)
    root.style.setProperty('--text-color', settings.textColor)
    root.style.setProperty('--panel-bg', settings.panelBg)
    root.style.setProperty('--panel-border-color', settings.panelBorderColor)
    root.style.setProperty('--link-color', settings.linkColor)
}

watch(settings, () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    applySettings()
}, { deep: true })

export function useSettings() {
    applySettings()
    return { settings, defaults }
}
