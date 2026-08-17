export function cloneWorkspaceValue(value, seen = new WeakMap()) {
  if (value === null || typeof value !== 'object') return value
  if (value instanceof Date) return new Date(value.getTime())
  if (seen.has(value)) return seen.get(value)

  if (Array.isArray(value)) {
    const result = []
    seen.set(value, result)
    value.forEach(item => result.push(cloneWorkspaceValue(item, seen)))
    return result
  }

  const result = {}
  seen.set(value, result)
  Object.entries(value).forEach(([key, item]) => {
    result[key] = cloneWorkspaceValue(item, seen)
  })
  return result
}
