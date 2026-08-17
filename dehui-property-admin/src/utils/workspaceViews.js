import { normalizeTableViewPrefs } from './tableViewPrefs.js'
import { cloneWorkspaceValue } from './workspaceClone.js'

export const DEFAULT_WORKSPACE_VIEW_ID = 'default'

export function buildWorkspaceStorageKey(username, pageKey) {
  const user = encodeURIComponent(String(username || 'anonymous').trim() || 'anonymous')
  const page = encodeURIComponent(String(pageKey || 'unknown').trim() || 'unknown')
  return `dhic-data-workspace:v2:${user}:${page}`
}

export function normalizeWorkspaceState(savedState, options) {
  const {
    defaultPrefs,
    availableColumnKeys,
    initialViews = [],
    legacyPrefs = null,
    defaultViewName = '默认视图'
  } = options
  const hasSavedViews = Array.isArray(savedState?.views) && savedState.views.length > 0
  const sourceViews = hasSavedViews
    ? [...savedState.views]
    : buildInitialViews(defaultPrefs, initialViews, legacyPrefs, defaultViewName)
  const views = []

  if (hasSavedViews) {
    initialViews
      .filter(view => view?.system && view.id)
      .forEach(view => {
        if (!sourceViews.some(item => item?.id === view.id)) {
          sourceViews.push({
            ...view,
            prefs: { ...defaultPrefs, ...(view.prefs || {}) },
            resetPrefs: { ...defaultPrefs, ...(view.prefs || {}) }
          })
        }
      })
  }

  sourceViews.forEach(view => {
    if (!view?.id || !view?.name || views.some(item => item.id === view.id)) return

    const preset = initialViews.find(item => item?.id === view.id)
    const resetPrefs = view.resetPrefs || (preset
      ? { ...defaultPrefs, ...(preset.prefs || {}) }
      : defaultPrefs)

    views.push({
      id: String(view.id),
      name: String(view.id === DEFAULT_WORKSPACE_VIEW_ID ? defaultViewName : (preset?.system ? preset.name : view.name)).trim(),
      system: view.id === DEFAULT_WORKSPACE_VIEW_ID || Boolean(view.system) || Boolean(preset?.system),
      prefs: normalizeViewPrefs(view.prefs, defaultPrefs, availableColumnKeys),
      resetPrefs: normalizeViewPrefs(resetPrefs, defaultPrefs, availableColumnKeys)
    })
  })

  if (!views.some(view => view.id === DEFAULT_WORKSPACE_VIEW_ID)) {
    views.unshift(defaultView(defaultPrefs, availableColumnKeys, legacyPrefs, defaultViewName))
  }

  const activeViewId = views.some(view => view.id === savedState?.activeViewId)
    ? savedState.activeViewId
    : DEFAULT_WORKSPACE_VIEW_ID

  return {
    version: 2,
    activeViewId,
    views
  }
}

export function createWorkspaceView(state, name, prefs, availableColumnKeys) {
  const normalizedName = String(name || '').trim()
  if (!normalizedName) return null

  const id = `view-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  const view = {
    id,
    name: normalizedName,
    system: false,
    prefs: cloneWorkspaceValue(normalizeViewPrefs(prefs, prefs, availableColumnKeys)),
    resetPrefs: cloneWorkspaceValue(normalizeViewPrefs(prefs, prefs, availableColumnKeys))
  }
  state.views.push(view)
  state.activeViewId = id
  return view
}

export function renameWorkspaceView(state, viewId, name) {
  const view = state.views.find(item => item.id === viewId)
  const normalizedName = String(name || '').trim()
  if (!view || view.system || !normalizedName) return false
  view.name = normalizedName
  return true
}

export function duplicateWorkspaceView(state, viewId, name, availableColumnKeys, sourcePrefs) {
  const source = state.views.find(item => item.id === viewId)
  if (!source) return null

  const requestedName = String(name || '').trim() || `${source.name} 副本`
  const uniqueName = uniqueWorkspaceViewName(state.views, requestedName)
  return createWorkspaceView(state, uniqueName, sourcePrefs || source.prefs, availableColumnKeys)
}

export function deleteWorkspaceView(state, viewId) {
  const view = state.views.find(item => item.id === viewId)
  if (!view || view.system) return false

  state.views = state.views.filter(item => item.id !== viewId)
  if (state.activeViewId === viewId) state.activeViewId = DEFAULT_WORKSPACE_VIEW_ID
  return true
}

function buildInitialViews(defaultPrefs, initialViews, legacyPrefs, defaultViewName) {
  return [
    {
      id: DEFAULT_WORKSPACE_VIEW_ID,
      name: defaultViewName,
      system: true,
      prefs: legacyPrefs || defaultPrefs,
      resetPrefs: defaultPrefs
    },
    ...initialViews.map((view, index) => ({
      id: view.id || `preset-${index + 1}`,
      name: view.name,
      system: Boolean(view.system),
      prefs: {
        ...defaultPrefs,
        ...(view.prefs || {})
      },
      resetPrefs: {
        ...defaultPrefs,
        ...(view.prefs || {})
      }
    }))
  ]
}

function defaultView(defaultPrefs, availableColumnKeys, legacyPrefs, defaultViewName) {
  return {
    id: DEFAULT_WORKSPACE_VIEW_ID,
    name: defaultViewName,
    system: true,
    prefs: normalizeViewPrefs(legacyPrefs || defaultPrefs, defaultPrefs, availableColumnKeys),
    resetPrefs: normalizeViewPrefs(defaultPrefs, defaultPrefs, availableColumnKeys)
  }
}

function uniqueWorkspaceViewName(views, requestedName) {
  const names = new Set(views.map(view => String(view.name).trim()))
  if (!names.has(requestedName)) return requestedName

  let sequence = 2
  while (names.has(`${requestedName} ${sequence}`)) sequence += 1
  return `${requestedName} ${sequence}`
}

function normalizeViewPrefs(prefs, defaults, availableColumnKeys) {
  const customKeys = Array.isArray(prefs?.customColumns)
    ? prefs.customColumns.map(column => column?.key).filter(Boolean)
    : []

  return cloneWorkspaceValue(normalizeTableViewPrefs(
    prefs || {},
    defaults || {},
    [...availableColumnKeys, ...customKeys]
  ))
}
