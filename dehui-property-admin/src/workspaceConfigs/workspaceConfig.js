import { customFieldWorkspaceRegistration } from './customFieldWorkspaceRegistry.js'

const REQUIRED_TEXT_KEYS = ['id', 'title', 'pageKey', 'storageKey', 'defaultViewName']
const QUERY_MODES = new Set(['server', 'client', 'page', 'disabled'])

function normalizedActionList(definition, key) {
  const actions = Array.isArray(definition[key]) ? definition[key] : []
  const actionIds = actions.map(action => action?.id).filter(Boolean)
  if (new Set(actionIds).size !== actionIds.length) {
    throw new Error(`Workspace config ${definition.id} contains duplicate ${key} ids`)
  }
  return actions
}

export function defineWorkspaceConfig(definition = {}) {
  REQUIRED_TEXT_KEYS.forEach(key => {
    if (!String(definition[key] || '').trim()) {
      throw new Error(`Workspace config requires ${key}`)
    }
  })

  const fields = Array.isArray(definition.fields) ? definition.fields : []
  const fieldKeys = fields.map(field => field?.key).filter(Boolean)
  if (new Set(fieldKeys).size !== fieldKeys.length) {
    throw new Error(`Workspace config ${definition.id} contains duplicate field keys`)
  }

  const views = Array.isArray(definition.views) ? definition.views : []
  const viewIds = views.map(view => view?.id).filter(Boolean)
  if (new Set(viewIds).size !== viewIds.length) {
    throw new Error(`Workspace config ${definition.id} contains duplicate view ids`)
  }

  const dataActions = normalizedActionList(definition, 'dataActions')
  const importExport = normalizedActionList(definition, 'importExport')
  const batchActions = normalizedActionList(definition, 'batchActions')
  const query = normalizeQueryDefinition(definition)
  const customFields = normalizeCustomFieldDefinition(definition, query)

  fields.forEach(field => validateFieldQuery(definition.id, field, query.dataMode))

  return Object.freeze({
    id: definition.id,
    title: definition.title,
    subtitle: definition.subtitle || '',
    pageKey: definition.pageKey,
    storageKey: definition.storageKey,
    defaultViewName: definition.defaultViewName,
    explicitSave: definition.explicitSave !== false,
    fields,
    views,
    metrics: Array.isArray(definition.metrics) ? definition.metrics : [],
    statuses: Array.isArray(definition.statuses) ? definition.statuses : [],
    statusTransitions: Array.isArray(definition.statusTransitions) ? definition.statusTransitions : [],
    permissions: Object.freeze({ ...(definition.permissions || {}) }),
    primaryAction: definition.primaryAction || null,
    dataActions,
    importExport,
    batchActions,
    customFields,
    query,
    table: Object.freeze({
      structuralColumnCount: definition.table?.structuralColumnCount ?? 2,
      groupColumnIndex: definition.table?.groupColumnIndex ?? 0,
      defaultPrefs: { ...(definition.table?.defaultPrefs || {}) }
    })
  })
}

export function workspaceTableOptions(config, overrides = {}) {
  return {
    storageKey: config.storageKey,
    pageKey: config.pageKey,
    defaultViewName: config.defaultViewName,
    explicitSave: config.explicitSave,
    columns: config.fields,
    initialViews: config.views,
    query: config.query,
    customFields: config.customFields,
    structuralColumnCount: config.table.structuralColumnCount,
    groupColumnIndex: config.table.groupColumnIndex,
    defaultPrefs: config.table.defaultPrefs,
    ...overrides
  }
}

