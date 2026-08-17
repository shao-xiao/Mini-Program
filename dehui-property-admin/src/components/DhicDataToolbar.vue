<template>
  <div class="dhic-data-toolbar" role="toolbar" aria-label="数据视图工具栏">
    <div class="toolbar-left">
      <slot name="primary-action" />
      <FilterBuilderPopover />
      <SortBuilderPopover />
      <GroupBuilderPopover />
      <FieldManager />
      <RowDensityMenu />

      <span v-if="workspaceConfig?.importExport?.length || $slots['data-actions']" class="toolbar-data-actions">
        <ImportExportManager
          :workspace-config="workspaceConfig"
          :loading-action-ids="importExportLoadingActionIds"
          :disabled-action-ids="importExportDisabledActionIds"
          @action="$emit('workspace-action', $event)"
          @file="$emit('workspace-file', $event)"
        />
        <slot name="data-actions" />
      </span>
    </div>

    <div class="toolbar-right">
      <el-button v-if="hasActiveViewState" link type="danger" @click="$emit('reset-view')">重置视图</el-button>
      <slot name="right" />
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance } from 'vue'
import FieldManager from './FieldManager.vue'
import ImportExportManager from './ImportExportManager.vue'
import FilterBuilderPopover from './workspace/FilterBuilderPopover.vue'
import GroupBuilderPopover from './workspace/GroupBuilderPopover.vue'
import RowDensityMenu from './workspace/RowDensityMenu.vue'
import SortBuilderPopover from './workspace/SortBuilderPopover.vue'
import { provideWorkspaceToolbar } from './workspace/workspaceToolbarContext.js'
import { useWorkspaceCustomFields } from '../composables/workspace/useWorkspaceCustomFields.js'

defineOptions({ name: 'DhicDataToolbar' })

const props = defineProps({
  workspaceConfig: { type: Object, default: null },
  importExportLoadingActionIds: { type: Array, default: () => [] },
  importExportDisabledActionIds: { type: Array, default: () => [] },
  viewName: { type: String, default: '默认视图' },
  columns: { type: Array, default: () => [] },
  visibleColumns: { type: Array, default: () => [] },
  columnOrder: { type: Array, default: () => [] },
  fixedColumns: { type: Object, default: () => ({}) },
  customColumns: { type: Array, default: () => [] },
  allowCustomFields: { type: Boolean, default: false },
  allowColumnLayout: { type: Boolean, default: false },
  fieldConfigSaveable: { type: Boolean, default: false },
  fieldConfigDirty: { type: Boolean, default: false },
  filterOptions: { type: Array, default: () => [] },
  filters: { type: Array, default: () => [] },
  filterLogic: { type: String, default: 'and' },
  filterLogics: { type: Array, default: () => ['and', 'or'] },
  sortOptions: { type: Array, default: () => [] },
  sorts: { type: Array, default: () => [] },
  sortKey: { type: String, default: '' },
  sortOrder: { type: String, default: '' },
  sortCount: { type: Number, default: 0 },
  groupOptions: { type: Array, default: () => [] },
  groups: { type: Array, default: () => [] },
  groupKey: { type: String, default: '' },
  groupCount: { type: Number, default: 0 },
  rowDensity: { type: String, default: 'standard' },
  activeFilterCount: { type: Number, default: 0 },
  filterItems: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'reset-view',
  'update:visibleColumns',
  'update:columnOrder',
  'update:fixedColumns',
  'update:customColumns',
  'update:filters',
  'update:filterLogic',
  'update:sortKey',
  'update:sortOrder',
  'update:sorts',
  'update:groupKey',
  'update:groups',
  'update:rowDensity',
  'create:custom-column',
  'save-field-config',
  'workspace-action',
  'workspace-file'
])

const instance = getCurrentInstance()
const resolvedCustomFieldConfig = computed(() => {
  if (props.workspaceConfig?.customFields?.enabled) return props.workspaceConfig.customFields
  if (!props.allowCustomFields || !props.workspaceConfig?.id) return { enabled: false }
  return {
    enabled: true,
    workspaceKey: legacyCustomFieldWorkspaceKey(props.workspaceConfig.id),
    managePermission: 'custom-field:manage',
    valueUpdatePermission: props.workspaceConfig.permissions?.update || ''
  }
})
const workspaceCustomFields = useWorkspaceCustomFields(resolvedCustomFieldConfig.value)
const resolvedCustomColumns = computed(() => mergeFields(
  workspaceCustomFields.activeDefinitions.value,
  props.customColumns
))
const resolvedColumns = computed(() => mergeFields(props.columns, resolvedCustomColumns.value))

async function createManagedCustomField(payload) {
  const created = await workspaceCustomFields.create(payload)
  activateCustomField(created)
  return created
}

async function updateManagedCustomField({ fieldId, payload }) {
  return workspaceCustomFields.update(fieldId, payload)
}

async function changeManagedCustomFieldStatus({ fieldId, status }) {
  const updated = await workspaceCustomFields.changeStatus(fieldId, status)
  if (status === 'ENABLED') activateCustomField(updated)
  return updated
}

function activateCustomField(field) {
  const key = field?.fieldCode || field?.key
  if (!key) return
  if (!props.visibleColumns.includes(key)) emit('update:visibleColumns', [...props.visibleColumns, key])
  if (!props.columnOrder.includes(key)) emit('update:columnOrder', [...props.columnOrder, key])
}

function hasBoundProp(name) {
  return Object.hasOwn(instance?.vnode?.props || {}, name)
}

