const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const source = fs.readFileSync(
  path.resolve(__dirname, '../pages/meeting/index.js'),
  'utf8'
)

function setByPath(target, key, value) {
  const parts = key.split('.')
  let cursor = target
  for (let index = 0; index < parts.length - 1; index += 1) {
    cursor[parts[index]] = cursor[parts[index]] || {}
    cursor = cursor[parts[index]]
  }
  cursor[parts[parts.length - 1]] = value
}

function createMeetingPage(overrides = {}) {
  const api = {
    get: async () => ({ available: true }),
    post: async () => ({}),
    ...overrides.api
  }
  const toastCalls = []
  let definition
  const wx = {
    showToast(options) {
      toastCalls.push(options)
    },
    showModal() {},
    navigateTo() {},
    redirectTo() {},
    switchTab() {},
    pageScrollTo() {},
    setNavigationBarTitle() {},
    stopPullDownRefresh() {},
    ...overrides.wx
  }
  const sandbox = {
    console,
    wx,
    Page(value) {
      definition = value
    },
    require(id) {
      if (id === '../../utils/request') return api
      if (id === '../../utils/auth') {
        return {
          authorizeWechatPhone: async () => ({}),
          refreshCurrentProfile: async () => ({})
        }
      }
      if (id === '../../utils/capabilities') {
        return {
          canAccessFeature: () => true,
          getFeatureAccessState: () => 'allowed',
          getUserType: () => 'TENANT',
          hasCapability: () => true
        }
      }
      if (id === '../../utils/consent') {
        return {
          acceptCurrentConsent() {},
          isConsentCurrent: () => true
        }
      }
      if (id === '../../utils/moduleFieldSchema') {
        return { loadModuleFieldSchema: async () => ({ fields: [] }) }
      }
      if (id === '../../config/env') return { getBaseURL: () => 'http://127.0.0.1:8080/api' }
      throw new Error(`Unexpected require: ${id}`)
    }
  }
  vm.runInNewContext(source, sandbox, { filename: 'pages/meeting/index.js' })
  assert.ok(definition, 'meeting page definition should be captured')

  const page = {
    ...definition,
    data: JSON.parse(JSON.stringify(definition.data)),
    _active: true,
    _loadSequence: 0,
    _availabilitySequence: 0,
    setData(payload, callback) {
      for (const [key, value] of Object.entries(payload)) {
        setByPath(this.data, key, value)
      }
      if (callback) callback()
    },
    selectComponent() {
      return null
    }
  }
  page.data.form = {
    ...page.data.form,
    date: '2099-07-28',
    startTime: '10:00',
    endTime: '11:00',
    meetingRoomId: 9
  }
  page.data.selectedRoom = {
    id: 9,
    roomName: '自动化会议室',
    capacity: 12,
    openTime: '08:00:00',
    closeTime: '22:00:00',
    openHoursText: '08:00-22:00'
  }
  return { page, api, toastCalls }
}

test('availability check rejects times outside the selected room opening hours locally', async () => {
  let getCalls = 0
  const { page } = createMeetingPage({
    api: {
      get: async () => {
        getCalls += 1
        return { available: true }
      }
    }
  })
  page.data.form.endTime = '23:00'

  await page.checkSelectedRoomAvailability()

  assert.equal(getCalls, 0)
  assert.equal(page.data.availabilityState, 'invalid')
  assert.match(page.data.availabilityMessage, /开放时间为 08:00-22:00/)
})

test('availability check maps a server conflict to occupied and records the exact interval key', async () => {
  const calls = []
  const { page } = createMeetingPage({
    api: {
      get: async (...args) => {
        calls.push(args)
        return { available: false }
      }
    }
  })

  await page.checkSelectedRoomAvailability()

  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], '/mobile/public/meeting-rooms/9/availability')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0][1])), {
    startTime: '2099-07-28T10:00:00',
    endTime: '2099-07-28T11:00:00'
  })
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0][2])), { silent: true })
  assert.equal(page.data.availabilityState, 'occupied')
  assert.equal(page.data.availabilityCheckedKey, '9|2099-07-28|10:00|11:00')
})

test('a slower old availability response cannot overwrite the latest scrolled interval', async () => {
  const pending = []
  const { page } = createMeetingPage({
    api: {
      get: () => new Promise(resolve => pending.push(resolve))
    }
  })

  const first = page.checkSelectedRoomAvailability()
  page.data.form.startTime = '12:00'
  page.data.form.endTime = '13:00'
  const second = page.checkSelectedRoomAvailability()
  pending[1]({ available: true })
  await second
  pending[0]({ available: false })
  await first

  assert.equal(page.data.availabilityState, 'available')
  assert.equal(page.data.availabilityCheckedKey, '9|2099-07-28|12:00|13:00')
  assert.equal(page.data.availabilityMessage, '该时间段空闲，可以预约')
})

test('submit is blocked when the selected interval has not been confirmed as available', async () => {
  let postCalls = 0
  const { page, toastCalls } = createMeetingPage({
    api: {
      get: async () => ({ available: false }),
      post: async () => {
        postCalls += 1
        return {}
      }
    }
  })
  page.data.availabilityState = 'occupied'
  page.data.availabilityCheckedKey = page.currentAvailabilityKey()

  await page.submitBooking()

  assert.equal(postCalls, 0)
  assert.equal(toastCalls.at(-1).title, '该时间段已被占用，请调整')
})

test('guest submit rejects an invalid editable contact phone before calling the API', async () => {
  let postCalls = 0
  const { page, toastCalls } = createMeetingPage({
    api: {
      post: async () => {
        postCalls += 1
        return {}
      }
    }
  })
  page.data.isPublicMode = true
  page.data.guestPhoneReady = true
  page.data.availabilityState = 'available'
  page.data.availabilityCheckedKey = page.currentAvailabilityKey()
  page.data.form.applicantName = '张昭'
  page.data.form.contactPhone = '12345'
  page.data.form.purpose = '项目会议'

  await page.submitBooking()

  assert.equal(postCalls, 0)
  assert.equal(toastCalls.at(-1).title, '请输入正确的联系电话')
})

test('guest submit sends the editable contact phone without client-owned identity ids', async () => {
  const postCalls = []
  const { page } = createMeetingPage({
    api: {
      post: async (...args) => {
        postCalls.push(args)
        return { bookingNumber: 'MBK-TEST', status: 'PENDING' }
      }
    }
  })
  page.loadMeetingHome = () => {}
  page.data.isPublicMode = true
  page.data.guestPhoneReady = true
  page.data.availabilityState = 'available'
  page.data.availabilityCheckedKey = page.currentAvailabilityKey()
  page.data.form.applicantName = '张昭'
  page.data.form.contactPhone = '13800009999'
  page.data.form.purpose = '项目会议'

  await page.submitBooking()

  assert.equal(postCalls.length, 1)
  assert.equal(postCalls[0][0], '/mobile/public/meeting-bookings')
  assert.equal(postCalls[0][1].contactPhone, '13800009999')
  assert.equal(Object.hasOwn(postCalls[0][1], 'tenantId'), false)
  assert.equal(Object.hasOwn(postCalls[0][1], 'mobileUserId'), false)
})
