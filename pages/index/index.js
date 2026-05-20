const api = require('../../utils/request')

const modules = [
  { icon: '告', title: '公告通知', desc: '园区公告与运营提醒', permission: 'public', url: '/pages/announcements/index' },
  { icon: '账', title: '账单中心', desc: '租金、能耗、停车账单', permission: 'tenant', url: '/pages/bills/index' },
  { icon: '能', title: '能耗查询', desc: '水电气用量与费用', permission: 'tenant', url: '/pages/bills/index?type=energy' },
  { icon: '会', title: '会议预约', desc: '查询会议室并预约', permission: 'tenant', url: '/pages/meeting/index' },
  { icon: '修', title: '报修服务', desc: '提交报修查看进度', permission: 'tenant', url: '/pages/workorders/index' },
  { icon: '访', title: '访客预约', desc: '登记访客到访状态', permission: 'tenant', url: '/pages/visitors/index' },
  { icon: '招', title: '招商中心', desc: '可租房源预约看房', permission: 'public', url: '/pages/investment/index' },
  { icon: '签', title: '员工签到', desc: '内部员工移动打卡', permission: 'staff', url: '/pages/checkin/index' },
  { icon: '我', title: '我的身份', desc: '登录、绑定账号', permission: 'public', tab: 'mine' }
]

const defaultBanners = [
  {
    id: 'banner-park-welcome',
    title: '德汇创新中心欢迎您',
    subtitle: '查看园区公告、运营提醒和企业服务动态。',
    time: '',
    targetType: 'announcement'
  }
]

function formatDateTime(value) {
  if (!value) return ''
  const normalized = String(value).replace('T', ' ')
  const [datePart, time = ''] = normalized.split(' ')
  const [year, month, day] = datePart.split('-')
  if (!year || !month || !day) {
    return normalized.slice(0, 16)
  }
  return `${year}-${month}-${day} ${time.slice(0, 5)}`
}

function getBannerSummary(item) {
  if (!item) return ''
  const source = item.content || item.summary || item.subtitle || '查看公告详情'
  return String(source).replace(/\s+/g, ' ').slice(0, 58)
}

function buildBanners(announcements) {
  const raw = Array.isArray(announcements)
    ? announcements
    : announcements && announcements.records
      ? announcements.records
      : []
  const list = raw.map((item) => ({
    id: item.id || `${Date.now()}-${Math.random()}`,
    title: item.title || '园区公告',
    subtitle: getBannerSummary(item),
    time: formatDateTime(item.publishTime || item.createdTime || item.time),
    targetType: 'announcement',
    targetId: item.id,
    url: item.id ? `/pages/announcements/detail?id=${item.id}` : '/pages/announcements/index'
  }))
  return list.length ? list : defaultBanners
}

Page({
  data: {
    overview: {
      title: '德汇创新中心',
      subtitle: '面向科技创新、研发办公、企业总部和成长型团队的综合办公空间。'
    },
    banners: defaultBanners,
    modules,
    me: {
      role: 'guest',
      tenantBound: false,
      staffBound: false
    }
  },

  onShow() {
    this.loadHome()
    this.loadMe()
  },

  async loadHome() {
    try {
      const [overviewResult, announcementResult] = await Promise.allSettled([
        api.request({ url: '/mobile/investment/overview', method: 'GET', silent: true }),
        api.request({ url: '/mobile/announcements', method: 'GET', silent: true })
      ])

      const overview = overviewResult.status === 'fulfilled'
        ? overviewResult.value
        : null
      const announcements = announcementResult.status === 'fulfilled'
        ? announcementResult.value
        : null

      this.setData({
        overview: {
          title: (overview && overview.title) ? overview.title : '德汇创新中心',
          subtitle: '面向科技创新、研发办公、企业总部和成长型团队的综合办公空间。'
        },
        banners: buildBanners(announcements)
      })
    } catch (error) {
      this.setData({ banners: defaultBanners })
    }
  },

  async loadMe() {
    try {
      const me = await api.request({ url: '/mobile/me', method: 'GET', silent: true })
      this.setData({
        me: {
          role: me && me.role ? me.role : 'guest',
          tenantBound: !!(me && me.tenantBound),
          staffBound: !!(me && me.staffBound)
        }
      })
    } catch (error) {
      this.setData({ me: { role: 'guest', tenantBound: false, staffBound: false } })
    }
  },

  openModule(event) {
    const index = Number(event.currentTarget.dataset.index)
    const item = this.data.modules[index]
    if (!item) return

    if (item.permission === 'tenant' && !this.data.me.tenantBound) {
      this.promptBindTenant()
      return
    }

    if (item.permission === 'staff' && !this.data.me.staffBound) {
      this.promptBindStaff()
      return
    }

    if (item.tab === 'mine') {
      wx.switchTab({ url: '/pages/mine/index' })
      return
    }

    wx.navigateTo({ url: item.url })
  },

  openBanner(event) {
    const index = Number(event.currentTarget.dataset.index)
    const banner = this.data.banners[index]
    if (!banner) return

    if (banner.targetType === 'investment') {
      wx.navigateTo({ url: '/pages/investment/index' })
      return
    }

    if (banner.targetType === 'announcement' && banner.targetId) {
      wx.navigateTo({ url: `/pages/announcements/detail?id=${banner.targetId}` })
      return
    }

    wx.navigateTo({ url: '/pages/announcements/index' })
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
  },

  promptBindStaff() {
    wx.showModal({
      title: '提示',
      content: '员工签到需要绑定内部员工账号后使用，是否前往绑定？',
      cancelText: '取消',
      confirmText: '前往绑定',
      confirmColor: '#E4312B',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/mine/staff-bind/index' })
        }
      }
    })
  }
})
