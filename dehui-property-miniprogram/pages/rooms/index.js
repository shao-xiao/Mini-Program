const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature } = require('../../utils/capabilities')

Page({
  data: { loading: true, loadingMore: false, pageState: 'loading', errorMessage: '', records: [], page: 1, pageSize: 20, total: 0, noMore: false },
  onShow() { this.refresh() },
  async load(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    this.setData(append ? { loadingMore: true } : { loading: true, pageState: 'loading', errorMessage: '' })
    try {
      const profile = await refreshCurrentProfile()
      if (!canAccessFeature('rooms', profile)) {
        this.setData({ loading: false, loadingMore: false, pageState: 'unauthorized', errorMessage: '当前身份无企业房间查看权限。' })
        return
      }
      const page = append ? this.data.page + 1 : 1
      const data = await api.get('/mobile/my-business/rooms', { page, pageSize: this.data.pageSize })
      const incoming = data.records || []
      const records = append ? this.data.records.concat(incoming) : incoming
      this.setData({ loading: false, loadingMore: false, pageState: records.length ? 'success' : 'empty', records, page: data.page || page, total: data.total || 0, noMore: records.length >= Number(data.total || 0) })
    } catch (error) {
      this.setData({ loading: false, loadingMore: false, pageState: 'error', errorMessage: error.message || '房间加载失败' })
    }
  },
  refresh() { return this.load(false) },
  retryLoad() { this.refresh() },
  onPullDownRefresh() { this.refresh().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { this.load(true) },
  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=room&id=${event.currentTarget.dataset.id}` })
  }
})
