import { http, unwrap } from './http'
import type { PageResult } from './types'
import type { Label } from './labels'

export interface BuiltinLabelCategory {
  code: string
  name: string
}

export async function listBuiltinLabels(params: {
  category?: string
  page?: number
  size?: number
}): Promise<PageResult<Label>> {
  return unwrap(http.get('/builtin-labels', { params }))
}

export async function listCategories(): Promise<BuiltinLabelCategory[]> {
  return unwrap(http.get('/builtin-labels/categories'))
}

export async function setBuiltinLabelActive(id: number, active: boolean): Promise<void> {
  await unwrap(http.put(`/builtin-labels/${id}/active`, null, { params: { active } }))
}

