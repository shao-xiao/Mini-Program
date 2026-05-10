const api = require('../../utils/request')

const typeTextMap = {
  NOTICE: '通知',
  MAINTENANCE: '维修',
  PAYMENT: '缴费',
  EVENT: '活动'
}

function formatDateTime(value) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  const [date, time = ''] = normalized.split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

Page({
  data: {
    loading: false,
    announcements: []
  },

  onShow() {
    this.loadAnnouncements()
  },

  async loadAnnouncements() {
    this.setData({ loading: true })
    try {
      const data = await api.get('/mobile/announcements')
      const announcements = (data || []).map(item => ({
        ...item,
        typeText: typeTextMap[item.type] || '公告',
        summary: item.content ? item.content.slice(0, 56) : '点击查看公告详情',
        publishTimeText: formatDateTime(item.publishTime)
      }))
      this.setData({ announcements })
    } finally {
      this.setData({ loading: false })
    }
  },

  openDetail(event) {
    const id = event.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/announcements/detail?id=${id}`
    })
  }
})
