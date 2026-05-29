import { reactive, watch } from 'vue'

const STORAGE_KEY = 'epic-settings'
const SETTINGS_VERSION = 2  // bump to invalidate old localStorage

const themes = {
    dark: {
        id: 'dark',
        label: '夜间模式',
        bgColor: '#080d12',
        textColor: '#c8d0dc',
        panelBg: '#0d1318',
        panelBorderColor: '#222c38',
        linkColor: '#55aaff'
    },
    stealth: {
        id: 'stealth',
        label: '摸鱼模式',
        bgColor: '#f5f5f5',
        textColor: '#333333',
        panelBg: '#ffffff',
        panelBorderColor: '#e0e0e0',
        linkColor: '#1a73e8'
    },
    light: {
        id: 'light',
        label: '亮色',
        bgColor: '#fafafa',
        textColor: '#212121',
        panelBg: '#ffffff',
        panelBorderColor: '#bdbdbd',
        linkColor: '#d32f2f'
    },
    dopamine: {
        id: 'dopamine',
        label: '多巴胺',
        bgColor: '#1a1a2e',
        textColor: '#f0f0f0',
        panelBg: '#0d1b2a',
        panelBorderColor: '#ff6b6b',
        linkColor: '#ffd93d'
    }
}

const defaults = {
    fontSize: '16px',
    theme: 'dark',
    bgColor: '#080d12',
    textColor: '#c8d0dc',
    panelBg: '#0d1318',
    panelBorderColor: '#222c38',
    linkColor: '#55aaff',
    mapSize: 10
}

function loadSettings() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
        const parsed = JSON.parse(saved)
        if (parsed._version === SETTINGS_VERSION) {
            return { ...defaults, ...parsed }
        }
        // Old version — discard and use new defaults
    }
    return { ...defaults }
}

const settings = reactive(loadSettings())

function applyTheme(themeId) {
    const theme = themes[themeId]
    if (!theme) return
    settings.theme = themeId
    settings.bgColor = theme.bgColor
    settings.textColor = theme.textColor
    settings.panelBg = theme.panelBg
    settings.panelBorderColor = theme.panelBorderColor
    settings.linkColor = theme.linkColor
}

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
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...settings, _version: SETTINGS_VERSION }))
    applySettings()
}, { deep: true })

export function useSettings() {
    applySettings()
    return { settings, defaults, themes, applyTheme }
}
