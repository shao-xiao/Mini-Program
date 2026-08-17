const assert = require('node:assert/strict')
const test = require('node:test')

function storageWx(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    values,
    wx: {
      getStorageSync(key) {
        return values.get(key) || ''
      },
      setStorageSync(key, value) {
        values.set(key, value)
      },
      removeStorageSync(key) {
        values.delete(key)
      }
    }
  }
}

test('consent persists both versions and acceptance time', () => {
  const storage = storageWx()
  global.wx = storage.wx
  const consent = require('../utils/consent')
  const acceptedAt = '2026-07-20T08:00:00.000Z'

  const saved = consent.acceptCurrentConsent(acceptedAt)
  assert.deepEqual(consent.readConsent(), saved)
  assert.equal(consent.isConsentCurrent(), true)
  assert.equal(saved.serviceAgreementVersion, consent.SERVICE_AGREEMENT_VERSION)
  assert.equal(saved.privacyPolicyVersion, consent.PRIVACY_POLICY_VERSION)
})

test('any agreement version change or invalid time invalidates cached consent', () => {
  const storage = storageWx()
  global.wx = storage.wx
  const consent = require('../utils/consent')

  assert.equal(consent.isConsentCurrent({
    serviceAgreementVersion: 'old',
    privacyPolicyVersion: consent.PRIVACY_POLICY_VERSION,
    acceptedAt: '2026-07-20T08:00:00.000Z'
  }), false)
  assert.equal(consent.isConsentCurrent({
    serviceAgreementVersion: consent.SERVICE_AGREEMENT_VERSION,
    privacyPolicyVersion: 'old',
    acceptedAt: '2026-07-20T08:00:00.000Z'
  }), false)
  assert.equal(consent.isConsentCurrent({
    serviceAgreementVersion: consent.SERVICE_AGREEMENT_VERSION,
    privacyPolicyVersion: consent.PRIVACY_POLICY_VERSION,
    acceptedAt: 'not-a-date'
  }), false)
})

test('clearing or logging out of a session does not erase legal consent', async () => {
  const storage = storageWx({
    token: '',
    identity: 'TENANT',
    userInfo: { userType: 'TENANT' },
    legalConsent: {
      serviceAgreementVersion: '2026-07-20-v1',
      privacyPolicyVersion: '2026-07-20-v1',
      acceptedAt: '2026-07-20T08:00:00.000Z'
    }
  })
  global.wx = storage.wx
  const auth = require('../utils/auth')

  auth.clearSession()
  assert.equal(storage.values.has('token'), false)
  assert.equal(storage.values.has('userInfo'), false)
  assert.equal(storage.values.has('identity'), false)
  assert.equal(storage.values.has('legalConsent'), true)

  await auth.logout()
  assert.equal(storage.values.has('legalConsent'), true)
})
