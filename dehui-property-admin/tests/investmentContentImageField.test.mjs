import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('investment content form does not expose image url field', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/investment/InvestmentContentList.vue'), 'utf8')

  assert.doesNotMatch(source, /<el-form-item label="图片地址">/)
  assert.doesNotMatch(source, /placeholder="可选，后续可接文件上传"/)
})
