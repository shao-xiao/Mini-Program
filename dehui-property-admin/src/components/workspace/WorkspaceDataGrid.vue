<template>
  <div class="workspace-data-grid" role="region" :aria-label="ariaLabel">
    <div v-if="errorText && rows.length" class="workspace-grid-error-banner" role="alert">
      <span>{{ errorText }}</span>
      <el-button v-if="retryable" link type="primary" @click="$emit('retry')">{{ retryLabel }}</el-button>
    </div>
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      :height="height || undefined"
      :row-key="resolvedRowKey"
      :empty-text="emptyState.text"
      :default-sort="defaultSort"
      :span-method="resolveSpanMethod"
      :row-class-name="resolveRowClassName"
      :class="['dhic-multitable', densityClass, tableClass]"
      :aria-label="ariaLabel"
      scrollbar-always-on
      border
      style="width: 100%"
      @selection-change="handleSelectionChange"
      @header-dragend="handleColumnResize"
      @sort-change="handleSortChange"
      @row-click="(...args) => $emit('row-click', ...args)"
      @cell-click="(...args) => $emit('cell-click', ...args)"
    >
      <el-table-column
        v-if="showSelection"
        type="selection"
        :width="selectionWidth"
        fixed="left"
        :selectable="isSelectable"
        reserve-selection
      />

      <el-table-column
        v-if="hasSelectionExtra"
        :width="selectionExtraWidth"
        fixed="left"
        align="center"
      >
        <template #default="scope">
          <slot name="selection-extra" v-bind="scope" :selected="selectedKeySet.has(resolvedRowKey(scope.row))" />
        </template>
      </el-table-column>

      <el-table-column
        v-if="showIndex"
        label="序号"
        :width="indexWidth"
        :fixed="indexFixed"
        align="center"
      >
        <template #default="{ row, $index }">
          <template v-if="isGroupRow(row)">
            <slot name="group-header" :row="row">
              <div class="group-row">
                <span>{{ row.label }}</span>
                <em>{{ row.count }} 条</em>
                <slot name="group-summary" :row="row" />
              </div>
            </slot>
          </template>
          <span v-else>{{ displayIndex(row, $index) }}</span>
        </template>
      </el-table-column>

      <el-table-column
        v-for="(field, fieldIndex) in gridColumns"
        :key="field.key"
        :column-key="field.key"
        :prop="field.key"
        :label="field.label"
        :width="resolveColumnWidth(field)"
        :min-width="field.minWidth || 120"
        :fixed="resolveColumnFixed(field)"
        :sortable="resolveColumnSortable(field)"
        :sort-orders="['ascending', 'descending', null]"
        :resizable="field.resizable !== false"
        :align="field.align"
        :header-align="field.headerAlign"
        :show-overflow-tooltip="resolveOverflowTooltip(field)"
      >
        <template v-if="hasSlot(`header-${field.key}`)" #header="scope">
          <slot :name="`header-${field.key}`" v-bind="scope" :field="field" />
        </template>
        <template #default="{ row, $index }">
          <template v-if="isGroupRow(row) && !showIndex && fieldIndex === 0">
            <slot name="group-header" :row="row">
              <div class="group-row">
                <span>{{ row.label }}</span>
                <em>{{ row.count }} 条</em>
                <slot name="group-summary" :row="row" />
              </div>
            </slot>
          </template>
          <slot
            v-else-if="hasSlot(`cell-${field.key}`)"
            :name="`cell-${field.key}`"
            :row="row"
            :field="field"
            :row-index="$index"
          />
          <div
            v-else
            class="workspace-grid-cell"
            :class="{
              'is-editable': isCellEditable(row, field),
              'is-active': isActiveEditCell(row, field),
              'is-saving': isCellSaving(row, field)
            }"
            :tabindex="cellTabIndex(row, field)"
            :role="isCellEditable(row, field) && !isActiveEditCell(row, field) ? 'button' : undefined"
            :aria-label="cellAriaLabel(row, field)"
            :aria-keyshortcuts="isCellEditable(row, field) ? 'Enter F2 Escape' : undefined"
            :aria-busy="isCellSaving(row, field) || undefined"
            @focus="focusedCellKey = cellIdentity(row, field)"
            @dblclick.stop="activateEditCell(row, field)"
            @keydown="handleCellKeydown($event, row, field)"
          >
            <CustomFieldCellEditor
              v-if="isActiveEditCell(row, field) && field.custom"
              :field="field"
              :row="row"
              :editable="true"
              :saving="isCellSaving(row, field)"
              @save="(value, complete) => handleCellSave(row, field, value, complete)"
              @cancel="handleCellCancel(row, field)"
              @error="error => handleCellError(row, field, error)"
            />
            <CellEditor
              v-else-if="isActiveEditCell(row, field)"
              :field="field"
              :row="row"
              :editable="true"
              :saving="isCellSaving(row, field)"
              @save="(value, complete) => handleCellSave(row, field, value, complete)"
              @cancel="handleCellCancel(row, field)"
              @error="error => handleCellError(row, field, error)"
            />
            <template v-else>
              <CustomFieldCellRenderer
                v-if="field.custom"
                :field="field"
                :row="row"
                :option-map="field.optionMap"
              />
              <CellRenderer v-else :field="field" :row="row" :option-map="field.optionMap" />
              <el-icon v-if="isCellEditable(row, field)" class="workspace-cell-edit-icon" aria-hidden="true">
                <EditPen />
              </el-icon>
              <el-icon v-if="isCellSaving(row, field)" class="workspace-cell-saving" aria-label="正在保存">
                <Loading />
              </el-icon>
            </template>
            <slot
              v-if="hasSlot(`cell-extra-${field.key}`)"
              :name="`cell-extra-${field.key}`"
              :row="row"
              :field="field"
              :row-index="$index"
            />
          </div>
        </template>
      </el-table-column>

      <el-table-column
        v-if="hasOperations"
        :label="operationColumn.label || '操作'"
        :width="operationColumn.width"
        :min-width="operationColumn.minWidth"
        :fixed="operationColumn.fixed ?? 'right'"
        :align="operationColumn.align || 'center'"
        :header-align="operationColumn.headerAlign || operationColumn.align || 'center'"
      >
        <template #default="{ row, $index }">
          <slot v-if="!isGroupRow(row)" name="operations" :row="row" :row-index="$index" />
        </template>
      </el-table-column>

      <template v-if="hasSlot('append')" #append>
        <slot name="append" />
      </template>

      <template #empty>
        <slot name="empty" :state="emptyState">
          <div
            class="workspace-grid-empty"
            :class="`is-${emptyState.kind}`"
            :role="emptyState.kind === 'error' ? 'alert' : 'status'"
          >
            <span>{{ emptyState.text }}</span>
            <el-button
              v-if="emptyState.kind === 'error' && retryable"
              type="primary"
              plain
              @click="$emit('retry')"
            >
              {{ retryLabel }}
            </el-button>
          </div>
        </slot>
      </template>
    </el-table>
    <FloatingTableScrollbar :table="tableRef" :rows="rows" :columns="gridColumns" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, useSlots, watch } from 'vue'
