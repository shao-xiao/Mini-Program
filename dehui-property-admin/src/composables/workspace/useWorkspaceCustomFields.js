import { computed, ref, shallowRef } from 'vue'
import {
  changeCustomFieldDefinitionStatus,
  createCustomFieldDefinition,
  deleteCustomFieldDefinition,
  listCustomFieldDefinitions,
  reorderCustomFieldDefinitions,
  updateCustomFieldDefinition
} from '../../api/customFields.js'
import { normalizeCustomFieldDefinition, normalizeCustomFieldDefinitions } from '../../utils/customFieldSchema.js'
import { hasPermission } from '../../utils/permission.js'

const defaultClient = {
  list: listCustomFieldDefinitions,
  create: createCustomFieldDefinition,
  update: updateCustomFieldDefinition,
  changeStatus: changeCustomFieldDefinitionStatus,
  remove: deleteCustomFieldDefinition,
  reorder: reorderCustomFieldDefinitions
}

export function createWorkspaceCustomFieldRepository(client = defaultClient) {
  const entries = new Map()

  function scope(workspaceKey, userScope = 'anonymous') {
    const normalizedWorkspaceKey = String(workspaceKey || '').trim()
    const cacheKey = `${String(userScope || 'anonymous')}::${normalizedWorkspaceKey}`
    if (!entries.has(cacheKey)) entries.set(cacheKey, createEntry(normalizedWorkspaceKey, client))
    return entries.get(cacheKey)
  }

  function clear(workspaceKey = '') {
    if (!workspaceKey) {
      entries.clear()
      return
    }
    for (const [cacheKey] of entries) {
      if (cacheKey.endsWith(`::${workspaceKey}`)) entries.delete(cacheKey)
    }
  }

  return { scope, clear }
}

const sharedRepository = createWorkspaceCustomFieldRepository()

export function useWorkspaceCustomFields(options = {}) {
  const workspaceKey = String(options.workspaceKey || '').trim()
  const enabled = options.enabled === true && Boolean(workspaceKey)
  const username = options.username || globalThis.localStorage?.getItem('username') || 'anonymous'
  const permissionCheck = options.permissionCheck || hasPermission
  const canManage = computed(() => enabled
    && permissionCheck(options.managePermission || 'custom-field:manage')
    && permissionCheck(options.valueUpdatePermission || ''))
  const canEditValues = computed(() => enabled && permissionCheck(options.valueUpdatePermission || ''))
  const repository = options.repository || sharedRepository
  const scoped = repository.scope(workspaceKey, username)

  if (enabled) void scoped.load({ includeDisabled: canManage.value }).catch(() => {})

  return {
    enabled,
    workspaceKey,
    canManage,
    canEditValues,
    definitions: scoped.definitions,
    activeDefinitions: scoped.activeDefinitions,
    loading: scoped.loading,
    saving: scoped.saving,
    error: scoped.error,
    load: scoped.load,
    refresh: scoped.refresh,
    create: scoped.create,
    update: scoped.update,
    changeStatus: scoped.changeStatus,
    remove: scoped.remove,
    reorder: scoped.reorder
  }
}

function createEntry(workspaceKey, client) {
  const definitions = shallowRef([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const activeDefinitions = computed(() => definitions.value.filter(definition => (
    definition.status !== 'DISABLED' && definition.deleted !== true
  )))
  let loaded = false
  let includesDisabled = false
  let inFlight = null
  let requestId = 0

  async function load(options = {}) {
    const includeDisabled = Boolean(options.includeDisabled)
    if (!options.force && loaded && (!includeDisabled || includesDisabled)) return definitions.value
    if (!options.force && inFlight) {
      return inFlight.then(() => (
        includeDisabled && !includesDisabled
          ? load({ includeDisabled: true, force: true })
          : definitions.value
      ))
    }
    const currentRequestId = ++requestId
    loading.value = true
    error.value = ''
    inFlight = Promise.resolve(client.list(workspaceKey, { includeDisabled }))
      .then(rows => {
        if (currentRequestId !== requestId) return definitions.value
        definitions.value = normalizeCustomFieldDefinitions(rows)
        loaded = true
        includesDisabled = includeDisabled
        return definitions.value
      })
      .catch(cause => {
        if (currentRequestId === requestId) {
          definitions.value = []
          loaded = false
          error.value = errorMessage(cause)
        }
        throw cause
      })
      .finally(() => {
        if (currentRequestId === requestId) loading.value = false
        inFlight = null
      })
    return inFlight
  }

  async function runMutation(task, apply) {
    saving.value = true
    error.value = ''
    try {
      const result = await task()
      apply?.(result)
      return result
    } catch (cause) {
      error.value = errorMessage(cause)
      throw cause
    } finally {
      saving.value = false
    }
  }

  async function create(payload) {
    return runMutation(
      () => client.create(workspaceKey, payload),
      created => upsert(created)
    )
  }

  async function update(fieldId, payload) {
    return runMutation(
      () => client.update(workspaceKey, fieldId, payload),
      updated => upsert(updated)
    )
  }

  async function changeStatus(fieldId, status) {
    return runMutation(
      () => client.changeStatus(workspaceKey, fieldId, status),
      updated => upsert(updated || { id: fieldId, status })
    )
  }

  async function remove(fieldId) {
    return runMutation(
      () => client.remove(workspaceKey, fieldId),
      () => { definitions.value = definitions.value.filter(item => item.id !== fieldId) }
    )
  }

  async function reorder(order) {
    return runMutation(
      () => client.reorder(workspaceKey, order),
      () => {
        const orderMap = new Map(order.map(item => [item.fieldId, item.displayOrder]))
        definitions.value = definitions.value
          .map(item => ({ ...item, displayOrder: orderMap.get(item.id) ?? item.displayOrder }))
          .sort((left, right) => left.displayOrder - right.displayOrder)
      }
    )
  }

  function upsert(rawDefinition) {
    const normalized = normalizeCustomFieldDefinition(rawDefinition || {})
    if (!normalized.fieldCode) return
    const index = definitions.value.findIndex(item => item.id === normalized.id || item.fieldCode === normalized.fieldCode)
    const next = [...definitions.value]
    if (index >= 0) next.splice(index, 1, normalized)
    else next.push(normalized)
    definitions.value = normalizeCustomFieldDefinitions(next)
  }

  return {
    definitions,
    activeDefinitions,
    loading,
    saving,
    error,
    load,
    refresh: options => load({ ...options, force: true }),
    create,
    update,
    changeStatus,
    remove,
    reorder
  }
}

function errorMessage(error) {
  return String(error?.message || error?.data?.message || error?.response?.data?.message || '自定义字段请求失败')
}
