const CLOSED_CONTRACT_STATUSES = new Set(['TERMINATED', 'CANCELLED', 'EXPIRED'])
const ACTIONABLE_LEASE_STATUSES = new Set([
  'PENDING_CHECKIN',
  'PARTIALLY_ACTIVE',
  'ACTIVE',
  'RENEWING',
  'CHECKOUT_PENDING',
  'PARTIALLY_TERMINATED'
])

export function normalizeTenantRecord(record = {}) {
  return {
    ...record,
    tenantCode: record.tenantCode ?? record.code ?? '',
    tenantName: record.tenantName ?? record.name ?? '',
    logoFileId: record.logoFileId ?? null,
    logoUrl: record.logoUrl ?? '',
    logoFileName: record.logoFileName ?? '',
    companyShortName: record.companyShortName ?? record.shortName ?? '',
    contactName: record.contactName ?? record.contactPerson ?? '',
    phone: record.phone ?? record.contactPhone ?? '',
    email: record.email ?? record.contactEmail ?? '',
    creditCode: record.unifiedCreditCode ?? record.creditCode ?? record.businessLicense ?? '',
    invoiceTitle: record.invoiceTitle ?? '',
    taxNumber: record.taxNumber ?? '',
    serviceManagerUserId: record.serviceManagerUserId ?? null
  }
}

export function normalizeContractRecord(record = {}) {
  const roomIds = uniqueValues(record.roomIds || (record.roomId ? [record.roomId] : []), Number)
  const roomNames = uniqueValues(record.roomNames || (record.roomName ? [record.roomName] : []), String)
  return {
    ...record,
    contractNo: record.contractNo ?? record.contractNumber ?? record.code ?? '',
    roomIds,
    roomNames,
    roomSummary: record.roomSummary || roomNames.join('、') || '未关联房间'
  }
}

export function filterTenantDirectory(records = [], keyword = '') {
  const normalized = String(keyword || '').trim().toLowerCase()
  if (!normalized) return [...records]
  return records.filter(record => [
    record.tenantName,
    record.tenantCode,
    record.contactName,
    record.phone,
    record.email,
    record.creditCode,
    record.companyShortName
  ].some(value => String(value || '').toLowerCase().includes(normalized)))
}

export function contractsForTenant(records = [], tenantId, status = 'ALL') {
  const id = Number(tenantId)
  return records.filter(contract => {
    if (Number(contract.tenantId) !== id) return false
    if (status === 'ALL') return true
    if (status === 'PENDING') return ['DRAFT', 'PENDING', 'PENDING_APPROVAL', 'SUSPENDED'].includes(contract.status)
    if (status === 'CLOSED') return CLOSED_CONTRACT_STATUSES.has(contract.status)
    return contract.status === status
  })
}

export function tenantDirectoryStats(contracts = []) {
  const result = new Map()
  contracts.forEach(contract => {
    const key = Number(contract.tenantId)
    const current = result.get(key) || { activeContracts: 0, roomIds: new Set() }
    if (!CLOSED_CONTRACT_STATUSES.has(contract.status)) current.activeContracts += 1
    ;(contract.roomIds || []).forEach(roomId => current.roomIds.add(Number(roomId)))
    result.set(key, current)
  })
  return new Map([...result].map(([tenantId, value]) => [tenantId, {
    activeContracts: value.activeContracts,
    roomCount: value.roomIds.size
  }]))
}

export function roomsForContracts(contracts = [], roomRecords = [], leases = []) {
  const roomById = new Map(roomRecords.map(room => [Number(room.id), room]))
  const leaseRoomStatus = new Map()
  leases.forEach(lease => {
    ;(lease.roomIds || []).forEach((roomId, index) => {
      leaseRoomStatus.set(Number(roomId), lease.roomStatuses?.[index] || lease.status || '')
    })
  })
  const rows = []
  contracts.forEach(contract => {
    ;(contract.roomIds || []).forEach((roomId, index) => {
      const id = Number(roomId)
      const room = roomById.get(id) || {}
      rows.push({
        ...room,
        id: `${contract.id}:${id}`,
        roomId: id,
        roomNumber: room.roomNumber || room.roomName || contract.roomNames?.[index] || `房间 ${id}`,
        roomName: room.roomName || room.roomNumber || contract.roomNames?.[index] || `房间 ${id}`,
        contractId: contract.id,
        contractNo: contract.contractNo,
        contractStatus: contract.status,
        occupancyStatus: leaseRoomStatus.get(id) || ''
      })
    })
  })
  return rows
}

export function actionableLeaseForContract(leases = [], contractId) {
  const matches = leases.filter(lease => (
    Number(lease.contractId) === Number(contractId)
    && ACTIONABLE_LEASE_STATUSES.has(lease.status)
  ))
  return matches.length === 1 ? matches[0] : null
}

export function contractBelongsToTenant(contract, tenantId) {
  return Boolean(contract) && Number(contract.tenantId) === Number(tenantId)
}

export function mergeWorkspaceRouteQuery(currentQuery = {}, patch = {}) {
  const next = { ...currentQuery }
  Object.entries(patch).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') delete next[key]
    else next[key] = String(value)
  })
  return next
}

function uniqueValues(values, convert) {
  return [...new Set((Array.isArray(values) ? values : []).map(convert).filter(value => (
    value !== '' && value !== null && value !== undefined && !(typeof value === 'number' && Number.isNaN(value))
  )))]
}
