import axios from 'axios'

/** 开发默认走 Vite 代理（见 vite.config.ts），与页面同源；生产或未配代理时用 VITE_API_BASE 或直连 8080 */
const baseURL =
  import.meta.env.VITE_API_BASE ||
  (import.meta.env.DEV ? '' : 'http://localhost:8080')

export const api = axios.create({
  baseURL,
  timeout: 30000,
  withCredentials: true,
})
