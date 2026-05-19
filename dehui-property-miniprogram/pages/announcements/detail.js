const api = require('../../utils/request')
const { announcementTypeText, formatDateTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    errorMessage: '',
    announcement: {}
  },

  onLoad(options) {
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    if (!id) {
      this.setData({ errorMessage: '公告 ID 缺失' })
      return
    }

    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get(`/mobile/announcements/${id}`)
      this.setData({
        announcement: {
          ...data,
          typeText: announcementTypeText(data.type),
          publishTimeText: formatDateTime(data.publishTime)
        }
      })
    } catch (error) {
      this.setData({
        announcement: {},
        errorMessage: error && error.message ? error.message : '公告不存在或已下线'
      })
    } finally {
      this.setData({ loading: false })
    }
  }
})
