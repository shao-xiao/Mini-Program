const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

test('anonymous reservation credentials persist per appointment and stay bounded', () => {
  const storage = new Map()
  global.wx = {
    getStorageSync(key) {
      return storage.get(key)
    },
    setStorageSync(key, value) {
      storage.set(key, value)
    }
  }
  delete require.cache[require.resolve('../utils/visitorReservationHistory')]
  const history = require('../utils/visitorReservationHistory')

  for (let index = 0; index < 25; index += 1) {
    const code = `PVR-${index.toString(16).toUpperCase().padStart(32, '0')}`
    assert.equal(history.saveReservationCredential({
      code,
      submissionToken: `visitor_history_token_${index.toString().padStart(4, '0')}`
    }), true)
  }

  const credentials = history.loadReservationCredentials()
  assert.equal(credentials.length, history.MAX_RESERVATIONS)
  assert.equal(credentials[0].code, 'PVR-00000000000000000000000000000018')
  assert.equal(
    history.credentialForCode(credentials[3].code).submissionToken,
    credentials[3].submissionToken
  )
  assert.equal(history.saveReservationCredential({ code: 'VIS-UNSAFE', submissionToken: 'short' }), false)
})

test('visitor page puts personal history before the new reservation form and reloads after submit', () => {
  const script = fs.readFileSync(path.join(root, 'pages/visitors/index.js'), 'utf8')
  const markup = fs.readFileSync(path.join(root, 'pages/visitors/index.wxml'), 'utf8')
  const style = fs.readFileSync(path.join(root, 'pages/visitors/index.wxss'), 'utf8')
  const historyIndex = markup.indexOf('个人预约历史记录')
  const formIndex = markup.indexOf('新增访客预约')

  assert.ok(historyIndex >= 0)
  assert.ok(formIndex > historyIndex)
  assert.match(script, /\/mobile\/public\/visitors\/history/)
  assert.match(script, /saveReservationCredential/)
  assert.match(script, /await this\.loadPublicVisitors\(\)/)
  assert.match(script, /X-Visitor-Submission-Token/)
  assert.doesNotMatch(markup, /访客邀请|创建邀请|我的访客邀请/)
  assert.match(style, /\.public-submit-button\s*\{[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/)
})
