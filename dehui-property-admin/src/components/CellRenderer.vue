<template>
  <span v-if="isEmpty" class="cell-empty">-</span>

  <StatusDisplay
    v-else-if="type === 'select' || type === 'status'"
    :value="rawValue"
    :label="singleOption.label"
    :tone="singleOption.tone"
    :show-dot="type === 'status'"
  />

  <span v-else-if="type === 'multi-select'" class="cell-tags">
    <el-tag v-for="option in multipleOptions" :key="String(option.value)" size="small" effect="light" :type="option.tone">
      {{ option.label }}
    </el-tag>
  </span>

  <span v-else-if="type === 'user'" class="cell-users">
    <span v-for="user in users" :key="user.id" class="cell-user">
      <el-avatar :size="22" :src="user.avatarUrl || undefined">{{ user.name.slice(0, 1) }}</el-avatar>
      <span>{{ user.name }}</span>
    </span>
  </span>

  <CustomFieldValueDisplay
    v-else-if="type === 'image' || type === 'attachment'"
    :field="field"
    :value="rawValue"
    mode="list"
  />

  <span v-else-if="type === 'relation'" class="cell-relations">
    <el-tag v-for="relation in relations" :key="String(relation.id)" size="small" effect="plain" class="relation-tag">
      {{ relation.label }}
    </el-tag>
  </span>

  <a v-else-if="type === 'url' && linkUrl" :href="linkUrl" target="_blank" rel="noopener noreferrer">
    {{ displayValue }}
  </a>

  <a v-else-if="type === 'email'" :href="`mailto:${rawValue}`">{{ displayValue }}</a>

  <span v-else class="cell-value" :class="{ 'is-formula': type === 'formula' }">{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { normalizeFieldType } from '../config/fieldTypes'
import CustomFieldValueDisplay from './workspace/CustomFieldValueDisplay.vue'
import StatusDisplay from './StatusDisplay.vue'
import {
  formatCellValue,
  normalizeCellRelations,
  normalizeCellUsers,
  resolveCellOption,
  resolveCellValue,
  safeCellUrl
} from '../utils/cellRenderer'

const fallbackOptionLabels = Object.freeze({
  ACTIVE: '启用',
  ENABLED: '启用',
  DISABLED: '停用',
  INACTIVE: '停用',
  NORMAL: '正常',
  ABNORMAL: '异常',
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  BOUND: '已绑定',
  UNBOUND: '未绑定',
  REVERIFY_REQUIRED: '待重新认证',
  NOT_APPLICABLE: '不适用',
  RESPONSIBLE: '负责人',
  EMPLOYEE: '员工',
  PUBLIC: '普通用户'
})

defineOptions({ name: 'CellRenderer' })

const props = defineProps({
  field: { type: Object, required: true },
  row: { type: Object, required: true },
  optionMap: { type: Map, default: null }
})

const type = computed(() => normalizeFieldType(props.field.type))
const rawValue = computed(() => resolveCellValue(props.row, props.field))
const isEmpty = computed(() => {
  const value = rawValue.value
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
})
const displayValue = computed(() => formatCellValue(rawValue.value, props.field))
const singleOption = computed(() => {
  const option = resolveCellOption(rawValue.value, props.field, props.optionMap)
  const key = String(rawValue.value ?? '').trim().toUpperCase()
  const rawLabel = String(option?.label ?? '').trim()
  if (!fallbackOptionLabels[key] || (rawLabel && rawLabel !== key)) return option
  return { ...option, label: fallbackOptionLabels[key] }
})
const multipleOptions = computed(() => {
  const values = Array.isArray(rawValue.value) ? rawValue.value : [rawValue.value]
  return values
    .filter(value => value !== undefined && value !== null && value !== '')
    .map(value => resolveCellOption(value, props.field, props.optionMap))
})
const users = computed(() => normalizeCellUsers(rawValue.value))
const relations = computed(() => normalizeCellRelations(rawValue.value, props.field))
const linkUrl = computed(() => safeCellUrl(rawValue.value))
</script>

<style scoped>
.cell-empty {
  color: #a0a8b5;
}

.cell-value {
  color: inherit;
}

.cell-value.is-formula {
  color: #49566a;
  font-variant-numeric: tabular-nums;
}

.cell-tags,
.cell-users,
.cell-attachments,
.cell-relations {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

.cell-status-tag {
  gap: 5px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #87909f;
}

.status-dot.tone-success {
  background: #16a36a;
}

.status-dot.tone-warning {
  background: #d99100;
}

.status-dot.tone-primary {
  background: #2563eb;
}

.status-dot.tone-danger {
  background: #d93025;
}

.relation-tag {
  border-color: #cbdaf8;
  background: #f5f8ff;
  color: #315ea8;
}

.cell-user {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.cell-attachments a,
a {
  color: #2563eb;
  text-decoration: none;
}

.cell-attachments a:hover,
a:hover {
  text-decoration: underline;
}
</style>
