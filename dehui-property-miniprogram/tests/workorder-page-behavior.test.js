const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')

function loadWorkOrderPage(overrides = {}) {
  let definition
  const toastCalls = []
  const modalCalls = []
  const api = {
    get: async () => ({
      records: [],
      total: 0,
      page: 1,
      profile: { tenantInfo: { name: '测试企业' } }
    }),
    post: async () => ({}),
    ...overrides.api
  }
  const wx = {
    showToast(options) {
      toastCalls.push(options)
    },
    showModal(options) {
      modalCalls.push(options)
      if (overrides.confirmModal) options.success({ confirm: true })
    },
    ...overrides.wx
  }
  const filename = path.join(root, 'pages/workorders/index.js')
  const source = fs.readFileSync(filename, 'utf8')

  vm.runInNewContext(source, {
    Page(value) {
      definition = value
    },
    require(request) {
      if (request === '../../utils/request') return api
      if (request === '../../utils/privacy') {
        return { ensurePrivacyAuthorization: async () => true }
      }
      if (request === '../../utils/auth') {
        return {
          refreshCurrentProfile: async () => ({
            profile: { name: '测试用户' },
            tenantInfo: { name: '测试企业' }
          })
        }
      }
      if (request === '../../utils/capabilities') {
        return {
          canAccessFeature: () => true,
          getFeatureAccessState: () => ({ allowed: true })
        }
      }
      if (request === '../../utils/moduleFieldSchema') {
        return {
          SURFACES: {},
          loadModuleFieldSchema: async () => ({ fields: [] }),
          fieldOptions: (schema, fieldKey, fallback) => fallback,
          fieldValidation: () => ({})
        }
      }
      throw new Error(`Unexpected dependency: ${request}`)
    },
    console,
    wx
  }, { filename })

  const page = {
    ...definition,
    _active: true,
    _loadSequence: 0,
    data: structuredClone(definition.data),
    setData(payload) {
      for (const [key, value] of Object.entries(payload)) {
        const parts = key.split('.')
        if (parts.length === 1) {
          this.data[key] = value
        } else {
          const [parent, child] = parts
          this.data[parent] = { ...this.data[parent], [child]: value }
        }
      }
    },
    selectComponent() {
      return null
    }
  }
  return { page, toastCalls, modalCalls }
}

test('workorder history never exposes unknown English enum codes', async () => {
  const { page } = loadWorkOrderPage({
    api: {
      get: async () => ({
        records: [{
          id: 9,
          title: '未知类型测试',
          problemCategory: 'LEGACY_CODE',
          status: 'WAITING_EXTERNAL',
          createdTime: '2026-07-30T10:00:00'
        }],
        total: 1,
        page: 1,
        profile: { tenantInfo: { name: '测试企业' } }
      })
    }
  })

  await page.loadWorkOrders()

  assert.equal(page.data.workOrders[0].categoryText, '其他')
  assert.equal(page.data.workOrders[0].statusText, '状态更新')
  assert.doesNotMatch(page.data.workOrders[0].categoryText, /LEGACY_CODE/)
  assert.doesNotMatch(page.data.workOrders[0].statusText, /WAITING_EXTERNAL/)
})

test('workorder submit shows the backend Chinese error for ordinary failures', async () => {
  const { page, toastCalls } = loadWorkOrderPage({
    api: {
      post: async () => {
        throw new Error('报修信息保存失败，请稍后重试')
      }
    }
  })
  page.data.form = {
    problemCategory: 'ELECTRIC',
    reporterName: '测试用户',
    companyName: '测试企业',
    reportedAt: '2026-07-30 10:00:00',
    description: '配电箱跳闸',
    remark: '',
    customFields: {}
  }

  await page.submitWorkOrder()

  assert.equal(toastCalls.at(-1).title, '报修信息保存失败，请稍后重试')
  assert.equal(toastCalls.at(-1).icon, 'none')
  assert.equal(page.data.submitting, false)
})

test('workorder history toggle switches between the three-card stack and all loaded records', () => {
  const { page } = loadWorkOrderPage()
  page.data.total = 5
  page.data.workOrders = [1, 2, 3, 4, 5].map(id => ({ id }))
  page.data.visibleWorkOrders = page.data.workOrders.slice(0, 3)
  const paginationCalls = []
  page.loadWorkOrders = append => {
    paginationCalls.push(append)
  }

  page.toggleHistory()
  assert.equal(page.data.historyExpanded, true)
  assert.deepEqual(page.data.visibleWorkOrders.map(item => item.id), [1, 2, 3, 4, 5])
  page.onReachBottom()
  assert.deepEqual(paginationCalls, [true])

  page.toggleHistory()
  assert.equal(page.data.historyExpanded, false)
  assert.deepEqual(page.data.visibleWorkOrders.map(item => item.id), [1, 2, 3])
})
