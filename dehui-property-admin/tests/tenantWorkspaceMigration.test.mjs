import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const source = fs.readFileSync(path.join(root, 'src/views/tenant/TenantList.vue'), 'utf8')
const mainListTemplate = source.split('<el-dialog')[0]

test('tenant main list uses the shared workspace stack and built-in metric panel', () => {
  assert.match(source, /<DataWorkspace[\s\S]*:workspace-config="tenantWorkspaceConfig"/)
  assert.match(source, /<DhicDataToolbar\b/)
  assert.match(source, /<WorkspaceDataGrid\b/)
  assert.match(source, /:metric-data="tenantMetricData"/)
  assert.doesNotMatch(mainListTemplate, /<el-table(?:\s|>)/)
  assert.doesNotMatch(source, /ListSummaryGrid/)
})

test('tenant page preserves real CRUD, contacts and overview endpoints', () => {
  assert.match(source, /request\.get\('\/tenant\/list'/)
  assert.match(source, /request\.post\('\/tenant\/save'/)
  assert.match(source, /request\.delete\(`\/tenants\/\$\{row\.id\}`/)
  assert.match(source, /request\.get\(`\/tenant\/\$\{currentTenant\.value\.id\}\/contacts`/)
  assert.match(source, /request\.post\(`\/tenant\/\$\{currentTenant\.value\.id\}\/contacts`/)
  assert.match(source, /request\.get\(`\/tenant\/\$\{tenantId\}\/overview`/)
  assert.match(source, /<el-drawer\b/)
  assert.match(source, /contactDialogVisible/)
})

test('tenant migration keeps business mutations in the page and does not add batch or import export', () => {
  assert.match(source, /openCreateDialog/)
  assert.match(source, /openEditDialog/)
  assert.match(source, /deleteTenant/)
  assert.match(source, /normalizeTenantWorkspaceRecord/)
  assert.doesNotMatch(source, /<ImportExportManager\b/)
  assert.doesNotMatch(source, /<BatchToolbar\b/)
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
})
