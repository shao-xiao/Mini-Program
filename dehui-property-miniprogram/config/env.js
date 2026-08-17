const env = {
  dev: {
    name: '本机开发',
    baseURL: 'http://localhost:8080/api'
  },
  lan: {
    name: '真机同网',
    baseURL: 'http://192.168.1.100:8080/api'
  },
  prod: {
    name: '生产环境',
    baseURL: 'https://wuye.mingda.com.cn/api'
  }
}

const activeEnv = 'prod'
const API_BASE_URL_KEY = 'apiBaseURL'
const RUNTIME_ENVIRONMENT_KEY = 'apiRuntimeEnvironment'
const ENVIRONMENT_SCOPED_SESSION_KEYS = [
  'token',
  'userInfo',
  'identity'
]

function normalizeBaseURL(value) {
  return String(value || '').trim().replace(/\/+$/, '')
}

function getBaseURL() {
  if (activeEnv === 'prod') {
    return normalizeBaseURL(env.prod.baseURL)
  }
  const stored = typeof wx !== 'undefined' ? wx.getStorageSync(API_BASE_URL_KEY) : ''
  return normalizeBaseURL(stored || env[activeEnv].baseURL)
}

function setBaseURL(value) {
  if (activeEnv === 'prod') {
    if (typeof wx !== 'undefined') wx.removeStorageSync(API_BASE_URL_KEY)
    return getBaseURL()
  }
  const next = normalizeBaseURL(value)
  if (next) {
    wx.setStorageSync(API_BASE_URL_KEY, next)
  }
  return getBaseURL()
}

function resetBaseURL() {
  wx.removeStorageSync(API_BASE_URL_KEY)
  return getBaseURL()
}

function runtimeEnvironmentFingerprint() {
  return `${activeEnv}|${normalizeBaseURL(env[activeEnv].baseURL)}`
}

function synchronizeRuntimeEnvironment() {
  if (typeof wx === 'undefined') return false
  const current = runtimeEnvironmentFingerprint()
  const previous = String(wx.getStorageSync(RUNTIME_ENVIRONMENT_KEY) || '')
  if (previous === current) return false

  wx.removeStorageSync(API_BASE_URL_KEY)
  ENVIRONMENT_SCOPED_SESSION_KEYS.forEach(key => wx.removeStorageSync(key))
  wx.setStorageSync(RUNTIME_ENVIRONMENT_KEY, current)
  return true
}

module.exports = {
  activeEnv,
  env,
  baseURL: env[activeEnv].baseURL,
  defaultBaseURL: env[activeEnv].baseURL,
  lanBaseURL: env.lan.baseURL,
  prodBaseURL: env.prod.baseURL,
  getBaseURL,
  setBaseURL,
  resetBaseURL,
  runtimeEnvironmentFingerprint,
  synchronizeRuntimeEnvironment
}
