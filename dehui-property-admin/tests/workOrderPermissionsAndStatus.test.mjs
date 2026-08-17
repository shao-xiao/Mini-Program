import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createWorkOrderWorkspaceConfig } from '../src/workspaceConfigs/workorder.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('workorder workspace is permission-driven and keeps existing action endpoints', () => {
  const viewSource = read('src/views/operation/WorkOrderList.vue')
  const config = createWorkOrderWorkspaceConfig({ currentUserId: 7 })

  assert.match(viewSource, /<DataWorkspace/)
  assert.match(viewSource, /:workspace-config="workOrderWorkspaceConfig"/)
  assert.deepEqual(
    config.views.map(view => view.id),
    ['pending-workorders', 'my-workorders', 'completed-workorders']
  )
  for (const permission of ['view', 'create', 'update', 'assign', 'complete', 'close']) {
    assert.equal(config.permissions[permission], `workorder:${permission}`)
  }
  assert.doesNotMatch(viewSource, /role\s*===|roles?\.includes/)

  assert.match(viewSource, /Promise\.allSettled\(rows\.map\(row => request\.patch\(`\/workorders\/\$\{row\.id\}\/assign`/)
  assert.match(viewSource, /Promise\.allSettled\(rows\.map\(row => request\.patch\(`\/workorders\/\$\{row\.id\}\/close`/)
  assert.match(viewSource, /request\.patch\(`\/workorders\/\$\{completeForm\.orderId\}\/complete`/)
  assert.match(viewSource, /request\.patch\(`\/workorders\/\$\{statusForm\.orderId\}\/status`/)
})

test('workorder read-only access does not initialize write-only reference requests', () => {
  const viewSource = read('src/views/operation/WorkOrderList.vue')

  assert.match(
    viewSource,
    /const loadUsers = async \(\)=>\{\s+if \(!canAssignWorkOrders\) \{[\s\S]*?return\s+\}[\s\S]*?request\.get\('\/workorders\/assignable-users'\)/
  )
  assert.match(
    viewSource,
    /const loadTenants = async \(\)=>\{\s+if \(!canViewTenants\) \{[\s\S]*?return\s+\}[\s\S]*?request\.get\('\/tenant\/list'\)/
  )
})

test('workorder workspace exposes real fields and semantic status tags', () => {
  const viewSource = read('src/views/operation/WorkOrderList.vue')
  const rendererSource = read('src/components/CellRenderer.vue')
  const gridSource = read('src/components/workspace/WorkspaceDataGrid.vue')
  const config = createWorkOrderWorkspaceConfig()
  const fieldKeys = config.fields.map(field => field.key)
  const statusTones = Object.fromEntries(config.statuses.map(status => [status.value, status.tone]))

  for (const field of ['orderNumber', 'title', 'orderType', 'category', 'status', 'priority', 'reporter', 'tenant', 'handler', 'location', 'createdTime', 'updatedTime', 'remark']) {
    assert.ok(fieldKeys.includes(field), `missing workorder field ${field}`)
  }
  assert.equal(statusTones.SUBMITTED, 'warning')
  assert.equal(statusTones.PROCESSING, 'primary')
  assert.equal(statusTones.COMPLETED, 'success')
  assert.equal(statusTones.CLOSED, 'info')
  assert.match(viewSource, /:can-edit-cell="canEditWorkOrderCell"/)
  assert.match(viewSource, /@cell-save="handleWorkOrderCellSave"/)
  assert.match(viewSource, /field\.key === 'status' && canChangeWorkOrderStatus/)
  assert.match(gridSource, /<CellEditor/)
  assert.match(viewSource, /canUseWorkOrderStatus\(statusForm\.status\)/)
  assert.match(rendererSource, /status-dot\.tone-primary[\s\S]*#2563eb/)
  assert.match(rendererSource, /status-dot\.tone-warning[\s\S]*#d99100/)
  assert.match(rendererSource, /status-dot\.tone-success[\s\S]*#16a36a/)
  assert.match(rendererSource, /status-dot\.tone-danger[\s\S]*#d93025/)
})

test('mini-program workorder form keeps phone identity server-derived while saving report snapshots', () => {
  const miniRoot = path.resolve(root, '..', 'dehui-property-miniprogram')
  const pageScript = fs.readFileSync(path.join(miniRoot, 'pages/workorders/index.js'), 'utf8')
  const pageMarkup = fs.readFileSync(path.join(miniRoot, 'pages/workorders/index.wxml'), 'utf8')

  assert.match(pageScript, /reporterName:\s*form\./)
  assert.match(pageScript, /companyName:\s*form\./)
  assert.match(pageScript, /reportedAt:\s*form\./)
  assert.doesNotMatch(pageScript, /contactPhone:\s*form\./)
  assert.doesNotMatch(pageMarkup, /data-field="contactPhone"/)
  assert.match(pageMarkup, /<dynamic-form[^>]+schema="\{\{customFieldSchema\}\}"/)
})

test('workorder detail separates overview, tenant submission and property handling records', () => {
  const viewSource = read('src/views/operation/WorkOrderList.vue')
  const detailPath = path.join(root, 'src/components/workorder/WorkOrderDetailDialog.vue')
  const detailSource = fs.existsSync(detailPath) ? fs.readFileSync(detailPath, 'utf8') : ''
  const source = `${viewSource}\n${detailSource}`

  assert.match(viewSource, /<WorkOrderDetailDialog/)
  assert.match(source, /工单概况/)
  assert.match(source, /租户报修信息/)
  assert.match(source, /物业处理信息/)
  assert.match(source, /propertyRecords/)
  assert.match(source, /record\.attachments/)
  assert.match(viewSource, /result:\s*completeForm\.handlingResult/)
})
