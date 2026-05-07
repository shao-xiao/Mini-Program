import axios from 'axios'
import { ElMessage } from 'element-plus'

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

      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      localStorage.removeItem('roles')
      localStorage.removeItem('userId')

      window.location.href = '/login'
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else {
      ElMessage.error(error.message || '网络请求失败')
    }

    return Promise.reject(error)
  }
)

export default request