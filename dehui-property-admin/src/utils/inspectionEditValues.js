// Keep the complete detail for schema fields, including system metadata and original photos.
export function inspectionEditValues(record = {}) {
  return {
    ...record,
    inspectionDate: record.inspectionDate || '',
    inspector: record.inspector || '',
    inspectionType: record.inspectionType || '',
    area: record.area || '',
    target: record.target || '',
    result: record.result || 'NORMAL',
    status: record.status || 'OPEN',
    problemDescription: record.problemDescription || '',
    actionTaken: record.actionTaken || '',
    remark: record.remark || '',
    images: record.images ?? record.attachments ?? [],
    customFields: record.customFields || {}
  }
}
