export const contactPhonePattern = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/
export const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidContactPhone(value) {
  const text = String(value || '').trim()
  return !text || contactPhonePattern.test(text)
}

export function isValidEmail(value) {
  const text = String(value || '').trim()
  return !text || emailPattern.test(text)
}

export function validateContactPhone(_rule, value, callback) {
  if (!isValidContactPhone(value)) {
    callback(new Error('请输入正确的联系电话，例如 13800000000 或 021-88888888'))
    return
  }
  callback()
}

export function validateEmail(_rule, value, callback) {
  if (!isValidEmail(value)) {
    callback(new Error('请输入正确的邮箱地址，例如 name@example.com'))
    return
  }
  callback()
}

export const optionalContactPhoneRule = { validator: validateContactPhone, trigger: 'blur' }
export const optionalEmailRule = { validator: validateEmail, trigger: 'blur' }
