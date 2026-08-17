import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import {
  getPathPermissionCodes,
  getPathViewPermissionCodes
} from '../src/config/permissionRules.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function readSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function buttonForHandler(source, handler) {
  const pattern = new RegExp(
    `<el-button\\b[^>]*@click="${escapeRegExp(handler)}"[^>]*>`,
    'g'
  )
  const matches = source.match(pattern) || []

  assert.equal(matches.length, 1, `expected one button for ${handler}`)
  return matches[0]
}

test('legacy LeaseList does not assume that every lease belongs to building 1', () => {
  const source = readSource('src/views/tenant/LeaseList.vue')

  assert.doesNotMatch(source, /\bBUILDING_ID\s*=\s*1\b/)
})

test('legacy LeaseList uses Lease application APIs instead of room mutation APIs', () => {
  const source = readSource('src/views/tenant/LeaseList.vue')

  assert.doesNotMatch(source, /\/rooms\/\$\{[^}]+\}\/(?:lease|checkout)/)
  assert.match(source, /request\.get\(\s*['"`]\/leases\/search['"`]/)
  assert.match(source, /request\.post\(\s*['"`]\/leases['"`]/)
  assert.match(source, /\/leases\/\$\{[^}]+\}\/check-in/)
  assert.match(source, /\/leases\/\$\{[^}]+\}\/check-out/)
})

test('ContractList lifecycle buttons use action-specific permissions', () => {
  const source = readSource('src/views/tenant/ContractList.vue')
  const expectedPermissions = new Map([
    ['activate(row)', 'contract:activate'],
    ['checkIn(row)', 'lease:checkin'],
    ['terminate(row)', 'contract:terminate'],
    ['cancelContract(row)', 'contract:cancel']
  ])

  for (const [handler, permissionCode] of expectedPermissions) {
    const button = buttonForHandler(source, handler)
    assert.match(button, new RegExp(`hasPermission\\('${permissionCode}'\\)`))
    assert.doesNotMatch(button, /hasPermission\('contract:create'\)/)
  }
})

test('unified tenant workspace redirects legacy routes without changing phase-one permission codes', () => {
  const routerSource = readSource('src/router/index.js')
  const contractCodes = getPathPermissionCodes('/contracts')
  const leaseCodes = getPathPermissionCodes('/leases')
  const targetContractCodes = [
    'contract:view',
    'contract:create',
    'contract:update',
    'contract:approve',
    'contract:activate',
    'contract:terminate',
    'contract:cancel'
  ]
  const targetLeaseCodes = [
    'lease:view',
    'lease:create',
    'lease:checkin',
    'lease:checkout',
    'lease:renew',
    'lease:terminate'
  ]

  assert.match(
    routerSource,
    /path:\s*'\/tenant\/workspace'[\s\S]{0,240}TenantWorkspaceView\.vue/
  )
  assert.match(
    routerSource,
    /path:\s*'\/contracts'[\s\S]{0,240}redirect:[\s\S]{0,240}'\/tenant\/workspace'/
  )
  assert.match(
    routerSource,
    /path:\s*'\/leases'[\s\S]{0,240}redirect:[\s\S]{0,240}'\/tenant\/workspace'/
  )
  assert.deepEqual(
    targetContractCodes.filter(code => contractCodes.includes(code)),
    targetContractCodes
  )
  assert.deepEqual(
    targetLeaseCodes.filter(code => leaseCodes.includes(code)),
    targetLeaseCodes
  )
  assert.deepEqual(getPathViewPermissionCodes('/contracts'), ['contract:view'])
  assert.deepEqual(getPathViewPermissionCodes('/leases'), ['lease:view'])
})
