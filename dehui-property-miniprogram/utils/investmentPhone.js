const DEFAULT_INVESTMENT_PHONE = '021-65686336'

const PHONE_DISPLAY_REG = /^\+?[\d\s()\-]+$/
const PHONE_DIAL_REG = /^(?:0\d{9,11}|[1-9]\d{6,14}|\+[1-9]\d{6,14})$/

function normalizePhoneNumber(value) {
  const displayValue = String(value || '').trim()
  if (!displayValue || !PHONE_DISPLAY_REG.test(displayValue)) return ''

  const dialValue = displayValue.replace(/[^\d+]/g, '')
  return PHONE_DIAL_REG.test(dialValue) ? dialValue : ''
}

function formatPhoneForDisplay(value, dialValue) {
  const displayValue = String(value || '').trim()
  if (!/^\d+$/.test(displayValue)) return displayValue
  if (/^0\d{10}$/.test(dialValue)) return `${dialValue.slice(0, 3)}-${dialValue.slice(3)}`
  if (/^0\d{11}$/.test(dialValue)) return `${dialValue.slice(0, 4)}-${dialValue.slice(4)}`
  return displayValue
}

function resolveInvestmentPhone(overview) {
  const source = overview && typeof overview === 'object' ? overview : {}
  const contacts = Array.isArray(source.contact) ? source.contact : []
  const candidates = [source.phone]

  contacts.forEach((contact) => {
    candidates.push(contact?.content, contact?.subtitle)
  })

  for (const candidate of candidates) {
    const dialPhone = normalizePhoneNumber(candidate)
    if (dialPhone) {
      return {
        displayPhone: formatPhoneForDisplay(candidate, dialPhone),
        dialPhone
      }
    }
  }

  return {
    displayPhone: DEFAULT_INVESTMENT_PHONE,
    dialPhone: normalizePhoneNumber(DEFAULT_INVESTMENT_PHONE)
  }
}

function isCancelled(error) {
  return /cancel|deny|denied|refuse|reject/i.test(String(error?.errMsg || error?.message || ''))
}

function showInvestmentPhoneConsult(page, overview, wxApi) {
  if (!page || !wxApi || page._investmentPhoneConsultPending) return

  const { displayPhone, dialPhone } = resolveInvestmentPhone(overview)
  if (!dialPhone) {
    wxApi.showToast({ title: '联系电话暂不可用', icon: 'none' })
    return
  }

  page._investmentPhoneConsultPending = true
  let dialing = false
  const release = () => {
    page._investmentPhoneConsultPending = false
  }

  try {
    wxApi.showActionSheet({
      itemList: [displayPhone],
      success: ({ tapIndex }) => {
        if (tapIndex !== 0) {
          release()
          return
        }

        dialing = true
        try {
          wxApi.makePhoneCall({
            phoneNumber: dialPhone,
            fail: (error) => {
              if (isCancelled(error)) return
              wxApi.showToast({ title: '无法发起拨号', icon: 'none' })
            },
            complete: release
          })
        } catch (error) {
          release()
          wxApi.showToast({ title: '无法发起拨号', icon: 'none' })
        }
      },
      fail: (error) => {
        release()
        if (isCancelled(error)) return
        wxApi.showToast({ title: '联系电话暂不可用', icon: 'none' })
      },
      complete: () => {
        if (!dialing) release()
      }
    })
  } catch (error) {
    release()
    wxApi.showToast({ title: '联系电话暂不可用', icon: 'none' })
  }
}

module.exports = {
  DEFAULT_INVESTMENT_PHONE,
  normalizePhoneNumber,
  resolveInvestmentPhone,
  showInvestmentPhoneConsult
}
