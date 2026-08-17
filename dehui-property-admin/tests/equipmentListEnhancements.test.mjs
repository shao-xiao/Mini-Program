import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createEquipmentWorkspaceConfig } from '../src/workspaceConfigs/equipment.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('equipment list keeps filters, delete action, and type/status formatters', () => {
  const source = read('src/views/building/EquipmentList.vue')

  assert.match(source, /query = reactive\(\{[\s\S]*keyword:/)
  assert.match(source, /query = reactive\(\{[\s\S]*equipmentType:/)
  assert.match(source, /query = reactive\(\{[\s\S]*status:/)
  assert.match(source, /resetQuery\(/)
  assert.match(source, /deleteEquipment\(row\)/)
  assert.match(source, /equipmentTypeText\(/)
  assert.match(source, /statusText\(/)
  assert.match(source, /@keyup\.enter="search"/)
})

test('equipment workspace consumes its schema through the unified toolbar', () => {
  const source = read('src/views/building/EquipmentList.vue')
  const toolbar = read('src/components/DhicDataToolbar.vue')
  const dataWorkspace = read('src/components/DataWorkspace.vue')
  const importExportManager = read('src/components/ImportExportManager.vue')
  const batchToolbar = read('src/components/BatchToolbar.vue')
  const viewManager = read('src/components/WorkspaceViewManager.vue')
  const config = createEquipmentWorkspaceConfig()

  assert.match(source, /<DataWorkspace[\s\S]*:workspace-config="equipmentWorkspaceConfig"/)
  assert.match(source, /<template #header-actions>/)
  assert.match(source, /:metric-data="equipmentMetricData"/)
  assert.match(source, /@batch-action="runEquipmentBatchAction"/)
  assert.match(source, /@workspace-file="handleImportEquipmentFile"/)
  assert.doesNotMatch(source, /<el-card>/)
  assert.match(dataWorkspace, /<MetricPanel/)
  assert.match(dataWorkspace, /<BatchToolbar/)
  assert.match(toolbar, /<ImportExportManager/)
  assert.match(importExportManager, /hasPermission/)
  assert.match(batchToolbar, /hasPermission/)
  assert.match(toolbar, /activeFilterCount/)
  assert.match(toolbar, /sortCount/)
  assert.match(toolbar, /groupCount/)
  assert.match(toolbar, /rowDensity/)
  assert.match(viewManager, /emit\('create'/)
  assert.deepEqual(config.importExport.map(action => action.id), ['import', 'export-current', 'export-template'])
})

test('equipment statuses use semantic tones rendered as status tags', () => {
  const renderer = read('src/components/CellRenderer.vue')
  const statusDisplay = read('src/components/StatusDisplay.vue')
  const statusTones = Object.fromEntries(
    createEquipmentWorkspaceConfig().statuses.map(status => [status.value, status.tone])
  )

  assert.equal(statusTones.NORMAL, 'success')
  assert.equal(statusTones.MAINTENANCE, 'warning')
  assert.equal(statusTones.FAULT, 'danger')
  assert.match(renderer, /import StatusDisplay from/)
  assert.match(renderer, /:show-dot="type === 'status'"/)
  assert.match(statusDisplay, /:type="resolvedTone"/)
  assert.match(statusDisplay, /v-if="showDot" class="dhic-status-display__dot"/)
})
