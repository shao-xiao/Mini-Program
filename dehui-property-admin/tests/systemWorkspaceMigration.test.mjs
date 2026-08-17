import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relativePath => fs.readFileSync(path.join(root, relativePath), 'utf8')

const pages = {
  users: read('src/views/system/UserList.vue'),
  roles: read('src/views/system/RoleList.vue'),
  mobile: read('src/views/system/MobileUserList.vue')
}

test('active system management pages use the complete shared workspace stack', () => {
  Object.values(pages).forEach(source => {
    assert.match(source, /<DataWorkspace\b/)
    assert.match(source, /<DhicDataToolbar\b/)
    assert.match(source, /<WorkspaceDataGrid\b/)
    assert.doesNotMatch(source, /<el-table(?:\s|>)/)
  })
})

test('system user page retains account and role mutations with permission codes', () => {
  const source = pages.users
  for (const pattern of [
    /request\.post\('\/system\/users'/,
    /request\.put\(`\/system\/users\/\$\{editingId\.value\}`/,
    /`\/system\/users\/\$\{roleForm\.userId\}\/roles`/,
    /`\/system\/users\/\$\{row\.id\}\/status`/,
    /`\/system\/users\/\$\{row\.id\}\/reset-password`/,
    /request\.delete\(`\/system\/users\/\$\{row\.id\}`/
  ]) assert.match(source, pattern)
  for (const code of ['system:user:create', 'system:user:update', 'system:user:disable', 'system:user:delete', 'system:user:assign-role', 'system:user:reset-password']) {
    assert.match(source, new RegExp(code.replaceAll(':', '\\:')))
  }
})

test('role page retains protected role rules and module access mutations', () => {
  const source = pages.roles
  assert.match(source, /isProtectedSystemRole/)
  assert.match(source, /modulePermissionSections/)
  assert.match(source, /resolveModulePermissionCodes/)
  assert.match(source, /buildRoleMenuSelection/)
  assert.match(source, /`\/system\/roles\/\$\{row\.id\}\/access`/)
  assert.match(source, /`\/system\/roles\/\$\{currentRoleId\.value\}\/access`/)
  assert.match(source, /system:role:assign-permission/)
})

test('mobile user page keeps server paging and only selects server-derived identity candidates', () => {
  const source = pages.mobile
  assert.match(source, /\.\.\.serverQueryParams\.value/)
  assert.match(source, /page:\s*pagination\.currentPage\s*-\s*1/)
  assert.match(source, /size:\s*pagination\.pageSize/)
  assert.match(source, /`\/mobile-users\/\$\{detail\.id\}\/default-identity`/)
  assert.match(source, /bindId:\s*identity\.bindId/)
  assert.match(source, /`\/mobile-users\/\$\{row\.id\}\/unbind`/)
  assert.match(source, /`\/mobile-users\/\$\{row\.id\}\/status`/)
  assert.doesNotMatch(source, /tenantId:\s*bindForm/)
  assert.match(source, /hasPermission\('mobile:update'\)/)
})
