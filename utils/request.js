const { baseURL } = require('../config/env')

function request(options) {
  const token = wx.getStorageSync('token')
  const header = {
    'content-type': 'application/json',
    ...(options.header || {})
  }

  if (token) {
    header.Authorization = token
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseURL}${options.url}`,
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

          wx.showToast({
            title: data.message || '请求失败',
            icon: 'none'
          })
          reject(data)
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data)
          return
        }

        wx.showToast({
          title: '网络请求失败',
          icon: 'none'
        })
        reject(res)
      },
      fail(error) {
        wx.showToast({
          title: '网络不可用',
          icon: 'none'
        })
        reject(error)
      }
    })
  })
}

module.exports = {
  request,
  get: (url, data) => request({ url, data, method: 'GET' }),
  post: (url, data) => request({ url, data, method: 'POST' }),
  patch: (url, data) => request({ url, data, method: 'PATCH' })
}
