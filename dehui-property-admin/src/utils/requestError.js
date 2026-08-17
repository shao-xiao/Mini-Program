function nonBlank(value) {
  if (typeof value !== 'string') return ''
  return value.trim()
}

export function requestErrorMessage(error, fallback = '请求失败，请稍后重试') {
  const responseData = error?.response?.data
  const directData = error?.data
  const candidates = [
    responseData?.message,
    responseData?.msg,
    directData?.message,
    directData?.msg,
    error?.message
  ]

  return candidates.map(nonBlank).find(Boolean) || fallback
}
