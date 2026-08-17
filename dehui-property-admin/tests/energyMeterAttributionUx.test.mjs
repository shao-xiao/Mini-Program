import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  currentOpenAttribution,
  sameAttributionTarget,
  validateAttributionDraft
} from '../src/utils/energyMeterAttribution.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('energy meter UI distinguishes commissioning from attribution dates', () => {
  const page = fs.readFileSync(path.join(root, 'src/views/energy/EnergyMeterList.vue'), 'utf8')

  assert.match(page, /label="表具投运日期"/)
  assert.match(page, /计量归属历史/)
  assert.match(page, /当前计量归属/)
  assert.match(page, /计量归属开始日期/)
  assert.match(page, /变更计量归属/)
  assert.doesNotMatch(page, />保存新绑定</)
})

test('currentOpenAttribution returns the sole open active attribution', () => {
  const current = currentOpenAttribution([
    {
      id: 1,
      subjectType: 'TENANT',
      tenantId: 17,
      effectiveFrom: '2026-06-01',
      effectiveTo: '2026-06-30',
      status: 'ENDED'
    },
    {
      id: 2,
      subjectType: 'PROPERTY',
      effectiveFrom: '2026-07-01',
      effectiveTo: null,
      status: 'ACTIVE'
    }
  ])

  assert.equal(current.id, 2)
})

test('sameAttributionTarget compares the real subject relation instead of labels', () => {
  assert.equal(sameAttributionTarget(
    { subjectType: 'TENANT', tenantId: '17', roomId: '25' },
    { subjectType: 'TENANT', tenantId: 17, roomId: 25 }
  ), true)
  assert.equal(sameAttributionTarget(
    { subjectType: 'TENANT', tenantId: 17, roomId: 25 },
    { subjectType: 'TENANT', tenantId: 18, roomId: 25 }
  ), false)
})

test('validateAttributionDraft explains commissioning, chronology and duplicate-target errors', () => {
  const current = {
    subjectType: 'TENANT',
    tenantId: 17,
    roomId: 25,
    effectiveFrom: '2026-06-01',
    status: 'ACTIVE'
  }

  assert.equal(validateAttributionDraft({
    current,
    commissioningDate: '2026-06-01',
    draft: { subjectType: 'PROPERTY', effectiveFrom: '2026-05-31' }
  }), '计量归属开始日期不能早于表具投运日期 2026-06-01')

  assert.equal(validateAttributionDraft({
    current,
    commissioningDate: '2026-06-01',
    draft: { subjectType: 'PROPERTY', effectiveFrom: '2026-06-01' }
  }), '新的计量归属开始日期必须晚于当前归属开始日期 2026-06-01')

  assert.equal(validateAttributionDraft({
    current,
    commissioningDate: '2026-06-01',
    draft: {
      subjectType: 'TENANT',
      tenantId: 17,
      roomId: 25,
      effectiveFrom: '2026-07-01'
    }
  }), '当前计量归属未发生变化，无需重复变更')
})
