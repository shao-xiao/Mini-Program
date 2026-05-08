const { getIdentity } = require('../../utils/auth')

Page({
  data: {
    identityText: '访客'
  },

  onShow() {
    const identityMap = {
      INTERNAL: '内部员工',
      TENANT: '租户用户',
      PUBLIC: '访客'
    }
    const identity = getIdentity()
    this.setData({
      identityText: identityMap[identity] || '访客'
    })
  }
})