import FloatingTableScrollbar from './FloatingTableScrollbar.vue'
import { EditPen, Loading } from '@element-plus/icons-vue'
import CellEditor from '../CellEditor.vue'
import CellRenderer from '../CellRenderer.vue'
import CustomFieldCellEditor from './CustomFieldCellEditor.vue'
import CustomFieldCellRenderer from './CustomFieldCellRenderer.vue'
import { useWorkspaceGrid } from './workspaceGridContext.js'
import {
  buildWorkspaceDisplayIndexMap,
  isWorkspaceGroupRow,
  normalizeWorkspaceGridColumns,
  reconcileWorkspaceSelectionKeys,
  workspaceGridDensityClass,
  workspaceGridEmptyState,
  workspaceGridGroupSpan,
  workspaceGridRowKey,
  workspaceGridSelectable,
  workspaceGridSortIntent
} from '../../utils/workspaceDataGrid.js'

defineOptions({ name: 'WorkspaceDataGrid' })

const props = defineProps({
  rows: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  errorText: { type: String, default: '' },
  retryable: { type: Boolean, default: false },
  retryLabel: { type: String, default: '重试' },
  rowKey: { type: [String, Function], default: 'id' },
  selectedRowKeys: { type: Array, default: () => [] },
  selectable: { type: Function, default: () => true },
  showSelection: { type: Boolean, default: false },
  showIndex: { type: Boolean, default: true },
  indexMethod: { type: Function, default: null },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  selectionWidth: { type: [Number, String], default: 48 },
  selectionExtraWidth: { type: [Number, String], default: 48 },
  indexWidth: { type: [Number, String], default: 72 },
  indexFixed: { type: [Boolean, String], default: false },
  density: { type: String, default: 'standard' },
  emptyText: { type: String, default: '暂无数据' },
  sortState: { type: Object, default: () => ({}) },
  dataMode: { type: String, default: 'client' },
  height: { type: [Number, String], default: '' },
  tableClass: { type: [String, Array, Object], default: '' },
  rowClassName: { type: [String, Function], default: '' },
  columnWidth: { type: Function, default: null },
  columnFixed: { type: Function, default: null },
  isColumnSortable: { type: Function, default: null },
  showOverflowTooltip: { type: [Boolean, Function], default: false },
  canEditCell: { type: Function, default: () => false },
  cellSavingKey: { type: [String, Number], default: '' },
  cellKey: { type: Function, default: null },
  editContextKey: { type: [String, Number], default: '' },
  operationColumn: { type: Object, default: () => ({}) },
  ariaLabel: { type: String, default: '数据表格' }
})

