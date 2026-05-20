import request from '../utils/request'

/**
 * @typedef {Object} ParkingSpace
 * @property {number} id
 * @property {string} spaceNo
 * @property {string} area
 * @property {string} floor
 * @property {string} type
 * @property {string} status
 * @property {string} partyNameSnapshot
 * @property {string} plateNo
 */

/**
 * @typedef {Object} ParkingAssignment
 * @property {number} id
 * @property {number} spaceId
 * @property {string} partyType
 * @property {string} partyNameSnapshot
 * @property {string} plateNo
 * @property {string} billingType
 * @property {number} monthlyFee
 */

/**
 * @typedef {Object} ParkingBill
 * @property {number} id
 * @property {string} billNo
 * @property {string} spaceNoSnapshot
 * @property {string} partyNameSnapshot
 * @property {string} plateNoSnapshot
 * @property {string} status
 * @property {string} syncStatus
 */

export function listParkingSpaces(params = {}) {
  return request.get('/parking/spaces', { params })
}

export function createParkingSpace(data) {
  return request.post('/parking/spaces', data)
}

export function updateParkingSpace(id, data) {
  return request.put(`/parking/spaces/${id}`, data)
}

export function deleteParkingSpace(id) {
  return request.delete(`/parking/spaces/${id}`)
}

export function bindParkingSpace(id, data) {
  return request.post(`/parking/spaces/${id}/bind`, data)
}

export function releaseParkingSpace(id, data = {}) {
  return request.post(`/parking/spaces/${id}/release`, data)
}

export function updateParkingSpaceStatus(id, status) {
  return request.patch(`/parking/spaces/${id}/status`, null, { params: { status } })
}

export function listParkingAssignments(params = {}) {
  return request.get('/parking/assignments', { params })
}

export function listParkingBills(params = {}) {
  return request.get('/parking/bills', { params })
}

export function syncParkingBills(data) {
  return request.post('/parking/bills/sync', data)
}

export function payParkingBill(id) {
  return request.post(`/parking/bills/${id}/pay`)
}

export function voidParkingBill(id) {
  return request.post(`/parking/bills/${id}/void`)
}
