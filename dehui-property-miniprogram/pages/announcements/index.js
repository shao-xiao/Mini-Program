const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature } = require('../../utils/capabilities')
const { getBaseURL } = require('../../config/env')

const typeTextMap = {
  NOTICE: '通知',
  ACTIVITY: '活动',
  PROPERTY: '物业',
  REMINDER: '提醒'
}

function normalizeImageUrl(value) {
  const url = String(value || '').trim()
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const origin = getBaseURL().replace(/\/api\/?$/, '')
  return url.startsWith('/') ? `${origin}${url}` : `${origin}/${url}`
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
    pageState: 'loading',
    errorMessage: '',
    announcements: []
  },

  onLoad() {
    this._active = true
    this._loadSequence = 0
  },

  onShow() {
    this._active = true
    this.loadAnnouncements()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadAnnouncements() {
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    this.setData({ loading: true, pageState: 'loading', errorMessage: '' })
    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      if (!canAccessFeature('announcement', profile)) {
        this.setData({
          announcements: [],
          pageState: 'unauthorized',
          errorMessage: '当前账号暂未获得公告查看能力。'
        })
        return
      }
      const data = await api.get('/mobile/announcements')
      const announcements = (data || []).map(item => ({
        ...item,
        typeText: typeTextMap[item.type] || '公告',
        coverImageUrl: normalizeImageUrl(item.coverImageUrl),
        summary: item.summary || (item.content ? item.content.slice(0, 56) : '点击查看公告详情'),
        publishTimeText: formatDateTime(item.publishTime)
      }))
      if (!this.isCurrentRequest(requestId)) return
      this.setData({
        announcements,
        pageState: announcements.length ? 'success' : 'empty'
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      const code = Number(error && (error.code || error.statusCode))
      this.setData({
        announcements: [],
        pageState: code === 401 ? 'login-required' : (code === 403 ? 'unauthorized' : 'error'),
        errorMessage: error && error.message ? error.message : '公告加载失败，请稍后重试'
      })
    } finally {
      if (this.isCurrentRequest(requestId)) {
        this.setData({ loading: false })
      }
    }
  },

  retry() {
    this.loadAnnouncements()
  },

  openDetail(event) {
    const id = event.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/announcements/detail?id=${id}`
    })
  }
})