const emit = defineEmits([
  'update:selectedRowKeys',
  'selection-change',
  'sort-change',
  'column-resize',
  'row-click',
  'cell-click',
  'cell-save',
  'custom-field-save',
  'cell-cancel',
  'cell-error',
  'retry'
])

const slots = useSlots()
const workspaceGrid = useWorkspaceGrid()
const tableRef = ref(null)
const synchronizingSelection = ref(false)
const selectionSyncRevision = ref(0)
const activeEditCell = ref('')
const focusedCellKey = ref('')
const gridColumns = computed(() => normalizeWorkspaceGridColumns(props.columns))
const displayIndexMap = computed(() => buildWorkspaceDisplayIndexMap(
  props.rows,
  props.rowKey,
  props.currentPage,
  props.pageSize
))
const densityClass = computed(() => workspaceGridDensityClass(props.density))
const emptyState = computed(() => workspaceGridEmptyState({
  loading: props.loading,
  errorText: props.errorText,
  emptyText: props.emptyText
}))
const defaultSort = computed(() => props.sortState?.key
  ? { prop: props.sortState.key, order: props.sortState.order }
  : {})
const hasOperations = computed(() => hasSlot('operations'))
const hasSelectionExtra = computed(() => hasSlot('selection-extra'))
const selectedKeySet = computed(() => new Set(props.selectedRowKeys))
const firstEditableCellKey = computed(() => {
  for (const row of props.rows) {
    for (const field of gridColumns.value) {
      if (isCellEditable(row, field)) return cellIdentity(row, field)
    }
  }
  return ''
})
const groupColumnIndex = computed(() => {
  if (props.showIndex) return Number(props.showSelection) + Number(hasSelectionExtra.value)
  return Number(props.showSelection) + Number(hasSelectionExtra.value)
})
const tableColumnCount = computed(() => (
  Number(props.showSelection)
  + Number(hasSelectionExtra.value)
  + Number(props.showIndex)
  + gridColumns.value.length
  + Number(hasOperations.value)
))

watch(
  [() => props.rows, () => props.selectedRowKeys],
  () => syncTableSelection(),
  { immediate: true, flush: 'post' }
)

watch(
  [() => props.rows, () => props.currentPage, () => props.editContextKey, () => props.loading, () => props.columns],
  () => cancelActiveEdit(),
  { flush: 'sync' }
)

onBeforeUnmount(() => {
  selectionSyncRevision.value += 1
})

function hasSlot(name) {
  return typeof slots[name] === 'function'
}

function isGroupRow(row) {
  return isWorkspaceGroupRow(row)
}

function resolvedRowKey(row) {
  return workspaceGridRowKey(row, props.rowKey)
}

