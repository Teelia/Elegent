import { http, unwrap } from './http'

export interface TaskStatistics {
  taskId: number
  totalRows: number
  processedRows: number
  failedRows: number
  labelStatistics: Record<string, Record<string, number>>
  labelDistributions: Array<{
    labelName: string
    labelValue: string
    count: number
    percentage: number
  }>
}

export interface KeywordCount {
  keyword: string
  count: number
}

export async function getStatistics(taskId: number): Promise<TaskStatistics> {
  return unwrap(http.get(`/tasks/${taskId}/statistics`))
}

export async function getKeywords(taskId: number, params: { labelKey: string; columns: string; top?: number }): Promise<KeywordCount[]> {
  return unwrap(http.get(`/tasks/${taskId}/keywords`, { params }))
}