function normalizeCustomFieldDefinition(definition, query) {
  const registration = customFieldWorkspaceRegistration(definition.id)
  const raw = definition.customFields || {}
  const enabled = registration ? true : raw.enabled === true
  const workspaceKey = String(raw.workspaceKey || registration?.workspaceKey || '').trim()
  if (enabled && !/^[a-z][a-z0-9-]*$/.test(workspaceKey)) {
    throw new Error(`Workspace config ${definition.id} has invalid custom field workspaceKey`)
  }
  if (registration && workspaceKey !== registration.workspaceKey) {
    throw new Error(`Workspace config ${definition.id} custom field workspaceKey must match the registry`)
  }
  if (registration && query.dataMode !== registration.queryMode) {
    throw new Error(`Workspace config ${definition.id} custom field query mode must match the registry`)
  }
  const valueUpdatePermission = String(
    raw.valueUpdatePermission || registration?.valueUpdatePermission || definition.permissions?.update || ''
  ).trim()
  if (enabled && !valueUpdatePermission.includes(':')) {
    throw new Error(`Workspace config ${definition.id} custom fields require valueUpdatePermission`)
  }
  if (registration && valueUpdatePermission !== registration.valueUpdatePermission) {
    throw new Error(`Workspace config ${definition.id} custom field update permission must match the registry`)
  }
  const permissionValues = new Set(Object.values(definition.permissions || {}))
  if (registration && (!permissionValues.has(registration.viewPermission) || !permissionValues.has(valueUpdatePermission))) {
    throw new Error(`Workspace config ${definition.id} must expose registered custom field permissions`)
  }
  return Object.freeze({
    enabled,
    workspaceKey,
    managePermission: String(raw.managePermission || 'custom-field:manage'),
    valueUpdatePermission,
    entityType: registration?.entityType || String(raw.entityType || '').trim(),
    viewPermission: registration?.viewPermission || String(raw.viewPermission || '').trim(),
    queryMode: registration?.queryMode || query.dataMode,
    valueSource: registration?.valueSource || (query.dataMode === 'server' ? 'projected' : 'batch'),
    maximumCustomFields: registration?.maximumCustomFields || Number(raw.maximumCustomFields || 50)
  })
}

function normalizeQueryDefinition(definition) {
  const dataMode = definition.query?.dataMode || 'client'
  if (!['server', 'client'].includes(dataMode)) {
    throw new Error(`Workspace config ${definition.id} has invalid query dataMode`)
  }
  const filterLogics = definition.query?.filterLogics || (dataMode === 'server' ? ['and'] : ['and', 'or'])
  if (!Array.isArray(filterLogics) || filterLogics.some(logic => !['and', 'or'].includes(logic))) {
    throw new Error(`Workspace config ${definition.id} has invalid query filterLogics`)
  }
  return Object.freeze({ dataMode, filterLogics: Object.freeze([...new Set(filterLogics)]) })
}

function validateFieldQuery(configId, field, dataMode) {
  const definitions = []
  if (field?.query && Object.hasOwn(field.query, 'mode')) definitions.push(['filter', field.query])
  ;['filter', 'sort', 'group'].forEach(capability => {
    if (field?.query?.[capability]) definitions.push([capability, field.query[capability]])
  })

  definitions.forEach(([capability, raw]) => {
    const definition = typeof raw === 'string' ? { mode: raw } : raw
    if (!definition || !QUERY_MODES.has(definition.mode)) {
      throw new Error(`Workspace config ${configId} field ${field.key} has invalid ${capability} query mode`)
    }
    if (definition.operators && (!Array.isArray(definition.operators) || definition.operators.some(operator => !String(operator)))) {
      throw new Error(`Workspace config ${configId} field ${field.key} has invalid query operators`)
    }
    if (dataMode === 'server' && definition.mode === 'server' && !definition.param && typeof definition.serialize !== 'function') {
      throw new Error(`Workspace config ${configId} field ${field.key} server query requires param or serialize`)
    }
  })
}

export function workspacePermission(config, permissionKey) {
  return config?.permissions?.[permissionKey] || ''
}

export function permittedWorkspaceActions(config, actions, permissionCheck) {
  if (typeof permissionCheck !== 'function') return []
  return (actions || []).filter(action => {
    const code = workspacePermission(config, action.permission)
    return Boolean(code) && permissionCheck(code)
  })
}

export function workspaceAction(config, actionId, collection = 'dataActions') {
  const actions = Array.isArray(config?.[collection]) ? config[collection] : []
  return actions.find(action => action.id === actionId) || null
}
