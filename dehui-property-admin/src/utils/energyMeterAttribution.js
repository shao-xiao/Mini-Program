function normalizeId(value) {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : String(value)
}

export function currentOpenAttribution(bindings = []) {
  return bindings.find(item => item?.status === 'ACTIVE' && !item?.effectiveTo) || null
}

export function sameAttributionTarget(left, right) {
  if (!left || !right) return false
  return String(left.subjectType || '').toUpperCase() === String(right.subjectType || '').toUpperCase()
    && normalizeId(left.tenantId) === normalizeId(right.tenantId)
    && normalizeId(left.roomId) === normalizeId(right.roomId)
}

export function validateAttributionDraft({ current, commissioningDate, draft }) {
  if (!draft?.effectiveFrom) return '请选择计量归属开始日期'

  if (commissioningDate && draft.effectiveFrom < commissioningDate) {
    return `计量归属开始日期不能早于表具投运日期 ${commissioningDate}`
  }

  if (current?.effectiveFrom && draft.effectiveFrom <= current.effectiveFrom) {
    return `新的计量归属开始日期必须晚于当前归属开始日期 ${current.effectiveFrom}`
  }

  if (current && sameAttributionTarget(current, draft)) {
    return '当前计量归属未发生变化，无需重复变更'
  }

  return ''
}
