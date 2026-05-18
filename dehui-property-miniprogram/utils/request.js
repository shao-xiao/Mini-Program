const { getBaseURL } = require('../config/env')

function request(options) {
  const token = wx.getStorageSync('token')
  const silent = options.silent === true
  const header = {
    'content-type': 'application/json',
    ...(options.header || {})
  }

  if (token) {
    header.Authorization = token
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${getBaseURL()}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success(res) {
        const data = res.data

        if (data && typeof data.code !== 'undefined') {
          if (data.code === 200) {
            resolve(data.data)
            return
          }

          if (!silent) {
            wx.showToast({
              title: data.message || '请求失败',
              icon: 'none'
            })
          }
          reject(data)
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data)
          return
        }

        if (!silent) {
          wx.showToast({
            title: '网络请求失败',
            icon: 'none'
          })
        }
        reject(res)
      },
      fail(error) {
        if (!silent) {
          wx.showToast({
            title: '网络不可用',
            icon: 'none'
          })
        }
        reject(error)
      }
    })
  })
}

module.exports = {
  request,
  get: (url, data) => request({ url, data, method: 'GET' }),
  post: (url, data) => request({ url, data, method: 'POST' }),
  patch: (url, data) => request({ url, data, method: 'PATCH' }),
  delete: (url, data) => request({ url, data, method: 'DELETE' }),
  upload: (url, filePath, name = 'file') => {
    const token = wx.getStorageSync('token')
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: `${getBaseURL()}${url}`,
        filePath,
        name,
        header: token ? { Authorization: token } : {},
        success(res) {
          let data = res.data
          if (typeof data === 'string') {
            try {
              data = JSON.parse(data)
            } catch (error) {
              reject(error)
              return
            }
          }
          if (data && data.code === 200) {
            resolve(data.data)
            return
          }
          wx.showToast({
            title: data.message || '上传失败',
            icon: 'none'
          })
          reject(data)
        },
        fail(error) {
          wx.showToast({
            title: '上传失败',
            icon: 'none'
          })
          reject(error)
        }
      })
    })
  },
  getBaseURL
}
