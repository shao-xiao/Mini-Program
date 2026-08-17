<template>
  <el-popover
    v-if="selectableColumns.length > 0 || filters.length > 0 || filterItems.length > 0"
    placement="bottom-start"
    width="min(680px, calc(100vw - 48px))"
    trigger="click"
    popper-class="dhic-view-popper"
  >
    <template #reference>
      <el-button class="workspace-toolbar-button" :class="{ active: activeFilterCount > 0 }" :icon="Filter">
        筛选
        <span v-if="activeFilterCount > 0" class="workspace-button-count">{{ activeFilterCount }}</span>
      </el-button>
    </template>

    <div class="workspace-builder-panel">
      <div class="builder-header">
        <div>
          <strong>设置筛选条件</strong>
          <span>条件范围由字段查询能力决定</span>
        </div>
        <el-segmented
          v-if="logicOptions.length > 1"
          :model-value="filterLogic"
          :options="logicOptions"
          @change="controller.query.updateFilterLogic"
        />
        <el-tag v-else size="small" effect="plain">满足全部</el-tag>
      </div>

      <div v-if="filters.length === 0 && filterItems.length === 0" class="builder-empty">暂无筛选条件。</div>
      <div v-for="(filter, index) in filters" :key="`${filter.key || 'filter'}-${index}`" class="filter-row">
        <el-select :model-value="filter.key" filterable placeholder="选择字段" @change="value => updateFilter(index, 'key', value)">
          <el-option v-for="column in selectableColumns" :key="column.key" :label="column.label" :value="column.key" />
        </el-select>
        <el-select :model-value="filter.operator" placeholder="条件" @change="value => updateFilter(index, 'operator', value)">
          <el-option v-for="operator in operatorsForFilter(filter)" :key="operator.value" :label="operator.label" :value="operator.value" />
        </el-select>
        <template v-if="requiresFilterValue(filter)">
          <el-select
            v-if="isOptionFilter(filter)"
            :model-value="filter.value"
            :multiple="isMultiOptionFilter(filter)"
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择"
            @change="value => updateFilter(index, 'value', value)"
          >
            <el-option v-for="option in filterValueOptions(filter)" :key="String(option.value)" :label="option.label" :value="option.value" />
          </el-select>
          <div v-else-if="isNumberRangeFilter(filter)" class="range-inputs">
            <el-input-number :model-value="rangeFilterValue(filter, 0)" controls-position="right" placeholder="最小值" @change="value => updateRangeValue(index, 0, value)" />
            <span>至</span>
            <el-input-number :model-value="rangeFilterValue(filter, 1)" controls-position="right" placeholder="最大值" @change="value => updateRangeValue(index, 1, value)" />
          </div>
          <el-input-number
            v-else-if="isNumberFilter(filter)"
            :model-value="filter.value"
            controls-position="right"
            placeholder="请输入数值"
            style="width: 100%"
            @change="value => updateFilter(index, 'value', value)"
          />
          <el-date-picker
            v-else-if="isDateRangeFilter(filter)"
            :model-value="filter.value"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
            @change="value => updateFilter(index, 'value', value || [])"
          />
          <el-date-picker
            v-else-if="isDateFilter(filter)"
            :model-value="filter.value"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            style="width: 100%"
            @change="value => updateFilter(index, 'value', value || '')"
          />
          <el-input v-else :model-value="filter.value" placeholder="请输入" @input="value => updateFilter(index, 'value', value)" />
        </template>
        <span v-else class="no-value">无需输入</span>
        <el-tooltip :content="capabilityFor(filter).reason || `作用范围：${capabilityFor(filter).label}`" placement="top">
          <el-tag size="small" effect="plain" :type="scopeType(capabilityFor(filter).mode)">
            {{ capabilityFor(filter).mode === 'page' ? '仅当前页' : capabilityFor(filter).label }}
          </el-tag>
        </el-tooltip>
        <el-button text circle :icon="Close" :aria-label="`删除第 ${index + 1} 个筛选条件`" @click="removeFilter(index)" />
      </div>

      <div v-for="item in filterItems" :key="item.key || item.label" class="readonly-filter">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <el-tag v-if="item.scopeLabel" size="small" effect="plain" :type="scopeType(item.scope)">{{ item.scopeLabel }}</el-tag>
      </div>
      <el-button link type="primary" :icon="Plus" :disabled="selectableColumns.length === 0" @click="addFilter">添加条件</el-button>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { Close, Filter, Plus } from '@element-plus/icons-vue'
import { isDateFieldType, isMultiValueFieldType, isNumericFieldType } from '../../config/fieldTypes.js'
import { defaultFilterOperator, emptyFilterValue, filterOperatorsForType } from '../../utils/tableViewPrefs.js'
import { availableWorkspaceFields, resolveWorkspaceQueryCapability } from '../../utils/workspaceToolbar.js'
import { useWorkspaceToolbar } from './workspaceToolbarContext.js'

defineOptions({ name: 'FilterBuilderPopover' })

