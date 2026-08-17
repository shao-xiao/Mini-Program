import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

test('bill create and edit form reject zero amounts before submission', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tenant/BillList.vue'), 'utf8')
  assert.match(source, /<el-input-number[^>]*v-model="form\.calculatedAmount"[^>]*:min="0\.01"/)
  assert.match(source, /<el-input-number[^>]*v-model="form\.adjustmentAmount"/)
  assert.match(source, /v-if="Number\(form\.adjustmentAmount \|\| 0\) !== 0"/)
  assert.match(source, /if \(!value \|\| value <= 0\)/)
  assert.match(source, /Number\.isFinite\(Number\(form\.calculatedAmount\)\)/)
  assert.match(source, /validateField\('calculatedAmount'\)\.catch\(\(\) => false\)[\s\S]*ElMessage\.warning\(/)
  assert.match(source, /formRef\.value\?\.validate\(\)\.catch\(\(\) => false\)/)
})
