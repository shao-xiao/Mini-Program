const CONSENT_STORAGE_KEY = 'legalConsent'
const SERVICE_AGREEMENT_VERSION = '2026-07-20-v1'
const PRIVACY_POLICY_VERSION = '2026-07-20-v1'

function readConsent() {
  if (typeof wx === 'undefined' || typeof wx.getStorageSync !== 'function') {
    return null
  }
  const value = wx.getStorageSync(CONSENT_STORAGE_KEY)
  return value && typeof value === 'object' ? value : null
}

function isValidAcceptedAt(value) {
  return typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Date.parse(value))
}

function isConsentCurrent(consent) {
  const value = arguments.length > 0 ? consent : readConsent()
  return !!(
    value &&
    value.serviceAgreementVersion === SERVICE_AGREEMENT_VERSION &&
    value.privacyPolicyVersion === PRIVACY_POLICY_VERSION &&
    isValidAcceptedAt(value.acceptedAt)
  )
}

function acceptCurrentConsent(acceptedAt = new Date().toISOString()) {
  const consent = {
    serviceAgreementVersion: SERVICE_AGREEMENT_VERSION,
    privacyPolicyVersion: PRIVACY_POLICY_VERSION,
    acceptedAt
  }
  if (!isConsentCurrent(consent)) {
    throw new Error('协议确认时间无效')
  }
  if (typeof wx !== 'undefined' && typeof wx.setStorageSync === 'function') {
    wx.setStorageSync(CONSENT_STORAGE_KEY, consent)
  }
  return consent
}

function clearConsent() {
  if (typeof wx !== 'undefined' && typeof wx.removeStorageSync === 'function') {
    wx.removeStorageSync(CONSENT_STORAGE_KEY)
  }
}

module.exports = {
  CONSENT_STORAGE_KEY,
  SERVICE_AGREEMENT_VERSION,
  PRIVACY_POLICY_VERSION,
  readConsent,
  isConsentCurrent,
  acceptCurrentConsent,
  clearConsent
}
