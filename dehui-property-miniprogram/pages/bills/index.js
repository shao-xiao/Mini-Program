const api = require('../../utils/request')

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

function formatDate(value) {
  if (!value) return '-'
  const [year, month, day] = value.split('-')
  return `${year}年${month}月${day}日`
}

function emptySummary() {
  return {
    totalAmount: '0.00',
    unpaidAmount: '0.00',
    overdueAmount: '0.00',
    paidAmount: '0.00',
    totalCount: 0,
    unpaidCount: 0,
    overdueCount: 0,
    paidCount: 0
  }
}

Page({
  data: {
    loading: false,
    errorMessage: '',
    activeStatus: '',
    filters: [
      { label: '全部', value: '' },
      { label: '待支付', value: 'UNPAID' },
      { label: '已支付', value: 'PAID' }
    ],
    tenantName: '',
    summary: emptySummary(),
    bills: []
  },

  onShow() {
    this.loadBills()
  },

  async loadBills() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const query = this.data.activeStatus ? { status: this.data.activeStatus } : {}
      const data = await api.get('/mobile/bills', query)
      const summary = data.summary || {}
      const bills = (data.bills || []).map(item => ({
        ...item,
        amountText: formatMoney(item.amount),
        paidAmountText: formatMoney(item.paidAmount),
        unpaidAmountText: formatMoney(item.unpaidAmount),
        periodText: `${formatDate(item.periodStart)} 至 ${formatDate(item.periodEnd)}`,
        dueDateText: formatDate(item.dueDate),
        statusClass: item.status === 'PAID' ? 'paid' : (item.overdue ? 'overdue' : 'unpaid')
      }))

      this.setData({
        tenantName: data.profile && data.profile.boundTenantName ? data.profile.boundTenantName : '',
        summary: {
          totalAmount: formatMoney(summary.totalAmount),
          unpaidAmount: formatMoney(summary.unpaidAmount),
          overdueAmount: formatMoney(summary.overdueAmount),
          paidAmount: formatMoney(summary.paidAmount),
          totalCount: summary.totalCount || 0,
          unpaidCount: summary.unpaidCount || 0,
          overdueCount: summary.overdueCount || 0,
          paidCount: summary.paidCount || 0
        },
        bills
      })
    } catch (error) {
      const message = error && error.message ? error.message : '账单加载失败，请重新登录或绑定租户身份'
      this.setData({
        tenantName: '',
        summary: emptySummary(),
        bills: [],
        errorMessage: message
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  changeStatus(event) {
    const status = event.currentTarget.dataset.value
    this.setData({ activeStatus: status }, () => this.loadBills())
  },

  handlePay(event) {
    const id = event.currentTarget.dataset.id
    wx.showModal({
      title: '支付功能待接入',
      content: `账单 ${id} 已可查询，真实微信支付会在后续阶段接入。`,
      showCancel: false
    })
  },

  goProfile() {
    wx.navigateTo({
      url: '/pages/me/index'
    })
  }
})
