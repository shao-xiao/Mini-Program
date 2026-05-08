App({
  globalData: {
    token: '',
    userInfo: null,
    identity: 'PUBLIC'
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.userInfo = wx.getStorageSync('userInfo') || null
    this.globalData.identity = wx.getStorageSync('identity') || 'PUBLIC'
  }
})
