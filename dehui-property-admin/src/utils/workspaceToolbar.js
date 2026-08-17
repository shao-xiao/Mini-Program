import { queryScopeLabel, workspaceFieldCapability } from './workspaceQueryPlan.js'

export function resolveWorkspaceQueryCapability(field, capability, dataMode = 'client') {
  const definition = workspaceFieldCapability(field, capability, dataMode)
  const mode = definition.mode || 'disabled'
  return {
    mode,
    label: queryScopeLabel(mode === 'disabled' ? 'unsupported' : mode),
    reason: definition.reason || (mode === 'disabled' ? '当前数据模式不支持' : ''),
    operators: Array.isArray(definition.operators) ? [...definition.operators] : undefined
  }
}

export function availableWorkspaceFields(fields = [], capability, dataMode = 'client') {
  return fields.filter(field => resolveWorkspaceQueryCapability(field, capability, dataMode).mode !== 'disabled')
}

export function appendUniqueWorkspaceCondition(conditions = [], fields = [], options = {}) {
  const {
    capability = 'sort',
    dataMode = 'client',
    defaults = { order: 'ascending' }
  } = options
  const used = new Set(conditions.map(condition => condition?.key).filter(Boolean))
  const field = availableWorkspaceFields(fields, capability, dataMode).find(item => !used.has(item.key))
  if (!field) return conditions.map(condition => ({ ...condition }))
  return [...conditions.map(condition => ({ ...condition })), { key: field.key, ...defaults }]
}

export function updateWorkspaceCondition(conditions = [], index, patch = {}) {
  return conditions.map((condition, itemIndex) => (
    itemIndex === index ? { ...condition, ...patch } : { ...condition }
  ))
}

export function removeWorkspaceCondition(conditions = [], index) {
  return conditions.filter((_, itemIndex) => itemIndex !== index).map(condition => ({ ...condition }))
}

export function moveWorkspaceCondition(conditions = [], index, direction) {
  const targetIndex = index + direction
  const next = conditions.map(condition => ({ ...condition }))
  if (index < 0 || targetIndex < 0 || index >= next.length || targetIndex >= next.length) return next
  ;[next[index], next[targetIndex]] = [next[targetIndex], next[index]]
  return next
}

export function isWorkspaceFieldUsed(conditions = [], key, currentIndex = -1) {
  return conditions.some((condition, index) => index !== currentIndex && condition?.key === key)
}
