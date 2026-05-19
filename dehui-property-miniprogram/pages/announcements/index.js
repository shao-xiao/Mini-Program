const api = require('../../utils/request')
const { announcementTypeText, formatDateTime } = require('../../utils/format')

Page({
  data: {
    loading: false,
    errorMessage: '',
    announcements: []
  },

  onShow() {
    this.loadAnnouncements()
  },

  async loadAnnouncements() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get('/mobile/announcements')
      const announcements = (data || []).map(item => ({
        ...item,
        typeText: announcementTypeText(item.type),
        summary: item.content ? item.content.slice(0, 56) : '点击查看公告详情',
        publishTimeText: formatDateTime(item.publishTime)
      }))
      this.setData({ announcements })
    } catch (error) {
      this.setData({
        announcements: [],
        errorMessage: error && error.message ? error.message : '公告加载失败，请稍后重试'
      })
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
