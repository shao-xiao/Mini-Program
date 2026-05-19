const api = require('../../utils/request')
const { formatDateTime } = require('../../utils/format')

function emptyMetric(value = '暂无数据') {
  return {
    value,
    desc: '现有移动端接口暂不可用'
  }
}

Page({
  data: {
    loading: false,
    publicError: '',
    overview: {},
    latestAnnouncement: null,
    metrics: {
      announcements: emptyMetric('0'),
      rooms: emptyMetric('0'),
      bills: emptyMetric(),
      meetings: emptyMetric(),
      workorders: emptyMetric(),
      visitors: emptyMetric(),
      checkins: emptyMetric()
    }
  },

  onShow() {
    this.loadDashboard()
  },

  async loadDashboard() {
    this.setData({ loading: true, publicError: '' })

    const [announcementsResult, overviewResult, roomsResult] = await Promise.allSettled([
      api.get('/mobile/announcements'),
      api.get('/mobile/investment/overview'),
      api.get('/mobile/investment/rooms')
    ])

    const announcements = announcementsResult.status === 'fulfilled' ? announcementsResult.value || [] : []
    const rooms = roomsResult.status === 'fulfilled' ? roomsResult.value || [] : []
    const overview = overviewResult.status === 'fulfilled' ? overviewResult.value || {} : {}

    const metrics = {
      announcements: {
        value: announcements.length,
        desc: '来源：/mobile/announcements'
      },
      rooms: {
        value: rooms.length,
        desc: '来源：/mobile/investment/rooms'
      },
      bills: emptyMetric(),
      meetings: emptyMetric(),
      workorders: emptyMetric(),
      visitors: emptyMetric(),
      checkins: emptyMetric()
    }

    const latest = announcements[0]
      ? {
          ...announcements[0],
          publishTimeText: formatDateTime(announcements[0].publishTime)
        }
      : null

    const token = wx.getStorageSync('token')
    if (token) {
      // 首页仅使用已有移动端接口做前端临时聚合；无权限或未绑定身份时静默降级。
      const [billsResult, meetingsResult, workordersResult, visitorsResult, checkinsResult] = await Promise.allSettled([
        api.request({ url: '/mobile/bills', method: 'GET', silent: true }),
        api.request({ url: '/mobile/meetings', method: 'GET', silent: true }),
        api.request({ url: '/mobile/workorders', method: 'GET', silent: true }),
        api.request({ url: '/mobile/visitors', method: 'GET', silent: true }),
        api.request({ url: '/mobile/checkins', method: 'GET', silent: true })
      ])

      if (billsResult.status === 'fulfilled') {
        const summary = billsResult.value.summary || {}
        metrics.bills = {
          value: summary.totalCount || 0,
          desc: '来源：/mobile/bills，按当前绑定租户聚合'
        }
      }
      if (meetingsResult.status === 'fulfilled') {
        const bookings = meetingsResult.value.bookings || []
        metrics.meetings = {
          value: bookings.length,
          desc: '来源：/mobile/meetings，当前身份预约数'
        }
      }
      if (workordersResult.status === 'fulfilled') {
        const workOrders = workordersResult.value.workOrders || []
        metrics.workorders = {
          value: workOrders.length,
          desc: '来源：/mobile/workorders，当前用户工单数'
        }
      }
      if (visitorsResult.status === 'fulfilled') {
        const visitors = visitorsResult.value.visitors || []
        metrics.visitors = {
          value: visitors.length,
          desc: '来源：/mobile/visitors，当前用户访客预约'
        }
      }
      if (checkinsResult.status === 'fulfilled') {
        const checkins = checkinsResult.value.checkins || []
        metrics.checkins = {
          value: checkins.length,
          desc: '来源：/mobile/checkins，内部员工签到'
        }
      }
    }

    this.setData({
      overview,
      latestAnnouncement: latest,
      metrics,
      publicError: announcementsResult.status === 'rejected' && roomsResult.status === 'rejected'
        ? '基础运营数据暂时无法加载，请检查接口地址或后端服务。'
        : '',
      loading: false
    })
  },

  goPage(event) {
    const url = event.currentTarget.dataset.url
    if (!url) return
    wx.navigateTo({ url })
  }
})
