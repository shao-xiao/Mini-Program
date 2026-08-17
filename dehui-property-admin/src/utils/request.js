import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAdminSession } from './authStorage.js'
import { requestErrorMessage } from './requestError.js'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')

    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = token
      config.headers.token = token
    }

    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const res = response.data
    const silent = response.config?.silent === true

    if (res && res.code !== undefined) {
      if (res.code !== 200) {
        if (!silent) {
          ElMessage.error(res.message || '请求失败')
          res.requestMessageShown = true
        }
        return Promise.reject(res)
      }

      return res.data
    }

    return res
  },
  error => {
    const status = error.response?.status
    const silent = error.config?.silent === true

    if (silent) {
      return Promise.reject(error)
    }

    if (status === 401) {
      ElMessage.error('未登录或登录已过期')
      error.requestMessageShown = true

      clearAdminSession()

      window.location.href = '/login'
    } else if (status === 403) {
      ElMessage.error('无权限访问')
      error.requestMessageShown = true
    } else {
      ElMessage.error(requestErrorMessage(error, '网络请求失败'))
      error.requestMessageShown = true
    }

    return Promise.reject(error)
  }
)

export default request
