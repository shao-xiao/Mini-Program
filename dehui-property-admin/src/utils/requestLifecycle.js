export function createLatestRequestGuard() {
  let currentRequestId = 0
  let active = true

  return {
    start() {
      currentRequestId += 1
      return currentRequestId
    },
    isCurrent(requestId) {
      return active && requestId === currentRequestId
    },
    dispose() {
      active = false
      currentRequestId += 1
    }
  }
}

export function createDebouncedTask(task, delay = 300) {
  let timer = null

  return {
    schedule(...args) {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        timer = null
        task(...args)
      }, delay)
    },
    cancel() {
      if (timer) clearTimeout(timer)
      timer = null
    }
  }
}
