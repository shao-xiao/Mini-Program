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

const activeEnv = 'dev'
const API_BASE_URL_KEY = 'apiBaseURL'

function normalizeBaseURL(value) {
  return String(value || '').trim().replace(/\/+$/, '')
}

function getBaseURL() {
  const stored = typeof wx !== 'undefined' ? wx.getStorageSync(API_BASE_URL_KEY) : ''
  return normalizeBaseURL(stored || env[activeEnv].baseURL)
}

function setBaseURL(value) {
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

module.exports = {
  activeEnv,
  env,
  baseURL: env[activeEnv].baseURL,
  defaultBaseURL: env[activeEnv].baseURL,
  lanBaseURL: env.lan.baseURL,
  prodBaseURL: env.prod.baseURL,
  getBaseURL,
  setBaseURL,
  resetBaseURL
}
