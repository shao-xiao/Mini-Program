import test from 'node:test'
import assert from 'node:assert/strict'
import { inspectionEditValues } from './inspectionEditValues.js'
import { splitModuleValues } from './moduleFieldSchema.js'

test('editing remarks retains original photo file IDs and readonly system metadata in schema form', () => {
  const detail = { id: 127, createdAt: '2026-10-14 10:19:55', remark: '已关闭', attachments: [{ id: 84, fileId: 116, url: '/api/files/116', sortOrder: 1 }], customFields: { feedback: '原反馈' } }
  const values = inspectionEditValues(detail)
  values.remark = '已关闭，已解决'
  const fields = ['id','createdAt','images','remark'].map(key => ({ fieldKey: key, apiField: key, source: 'SYSTEM' }))
  fields.push({ fieldKey:'feedback',source:'CUSTOM' })
  const payload = splitModuleValues(fields, values)
  assert.equal(payload.images[0].fileId,116)
  assert.equal(payload.id,127)
  assert.equal(payload.createdAt,detail.createdAt)
  assert.equal(payload.remark,'已关闭，已解决')
  assert.equal(payload.customFields.feedback,'原反馈')
  assert.equal(detail.remark,'已关闭')
})
test('explicit empty photo array is respected and a new form never inherits previous record values', () => {
  assert.deepEqual(inspectionEditValues({ images: [], attachments: [{ fileId:116 }] }).images, [])
  const fresh = inspectionEditValues()
  assert.deepEqual(fresh.images,[])
  assert.deepEqual(fresh.customFields,{})
  assert.equal(fresh.id,undefined)
  assert.equal(fresh.createdAt,undefined)
})
