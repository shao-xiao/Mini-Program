export function buildCsvText(rows = [], columns = []) {
  const header = columns.map(column => escapeCsvCell(column.label)).join(',')
  const body = rows.map((row, rowIndex) => columns
    .map(column => escapeCsvCell(readColumnValue(column, row, rowIndex)))
    .join(','))

  return [header, ...body].join('\r\n')
}

export function escapeCsvCell(value) {
  if (value === undefined || value === null) return ''
  const rawText = Array.isArray(value) ? value.join('、') : String(value)
  const text = typeof value === 'string' && /^[\t\r ]*[=+\-@]/.test(rawText) ? `'${rawText}` : rawText
  if (!/[",\r\n]/.test(text)) return text
  return `"${text.replaceAll('"', '""')}"`
}

function readColumnValue(column, row, rowIndex) {
  if (typeof column.valueGetter === 'function') return column.valueGetter(row, rowIndex)
  return row?.[column.key]
}
