import { http, unwrap } from './http'
import type { PageResult } from './types'
import type { Label } from './labels'

export interface FileUploadResponse {
  taskId: number
  filename: string
  fileSize: number
  totalRows: number
  columns: string[]
  preview: Array<Record<string, unknown>>
}

export interface TaskProgress {
  taskId: number
  total: number
  processed: number
  failed: number
  percentage: number
  currentRow: number
  etaSeconds?: number | null
  status: string
}

/**
 * 任务状态类型
 */
export type TaskStatus = 'uploaded' | 'pending' | 'processing' | 'paused' | 'completed' | 'failed' | 'cancelled' | 'archived'

export interface Task {
  id: number
  userId: number
  filename: string
  originalFilename: string
  fileSize: number
  status: TaskStatus
  totalRows: number
  processedRows: number
  failedRows: number
  errorMessage?: string
  labels?: Label[]
  columns?: string[]
  createdAt?: string
  startedAt?: string
  completedAt?: string
  archivedAt?: string
  pausedAt?: string
}

/**
 * 创建临时标签请求
 */
export interface CreateTempLabelRequest {
  name: string
  description?: string
  focusColumns?: string[]
}

/**
 * 数据预览响应
 */
export interface TaskPreview {
  taskId: number
  totalRows: number
  totalColumns: number
  fileSize: number
  columns: Array<{
    index: number
    name: string
    dataType: string
    nonNullRate: number
  }>
  previewRows: Array<Record<string, unknown>>
}

/**
 * 上传文件
 */
export async function uploadTask(file: File): Promise<FileUploadResponse> {
  const form = new FormData()
  form.append('file', file)
  return unwrap(http.post('/tasks/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } }))
}

/**
 * 获取任务列表
 */
export async function listTasks(params: { page?: number; size?: number; status?: string; userId?: number }): Promise<PageResult<Task>> {
  return unwrap(http.get('/tasks', { params }))
}

/**
 * 获取任务详情
 */
export async function getTask(id: number): Promise<Task> {
  return unwrap(http.get(`/tasks/${id}`))
}

/**
 * 配置任务标签（不启动分析）
 */
export async function configureTaskLabels(id: number, labelIds: number[]): Promise<Task> {
  return unwrap(http.post(`/tasks/${id}/labels`, { labelIds }))
}

/**
 * 移除任务标签
 */
export async function removeTaskLabel(taskId: number, labelId: number): Promise<void> {
  await unwrap(http.delete(`/tasks/${taskId}/labels/${labelId}`))
}

/**
 * 启动任务分析
 * 仅允许 pending 状态的任务启动
 */
export async function startTask(
  taskId: number, 
  labelIds: number[], 
  modelConfigId?: number, 
  includeReasoning?: boolean
): Promise<void> {
  return unwrap(http.post(`/tasks/${taskId}/start`, { 
    labelIds,
    modelConfigId,
    includeReasoning
  }))
}

/**
 * 暂停任务
 */
export async function pauseTask(id: number): Promise<Task> {
  return unwrap(http.post(`/tasks/${id}/pause`))
}

/**
 * 继续任务（从暂停状态恢复）
 */
export async function resumeTask(id: number): Promise<Task> {
  return unwrap(http.post(`/tasks/${id}/resume`))
}

/**
 * 重新启动任务（用于completed/failed/cancelled状态）
 */
export async function restartTask(
  taskId: number,
  labelIds: number[],
  modelConfigId?: number,
  includeReasoning?: boolean
): Promise<void> {
  return unwrap(http.post(`/tasks/${taskId}/restart`, {
    labelIds,
    modelConfigId,
    includeReasoning
  }))
}

/**
 * 取消任务
 */
export async function cancelTask(id: number): Promise<void> {
  await unwrap(http.post(`/tasks/${id}/cancel`))
}

/**
 * 发起异步分析（兼容旧接口）
 */
export async function analyzeTask(id: number, labelIds: number[]): Promise<void> {
  await unwrap(http.post(`/tasks/${id}/analyze`, { labelIds }))
}

/**
 * 获取任务进度
 */
export async function getProgress(id: number): Promise<TaskProgress> {
  return unwrap(http.get(`/tasks/${id}/progress`))
}

/**
 * 获取数据预览
 */
export async function getTaskPreview(id: number): Promise<TaskPreview> {
  return unwrap(http.get(`/tasks/${id}/preview`))
}

/**
 * 更新单行信心度阈值
 */
export async function updateRowThreshold(taskId: number, rowId: number, confidenceThreshold: number): Promise<void> {
  await unwrap(http.put(`/tasks/${taskId}/rows/${rowId}/threshold`, { confidenceThreshold }))
}

/**
 * 归档任务
 */
export async function archiveTask(id: number): Promise<void> {
  await unwrap(http.post(`/tasks/${id}/archive`))
}

/**
 * 创建任务临时标签
 */
export async function createTempLabel(taskId: number, data: CreateTempLabelRequest): Promise<Label> {
  return unwrap(http.post(`/tasks/${taskId}/temp-labels`, data))
}

/**
 * 获取任务临时标签列表
 */
export async function getTempLabels(taskId: number): Promise<Label[]> {
  return unwrap(http.get(`/tasks/${taskId}/temp-labels`))
}

/**
 * 将临时标签保存到全局标签库
 */
export async function promoteTempLabel(taskId: number, labelId: number): Promise<Label> {
  return unwrap(http.post(`/tasks/${taskId}/temp-labels/${labelId}/promote`))
}

/**
 * 导出任务结果
 */
export async function exportTask(id: number): Promise<Blob> {
  const resp = await http.get(`/tasks/${id}/export`, { responseType: 'blob' })
  return resp.data as Blob
}

/**
 * 状态显示文本
 */
export const statusLabels: Record<TaskStatus, string> = {
  uploaded: '已上传',
  pending: '待启动',
  processing: '进行中',
  paused: '已暂停',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消',
  archived: '已归档'
}

/**
 * 状态标签类型
 */
export const statusTagTypes: Record<TaskStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  uploaded: 'info',
  pending: 'info',
  processing: 'warning',
  paused: 'warning',
  completed: 'success',
  failed: 'danger',
  cancelled: 'info',
  archived: 'info'
}

