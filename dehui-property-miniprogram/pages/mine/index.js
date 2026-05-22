const api = require('../../utils/request')
const { activeEnv, getBaseURL, prodBaseURL } = require('../../config/env')

const identityItems = [
  {
    key: 'tenant',
    icon: '租',
    title: '租户账号绑定',
    subtitle: '绑定后可使用租户功能',
    url: '/pages/mine/tenant-bind/index'
  },
  {
    key: 'staff',
    icon: '员',
    title: '内部员工绑定',
    subtitle: '绑定后可进行员工签到',
    url: '/pages/mine/staff-bind/index'
  }
]

const overviewItems = [
  {
    key: 'announcement',
    icon: '告',
    title: '已发布公告',
    subtitle: '园区公告与运营提醒',
    target: '/pages/announcements/index'
  },
  {
    key: 'room',
    icon: '房',
    title: '可租房源',
    subtitle: '查看可租房源并预约看房',
    target: '/pages/investment/index'
  }
]

const businessItems = [
  { key: 'bill', icon: '账', title: '我的账单', subtitle: '租金、能耗、停车和服务账单', target: '/pages/bills/index' },
  { key: 'meeting', icon: '会', title: '我的会议', subtitle: '查看会议室预约记录', target: '/pages/meeting/index' },
  { key: 'workorder', icon: '修', title: '我的工单', subtitle: '查看报修处理进度', target: '/pages/workorders/index' },
  { key: 'visitor', icon: '访', title: '访客预约', subtitle: '登记访客并查看到访状态', target: '/pages/visitors/index' }
]

const emptyMe = {
  loginStatus: false,
  role: 'guest',
  tenantBound: false,
  staffBound: false,
  tenantInfo: null,
  staffInfo: null
}

const emptySummary = {
  announcementCount: 0,
  availableRoomCount: 0,
  billCount: null,
  meetingCount: null,
  workOrderCount: null,
  visitorCount: null,
  needTenantBind: true
}

function roleText(role) {
  if (role === 'tenant' || role === 'TENANT') return '租户'
  if (role === 'staff' || role === 'INTERNAL' || role === 'STAFF') return '内部员工'
  return '游客'
}

function statusText(bound) {
  return bound ? '已绑定' : '未绑定'
}

function buildIdentityItems(me) {
  const safeMe = me || emptyMe
  return identityItems.map((item) => ({
    ...item,
    valueText: item.key === 'tenant' ? statusText(safeMe.tenantBound) : statusText(safeMe.staffBound),
    valueClass: item.key === 'tenant'
      ? (safeMe.tenantBound ? 'bound' : 'not-bound')
      : (safeMe.staffBound ? 'bound' : 'not-bound')
  }))
}

function buildOverviewItems(summary) {
  const safeSummary = summary || emptySummary
  return [
    {
      ...overviewItems[0],
      valueText: String(safeSummary.announcementCount || 0)
    },
    {
      ...overviewItems[1],
      valueText: String(safeSummary.availableRoomCount || 0)
    }
  ]
}

function buildBusinessItems(summary) {
  const safeSummary = summary || emptySummary
  if (safeSummary.needTenantBind) {
    return businessItems.map((item) => ({ ...item, valueText: '未绑定' }))
  }

  return [
    { ...businessItems[0], valueText: String(safeSummary.billCount || 0) },
    { ...businessItems[1], valueText: String(safeSummary.meetingCount || 0) },
    { ...businessItems[2], valueText: String(safeSummary.workOrderCount || 0) },
    { ...businessItems[3], valueText: String(safeSummary.visitorCount || 0) }
  ]
}

function shouldShowApiSetting() {
  const baseURL = getBaseURL()
  return activeEnv !== 'prod' && baseURL !== prodBaseURL
}

Page({
  data: {
    loading: false,
    me: emptyMe,
    summary: emptySummary,
    identityText: '游客',
    identityCards: buildIdentityItems(emptyMe),
    overviewCards: buildOverviewItems(emptySummary),
    businessCards: buildBusinessItems(emptySummary),
    showApiSettings: shouldShowApiSetting()
  },

  onShow() {
    this.refresh()
  },

  async refresh() {
    this.setData({ loading: true, showApiSettings: shouldShowApiSetting() })
    try {
      const [me, summary] = await Promise.all([
        api.request({ url: '/mobile/me', method: 'GET', silent: true }),
        api.request({ url: '/mobile/mine/summary', method: 'GET', silent: true })
      ])
      const safeMe = me || emptyMe
      const safeSummary = summary || emptySummary
      this.setData({
        me: safeMe,
        summary: safeSummary,
        identityText: roleText(safeMe.role),
        identityCards: buildIdentityItems(safeMe),
        overviewCards: buildOverviewItems(safeSummary),
        businessCards: buildBusinessItems(safeSummary)
      })
    } catch (error) {
      this.setData({
        me: emptyMe,
        summary: emptySummary,
        identityText: roleText('guest'),
        identityCards: buildIdentityItems(emptyMe),
        overviewCards: buildOverviewItems(emptySummary),
        businessCards: buildBusinessItems(emptySummary)
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  openIdentityItem(event) {
    const url = event.currentTarget.dataset.url
    if (!url) return
    wx.navigateTo({ url })
  },

  openOverviewItem(event) {
    const url = event.currentTarget.dataset.url
    if (url) wx.navigateTo({ url })
  },

  openBusinessItem(event) {
    if (this.data.summary.needTenantBind) {
      this.promptBindTenant()
      return
    }
    const url = event.currentTarget.dataset.url
    if (url) wx.navigateTo({ url })
  },

  openApiSetting() {
    wx.navigateTo({ url: '/pages/me/index' })
  },

  promptBindTenant() {
    wx.showModal({
      title: '提示',
      content: '当前功能需要绑定租户账号后使用，是否前往绑定？',
      cancelText: '取消',
      confirmText: '前往绑定',
      confirmColor: '#E4312B',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/mine/tenant-bind/index' })
        }
      }
    })
  }
})
