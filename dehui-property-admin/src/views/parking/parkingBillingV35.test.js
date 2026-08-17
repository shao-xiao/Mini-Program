import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

test('parking assignment UI persists and edits a real monthly fee without a guessed default', () => {
  const source = fs.readFileSync(
    path.join(process.cwd(), 'src/views/parking/ParkingSpaceList.vue'),
    'utf8'
  )
  const apiSource = fs.readFileSync(
    path.join(process.cwd(), 'src/api/parking.js'),
    'utf8'
  )

  assert.match(source, /monthlyFee: null/)
  assert.doesNotMatch(source, /monthlyFee: 300/)
  assert.match(source, /updateParkingAssignmentMonthlyFee/)
  assert.match(source, /currentSpace\.value\.assignmentId/)
  assert.match(source, /command="monthly-fee"/)
  assert.match(source, /hasPermission\('parking:update'\)/)
  assert.match(apiSource, /\/parking\/assignments\/\$\{id\}\/monthly-fee/)
})

test('parking bill UI sends an explicit billing month and keeps due date separate', () => {
  const source = fs.readFileSync(
    path.join(process.cwd(), 'src/views/parking/ParkingBillList.vue'),
    'utf8'
  )

  assert.match(source, /v-model="createForm\.billingMonth"/)
  assert.match(source, /value-format="YYYY-MM"/)
  assert.match(source, /v-model="createForm\.dueDate"/)
  assert.match(source, /value-format="YYYY-MM-DD"/)
  assert.match(source, /createParkingBill\(\{ \.\.\.createForm \}\)/)
  assert.match(source, /syncParkingBills\(\{ month: generationMonth\.value \}\)/)
})
