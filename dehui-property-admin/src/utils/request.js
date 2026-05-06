import axios from 'axios'
import { ElMessage } from 'element-plus'

// ⚠️ 如果你后面要用 router，可以再引入
// import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// ================= 请求拦截 =================
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    console.log('request.js token=', token)

    if (token) {
      config.headers = config.headers || {}
      config.headers['Authorization'] = token
      config.headers['token'] = token
      console.log('headers=', config.headers)
    }

    return config
  },
  error => Promise.reject(error)
)

// ================= 响应拦截 =================
request.interceptors.response.use(
  response => {
    const res = response.data

    // 兼容后端统一返回结构
    if (res && res.code !== undefined) {
      if (res.code !== 200) {
        ElMessage.error(res.message || '请求失败')
        return Promise.reject(res)
      }

      // 👉 返回真正数据
      return res.data
    }

    // 👉 如果不是统一结构，直接返回
    return res
  },
  error => {
    const status = error.response?.status

    if (status === 401) {
      ElMessage.error('未登录或登录已过期')

      localStorage.removeItem('token')
      localStorage.removeItem('username')

      // ⚠️ 简单方案（你现在用这个就行）
      window.location.href = '/login'

      // 👉 如果后面用 router，可改成：
      // router.push('/login')
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else {
      ElMessage.error(error.message || '网络请求失败')
    }

    return Promise.reject(error)
  }
)

export default request