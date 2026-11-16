import axios from 'axios'

/**
 * axios 实例
 * 默认走相对路径 /api，由 Vite dev server 代理到 http://127.0.0.1:8080；
 * 如需直连后端，可在 .env.local 中设置 VITE_API_BASE_URL=http://127.0.0.1:8080。
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60000,
})

// 请求拦截器：可按需加 token 等
http.interceptors.request.use((config) => config)

// 响应拦截器：统一拆解 Result{code,msg,data}
http.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result && typeof result === 'object' && 'code' in result) {
      if (result.code === 0) {
        return result.data
      }
      return Promise.reject(new Error(result.msg || '请求失败'))
    }
    return result
  },
  (error) => {
    let message = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    } else if (error.response) {
      message = `服务器错误（${error.response.status}）`
    } else if (error.request) {
      message = '无法连接服务器，请确认后端已启动'
    }
    return Promise.reject(new Error(message))
  },
)

export default http
