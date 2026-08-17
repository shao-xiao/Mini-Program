<template>
  <div class="field-order-list" role="list" aria-label="字段排序和固定设置">
    <div
      v-for="(column, index) in orderedColumns"
      :key="column.key"
      class="order-row"
      :class="{ dragging: draggingKey === column.key }"
      @dragover.prevent
      @drop="drop($event, column.key)"
    >
      <el-icon
        class="drag-handle"
        draggable="true"
        :aria-label="`拖动${column.label}`"
        @dragstart="startDrag($event, column.key)"
        @dragend="endDrag"
      >
        <Rank />
      </el-icon>
      <span class="field-type">{{ workspaceFieldTypeIcon(column.type) }}</span>
      <span class="field-label" :title="column.label">{{ column.label }}</span>
      <span class="order-actions">
        <el-button text circle :icon="Top" :disabled="index === 0" :aria-label="`上移${column.label}`" @click="move(column.key, -1)" />
        <el-button text circle :icon="Bottom" :disabled="index === orderedColumns.length - 1" :aria-label="`下移${column.label}`" @click="move(column.key, 1)" />
        <FixedColumnControls
          :column-key="column.key"
          :column-label="column.label"
          :model-value="fixedColumns[column.key] || ''"
          @update:model-value="side => setFixed(column.key, side)"
        />
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Bottom, Rank, Top } from '@element-plus/icons-vue'
import {
  moveWorkspaceField,
  orderWorkspaceFields,
  reorderWorkspaceField,
  setWorkspaceFixedColumn,
  workspaceFieldTypeIcon
} from '../../utils/workspaceFieldManager.js'
import FixedColumnControls from './FixedColumnControls.vue'

defineOptions({ name: 'FieldOrderList' })

const props = defineProps({
  columns: { type: Array, default: () => [] },
  columnOrder: { type: Array, default: () => [] },
  fixedColumns: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:columnOrder', 'update:fixedColumns'])
const draggingKey = ref('')
const orderedColumns = computed(() => orderWorkspaceFields(props.columns, props.columnOrder))

function currentOrder() {
  return orderedColumns.value.map(column => column.key)
}

function move(key, direction) {
  emit('update:columnOrder', moveWorkspaceField(currentOrder(), key, direction))
}

function startDrag(event, key) {
  draggingKey.value = key
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', key)
}

function endDrag() {
  draggingKey.value = ''
}

function drop(event, targetKey) {
  const sourceKey = draggingKey.value || event.dataTransfer.getData('text/plain')
  if (!sourceKey || sourceKey === targetKey) {
    endDrag()
    return
  }
  const bounds = event.currentTarget.getBoundingClientRect()
  const insertAfter = event.clientY > bounds.top + bounds.height / 2
  emit('update:columnOrder', reorderWorkspaceField(currentOrder(), sourceKey, targetKey, insertAfter))
  endDrag()
}

function setFixed(key, side) {
  emit('update:fixedColumns', setWorkspaceFixedColumn(props.fixedColumns, key, side))
}
</script>

<style scoped>
.field-order-list {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
}

.order-row {
  display: grid;
  grid-template-columns: 24px 34px minmax(0, 1fr) 92px;
  align-items: center;
  min-height: 42px;
  padding: 0 8px;
  border-bottom: 1px solid var(--dhic-border-soft);
}

.order-row:last-child {
  border-bottom: 0;
}

.order-row.dragging {
  opacity: 0.45;
}

.drag-handle {
  color: var(--dhic-text-muted);
  cursor: grab;
}

.field-type {
  color: var(--dhic-text-muted);
  font-weight: 700;
}

.field-label {
  overflow: hidden;
  color: var(--dhic-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-actions {
  display: inline-flex;
  justify-content: flex-end;
}

.order-actions :deep(.el-button) {
  width: 28px;
  height: 28px;
  margin: 0;
}
</style>
