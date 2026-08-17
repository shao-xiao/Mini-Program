import test from 'node:test'
import assert from 'node:assert/strict'
import { buildEquipmentUpdatePayload, isEquipmentUpdateField } from './equipmentEdit.js'

test('buildEquipmentUpdatePayload reuses the existing complete equipment PUT contract', () => {
  const payload = buildEquipmentUpdatePayload({
    equipmentName: '客梯 1',
    equipmentCode: 'DT-01',
    equipmentType: 'ELEVATOR',
    status: 'NORMAL',
    location: '1F',
    manufacturer: '厂家',
    model: 'M1',
    installDate: '2026-01-02',
    remark: '原备注'
  }, 'location', '2F')

  assert.deepEqual(payload, {
    equipmentName: '客梯 1',
    equipmentCode: 'DT-01',
    equipmentType: 'ELEVATOR',
    status: 'NORMAL',
    location: '2F',
    manufacturer: '厂家',
    model: 'M1',
    installDate: '2026-01-02',
    remark: '原备注'
  })
})

test('equipment inline editing rejects fields outside the existing API contract', () => {
  assert.equal(isEquipmentUpdateField('equipmentName'), true)
  assert.equal(isEquipmentUpdateField('createdAt'), false)
  assert.throws(() => buildEquipmentUpdatePayload({}, 'createdAt', '2026-01-01'))
})
