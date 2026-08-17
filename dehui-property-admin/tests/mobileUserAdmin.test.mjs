import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const root = path.resolve(process.cwd())

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('admin router registers mini-program mobile users page under system management', () => {
  const router = read('src/router/index.js')
  const access = read('src/config/access.js')
  const modulePermissions = read('src/config/modulePermissionBundles.js')

  assert.match(router, /path:\s*'\/system\/mobile-users'/)
  assert.match(router, /MobileUserList/)
  assert.match(router, /views\/system\/MobileUserList\.vue/)
  assert.match(access, /modulePermissionMenuSections/)
  assert.match(modulePermissions, /route:\s*'\/system\/mobile-users'/)
  assert.match(modulePermissions, /title:\s*'小程序用户'/)
})

test('mini-program mobile users page lists phone-login users and manages only server-derived identities', () => {
  const viewPath = path.join(root, 'src/views/system/MobileUserList.vue')
  assert.equal(fs.existsSync(viewPath), true)
  const source = fs.readFileSync(viewPath, 'utf8')

  assert.match(source, /request\.get\('\/mobile-users'/)
  assert.match(source, /request\.get\(`\/mobile-users\/\$\{row\.id\}`/)
  assert.match(source, /request\.post\(`\/mobile-users\/\$\{detail\.id\}\/default-identity`/)
  assert.match(source, /bindId:\s*identity\.bindId/)
  assert.match(source, /request\.post\(`\/mobile-users\/\$\{row\.id\}\/unbind`\)/)
  assert.match(source, /request\.patch\(`\/mobile-users\/\$\{row\.id\}\/status`/)
  assert.match(source, /验证手机号可用身份/)
  assert.match(source, /绑定审计/)
  assert.doesNotMatch(source, /request\.get\('\/tenant\/list'\)/)
  assert.doesNotMatch(source, /tenantId:\s*bindForm/)
})
