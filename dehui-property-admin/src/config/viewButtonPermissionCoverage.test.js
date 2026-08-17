import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const rootDir = process.cwd()

const requiredPermissionCodesByView = {
  'src/views/building/BuildingList.vue': ['building:create', 'building:update', 'building:delete'],
  'src/views/building/FloorList.vue': ['floor:create', 'floor:update', 'floor:delete'],
  'src/views/building/RoomList.vue': ['room:create', 'room:update', 'room:delete'],
  'src/views/building/AssetList.vue': ['asset:create', 'asset:update', 'asset:delete'],
  'src/views/building/EquipmentList.vue': ['asset:create', 'asset:update', 'asset:delete'],
  'src/views/energy/EnergyMeterList.vue': ['energy:meter:create', 'energy:meter:update', 'energy:meter:delete'],
  'src/views/energy/EnergyRecordList.vue': ['energy:create', 'energy:update', 'energy:reading:update', 'energy:reading:delete'],
  'src/views/energy/EnergyStats.vue': ['energy:reading:update'],
  'src/views/operation/AnnouncementList.vue': ['announcement:create', 'announcement:update', 'announcement:delete'],
  'src/views/operation/InspectionList.vue': ['inspection:create', 'inspection:update', 'inspection:delete'],
  'src/views/operation/VisitorList.vue': ['visitor:create', 'visitor:update'],
  'src/views/operation/WorkOrderList.vue': ['workorder:create', 'workorder:assign', 'workorder:complete', 'workorder:close'],
  'src/views/parking/ParkingSpaceList.vue': ['parking:create', 'parking:update', 'parking:delete'],
  'src/views/parking/ParkingBillList.vue': ['parking-bill:add', 'parking-bill:update', 'parking-bill:pay'],
  'src/views/system/MobileUserList.vue': ['mobile:update'],
  'src/views/tenant/BillList.vue': ['bill:add', 'bill:audit', 'bill:pay', 'finance:invoice:upload'],
  'src/views/tenant/ContractList.vue': [
    'contract:create',
    'contract:update',
    'contract:approve',
    'contract:activate',
    'contract:terminate',
    'contract:cancel',
    'lease:checkin'
  ],
  'src/views/tenant/LeaseList.vue': ['lease:create', 'lease:checkin', 'lease:checkout'],
  'src/views/tenant/FeeRuleList.vue': ['fee-rule:create', 'fee-rule:update', 'fee-rule:activate'],
  'src/views/tenant/BillingPlanList.vue': ['billing-plan:create', 'billing-plan:update', 'billing-plan:terminate'],
  'src/views/tenant/TenantList.vue': ['tenant:create', 'tenant:update', 'tenant:delete']
}

const workspaceConfigByView = {
  'src/views/building/BuildingList.vue': 'src/workspaceConfigs/buildingWorkspaceConfig.js',
  'src/views/building/FloorList.vue': 'src/workspaceConfigs/buildingWorkspaceConfig.js',
  'src/views/building/RoomList.vue': 'src/workspaceConfigs/buildingWorkspaceConfig.js',
  'src/views/building/EquipmentList.vue': 'src/workspaceConfigs/equipment.js',
  'src/views/energy/EnergyMeterList.vue': 'src/workspaceConfigs/energyWorkspaceConfig.js',
  'src/views/energy/EnergyRecordList.vue': 'src/workspaceConfigs/energyWorkspaceConfig.js',
  'src/views/operation/WorkOrderList.vue': 'src/workspaceConfigs/workorder.js',
  'src/views/parking/ParkingBillList.vue': 'src/workspaceConfigs/parkingBillWorkspaceConfig.js'
}

function readView(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), 'utf8')
}

test('write-action views reference the permission codes that gate their buttons', () => {
  const missing = []

  for (const [relativePath, codes] of Object.entries(requiredPermissionCodesByView)) {
    const configPath = workspaceConfigByView[relativePath]
    const source = [readView(relativePath), configPath ? readView(configPath) : ''].join('\n')
    for (const code of codes) {
      if (!source.includes(code)) {
        missing.push(`${relativePath} -> ${code}`)
      }
    }
  }

  assert.deepEqual(missing, [])
})

test('equipment inline cells require asset:update before entering edit mode', () => {
  const viewSource = readView('src/views/building/EquipmentList.vue')
  const configSource = readView('src/workspaceConfigs/equipment.js')
  const gridSource = readView('src/components/workspace/WorkspaceDataGrid.vue')

  assert.match(viewSource, /:can-edit-cell="canEditEquipmentCell"/)
  assert.match(viewSource, /hasPermission\(equipmentPermissions\.update\)[\s\S]*column\.editable === true/)
  assert.match(viewSource, /!column\.custom \|\| column\.platformCustom === true/)
  assert.match(gridSource, /field\.editable === true && props\.canEditCell\(row, field\) === true/)
  assert.match(gridSource, /workspaceGrid\.customFields\.canEditCell\(row, field\)/)
  assert.match(configSource, /update:\s*'asset:update'/)
})
