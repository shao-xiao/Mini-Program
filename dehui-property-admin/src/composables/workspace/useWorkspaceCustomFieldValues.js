import { computed, ref, shallowRef, unref } from 'vue'
import {
  batchReadCustomFieldValues,
  batchUpdateCustomFieldValues,
  saveCustomFieldValues
} from '../../api/customFields.js'
import {
  mergeCustomFieldValues,
  normalizeCustomFieldPayload
} from '../../utils/customFieldValues.js'
import { hasPermission } from '../../utils/permission.js'

const defaultClient = {
  read: batchReadCustomFieldValues,
  save: saveCustomFieldValues,
  batchSave: batchUpdateCustomFieldValues
}

export function useWorkspaceCustomFieldValues(options = {}) {
  const enabled = options.enabled === true && Boolean(options.workspaceKey)
  const workspaceKey = String(options.workspaceKey || '').trim()
  const rowKey = options.rowKey || 'id'
  const permissionCheck = options.permissionCheck || hasPermission
  const client = options.client || defaultClient
  const definitions = computed(() => {
    const value = unref(options.definitions)
    return Array.isArray(value) ? value.filter(field => field?.custom && field?.key) : []
  })
  const canEditValues = computed(() => enabled
    && Boolean(options.valueUpdatePermission)
    && permissionCheck(options.valueUpdatePermission))
  const valuesByEntity = shallowRef({})
  const loading = ref(false)
  const error = ref('')
  const savingCellKey = ref('')
  let loadRevision = 0
  const saveRevisions = new Map()

  async function load(recordsOrIds = []) {
    const ids = uniqueEntityIds(recordsOrIds, rowKey)
    const revision = ++loadRevision
    if (!enabled || ids.length === 0) {
      valuesByEntity.value = {}
      loading.value = false
      error.value = ''
      return valuesByEntity.value
    }

    loading.value = true
    error.value = ''
    try {
      const result = await client.read(workspaceKey, ids)
      if (revision !== loadRevision) return valuesByEntity.value
      valuesByEntity.value = normalizeValueMap(result)
      return valuesByEntity.value
    } catch (cause) {
      if (revision === loadRevision) {
        valuesByEntity.value = {}
        error.value = errorMessage(cause, '自定义字段值加载失败')
      }
      throw cause
    } finally {
      if (revision === loadRevision) loading.value = false
    }
  }

  function hydrateRows(records = []) {
    if (!Array.isArray(records)) return []
    return records.map((row) => {
      const id = entityId(row, rowKey)
      const values = id == null ? {} : valuesByEntity.value[String(id)] || {}
      return {
        ...row,
        customFields: mergeCustomFieldValues(definitions.value, values)
      }
    })
  }

  function canEditCell(row, field) {
    return canEditValues.value
      && Boolean(entityId(row, rowKey))
      && field?.platformCustom === true
      && definitions.value.some(definition => definition.key === field.key)
      && field?.editable !== false
  }

  function cellKey(row, field) {
    const id = entityId(row, rowKey)
    return id && field?.key ? `${String(id)}::${field.key}` : ''
  }

  async function saveCell(row, field, nextValue) {
    if (!canEditCell(row, field)) {
      return { success: false, message: '无权编辑该自定义字段' }
    }
    const id = entityId(row, rowKey)
    const key = cellKey(row, field)
    const revision = (saveRevisions.get(key) || 0) + 1
    saveRevisions.set(key, revision)
    const currentValues = {
      ...(valuesByEntity.value[String(id)] || {}),
      ...(row.customFields || {})
    }
    const payload = normalizeCustomFieldPayload(definitions.value, {
      ...currentValues,
      [field.key]: nextValue
    })
    savingCellKey.value = key
    error.value = ''
    try {
      await client.save(workspaceKey, id, payload)
      if (saveRevisions.get(key) !== revision) {
        return { success: false, stale: true, message: '该字段已有更新的保存请求' }
      }
      applyValues(row, id, payload)
      return { success: true, values: payload }
    } catch (cause) {
      const message = errorMessage(cause, '自定义字段保存失败')
      if (saveRevisions.get(key) === revision) error.value = message
      return { success: false, message }
    } finally {
      if (saveRevisions.get(key) === revision && savingCellKey.value === key) savingCellKey.value = ''
    }
  }

  async function saveRecord(row, values = {}) {
    if (!canEditValues.value) return { success: false, message: '无权编辑自定义字段' }
    const id = entityId(row, rowKey)
    if (!id) return { success: false, message: '业务记录缺少稳定 ID' }
    const payload = normalizeCustomFieldPayload(definitions.value, values)
    try {
      await client.save(workspaceKey, id, payload)
      applyValues(row, id, payload)
      return { success: true, values: payload }
    } catch (cause) {
      const message = errorMessage(cause, '自定义字段保存失败')
      error.value = message
      return { success: false, message }
    }
  }

  async function batchSave(items = []) {
    if (!canEditValues.value) return { success: false, message: '无权批量编辑自定义字段', results: [] }
    const payload = items
      .map(item => {
        const id = entityId(item.row || item, rowKey)
        if (!id) return null
        return {
          entityId: id,
          values: normalizeCustomFieldPayload(definitions.value, item.values || item.row?.customFields || item.customFields || {})
        }
      })
      .filter(Boolean)
    try {
      const results = await client.batchSave(workspaceKey, payload)
      return { success: (results || []).every(item => item.success !== false), results: results || [] }
    } catch (cause) {
      const message = errorMessage(cause, '自定义字段批量保存失败')
      error.value = message
      return { success: false, message, results: [] }
    }
  }

  function clear() {
    loadRevision += 1
    valuesByEntity.value = {}
    loading.value = false
    error.value = ''
    savingCellKey.value = ''
  }

  function applyValues(row, id, values) {
    const nextValues = { ...values }
    valuesByEntity.value = {
      ...valuesByEntity.value,
      [String(id)]: nextValues
    }
    row.customFields = nextValues
  }

  return {
    enabled,
    workspaceKey,
    definitions,
    canEditValues,
    valuesByEntity,
    loading,
    error,
    savingCellKey,
    load,
    hydrateRows,
    canEditCell,
    cellKey,
    saveCell,
    saveRecord,
    batchSave,
    clear
  }
}

function uniqueEntityIds(recordsOrIds, rowKey) {
  const values = Array.isArray(recordsOrIds) ? recordsOrIds : []
  const result = []
  const seen = new Set()
  values.forEach(item => {
    const id = typeof item === 'object' && item !== null ? entityId(item, rowKey) : item
    if (id === undefined || id === null || id === '' || seen.has(String(id))) return
    seen.add(String(id))
    result.push(id)
  })
  return result
}

function entityId(row, rowKey) {
  return typeof rowKey === 'function' ? rowKey(row) : row?.[rowKey]
}

function normalizeValueMap(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(Object.entries(value).map(([id, fields]) => [
    String(id),
    fields && typeof fields === 'object' && !Array.isArray(fields) ? { ...fields } : {}
  ]))
}

function errorMessage(cause, fallback) {
  return String(
    cause?.response?.data?.message
    || cause?.data?.message
    || cause?.message
    || fallback
  )
}
