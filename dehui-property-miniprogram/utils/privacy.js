const PRIVACY_SCENARIOS = {
  workorderImage: {
    title: '现场照片使用说明',
    content: '为了提交报修工单和辅助物业人员判断问题，我们会使用你选择或拍摄的现场照片，并上传到物业服务系统。'
  },
  staffInternalImage: {
    title: '业务照片使用说明',
    content: '为了完成巡检、工单处理或抄表，我们会使用你选择或拍摄的现场照片，并以受保护的内部业务附件上传。'
  },
  publicVisitor: {
    title: '访客预约信息使用说明',
    content: '为了完成无需登录的访客预约，我们会使用你主动填写的姓名、手机号、来访时间和来访事由，用于预约联系及到访核验。'
  }
}

const acceptedScenarios = {}

function getScenario(key) {
  return PRIVACY_SCENARIOS[key] || {
    title: '隐私信息使用说明',
    content: '为了完成当前业务操作，我们会使用你主动提供的相关信息。'
  }
}

function confirmUsageNotice(scenario) {
  if (typeof wx === 'undefined' || typeof wx.showModal !== 'function') {
    return Promise.resolve(true)
  }

  return new Promise((resolve) => {
    wx.showModal({
      title: scenario.title,
      content: `${scenario.content}\n\n请确认已阅读并同意小程序《用户隐私保护指引》。`,
      cancelText: '暂不使用',
      confirmText: '同意继续',
      confirmColor: '#D71920',
      success(res) {
        resolve(Boolean(res.confirm))
      },
      fail() {
        resolve(false)
      }
    })
  })
}

function ensurePlatformPrivacyAuthorization() {
  if (
    typeof wx === 'undefined' ||
    typeof wx.getPrivacySetting !== 'function' ||
    typeof wx.requirePrivacyAuthorize !== 'function'
  ) {
    return Promise.resolve(true)
  }

  return new Promise((resolve) => {
    wx.getPrivacySetting({
      success(res) {
        if (!res || !res.needAuthorization) {
          resolve(true)
          return
        }

        wx.requirePrivacyAuthorize({
          success() {
            resolve(true)
          },
          fail() {
            resolve(false)
          }
        })
      },
      fail() {
        resolve(true)
      }
    })
  })
}

async function ensurePrivacyAuthorization(key) {
  if (acceptedScenarios[key]) {
    return true
  }

  const platformAuthorized = await ensurePlatformPrivacyAuthorization()
  if (!platformAuthorized) {
    return false
  }

  const accepted = await confirmUsageNotice(getScenario(key))
  if (accepted) {
    acceptedScenarios[key] = true
  }
  return accepted
}

module.exports = {
  ensurePrivacyAuthorization
}
