import assert from 'node:assert/strict'
import test from 'node:test'
import {
  useWorkspaceListRequestState,
  useWorkspaceMetricState
} from './useWorkspaceRequestState.js'

test('a failed new query clears old rows and total while retry can recover', () => {
  let rows = [{ id: 1 }]
  let total = 1
  const state = useWorkspaceListRequestState({
    hasResults: () => rows.length > 0,
    clearResults: () => {
      rows = []
      total = 0
    }
  })

  const first = state.begin({ page: 1, keyword: 'old' })
  rows = [{ id: 1 }]
  total = 1
  state.succeed(first)

  const changed = state.begin({ page: 1, keyword: 'new' })
  assert.deepEqual(rows, [])
  assert.equal(total, 0)
  state.fail(changed, '设备列表加载失败，请稍后重试')
  assert.equal(state.status.value, 'error')
  assert.equal(state.errorText.value, '设备列表加载失败，请稍后重试')
  assert.equal(state.showingStaleData.value, false)

  const retry = state.begin({ page: 1, keyword: 'new' })
  rows = [{ id: 2 }]
  total = 1
  state.succeed(retry)
  assert.equal(state.status.value, 'success')
  assert.equal(state.errorText.value, '')
})

test('a failed refresh of the same query keeps last successful data but labels it stale', () => {
  let rows = []
  let clearCount = 0
  const state = useWorkspaceListRequestState({
    hasResults: () => rows.length > 0,
    clearResults: () => {
      clearCount += 1
      rows = []
    }
  })

  const first = state.begin({ page: 2, keyword: 'same' })
  rows = [{ id: 9 }]
  state.succeed(first)
  const refresh = state.begin({ keyword: 'same', page: 2 })
  state.fail(refresh, '请求失败')

  assert.deepEqual(rows, [{ id: 9 }])
  assert.equal(clearCount, 1)
  assert.equal(state.showingStaleData.value, true)
  assert.equal(state.errorText.value, '刷新失败，当前显示上次成功数据')
})

test('metric revisions clear stale values and ignore older responses', () => {
  const metrics = useWorkspaceMetricState(['total', 'abnormal', 'amount'])
  const firstRevision = metrics.begin()
  metrics.resolve(firstRevision, 'total', 18)
  metrics.resolve(firstRevision, 'abnormal', 3)
  metrics.resolve(firstRevision, 'amount', 120)

  const secondRevision = metrics.begin()
  assert.deepEqual(metrics.values.value, { total: null, abnormal: null, amount: null })
  metrics.resolve(firstRevision, 'amount', 999)
  metrics.resolve(secondRevision, 'total', 20)
  metrics.reject(secondRevision, 'abnormal')
  metrics.resolve(secondRevision, 'amount', 140)

  assert.deepEqual(metrics.values.value, { total: 20, abnormal: null, amount: 140 })
  assert.equal(metrics.states.abnormal.error, true)
  assert.equal(metrics.states.abnormal.loading, false)
  assert.equal(metrics.states.abnormal.revision, secondRevision)
})
