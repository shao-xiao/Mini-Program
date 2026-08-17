import assert from 'node:assert/strict'
import test from 'node:test'
import { createDebouncedTask, createLatestRequestGuard } from './requestLifecycle.js'

test('a newer request invalidates older responses and dispose blocks later writes', () => {
  const guard = createLatestRequestGuard()
  const first = guard.start()
  const second = guard.start()

  assert.equal(guard.isCurrent(first), false)
  assert.equal(guard.isCurrent(second), true)
  guard.dispose()
  assert.equal(guard.isCurrent(second), false)
})

test('debounced task executes only the latest scheduled query', async () => {
  const values = []
  const task = createDebouncedTask(value => values.push(value), 20)

  task.schedule('old')
  task.schedule('new')
  await new Promise(resolve => setTimeout(resolve, 35))

  assert.deepEqual(values, ['new'])
})

test('cancelled debounced task does not execute after page disposal', async () => {
  const values = []
  const task = createDebouncedTask(value => values.push(value), 20)
  task.schedule('ignored')
  task.cancel()
  await new Promise(resolve => setTimeout(resolve, 35))

  assert.deepEqual(values, [])
})
