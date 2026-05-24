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

      if (anim.type === 'impact' || anim.type === 'shake' || anim.type === 'damage_number') {
        // Hit animations play in parallel, don't add to delay
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
      case 'projectile': return anim.speed === 'fast' ? 300 : anim.speed === 'slow' ? 600 : 400
      case 'impact': return 150
      case 'shake': return 250
      case 'damage_number': return 500
      default: return 300
    }
  }

  return { playing, activeAnimations, play }
}
