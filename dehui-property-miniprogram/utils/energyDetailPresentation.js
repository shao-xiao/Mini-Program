const {
  billStatusPresentation,
  meterTypePresentation
} = require('./energyPresentation')

const ENERGY_DETAIL_FIELDS = [
  ['periodMonth', '账期'],
  ['readingDate', '抄表日期'],
  ['meterNo', '表具编号'],
  ['meterName', '表具名称'],
  ['meterType', '能耗类型'],
  ['previousReading', '上期读数'],
  ['currentReading', '本次读数'],
  ['usage', '本期用量'],
  ['unit', '计量单位'],
  ['unitPriceSnapshot', '单价快照'],
  ['amount', '结算金额'],
  ['basisStatus', '计费状态'],
  ['location', '安装位置']
]

function readableValue(key, value) {
  if (key === 'meterType') {
    return meterTypePresentation(value).label
  }
  if (key === 'basisStatus') {
    return billStatusPresentation(value).label
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value).replace('T', ' ')
}

function presentEnergyDetailRows(data = {}) {
  const source = data && typeof data === 'object' ? data : {}
  return ENERGY_DETAIL_FIELDS
    .filter(([key]) => Object.prototype.hasOwnProperty.call(source, key))
    .map(([key, label]) => ({
      key,
      label,
      value: readableValue(key, source[key])
    }))
}

module.exports = {
  presentEnergyDetailRows
}
