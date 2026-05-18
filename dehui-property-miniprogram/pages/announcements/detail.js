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
    announcement: {}
  },

  onLoad(options) {
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    if (!id) return

    this.setData({ loading: true })
    try {
      const data = await api.get(`/mobile/announcements/${id}`)
      this.setData({
        announcement: {
          ...data,
          typeText: typeTextMap[data.type] || '公告',
          publishTimeText: formatDateTime(data.publishTime)
        }
      })
    } finally {
      this.setData({ loading: false })
    }
  }
})
