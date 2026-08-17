import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('room dialog exposes building and floor selectors and saves selected floor', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/building/RoomList.vue'), 'utf8')

  assert.match(source, /:disabled="!query\.buildingId"/)
  assert.doesNotMatch(source, /:disabled="!query\.buildingId \|\| !query\.floorId"/)
  assert.match(source, /v-model="form\.buildingId"/)
  assert.match(source, /v-model="form\.floorId"/)
  assert.match(source, /formFloors/)
  assert.match(source, /buildingId:\s*form\.buildingId/)
  assert.match(source, /floorId:\s*form\.floorId/)
  assert.match(source, /query\.floorId = null/)
  assert.doesNotMatch(source, /query\.floorId = matched \? preferredFloorId : floors\.value\[0\]\.id/)
  assert.doesNotMatch(source, /buildingId:\s*query\.buildingId/)
  assert.doesNotMatch(source, /floorId:\s*query\.floorId/)
})
