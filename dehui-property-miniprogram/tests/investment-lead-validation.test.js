const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function loadInvestmentPage(relativePath, authority, wx, requestApi = {}) {
  let definition
  vm.runInNewContext(read(relativePath), {
    Page(value) {
      definition = value
    },
    console,
    Date,
    setTimeout,
    clearTimeout,
    wx,
    require(id) {
      if (id.endsWith('/utils/request')) return requestApi
      if (id.endsWith('/utils/auth')) {
        return {
          authorizeWechatPhone: async () => authority,
          ensureWechatSession: async () => authority,
          refreshCurrentProfile: async () => authority
        }
      }
      if (id.endsWith('/utils/capabilities')) {
        return {
          canAccessFeature: (feature, profile) => feature !== 'investmentBooking'
            || profile.capabilities.includes('investment.booking.create'),
          getFeatureAccessState: () => ({ loginRequired: false })
        }
      }
      if (id.endsWith('/utils/investmentPhone')) {
        return require(path.join(root, 'utils/investmentPhone'))
      }
      if (id.endsWith('/config/env')) return { getBaseURL: () => 'http://127.0.0.1:8080/api' }
      throw new Error(`Unexpected module: ${id}`)
    }
  })
  return definition
}

function createPageInstance(definition, overrides = {}) {
  const instance = {
    ...definition,
    _active: true,
    data: { ...definition.data, ...overrides },
    setData(patch) {
      for (const [key, value] of Object.entries(patch)) {
        const pathParts = key.split('.')
        if (pathParts.length === 1) {
          this.data[key] = value
        } else {
          this.data[pathParts[0]] = { ...this.data[pathParts[0]], [pathParts[1]]: value }
        }
      }
    }
  }
  return instance
}

function createPhoneConsultationHarness({ overview = {}, requestApi = {} } = {}) {
  const actionSheets = []
  const phoneCalls = []
  const toasts = []
  const wx = {
    showActionSheet: options => actionSheets.push(options),
    makePhoneCall: options => phoneCalls.push(options),
    showToast: options => toasts.push(options)
  }
  const authority = { capabilities: [] }
  const definition = loadInvestmentPage(
    'pages/investment/index.js',
    authority,
    wx,
    requestApi
  )
  const instance = createPageInstance(definition, { overview })

  return { actionSheets, definition, instance, phoneCalls, toasts }
}

test('investment viewing appointment validates fields before submitting', () => {
  const source = read('pages/investment/index.js')

  assert.match(source, /validateLeadForm\(form\)/)
  assert.match(source, /CONTACT_NAME_REG/)
  assert.match(source, /COMPANY_NAME_REG/)
  assert.match(source, /SAFE_TEXT_REG/)
  assert.match(source, /请输入正确的联系人姓名/)
  assert.match(source, /请输入正确的公司名称/)
  assert.match(source, /请输入正确的意向面积，只能填写数字/)
  assert.match(source, /备注最多 200 个字符/)
  assert.match(source, /buildPreferredVisitTime\(form\.preferredVisitDate,\s*form\.preferredVisitClock\)/)

  const submitIndex = source.indexOf('async submitLead()')
  const validateIndex = source.indexOf('const validation = validateLeadForm(form)')
  const postIndex = source.indexOf("api.post('/mobile/investment/appointments'")
  assert.ok(validateIndex > submitIndex, 'submitLead should validate the form')
  assert.ok(validateIndex < postIndex, 'validation should happen before the backend request')
})

test('investment viewing appointment inputs have mobile-side length and keyboard constraints', () => {
  const wxml = read('pages/investment/index.wxml')

  assert.match(wxml, /maxlength="20"[\s\S]*data-field="name"/)
  assert.match(wxml, /open-type="getPhoneNumber"/)
  assert.match(wxml, /bindgetphonenumber="authorizePhone"/)
  assert.doesNotMatch(wxml, /data-field="phone"/)
  assert.match(wxml, /maxlength="50"[\s\S]*data-field="companyName"/)
  assert.match(wxml, /value="\{\{leadForm\.intendedRoom\}\}" disabled/)
  assert.match(wxml, /type="digit"[\s\S]*data-field="desiredArea"/)
  assert.match(wxml, /maxlength="50"[\s\S]*data-field="intendedUse"/)
  assert.match(wxml, /maxlength="200"[\s\S]*data-field="remark"/)
  assert.match(wxml, /<calendar-date-field/)
  assert.match(wxml, /<calendar-time-field/)
  assert.doesNotMatch(wxml, /picker\s+mode="date"/)
  assert.doesNotMatch(wxml, /picker\s+mode="time"/)
})

