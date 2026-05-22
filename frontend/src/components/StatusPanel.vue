<template>
  <div class="status-content">
    <div class="status-section">
      <h4>人物信息</h4>
      <div class="status-row">
        <span class="label">名称</span>
        <span class="value">冒险者</span>
      </div>
      <div class="status-row">
        <span class="label">等级</span>
        <span class="value">1</span>
      </div>
      <div class="status-row">
        <span class="label">当前位置</span>
        <span class="value">{{ currentScene || '未知' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  currentScene: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.status-content {
  font-size: 0.9em;
}

.status-section h4 {
  color: var(--link-color);
  margin-bottom: 0.6rem;
  font-size: 0.95em;
}

.status-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.4rem;
  padding: 0.2rem 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.label {
  color: var(--text-color);
}

.value {
  color: var(--text-color);
}
</style>
