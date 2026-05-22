const { getBaseURL } = require('../config/env')

function normalizePath(url) {
  const value = String(url || '').trim()
  return value.startsWith('/') ? value : `/${value}`
}

function buildURL(url) {
  return `${getBaseURL()}${normalizePath(url)}`
}

function getToken() {
  return wx.getStorageSync('token')
}

function clearSession() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  wx.removeStorageSync('identity')
}

function showToast(title, silent) {
  if (!silent) {
    wx.showToast({
      title,
      icon: 'none'
    })
  }
}

function unwrapResponse(res, silent) {
  const data = res.data

  if (data && typeof data.code !== 'undefined') {
    if (data.code === 200) {
      return data.data
    }

    if (data.code === 401) {
      clearSession()
    }

    throw data
  }

  if (res.statusCode >= 200 && res.statusCode < 300) {
    return data
  }

  if (res.statusCode === 401) {
    clearSession()
  }

  throw {
    code: res.statusCode,
    message: '请求失败',
    data
  }
}

function request(options) {
  const silent = options.silent === true
  const token = getToken()
  const header = {
    'content-type': 'application/json',
    ...(options.header || {})
  }

  if (token) {
    header.Authorization = token
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildURL(options.url),
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success(res) {
        try {
          resolve(unwrapResponse(res, silent))
        } catch (error) {
          showToast(error.message || '请求失败', silent)
          reject(error)
        }
      },
      fail(error) {
        showToast('网络不可用', silent)
        reject(error)
      }
    })
  })
}

function upload(url, filePath, name = 'file', formData = {}) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: buildURL(url),
      filePath,
      name,
      formData,
      header: token ? { Authorization: token } : {},
      success(res) {
        let data = res.data
        if (typeof data === 'string') {
          try {
            data = JSON.parse(data)
          } catch (error) {
            showToast('上传响应解析失败', false)
            reject(error)
            return
          }
        }

        try {
          resolve(unwrapResponse({ ...res, data }, false))
        } catch (error) {
          showToast(error.message || '上传失败', false)
          reject(error)
        }
      },
      fail(error) {
        showToast('上传失败', false)
        reject(error)
      }
    })
  })
}

module.exports = {
  request,
  get: (url, data, options = {}) => request({ ...options, url, data, method: 'GET' }),
  post: (url, data, options = {}) => request({ ...options, url, data, method: 'POST' }),
  put: (url, data, options = {}) => request({ ...options, url, data, method: 'PUT' }),
  patch: (url, data, options = {}) => request({ ...options, url, data, method: 'PATCH' }),
  delete: (url, data, options = {}) => request({ ...options, url, data, method: 'DELETE' }),
  upload,
  getBaseURL
}
