import { http, unwrap } from './http'

export interface User {
  id: number
  username: string
  role: 'admin' | 'normal'
  email?: string
  fullName?: string
  isActive?: boolean
  lastLogin?: string
  createdAt?: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return unwrap(http.post('/auth/login', { username, password }))
}

export async function me(): Promise<User> {
  return unwrap(http.get('/auth/me'))
}

export async function logout(): Promise<void> {
  await unwrap(http.post('/auth/logout'))
}

