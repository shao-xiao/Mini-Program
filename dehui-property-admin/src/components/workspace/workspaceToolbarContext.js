import { inject, provide } from 'vue'

export const WORKSPACE_TOOLBAR_CONTEXT = Symbol('dhic-workspace-toolbar')

const noop = () => {}

export function createWorkspaceToolbarFallback() {
  return {
    provided: false,
    query: {
      workspaceConfig: null,
      dataMode: 'client',
      filterOptions: [],
      filters: [],
      filterLogic: 'and',
      filterLogics: ['and', 'or'],
      sortOptions: [],
      sorts: [],
      sortKey: '',
      sortOrder: '',
      sortCount: 0,
      groupOptions: [],
      groups: [],
      groupKey: '',
      groupCount: 0,
      activeFilterCount: 0,
      filterItems: [],
      updateFilters: noop,
      updateFilterLogic: noop,
      updateSorts: noop,
      updateGroups: noop
    },
    fields: {
      columns: [],
      visibleColumns: [],
      columnOrder: [],
      fixedColumns: {},
      customColumns: [],
      customFieldDefinitions: [],
      canManageCustomFields: false,
      customFieldLoading: false,
      customFieldSaving: false,
      customFieldError: '',
      allowCustomFields: false,
      allowColumnLayout: false,
      saveable: false,
      dirty: false,
      updateVisibleColumns: noop,
      updateColumnOrder: noop,
      updateFixedColumns: noop,
      updateCustomColumns: noop,
      createCustomColumn: noop,
      createCustomField: noop,
      updateCustomField: noop,
      changeCustomFieldStatus: noop,
      deleteCustomField: noop,
      reorderCustomFields: noop,
      retryCustomFields: noop,
      save: noop
    },
    view: {
      rowDensity: 'standard',
      updateRowDensity: noop,
      reset: noop
    },
    transfer: {
      workspaceConfig: null,
      loadingActionIds: [],
      disabledActionIds: [],
      runAction: noop,
      selectFile: noop
    }
  }
}

export function provideWorkspaceToolbar(controller) {
  const resolved = { provided: true, ...controller }
  provide(WORKSPACE_TOOLBAR_CONTEXT, resolved)
  return resolved
}

export function useWorkspaceToolbar(componentName = 'WorkspaceToolbarControl') {
  const controller = inject(WORKSPACE_TOOLBAR_CONTEXT, null)
  if (!controller && import.meta.env?.DEV) {
    console.warn(`[DataWorkspace] ${componentName} must be rendered inside DhicDataToolbar`)
  }
  return controller || createWorkspaceToolbarFallback()
}
