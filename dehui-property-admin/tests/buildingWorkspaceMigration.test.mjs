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

const buildingSource = read('src/views/building/BuildingList.vue')
const floorSource = read('src/views/building/FloorList.vue')
const roomSource = read('src/views/building/RoomList.vue')
const configSource = read('src/workspaceConfigs/buildingWorkspaceConfig.js')

test('building, floor and room pages use the shared workspace stack without raw tables', () => {
  for (const source of [buildingSource, floorSource, roomSource]) {
    assert.match(source, /<DataWorkspace\b/)
    assert.match(source, /<DhicDataToolbar\b/)
    assert.match(source, /<WorkspaceDataGrid\b/)
    assert.doesNotMatch(source, /<el-table(?:\s|>)/)
    assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
  }
})

test('building keeps its existing CRUD endpoints and page-owned dialog mutations', () => {
  assert.match(buildingSource, /request\.get\('\/buildings'/)
  assert.match(buildingSource, /request\.post\('\/buildings'/)
  assert.match(buildingSource, /request\.put\(`\/buildings\/\$\{editingId\.value\}`/)
  assert.match(buildingSource, /request\.delete\(`\/buildings\/\$\{row\.id\}`/)
  assert.match(buildingSource, /hasPermission\(buildingPermissions\.create\)/)
  assert.match(buildingSource, /hasPermission\(buildingPermissions\.update\)/)
  assert.match(buildingSource, /hasPermission\(buildingPermissions\.delete\)/)
})

test('floor keeps the building scope and batch generation as a business action', () => {
  assert.match(floorSource, /params:\s*\{ building_id: buildingId \}/)
  assert.match(floorSource, /request\.post\('\/floors\/batch-generate'/)
  assert.match(floorSource, /buildingId: currentBuildingId\.value/)
  assert.match(floorSource, /batchGenerateFloors/)
  assert.doesNotMatch(floorSource, /@batch-action=/)
  assert.equal(configSource.includes("{ id: 'batch-generate', label: '批量生成楼层', permission: 'create' }"), true)
})

test('room keeps server pagination, supported filters and real room metrics', () => {
  assert.match(roomSource, /\.\.\.serverQueryParams\.value/)
  assert.match(roomSource, /\.\.\.pageParams\(pagination\)/)
  assert.match(roomSource, /const page = readPage\(result\.value\)/)
  assert.match(roomSource, /pagination\.total = page\.total/)
  assert.match(roomSource, /loadListTotalResult\('\/rooms'/)
  assert.match(roomSource, /loadListTotalsDetailed\(/)
  assert.match(roomSource, /status: 'VACANT'/)
  assert.match(configSource, /query: \{ dataMode: 'server', filterLogics: \['and'\] \}/)
  assert.match(configSource, /param: 'status'/)
  assert.doesNotMatch(roomSource, /status:\s*query\.status/)
})

test('asset management permissions remain permission-code based', () => {
  for (const code of [
    'building:view', 'building:create', 'building:update', 'building:delete',
    'floor:view', 'floor:create', 'floor:update', 'floor:delete',
    'room:view', 'room:create', 'room:update', 'room:delete', 'room:lease', 'room:checkout'
  ]) {
    assert.equal(configSource.includes(code), true, `missing permission ${code}`)
  }
  assert.equal(configSource.includes(':*'), false)
})
