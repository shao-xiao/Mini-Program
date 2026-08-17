const api = require('./request')
const { refreshCurrentProfile } = require('./auth')
const { ensurePrivacyAuthorization } = require('./privacy')
const {
  canAccessFeature,
  getCapabilities,
  getUserType,
  FEATURE_DEFINITIONS
} = require('./capabilities')

async function requireStaffFeature(featureKey) {
  const profile = await refreshCurrentProfile({ silent: true })
  if (getUserType(profile) !== 'STAFF' || !canAccessFeature(featureKey, profile)) {
    wx.showToast({ title: '当前员工无此移动权限', icon: 'none' })
    setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 200)
    const error = new Error('STAFF capability denied')
    error.kind = 'FORBIDDEN'
    throw error
  }
  return profile
}

function has(capability, profile) {
  return getCapabilities(profile).includes(capability)
}

function staffModules(profile) {
  return [
    'staffInspection',
    'staffWorkorder',
    'staffEnergy',
    'staffMeeting',
    'staffVisitor',
    'staffInvestment'
  ].filter(key => canAccessFeature(key, profile))
    .map(key => ({ ...FEATURE_DEFINITIONS[key] }))
}

function pageRecords(payload) {
  if (!payload || typeof payload !== 'object') return { records: [], total: 0, page: 1, pageSize: 20 }
  const records = Array.isArray(payload.records)
    ? payload.records
    : (Array.isArray(payload.items) ? payload.items : [])
  return {
    records,
    total: Number(payload.total || records.length),
    page: Number(payload.page || 1),
    pageSize: Number(payload.pageSize || 20)
  }
}

function mergePage(current, payload, refresh) {
  const normalized = pageRecords(payload)
  const records = refresh ? normalized.records : (current || []).concat(normalized.records)
  return {
    records,
    page: normalized.page,
    pageSize: normalized.pageSize,
    total: normalized.total,
    hasMore: records.length < normalized.total
  }
}

function compactQuery(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

async function loadSchema(moduleKey, surface = 'MINIPROGRAM_FORM') {
  return api.get(`/mobile/module-fields/${moduleKey}`, { surface }, { silent: true })
}

function chooseImages(count = 6) {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count: Math.min(Math.max(Number(count) || 1, 1), 9),
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success(result) {
        resolve((result.tempFiles || []).map(item => item.tempFilePath).filter(Boolean))
      },
      fail(error) {
        if (error && String(error.errMsg || '').includes('cancel')) resolve([])
        else reject(error)
      }
    })
  })
}

async function uploadInternalImages(count = 6) {
  const authorized = await ensurePrivacyAuthorization('staffInternalImage')
  if (!authorized) return []
  const paths = await chooseImages(count)
  const result = []
  for (const path of paths) {
    result.push(await api.upload('/mobile/staff/files', path, 'file'))
  }
  return result
}

function customFileValue(files) {
  return {
    version: 1,
    files: (files || []).map((file, index) => ({
      fileId: Number(file.fileId),
      sortOrder: index
    }))
  }
}

function imageFieldLimit(field, fallback = 6) {
  const configured = Number(field && field.validation && field.validation.maxFiles)
  return Number.isFinite(configured) && configured > 0 ? configured : fallback
}

async function previewProtected(items, current) {
  const source = (items || [])
    .map(item => {
      if (typeof item === 'string') return { url: item, name: 'image.jpg' }
      if (!item || !item.url) return null
      return { url: item.url, name: item.name || 'image.jpg' }
    })
    .filter(Boolean)
  if (!source.length) return
  wx.showLoading({ title: '加载图片' })
  try {
    const localPaths = []
    for (const item of source) {
      localPaths.push(await api.download(item.url, item.name, { silent: true }))
    }
    const currentUrl = typeof current === 'string' ? current : current && current.url
    const currentIndex = source.findIndex(item => item.url === currentUrl)
    wx.previewImage({
      urls: localPaths,
      current: localPaths[Math.max(0, currentIndex)]
    })
  } finally {
    wx.hideLoading()
  }
}

function formValue(event) {
  return event && event.detail ? event.detail.value : undefined
}

function message(error, fallback = '加载失败，请稍后重试') {
  return error && error.message ? error.message : fallback
}

module.exports = {
  requireStaffFeature,
  has,
  staffModules,
  pageRecords,
  mergePage,
  compactQuery,
  loadSchema,
  uploadInternalImages,
  customFileValue,
  imageFieldLimit,
  previewProtected,
  formValue,
  message
}
