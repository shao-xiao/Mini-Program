import { computed, reactive, ref } from 'vue'

export function useWorkspaceListRequestState({
  hasResults = () => false,
  clearResults = () => {},
  staleMessage = '刷新失败，当前显示上次成功数据'
} = {}) {
  const status = ref('idle')
  const errorText = ref('')
  const showingStaleData = ref(false)
  const currentRevision = ref(0)
  const lastSuccessQueryKey = ref('')

  function begin(query = {}) {
    const queryKey = stableSerialize(query)
    const revision = currentRevision.value + 1
    const isRefresh = queryKey === lastSuccessQueryKey.value && Boolean(hasResults())

    currentRevision.value = revision
    status.value = 'loading'
    errorText.value = ''
    showingStaleData.value = false
    if (!isRefresh) clearResults()

    return { revision, queryKey, isRefresh }
  }

  function succeed(context) {
    if (!isCurrent(context)) return false
    lastSuccessQueryKey.value = context.queryKey
    status.value = 'success'
    errorText.value = ''
    showingStaleData.value = false
    return true
  }

  function fail(context, message = '数据加载失败，请稍后重试') {
    if (!isCurrent(context)) return false
    const keepStaleData = context.isRefresh && Boolean(hasResults())
    if (!keepStaleData) clearResults()
    status.value = 'error'
    showingStaleData.value = keepStaleData
    errorText.value = keepStaleData ? staleMessage : message
    return true
  }

  function isCurrent(context) {
    return context?.revision === currentRevision.value
  }

  return {
    status,
    errorText,
    showingStaleData,
    currentRevision,
    begin,
    succeed,
    fail,
    isCurrent
  }
}

export function useWorkspaceMetricState(keys = []) {
  const revision = ref(0)
  const states = reactive({})

  for (const key of keys) {
    states[key] = createMetricState()
  }

  const values = computed(() => Object.fromEntries(
    Object.entries(states).map(([key, state]) => [key, state.value])
  ))

  function begin() {
    const nextRevision = revision.value + 1
    revision.value = nextRevision
    Object.values(states).forEach(state => {
      state.value = null
      state.loading = true
      state.error = false
      state.revision = nextRevision
    })
    return nextRevision
  }

  function resolve(targetRevision, key, value) {
    const state = states[key]
    if (!state || targetRevision !== revision.value || state.revision !== targetRevision) return false
    state.value = value
    state.loading = false
    state.error = false
    return true
  }

  function reject(targetRevision, key) {
    const state = states[key]
    if (!state || targetRevision !== revision.value || state.revision !== targetRevision) return false
    state.value = null
    state.loading = false
    state.error = true
    return true
  }

  function rejectAll(targetRevision) {
    Object.keys(states).forEach(key => reject(targetRevision, key))
  }

  return {
    revision,
    states,
    values,
    begin,
    resolve,
    reject,
    rejectAll
  }
}

function createMetricState() {
  return {
    value: null,
    loading: false,
    error: false,
    revision: 0
  }
}

function stableSerialize(value) {
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}
