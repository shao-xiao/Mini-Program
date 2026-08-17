const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = filePath => fs.readFileSync(path.join(root, filePath), 'utf8')

test('account page exposes only WeChat verified phone login behind consent', () => {
  const markup = read('pages/me/index.wxml')
  const script = read('pages/me/index.js')
  assert.match(markup, /src="\/assets\/dehui-logo\.png"/)
  assert.match(markup, /open-type="getPhoneNumber"/)
  assert.match(markup, /bindgetphonenumber="loginWithPhoneNumber"/)
  assert.match(markup, /agreement-checkbox-group/)
  assert.match(script, /isConsentCurrent\(\)/)
  assert.match(script, /acceptCurrentConsent\(\)/)
  assert.match(script, /ensureAgreementAccepted/)
  assert.match(script, /authorizeWechatPhone\(detail\.code/)
  assert.doesNotMatch(markup, /password/)
  assert.doesNotMatch(markup, /verificationCode/)
})

test('phone login supports unmatched, automatic binding, and multi-identity selection', () => {
  const script = read('pages/me/index.js')
  const markup = read('pages/me/index.wxml')
  assert.match(script, /ensureWechatSession\(\{\s*userInitiated:\s*true\s*\}\)/)
  assert.match(script, /bindingState === 'UNMATCHED'/)
  assert.match(script, /bindingState === 'PENDING_SELECTION'/)
  assert.match(script, /availableIdentities\.length > 1/)
  assert.match(script, /bindingState === 'PENDING_SELECTION' && availableIdentities\.length > 0/)
  assert.match(markup, /wx:if="\{\{showIdentitySelector\}\}"/)
  assert.match(script, /selectIdentity\(candidateToken/)
  assert.match(script, /unbindCurrentIdentity/)
})

test('identity selection submits only opaque candidateToken', () => {
  const auth = read('utils/auth.js')
  assert.match(auth, /url:\s*'\/mobile\/auth\/select-identity'/)
  assert.match(auth, /data:\s*\{\s*candidateToken\s*\}/)
  for (const forbidden of ['tenantId', 'tenantContactId', 'sysUserId', 'mobileUserId', 'openid']) {
    const selectStart = auth.indexOf('async function selectIdentity')
    const selectEnd = auth.indexOf('async function unbindCurrentIdentity', selectStart)
    assert.equal(auth.slice(selectStart, selectEnd).includes(forbidden), false)
  }
})

test('account page never exposes ordinary registration or password binding', () => {
  const markup = read('pages/me/index.wxml')
  const script = read('pages/me/index.js')
  for (const forbidden of [
    'registerPhoneAccount',
    '/mobile/auth/register-phone',
    '/mobile/auth/bind-tenant',
    '/mobile/auth/bind-internal',
    'openPasswordLogin'
  ]) {
    assert.equal(script.includes(forbidden) || markup.includes(forbidden), false)
  }
})

test('logged account view contains masked identity details, identity switching, legal entries, and logout', () => {
  const markup = read('pages/me/index.wxml')
  const script = read('pages/me/index.js')
  assert.match(markup, /tenantName/)
  assert.match(markup, /contactRoleLabel/)
  assert.match(markup, /availableIdentities/)
  assert.match(markup, /candidateToken/)
  assert.match(script, /maskedPhone/)
  assert.match(script, /logoutSession\(\)/)
  assert.match(script, /wx\.openPrivacyContract/)
})

test('phone quick login delegates expired-session recovery to shared auth utility', () => {
  const auth = read('utils/auth.js')
  assert.match(auth, /function isUnauthorized\(error\)/)
  assert.match(auth, /if \(!isUnauthorized\(error\)\) throw error/)
  assert.match(auth, /clearSession\(\)/)
  assert.match(auth, /force:\s*true/)
})
