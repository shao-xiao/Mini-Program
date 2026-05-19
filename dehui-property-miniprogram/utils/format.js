function pad(value) {
  return String(value).padStart(2, '0')
}

function toDatePart(value) {
  if (!value) return null
  const parts = String(value).split('T')[0].split('-')
  return parts.length === 3 ? parts : null
}

function formatMoney(value) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

function formatDate(value) {
  if (!value) return '-'
  const part = toDatePart(value)
  if (!part) return String(value)
  return `${part[0]}-${part[1]}-${part[2]}`
}

function formatDateTime(value) {
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  const [date, time = ''] = normalized.split(' ')
  const part = toDatePart(`${date}T00:00:00`)
  if (!part) return String(value)
  return `${part[0]}-${part[1]}-${part[2]} ${time.slice(0, 5)}`
}

function formatDateRange(start, end) {
  return `${formatDate(start)} ~ ${formatDate(end)}`
}

function formatInputDate(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatInputTime(date = new Date()) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function announcementTypeText(type) {
  const map = {
    NOTICE: '通知',
    MAINTENANCE: '维修',
    PAYMENT: '缴费',
    EVENT: '活动'
  }
  return map[type] || '其他'
}

function invoiceStatusText(status) {
  const map = {
    INVOICED: '已开票',
    UNINVOICED: '未开票',
    PENDING: '待处理'
  }
  return map[status] || (status || '未开票')
}

module.exports = {
  formatMoney,
  formatDate,
  formatDateTime,
  formatDateRange,
  formatInputDate,
  formatInputTime,
  announcementTypeText,
  invoiceStatusText
}
