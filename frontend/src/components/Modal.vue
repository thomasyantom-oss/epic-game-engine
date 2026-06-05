<template>
  <div v-if="visible" class="modal-mask" @click.self="$emit('close')">
    <div class="modal-frame">
      <button class="modal-close" @click="$emit('close')">x</button>
      <slot />
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close'])

function onKey(event) {
  if (event.key === 'Escape' && props.visible) emit('close')
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.62);
}

.modal-frame {
  position: relative;
  width: min(42rem, 86vw);
  max-height: 82vh;
  overflow: auto;
  padding: 1rem;
  background: var(--panel-bg);
  border: 2px solid var(--panel-border-color);
  border-radius: 4px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
}

.modal-close {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  z-index: 2;          /* 高于 slot 面板内容(如专精面板 position:relative 的根),保证关闭按钮始终可点;仍低于面板内 confirm-overlay(z-index:10) */
  width: 1.8rem;
  height: 1.8rem;
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 3px;
  color: var(--color-text);
  cursor: pointer;
  font-family: inherit;
  font-weight: 700;
}

.modal-close:hover {
  border-color: var(--color-enemy);
  color: var(--color-enemy);
}
</style>
