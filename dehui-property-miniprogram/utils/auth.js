function getIdentity() {
  return wx.getStorageSync('identity') || 'PUBLIC'
}

function setSession(session) {
  if (session.token) {
    wx.setStorageSync('token', session.token)
  }
  const profile = session.profile || session.userInfo
  if (profile) {
    wx.setStorageSync('userInfo', profile)
    wx.setStorageSync('identity', profile.userType || 'PUBLIC')
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
