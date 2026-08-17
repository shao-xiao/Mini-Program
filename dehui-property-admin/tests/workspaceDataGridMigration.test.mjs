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
  'src/views/energy/EnergyRecordList.vue',
  'src/views/parking/ParkingBillList.vue',
  'src/views/building/BuildingList.vue',
  'src/views/building/FloorList.vue',
  'src/views/building/RoomList.vue'
]

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('all migrated workspaces delegate table scaffolding to WorkspaceDataGrid', () => {
  for (const page of migratedPages) {
    const source = read(page)
    assert.match(source, /<WorkspaceDataGrid\b/, `${page} must render WorkspaceDataGrid`)
    assert.doesNotMatch(source, /v-for="column in visibleOrderedColumns"/, `${page} must not repeat dynamic columns`)
  }
})

test('WorkspaceDataGrid keeps business mutations in their page owners', () => {
  const equipment = read(migratedPages[0])
  const workorders = read(migratedPages[1])
  const meters = read(migratedPages[2])
  const readings = read(migratedPages[3])
  const parkingBills = read(migratedPages[4])
  const buildings = read(migratedPages[5])
  const floors = read(migratedPages[6])
  const rooms = read(migratedPages[7])

  for (const method of ['openDialog', 'saveInlineCell', 'deleteEquipment', 'batchDeleteEquipment']) {
    assert.match(equipment, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openDialog', 'assign', 'complete', 'close']) {
    assert.match(workorders, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openDialog', 'remove']) {
    assert.match(meters, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['generateBill', 'markPosted', 'withdrawBill', 'deleteRecord']) {
    assert.match(readings, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openCreate', 'submitCreate', 'syncBills', 'pay', 'voidBill']) {
    assert.match(parkingBills, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openCreateDialog', 'openEditDialog', 'save', 'deleteBuilding']) {
    assert.match(buildings, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openCreateDialog', 'openEditDialog', 'save', 'deleteFloor', 'batchGenerateFloors']) {
    assert.match(floors, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
  for (const method of ['openCreateDialog', 'openEditDialog', 'saveRoom', 'deleteRoom']) {
    assert.match(rooms, new RegExp(`(?:function|const)\\s+${method}\\b`))
  }
})
