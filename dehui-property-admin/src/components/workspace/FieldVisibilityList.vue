<template>
  <div class="field-visibility">
    <div class="field-list-header">
      <el-input v-model="keyword" clearable placeholder="搜索字段" />
      <span>{{ visibleCount }}/{{ columns.length }}</span>
    </div>
    <div class="field-list" role="list" aria-label="字段显示设置">
      <label v-for="column in filteredColumns" :key="column.key" class="field-row">
        <el-checkbox
          :model-value="isVisible(column)"
          :disabled="column.required"
          :aria-label="`${isVisible(column) ? '隐藏' : '显示'}${column.label}`"
          @change="checked => toggle(column.key, checked)"
        />
        <span class="field-type">{{ workspaceFieldTypeIcon(column.type) }}</span>
        <span class="field-label" :title="column.label">{{ column.label }}</span>
        <el-tag v-if="column.required" size="small" effect="plain">必选</el-tag>
        <el-tag v-else-if="column.custom" size="small" effect="plain">自定义</el-tag>
      </label>
      <div v-if="filteredColumns.length === 0" class="empty-state">没有匹配字段</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { toggleWorkspaceFieldVisibility, workspaceFieldTypeIcon } from '../../utils/workspaceFieldManager.js'

defineOptions({ name: 'FieldVisibilityList' })

const props = defineProps({
  columns: { type: Array, default: () => [] },
  visibleColumns: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:visibleColumns'])
const keyword = ref('')
const filteredColumns = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) return props.columns
  return props.columns.filter(column => String(column.label || '').toLowerCase().includes(normalized))
})
const visibleCount = computed(() => props.columns.filter(isVisible).length)

function isVisible(column) {
  return Boolean(column.required || props.visibleColumns.includes(column.key))
}

function toggle(key, checked) {
  emit('update:visibleColumns', toggleWorkspaceFieldVisibility(props.columns, props.visibleColumns, key, checked))
}
</script>

<style scoped>
.field-visibility {
  display: grid;
  gap: 8px;
}

.field-list-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.field-list-header > span,
.empty-state {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.field-list {
  max-height: 330px;
  overflow: auto;
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
}

.field-row {
  display: grid;
  grid-template-columns: 28px 34px minmax(0, 1fr) auto;
  align-items: center;
  min-height: 40px;
  padding: 0 10px;
  border-bottom: 1px solid var(--dhic-border-soft);
  cursor: pointer;
}

.field-row:last-child {
  border-bottom: 0;
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

.empty-state {
  padding: 20px;
  text-align: center;
}
</style>
