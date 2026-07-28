const STORAGE_KEY = 'visitorReservationCredentialsV1'
const MAX_RESERVATIONS = 20
const CODE_PATTERN = /^PVR-[A-F0-9]{32}$/
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{16,128}$/

function normalizeCredential(value) {
  if (!value || typeof value !== 'object') return null
  const code = String(value.code || '').trim().toUpperCase()
  const submissionToken = String(value.submissionToken || '').trim()
  if (!CODE_PATTERN.test(code) || !TOKEN_PATTERN.test(submissionToken)) return null
  return { code, submissionToken }
}

function loadReservationCredentials() {
  try {
    const stored = wx.getStorageSync(STORAGE_KEY)
    if (!Array.isArray(stored)) return []
    const seen = new Set()
    return stored
      .map(normalizeCredential)
      .filter(item => item && !seen.has(item.code) && seen.add(item.code))
      .slice(0, MAX_RESERVATIONS)
  } catch {
    return []
  }
}

function saveReservationCredential(value) {
  const credential = normalizeCredential(value)
  if (!credential) return false
  const next = [
    credential,
    ...loadReservationCredentials().filter(item => item.code !== credential.code)
  ].slice(0, MAX_RESERVATIONS)
  try {
    wx.setStorageSync(STORAGE_KEY, next)
    return true
  } catch {
    return false
  }
}

function credentialForCode(code) {
  const normalizedCode = String(code || '').trim().toUpperCase()
  return loadReservationCredentials().find(item => item.code === normalizedCode) || null
}

module.exports = {
  STORAGE_KEY,
  MAX_RESERVATIONS,
  loadReservationCredentials,
  saveReservationCredential,
  credentialForCode
}
