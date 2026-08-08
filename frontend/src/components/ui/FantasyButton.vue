<template>
  <button
    class="fantasy-button"
    :class="{ active, danger }"
    :type="type"
    :disabled="disabled"
    v-bind="$attrs"
    @click="onClick"
  >
    <span class="fb-slice fb-left" aria-hidden="true"></span>
    <span class="fb-slice fb-center" aria-hidden="true"></span>
    <span class="fb-slice fb-right" aria-hidden="true"></span>
    <span class="fb-label"><slot /></span>
  </button>
</template>

<script setup>
defineOptions({ inheritAttrs: false })

const props = defineProps({
  type: { type: String, default: 'button' },
  disabled: Boolean,
  active: Boolean,
  danger: Boolean,
})

const emit = defineEmits(['click'])

function onClick(event) {
  if (props.disabled) return
  emit('click', event)
}
</script>

<style scoped>
.fantasy-button {
  --fb-left: var(--ui-button-normal-left);
  --fb-center: var(--ui-button-normal-center);
  --fb-right: var(--ui-button-normal-right);
  --fb-text: var(--color-text);
  --fb-glow: transparent;

  position: relative;
  display: inline-block;
  min-width: 3.25rem;
  min-height: var(--ui-button-height);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--fb-text);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  line-height: 1;
  text-align: center;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  user-select: none;
  filter: drop-shadow(0 1px 0 rgba(0, 0, 0, 0.65));
}

.fantasy-button:hover:not(:disabled),
.fantasy-button.active {
  --fb-left: var(--ui-button-hover-left);
  --fb-center: var(--ui-button-hover-center);
  --fb-right: var(--ui-button-hover-right);
  --fb-text: var(--color-highlight);
  --fb-glow: color-mix(in srgb, var(--color-highlight) 36%, transparent);
}

.fantasy-button.danger {
  --fb-text: var(--color-enemy);
}

.fantasy-button.danger:hover:not(:disabled) {
  --fb-text: var(--color-enemy);
  --fb-glow: color-mix(in srgb, var(--color-enemy) 42%, transparent);
}

.fantasy-button:disabled {
  opacity: 0.42;
  cursor: not-allowed;
  filter: grayscale(0.4);
}

.fb-slice {
  position: absolute;
  top: 0;
  bottom: 0;
  min-width: 0;
  min-height: var(--ui-button-height);
  background-color: transparent;
  background-repeat: no-repeat;
  background-size: 100% 100%;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}

.fb-left {
  left: 0;
  z-index: 1;
  width: var(--ui-button-cap-width);
  background-image: var(--fb-left);
}

.fb-center {
  left: calc(var(--ui-button-cap-width) * 0.48);
  right: calc(var(--ui-button-cap-width) * 0.48);
  z-index: 0;
  background-image: var(--fb-center);
  background-repeat: repeat-x;
  background-size: auto 100%;
}

.fb-right {
  right: 0;
  z-index: 1;
  width: var(--ui-button-cap-width);
  background-image: var(--fb-right);
}

.fb-label {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0.15rem calc(var(--ui-button-cap-width) * 0.72);
  overflow: hidden;
  overflow-wrap: anywhere;
  -webkit-text-stroke: 1px rgba(0, 0, 0, 0.85);
  paint-order: stroke fill;
  text-shadow:
    -1px -1px 0 rgba(0, 0, 0, 0.9),
    1px -1px 0 rgba(0, 0, 0, 0.9),
    -1px 1px 0 rgba(0, 0, 0, 0.9),
    1px 1px 0 rgba(0, 0, 0, 0.9),
    0 0 8px var(--fb-glow);
  white-space: nowrap;
  pointer-events: none;
}
</style>
