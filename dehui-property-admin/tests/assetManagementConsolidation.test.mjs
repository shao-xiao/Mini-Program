import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { fallbackMenuSections } from '../src/config/access.js'
import { createEnergyReadingWorkspaceConfig } from '../src/workspaceConfigs/energyWorkspaceConfig.js'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const projectRoot = path.resolve(root, '..')
const read = relativePath => fs.readFileSync(path.join(root, relativePath), 'utf8')

test('asset management fallback navigation contains exactly five consolidated entries', () => {
  const asset = fallbackMenuSections.find(item => item.menuCode === 'asset')
  assert.ok(asset)
  assert.deepEqual(asset.children.map(item => [item.title, item.path]), [
    ['楼宇空间管理', '/assets/spaces'],
    ['设备台账', '/assets/equipment'],
    ['能耗管理', '/assets/energy'],
    ['停车位管理', '/assets/parking'],
    ['会议管理', '/assets/meetings']
  ])
  assert.equal(fallbackMenuSections.some(item => ['parking', 'meeting', 'energy'].includes(item.menuCode)), false)
})

test('legacy asset URLs redirect to the consolidated pages and parking bills redirect to finance', () => {
  const router = read('src/router/index.js')
  const redirects = [
    ['/buildings', '/assets/spaces'],
    ['/floors', '/assets/spaces'],
    ['/rooms', '/assets/spaces'],
    ['/equipment', '/assets/equipment'],
    ['/energy/meters', '/assets/energy'],
    ['/energy/records', '/assets/energy'],
    ['/energy/stats', '/assets/energy'],
    ['/parking/spaces', '/assets/parking'],
    ['/parking/bills', '/bills'],
    ['/meetings/rooms', '/assets/meetings'],
    ['/meetings/bookings', '/assets/meetings']
  ]

  redirects.forEach(([legacyPath, targetPath]) => {
    const start = router.indexOf(`path: '${legacyPath}'`)
    assert.notEqual(start, -1, `${legacyPath} route must remain`)
    assert.notEqual(router.slice(start, start + 260).indexOf(`path: '${targetPath}'`), -1, `${legacyPath} must redirect to ${targetPath}`)
  })
})

test('active asset pages do not expose bill generation and energy disables legacy billing actions', () => {
  const energy = read('src/views/assets/AssetEnergyView.vue')
  const parking = read('src/views/assets/AssetParkingView.vue')
  const meetings = read('src/views/assets/AssetMeetingsView.vue')
  const activeAssetSources = `${energy}\n${parking}\n${meetings}`

  assert.match(energy, /:allow-billing-actions="false"/)
  assert.match(energy, /sourceType: 'ENERGY'/)
  assert.doesNotMatch(activeAssetSources, /\/energy\/readings\/\$\{[^}]+\}\/generate-bill/)
  assert.doesNotMatch(activeAssetSources, /batch-generate-bills|mark-posted/)
  assert.doesNotMatch(parking, /\/parking\/bills/)
  assert.doesNotMatch(meetings, /generate-bill|create-bill/)

  const readingConfig = createEnergyReadingWorkspaceConfig({ billingEnabled: false })
  assert.equal(readingConfig.importExport.some(action => action.id === 'batch-bill'), false)
})

test('meeting room cards expose compact operational details and a lightweight management action', () => {
  const meetings = read('src/views/assets/AssetMeetingsView.vue')

  assert.match(meetings, /class="room-card__detail-line"[\s\S]*设施[\s\S]*meetingRoomFacilitiesText\(room\)/)
  assert.match(meetings, /class="room-card__detail-line"[\s\S]*开放[\s\S]*meetingRoomOpenHours\(room\)/)
  assert.match(meetings, /class="room-card__more"/)
  assert.match(meetings, /\.room-card__more\.el-button[\s\S]*width: 26px;[\s\S]*background: rgb\(31 35 41 \/ 66%\)/)
  assert.doesNotMatch(meetings, /\.room-card__menu :deep\(\.el-button\)[\s\S]*background: rgb\(255 255 255/)
})

test('energy statistics uses the shared meter type labels for non-empty chart data', () => {
  const statistics = read('src/views/energy/EnergyStats.vue')

  assert.match(statistics, /energyMeterTypeOptions/)
  assert.match(statistics, /const meterTypeLabelMap = new Map/)
  assert.match(statistics, /function meterTypeText\(value\)/)
})

test('V40 only consolidates menus and role-menu links without changing permission codes', () => {
  const migration = fs.readFileSync(path.join(
    projectRoot,
    'dehui-property-management/src/main/resources/db/migration/V40__consolidate_asset_management_navigation.sql'
  ), 'utf8')

  for (const code of ['asset:spaces', 'asset:energy', 'asset:parking', 'asset:meetings']) {
    assert.match(migration, new RegExp(`'${code}'`))
  }
  assert.match(migration, /UPDATE sys_menu[\s\S]*status = 'DISABLED'/)
  assert.doesNotMatch(migration, /sys_role_permission|INSERT INTO sys_permission|DELETE FROM sys_menu/)
})
