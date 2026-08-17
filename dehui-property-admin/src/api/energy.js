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

export function confirmEnergyChargeBasis(id) {
  return request.post(`/energy/readings/${id}/confirm-charge-basis`)
}

export function generateEnergyBill(id) {
  return confirmEnergyChargeBasis(id)
}

export function markEnergyPosted(id) {
  return request.post(`/energy/readings/${id}/mark-posted`)
}

export function withdrawEnergyBill(id, data) {
  return request.post(`/energy/readings/${id}/withdraw-bill`, data || {})
}

export function generateMonthlyEnergyReadings(data) {
  return request.post('/energy/readings/monthly-tasks', data)
}

export function getEnergyPriceSettings(params) {
  return request.get('/energy/price-settings', { params })
}

export function saveEnergyPriceSettings(data) {
  return request.put('/energy/price-settings', data)
}

export function importEnergyReadingValues(data) {
  return request.post('/energy/readings/import', data)
}

export function batchConfirmEnergyChargeBases(data) {
  return request.post('/energy/readings/batch-confirm-charge-bases', data)
}

export function batchGenerateEnergyBills(data) {
  return batchConfirmEnergyChargeBases(data)
}

export function downloadEnergyReadingTemplate(params) {
  return request.get('/energy/readings/template', {
    params,
    responseType: 'blob'
  })
}

export function updateAnomalyStatus(id, abnormalStatus) {
  return request.patch(`/energy/readings/${id}/anomaly-status`, { abnormalStatus })
}

export function getEnergyStats(params, requestConfig = {}) {
  return request.get('/energy/stats', { ...requestConfig, params })
}

export function getEnergyMeters(params, requestConfig = {}) {
  return request.get('/energy/meters', { ...requestConfig, params })
}

export function createEnergyMeter(data) {
  return request.post('/energy/meters', data)
}

export function updateEnergyMeter(id, data) {
  return request.put(`/energy/meters/${id}`, data)
}

export function deleteEnergyMeter(id) {
  return request.delete(`/energy/meters/${id}`)
}

export function getLastReading(meterId, params = {}) {
  return request.get(`/energy/meters/${meterId}/last-reading`, { params })
}

export function listEnergyMeterBindings(meterId) {
  return request.get(`/energy/meters/${meterId}/bindings`)
}

export function createEnergyMeterBinding(meterId, data) {
  return request.post(`/energy/meters/${meterId}/bindings`, data)
}

export function replaceEnergyMeterBinding(meterId, data) {
  return request.post(`/energy/meters/${meterId}/bindings/replace`, data)
}
