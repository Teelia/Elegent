import { http, unwrap } from './http'
import type { PageResult } from './types'

/**
 * 数据行
 */
export interface DataRow {
  id: number
  taskId: number
  rowIndex: number
  originalData: Record<string, unknown>
  labelResults: Record<string, unknown>
  /** AI信心度 {"标签名_v1": 0.95} */
  aiConfidence?: Record<string, number>
  /** AI分析原因 {"标签名_v1": "原因..."} */
  aiReasoning?: Record<string, string>
  /** 信心度采纳阈值 */
  confidenceThreshold?: number
  /** 是否需要人工审核 */
  needsReview?: boolean
  isModified: boolean
  processingStatus: string
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 数据行查询参数
 */
export interface ListRowsParams {
  page?: number
  size?: number
  /** 按标签筛选 */
  labelKey?: string
  /** 按结果值筛选（是/否） */
  resultValue?: string
  /** 是否需要审核 */
  needsReview?: boolean
  /** 最小信心度 */
  minConfidence?: number
  /** 最大信心度 */
  maxConfidence?: number
}

/**
 * 获取数据行列表
 */
export async function listRows(taskId: number, params: ListRowsParams = {}): Promise<PageResult<DataRow>> {
  return unwrap(http.get(`/tasks/${taskId}/rows`, { params }))
}

/**
 * 更新单行标签结果
 */
export async function updateRow(taskId: number, rowId: number, labelResults: Record<string, unknown>): Promise<void> {
  await unwrap(http.put(`/tasks/${taskId}/rows/${rowId}`, { labelResults }))
}

/**
 * 批量更新数据行
 */
export async function batchUpdateRows(
  taskId: number,
  items: Array<{ rowId: number; labelResults: Record<string, unknown> }>
): Promise<void> {
  await unwrap(http.put(`/tasks/${taskId}/rows`, { items }))
}

/**
 * 更新单行信心度阈值
 */
export async function updateRowThreshold(taskId: number, rowId: number, confidenceThreshold: number): Promise<void> {
  await unwrap(http.put(`/tasks/${taskId}/rows/${rowId}/threshold`, { confidenceThreshold }))
}

