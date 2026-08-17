import { computed, ref } from 'vue'
import { useWorkspaceCustomFields } from '../../../composables/workspace/useWorkspaceCustomFields.js'
import { useWorkspaceCustomFieldValues } from '../../../composables/workspace/useWorkspaceCustomFieldValues.js'
import request from '../../../utils/request.js'
import { readPage } from '../../../utils/pagination.js'
import { hasPermission } from '../../../utils/permission.js'
import { createLatestRequestGuard } from '../../../utils/requestLifecycle.js'
import { loadAllWorkspaceRecords } from '../../../utils/workspaceExport.js'
import { normalizeContractRecord, normalizeTenantRecord } from './tenantWorkspace.js'

const contractTransitionEndpoints = Object.freeze({
  submit: contractId => `/contracts/${contractId}/submit`,
  approve: contractId => `/contracts/${contractId}/approve`,
  activate: contractId => `/contracts/${contractId}/activate`,
  terminate: contractId => `/contracts/${contractId}/terminate`,
  cancel: contractId => `/contracts/${contractId}/cancel`
})

export function useTenantWorkspace() {
  const tenants = ref([])
  const contracts = ref([])
  const rooms = ref([])
  const archiveRooms = ref([])
  const parkingSpaces = ref([])
  const contacts = ref([])
  const leases = ref([])
  const bills = ref([])
  const history = ref([])
  const tenantOverview = ref(null)
  const serviceManagers = ref([])
  const directoryPage = ref(1)
  const directoryPageSize = ref(30)
  const directoryTotal = ref(0)
  const directoryLoading = ref(false)
  const detailLoading = ref(false)
  const saving = ref(false)
  const directoryError = ref('')
  const detailError = ref('')
  const directoryGuard = createLatestRequestGuard()
  const detailGuard = createLatestRequestGuard()

  const tenantFields = useWorkspaceCustomFields({
    workspaceKey: 'tenants',
    enabled: hasPermission('tenant:view'),
    valueUpdatePermission: 'tenant:update'
  })
  const contractFields = useWorkspaceCustomFields({
    workspaceKey: 'contracts',
    enabled: hasPermission('contract:view'),
    valueUpdatePermission: 'contract:update'
  })
  const tenantValues = useWorkspaceCustomFieldValues({
    workspaceKey: 'tenants',
    enabled: tenantFields.enabled,
    definitions: tenantFields.activeDefinitions,
    valueUpdatePermission: 'tenant:update'
  })
  const contractValues = useWorkspaceCustomFieldValues({
    workspaceKey: 'contracts',
    enabled: contractFields.enabled,
    definitions: contractFields.activeDefinitions,
    valueUpdatePermission: 'contract:update'
  })

  async function loadDirectory(options = {}) {
    const requestId = directoryGuard.start()
    directoryLoading.value = true
    directoryError.value = ''
    const tenantTask = hasPermission('tenant:view')
      ? request.get('/tenants/search', {
        params: {
          keyword: options.keyword || undefined,
          status: options.status || undefined,
          page: options.page || directoryPage.value,
          pageSize: options.pageSize || directoryPageSize.value
        },
        silent: true
      })
      : Promise.resolve({ records: [], total: 0, page: 1, pageSize: directoryPageSize.value })
    const roomTask = hasPermission('room:view')
      ? loadAllWorkspaceRecords(({ page, pageSize }) => request.get('/rooms', {
        params: { page, pageSize },
        silent: true
      }), { pageSize: 100, maxRecords: 10000 })
      : Promise.resolve([])
    const fieldTasks = [
      tenantFields.enabled ? tenantFields.load({ includeDisabled: tenantFields.canManage.value }) : Promise.resolve([]),
      contractFields.enabled ? contractFields.load({ includeDisabled: contractFields.canManage.value }) : Promise.resolve([])
    ]

    try {
      const [tenantResult, roomResult] = await Promise.allSettled([
        tenantTask,
        roomTask,
        ...fieldTasks
      ])
      if (!directoryGuard.isCurrent(requestId)) return

      const tenantPage = tenantResult.status === 'fulfilled'
        ? readPage(tenantResult.value)
        : readPage([])
      const loadedTenants = tenantResult.status === 'fulfilled'
        ? tenantPage.records.map(normalizeTenantRecord)
        : []
      tenants.value = loadedTenants
      directoryPage.value = Number(tenantPage.page || options.page || 1)
      directoryPageSize.value = Number(tenantPage.pageSize || options.pageSize || directoryPageSize.value)
      directoryTotal.value = Number(tenantPage.total || loadedTenants.length)
      rooms.value = roomResult.status === 'fulfilled' ? readPage(roomResult.value).records : []

      const failures = [tenantResult, roomResult]
        .filter(result => result.status === 'rejected')
        .map(result => requestError(result.reason))
      directoryError.value = failures.join('；')

      await hydrateCustomValues(requestId)
    } finally {
      if (directoryGuard.isCurrent(requestId)) directoryLoading.value = false
    }
  }

  async function hydrateCustomValues(requestId) {
    const tasks = []
    if (tenantValues.enabled && tenants.value.length) {
      tasks.push(tenantValues.load(tenants.value).then(() => {
        if (directoryGuard.isCurrent(requestId)) tenants.value = tenantValues.hydrateRows(tenants.value)
      }))
    }
    if (contractValues.enabled && contracts.value.length) {
      tasks.push(contractValues.load(contracts.value).then(() => {
        if (directoryGuard.isCurrent(requestId)) contracts.value = contractValues.hydrateRows(contracts.value)
      }))
    }
    await Promise.allSettled(tasks)
  }

  async function loadTenantContext(tenantId) {
    const requestId = detailGuard.start()
    detailLoading.value = true
    detailError.value = ''
    contacts.value = []
    archiveRooms.value = []
    parkingSpaces.value = []
    leases.value = []
    bills.value = []
    history.value = []
    tenantOverview.value = null

    const tasks = [
      hasPermission('tenant:view')
        ? request.get(`/tenants/${tenantId}/archive/overview`, { silent: true })
        : Promise.resolve(null),
      hasPermission('tenant:view')
        ? request.get(`/tenants/${tenantId}/archive/contacts`, {
          params: { page: 1, pageSize: 100 },
          silent: true
        })
        : Promise.resolve([]),
      hasPermission('tenant:view')
        ? request.get(`/tenants/${tenantId}/archive/spaces`, {
          params: { page: 1, pageSize: 100 },
          silent: true
        })
        : Promise.resolve([]),
      hasPermission('tenant:view')
        ? request.get(`/tenants/${tenantId}/archive/contracts`, {
          params: { page: 1, pageSize: 100 },
          silent: true
        })
        : Promise.resolve([]),
      hasPermission('lease:view')
        ? loadPaged('/leases/search', { tenantId })
        : Promise.resolve([]),
      hasPermission('bill:view')
        ? request.get(`/tenants/${tenantId}/archive/bills`, {
          params: { page: 1, pageSize: 100 },
          silent: true
        })
        : Promise.resolve([])
    ]

    try {
      const [overviewResult, contactResult, spaceResult, contractResult, leaseResult, billResult] = await Promise.allSettled(tasks)
      if (!detailGuard.isCurrent(requestId)) return
      tenantOverview.value = overviewResult.status === 'fulfilled' ? overviewResult.value : null
      contacts.value = contactResult.status === 'fulfilled'
        ? readPage(contactResult.value).records.map(normalizeContactRecord)
        : []
      const spaces = spaceResult.status === 'fulfilled' ? spaceResult.value || {} : {}
      archiveRooms.value = readPage(spaces.rooms || []).records.map(normalizeRoomSpace)
      parkingSpaces.value = readPage(spaces.parkingSpaces || []).records
      contracts.value = contractResult.status === 'fulfilled'
        ? readPage(contractResult.value).records.map(record => normalizeContractRecord({ ...record, tenantId }))
        : []
      leases.value = leaseResult.status === 'fulfilled' ? readPage(leaseResult.value).records : []
      bills.value = billResult.status === 'fulfilled' ? readPage(billResult.value).records : []
      detailError.value = [overviewResult, contactResult, spaceResult, contractResult, leaseResult, billResult]
        .filter(result => result.status === 'rejected')
        .map(result => requestError(result.reason))
        .join('；')
    } finally {
      if (detailGuard.isCurrent(requestId)) detailLoading.value = false
    }
  }

  async function loadContractContext(tenantId, contractId) {
    const requestId = detailGuard.start()
    detailLoading.value = true
    detailError.value = ''
    bills.value = []
    history.value = []

    const tasks = [
      hasPermission('bill:view')
        ? request.get(`/tenants/${tenantId}/archive/bills`, {
          params: { contractId, page: 1, pageSize: 100 },
          silent: true
        })
        : Promise.resolve([]),
      hasPermission('contract:view')
        ? request.get(`/contracts/${contractId}/history`, { silent: true })
        : Promise.resolve([])
    ]

    try {
      const [billResult, historyResult] = await Promise.allSettled(tasks)
      if (!detailGuard.isCurrent(requestId)) return
      bills.value = billResult.status === 'fulfilled' ? readPage(billResult.value).records : []
      history.value = historyResult.status === 'fulfilled'
        ? readPage(historyResult.value).records.map((item, index) => ({
          ...item,
          rowKey: `${item.sourceType || 'history'}:${item.sourceId || index}:${item.occurredAt || index}`
        }))
        : []
      detailError.value = [billResult, historyResult]
        .filter(result => result.status === 'rejected')
        .map(result => requestError(result.reason))
        .join('；')
    } finally {
      if (detailGuard.isCurrent(requestId)) detailLoading.value = false
    }
  }

  async function saveTenant(payload) {
    return runSaving(() => request.post('/tenant/save', payload))
  }

  async function updateTenantLogo(tenantId, logoFileId) {
    return runSaving(() => request.put(`/tenants/${tenantId}/logo`, {
      logoFileId: logoFileId ?? null
    }))
  }

  async function deleteTenant(id) {
    return runSaving(() => request.delete(`/tenants/${id}`))
  }

  async function saveContact(tenantId, payload) {
    return runSaving(() => request.post(`/tenant/${tenantId}/contacts`, payload))
  }

  async function deactivateContact(id) {
    return runSaving(() => request.post(`/tenant/contacts/${id}/deactivate`))
  }

  async function deleteContact(tenantId, id) {
    return runSaving(() => request.delete(`/tenant/${tenantId}/contacts/${id}`))
  }

  async function setResponsibleContact(id) {
    return runSaving(() => request.post(`/tenant/contacts/${id}/responsible`))
  }

  async function setEmergencyContact(id) {
    return runSaving(() => request.post(`/tenant/contacts/${id}/emergency`))
  }

  async function loadServiceManagers(keyword = '') {
    if (!hasPermission('tenant:view')) {
      serviceManagers.value = []
      return []
    }
    const records = await request.get('/tenants/service-managers', {
      params: { keyword: keyword || undefined },
      silent: true
    })
    serviceManagers.value = Array.isArray(records) ? records : []
    return serviceManagers.value
  }

  async function saveContract(payload, source) {
    return runSaving(() => source?.id
      ? request.put(`/contracts/${source.id}`, payload)
      : request.post('/contracts', payload))
  }

  async function transitionContract(contract, action, reason) {
    const endpoint = contractTransitionEndpoints[action]
    if (!endpoint) throw new Error(`不支持的合同状态操作：${action}`)
    return runSaving(() => request.post(endpoint(contract.id), {
      version: contract.version,
      reason
    }))
  }

  async function checkInContract(contract, payload) {
    return runSaving(() => request.post(`/contracts/${contract.id}/check-in`, payload))
  }

  async function checkOutLease(lease, payload) {
    return runSaving(() => request.post(`/leases/${lease.id}/check-out`, payload))
  }

  async function runSaving(task) {
    saving.value = true
    try {
      return await task()
    } finally {
      saving.value = false
    }
  }

  async function loadPaged(path, params = {}) {
    return loadAllWorkspaceRecords(({ page, pageSize }) => request.get(path, {
      params: { ...params, page, pageSize },
      silent: true
    }), { pageSize: 100, maxRecords: 10000 })
  }

  function clearDetail() {
    detailGuard.start()
    contacts.value = []
    contracts.value = []
    archiveRooms.value = []
    parkingSpaces.value = []
    leases.value = []
    bills.value = []
    history.value = []
    tenantOverview.value = null
    detailError.value = ''
    detailLoading.value = false
  }

  return {
    tenants,
    contracts,
    rooms,
    archiveRooms,
    parkingSpaces,
    contacts,
    leases,
    bills,
    history,
    tenantOverview,
    serviceManagers,
    directoryPage,
    directoryPageSize,
    directoryTotal,
    directoryLoading,
    detailLoading,
    saving,
    directoryError,
    detailError,
    tenantFields,
    contractFields,
    tenantValues,
    contractValues,
    loadDirectory,
    loadTenantContext,
    loadContractContext,
    clearDetail,
    saveTenant,
    updateTenantLogo,
    deleteTenant,
    saveContact,
    deactivateContact,
    deleteContact,
    setResponsibleContact,
    setEmergencyContact,
    loadServiceManagers,
    saveContract,
    transitionContract,
    checkInContract,
    checkOutLease
  }
}

function normalizeContactRecord(record = {}) {
  return {
    ...record,
    isPrimary: Boolean(record.isPrimary ?? record.primary),
    isEmergency: Boolean(record.isEmergency ?? record.emergency)
  }
}

function normalizeRoomSpace(record = {}) {
  return {
    ...record,
    id: record.rowKey || `${record.contractId}:${record.roomId}`,
    roomId: record.roomId,
    roomNumber: record.roomNumber || '',
    roomName: record.roomNumber || '',
    contractNo: record.contractNumber || record.contractNo || '',
    status: record.current ? 'CURRENT' : 'HISTORY'
  }
}

function requestError(error) {
  return String(error?.response?.data?.message || error?.message || '请求失败')
}
