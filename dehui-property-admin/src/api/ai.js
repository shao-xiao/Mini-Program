import request from '../utils/request'

export function getDailyReport(params = {}) {
  return request.get('/ai/daily-report', { params })
}

export function refreshDailyReport(params = {}) {
  return request.post('/ai/daily-report/refresh', null, { params })
}

export function getDailyReportHistory(params = {}) {
  return request.get('/ai/daily-report/history', { params })
}

export function getDailyReportDetail(id) {
  return request.get(`/ai/daily-report/${id}`)
}
