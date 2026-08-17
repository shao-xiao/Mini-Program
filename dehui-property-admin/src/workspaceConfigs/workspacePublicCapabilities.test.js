import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const root = process.cwd()

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('DataWorkspace renders config metrics and batch actions through shared components', () => {
  const source = read('src/components/DataWorkspace.vue')
  const batchToolbar = read('src/components/BatchToolbar.vue')

  assert.match(source, /resolvedMetrics/)
  assert.match(source, /<MetricPanel/)
  assert.match(source, /<BatchToolbar/)
  assert.match(source, /@action="\$emit\('batch-action', \$event\)"/)
  assert.match(batchToolbar, /permittedWorkspaceActions/)
  assert.match(batchToolbar, /hasPermission/)
})

test('DhicDataToolbar renders config import and export actions through one manager', () => {
  const toolbar = read('src/components/DhicDataToolbar.vue')
  const manager = read('src/components/ImportExportManager.vue')

  assert.match(toolbar, /<ImportExportManager/)
  assert.match(toolbar, /workspaceConfig\?\.importExport/)
  assert.match(manager, /permittedWorkspaceActions/)
  assert.match(manager, /hasPermission/)
  assert.match(manager, /type="file"/)
  assert.match(manager, /emit\('action', action\.id\)/)
  assert.match(manager, /emit\('file', \{ actionId: action\.id, file \}\)/)
})

test('migrated workspaces consume metric, import-export and batch configuration', () => {
  const equipment = read('src/views/building/EquipmentList.vue')
  const workorder = read('src/views/operation/WorkOrderList.vue')
  const energyMeter = read('src/views/energy/EnergyMeterList.vue')
  const energyReading = read('src/views/energy/EnergyRecordList.vue')
  const parkingBill = read('src/views/parking/ParkingBillList.vue')
  const building = read('src/views/building/BuildingList.vue')
  const floor = read('src/views/building/FloorList.vue')
  const room = read('src/views/building/RoomList.vue')

  assert.match(equipment, /:metric-data="equipmentMetricData"/)
  assert.match(equipment, /@batch-action="runEquipmentBatchAction"/)
  assert.match(equipment, /@workspace-file="handleImportEquipmentFile"/)
  assert.match(workorder, /:metric-data="workOrderMetricData"/)
  assert.match(workorder, /@batch-action="runWorkOrderBatchAction"/)
  assert.match(workorder, /@workspace-action="handleWorkOrderTransferAction"/)
  assert.match(energyMeter, /:metric-data="energyMeterMetricData"/)
  assert.match(energyReading, /:metric-data="energyReadingMetricData"/)
  assert.match(energyReading, /@workspace-file="handleImportReadingFile"/)
  assert.match(parkingBill, /:metric-data="parkingBillMetricData"/)
  assert.match(parkingBill, /parkingBillWorkspaceConfig/)
  assert.match(parkingBill, /<WorkspaceDataGrid/)
  assert.match(building, /buildingWorkspaceConfig/)
  assert.match(building, /<WorkspaceDataGrid/)
  assert.match(floor, /floorWorkspaceConfig/)
  assert.match(floor, /batchGenerateFloors/)
  assert.match(room, /roomWorkspaceConfig/)
  assert.match(room, /:metric-data="roomMetricData"/)
  assert.match(room, /<WorkspaceDataGrid/)
})
