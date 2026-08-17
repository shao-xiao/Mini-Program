import { computed, reactive } from 'vue'
import {
  buildWorkspaceStorageKey,
  createWorkspaceView,
  deleteWorkspaceView,
  duplicateWorkspaceView,
  normalizeWorkspaceState,
  renameWorkspaceView
} from '../../utils/workspaceViews.js'
import { cloneWorkspaceValue } from '../../utils/workspaceClone.js'

export function useWorkspaceViewRepository(options) {
  const {
    storageKey,
    pageKey,
    username,
    columns,
    defaultPrefs,
    initialViews = [],
    defaultViewName,
    normalizePrefs,
    availableKeys,
    storage = globalThis.localStorage
  } = options

  const scopedStorageKey = buildWorkspaceStorageKey(username, pageKey)
  const workspaceState = reactive(readWorkspaceState())
  ensureActiveViewId()
  const activeView = computed(() => (
    workspaceState.views.find(view => view.id === workspaceState.activeViewId) || workspaceState.views[0]
  ))
  const persistedPrefs = computed(() => activeView.value?.prefs || defaultPrefs)
  const views = computed(() => workspaceState.views.map(({ id, name, system }) => ({ id, name, system })))
  const activeViewId = computed(() => workspaceState.activeViewId)
  const activeViewName = computed(() => activeView.value?.name || defaultViewName)

  function readWorkspaceState() {
    try {
      const savedState = JSON.parse(storage?.getItem(scopedStorageKey) || '{}')
      const legacyPrefs = JSON.parse(storage?.getItem(storageKey) || 'null')
      const normalizedState = normalizeWorkspaceState(savedState, {
        defaultPrefs,
        availableColumnKeys: restoredAvailableKeys(savedState, legacyPrefs),
        initialViews,
        legacyPrefs,
        defaultViewName
      })
      if (!Array.isArray(normalizedState?.views) || !normalizedState.views.length) {
        return normalizeWorkspaceState({}, {
          defaultPrefs,
          availableColumnKeys: columns.map(column => column.key),
          initialViews,
          defaultViewName
        })
      }

      if (!normalizedState.views.some(view => String(view.id) === String(normalizedState.activeViewId))) {
        return {
          ...normalizedState,
          activeViewId: normalizedState.views[0]?.id || null
        }
      }
      return normalizedState
    } catch (_error) {
      return normalizeWorkspaceState({}, {
        defaultPrefs,
        availableColumnKeys: columns.map(column => column.key),
        initialViews,
        defaultViewName
      })
    }
  }

  function restoredAvailableKeys(savedState, legacyPrefs) {
    const keys = new Set(columns.map(column => column.key))
    const collect = prefs => {
      if (!prefs) return
      availableKeys(prefs?.customColumns, prefs).forEach(key => keys.add(key))
    }
    collect(legacyPrefs)
    ;(savedState?.views || []).forEach(view => {
      collect(view?.prefs)
      collect(view?.resetPrefs)
    })
    return [...keys]
  }

  function writeWorkspaceState() {
    ensureActiveViewId()
    storage?.setItem(scopedStorageKey, JSON.stringify(workspaceState))
  }

  function ensureActiveViewId() {
    const firstView = workspaceState.views[0]
    if (workspaceState.views.some(view => String(view.id) === String(workspaceState.activeViewId))) {
      return workspaceState.activeViewId
    }
    if (!firstView) {
      workspaceState.activeViewId = null
      return null
    }
    workspaceState.activeViewId = firstView.id
    return workspaceState.activeViewId
  }

  function syncActivePrefs(prefs) {
    ensureActiveViewId()
    if (!activeView.value) return null
    const normalized = normalizePrefs(prefs)
    activeView.value.prefs = cloneWorkspaceValue(normalized)
    return normalized
  }

  function persistActivePrefs(prefs, { syncCustomColumns = false, updateResetPrefs = false } = {}) {
    const normalized = normalizePrefs(prefs)
    ensureActiveViewId()
    const currentView = activeView.value
    if (!currentView) return null

    if (syncCustomColumns) {
      const customColumns = normalized.customColumns || []
      const customKeys = customColumns.map(column => column.key)

      workspaceState.views.forEach(view => {
        if (view.id === currentView.id) {
          view.prefs = cloneWorkspaceValue(normalized)
          if (updateResetPrefs && !view.system) view.resetPrefs = cloneWorkspaceValue(normalized)
          return
        }

        view.prefs = cloneWorkspaceValue(normalizePrefs({
          ...view.prefs,
          customColumns,
          visibleColumns: Array.from(new Set([...(view.prefs.visibleColumns || []), ...customKeys])),
          columnOrder: Array.from(new Set([...(view.prefs.columnOrder || []), ...customKeys]))
        }))
      })
    } else {
      currentView.prefs = cloneWorkspaceValue(normalized)
      if (updateResetPrefs && !currentView.system) currentView.resetPrefs = cloneWorkspaceValue(normalized)
    }

    writeWorkspaceState()
    return cloneWorkspaceValue(currentView.prefs)
  }

  function persistCustomColumns(customColumns) {
    ensureActiveViewId()
    const customKeys = customColumns.map(column => column.key)
    workspaceState.views.forEach(view => {
      view.prefs = cloneWorkspaceValue(normalizePrefs({
        ...view.prefs,
        customColumns,
        visibleColumns: Array.from(new Set([...(view.prefs.visibleColumns || []), ...customKeys])),
        columnOrder: Array.from(new Set([...(view.prefs.columnOrder || []), ...customKeys]))
      }))
    })
    writeWorkspaceState()
    return cloneWorkspaceValue(activeView.value?.prefs || defaultPrefs)
  }

  function setActiveView(viewId) {
    const target = workspaceState.views.find(view => view.id === viewId)
    if (!target) {
      const fallback = workspaceState.views[0]
      if (!fallback || fallback.id === workspaceState.activeViewId) return fallback || null
      workspaceState.activeViewId = fallback.id
      writeWorkspaceState()
      return fallback
    }
    if (target.id === workspaceState.activeViewId) return target
    workspaceState.activeViewId = target.id
    writeWorkspaceState()
    return target
  }

  function createView(name, prefs) {
    const view = createWorkspaceView(workspaceState, name, prefs, availableKeys(prefs?.customColumns))
    if (view) writeWorkspaceState()
    return view
  }

  function copyView(viewId, name, sourcePrefs) {
    const view = duplicateWorkspaceView(
      workspaceState,
      viewId,
      name,
      availableKeys(sourcePrefs?.customColumns),
      sourcePrefs
    )
    if (view) writeWorkspaceState()
    return view
  }

  function renameView(viewId, name) {
    const renamed = renameWorkspaceView(workspaceState, viewId, name)
    if (renamed) writeWorkspaceState()
    return renamed
  }

  function deleteView(viewId) {
    const deleted = deleteWorkspaceView(workspaceState, viewId)
    if (deleted) writeWorkspaceState()
    return deleted
  }

  function activeResetPrefs() {
    return cloneWorkspaceValue(activeView.value?.resetPrefs || defaultPrefs)
  }

  return {
    scopedStorageKey,
    workspaceState,
    views,
    activeView,
    activeViewId,
    activeViewName,
    persistedPrefs,
    writeWorkspaceState,
    syncActivePrefs,
    persistActivePrefs,
    persistCustomColumns,
    setActiveView,
    createView,
    copyView,
    renameView,
    deleteView,
    activeResetPrefs
  }
}
