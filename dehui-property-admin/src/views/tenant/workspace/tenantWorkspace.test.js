import assert from 'node:assert/strict'
import test from 'node:test'
import {
  actionableLeaseForContract,
  contractBelongsToTenant,
  contractsForTenant,
  filterTenantDirectory,
  mergeWorkspaceRouteQuery,
  normalizeContractRecord,
  normalizeTenantRecord,
  roomsForContracts,
  tenantDirectoryStats
} from './tenantWorkspace.js'

test('tenant normalization keeps one credit-code field and distinct number, short name and invoice fields', () => {
  const tenant = normalizeTenantRecord({
    code: 'TEN-001',
    name: '测试企业',
    shortName: '测试',
    unifiedCreditCode: '91310000TEST',
    logoFileId: 19,
    logoUrl: '/api/files/19',
    logoFileName: '测试企业.png',
    invoiceTitle: '测试企业发票',
    taxNumber: 'TAX-001'
  })

  assert.equal(tenant.tenantCode, 'TEN-001')
  assert.equal(tenant.tenantName, '测试企业')
  assert.equal(tenant.companyShortName, '测试')
  assert.equal(tenant.creditCode, '91310000TEST')
  assert.equal(tenant.logoFileId, 19)
  assert.equal(tenant.logoUrl, '/api/files/19')
  assert.equal(tenant.logoFileName, '测试企业.png')
  assert.equal(tenant.invoiceTitle, '测试企业发票')
  assert.equal(tenant.taxNumber, 'TAX-001')
})

test('tenant logo normalization keeps an unconfigured tenant empty without a brand fallback', () => {
  const tenant = normalizeTenantRecord({ id: 2, name: '未配置 Logo 企业' })

  assert.equal(tenant.logoFileId, null)
  assert.equal(tenant.logoUrl, '')
  assert.equal(tenant.logoFileName, '')
})

test('tenant search and contract filtering keep tenant ownership exact', () => {
  const tenants = [
    { id: 1, tenantName: '德汇科技', tenantCode: 'T-1', contactName: '张三' },
    { id: 2, tenantName: '创新服务', tenantCode: 'T-2', phone: '13800000000' }
  ]
  const contracts = [
    { id: 11, tenantId: 1, status: 'ACTIVE' },
    { id: 12, tenantId: 1, status: 'DRAFT' },
    { id: 21, tenantId: 2, status: 'ACTIVE' }
  ]

  assert.deepEqual(filterTenantDirectory(tenants, '张三').map(item => item.id), [1])
  assert.deepEqual(contractsForTenant(contracts, 1, 'ALL').map(item => item.id), [11, 12])
  assert.deepEqual(contractsForTenant(contracts, 1, 'ACTIVE').map(item => item.id), [11])
  assert.deepEqual(contractsForTenant(contracts, 1, 'PENDING').map(item => item.id), [12])
  assert.equal(contractBelongsToTenant(contracts[0], 1), true)
  assert.equal(contractBelongsToTenant(contracts[2], 1), false)
})

test('multi-room contract normalization and directory stats never keep only the first room', () => {
  const contracts = [
    normalizeContractRecord({ id: 10, tenantId: 1, status: 'ACTIVE', roomIds: [101, 102], roomNames: ['A101', 'A102'] }),
    normalizeContractRecord({ id: 11, tenantId: 1, status: 'TERMINATED', roomIds: [103], roomNames: ['A103'] })
  ]
  const stats = tenantDirectoryStats(contracts).get(1)

  assert.deepEqual(contracts[0].roomIds, [101, 102])
  assert.equal(contracts[0].roomSummary, 'A101、A102')
  assert.deepEqual(stats, { activeContracts: 1, roomCount: 3 })
})

test('tenant and contract room rows preserve stable contract-room keys and occupancy state', () => {
  const contracts = [{ id: 10, contractNo: 'C-10', status: 'ACTIVE', roomIds: [101, 102], roomNames: ['A101', 'A102'] }]
  const rooms = [
    { id: 101, buildingName: 'A座', floorName: '1层', roomNumber: '101' },
    { id: 102, buildingName: 'A座', floorName: '1层', roomNumber: '102' }
  ]
  const leases = [{ contractId: 10, status: 'ACTIVE', roomIds: [101, 102], roomStatuses: ['ACTIVE', 'PENDING_CHECKIN'] }]
  const rows = roomsForContracts(contracts, rooms, leases)

  assert.deepEqual(rows.map(item => item.id), ['10:101', '10:102'])
  assert.deepEqual(rows.map(item => item.occupancyStatus), ['ACTIVE', 'PENDING_CHECKIN'])
})

test('checkout requires one unambiguous actionable lease', () => {
  const leases = [
    { id: 1, contractId: 10, status: 'ACTIVE' },
    { id: 2, contractId: 10, status: 'TERMINATED' }
  ]
  assert.equal(actionableLeaseForContract(leases, 10)?.id, 1)
  assert.equal(actionableLeaseForContract([...leases, { id: 3, contractId: 10, status: 'CHECKOUT_PENDING' }], 10), null)
})

test('workspace query merge preserves unrelated deep-link state and removes invalid selections', () => {
  assert.deepEqual(
    mergeWorkspaceRouteQuery({ source: 'dashboard', tenantId: '1', contractId: '2' }, { tenantId: 3, contractId: undefined, tab: 'contacts' }),
    { source: 'dashboard', tenantId: '3', tab: 'contacts' }
  )
})
