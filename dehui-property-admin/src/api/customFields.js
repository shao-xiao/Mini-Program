import request from '../utils/request.js'
import { normalizeCustomFieldDefinition, normalizeCustomFieldDefinitions } from '../utils/customFieldSchema.js'

const withWorkspaceKey = (module, definition) => ({
  ...definition,
  workspaceKey: module
})

export async function listCustomFieldDefinitions(module, options = {}) {
  const definitions = await request.get(`/workspaces/${module}/custom-fields`, {
    params: { includeDisabled: Boolean(options.includeDisabled) },
    silent: options.silent !== false
  })
  return normalizeCustomFieldDefinitions(definitions)
    .map(definition => withWorkspaceKey(module, definition))
}

export function getModuleFieldSchema(module, surface, options = {}) {
  return request.get(`/module-fields/${module}`, {
    params: surface ? { surface } : {},
    silent: options.silent !== false
  })
}

export function updateModuleSystemField(module, fieldKey, data) {
  return request.put(`/module-fields/${module}/${fieldKey}`, data)
}

export function createCustomFieldDefinition(module, data) {
  return request.post(`/workspaces/${module}/custom-fields`, data)
    .then(normalizeCustomFieldDefinition)
    .then(definition => withWorkspaceKey(module, definition))
}

export function updateCustomFieldDefinition(module, fieldId, data) {
  return request.put(`/workspaces/${module}/custom-fields/${fieldId}`, data)
    .then(normalizeCustomFieldDefinition)
    .then(definition => withWorkspaceKey(module, definition))
}

export function changeCustomFieldDefinitionStatus(module, fieldId, status) {
  const requestCall = status === 'ENABLED'
    ? request.post(`/workspaces/${module}/custom-fields/${fieldId}/enable`)
    : request.post(`/workspaces/${module}/custom-fields/${fieldId}/disable`)
  return requestCall
    .then(normalizeCustomFieldDefinition)
    .then(definition => withWorkspaceKey(module, definition))
}

export function deleteCustomFieldDefinition(module, fieldId) {
  return request.delete(`/workspaces/${module}/custom-fields/${fieldId}`)
}

export function reorderCustomFieldDefinitions(module, order) {
  return request.post(`/workspaces/${module}/custom-fields/reorder`, order)
}

export function listCustomFieldValues(module, recordIds = []) {
  const ids = recordIds.filter(Boolean)
  if (ids.length === 0) return Promise.resolve({})
  return request.get(`/custom-fields/${module}/values`, {
    params: { recordIds: ids.join(',') },
    silent: true
  })
}

export function saveCustomFieldValues(module, recordId, values) {
  if (!recordId) return Promise.resolve()
  return request.put(`/custom-fields/${module}/records/${recordId}`, values || {})
}

export function batchReadCustomFieldValues(module, entityIds = []) {
  const ids = entityIds.filter(Boolean)
  if (ids.length === 0) return Promise.resolve({})
  return request.post(`/workspaces/${module}/custom-field-values/batch-read`, { entityIds: ids }, { silent: true })
}

export function batchUpdateCustomFieldValues(module, items = []) {
  if (!items.length) return Promise.resolve([])
  return request.put(`/workspaces/${module}/custom-field-values/batch-update`, { items })
}

export function uploadCustomFieldAttachment(file, options = {}) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('fieldType', options.fieldType || 'ATTACHMENT')
  if (options.workspaceKey) formData.append('workspaceKey', options.workspaceKey)
  const { fieldType, workspaceKey, ...requestOptions } = options
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...requestOptions
  })
}
