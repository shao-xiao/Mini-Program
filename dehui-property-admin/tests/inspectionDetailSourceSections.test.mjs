import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('inspection detail separates mobile registration from property handling history', () => {
  const pageSource = read('src/views/operation/InspectionList.vue')
  const dialogSource = read('src/components/inspection/InspectionDetailDialog.vue')

  assert.match(pageSource, /InspectionDetailDialog/)
  assert.match(pageSource, /:fields="detailSchemaFieldsWithOptions"/)
  assert.match(pageSource, /:values="detailValues"/)

  assert.match(dialogSource, /小程序巡检信息/)
  assert.match(dialogSource, /物业处理信息/)
  assert.match(dialogSource, /values\.attachments/)
  assert.match(dialogSource, /values\.history/)
  assert.match(dialogSource, /resolutionImages/)
  assert.match(dialogSource, /现场照片/)
  assert.match(dialogSource, /处理后照片/)
})
