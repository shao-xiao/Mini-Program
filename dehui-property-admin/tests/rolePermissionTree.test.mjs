import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

const roleListSource = () => fs.readFileSync(path.join(root, 'src/views/system/RoleList.vue'), 'utf8')

test('role permission dialog exposes only module-level read and write controls', () => {
  const source = roleListSource()
  const template = source.split('<script setup>')[0]

  assert.match(source, /modulePermissionSections/)
  assert.match(source, /filteredModuleSections/)
  assert.match(template, /module-permission-row/)
  assert.match(template, /module\.readLabel \|\| '读'/)
  assert.match(template, /module\.writeLabel \|\| '写'/)
  assert.match(template, /v-if="section\.bulkAccess" class="module-section-actions"/)
  assert.match(source, /if \(!section\.bulkAccess\)/)
  assert.match(source, /toggleWorkbenchAccess/)
  assert.match(template, /启用物业端/)
  assert.match(template, /移动端入口/)
  assert.match(template, /一次会话只启用一个身份，权限和数据范围不会合并/)
  assert.match(source, /permissionDialogSections/)
  assert.match(source, /expandPriorityModuleSection/)
  assert.match(source, /Boolean\(permissionKeyword\.value\.trim\(\)\)/)
  assert.match(template, /历史部分权限/)
  assert.match(source, /permissionKeyword/)
  assert.match(source, /展开全部/)
  assert.match(source, /收起全部/)
  assert.match(source, /toggleModuleAccess/)
  assert.match(source, /toggleModuleSectionAccess/)
  assert.match(source, /resolveModulePermissionCodes/)
  assert.match(source, /permissionCodesFromRoleAccess/)
  assert.match(source, /buildRoleMenuSelection/)
  assert.match(source, /loadedPermissionRoleId !== currentRoleId/)
  assert.match(source, /未触碰的历史部分权限不会自动扩张/)
  assert.doesNotMatch(template, /perm-code/)
  assert.doesNotMatch(template, /permission\.permissionCode|item\.permissionCode/)
  assert.doesNotMatch(source, /togglePermissionItem/)
})

test('role permission tree maps existing permission codes under the left menu hierarchy', async () => {
  const { buildRolePermissionTree, UNMATCHED_PERMISSION_MODULE_NAME } = await import('../src/utils/rolePermissionTree.js')

  const menus = [
    {
      title: '资产管理',
      menuName: '资产管理',
      children: [
        { title: '楼宇管理', menuName: '楼宇管理', path: '/buildings' },
        { title: '设备台账', menuName: '设备台账', path: '/equipment' }
      ]
    },
    {
      title: '停车管理',
      menuName: '停车管理',
      children: [
        { title: '车位管理', menuName: '车位管理', path: '/parking/spaces' },
        { title: '停车账单', menuName: '停车账单', path: '/parking/bills' }
      ]
    }
  ]

  const permissions = [
    { id: 1, code: 'building:view', permissionName: '查看楼宇' },
    { id: 2, code: 'building:create', permissionName: '新增楼宇' },
    { id: 3, code: 'asset:view', permissionName: '查看资产' },
    { id: 4, code: 'parking:view', permissionName: '查看车位' },
    { id: 5, code: 'parking-bill:update', permissionName: '更新停车账单' },
    { id: 6, code: 'feerule:view', permissionName: '查看收费规则' }
  ]

  const tree = buildRolePermissionTree(menus, permissions)
  const assetModule = tree.find(item => item.label === '资产管理')
  const parkingModule = tree.find(item => item.label === '停车管理')
  const unmatchedModule = tree.find(item => item.label === UNMATCHED_PERMISSION_MODULE_NAME)

  assert.ok(assetModule)
  assert.deepEqual(
    assetModule.children.find(item => item.label === '楼宇管理').permissions.map(item => item.code),
    ['building:view', 'building:create']
  )
  assert.deepEqual(
    assetModule.children.find(item => item.label === '设备台账').permissions.map(item => item.code),
    ['asset:view']
  )
  assert.deepEqual(
    parkingModule.children.find(item => item.label === '车位管理').permissions.map(item => item.code),
    ['parking:view']
  )
  assert.deepEqual(
    parkingModule.children.find(item => item.label === '停车账单').permissions.map(item => item.code),
    ['parking-bill:update']
  )
  assert.deepEqual(
    unmatchedModule.children[0].permissions.map(item => item.code),
    ['feerule:view']
  )
})

test('operation permissions automatically depend on the feature view permission', async () => {
  const { nextPermissionIdsAfterToggle } = await import('../src/utils/rolePermissionTree.js')

  const feature = {
    permissions: [
      { id: 11, code: 'parking:view' },
      { id: 12, code: 'parking:create' },
      { id: 13, code: 'parking:delete' }
    ]
  }

  assert.deepEqual(nextPermissionIdsAfterToggle([], feature, 12, true).sort(), [11, 12])
  assert.deepEqual(nextPermissionIdsAfterToggle([11, 12, 13], feature, 11, false), [])
})