test('investment center supports listing card filters and direct listing appointment entry', () => {
  const source = read('pages/investment/index.js')
  const wxml = read('pages/investment/index.wxml')
  const wxss = read('pages/investment/index.wxss')

  assert.match(source, /activeSection:\s*'rooms'/)
  assert.match(source, /floorFilters/)
  assert.match(source, /typeFilters/)
  assert.match(source, /areaFilters/)
  assert.match(source, /floorFiltersFromRooms\(rooms\)/)
  assert.match(source, /compareFloorLabel/)
  assert.match(source, /\.sort\(compareFloorLabel\)/)
  assert.match(source, /applyFilters\(\)/)
  assert.match(source, /openLeadPopup\(event\)/)
  assert.match(source, /OFFICE:\s*'办公'/)
  assert.match(source, /WAREHOUSE:\s*'仓库'/)
  assert.match(source, /STORAGE:\s*'仓库'/)
  assert.match(source, /PUBLIC_AREA:\s*'公共区域'/)
  assert.match(source, /RENTED:\s*'已出租'/)
  assert.match(source, /RESERVED:\s*'预约\/不可用'/)
  assert.match(source, /listingId:\s*room\.listingId/)
  assert.doesNotMatch(source, /roomId:\s*room\.roomId/)
  assert.match(source, /leadPopupVisible/)
  assert.match(source, /roomStats:\s*EMPTY_ROOM_STATS/)

  assert.match(wxml, /data-field="floor"[\s\S]*bindtap="changeFilter"/)
  assert.match(wxml, /data-field="type"[\s\S]*bindtap="changeFilter"/)
  assert.match(wxml, /data-field="area"[\s\S]*bindtap="changeFilter"/)
  assert.match(wxml, /class="room-card"/)
  assert.match(wxml, /class="cover-wrap"/)
  assert.match(wxml, /bindtap="openLeadPopup"/)
  assert.match(wxml, /\/pages\/investment\/detail\/index/)
  assert.match(wxml, /wx:if="\{\{canBookViewing && leadPopupVisible\}\}"/)
  assert.match(wxml, /电话咨询/)
  assert.doesNotMatch(wxml, /scroll-x/)
  assert.match(wxml, /暂无可展示房源/)

  assert.match(wxss, /\.investment-actions\s*\{[\s\S]*display:\s*flex/)
  assert.match(wxss, /\.filter-options\s*\{[\s\S]*flex-wrap:\s*wrap/)
  assert.match(wxss, /\.primary-button,[\s\S]*width:\s*100%/)
  assert.match(wxss, /overflow-x:\s*hidden/)
})

test('investment detail page keeps listing appointment context and mobile calendar controls', () => {
  const source = read('pages/investment/detail/index.js')
  const wxml = read('pages/investment/detail/index.wxml')
  const pageJson = read('pages/investment/detail/index.json')

  assert.match(source, /\/mobile\/investment\/listings\/\$\{this\.data\.id\}/)
  assert.match(source, /listingId:\s*detail\.listingId/)
  assert.doesNotMatch(source, /roomId:\s*detail\.roomId/)
  assert.match(source, /WAREHOUSE:\s*'仓库'/)
  assert.match(source, /RENTED:\s*'已出租'/)
  assert.match(source, /RESERVED:\s*'预约\/不可用'/)
  assert.match(source, /buildPreferredVisitTime\(form\.preferredVisitDate,\s*form\.preferredVisitClock\)/)
  assert.match(source, /canAccessFeature\('investmentBooking', this\.data\.profile\)/)
  assert.match(source, /canAccessFeature\('investment', profile\)/)

  assert.match(pageJson, /calendar-date-field/)
  assert.match(pageJson, /calendar-time-field/)
  assert.match(wxml, /<calendar-date-field/)
  assert.match(wxml, /<calendar-time-field/)
  assert.doesNotMatch(wxml, /picker\s+mode="date"/)
  assert.doesNotMatch(wxml, /picker\s+mode="time"/)
})

test('backend capabilities gate investment viewing and appointment entry on list and detail pages', () => {
  for (const page of ['pages/investment/index', 'pages/investment/detail/index']) {
    const source = read(`${page}.js`)
    const wxml = read(`${page}.wxml`)
    assert.match(source, /refreshCurrentProfile/)
    assert.match(source, /canAccessFeature\('investment'/)
    assert.match(source, /canAccessFeature\('investmentBooking'/)
    assert.match(source, /const profile = me \|\| \{\}/)
    assert.doesNotMatch(source, /const profile = me\?\.profile \|\| me/)
    assert.match(source, /'leadForm\.phone': profile\.maskedPhone \|\| ''/)
    assert.match(source, /if \(!this\.data\.profile\?\.maskedPhone\)/)
    assert.doesNotMatch(source, /profile\?\.phone/)
    assert.match(source, /leadPopupVisible: canBookViewing && this\.data\.leadPopupVisible/)
    assert.match(wxml, /canBookViewing/)
    assert.match(source, /onUnload\(\)/)
    assert.match(source, /isCurrentRequest\(/)
  }
})

test('verified phone authorization keeps the full authority and masked phone on both investment pages', async () => {
  const authority = {
    userType: 'TENANT',
    bindingState: 'BOUND',
    maskedPhone: '166****7751',
    profile: { name: '张经理', maskedPhone: '166****7751', avatarUrl: '' },
    capabilities: ['investment.view', 'investment.booking.create']
  }

  for (const page of ['pages/investment/index.js', 'pages/investment/detail/index.js']) {
    const toasts = []
    const modals = []
    const wx = {
      showToast: value => toasts.push(value),
      showModal: value => modals.push(value)
    }
    const definition = loadInvestmentPage(page, authority, wx)
    const instance = createPageInstance(definition, {
      canBookViewing: true,
      leadPopupVisible: true,
      detail: { availableForAppointment: true },
      leadForm: { ...definition.data.leadForm }
    })

    await definition.authorizePhone.call(instance, { detail: { code: 'wx-phone-code' } })

    assert.equal(instance.data.profile, authority)
    assert.equal(instance.data.canBookViewing, true)
    assert.equal(instance.data.leadPopupVisible, true)
    assert.equal(instance.data.leadForm.phone, '166****7751')
    assert.equal(modals.length, 0)
    assert.equal(toasts.at(-1).title, '手机号已验证')
  }
})

test('investment phone consultation shows the default formatted number before dialing', () => {
  const { actionSheets, definition, instance, phoneCalls, toasts } = createPhoneConsultationHarness()

  definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 1)
  assert.deepEqual(Array.from(actionSheets[0].itemList), ['021-65686336'])
  assert.equal(phoneCalls.length, 0, 'the first button tap must not start a phone call')

  actionSheets[0].success({ tapIndex: 0 })

  assert.equal(phoneCalls.length, 1)
  assert.equal(phoneCalls[0].phoneNumber, '02165686336')
  assert.equal(toasts.length, 0)
})

test('investment phone consultation prefers the published showcase CONTACT number', async () => {
  const backendOverview = {
    contact: [{ sectionKey: 'contact', content: '010-8888-6666' }]
  }
  const requestApi = {
    get: async pathName => {
      assert.equal(pathName, '/mobile/investment/showcase')
      return backendOverview
    }
  }
  const { actionSheets, definition, instance, phoneCalls } = createPhoneConsultationHarness({ requestApi })
  instance._loadSequence = 1

  await definition.loadOverview.call(instance, 1)
  definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 1)
  assert.deepEqual(Array.from(actionSheets[0].itemList), ['010-8888-6666'])
  assert.equal(phoneCalls.length, 0)

  actionSheets[0].success({ tapIndex: 0 })

  assert.equal(phoneCalls[0].phoneNumber, '01088886666')
})

test('cancelling the investment phone action sheet does not dial', () => {
  const { actionSheets, definition, instance, phoneCalls, toasts } = createPhoneConsultationHarness()

  definition.callConsultant.call(instance)
  assert.equal(actionSheets.length, 1)
  actionSheets[0].fail?.({ errMsg: 'showActionSheet:fail cancel' })
  actionSheets[0].complete?.({ errMsg: 'showActionSheet:fail cancel' })

  assert.equal(phoneCalls.length, 0)
  assert.equal(toasts.length, 0)
})

test('cancelling the system phone confirmation does not show an error', () => {
  const { actionSheets, definition, instance, phoneCalls, toasts } = createPhoneConsultationHarness()

  definition.callConsultant.call(instance)
  assert.equal(actionSheets.length, 1)
  actionSheets[0].success({ tapIndex: 0 })
  assert.equal(phoneCalls.length, 1)
  phoneCalls[0].fail?.({ errMsg: 'makePhoneCall:fail cancel' })
  phoneCalls[0].complete?.({ errMsg: 'makePhoneCall:fail cancel' })

  assert.equal(toasts.length, 0)
})

test('a real investment dialing failure shows a clear error', () => {
  const { actionSheets, definition, instance, phoneCalls, toasts } = createPhoneConsultationHarness()

  definition.callConsultant.call(instance)
  assert.equal(actionSheets.length, 1)
  actionSheets[0].success({ tapIndex: 0 })
  assert.equal(phoneCalls.length, 1)
  phoneCalls[0].fail?.({ errMsg: 'makePhoneCall:fail unavailable' })

  assert.equal(toasts.length, 1)
  assert.deepEqual(
    { title: toasts[0].title, icon: toasts[0].icon },
    { title: '无法发起拨号', icon: 'none' }
  )
})

test('repeated investment phone taps cannot stack action sheets', () => {
  const { actionSheets, definition, instance, phoneCalls } = createPhoneConsultationHarness()

  definition.callConsultant.call(instance)
  definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 1)
  assert.equal(phoneCalls.length, 0)

  actionSheets[0].fail?.({ errMsg: 'showActionSheet:fail cancel' })
  actionSheets[0].complete?.({ errMsg: 'showActionSheet:fail cancel' })
  definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 2, 'the guard must be released after the sheet closes')
})

test('an invalid published CONTACT number falls back instead of reaching makePhoneCall', async () => {
  const backendOverview = {
    phone: '联系前台转 123',
    contact: [{ sectionKey: 'contact', content: '联系前台转 123' }]
  }
  const requestApi = { get: async () => backendOverview }
  const { actionSheets, definition, instance, phoneCalls } = createPhoneConsultationHarness({ requestApi })
  instance._loadSequence = 1

  await definition.loadOverview.call(instance, 1)
  definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 1)
  assert.deepEqual(Array.from(actionSheets[0].itemList), ['021-65686336'])
  actionSheets[0].success({ tapIndex: 0 })
  assert.equal(phoneCalls[0].phoneNumber, '02165686336')
})