function isSelectable(row, rowIndex) {
  return resolvedRowKey(row) !== null && workspaceGridSelectable(row, props.selectable, rowIndex)
}

function displayIndex(row, rowIndex) {
  if (typeof props.indexMethod === 'function') return props.indexMethod(row, rowIndex)
  return displayIndexMap.value.get(resolvedRowKey(row)) ?? ''
}

function resolveColumnWidth(field) {
  return props.columnWidth ? props.columnWidth(field.key, field.width) : field.width
}

function resolveColumnFixed(field) {
  return props.columnFixed ? props.columnFixed(field.key, field.fixed || false) : (field.fixed || false)
}

function resolveColumnSortable(field) {
  const sortable = props.isColumnSortable ? props.isColumnSortable(field.key) : Boolean(field.sortable)
  return sortable ? 'custom' : false
}

function resolveOverflowTooltip(field) {
  return typeof props.showOverflowTooltip === 'function'
    ? props.showOverflowTooltip(field)
    : props.showOverflowTooltip
}

function resolveSpanMethod(scope) {
  return workspaceGridGroupSpan({
    ...scope,
    columnCount: tableColumnCount.value,
    groupColumnIndex: groupColumnIndex.value
  })
}

function resolveRowClassName(scope) {
  const classes = []
  if (isGroupRow(scope.row)) classes.push('view-group-row')
  const custom = typeof props.rowClassName === 'function'
    ? props.rowClassName(scope)
    : props.rowClassName
  if (custom) classes.push(custom)
  return [...new Set(classes)].join(' ')
}

function handleColumnResize(newWidth, oldWidth, column) {
  emit('column-resize', newWidth, oldWidth, column)
}

function handleSortChange(payload) {
  const intent = workspaceGridSortIntent(payload, gridColumns.value, props.dataMode)
  emit('sort-change', { ...payload, ...intent })
}

function handleSelectionChange(rows) {
  if (synchronizingSelection.value) return
  const selectedRows = (Array.isArray(rows) ? rows : []).filter(isSelectable)
  const keys = reconcileWorkspaceSelectionKeys(selectedRows, selectedRows.map(resolvedRowKey), props.rowKey, props.selectable)
  emit('update:selectedRowKeys', keys)
  emit('selection-change', selectedRows)
}

async function syncTableSelection() {
  if (!props.showSelection) return
  const revision = selectionSyncRevision.value + 1
  selectionSyncRevision.value = revision
  const rows = props.rows
  const selectedRowKeys = [...props.selectedRowKeys]
  await nextTick()
  if (revision !== selectionSyncRevision.value || rows !== props.rows) return
  const table = tableRef.value
  if (!table) return

  const reconciledKeys = reconcileWorkspaceSelectionKeys(
    rows,
    selectedRowKeys,
    props.rowKey,
    props.selectable
  )
  const validRows = new Map()
  rows.forEach(row => {
    const key = resolvedRowKey(row)
    if (key !== null && !isGroupRow(row)) validRows.set(key, row)
  })

  synchronizingSelection.value = true
  table.clearSelection?.()
  reconciledKeys.forEach(key => table.toggleRowSelection?.(validRows.get(key), true))
  await nextTick()
  if (revision === selectionSyncRevision.value) synchronizingSelection.value = false
}

function isCellEditable(row, field) {
  if (isGroupRow(row) || field.editable !== true) return false
  if (isManagedCustomField(field)) return workspaceGrid.customFields.canEditCell(row, field) === true
  return field.editable === true && props.canEditCell(row, field) === true
}

function cellIdentity(row, field) {
  const rowIdentity = resolvedRowKey(row)
  if (rowIdentity === null || !field?.key) return ''
  return `${typeof rowIdentity}:${String(rowIdentity)}::${field.key}`
}

function isActiveEditCell(row, field) {
  return activeEditCell.value !== '' && activeEditCell.value === cellIdentity(row, field)
}

function cellTabIndex(row, field) {
  if (!isCellEditable(row, field)) return undefined
  const identity = cellIdentity(row, field)
  return identity === (focusedCellKey.value || firstEditableCellKey.value) ? 0 : -1
}

function cellAriaLabel(row, field) {
  if (!isCellEditable(row, field) || isActiveEditCell(row, field)) return undefined
  return `${field.label || field.key}，按 Enter 或 F2 编辑，按 Escape 取消`
}

