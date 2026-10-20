import test from 'node:test'
import assert from 'node:assert/strict'
import { financeRatio, financeMonthlyWindow, financeTenantRows, financeChargeRows } from './financeDashboard.js'

test('collection and overdue percentages preserve missing denominators and genuine zero', () => {
  assert.equal(financeRatio(0,100),0)
  assert.equal(financeRatio(0,0),null)
  assert.equal(financeRatio(3458645.61,3631299.81).toFixed(1),'95.2')
})
test('monthly trend uses chronological calendar months and leaves missing months empty', () => {
  const rows=[{dimension:'2026-10',receivableAmount:100},{dimension:'2026-06',receivableAmount:0},{dimension:'2026-07',receivableAmount:200},{dimension:null,receivableAmount:999}]
  const result=financeMonthlyWindow(rows)
  assert.deepEqual(result.map(row => row.dimension),['2026-06','2026-07','2026-08','2026-09','2026-10'])
  assert.equal(result[2].record,null)
  assert.equal(result[0].record.receivableAmount,0)
  assert.equal(rows[0].dimension,'2026-10')
})
test('monthly window is bounded to twelve calendar months across years', () => {
  const result=financeMonthlyWindow([{dimension:'2024-01'},{dimension:'2026-02'}])
  assert.equal(result.length,12)
  assert.equal(result[0].dimension,'2025-03')
  assert.equal(result.at(-1).dimension,'2026-02')
})
test('tenant follow-up sorts actual arrears and distinguishes unpaid share from overdue risk', () => {
  const rows=[{tenantId:1,receivableAmount:100,receivedAmount:100,unpaidAmount:0},{tenantId:2,receivableAmount:400,receivedAmount:100,unpaidAmount:300},{tenantId:null,unpaidAmount:100}]
  const result=financeTenantRows(rows,400)
  assert.equal(result[0].tenantId,2)
  assert.equal(result[0].collectionRate,25)
  assert.equal(result[0].unpaidShare,75)
  assert.equal(result[1].tenantName,'未关联租户')
  assert.equal(rows[0].tenantId,1)
})
test('charge breakdown retains credits separately and never draws negative percentage bars', () => {
  const result=financeChargeRows([{chargeItemType:'RENT',finalAmount:300},{chargeItemType:'PROPERTY',finalAmount:100},{chargeItemType:'ADJUSTMENT',finalAmount:-50},{chargeItemType:'GAS',finalAmount:0}])
  assert.equal(result.length,3)
  assert.equal(result[0].share,75)
  assert.equal(result[2].amount,-50)
  assert.equal(result[2].share,null)
})
