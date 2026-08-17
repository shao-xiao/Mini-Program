import { computed, getCurrentInstance, watch } from 'vue'
import { provideWorkspaceGrid } from '../components/workspace/workspaceGridContext.js'
import { normalizeTableViewPrefs } from '../utils/tableViewPrefs.js'
import { cloneWorkspaceValue } from '../utils/workspaceClone.js'
import { useWorkspaceColumns } from './workspace/useWorkspaceColumns.js'
import { useWorkspaceCustomFields } from './workspace/useWorkspaceCustomFields.js'
import { useWorkspaceCustomFieldValues } from './workspace/useWorkspaceCustomFieldValues.js'
import { useWorkspaceDraft } from './workspace/useWorkspaceDraft.js'
import { useWorkspaceQuery } from './workspace/useWorkspaceQuery.js'
import { useWorkspaceRows } from './workspace/useWorkspaceRows.js'
import { useWorkspaceSelection } from './workspace/useWorkspaceSelection.js'
import { useWorkspaceViewRepository } from './workspace/useWorkspaceViewRepository.js'

export function useDhicTableView(options) {
  const {
    storageKey,
    pageKey = globalThis.location?.pathname || storageKey,
    username = globalThis.localStorage?.getItem('username') || 'anonymous',
    columns,
    records,
    filters = () => [],
    groupLabel = value => value || '未填写',
    defaultViewName = '默认视图',
    explicitSave = false,
    defaultPrefs = {},
    initialViews = [],
    query: queryConfig = {},
    queryState = {},
    queryLifecycle = {},
    customFields: customFieldConfig = {},
    serverTotal = () => records().length,
    structuralColumnCount = 2,
    groupColumnIndex = 0,
    selection = {}
  } = options

  const displayColumnKeys = columns.filter(column => !column.filterOnly).map(column => column.key)
  const defaultTablePrefs = {
    visibleColumns: displayColumnKeys,
    columnOrder: displayColumnKeys,
    columnWidths: {},
    fixedColumns: {},
    rowDensity: 'standard',
    pageSize: 20,
    sortKey: '',
    sortOrder: '',
    groupKey: '',
    customColumns: [],
    filterLogic: 'and',
    filters: [],
    groups: [],
    sorts: [],
    ...defaultPrefs
  }
  const dataMode = queryConfig.dataMode || 'client'
  const workspaceCustomFields = useWorkspaceCustomFields(customFieldConfig)
  const workspaceCustomFieldValues = useWorkspaceCustomFieldValues({
    ...customFieldConfig,
    definitions: workspaceCustomFields.activeDefinitions,
    rowKey: customFieldConfig.entityIdField || selection.rowKey || 'id'
  })
  const sourceRecords = computed(() => {
    const value = records()
    return Array.isArray(value) ? value : []
  })
  const usesBatchCustomFieldValues = customFieldConfig.enabled === true
    && customFieldConfig.valueSource === 'batch'
    && dataMode === 'client'
  const customFieldBatchKey = computed(() => {
    if (!usesBatchCustomFieldValues || workspaceCustomFields.loading.value) return ''
    const definitions = workspaceCustomFields.activeDefinitions.value
    if (!definitions.length) return ''
    const fieldRevision = definitions
      .map(field => `${field.id || field.key}:${field.version || 0}:${field.status || 'ENABLED'}`)
      .join(',')
    const entityIds = sourceRecords.value
      .map(row => resolveWorkspaceRecordId(row, customFieldConfig.entityIdField || selection.rowKey || 'id'))
      .filter(value => value !== undefined && value !== null && value !== '')
      .map(String)
      .join(',')
    return entityIds ? `${fieldRevision}|${entityIds}` : ''
  })
  const resolvedRecords = computed(() => (
    usesBatchCustomFieldValues && workspaceCustomFields.activeDefinitions.value.length
      ? workspaceCustomFieldValues.hydrateRows(sourceRecords.value)
      : sourceRecords.value
  ))

  watch(customFieldBatchKey, nextKey => {
    if (!nextKey) {
      workspaceCustomFieldValues.clear()
      return
    }
    void workspaceCustomFieldValues.load(sourceRecords.value).catch(() => {})
  }, { immediate: true })

  function availableKeys(customColumns = [], prefs = {}) {
    return [
      ...columns.map(column => column.key),
      ...workspaceCustomFields.activeDefinitions.value.map(column => column.key),
      ...persistedCustomFieldKeys(prefs),
      ...(customColumns || []).map(column => column.key)
    ]
  }

  function normalizePrefs(nextPrefs = {}) {
    return normalizeTableViewPrefs(
      nextPrefs,
      defaultTablePrefs,
      availableKeys(nextPrefs.customColumns, nextPrefs)
    )
  }

  const viewRepository = useWorkspaceViewRepository({
    storageKey,
    pageKey,
    username,
    columns,
    defaultPrefs: defaultTablePrefs,
    initialViews,
    defaultViewName,
    normalizePrefs,
    availableKeys
  })
  const workspaceDraft = useWorkspaceDraft({
    persistedPrefs: viewRepository.persistedPrefs,
    normalizePrefs,
    explicitSave
  })
  const { draftPrefs, tablePrefs } = workspaceDraft

  function persistTableViewPrefs(nextPrefs) {
    const normalizedPrefs = normalizePrefs(nextPrefs)
    workspaceDraft.updateDraft(normalizedPrefs)
    if (!explicitSave) {
      const savedPrefs = viewRepository.persistActivePrefs(normalizedPrefs)
      workspaceDraft.markSaved(savedPrefs || normalizedPrefs)
    }
  }

  const workspaceColumns = useWorkspaceColumns({
    columns,
    tablePrefs,
    customFields: workspaceCustomFields.activeDefinitions,
    dataMode,
    updatePrefs: persistTableViewPrefs
  })
  const workspaceQuery = useWorkspaceQuery({
    fields: workspaceColumns.allColumns,
    fieldByKey: workspaceColumns.fieldByKey,
    tablePrefs,
    filters,
    queryConfig,
    queryState,
    queryLifecycle,
    updatePrefs: persistTableViewPrefs,
    isColumnSortable: workspaceColumns.isColumnSortable
  })
  const workspaceRows = useWorkspaceRows({
    records: () => resolvedRecords.value,
    serverTotal,
    tablePrefs,
    fields: workspaceColumns.allColumns,
    fieldAccessors: workspaceColumns.fieldAccessors,
    localFilters: workspaceQuery.localFilters,
    localSorts: workspaceQuery.localSorts,
    localGroups: workspaceQuery.localGroups,
    serverGroups: workspaceQuery.serverGroups,
    groupLabel,
    visibleColumnCount: () => tablePrefs.visibleColumns.length,
    structuralColumnCount,
    groupColumnIndex,
    rowKey: customFieldConfig.entityIdField || selection.rowKey || 'id'
  })
  const workspaceSelection = useWorkspaceSelection({
    records: () => resolvedRecords.value,
    rowKey: selection.rowKey || 'id',
    selectable: selection.selectable,
    preserveAcrossPages: selection.preserveAcrossPages
  })

  if (getCurrentInstance()) {
    provideWorkspaceGrid({
      customFields: {
        get enabled() { return customFieldConfig.enabled === true },
        canEditCell: workspaceCustomFieldValues.canEditCell,
        cellKey: workspaceCustomFieldValues.cellKey,
        isSaving: (row, field) => (
          workspaceCustomFieldValues.savingCellKey.value === workspaceCustomFieldValues.cellKey(row, field)
        ),
        saveCell: workspaceCustomFieldValues.saveCell
      }
    })
  }

  function updateCustomColumns(value) {
    const customKeys = value.map(column => column.key)
    if (explicitSave) {
      persistTableViewPrefs({
        ...tablePrefs,
        customColumns: value,
        visibleColumns: Array.from(new Set([...tablePrefs.visibleColumns, ...customKeys])),
        columnOrder: Array.from(new Set([...tablePrefs.columnOrder, ...customKeys]))
      })
      return
    }

    const savedPrefs = viewRepository.persistCustomColumns(value)
    workspaceDraft.markSaved(savedPrefs)
  }

  function saveCurrentView() {
    const normalizedPrefs = normalizePrefs(tablePrefs)
    const savedPrefs = viewRepository.persistActivePrefs(normalizedPrefs, {
      syncCustomColumns: true,
      updateResetPrefs: true
    })
    workspaceDraft.markSaved(savedPrefs || normalizedPrefs)
    return true
  }

  function discardCurrentViewChanges() {
    workspaceDraft.discardDraft()
  }

  function resetTableView() {
    const customColumns = tablePrefs.customColumns || []
    const customKeys = customColumns.map(column => column.key)
    const resetPrefs = viewRepository.activeResetPrefs()
    persistTableViewPrefs({
      ...resetPrefs,
      customColumns,
      visibleColumns: Array.from(new Set([...(resetPrefs.visibleColumns || []), ...customKeys])),
      columnOrder: Array.from(new Set([...(resetPrefs.columnOrder || []), ...customKeys]))
    })
  }

  function switchTableView(viewId) {
    if (viewId === viewRepository.activeViewId.value) return
    if (!explicitSave) viewRepository.syncActivePrefs(tablePrefs)
    const target = viewRepository.setActiveView(viewId)
    if (!target) return
    workspaceDraft.markSaved(target.prefs)
    workspaceRows.clearCollapsedGroups()
  }

  function createTableView(name) {
    if (!explicitSave) viewRepository.syncActivePrefs(tablePrefs)
    const view = viewRepository.createView(name, tablePrefs)
    if (!view) return null
    workspaceDraft.markSaved(view.prefs)
    return view.id
  }

  function renameTableView(viewId, name) {
    return viewRepository.renameView(viewId, name)
  }

  function duplicateTableView(viewId, name) {
    if (!explicitSave) viewRepository.syncActivePrefs(tablePrefs)
    const sourcePrefs = explicitSave && viewId === viewRepository.activeViewId.value
      ? cloneWorkspaceValue(tablePrefs)
      : undefined
    const view = viewRepository.copyView(viewId, name, sourcePrefs)
    if (!view) return null
    workspaceDraft.markSaved(view.prefs)
    return view.id
  }

  function deleteTableView(viewId) {
    const deleted = viewRepository.deleteView(viewId)
    if (!deleted) return false
    workspaceDraft.markSaved(viewRepository.persistedPrefs.value)
    return true
  }

  function updatePageSize(value) {
    persistTableViewPrefs({ ...tablePrefs, pageSize: value })
  }

  return {
    scopedStorageKey: viewRepository.scopedStorageKey,
    workspaceViews: viewRepository.views,
    activeViewId: viewRepository.activeViewId,
    activeViewName: viewRepository.activeViewName,
    isViewDirty: workspaceDraft.isDirty,
    persistedPrefs: viewRepository.persistedPrefs,
    draftPrefs,
    tablePrefs,
    customFieldDefinitions: workspaceCustomFields.definitions,
    activeCustomFields: workspaceCustomFields.activeDefinitions,
    customFieldLoading: workspaceCustomFields.loading,
    customFieldError: workspaceCustomFields.error,
    customFieldValuesByEntity: workspaceCustomFieldValues.valuesByEntity,
    customFieldValueLoading: workspaceCustomFieldValues.loading,
    customFieldValueError: workspaceCustomFieldValues.error,
    customFieldCellSavingKey: workspaceCustomFieldValues.savingCellKey,
    canEditCustomFieldValues: workspaceCustomFieldValues.canEditValues,
    loadCustomFieldValues: workspaceCustomFieldValues.load,
    hydrateCustomFieldRows: workspaceCustomFieldValues.hydrateRows,
    canEditCustomFieldCell: workspaceCustomFieldValues.canEditCell,
    customFieldCellKey: workspaceCustomFieldValues.cellKey,
    saveCustomFieldCell: workspaceCustomFieldValues.saveCell,
    saveCustomFieldRecord: workspaceCustomFieldValues.saveRecord,
    batchSaveCustomFieldRecords: workspaceCustomFieldValues.batchSave,
    clearCustomFieldValues: workspaceCustomFieldValues.clear,
    allColumns: workspaceColumns.allColumns,
    fieldByKey: workspaceColumns.fieldByKey,
    fieldAccessors: workspaceColumns.fieldAccessors,
    optionMaps: workspaceColumns.optionMaps,
    orderedColumns: workspaceColumns.orderedColumns,
    visibleOrderedColumns: workspaceColumns.visibleOrderedColumns,
    loadedRecords: workspaceRows.loadedRecords,
    visibleRecords: workspaceRows.visibleRecords,
    groupedRows: workspaceRows.groupedRows,
    tableRows: workspaceRows.tableRows,
    filteredRecords: workspaceRows.filteredRecords,
    sortedRecords: workspaceRows.sortedRecords,
    sortOptions: workspaceQuery.sortOptions,
    groupOptions: workspaceQuery.groupOptions,
    filterOptions: workspaceQuery.filterOptions,
    filterLogicOptions: workspaceQuery.filterLogicOptions,
    queryPlan: workspaceQuery.queryPlan,
    serverQueryParams: workspaceQuery.serverQueryParams,
    queryCriteriaKey: workspaceQuery.queryCriteriaKey,
    queryRefreshKey: workspaceQuery.queryRefreshKey,
    queryNotices: workspaceQuery.queryNotices,
    queryRequestGuard: workspaceQuery.queryRequestGuard,
    runLatestRequest: workspaceQuery.runLatestRequest,
    debouncedQueryTask: workspaceQuery.debouncedQueryTask,
    disposeQueryLifecycle: workspaceQuery.disposeQueryLifecycle,
    resultScope: workspaceRows.resultScope,
    viewFilterItems: workspaceQuery.viewFilterItems,
    activeFilterItems: workspaceQuery.activeFilterItems,
    activeFilterCount: workspaceQuery.activeFilterCount,
    groupCount: workspaceQuery.groupCount,
    sortCount: workspaceQuery.sortCount,
    activeTableSort: workspaceQuery.activeTableSort,
    tableEmptyText: workspaceRows.tableEmptyText,
    selectedRows: workspaceSelection.selectedRows,
    selectedRowKeys: workspaceSelection.selectedRowKeys,
    selectedCount: workspaceSelection.selectedCount,
    handleSelectionChange: workspaceSelection.handleSelectionChange,
    reconcileSelection: workspaceSelection.reconcileSelection,
    clearSelection: workspaceSelection.clearSelection,
    removeSelectionByKeys: workspaceSelection.removeSelectionByKeys,
    isColumnVisible: workspaceColumns.isColumnVisible,
    updateVisibleColumns: workspaceColumns.updateVisibleColumns,
    updateColumnOrder: workspaceColumns.updateColumnOrder,
    updateColumnWidth: workspaceColumns.updateColumnWidth,
    updateFixedColumns: workspaceColumns.updateFixedColumns,
    columnWidth: workspaceColumns.columnWidth,
    columnFixed: workspaceColumns.columnFixed,
    handleHeaderDragEnd: workspaceColumns.handleHeaderDragEnd,
    handleTableSortChange: workspaceQuery.handleTableSortChange,
    isColumnSortable: workspaceColumns.isColumnSortable,
    updateSortKey: workspaceQuery.updateSortKey,
    updateSortOrder: workspaceQuery.updateSortOrder,
    updateGroupKey: workspaceQuery.updateGroupKey,
    updateRowDensity: workspaceColumns.updateRowDensity,
    updatePageSize,
    updateCustomColumns,
    updateFilterLogic: workspaceQuery.updateFilterLogic,
    updateFilters: workspaceQuery.updateFilters,
    removeViewFilter: workspaceQuery.removeViewFilter,
    updateGroups: workspaceQuery.updateGroups,
    updateSorts: workspaceQuery.updateSorts,
    resetTableView,
    switchTableView,
    createTableView,
    renameTableView,
    duplicateTableView,
    deleteTableView,
    saveCurrentView,
    discardCurrentViewChanges,
    tableSpanMethod: workspaceRows.tableSpanMethod,
    recordDisplayIndex: workspaceRows.recordDisplayIndex,
    collapsedGroupKeys: workspaceRows.collapsedGroupKeys,
    toggleGroup: workspaceRows.toggleGroup,
    isGroupCollapsed: workspaceRows.isGroupCollapsed,
    clearCollapsedGroups: workspaceRows.clearCollapsedGroups
  }
}

function persistedCustomFieldKeys(prefs = {}) {
  const keys = [
    ...(prefs.visibleColumns || prefs.visibleFields || []),
    ...(prefs.columnOrder || prefs.fieldOrder || []),
    ...Object.keys(prefs.columnWidths || {}),
    ...Object.keys(prefs.fixedColumns || {}),
    ...(prefs.filters || []).map(item => item?.key),
    ...(prefs.sorts || []).map(item => item?.key),
    ...(prefs.groups || []).map(item => item?.key)
  ]
  return keys.filter(key => /^custom_[a-z0-9_]{1,56}$/i.test(String(key || '')))
}

function resolveWorkspaceRecordId(record, rowKey) {
  return typeof rowKey === 'function' ? rowKey(record) : record?.[rowKey]
}
