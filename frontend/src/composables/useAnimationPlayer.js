import { ref } from 'vue'

export function useAnimationPlayer() {
  const playing = ref(false)
  const activeAnimations = ref([])
  let queue = []
  let currentResolve = null

  function play(events) {
    if (!events || events.length === 0) return Promise.resolve()
    playing.value = true
    queue = [...events]
    return new Promise(resolve => {
      currentResolve = resolve
      playNext()
    })
  }

  function playNext() {
    if (queue.length === 0) {
      playing.value = false
      activeAnimations.value = []
      if (currentResolve) currentResolve()
      return
    }

    const event = queue.shift()
    const anims = event.animation || []
    if (anims.length === 0) {
      setTimeout(playNext, 400)
      return
    }

    playAnimationSequence(anims)
  }

  function playAnimationSequence(anims) {
    let delay = 0
    const active = []

    for (const anim of anims) {
      const duration = getAnimDuration(anim)
      active.push({ ...anim, startDelay: delay, duration })

      const parallel = ['impact', 'shake', 'damage_number', 'buff_up', 'debuff_down', 'mark_dead', 'indicator_add']
      if (parallel.includes(anim.type)) {
        // These play in parallel with the preceding launch animation
      } else {
        delay += duration
      }
    }

    activeAnimations.value = active
    const totalDuration = Math.max(...active.map(a => a.startDelay + a.duration))

    setTimeout(() => {
      activeAnimations.value = []
      setTimeout(playNext, 100)
    }, totalDuration)
  }

  function getAnimDuration(anim) {
    switch (anim.type) {
      case 'lunge': return 250
      case 'pulse': return 350
      case 'flash_sequence': return 400
      case 'projectile': return anim.speed === 'fast' ? 300 : anim.speed === 'slow' ? 600 : 400
      case 'beam': return 300
      case 'slash': return 400
      case 'impact': return 150
      case 'shake': return 250
      case 'damage_number': return 500
      case 'buff_up': return 500
      case 'debuff_down': return 500
      case 'mark_dead': return 400
      case 'indicator_add': return 300
      default: return 300
    }
  }

  return { playing, activeAnimations, play }
}
