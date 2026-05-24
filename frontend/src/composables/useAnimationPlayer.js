import { ref } from 'vue'

export function useAnimationPlayer() {
  const playing = ref(false)
  const activeAnimations = ref([])
  const playedEventIndex = ref(-1)
  let queue = []
  let currentResolve = null
  let eventIndex = 0

  let onEventCallback = null

  function play(events, onEvent) {
    if (!events || events.length === 0) return Promise.resolve()
    playing.value = true
    playedEventIndex.value = -1
    eventIndex = 0
    onEventCallback = onEvent || null
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
    const currentIdx = eventIndex++

    if (anims.length === 0) {
      setTimeout(() => {
        playedEventIndex.value = currentIdx
        if (onEventCallback) onEventCallback(currentIdx)
        playNext()
      }, 300)
      return
    }

    playAnimationSequence(anims, () => {
      playedEventIndex.value = currentIdx
      if (onEventCallback) onEventCallback(currentIdx)
    })
  }

  let animIdCounter = 0

  function playAnimationSequence(anims, doneCallback) {
    let delay = 0
    const active = []

    for (const anim of anims) {
      const duration = getAnimDuration(anim)
      active.push({ ...anim, startDelay: delay, duration, _id: ++animIdCounter })

      const parallel = ['impact', 'shake', 'damage_number', 'buff_up', 'debuff_down', 'mark_dead', 'indicator_add']
      if (parallel.includes(anim.type)) {
        // These play in parallel with the preceding launch animation
      } else {
        delay += getDelayContribution(anim)
      }
    }

    activeAnimations.value = active
    const totalDuration = Math.max(...active.map(a => a.startDelay + a.duration))

    // Trigger HP/log update when hit animations start (= delay, which is when launches finish)
    setTimeout(() => {
      if (doneCallback) doneCallback()
    }, delay)

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

  // How long before the next animation should start (arrival time for projectiles/beams)
  function getDelayContribution(anim) {
    switch (anim.type) {
      case 'projectile': {
        const total = anim.speed === 'fast' ? 300 : anim.speed === 'slow' ? 600 : 400
        return Math.floor(total * 0.7)
      }
      case 'beam': return 120
      case 'slash': return 350
      default: return getAnimDuration(anim)
    }
  }

  return { playing, activeAnimations, playedEventIndex, play }
}
