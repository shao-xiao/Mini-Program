import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const source = fs.readFileSync(path.join(root, 'src/views/tenant/ContractList.vue'), 'utf8')

test('contract list uses the shared workspace display stack', () => {
  assert.match(source, /<DataWorkspace[\s\S]*:workspace-config="contractWorkspaceConfig"/)
  assert.match(source, /<DhicDataToolbar\b/)
  assert.match(source, /<WorkspaceDataGrid\b/)
  assert.match(source, /:metric-data="contractMetricData"/)
  assert.doesNotMatch(source, /<el-table(?:\s|>)/)
  assert.doesNotMatch(source, /ListSummaryGrid/)
})

test('contract business mutations and electronic file operations remain page owned without bill generation', () => {
  assert.match(source, /request\.get\('\/contracts'/)
  assert.match(source, /request\.post\('\/contracts', payload\(\)\)/)
  assert.match(source, /request\.put\(`\/contracts\/\$\{form\.id\}`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/submit`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/approve`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/activate`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/check-in`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/terminate`/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/cancel`/)
  assert.doesNotMatch(source, /\/reactivate/)
  assert.match(source, /version:\s*row\.version/)
  assert.match(source, /request\.post\(`\/contracts\/\$\{row\.id\}\/file`/)
  assert.match(source, /request\.get\(`\/contracts\/\$\{row\.id\}\/file\/download`/)
  assert.doesNotMatch(source, /\/contracts\/generate-bills/)
})

test('contract workspace migration preserves permission checks and excludes bulk capabilities', () => {
  assert.match(source, /hasPermission\('contract:create'\)/)
  assert.match(source, /hasPermission\('contract:update'\)/)
  assert.match(source, /hasPermission\('contract:approve'\)/)
  assert.match(source, /hasPermission\('contract:activate'\)/)
  assert.match(source, /hasPermission\('lease:checkin'\)/)
  assert.match(source, /hasPermission\('contract:terminate'\)/)
  assert.match(source, /hasPermission\('contract:cancel'\)/)
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
  assert.doesNotMatch(source, /<ImportExportManager\b/)
  assert.doesNotMatch(source, /<BatchToolbar\b/)
  assert.doesNotMatch(source, /propertyFeeAmount/)
  assert.doesNotMatch(source, /paymentCycle/)
  assert.doesNotMatch(source, /billingLeadDays/)
})