function updateSorts(value) {
  if (hasBoundProp('sorts')) {
    emit('update:sorts', value)
    return
  }
  const first = value[0] || { key: '', order: '' }
  emit('update:sortKey', first.key)
  emit('update:sortOrder', first.order)
}

function updateGroups(value) {
  if (hasBoundProp('groups')) {
    emit('update:groups', value)
    return
  }
  emit('update:groupKey', value[0]?.key || '')
}

provideWorkspaceToolbar({
  query: {
    get workspaceConfig() { return props.workspaceConfig },
    get dataMode() { return props.workspaceConfig?.query?.dataMode || 'client' },
    get filterOptions() { return props.filterOptions },
    get filters() { return props.filters },
    get filterLogic() { return props.filterLogic },
    get filterLogics() { return props.filterLogics },
    get sortOptions() { return props.sortOptions },
    get sorts() { return props.sorts },
    get sortKey() { return props.sortKey },
    get sortOrder() { return props.sortOrder },
    get sortCount() { return props.sortCount },
    get groupOptions() { return props.groupOptions },
    get groups() { return props.groups },
    get groupKey() { return props.groupKey },
    get groupCount() { return props.groupCount },
    get activeFilterCount() { return props.activeFilterCount },
    get filterItems() { return props.filterItems },
    updateFilters: value => emit('update:filters', value),
    updateFilterLogic: value => emit('update:filterLogic', value),
    updateSorts,
    updateGroups
  },
  fields: {
    get columns() { return resolvedColumns.value },
    get visibleColumns() { return props.visibleColumns },
    get columnOrder() { return props.columnOrder },
    get fixedColumns() { return props.fixedColumns },
    get customColumns() { return resolvedCustomColumns.value },
    get customFieldDefinitions() { return workspaceCustomFields.definitions.value },
    get canManageCustomFields() { return workspaceCustomFields.canManage.value },
    get customFieldLoading() { return workspaceCustomFields.loading.value },
    get customFieldSaving() { return workspaceCustomFields.saving.value },
    get customFieldError() { return workspaceCustomFields.error.value },
    get allowCustomFields() { return resolvedCustomFieldConfig.value.enabled },
    get allowColumnLayout() { return props.allowColumnLayout },
    get saveable() { return props.fieldConfigSaveable },
    get dirty() { return props.fieldConfigDirty },
    updateVisibleColumns: value => emit('update:visibleColumns', value),
    updateColumnOrder: value => emit('update:columnOrder', value),
    updateFixedColumns: value => emit('update:fixedColumns', value),
    updateCustomColumns: value => emit('update:customColumns', value),
    createCustomColumn: value => emit('create:custom-column', value),
    createCustomField: createManagedCustomField,
    updateCustomField: updateManagedCustomField,
    changeCustomFieldStatus: changeManagedCustomFieldStatus,
    deleteCustomField: fieldId => workspaceCustomFields.remove(fieldId),
    reorderCustomFields: order => workspaceCustomFields.reorder(order),
    retryCustomFields: () => workspaceCustomFields.refresh({
      includeDisabled: workspaceCustomFields.canManage.value
    }),
    save: () => emit('save-field-config')
  },
  view: {
    get rowDensity() { return props.rowDensity },
    updateRowDensity: value => emit('update:rowDensity', value),
    reset: () => emit('reset-view')
  },
  transfer: {
    get workspaceConfig() { return props.workspaceConfig },
    get loadingActionIds() { return props.importExportLoadingActionIds },
    get disabledActionIds() { return props.importExportDisabledActionIds },
    runAction: value => emit('workspace-action', value),
    selectFile: value => emit('workspace-file', value)
  }
})

const hasActiveViewState = computed(() => (
  props.activeFilterCount > 0
  || props.filters.length > 0
  || props.groupCount > 0
  || props.groups.length > 0
  || Boolean(props.groupKey)
  || props.sortCount > 0
  || props.sorts.length > 0
  || Boolean(props.sortKey)
  || props.rowDensity !== 'standard'
))

function mergeFields(primary = [], secondary = []) {
  const result = []
  const seen = new Set()
  ;[...(primary || []), ...(secondary || [])].forEach(field => {
    if (!field?.key || seen.has(field.key)) return
    seen.add(field.key)
    result.push(field)
  })
  return result
}

function legacyCustomFieldWorkspaceKey(configId) {
  return {
    'finance-bills': 'bills',
    'system-mobile-users': 'mobile-users'
  }[configId] || configId
}
</script>

<style scoped>
.dhic-data-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-height: 40px;
  margin: 0 0 10px;
  padding: 5px 7px;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-base);
  background: var(--dhic-card-bg);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.toolbar-left {
  flex-wrap: wrap;
  flex: 1 1 auto;
}

.toolbar-right {
  flex: 0 1 auto;
  justify-content: flex-end;
  margin-left: auto;
}

.toolbar-data-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
  padding-left: 8px;
  border-left: 1px solid var(--dhic-border);
}

@media (max-width: 1180px) {
  .dhic-data-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-right {
    flex-wrap: wrap;
    width: 100%;
  }

  .toolbar-right {
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>

<style>
.dhic-data-toolbar .workspace-toolbar-button {
  min-height: 28px;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.dhic-data-toolbar .workspace-toolbar-button:hover,
.dhic-data-toolbar .workspace-toolbar-button.active {
  color: var(--dhic-primary);
  background: var(--dhic-primary-soft);
}

.workspace-button-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--dhic-primary-soft);
  color: var(--dhic-primary);
  font-size: 12px;
  font-weight: 700;
}

.dhic-view-popper {
  max-width: calc(100vw - 48px);
  border-radius: 8px !important;
}
</style>
