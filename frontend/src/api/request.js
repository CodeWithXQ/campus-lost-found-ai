import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 20000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && data.code === 200) {
      return data.data
    }
    if (data && data.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(data.msg || '请先登录')
      window.location.href = '/login'
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error((data && data.msg) || '请求失败')
    return Promise.reject(new Error((data && data.msg) || '请求失败'))
  },
  (err) => {
    ElMessage.error(err.message || '网络错误，请检查后端服务是否启动')
    return Promise.reject(err)
  }
)

export default request
