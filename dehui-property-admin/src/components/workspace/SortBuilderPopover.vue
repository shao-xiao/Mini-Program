<template>
  <el-popover v-if="options.length > 0 || sorts.length > 0" placement="bottom-start" width="min(560px, calc(100vw - 48px))" trigger="click" popper-class="dhic-view-popper">
    <template #reference>
      <el-button class="workspace-toolbar-button" :class="{ active: sorts.length > 0 }" :icon="Sort">
        排序
        <span v-if="sorts.length > 0" class="workspace-button-count">{{ sorts.length }}</span>
      </el-button>
    </template>
    <div class="builder-panel">
      <div class="builder-heading">设置排序条件</div>
      <div v-for="(sort, index) in sorts" :key="`${sort.key || 'sort'}-${index}`" class="builder-row">
        <el-select :model-value="sort.key" filterable placeholder="选择字段" @change="value => updateKey(index, value)">
          <el-option
            v-for="column in options"
            :key="column.key"
            :label="column.label"
            :value="column.key"
            :disabled="isWorkspaceFieldUsed(sorts, column.key, index)"
          />
        </el-select>
        <el-segmented :model-value="sort.order" :options="orderOptions" @change="value => update(index, { order: value })" />
        <el-tag size="small" effect="plain" :type="scopeType(sort)">{{ scopeLabel(sort) }}</el-tag>
        <span class="row-actions">
          <el-button text circle :icon="Top" :disabled="index === 0" :aria-label="`上移第 ${index + 1} 个排序条件`" @click="move(index, -1)" />
          <el-button text circle :icon="Bottom" :disabled="index === sorts.length - 1" :aria-label="`下移第 ${index + 1} 个排序条件`" @click="move(index, 1)" />
          <el-button text circle :icon="Close" :aria-label="`删除第 ${index + 1} 个排序条件`" @click="remove(index)" />
        </span>
      </div>
      <el-button link type="primary" :icon="Plus" :disabled="!canAdd" @click="add">添加排序</el-button>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { Bottom, Close, Plus, Sort, Top } from '@element-plus/icons-vue'
import {
  appendUniqueWorkspaceCondition,
  availableWorkspaceFields,
  isWorkspaceFieldUsed,
  moveWorkspaceCondition,
  removeWorkspaceCondition,
  resolveWorkspaceQueryCapability,
  updateWorkspaceCondition
} from '../../utils/workspaceToolbar.js'
import { useWorkspaceToolbar } from './workspaceToolbarContext.js'

defineOptions({ name: 'SortBuilderPopover' })

const controller = useWorkspaceToolbar('SortBuilderPopover')
const orderOptions = [{ label: '正序', value: 'ascending' }, { label: '倒序', value: 'descending' }]
const dataMode = computed(() => controller.query.dataMode || 'client')
const rawOptions = computed(() => controller.query.sortOptions || [])
const options = computed(() => availableWorkspaceFields(rawOptions.value, 'sort', dataMode.value))
const sorts = computed(() => {
  if ((controller.query.sorts || []).length > 0) return controller.query.sorts
  if (controller.query.sortKey && controller.query.sortOrder) return [{ key: controller.query.sortKey, order: controller.query.sortOrder }]
  return []
})
const canAdd = computed(() => sorts.value.length < options.value.length)

function add() {
  controller.query.updateSorts(appendUniqueWorkspaceCondition(sorts.value, options.value, {
    capability: 'sort',
    dataMode: dataMode.value,
    defaults: { order: 'ascending' }
  }))
}

function update(index, patch) {
  controller.query.updateSorts(updateWorkspaceCondition(sorts.value, index, patch))
}

function updateKey(index, key) {
  if (isWorkspaceFieldUsed(sorts.value, key, index)) return
  update(index, { key })
}

function remove(index) {
  controller.query.updateSorts(removeWorkspaceCondition(sorts.value, index))
}

function move(index, direction) {
  controller.query.updateSorts(moveWorkspaceCondition(sorts.value, index, direction))
}

function capability(sort) {
  return resolveWorkspaceQueryCapability(options.value.find(field => field.key === sort.key), 'sort', dataMode.value)
}

function scopeLabel(sort) {
  const resolved = capability(sort)
  return resolved.mode === 'page' ? '仅当前页' : resolved.label
}

function scopeType(sort) {
  return capability(sort).mode === 'page' ? 'warning' : 'info'
}
</script>

<style scoped>
.builder-panel {
  display: grid;
  gap: 12px;
}

.builder-heading {
  color: var(--dhic-text);
  font-weight: 700;
}

.builder-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 170px auto 96px;
  align-items: center;
  gap: 8px;
}

.row-actions {
  display: inline-flex;
  justify-content: flex-end;
}

.row-actions :deep(.el-button) {
  width: 28px;
  height: 28px;
  margin: 0;
}
</style>
