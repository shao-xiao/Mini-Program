import test from 'node:test'
import assert from 'node:assert/strict'

import { getPathPermissionCodes, getPathViewPermissionCodes } from './permissionRules.js'

test('building, floor and room permissions use independent operation codes', () => {
  assert.deepEqual(getPathPermissionCodes('/buildings'), [
    'building:*',
    'building:view',
    'building:create',
    'building:update',
    'building:delete'
  ])
  assert.deepEqual(getPathPermissionCodes('/floors'), [
    'floor:*',
    'floor:view',
    'floor:create',
    'floor:update',
    'floor:delete'
  ])
  assert.deepEqual(getPathPermissionCodes('/rooms'), [
    'room:*',
    'room:view',
    'room:create',
    'room:update',
    'room:delete',
    'room:lease',
    'room:checkout'
  ])
})

test('building, floor and room menu visibility uses each module view permission', () => {
  assert.deepEqual(getPathViewPermissionCodes('/buildings'), ['building:*', 'building:view'])
  assert.deepEqual(getPathViewPermissionCodes('/floors'), ['floor:*', 'floor:view'])
  assert.deepEqual(getPathViewPermissionCodes('/rooms'), ['room:*', 'room:view'])
})

test('unified asset pages preserve the union of existing granular view permissions', () => {
  assert.deepEqual(getPathViewPermissionCodes('/assets/spaces'), [
    'building:view',
    'floor:view',
    'room:view'
  ])
  assert.deepEqual(getPathViewPermissionCodes('/assets/equipment'), ['asset:view'])
  assert.deepEqual(getPathViewPermissionCodes('/assets/energy'), ['energy:meter:view', 'energy:view'])
  assert.deepEqual(getPathViewPermissionCodes('/assets/parking'), ['parking:view'])
  assert.deepEqual(getPathViewPermissionCodes('/assets/meetings'), [
    'meeting-room:view',
    'meeting-booking:view'
  ])
})

test('investment feature pages use independent operation codes', () => {
  assert.deepEqual(getPathViewPermissionCodes('/investment/manage'), [
    'investment-content:view',
    'investment-listing:view'
  ])
  assert.deepEqual(getPathPermissionCodes('/investment/contents'), [
    'investment-content:*',
    'investment-content:view',
    'investment-content:create',
    'investment-content:update',
    'investment-content:delete',
    'investment-content:publish'
  ])
  assert.deepEqual(getPathPermissionCodes('/investment/listings'), [
    'investment-listing:*',
    'investment-listing:view',
    'investment-listing:create',
    'investment-listing:update',
    'investment-listing:delete',
    'investment-listing:publish',
    'investment-listing:image'
  ])
  assert.deepEqual(getPathPermissionCodes('/investment/leads'), [
    'investment-lead:view',
    'investment-lead:follow',
    'investment-lead:convert',
    'investment-lead:delete'
  ])
  assert.deepEqual(getPathPermissionCodes('/investment/announcements'), [
    'announcement:view',
    'announcement:create',
    'announcement:update',
    'announcement:delete'
  ])
})

test('unified tenant workspace preserves tenant, contract and occupancy view boundaries', () => {
  assert.deepEqual(getPathViewPermissionCodes('/tenant/workspace'), [
    'tenant:view',
    'contract:view',
    'lease:view'
  ])
  const codes = getPathPermissionCodes('/tenant/workspace')
  assert.equal(codes.includes('tenant:update'), true)
  assert.equal(codes.includes('contract:approve'), true)
  assert.equal(codes.includes('lease:checkout'), true)
  assert.equal(codes.includes('bill:add'), false)
})

test('meeting room and booking pages use independent operation codes', () => {
  assert.deepEqual(getPathPermissionCodes('/meetings/rooms'), [
    'meeting-room:*',
    'meeting-room:view',
    'meeting-room:create',
    'meeting-room:update',
    'meeting-room:delete'
  ])
  assert.deepEqual(getPathPermissionCodes('/meetings/bookings'), [
    'meeting-booking:*',
    'meeting-booking:view',
    'meeting-booking:create',
    'meeting-booking:confirm',
    'meeting-booking:cancel',
    'meeting-booking:complete'
  ])
})

test('investment and meeting menu visibility uses each feature view permission', () => {
  assert.deepEqual(getPathViewPermissionCodes('/investment/contents'), ['investment-content:*', 'investment-content:view'])
  assert.deepEqual(getPathViewPermissionCodes('/investment/listings'), ['investment-listing:*', 'investment-listing:view'])
  assert.deepEqual(getPathViewPermissionCodes('/investment/leads'), ['investment-lead:view'])
  assert.deepEqual(getPathViewPermissionCodes('/meetings/rooms'), ['meeting-room:*', 'meeting-room:view'])
  assert.deepEqual(getPathViewPermissionCodes('/meetings/bookings'), ['meeting-booking:*', 'meeting-booking:view'])
})
