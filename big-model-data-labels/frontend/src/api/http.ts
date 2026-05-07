import axios from 'axios'
import type { ApiResponse } from './types'

export const http = axios.create({
  baseURL: '/api',
  timeout: 60_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const contentType = resp.headers['content-type'] as string | undefined
    if (contentType && contentType.includes('application/json')) {
      const body = resp.data as ApiResponse<unknown>
      if (typeof body?.code === 'number' && body.code !== 0) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
    }
    return resp
  },
  (err) => {
    if (err?.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

export async function unwrap<T>(p: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const resp = await p
  return resp.data.data
}

