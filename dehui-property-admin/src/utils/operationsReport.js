import { loadAllWorkspaceRecords } from './workspaceExport.js'
import { loadDashboardRoomStatistics } from './dashboardRoomStatistics.js'
import { readPage } from './pagination.js'

export function reportDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
export function addReportDays(day, count) {
  const date = new Date(`${day}T12:00:00`)
  date.setDate(date.getDate() + count)
  return reportDate(date)
}

// Each business source retains its own permission and failure state.
export async function loadOperationsSources(get, can, today) {
  const one = (url, params = {}) => get(url, { params, silent: true })
  const all = url => loadAllWorkspaceRecords(page => one(url, page))
  const specs = [
    ['rooms', '房源', 'room:view', () => loadDashboardRoomStatistics(get)],
    ['finance', '账单', 'bill:view', () => one('/bills/summary')],
    ['overdue', '逾期明细', 'bill:view', async () => readPage(await one('/bills', { overdue: true, page: 1, pageSize: 5, sortField: 'dueDate', sortDirection: 'ASC' })).records],
    ['payments', '收款流水', 'bill:view', () => all('/bills/payments')],
    ['contracts', '合同', 'contract:view', async () => (await all('/contracts')).map(row => ({ ...row, contractNo: row.contractNo || row.contractNumber }))],
    ['workorders', '工单', 'workorder:view', () => all('/workorders')],
    ['inspections', '巡检', 'inspection:view', async () => {
      const [total, daily, abnormal] = await Promise.all([
        one('/inspections/summary'),
        one('/inspections/summary', { startDate: today, endDate: today }),
        one('/inspections/summary', { status: 'OPEN', result: 'ABNORMAL' })
      ])
      return { total, daily, abnormal }
    }],
    ['equipment', '设备', 'asset:view', async () => {
      const [total, fault] = await Promise.all([one('/equipments', { page: 1, pageSize: 1 }), one('/equipments', { status: 'FAULT', page: 1, pageSize: 1 })])
      return { total: readPage(total).total, fault: readPage(fault).total }
    }],
    ['energy', '能耗', 'energy:view', () => one('/energy/stats')]
  ]
  const results = await Promise.allSettled(specs.map(spec => can(spec[2]) ? spec[3]() : Promise.resolve(null)))
  return Object.fromEntries(specs.map(([key, label, permission], index) => [key, {
    label, status: !can(permission) ? 'denied' : results[index].status === 'fulfilled' ? 'ready' : 'error',
    data: results[index].status === 'fulfilled' ? results[index].value : null
  }]))
}

export function summarizeWorkOrders(rows, today) {
  const pending = rows.filter(row => ['CREATED', 'SUBMITTED', 'ASSIGNED', 'PROCESSING', 'COMPLETED'].includes(row.status))
  const high = pending.filter(row => ['HIGH', 'URGENT'].includes(row.priority))
  return {
    today: rows.filter(row => String(row.createdTime || '').slice(0, 10) === today).length,
    pending: pending.length,
    unassigned: pending.filter(row => ['CREATED', 'SUBMITTED'].includes(row.status)).length,
    processing: pending.filter(row => ['ASSIGNED', 'PROCESSING'].includes(row.status)).length,
    awaitingClose: pending.filter(row => row.status === 'COMPLETED').length,
    high: high.length,
    rows: [...pending].sort((a, b) => Number(['HIGH', 'URGENT'].includes(b.priority)) - Number(['HIGH', 'URGENT'].includes(a.priority)) || String(a.createdTime).localeCompare(String(b.createdTime))).slice(0, 6),
    trend: Array.from({ length: 7 }, (_, i) => {
      const day = addReportDays(today, i - 6)
      return { day, count: rows.filter(row => String(row.createdTime || '').slice(0, 10) === day).length }
    })
  }
}

export function summarizeContracts(rows, today) {
  const active = rows.filter(row => row.status === 'ACTIVE')
  const end = addReportDays(today, 30)
  return { active: active.length, expiring: active.filter(row => row.endDate && String(row.endDate).slice(0, 10) >= today && String(row.endDate).slice(0, 10) <= end).sort((a, b) => String(a.endDate).localeCompare(String(b.endDate))) }
}

export function summarizePayments(rows, today) {
  const paid = rows.filter(row => row.status === 'PAID' && row.paidAt && String(row.paidAt).slice(0, 10) <= today)
  const sum = list => Math.round(list.reduce((value, row) => value + (Number(row.amount) || 0), 0) * 100) / 100
  return { today: sum(paid.filter(row => String(row.paidAt || '').slice(0, 10) === today)), month: sum(paid.filter(row => String(row.paidAt || '').slice(0, 7) === today.slice(0, 7))) }
}
