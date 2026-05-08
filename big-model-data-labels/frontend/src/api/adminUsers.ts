import { http, unwrap } from './http'
import type { PageResult } from './types'

export type UserRole = 'admin' | 'normal'

export interface AdminUser {
  id: number
  username: string
  role: UserRole
  email?: string
  fullName?: string
  isActive: boolean
  lastLogin?: string
  createdAt?: string
}

export async function listUsers(params: {
  page?: number
  size?: number
  keyword?: string
}): Promise<PageResult<AdminUser>> {
  return unwrap(http.get('/admin/users', { params }))
}

export async function createUser(payload: {
  username: string
  password: string
  role: UserRole
  email?: string
  fullName?: string
  isActive: boolean
}): Promise<AdminUser> {
  return unwrap(http.post('/admin/users', payload))
}

export async function updateUser(
  id: number,
  payload: {
    role: UserRole
    email?: string
    fullName?: string
    isActive: boolean
  },
): Promise<AdminUser> {
  return unwrap(http.put(`/admin/users/${id}`, payload))
}

export async function resetPassword(id: number, payload: { newPassword: string }): Promise<void> {
  await unwrap(http.post(`/admin/users/${id}/reset-password`, payload))
}

