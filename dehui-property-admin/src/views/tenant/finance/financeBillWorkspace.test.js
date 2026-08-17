import assert from 'node:assert/strict'
import test from 'node:test'
import {
  contractsForFinanceTenant,
  filterFinanceTenants,
  financeScopeParams,
  mergeFinanceRouteQuery,
  resolveFinanceRouteScope
} from './financeBillWorkspace.js'

const tenants = [
  { id: 6, tenantName: '上海云启科技有限公司', tenantCode: 'DEMO-TEN-001', contactPerson: '张三', contactPhone: '13800010001' },
  { id: 7, tenantName: '德汇咖啡服务有限公司', tenantCode: 'DEMO-TEN-002', contactPerson: '李四', contactPhone: '13800010002' }
]
const contracts = [
  { id: 11, tenantId: 6, contractNumber: 'DEMO-CON-001' },
  { id: 12, tenantId: 7, contractNumber: 'DEMO-CON-002' }
]

test('finance route restores a valid tenant, contract and tab', () => {
  assert.deepEqual(
    resolveFinanceRouteScope({ tenantId: '6', contractId: '11', tab: 'payments' }, tenants, contracts),
    { tenantId: 6, contractId: 11, tab: 'payments', allTenants: false }
  )
})

test('finance route rejects a cross-tenant contract and invalid tab', () => {
  assert.deepEqual(
    resolveFinanceRouteScope({ tenantId: '6', contractId: '12', tab: 'unknown' }, tenants, contracts),
    { tenantId: 6, contractId: null, tab: 'bills', allTenants: false }
  )
})

test('a contract deep link derives its owning tenant unless all tenants is explicit', () => {
  assert.deepEqual(
    resolveFinanceRouteScope({ contractId: '12', tab: 'invoices' }, tenants, contracts),
    { tenantId: 7, contractId: 12, tab: 'invoices', allTenants: false }
  )
  assert.deepEqual(
    resolveFinanceRouteScope({ tenantId: 'all', contractId: '12' }, tenants, contracts),
    { tenantId: null, contractId: null, tab: 'bills', allTenants: true }
  )
})

test('scope serialization omits global filters and preserves unrelated query values', () => {
  assert.deepEqual(financeScopeParams({ tenantId: null, contractId: null }), {})
  assert.deepEqual(financeScopeParams({ tenantId: 6, contractId: 11 }), { tenantId: 6, contractId: 11 })
  assert.deepEqual(
    mergeFinanceRouteQuery({ billType: 'RENT', keyword: '2026' }, { tenantId: 6, contractId: 11, tab: 'bills' }),
    { billType: 'RENT', keyword: '2026', tenantId: 6, contractId: 11, tab: 'bills' }
  )
  assert.deepEqual(
    mergeFinanceRouteQuery({ contractId: '11' }, { tenantId: null, contractId: null, tab: 'overview' }),
    { tenantId: 'all', tab: 'overview' }
  )
})

test('tenant search and contract options use real tenant ownership', () => {
  assert.deepEqual(filterFinanceTenants(tenants, '云启').map(item => item.id), [6])
  assert.deepEqual(filterFinanceTenants(tenants, '13800010002').map(item => item.id), [7])
  assert.deepEqual(contractsForFinanceTenant(contracts, 6).map(item => item.id), [11])
  assert.deepEqual(contractsForFinanceTenant(contracts, null), [])
})
