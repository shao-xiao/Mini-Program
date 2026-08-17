import test from 'node:test'
import assert from 'node:assert/strict'
import { findDuplicateEnergyReading } from './energyReadingDraft.js'

const records = [
  { id: 10, meterId: 500, periodMonth: '2026-07', billStatus: 'POSTED' },
  { id: 11, meterId: 500, periodMonth: '2026-06', billStatus: 'NOT_GENERATED' }
]

test('findDuplicateEnergyReading detects an existing meter and billing month', () => {
  assert.equal(
    findDuplicateEnergyReading(records, { meterId: '500', periodMonth: '2026-07' })?.id,
    10
  )
})

test('findDuplicateEnergyReading excludes the record currently being edited', () => {
  assert.equal(
    findDuplicateEnergyReading(records, { id: 10, meterId: 500, periodMonth: '2026-07' }),
    null
  )
})

test('findDuplicateEnergyReading ignores incomplete drafts', () => {
  assert.equal(findDuplicateEnergyReading(records, { meterId: 500, periodMonth: '' }), null)
  assert.equal(findDuplicateEnergyReading(records, { meterId: null, periodMonth: '2026-07' }), null)
})
