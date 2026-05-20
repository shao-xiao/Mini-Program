import request from '../utils/request'

export function getEnergyReadings(params) {
  return request.get('/energy/readings', { params })
}

export function createEnergyReading(data) {
  return request.post('/energy/readings', data)
}

export function updateEnergyReading(id, data) {
  return request.put(`/energy/readings/${id}`, data)
}

export function deleteEnergyReading(id) {
  return request.delete(`/energy/readings/${id}`)
}

export function generateEnergyBill(id) {
  return request.post(`/energy/readings/${id}/generate-bill`)
}

export function markEnergyPosted(id) {
  return request.post(`/energy/readings/${id}/mark-posted`)
}

export function updateAnomalyStatus(id, abnormalStatus) {
  return request.patch(`/energy/readings/${id}/anomaly-status`, { abnormalStatus })
}

export function getEnergyStats(params) {
  return request.get('/energy/stats', { params })
}

export function getEnergyMeters(params) {
  return request.get('/energy/meters', { params })
}

export function getLastReading(meterId) {
  return request.get(`/energy/meters/${meterId}/last-reading`)
}
