const api = require('../../utils/request')

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

function formatDate(value) {
  if (!value) return '-'
  const datePart = String(value).split('T')[0]
  const [year, month, day] = datePart.split('-')
  return year && month && day ? `${year}年${month}月${day}日` : value
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
      { label: '待缴', value: 'UNPAID' },
      { label: '已缴', value: 'PAID' },
      { label: '逾期', value: 'OVERDUE' }
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
        displayTitle: item.title || item.billTypeText || '账单',
        amountText: formatMoney(item.amount),
        paidAmountText: formatMoney(item.paidAmount),
        unpaidAmountText: formatMoney(item.unpaidAmount),
        periodText: `${formatDate(item.periodStart)} 至 ${formatDate(item.periodEnd)}`,
        dueDateText: formatDate(item.dueDate),
        statusDisplay: item.overdue && item.status !== 'PAID' ? '已逾期' : (item.statusText || item.status || '未知'),
        statusClass: item.status === 'PAID'
          ? 'paid'
          : (item.status === 'CANCELLED' ? 'cancelled' : (item.overdue ? 'overdue' : 'unpaid'))
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

  showPaymentGuide(event) {
    const { number, title, amount } = event.currentTarget.dataset
    wx.showModal({
      title: '线下缴费说明',
      content: `${title || '账单'}\n编号：${number || '-'}\n待缴金额：¥ ${amount || '0.00'}\n\n当前暂未接入微信支付，请按物业通知线下缴费，后台确认收款后此处会更新为已缴。`,
      showCancel: false,
      confirmText: '知道了'
    })
  },

  goProfile() {
    wx.navigateTo({
      url: '/pages/me/index'
    })
  }
})
