const assert = require('node:assert/strict')
const test = require('node:test')
const capabilities = require('../utils/capabilities')

test('sensitive feature access requires matching identity and server capability', () => {
  assert.equal(capabilities.canAccessFeature('bill', {
    userType: 'TENANT',
    capabilities: []
  }), false)
  assert.equal(capabilities.canAccessFeature('bill', {
    userType: 'PUBLIC',
    capabilities: ['bill.view']
  }), false)
  assert.equal(capabilities.canAccessFeature('bill', {
    userType: 'TENANT',
    capabilities: ['bill.view']
  }), true)
  assert.equal(capabilities.canAccessFeature('energy', {
    userType: 'STAFF',
    capabilities: ['energy.staff.view']
  }), true)
})

test('missing server capabilities fail closed for TENANT, STAFF, and legacy INTERNAL', () => {
  for (const userType of ['TENANT', 'STAFF', 'INTERNAL']) {
    assert.equal(capabilities.hasCapability('bill.view', { userType }), false)
    assert.equal(capabilities.hasCapability('workorder.view', { userType }), false)
  }
  assert.equal(capabilities.getUserType({ userType: 'INTERNAL' }), 'STAFF')
  assert.deepEqual(capabilities.FALLBACK_CAPABILITIES.TENANT, [])
  assert.deepEqual(capabilities.FALLBACK_CAPABILITIES.STAFF, [])
})

test('PUBLIC home contains only real public routes', () => {
  const home = capabilities.getVisibleHomeFeatures({ userType: 'PUBLIC' })
  assert.deepEqual(home.map(item => item.key), ['announcement', 'investment', 'meetingPublic', 'visitorGuide'])
  assert.deepEqual(Object.keys(home[0]), [
    'key',
    'title',
    'description',
    'url',
    'capability',
    'publicRoute'
  ])
})

test('TENANT mine and access-state helpers honor exact server grants', () => {
  const profile = {
    userType: 'TENANT',
    capabilities: [
      'bill.view',
      'contract.view',
      'parking.mine.view',
      'meeting.book',
      'workorder.view',
      'visitor.create'
    ]
  }
  assert.deepEqual(
    capabilities.getVisibleMineFeatures(profile).map(item => item.key),
    ['bill', 'rooms', 'contract', 'parkingMine', 'meeting', 'workorder', 'visitor']
  )
  const homeKeys = capabilities.getVisibleHomeFeatures(profile).map(item => item.key)
  assert.equal(homeKeys.includes('contract'), false)
  assert.equal(homeKeys.includes('parkingMine'), false)
  assert.deepEqual(capabilities.getFeatureAccessState('bill', { userType: 'PUBLIC' }), {
    featureKey: 'bill',
    capability: 'bill.view',
    userType: 'PUBLIC',
    allowed: false,
    loginRequired: true,
    forbidden: false,
    reason: 'login-required'
  })
  const denied = capabilities.getFeatureAccessState('bill', {
    userType: 'TENANT',
    capabilities: []
  })
  assert.equal(denied.forbidden, true)
  assert.equal(denied.reason, 'forbidden')
})
