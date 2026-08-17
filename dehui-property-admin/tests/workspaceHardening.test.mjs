import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const migratedPages = [
  'src/views/building/EquipmentList.vue',
  'src/views/operation/WorkOrderList.vue',
  'src/views/energy/EnergyMeterList.vue',
  'src/views/energy/EnergyRecordList.vue'
]

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('migrated pages wire explicit list errors and delegate indexing to the grid', () => {
  for (const page of migratedPages) {
    const source = read(page)
    assert.match(source, /:error-text="listErrorText"/, `${page} must wire the list error`)
    assert.match(source, /\bretryable\b/, `${page} must expose retry`)
    assert.match(source, /@retry=/, `${page} must own retry handling`)
    assert.match(source, /const listTask =/, `${page} must settle list requests independently`)
    assert.match(source, /const metricsTask =/, `${page} must not let metrics block list rendering`)
    assert.doesNotMatch(source, /:index-method=/, `${page} must use the shared grid index`)
    assert.doesNotMatch(source, /\brecordDisplayIndex\b/, `${page} must not keep O\(n squared\) indexing`)
  }
})

test('CellEditor is mounted only for the active grid cell and has no hidden focus button', () => {
  const grid = read('src/components/workspace/WorkspaceDataGrid.vue')
  const editor = read('src/components/CellEditor.vue')

  assert.match(grid, /activeEditCell/)
  assert.match(grid, /isActiveEditCell/)
  assert.doesNotMatch(grid, /v-else-if="isCellEditable\(row, field\)"/)
  assert.doesNotMatch(editor, /cell-edit-trigger/)
  assert.doesNotMatch(editor, /<el-button/)
})

test('work-order more menu is derived from executable row actions', () => {
  const source = read('src/views/operation/WorkOrderList.vue')
  assert.match(source, /secondaryWorkOrderActions\(row\)\.length/)
  assert.doesNotMatch(source, /\bcanManageWorkOrders\b/)
  assert.doesNotMatch(source, /['"]bill:add['"]|['"]finance:bill:create['"]/)
})

test('equipment operations keep edit primary and derive a narrow permission-aware more menu', () => {
  const source = read('src/views/building/EquipmentList.vue')
  assert.match(source, /operation-column="\{ width: 176, fixed: 'right', align: 'left' \}"/)
  assert.match(source, /equipmentSecondaryActions\(row\)\.length/)
  assert.match(source, /option\.value !== row\.status/)
  assert.match(source, /hasPermission\(equipmentPermissions\.update\)/)
  assert.match(source, /hasPermission\(equipmentPermissions\.delete\)/)
  assert.match(source, /runEquipmentSecondaryAction/)
  assert.doesNotMatch(source, /<el-button[^>]+type="danger"[^>]+@click="deleteEquipment\(row\)"/)
})

test('energy cross-page actions use target view permissions and the client does not submit an operator', () => {
  const config = read('src/workspaceConfigs/energyWorkspaceConfig.js')
  const meters = read('src/views/energy/EnergyMeterList.vue')
  const readings = read('src/views/energy/EnergyRecordList.vue')

  assert.match(config, /readingView:\s*['"]energy:view['"]/)
  assert.match(config, /meterView:\s*['"]energy:meter:view['"]/)
  assert.match(meters, /hasPermission\(energyMeterReadingsPermission\)/)
  assert.match(readings, /hasPermission\(energyReadingMetersPermission\)/)
  assert.doesNotMatch(readings, /operatorName:\s*['"]admin['"]|默认 admin/)
  assert.doesNotMatch(readings, /operatorName:\s*form\.operatorName|operatorName:\s*rowValue/)
})
