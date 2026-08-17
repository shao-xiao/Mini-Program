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
    announcement: {},
    announcementId: null
  },

  onLoad(options) {
    this._active = true
    this.setData({ announcementId: options.id || null })
    this.loadDetail(options.id)
  },

  onUnload() {
    this._active = false
  },

  async loadDetail(id) {
    if (!id) {
      this.setData({ pageState: 'empty', announcement: {} })
      return
    }

    this.setData({ loading: true, pageState: 'loading', errorMessage: '', announcement: {} })
    try {
      const profile = await refreshCurrentProfile()
      if (!this._active) return
      if (!canAccessFeature('announcement', profile)) {
        this.setData({
          pageState: 'unauthorized',
          errorMessage: '当前账号暂未获得公告查看能力。',
          announcement: {}
        })
        return
      }
      const data = await api.get(`/mobile/announcements/${id}`)
      if (!this._active) return
      if (!data || !data.id) {
        this.setData({ pageState: 'empty', announcement: {} })
        return
      }
      this.setData({
        pageState: 'success',
        announcement: {
          ...data,
          typeText: typeTextMap[data.type] || '公告',
          coverImageUrl: normalizeImageUrl(data.coverImageUrl),
          publishTimeText: formatDateTime(data.publishTime)
        }
      })
    } catch (error) {
      if (!this._active) return
      const code = Number(error && (error.code || error.statusCode))
      this.setData({
        pageState: code === 404 ? 'empty' : (code === 401 ? 'login-required' : (code === 403 ? 'unauthorized' : 'error')),
        errorMessage: error && error.message ? error.message : '公告详情加载失败',
        announcement: {}
      })
    } finally {
      if (this._active) {
        this.setData({ loading: false })
      }
    }
  },

  retry() {
    this.loadDetail(this.data.announcementId)
  }
})
