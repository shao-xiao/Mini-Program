const { getBaseURL } = require('../config/env')

const ERROR_KINDS = Object.freeze({
  AUTH_REQUIRED: 'AUTH_REQUIRED',
  FORBIDDEN: 'FORBIDDEN',
  BUSINESS: 'BUSINESS',
  NETWORK: 'NETWORK',
  SERVER: 'SERVER'
})

function requestURL(url) {
  const value = String(url || '')
  if (/^https?:\/\//i.test(value)) {
    return value
  }
  const baseURL = getBaseURL()
  const relative = /\/api$/i.test(baseURL) && /^\/api(?:\/|$)/i.test(value)
    ? value.slice(4)
    : value
  return `${baseURL}${relative.startsWith('/') ? relative : `/${relative}`}`
}

function httpOrigin(url) {
  const matched = String(url || '').match(/^(https?):\/\/([^/?#]+)/i)
  return matched ? `${matched[1].toLowerCase()}://${matched[2].toLowerCase()}` : ''
}

function isTrustedApiURL(url) {
  const targetOrigin = httpOrigin(requestURL(url))
  const apiOrigin = httpOrigin(getBaseURL())
  return Boolean(targetOrigin && apiOrigin && targetOrigin === apiOrigin)
}

function authorizedHeaders(url, token, supplied = {}) {
  const header = { ...supplied }
  let suppliedAuthorization = ''
  Object.keys(header).forEach(key => {
    if (key.toLowerCase() === 'authorization') {
      suppliedAuthorization = String(header[key] || suppliedAuthorization)
      delete header[key]
    }
  })
  if (isTrustedApiURL(url) && (token || suppliedAuthorization)) {
    header.Authorization = token || suppliedAuthorization
  }
  return header
}

function downloadFilePath(url, filename) {
  if (!wx.env || !wx.env.USER_DATA_PATH) {
    return ''
  }
  const suffix = String(filename || '')
    .split('.')
    .pop()
    .toLowerCase()
  const ext = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png', 'webp'].includes(suffix)
    ? suffix
    : 'pdf'
  const value = String(url || '')
  const billId = value.match(/\/bills\/([^/]+)\/invoice\/download/)?.[1]
  const contractId = value.match(/\/contracts\/([^/]+)\/file\/download/)?.[1]
  const prefix = contractId ? 'contract' : 'invoice'
  const id = contractId || billId || 'download'
  return `${wx.env.USER_DATA_PATH}/${prefix}-${id}-${Date.now().toString(36)}.${ext}`
}

function numericCode(value) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) return Number(value)
  return null
}

function isSuccessStatus(statusCode) {
  return statusCode === null || (statusCode >= 200 && statusCode < 300)
}

function clearUnauthorizedSession(requestToken) {
  if (typeof requestToken !== 'undefined' && typeof wx !== 'undefined' && typeof wx.getStorageSync === 'function') {
    const currentToken = wx.getStorageSync('token') || ''
    if (currentToken !== (requestToken || '')) {
      return false
    }
  }
  try {
    const auth = require('./auth')
    if (typeof auth.clearSession === 'function') {
      auth.clearSession()
      return true
    }
  } catch (error) {
    // Fall through to the storage-only cleanup when auth is not available yet.
  }

  if (typeof wx !== 'undefined' && typeof wx.removeStorageSync === 'function') {
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    wx.removeStorageSync('identity')
  }
  return true
}

function errorKind(code, statusCode, network) {
  if (network) return ERROR_KINDS.NETWORK
  if (code === 401 || statusCode === 401) return ERROR_KINDS.AUTH_REQUIRED
  if (code === 403 || statusCode === 403) return ERROR_KINDS.FORBIDDEN
  if ((code && code >= 500) || (statusCode && statusCode >= 500)) return ERROR_KINDS.SERVER
  return ERROR_KINDS.BUSINESS
}

function fallbackMessage(kind) {
  if (kind === ERROR_KINDS.AUTH_REQUIRED) return '请先登录或重新登录'
  if (kind === ERROR_KINDS.FORBIDDEN) return '当前账号无业务资格'
  if (kind === ERROR_KINDS.NETWORK) return '网络不可用'
  if (kind === ERROR_KINDS.SERVER) return '服务暂时不可用'
  return '请求失败'
}

function createApiError({ response, data, cause, message, network = false, requestToken }) {
  const statusCode = numericCode(response && response.statusCode)
  const bodyCode = numericCode(data && data.code)
  const code = statusCode === 401 || statusCode === 403
    ? statusCode
    : (bodyCode === null ? statusCode : bodyCode)
  const kind = errorKind(code, statusCode, network)
  const detail = message || (data && data.message) || fallbackMessage(kind)
  const error = new Error(detail)
  error.name = 'ApiError'
  error.kind = kind
  error.code = code
  error.statusCode = statusCode
  error.data = data
  error.raw = response || cause || data
  if (cause) error.cause = cause

  if (kind === ERROR_KINDS.AUTH_REQUIRED) {
    clearUnauthorizedSession(requestToken)
  }
  return error
}

function notifyError(error, silent, fallback) {
  if (silent || typeof wx === 'undefined' || typeof wx.showToast !== 'function') return
  wx.showToast({
    title: error.message || fallback || '请求失败',
    icon: 'none'
  })
}

function responseError(response, data, silent, fallback, requestToken) {
  const error = createApiError({ response, data, requestToken })
  notifyError(error, silent, fallback)
  return error
}

function request(options = {}) {
  const token = wx.getStorageSync('token')
  const silent = options.silent === true
  const header = authorizedHeaders(options.url, token, {
    'content-type': 'application/json',
    ...(options.header || {})
  })

  return new Promise((resolve, reject) => {
    wx.request({
      url: requestURL(options.url),
      method: options.method || 'GET',
      data: typeof options.data === 'undefined' ? {} : options.data,
      header,
      success(res) {
        const data = res.data
        const statusCode = numericCode(res.statusCode)

        if (statusCode === 401 || statusCode === 403) {
          reject(responseError(res, data, silent, undefined, token))
          return
        }

        if (data && typeof data.code !== 'undefined') {
          if (numericCode(data.code) === 200 && isSuccessStatus(statusCode)) {
            resolve(data.data)
            return
          }
          reject(responseError(res, data, silent, undefined, token))
          return
        }

        if (isSuccessStatus(statusCode)) {
          resolve(data)
          return
        }

        reject(responseError(res, data, silent, '网络请求失败', token))
      },
      fail(cause) {
        const error = createApiError({ cause, network: true })
        notifyError(error, silent, '网络不可用')
        reject(error)
      }
    })
  })
}

function get(url, data, options = {}) {
  return request({ ...options, url, data, method: 'GET' })
}

function post(url, data, options = {}) {
  return request({ ...options, url, data, method: 'POST' })
}

function patch(url, data, options = {}) {
  return request({ ...options, url, data, method: 'PATCH' })
}

function remove(url, data, options = {}) {
  return request({ ...options, url, data, method: 'DELETE' })
}

function download(url, filename, options = {}) {
  const token = wx.getStorageSync('token')
  const silent = options.silent === true
  return new Promise((resolve, reject) => {
    const filePath = options.temporary === true ? '' : downloadFilePath(url, filename)
    const downloadOptions = {
      url: requestURL(url),
      header: authorizedHeaders(url, token, options.header || {}),
      success(res) {
        const statusCode = numericCode(res.statusCode)
        const downloadedPath = res.filePath || res.tempFilePath
        if (statusCode >= 200 && statusCode < 300 && downloadedPath) {
          resolve(downloadedPath)
          return
        }
        const error = responseError(res, res.data, silent, '文件下载失败', token)
        reject(error)
      },
      fail(cause) {
        const error = createApiError({ cause, message: '文件下载失败', network: true })
        notifyError(error, silent, '文件下载失败')
        reject(error)
      }
    }
    if (filePath) {
      downloadOptions.filePath = filePath
    }
    wx.downloadFile(downloadOptions)
  })
}

function upload(url, filePath, name = 'file', options = {}) {
  const token = wx.getStorageSync('token')
  const silent = options.silent === true
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: requestURL(url),
      filePath,
      name,
      header: authorizedHeaders(url, token, options.header || {}),
      formData: options.formData || {},
      success(res) {
        let data = res.data
        if (typeof data === 'string') {
          try {
            data = JSON.parse(data)
          } catch (cause) {
            const statusCode = numericCode(res.statusCode)
            const error = isSuccessStatus(statusCode)
              ? createApiError({ response: res, cause, message: '上传响应格式错误' })
              : responseError(res, null, true, '上传失败', token)
            notifyError(error, silent, '上传失败')
            reject(error)
            return
          }
        }

        const statusCode = numericCode(res.statusCode)
        if (statusCode === 401 || statusCode === 403) {
          reject(responseError(res, data, silent, '上传失败', token))
          return
        }
        if (data && typeof data.code !== 'undefined') {
          if (numericCode(data.code) === 200 && isSuccessStatus(statusCode)) {
            resolve(data.data)
            return
          }
          reject(responseError(res, data, silent, '上传失败', token))
          return
        }
        if (isSuccessStatus(statusCode)) {
          resolve(data)
          return
        }
        reject(responseError(res, data, silent, '上传失败', token))
      },
      fail(cause) {
        const error = createApiError({ cause, message: '上传失败', network: true })
        notifyError(error, silent, '上传失败')
        reject(error)
      }
    })
  })
}

module.exports = {
  request,
  get,
  post,
  patch,
  delete: remove,
  download,
  upload,
  getBaseURL,
  ERROR_KINDS,
  createApiError
}
