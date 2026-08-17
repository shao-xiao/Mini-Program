const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const caps = require(path.join(root, 'utils/capabilities'))
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const keys = features => features.map(item => item.key)

test('PUBLIC home contains only implemented public routes', () => {
  assert.deepEqual(keys(caps.getVisibleHomeFeatures({
    userType: 'PUBLIC',
    capabilities: []
  })), ['announcement', 'investment', 'meetingPublic', 'visitorGuide'])
})

test('TENANT responsible home follows company-level server capabilities', () => {
  const visible = keys(caps.getVisibleHomeFeatures({
    userType: 'TENANT',
    contactRole: 'RESPONSIBLE',
    capabilities: [
      'bill.view',
      'contract.view',
      'energy.company.view',
      'parking.company.view',
      'parking.mine.view',
      'meeting.book',
      'workorder.view',
      'visitor.create'
    ]
  }))
  assert.ok(visible.includes('investment'))
  assert.ok(visible.includes('energy'))
  assert.ok(visible.includes('parkingCompany'))
})

test('TENANT employee home excludes ungranted company-level features', () => {
  const visible = keys(caps.getVisibleHomeFeatures({
    userType: 'TENANT',
    contactRole: 'EMPLOYEE',
    capabilities: [
      'parking.mine.view',
      'meeting.book',
      'workorder.view',
      'visitor.create'
    ]
  }))
  assert.ok(visible.includes('investment'))
  assert.ok(!visible.includes('energy'))
  assert.ok(!visible.includes('parkingCompany'))
})

test('STAFF home exposes only implemented capability-backed workbench entry', () => {
  const visible = keys(caps.getVisibleHomeFeatures({
    userType: 'STAFF',
    capabilities: ['energy.staff.view', 'workorder.staff.view']
  }))
  assert.deepEqual(visible, ['announcement', 'investment', 'staffWorkbench'])
})

test('mine and account pages render all three outward identities', () => {
  const mine = read('pages/mine/index.js')
  const account = read('pages/me/index.js')
  for (const identity of ['PUBLIC', 'TENANT', 'STAFF']) {
    assert.ok(mine.includes(identity) || account.includes(identity))
  }
  assert.match(account, /contactRoleLabel/)
  assert.match(account, /maskedPhone/)
})

test('legacy INTERNAL is normalized only by shared compatibility functions', () => {
  assert.equal(caps.getUserType({ userType: 'INTERNAL' }), 'STAFF')
  assert.doesNotMatch(read('pages/me/index.js'), /userType\s*===\s*'INTERNAL'/)
  assert.doesNotMatch(read('pages/mine/index.js'), /userType\s*===\s*'INTERNAL'/)
  assert.doesNotMatch(read('pages/index/index.js'), /userType\s*===\s*'INTERNAL'/)
})

test('/mobile/me failure path is fail-closed to PUBLIC with no capabilities', () => {
  const auth = read('utils/auth.js')
  assert.match(auth, /function failClosed\(\)/)
  assert.match(auth, /userType:\s*'PUBLIC'/)
  assert.match(auth, /capabilities:\s*Object\.freeze\(\[\]\)/)
  assert.match(auth, /failClosed\(\)/)
})

test('empty capability list hides every sensitive entry', () => {
  assert.deepEqual(keys(caps.getVisibleMineFeatures({
    userType: 'TENANT',
    capabilities: []
  })), [])
  assert.deepEqual(keys(caps.getVisibleHomeFeatures({
    userType: 'STAFF',
    capabilities: []
  })), ['announcement', 'investment'])
})

test('deep-linked tenant pages validate profile before business request', () => {
  for (const file of [
    'pages/bills/index.js',
    'pages/contracts/index.js',
    'pages/workorders/index.js',
    'pages/meeting/index.js',
    'pages/visitors/index.js'
  ]) {
    const source = read(file)
    assert.match(source, /await refreshCurrentProfile\(\)/)
    assert.match(source, /canAccessFeature/)
  }
})

test('multi-identity selection and switch use candidate token endpoint', () => {
  const account = read('pages/me/index.js')
  const auth = read('utils/auth.js')
  assert.match(account, /chooseIdentity/)
  assert.match(account, /dataset\.token/)
  assert.match(auth, /\/mobile\/auth\/select-identity/)
  assert.match(auth, /data:\s*\{\s*candidateToken\s*\}/)
})

test('STAFF presentation uses outward STAFF identity consistently', () => {
  const home = read('pages/index/index.js')
  const mine = read('pages/mine/index.js')
  const account = read('pages/me/index.js')
  assert.match(home, /userType === 'STAFF'/)
  assert.match(mine, /userType === 'STAFF'/)
  assert.match(account, /userType === 'STAFF'/)
})

test('ordinary employee never receives company-sensitive features by identity alone', () => {
  const employee = {
    userType: 'TENANT',
    contactRole: 'EMPLOYEE',
    capabilities: []
  }
  for (const feature of ['bill', 'contract', 'energy', 'parkingCompany']) {
    assert.equal(caps.canAccessFeature(feature, employee), false)
  }
})
