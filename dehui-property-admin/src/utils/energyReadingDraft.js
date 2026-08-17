export function findDuplicateEnergyReading(records, draft) {
  if (!Array.isArray(records) || !draft?.meterId || !draft?.periodMonth) return null

  const meterId = String(draft.meterId)
  const periodMonth = String(draft.periodMonth)
  const editingId = draft.id == null ? null : String(draft.id)

  return records.find(record => (
    String(record?.meterId) === meterId
    && String(record?.periodMonth) === periodMonth
    && (editingId === null || String(record?.id) !== editingId)
  )) || null
}