const controller = useWorkspaceToolbar('FilterBuilderPopover')
const dataMode = computed(() => controller.query.dataMode || 'client')
const filters = computed(() => controller.query.filters || [])
const filterItems = computed(() => controller.query.filterItems || [])
const activeFilterCount = computed(() => controller.query.activeFilterCount || 0)
const filterLogic = computed(() => controller.query.filterLogic || 'and')
const logicOptions = computed(() => (controller.query.filterLogics || ['and']).map(value => ({
  label: value === 'or' ? '满足任一' : '满足全部',
  value
})))
const allColumns = computed(() => (controller.fields.columns || []).filter(column => !column.fixedOnly))
const candidateColumns = computed(() => {
  const configured = controller.query.filterOptions || []
  if (dataMode.value === 'server') return configured
  return configured.length > 0 ? configured : allColumns.value
})
const selectableColumns = computed(() => availableWorkspaceFields(candidateColumns.value, 'filter', dataMode.value))

function addFilter() {
  const column = selectableColumns.value[0]
  if (!column) return
  const operator = firstFilterOperator(column)
  controller.query.updateFilters([
    ...filters.value,
    { key: column.key, operator, value: emptyFilterValue(operator, column.type || 'text') }
  ])
}

function updateFilter(index, field, value) {
  const next = filters.value.map((filter, itemIndex) => {
    if (itemIndex !== index) return { ...filter }
    const nextFilter = { ...filter, [field]: value }
    if (field === 'key') {
      const column = filterColumn(nextFilter)
      nextFilter.operator = firstFilterOperator(column)
      nextFilter.value = emptyFilterValue(nextFilter.operator, column?.type || 'text')
    }
    if (field === 'operator') nextFilter.value = emptyFilterValue(value, filterColumn(nextFilter)?.type || 'text')
    return nextFilter
  })
  controller.query.updateFilters(next)
}

function removeFilter(index) {
  controller.query.updateFilters(filters.value.filter((_, itemIndex) => itemIndex !== index).map(item => ({ ...item })))
}

function filterColumn(filter) {
  return allColumns.value.find(column => column.key === filter?.key)
    || selectableColumns.value.find(column => column.key === filter?.key)
}

function capabilityFor(filter) {
  return resolveWorkspaceQueryCapability(filterColumn(filter), 'filter', dataMode.value)
}

function operatorsForFilter(filter) {
  const column = filterColumn(filter)
  const operators = filterOperatorsForType(column?.type || 'text')
  const allowed = capabilityFor(filter).operators
  return Array.isArray(allowed) ? operators.filter(operator => allowed.includes(operator.value)) : operators
}

function firstFilterOperator(column) {
  return operatorsForFilter({ key: column?.key })[0]?.value || defaultFilterOperator(column?.type || 'text')
}

function requiresFilterValue(filter) {
  return !['empty', 'notEmpty', 'today', 'thisWeek', 'thisMonth'].includes(filter.operator)
}

function isNumberFilter(filter) {
  return isNumericFieldType(filterColumn(filter)?.type) && filter.operator !== 'between'
}

function isNumberRangeFilter(filter) {
  return isNumericFieldType(filterColumn(filter)?.type) && filter.operator === 'between'
}

function isDateFilter(filter) {
  return isDateFieldType(filterColumn(filter)?.type) && ['equals', 'notEquals', 'before', 'after'].includes(filter.operator)
}

function isDateRangeFilter(filter) {
  return isDateFieldType(filterColumn(filter)?.type) && ['dateRange', 'between'].includes(filter.operator)
}

function rangeFilterValue(filter, index) {
  return Array.isArray(filter.value) ? filter.value[index] ?? null : null
}

function updateRangeValue(filterIndex, valueIndex, value) {
  const range = Array.isArray(filters.value[filterIndex]?.value) ? [...filters.value[filterIndex].value] : []
  range[valueIndex] = value
  updateFilter(filterIndex, 'value', range)
}

function filterValueOptions(filter) {
  const column = filterColumn(filter)
  const source = typeof column?.options === 'function' ? column.options() : column?.options
  return (Array.isArray(source) ? source : []).map(option => {
    if (option && typeof option === 'object') {
      const value = option.value ?? option.label ?? ''
      return { label: String(option.label ?? value), value }
    }
    return { label: String(option), value: option }
  }).filter(option => option.label)
}

function isOptionFilter(filter) {
  return filterValueOptions(filter).length > 0
}

function isMultiOptionFilter(filter) {
  return filter.operator === 'containsAny' || isMultiValueFieldType(filterColumn(filter)?.type)
}

function scopeType(mode) {
  return mode === 'page' ? 'warning' : (mode === 'disabled' ? 'danger' : 'info')
}
</script>

<style scoped>
.workspace-builder-panel {
  display: grid;
  gap: 14px;
}

.builder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.builder-header > div {
  display: grid;
  gap: 2px;
}

.builder-header strong {
  color: var(--dhic-text);
}

.builder-header span,
.builder-empty,
.no-value {
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.filter-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 112px minmax(160px, 1.3fr) auto 32px;
  align-items: center;
  gap: 10px;
}

.range-inputs {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.readonly-filter {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 30px;
  padding: 5px 8px;
  border: 1px solid var(--dhic-border-soft);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--dhic-text-muted);
  font-size: 12px;
}

.readonly-filter strong {
  overflow: hidden;
  color: var(--dhic-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1024px) {
  .filter-row {
    grid-template-columns: minmax(120px, 1fr) 104px minmax(150px, 1.2fr) auto 32px;
    gap: 8px;
  }
}
</style>
