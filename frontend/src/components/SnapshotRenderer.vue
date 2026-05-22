<template>
  <div class="snapshot-renderer" v-if="snapshot">
    <div class="panel-main">
      <div v-if="snapshot.combat" class="combat-info">
        <div>战斗 - 第 {{ snapshot.combat.round }} 回合 ({{ snapshot.combat.phase }})</div>
        <div v-for="c in snapshot.combat.combatants" :key="c.id" class="combatant-row">
          <span :style="{ color: c.side === 'PLAYER' ? '#4ecdc4' : '#e94560' }">{{ c.name }}</span>
          <span> HP: {{ c.hp }}/{{ c.maxHp }}</span>
          <span v-if="!c.alive" style="color: #e94560"> (击败)</span>
        </div>
      </div>
      <div v-else-if="snapshot.map" class="map-info">
        地图: {{ snapshot.map.mapId }} ({{ snapshot.map.playerX }}, {{ snapshot.map.playerY }})
      </div>
      <div v-else class="empty-state">
        加载中...
      </div>
    </div>

    <div class="panel-status">
      <StatusBars :bars="snapshot.statusBars" />
      <div class="buffs" v-if="snapshot.buffs && snapshot.buffs.length">
        <div v-for="buff in snapshot.buffs" :key="buff.id" class="buff-item">
          {{ buff.name }} ({{ buff.remaining }})
        </div>
      </div>
    </div>

    <div class="panel-log">
      <div v-for="(entry, i) in snapshot.log" :key="i">
        <span v-for="(seg, j) in entry.segments" :key="j" :style="{ color: seg.color }">{{ seg.text }}</span>
      </div>
      <div v-if="!snapshot.log || snapshot.log.length === 0" class="empty-log">
        暂无事件
      </div>
    </div>

    <div class="panel-actions">
      <ActionPanel :actions="snapshot.actions" @action="$emit('action', $event)" />
    </div>
  </div>
</template>

<script setup>
import StatusBars from './StatusBars.vue'
import ActionPanel from './ActionPanel.vue'

defineProps({ snapshot: Object })
defineEmits(['action'])
</script>

<style scoped>
.snapshot-renderer {
  display: grid;
  grid-template-columns: 3fr 1fr;
  grid-template-rows: 2fr 1fr;
  gap: 0.5rem;
  height: 100%;
}
.panel-main { grid-column: 1; grid-row: 1; min-height: 0; overflow: hidden; border: 2px solid var(--panel-border-color); padding: 0.5rem; }
.panel-status { grid-column: 2; grid-row: 1; min-height: 0; border: 2px solid var(--panel-border-color); padding: 0.5rem; }
.panel-log { grid-column: 1; grid-row: 2; min-height: 0; overflow-y: auto; border: 2px solid var(--panel-border-color); padding: 0.5rem; }
.panel-actions { grid-column: 2; grid-row: 2; min-height: 0; border: 2px solid var(--panel-border-color); padding: 0.5rem; }
.combat-info { line-height: 1.8; }
.combatant-row { margin-left: 1rem; }
.map-info { color: var(--text-color); }
.empty-state { color: var(--text-color); opacity: 0.5; }
.empty-log { color: var(--text-color); opacity: 0.5; }
.buff-item { font-size: 0.9em; margin: 0.2rem 0; color: var(--text-color); }
</style>
