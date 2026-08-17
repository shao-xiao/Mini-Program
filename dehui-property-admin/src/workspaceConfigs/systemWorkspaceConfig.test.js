import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  createMobileUserWorkspaceConfig,
  createSystemRoleWorkspaceConfig,
  createSystemUserWorkspaceConfig
} from './systemWorkspaceConfig.js'

test('system user and role workspaces use complete client arrays and existing permissions', () => {
  const userConfig = createSystemUserWorkspaceConfig()
  const roleConfig = createSystemRoleWorkspaceConfig()

  assert.equal(userConfig.query.dataMode, 'client')
  assert.equal(roleConfig.query.dataMode, 'client')
  assert.equal(userConfig.permissions.create, 'system:user:create')
  assert.equal(userConfig.permissions.assignRole, 'system:user:assign-role')
  assert.equal(roleConfig.permissions.assignPermission, 'system:role:assign-permission')
})

test('system workspace fields exclude credentials and unavailable user aliases', () => {
  const userKeys = createSystemUserWorkspaceConfig().fields.map(field => field.key)
  const roleKeys = createSystemRoleWorkspaceConfig().fields.map(field => field.key)
  const mobileKeys = createMobileUserWorkspaceConfig().fields.map(field => field.key)

  assert.deepEqual(userKeys, [
    'code', 'realName', 'username', 'phone', 'primaryRoleName', 'status',
    'webLoginEnabled', 'mobileEnabled', 'mobileBound', 'lastLoginAt', 'remark'
  ])
  assert.deepEqual(roleKeys, ['roleCode', 'roleName', 'roleType', 'description', 'status', 'createdTime', 'updatedTime'])
  assert.deepEqual(mobileKeys, [
    'keyword', 'code', 'verifiedPhone', 'nickname', 'realName', 'userType',
    'bindingState', 'contactRole', 'availableIdentityCount', 'tenantName',
    'tenantContactName', 'boundSysRealName', 'backendRoleSummary',
    'mobileWorkbenchState', 'status', 'createdAt', 'lastLoginAt', 'updatedAt'
  ])
  assert.equal(userKeys.includes('password'), false)
  assert.equal(userKeys.includes('passwordHash'), false)
  assert.equal(userKeys.includes('userTypeText'), false)
  assert.equal(userKeys.includes('tenantName'), false)
})

test('mobile user workspace uses server paging and whitelisted query parameters', () => {
  const config = createMobileUserWorkspaceConfig()
  const plan = buildWorkspaceQueryPlan({
    fields: config.fields,
    filters: [
      { key: 'userType', operator: 'equals', value: 'TENANT' },
      { key: 'tenantName', operator: 'contains', value: '德汇' }
    ],
    groups: [{ key: 'status', order: 'ascending' }],
    sorts: [{ key: 'updatedAt', order: 'descending' }],
    page: 2,
    pageSize: 50,
    query: config.query
  })

  assert.equal(config.query.dataMode, 'server')
  assert.deepEqual(plan.serverParams, {
    groupBy: 'status:asc',
    page: 2,
    pageSize: 50,
    sorts: 'status:asc,updatedAt:desc',
    tenantName: '德汇',
    userType: 'TENANT'
  })
})
