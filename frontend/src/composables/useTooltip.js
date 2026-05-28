import { ref, reactive } from 'vue'

const visible = ref(false)
const position = reactive({ x: 0, y: 0 })
const content = ref(null)

export function useTooltip() {
  function showTooltip(data, event) {
    content.value = data
    const x = Math.min(event.clientX + 14, window.innerWidth - 220)
    const y = Math.min(event.clientY + 14, window.innerHeight - 200)
    position.x = x
    position.y = y
    visible.value = true
  }

  function moveTooltip(event) {
    if (!visible.value) return
    const x = Math.min(event.clientX + 14, window.innerWidth - 220)
    const y = Math.min(event.clientY + 14, window.innerHeight - 200)
    position.x = x
    position.y = y
  }

  function hideTooltip() {
    visible.value = false
    content.value = null
  }

  return { visible, position, content, showTooltip, moveTooltip, hideTooltip }
}
