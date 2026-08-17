export const FINANCE_WORKSPACE_TABS = Object.freeze(['overview', 'bills', 'bases', 'payments', 'invoices'])

export function filterFinanceTenants(tenants, keyword) {
  const normalized = String(keyword || '').trim().toLowerCase()
  if (!normalized) return [...(tenants || [])]
  return (tenants || []).filter(tenant => [
    tenant.tenantName,
    tenant.tenantCode,
    tenant.contactPerson,
    tenant.contactPhone
  ].some(value => String(value || '').toLowerCase().includes(normalized)))
}

export function resolveFinanceRouteScope(query, tenants, contracts) {
  const tenantList = tenants || []
  const contractList = contracts || []
  const requestedTab = String(query?.tab || 'bills')
  const tab = FINANCE_WORKSPACE_TABS.includes(requestedTab) ? requestedTab : 'bills'
  const allTenants = String(query?.tenantId || '').toLowerCase() === 'all'
  const requestedTenantId = allTenants ? null : positiveId(query?.tenantId)
  const requestedContractId = positiveId(query?.contractId)
  const tenantIdSet = new Set(tenantList.map(item => Number(item.id)))
  const contract = contractList.find(item => Number(item.id) === requestedContractId) || null

  let tenantId = requestedTenantId && tenantIdSet.has(requestedTenantId) ? requestedTenantId : null
  let contractId = null
  if (!allTenants && contract) {
    const contractTenantId = positiveId(contract.tenantId)
    if (!tenantId && contractTenantId && tenantIdSet.has(contractTenantId)) tenantId = contractTenantId
    if (tenantId && contractTenantId === tenantId) contractId = Number(contract.id)
  }

  return {
    tenantId,
    contractId,
    tab,
    allTenants: allTenants || tenantId === null
  }
}

export function financeScopeParams(scope) {
  return {
    ...(scope?.tenantId ? { tenantId: Number(scope.tenantId) } : {}),
    ...(scope?.contractId ? { contractId: Number(scope.contractId) } : {})
  }
}

export function mergeFinanceRouteQuery(currentQuery, nextScope) {
  const query = { ...(currentQuery || {}) }
  const tenantId = positiveId(nextScope?.tenantId)
  const contractId = positiveId(nextScope?.contractId)
  query.tenantId = tenantId || 'all'
  if (tenantId && contractId) query.contractId = contractId
  else delete query.contractId
  query.tab = FINANCE_WORKSPACE_TABS.includes(nextScope?.tab) ? nextScope.tab : 'bills'
  return query
}

export function contractsForFinanceTenant(contracts, tenantId) {
  const normalizedTenantId = positiveId(tenantId)
  if (!normalizedTenantId) return []
  return (contracts || []).filter(contract => Number(contract.tenantId) === normalizedTenantId)
}

function positiveId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}
