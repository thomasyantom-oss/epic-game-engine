<template>
  <section class="fantasy-panel" v-bind="$attrs">
    <div class="fp-frame" aria-hidden="true">
      <span class="fp-slice fp-tl"></span>
      <span class="fp-slice fp-t"></span>
      <span class="fp-slice fp-tr"></span>
      <span class="fp-slice fp-l"></span>
      <span class="fp-slice fp-c"></span>
      <span class="fp-slice fp-r"></span>
      <span class="fp-slice fp-bl"></span>
      <span class="fp-slice fp-b"></span>
      <span class="fp-slice fp-br"></span>
    </div>
    <div class="fantasy-panel-content" :class="contentClass">
      <slot />
    </div>
  </section>
</template>

<script setup>
defineOptions({ inheritAttrs: false })

defineProps({
  contentClass: { type: [String, Array, Object], default: null },
})
</script>

<style scoped>
.fantasy-panel {
  position: relative;
  min-width: calc(var(--ui-panel-corner-size) * 2);
  min-height: calc(var(--ui-panel-corner-size) * 2);
  background: var(--panel-bg);
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}

.fp-frame {
  position: absolute;
  inset: 0;
  z-index: 0;
  display: grid;
  grid-template-columns: var(--ui-panel-corner-size) minmax(0, 1fr) var(--ui-panel-corner-size);
  grid-template-rows: var(--ui-panel-corner-size) minmax(0, 1fr) var(--ui-panel-corner-size);
  pointer-events: none;
}

.fp-slice {
  min-width: 0;
  min-height: 0;
  background-color: var(--panel-bg);
  background-repeat: no-repeat;
  background-size: 100% 100%;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}

.fp-tl { background-image: var(--ui-panel-tl); }
.fp-t { background-image: linear-gradient(180deg, rgba(255,255,255,0.08), transparent), var(--ui-panel-t); background-repeat: repeat-x; background-size: auto 100%; }
.fp-tr { background-image: var(--ui-panel-tr); }
.fp-l { background-image: linear-gradient(90deg, rgba(255,255,255,0.08), transparent), var(--ui-panel-l); background-repeat: repeat-y; background-size: 100% auto; }
.fp-c { background-image: radial-gradient(circle at 50% 0, rgba(255,255,255,0.04), transparent 42%), var(--ui-panel-c); background-repeat: repeat; background-size: auto; }
.fp-r { background-image: linear-gradient(270deg, rgba(255,255,255,0.08), transparent), var(--ui-panel-r); background-repeat: repeat-y; background-size: 100% auto; }
.fp-bl { background-image: var(--ui-panel-bl); }
.fp-b { background-image: linear-gradient(0deg, rgba(255,255,255,0.08), transparent), var(--ui-panel-b); background-repeat: repeat-x; background-size: auto 100%; }
.fp-br { background-image: var(--ui-panel-br); }

.fantasy-panel-content {
  position: relative;
  z-index: 1;
  min-height: 100%;
  padding: var(--ui-panel-content-padding);
}
</style>
