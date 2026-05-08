function getIdentity() {
  return wx.getStorageSync('identity') || 'PUBLIC'
}

function setSession(session) {
  if (session.token) {
    wx.setStorageSync('token', session.token)
  }
  if (session.userInfo) {
    wx.setStorageSync('userInfo', session.userInfo)
  }
  if (session.identity) {
    wx.setStorageSync('identity', session.identity)
  }
}

function clearSession() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  wx.removeStorageSync('identity')
}

module.exports = {
  getIdentity,
  setSession,
  clearSession
}
