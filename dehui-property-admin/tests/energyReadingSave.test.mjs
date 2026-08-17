import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/energy/EnergyRecordList.vue', import.meta.url), 'utf8')
const requestSource = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')

test('meter reading save does not use an async validation callback that leaks rejected requests', () => {
  assert.doesNotMatch(source, /\.validate\(async\s+valid\s*=>/)
  assert.match(source, /await formRef\.value\.validate\(\)/)
  assert.match(source, /catch\s*\(error\)/)
})

test('meter reading form blocks a duplicate loaded meter-period record before submit', () => {
  assert.match(source, /findDuplicateEnergyReading/)
  assert.match(source, /duplicateReading/)
  assert.match(source, /:disabled="Boolean\(duplicateReading\)"/)
})

test('the global request layer surfaces backend business messages', () => {
  assert.match(requestSource, /requestErrorMessage\(error/)
  assert.doesNotMatch(requestSource, /ElMessage\.error\(error\.message\s*\|\|/)
})
