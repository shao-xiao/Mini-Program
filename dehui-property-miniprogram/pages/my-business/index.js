const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature } = require('../../utils/capabilities')

const LABELS = {
  effectiveRoomCount: '有效房间',
  effectiveContractCount: '有效合同',
  pendingBillCount: '待缴账单',
  pendingBillAmount: '待缴金额',
  currentMonthEnergy: '本月能耗',
  parkingCount: '有效车位',
  upcomingMeetingCount: '近期会议',
  upcomingVisitorCount: '近期访客',
  openWorkOrderCount: '未完成工单'
}

Page({
  data: { loading: true, pageState: 'loading', errorMessage: '', companyName: '', items: [] },
  onShow() { this.loadSummary() },
  async loadSummary() {
    this.setData({ loading: true, pageState: 'loading', errorMessage: '' })
    try {
      const profile = await refreshCurrentProfile()
      if (!canAccessFeature('myBusiness', profile)) {
        this.setData({ loading: false, pageState: 'unauthorized', errorMessage: '当前身份没有“我的业务”权限。' })
        return
      }
      const data = await api.get('/mobile/my-business/summary')
      const summary = data.summary || data
      const items = Object.keys(LABELS)
        .filter(key => summary[key] !== undefined && summary[key] !== null)
        .map(key => ({
          key,
          label: LABELS[key],
          value: /Amount$/.test(key) ? `¥${Number(summary[key] || 0).toFixed(2)}` : summary[key]
        }))
      this.setData({
        loading: false,
        pageState: items.length ? 'success' : 'empty',
        companyName: summary.companyName || data.companyName || '',
        items
      })
    } catch (error) {
      this.setData({ loading: false, pageState: 'error', errorMessage: error.message || '我的业务加载失败' })
    }
  },
  retryLoad() { this.loadSummary() },
  onPullDownRefresh() { this.loadSummary().finally(() => wx.stopPullDownRefresh()) }
})
