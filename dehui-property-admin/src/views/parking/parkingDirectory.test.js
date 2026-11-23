import test from 'node:test'
import assert from 'node:assert/strict'
import { buildParkingDirectory, parkingGroup } from './parkingDirectory.js'

test('requires a current binding for rented spaces and keeps contradictory statuses visible', () => {
  assert.equal(parkingGroup({ status: 'OCCUPIED', assignmentId: 14 }), 'rented')
  assert.equal(parkingGroup({ status: 'OCCUPIED' }), 'other')
  assert.equal(parkingGroup({ status: 'AVAILABLE', assignmentId: 15 }), 'other')
  assert.equal(parkingGroup({ status: 'AVAILABLE' }), 'free')
  assert.equal(parkingGroup({ status: 'DISABLED' }), 'disabled')
})

test('keeps all four zones, legacy area records and numeric order within a group', () => {
  const rows = ['12', '3', '21'].map((spaceNo, i) => ({
    id: i + 1, area: ['Ａ区', 'a', null][i], spaceNo, status: 'AVAILABLE'
  }))
  const zones = buildParkingDirectory(rows)
  assert.deepEqual(zones.map(zone => zone.key), ['A', 'B', 'C', 'D', '未登记区域'])
  assert.deepEqual(zones[0].groups.free.map(space => space.spaceNo), ['3', '12'])
  assert.deepEqual(buildParkingDirectory(rows, true)[0].groups.free.map(space => space.spaceNo), ['12', '3'])
  assert.equal(zones.reduce((n, zone) => n + zone.total, 0), 3)
})
