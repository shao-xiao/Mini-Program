const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const {
  getVisibleHomeFeatures,
  getVisibleMineFeatures
} = require(path.join(root, 'utils/capabilities'))

const read = filePath => fs.readFileSync(path.join(root, filePath), 'utf8')
const keys = features => features.map(item => item.key)

test('PUBLIC receives no tenant business entries', () => {
  const home = keys(getVisibleHomeFeatures({ userType: 'PUBLIC' }))
  assert.deepEqual(home, ['announcement', 'investment', 'meetingPublic', 'visitorGuide'])
  assert.deepEqual(keys(getVisibleMineFeatures({ userType: 'PUBLIC' })), [])
})

test('TENANT home keeps shared services and moves personal contract and parking entries to mine', () => {
  const employee = {
    userType: 'TENANT',
    contactRole: 'EMPLOYEE',
    capabilities: [
      'bill.view',
      'contract.view',
      'workorder.view',
      'parking.mine.view',
      'meeting.book',
      'visitor.create'
    ]
  }
  const home = keys(getVisibleHomeFeatures(employee))
  for (const key of ['investment', 'bill', 'workorder', 'meeting', 'visitor']) {
    assert.ok(home.includes(key), `TENANT home should see granted ${key}`)
  }
  for (const key of ['contract', 'parkingMine', 'energy', 'parkingCompany']) {
    assert.ok(!home.includes(key), `TENANT home should not show ${key}`)
  }
  const mine = keys(getVisibleMineFeatures(employee))
  assert.ok(mine.includes('contract'))
  assert.ok(mine.includes('parkingMine'))
})

test('company-level capabilities are visible only when explicitly granted', () => {
  const responsible = {
    userType: 'TENANT',
    contactRole: 'RESPONSIBLE',
    capabilities: ['energy.company.view', 'parking.company.view', 'parking.mine.view']
  }
  const home = keys(getVisibleHomeFeatures(responsible))
  assert.ok(home.includes('energy'))
  assert.ok(home.includes('parkingCompany'))
  assert.ok(!home.includes('parkingMine'))
  assert.ok(keys(getVisibleMineFeatures(responsible)).includes('parkingMine'))
})

test('tenant business pages guard deep links before requesting data', () => {
  const pageFiles = {
    bill: 'pages/bills/index.js',
    contract: 'pages/contracts/index.js',
    workorder: 'pages/workorders/index.js',
    meeting: 'pages/meeting/index.js',
    visitor: 'pages/visitors/index.js'
  }
  for (const [featureKey, filePath] of Object.entries(pageFiles)) {
    const source = read(filePath)
    assert.match(source, /refreshCurrentProfile/)
    assert.match(source, new RegExp(`FEATURE_KEY = '${featureKey}'`))
    assert.match(source, /canAccessFeature\(FEATURE_KEY, profile\)/)
    assert.match(source, /getFeatureAccessState\(FEATURE_KEY, profile\)/)
    assert.match(source, /pageState:\s*'login-required'/)
    assert.match(source, /pageState:\s*'unauthorized'/)
  }
})

test('visitor deep link provides login and forbidden states', () => {
  const markup = read('pages/visitors/index.wxml')
  assert.match(markup, /pageState === 'login-required'/)
  assert.match(markup, /pageState === 'unauthorized'/)
})
