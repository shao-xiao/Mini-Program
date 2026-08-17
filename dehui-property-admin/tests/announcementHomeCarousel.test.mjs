import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('announcement form persists carousel preference for the explicit publish transition', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/operation/AnnouncementList.vue'), 'utf8')

  assert.match(source, /首页轮播/)
  assert.match(source, /v-model="form\.showOnHome"/)
  assert.match(source, /showOnHome: form\.showOnHome/)
  assert.match(source, /status: form\.status \|\| 'DRAFT'/)
  assert.match(source, /`\/announcements\/\$\{row\.id\}\/publish`/)
})

test('announcement supports preview, publish and disable without inventing a new API family', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/operation/AnnouncementList.vue'), 'utf8')

  assert.match(source, /openPreview\(row\)/)
  assert.match(source, /`\/announcements\/\$\{row\.id\}\/publish`/)
  assert.match(source, /`\/announcements\/\$\{row\.id\}\/disable`/)
  assert.match(source, /停用后小程序将不再展示/)
})

test('announcement list shows whether a notice appears on mini-program home carousel', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/operation/AnnouncementList.vue'), 'utf8')

  assert.match(source, /label="首页轮播"/)
  assert.match(source, /isHomeCarousel\(row\.showOnHome\)/)
})
