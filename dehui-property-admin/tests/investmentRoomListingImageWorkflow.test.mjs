import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('investment listing create dialog exposes image upload workflow before save', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/investment/InvestmentRoomListingList.vue'), 'utf8')

  assert.match(source, /pendingImages/)
  assert.match(source, /selectPendingImages/)
  assert.match(source, /uploadPendingImages/)
  assert.match(source, /<input[^>]+ref="pendingImageInputRef"/s)
  assert.match(source, /uploadPendingImages\(savedId\)/)
  assert.match(source, /单张不超过20MB/)
  assert.match(source, /MAX_LISTING_IMAGE_SIZE = 20 \* 1024 \* 1024/)
  assert.match(source, /:before-upload="validateListingImage"/)
  assert.match(source, /file\.size <= MAX_LISTING_IMAGE_SIZE/)
})

test('investment listing publication only uses the validated publish endpoint', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/investment/InvestmentRoomListingList.vue'), 'utf8')

  assert.match(source, /已发布（请使用列表上架操作）/)
  assert.match(source, /value="PUBLISHED" disabled/)
  assert.match(source, /request\.post\(`\/investment\/listings\/\$\{row\.id\}\/publish`\)/)
  assert.match(source, /requestErrorMessage\(error, '房源上架失败'\)/)
})
