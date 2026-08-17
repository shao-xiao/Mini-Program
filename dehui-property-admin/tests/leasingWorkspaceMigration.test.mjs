import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

const pages = {
  lease: read('src/views/tenant/LeaseList.vue'),
  feeRule: read('src/views/tenant/FeeRuleList.vue'),
  billingPlan: read('src/views/tenant/BillingPlanList.vue')
}

test('lease, fee rule and billing plan main tables use the shared workspace stack', () => {
  for (const [name, source] of Object.entries(pages)) {
    assert.match(source, /<DataWorkspace\b/, `${name} must render DataWorkspace`)
    assert.match(source, /<DhicDataToolbar\b/, `${name} must render DhicDataToolbar`)
    assert.match(source, /<WorkspaceDataGrid\b/, `${name} must render WorkspaceDataGrid`)
    assert.doesNotMatch(source, /<el-table(?:\s|>)/, `${name} must not retain a raw table`)
    assert.match(source, /\.\.\.serverQueryParams\.value/)
    assert.match(source, /\.\.\.pageParams\(pagination\)/)
  }
})

test('leasing pages keep their real APIs and page-owned business mutations', () => {
  assert.match(pages.lease, /request\.get\('\/leases\/search'/)
  assert.match(pages.lease, /request\.post\('\/leases'/)
  assert.match(pages.lease, /`\/leases\/\$\{lifecycleLease\.value\.id\}\/check-in`/)
  assert.match(pages.lease, /`\/leases\/\$\{lifecycleLease\.value\.id\}\/check-out`/)

  assert.match(pages.feeRule, /request\.get\('\/fee-rules\/search'/)
  assert.match(pages.feeRule, /request\.post\('\/fee-rules'/)
  assert.match(pages.feeRule, /request\.put\(`\/fee-rules\/\$\{editingRule\.value\.id\}`/)
  assert.match(pages.feeRule, /`\/fee-rules\/\$\{row\.id\}\/activate`/)
  assert.match(pages.feeRule, /`\/fee-rules\/\$\{row\.id\}\/disable`/)

  assert.match(pages.billingPlan, /request\.get\('\/billing-plans\/search'/)
  assert.match(pages.billingPlan, /request\.post\('\/billing-plans'/)
  assert.match(pages.billingPlan, /request\.put\(`\/billing-plans\/\$\{editingPlan\.value\.id\}`/)
  assert.match(pages.billingPlan, /`\/billing-plans\/\$\{row\.id\}\/activate`/)
  assert.match(pages.billingPlan, /`\/billing-plans\/\$\{row\.id\}\/terminate`/)
})

test('leasing workspace actions remain permission-code based', () => {
  assert.doesNotMatch(Object.values(pages).join('\n'), /role\s*===\s*['"]ADMIN['"]/)
  assert.match(pages.lease, /hasPermission\('lease:create'\)/)
  assert.match(pages.lease, /hasPermission\('lease:checkin'\)/)
  assert.match(pages.lease, /hasPermission\('lease:checkout'\)/)
  assert.match(pages.feeRule, /hasPermission\('fee-rule:create'\)/)
  assert.match(pages.feeRule, /hasPermission\('fee-rule:update'\)/)
  assert.match(pages.billingPlan, /hasPermission\('billing-plan:create'\)/)
  assert.match(pages.billingPlan, /hasPermission\('billing-plan:update'\)/)
})
