import { ref } from 'vue'

const active = ref(null)

export function useModal() {
  return {
    active,
    open: (id) => { active.value = id },
    close: () => { active.value = null },
  }
}
