<template>
  <el-popover v-if="options.length > 0 || groups.length > 0" placement="bottom-start" width="min(560px, calc(100vw - 48px))" trigger="click" popper-class="dhic-view-popper">
    <template #reference>
      <el-button class="workspace-toolbar-button" :class="{ active: groups.length > 0 }" :icon="Operation">
        分组
        <span v-if="groups.length > 0" class="workspace-button-count">{{ groups.length }}</span>
      </el-button>
    </template>
    <div class="builder-panel">
      <div class="builder-heading">设置分组条件</div>
      <div v-for="(group, index) in groups" :key="`${group.key || 'group'}-${index}`" class="builder-row">
        <el-select :model-value="group.key" filterable placeholder="选择字段" @change="value => updateKey(index, value)">
          <el-option
            v-for="column in options"
            :key="column.key"
            :label="column.label"
            :value="column.key"
            :disabled="isWorkspaceFieldUsed(groups, column.key, index)"
          />
        </el-select>
        <el-segmented :model-value="group.order" :options="orderOptions" @change="value => update(index, { order: value })" />
        <el-tooltip :content="scopeDescription(group)" placement="top">
          <el-tag size="small" effect="plain" :type="scopeType(group)">{{ scopeLabel(group) }}</el-tag>
        </el-tooltip>
        <span class="row-actions">
          <el-button text circle :icon="Top" :disabled="index === 0" :aria-label="`上移第 ${index + 1} 个分组条件`" @click="move(index, -1)" />
          <el-button text circle :icon="Bottom" :disabled="index === groups.length - 1" :aria-label="`下移第 ${index + 1} 个分组条件`" @click="move(index, 1)" />
          <el-button text circle :icon="Close" :aria-label="`删除第 ${index + 1} 个分组条件`" @click="remove(index)" />
        </span>
      </div>
      <el-button link type="primary" :icon="Plus" :disabled="!canAdd" @click="add">添加分组</el-button>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { Bottom, Close, Operation, Plus, Top } from '@element-plus/icons-vue'
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

defineOptions({ name: 'GroupBuilderPopover' })

const controller = useWorkspaceToolbar('GroupBuilderPopover')
const orderOptions = [{ label: '正序', value: 'ascending' }, { label: '倒序', value: 'descending' }]
const dataMode = computed(() => controller.query.dataMode || 'client')
const rawOptions = computed(() => controller.query.groupOptions || [])
const options = computed(() => availableWorkspaceFields(rawOptions.value, 'group', dataMode.value))
const groups = computed(() => {
  if ((controller.query.groups || []).length > 0) return controller.query.groups
  if (controller.query.groupKey) return [{ key: controller.query.groupKey, order: 'ascending' }]
  return []
})
const canAdd = computed(() => groups.value.length < options.value.length)

function add() {
  controller.query.updateGroups(appendUniqueWorkspaceCondition(groups.value, options.value, {
    capability: 'group',
    dataMode: dataMode.value,
    defaults: { order: 'ascending' }
  }))
}

function update(index, patch) {
  controller.query.updateGroups(updateWorkspaceCondition(groups.value, index, patch))
}

function updateKey(index, key) {
  if (isWorkspaceFieldUsed(groups.value, key, index)) return
  update(index, { key })
}

function remove(index) {
  controller.query.updateGroups(removeWorkspaceCondition(groups.value, index))
}

function move(index, direction) {
  controller.query.updateGroups(moveWorkspaceCondition(groups.value, index, direction))
}

function capability(group) {
  return resolveWorkspaceQueryCapability(options.value.find(field => field.key === group.key), 'group', dataMode.value)
}

function scopeLabel(group) {
  const resolved = capability(group)
  return resolved.mode === 'page' ? '仅当前页' : resolved.label
}

function scopeDescription(group) {
  return capability(group).mode === 'page' ? '分组仅基于当前已加载页面记录' : `作用范围：${scopeLabel(group)}`
}

function scopeType(group) {
  return capability(group).mode === 'page' ? 'warning' : 'info'
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