test('investment list and detail share the same two-step phone consultation flow', () => {
  for (const relativePath of ['pages/investment/index.js', 'pages/investment/detail/index.js']) {
    const source = read(relativePath)

    assert.match(source, /showInvestmentPhoneConsult\(this, this\.data\.overview, wx\)/)
    assert.doesNotMatch(source, /后台暂未发布联系电话/)
    assert.doesNotMatch(source, /wx\.makePhoneCall\(\{ phoneNumber \}\)/)
  }
})

test('investment phone consultation waits for an in-flight showcase before choosing the fallback', async () => {
  let resolveOverview
  const requestApi = {
    get: () => new Promise((resolve) => {
      resolveOverview = resolve
    })
  }
  const { actionSheets, definition, instance } = createPhoneConsultationHarness({ requestApi })
  instance._loadSequence = 1

  const overviewPromise = definition.loadOverview.call(instance, 1)
  instance._overviewPromise = overviewPromise
  const consultationPromise = definition.callConsultant.call(instance)

  assert.equal(actionSheets.length, 0, 'the fallback must wait while the showcase request is pending')

  resolveOverview({ phone: '010-8888-6666' })
  await overviewPromise
  await consultationPromise

  assert.equal(actionSheets.length, 1)
  assert.deepEqual(Array.from(actionSheets[0].itemList), ['010-8888-6666'])
  assert.match(read('pages/investment/index.js'), /this\._overviewPromise = overviewPromise/)
})
