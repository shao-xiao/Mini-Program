const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')

function activeDefaultBaseURL() {
  return require('../config/env').defaultBaseURL
}

test('download writes invoice to a pdf file path with auth header', async () => {
  let downloadOptions
  global.wx = {
    env: { USER_DATA_PATH: path.join('C:', 'tmp', 'wechat') },
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: options.filePath })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  const filePath = await api.download('/mobile/bills/16/invoice/download', '张昭通讯费-26年04月发票.pdf')

  assert.equal(downloadOptions.url, `${activeDefaultBaseURL()}/mobile/bills/16/invoice/download`)
  assert.equal(downloadOptions.header.Authorization, 'tenant-token')
  assert.match(downloadOptions.filePath, /invoice-16-[a-z0-9]+\.pdf$/)
  assert.equal(filePath, downloadOptions.filePath)
})

test('download writes electronic contract to a contract pdf file path', async () => {
  let downloadOptions
  global.wx = {
    env: { USER_DATA_PATH: path.join('C:', 'tmp', 'wechat') },
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: options.filePath })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  const filePath = await api.download('/mobile/contracts/21/file/download', '德汇咖啡电子合同.pdf')

  assert.equal(downloadOptions.url, `${activeDefaultBaseURL()}/mobile/contracts/21/file/download`)
  assert.equal(downloadOptions.header.Authorization, 'tenant-token')
  assert.match(downloadOptions.filePath, /contract-21-[a-z0-9]+\.pdf$/)
  assert.equal(filePath, downloadOptions.filePath)
})

test('download does not duplicate the api prefix on protected mobile file URLs', async () => {
  let downloadOptions
  global.wx = {
    env: { USER_DATA_PATH: path.join('C:', 'tmp', 'wechat') },
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: options.filePath })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  const filePath = await api.download('/api/mobile/files/25', 'visitor-qr-14.png')

  assert.equal(downloadOptions.url, `${activeDefaultBaseURL()}/mobile/files/25`)
  assert.equal(downloadOptions.header.Authorization, 'tenant-token')
  assert.match(downloadOptions.filePath, /\.png$/)
  assert.equal(filePath, downloadOptions.filePath)
})

test('temporary protected image download keeps auth without creating a persistent user-data file', async () => {
  let downloadOptions
  global.wx = {
    env: { USER_DATA_PATH: path.join('C:', 'tmp', 'wechat') },
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: 'wxfile://tmp/tenant-logo' })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  const filePath = await api.download(
    '/api/mobile/files/31',
    'tenant-logo.png',
    { temporary: true, silent: true }
  )

  assert.equal(downloadOptions.url, `${activeDefaultBaseURL()}/mobile/files/31`)
  assert.equal(downloadOptions.header.Authorization, 'tenant-token')
  assert.equal(Object.hasOwn(downloadOptions, 'filePath'), false)
  assert.equal(filePath, 'wxfile://tmp/tenant-logo')
})

test('external file download never forwards the current session token', async () => {
  let downloadOptions
  global.wx = {
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: 'wxfile://tmp/external-image' })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  await api.download(
    'https://files.example.invalid/legacy-image.jpg',
    'legacy-image.jpg',
    {
      temporary: true,
      header: {
        Authorization: 'caller-supplied-token',
        authorization: 'lowercase-token'
      }
    }
  )

  assert.equal(downloadOptions.url, 'https://files.example.invalid/legacy-image.jpg')
  assert.equal(downloadOptions.header.Authorization, undefined)
  assert.equal(downloadOptions.header.authorization, undefined)
})

test('download merges an anonymous reservation capability header without requiring login', async () => {
  let downloadOptions
  global.wx = {
    env: { USER_DATA_PATH: path.join('C:', 'tmp', 'wechat') },
    getStorageSync(key) {
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    downloadFile(options) {
      downloadOptions = options
      options.success({ statusCode: 200, tempFilePath: options.filePath })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  await api.download(
    '/mobile/public/visitors/PVR-ABCDEF0123456789ABCDEF0123456789/qr',
    'visitor-qr.png',
    { header: { 'X-Visitor-Submission-Token': 'public-visitor-token-0001' } }
  )

  assert.equal(downloadOptions.header.Authorization, undefined)
  assert.equal(
    downloadOptions.header['X-Visitor-Submission-Token'],
    'public-visitor-token-0001'
  )
})

test('upload uses requestURL with the active environment base URL', async () => {
  let uploadOptions
  global.wx = {
    getStorageSync(key) {
      if (key === 'token') return 'tenant-token'
      if (key === 'apiBaseURL') return 'http://localhost:8080/api'
      return ''
    },
    uploadFile(options) {
      uploadOptions = options
      options.success({ data: JSON.stringify({ code: 200, data: { ok: true } }) })
    },
    showToast() {}
  }
  delete require.cache[require.resolve('../utils/request')]
  delete require.cache[require.resolve('../config/env')]

  const api = require('../utils/request')
  const result = await api.upload('/mobile/contracts/1/file', 'C:\\tmp\\contract.pdf')

  assert.equal(uploadOptions.url, `${activeDefaultBaseURL()}/mobile/contracts/1/file`)
  assert.equal(uploadOptions.header.Authorization, 'tenant-token')
  assert.equal(uploadOptions.filePath, 'C:\\tmp\\contract.pdf')
  assert.deepEqual(result, { ok: true })
})
