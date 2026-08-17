import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

test('meeting booking calculation keeps internal employee bookings free without remote fee calculation', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/meeting/MeetingBookingList.vue'), 'utf8')

  assert.match(source, /const applyInternalFree = \(\) => \{[\s\S]*form\.feeType = 'INTERNAL_FREE'[\s\S]*form\.amount = 0[\s\S]*\}/)
  assert.match(source, /const calculate = async \(\) => \{[\s\S]*if \(form\.sourceType === 'INTERNAL'\) \{[\s\S]*applyInternalFree\(\)[\s\S]*return[\s\S]*\}/)
})
