import { http, unwrap } from './http'
import type { PageResult } from './types'
import type { LabelType } from './labels'

// ==================== 类型定义 ====================

/**
 * 标签结果
 */
export interface LabelResult {
  id: number
  dataRowId: number
  analysisTaskId: number
  labelId: number
  labelName: string
  /** 标签类型：classification(分类判断), extraction(信息提取) */
  labelType?: LabelType
  /** 结果值：分类标签为是/否，提取标签为摘要 */
  result: string | null
  /** 提取的数据（仅提取类型标签使用） */
  extractedData?: Record<string, unknown>
  aiConfidence: number | null
  confidenceThreshold: number
  needsReview: boolean
  isModified: boolean
  aiReason?: string
  createdAt: string
  updatedAt?: string
  // 关联的原始数据（可选）
  originalData?: Record<string, unknown>
  rowIndex?: number
}

/**
 * 更新标签结果请求
 */
export interface UpdateLabelResultRequest {
  result?: string
  confidenceThreshold?: number
  /** 更新提取的数据（仅提取类型标签使用） */
  extractedData?: Record<string, unknown>
}

/**
 * 批量更新阈值请求
 */
export interface BatchUpdateThresholdRequest {
  analysisTaskId: number
  labelId?: number
  threshold: number
}

/**
 * 标签结果统计
 */
export interface LabelResultStatistics {
  labelId: number
  labelName: string
  totalCount: number
  yesCount: number
  noCount: number
  hitRate: number
  avgConfidence: number
  needsReviewCount: number
  modifiedCount: number
}

// ==================== API 函数 ====================

/**
 * 获取标签结果列表
 */
export async function listLabelResults(params: {
  analysisTaskId: number
  labelId?: number
  result?: string
  needsReview?: boolean
  isModified?: boolean
  page?: number
  size?: number
}): Promise<PageResult<LabelResult>> {
  // 后端使用 taskId 参数名，result 对应后端的 resultValue
  const { analysisTaskId, result, ...rest } = params
  return unwrap(http.get('/label-results', { params: { taskId: analysisTaskId, resultValue: result, ...rest } }))
}

/**
 * 获取单个标签结果
 */
export async function getLabelResult(id: number): Promise<LabelResult> {
  return unwrap(http.get(`/label-results/${id}`))
}

/**
 * 更新标签结果
 */
export async function updateLabelResult(id: number, request: UpdateLabelResultRequest): Promise<LabelResult> {
  return unwrap(http.put(`/label-results/${id}`, request))
}

/**
 * 批量更新阈值
 */
export async function batchUpdateThreshold(request: BatchUpdateThresholdRequest): Promise<number> {
  return unwrap(http.post('/label-results/batch-threshold', request))
}

/**
 * 获取任务的标签结果统计
 */
export async function getLabelResultStatistics(analysisTaskId: number): Promise<LabelResultStatistics[]> {
  // 后端使用 taskId 参数名
  return unwrap(http.get('/label-results/statistics', { params: { taskId: analysisTaskId } }))
}

/**
 * 获取某行数据的所有标签结果
 */
export async function getRowLabelResults(dataRowId: number, analysisTaskId: number): Promise<LabelResult[]> {
  // 后端使用 taskId 参数名
  const page = await unwrap(http.get('/label-results', {
    params: {
      dataRowId,
      taskId: analysisTaskId,
      page: 1,
      size: 100
    }
  })) as PageResult<LabelResult>
  return page.items
}

/**
 * 按数据行分页获取标签结果（推荐使用）
 * 解决原接口按LabelResult分页导致数据行不完整的问题
 */
export async function listLabelResultsByRow(params: {
  analysisTaskId: number
  page?: number
  size?: number
  resultFilter?: string
  onlyNeedsReview?: boolean
}): Promise<PageResult<{
  rowId: number
  rowIndex: number
  originalData: Record<string, unknown>
  labelResults: Record<string, {
    result: string | null
    confidence: number | null
    needsReview: boolean
    isModified: boolean
    aiReason?: string
  }>
}>> {
  const { analysisTaskId, ...rest } = params
  return unwrap(http.get('/label-results/by-row', { params: { taskId: analysisTaskId, ...rest } }))
}