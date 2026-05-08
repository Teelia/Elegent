import { http, unwrap } from './http'
import type { PageResult } from './types'

// ==================== 类型定义 ====================

/**
 * 数据集状态
 */
export type DatasetStatus = 'uploaded' | 'archived'

/**
 * 列信息
 */
export interface ColumnInfo {
  name: string
  type: string
  nonNullRate?: number
  sampleValues?: string[]
}

/**
 * 数据集
 */
export interface Dataset {
  id: number
  userId: number
  name: string
  originalFilename: string
  storedFilename: string
  fileSize: number
  totalRows: number
  columns: ColumnInfo[]
  status: DatasetStatus
  createdAt: string
  updatedAt?: string
  archivedAt?: string
  // 数据来源类型（数据库导入相关）
  sourceType?: 'file' | 'database'
  externalSourceId?: number
  importQuery?: string
  lastImportTime?: string
  description?: string
  processedRows?: number
  // 关联统计
  taskCount?: number
  labelCount?: number
}

/**
 * 数据集上传响应
 * 注意：后端实际返回 DatasetVO，这里定义兼容两种格式
 */
export interface DatasetUploadResponse {
  // 后端实际返回的字段
  id?: number
  name?: string
  originalFilename?: string
  totalRows: number
  columns: ColumnInfo[]
  // 兼容旧的前端期望字段
  datasetId?: number
  filename?: string
  fileSize?: number
  preview?: Array<Record<string, unknown>>
}

/**
 * 数据行
 */
export interface DataRow {
  id: number
  datasetId: number
  rowIndex: number
  originalData: Record<string, unknown>
  createdAt: string
}

// ==================== API 函数 ====================

/**
 * 上传数据集
 * 注意：大文件上传可能需要较长时间，设置 5 分钟超时
 */
export async function uploadDataset(file: File): Promise<DatasetUploadResponse> {
  const form = new FormData()
  form.append('file', file)
  return unwrap(http.post('/datasets/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300_000 // 5 分钟超时，适应大文件上传和解析
  }))
}

/**
 * 获取数据集列表
 */
export async function listDatasets(params: {
  page?: number
  size?: number
  status?: DatasetStatus
  keyword?: string
}): Promise<PageResult<Dataset>> {
  return unwrap(http.get('/datasets', { params }))
}

/**
 * 获取数据集详情
 */
export async function getDataset(id: number): Promise<Dataset> {
  return unwrap(http.get(`/datasets/${id}`))
}

/**
 * 归档数据集
 */
export async function archiveDataset(id: number): Promise<void> {
  await unwrap(http.post(`/datasets/${id}/archive`))
}

/**
 * 删除数据集
 */
export async function deleteDataset(id: number): Promise<void> {
  await unwrap(http.delete(`/datasets/${id}`))
}

/**
 * 获取数据集的数据行
 *
 * @param datasetId 数据集ID
 * @param params 查询参数
 * @param params.page 页码（默认1）
 * @param params.size 每页大小（默认50）
 * @param params.keyword 搜索关键词（可选，在所有列中模糊搜索）
 */
export async function getDatasetRows(
  datasetId: number,
  params: {
    page?: number
    size?: number
    keyword?: string
  }
): Promise<PageResult<DataRow>> {
  return unwrap(http.get(`/datasets/${datasetId}/rows`, { params }))
}

/**
 * 获取数据集的分析任务列表
 */
export async function getDatasetTasks(datasetId: number): Promise<import('./analysisTasks').AnalysisTask[]> {
  return unwrap(http.get(`/datasets/${datasetId}/tasks`))
}

/**
 * 批量更新数据行
 */
export async function batchUpdateDataRows(
  datasetId: number,
  updates: Array<{
    rowId: number
    originalData: Record<string, any>
  }>
): Promise<void> {
  await unwrap(http.put(`/datasets/${datasetId}/rows/batch`, updates))
}