function activateEditCell(row, field) {
  if (!isCellEditable(row, field) || isCellSaving(row, field)) return
  const identity = cellIdentity(row, field)
  focusedCellKey.value = identity
  activeEditCell.value = identity
}

function handleCellKeydown(event, row, field) {
  if (!isCellEditable(row, field)) return
  if (event.key === 'Enter' || event.key === 'F2') {
    event.preventDefault()
    activateEditCell(row, field)
  } else if (event.key === 'Escape' && isActiveEditCell(row, field)) {
    event.preventDefault()
    handleCellCancel(row, field)
  }
}

function cancelActiveEdit() {
  activeEditCell.value = ''
}

function isCellSaving(row, field) {
  if (isManagedCustomField(field)) return workspaceGrid.customFields.isSaving(row, field) === true
  if (!props.cellKey || props.cellSavingKey === '') return false
  return props.cellSavingKey === props.cellKey(row, field)
}

async function handleCellSave(row, field, value, editorComplete) {
  let completed = false
  const complete = result => {
    if (completed) return
    completed = true
    const normalized = typeof result === 'object' && result !== null ? result : { success: result !== false }
    editorComplete?.(normalized)
    if (normalized.success !== false) activeEditCell.value = ''
  }
  if (isManagedCustomField(field)) {
    const result = await workspaceGrid.customFields.saveCell(row, field, value)
    complete(result)
    if (result?.success !== false) emit('custom-field-save', { row, field, value, result })
    else handleCellError(row, field, new Error(result?.message || '自定义字段保存失败'))
    return
  }
  emit('cell-save', { row, field, value, complete })
}

function handleCellCancel(row, field) {
  activeEditCell.value = ''
  emit('cell-cancel', { row, field })
}

function handleCellError(row, field, error) {
  emit('cell-error', { row, field, error })
}

function isManagedCustomField(field) {
  return field?.platformCustom === true && workspaceGrid.customFields.enabled === true
}

function clearSelection() {
  tableRef.value?.clearSelection?.()
  emit('update:selectedRowKeys', [])
  emit('selection-change', [])
}

function toggleRowSelection(row, selected) {
  if (!isSelectable(row)) return
  tableRef.value?.toggleRowSelection?.(row, selected)
}

defineExpose({
  clearSelection,
  toggleRowSelection,
  tableRef
})
</script>

<style scoped>
.workspace-data-grid {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  scrollbar-gutter: stable;
}

.workspace-data-grid :deep(.el-table) {
  min-width: 100%;
}

.workspace-data-grid :deep(.el-table__fixed-right) {
  box-shadow: -8px 0 16px rgba(32, 33, 36, 0.045);
}

.workspace-data-grid :deep(.el-table__fixed-right .cell) {
  white-space: nowrap;
}

.workspace-grid-error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
  font-size: 13px;
}

.workspace-grid-empty {
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  color: var(--dhic-text-muted);
  text-align: center;
  flex-direction: column;
  gap: 10px;
}

.workspace-grid-cell {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  min-width: 0;
  overflow: hidden;
  outline: none;
}

.workspace-grid-cell > :deep(*) {
  min-width: 0;
}

.workspace-grid-cell.is-editable {
  cursor: text;
}

.workspace-grid-cell.is-editable:focus-visible {
  border-radius: 3px;
  box-shadow: inset 0 0 0 2px var(--el-color-primary-light-5);
}

.workspace-cell-edit-icon {
  flex: none;
  opacity: 0;
  color: var(--dhic-text-muted);
  transition: opacity 0.15s ease;
}

.workspace-grid-cell.is-editable:hover .workspace-cell-edit-icon,
.workspace-grid-cell.is-editable:focus-visible .workspace-cell-edit-icon {
  opacity: 1;
}

.workspace-cell-saving {
  flex: none;
  color: var(--el-color-primary);
  animation: workspace-grid-rotating 1.2s linear infinite;
}

@keyframes workspace-grid-rotating {
  to { transform: rotate(360deg); }
}

.workspace-grid-empty.is-error {
  color: var(--dhic-primary);
}
</style>
