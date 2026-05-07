import { http, unwrap } from './http'
import type { PageResult } from './types'

// ==================== 类型定义 ====================

/**
 * 分析任务状态
 */
export type AnalysisTaskStatus = 'pending' | 'processing' | 'paused' | 'completed' | 'failed' | 'cancelled'

/**
 * 任务关联的标签快照
 */
export interface TaskLabel {
  id: number
  analysisTaskId: number
  labelId: number
  labelName: string
  labelVersion: number
  labelDescription: string
}

/**
 * 分析任务
 */
export interface AnalysisTask {
  id: number
  datasetId: number
  userId: number
  name: string
  status: AnalysisTaskStatus
  totalRows: number
  processedRows: number
  successRows: number
  failedRows: number
  defaultConfidenceThreshold: number
  errorMessage?: string
  createdAt: string
  startedAt?: string
  pausedAt?: string
  completedAt?: string
  // 关联数据
  labels?: TaskLabel[]
  datasetName?: string
}

/**
 * 正在分析的标签信息
 */
export interface AnalyzingLabel {
  labelId: number
  labelName: string
  labelVersion: number
  labelDescription: string
  processedRows: number
  hitCount: number
  hitRate: number
  isProcessing: boolean
  processingStatus: string
}

/**
 * 分析日志条目
 */
export interface AnalysisLogEntry {
  id: number
  dataRowId?: number
  rowIndex?: number
  labelKey?: string
  logLevel: string
  message: string
  confidence?: number
  durationMs?: number
  createdAt: string
  timeDisplay: string
}

/**
 * 当前处理信息
 */
export interface CurrentProcessingInfo {
  currentRowIndex?: number
  currentLabelName?: string
  processingPhase?: string
  processingPhaseDisplay?: string
}

/**
 * 分析过程详情
 */
export interface AnalysisProcess {
  taskId: number
  taskName: string
  status: AnalysisTaskStatus
  statusDisplay: string
  totalRows: number
  processedRows: number
  successRows: number
  failedRows: number
  progressPercent: number
  estimatedSecondsRemaining?: number
  analyzingLabels: AnalyzingLabel[]
  recentLogs: AnalysisLogEntry[]
  currentProcessing?: CurrentProcessingInfo
  startedAt?: string
  lastUpdatedAt: string
}

/**
 * 任务进度
 */
export interface AnalysisTaskProgress {
  taskId: number
  status: AnalysisTaskStatus
  total: number
  processed: number
  success: number
  failed: number
  percentage: number
  etaSeconds?: number
  // 分标签进度
  labelProgress?: Array<{
    labelId: number
    labelName: string
    processed: number
    total: number
    percentage: number
  }>
}

/**
 * 创建分析任务请求
 */
export interface CreateAnalysisTaskRequest {
  datasetId: number
  name: string
  labelIds: number[]
  defaultConfidenceThreshold?: number
  autoStart?: boolean
  /** 指定的模型配置ID（可选，不指定则使用默认配置） */
  modelConfigId?: number
  /** 并发处理数量（1-10，默认1） */
  concurrency?: number
}

/**
 * 任务统计
 */
export interface TaskStatistics {
  taskId: number
  totalRows: number
  processedRows: number
  // 按标签统计
  labelStats: Array<{
    labelId: number
    labelName: string
    yesCount: number
    noCount: number
    hitRate: number
    avgConfidence: number
    needsReviewCount: number
  }>
}

// ==================== API 函数 ====================

/**
 * 创建分析任务
 */
export async function createAnalysisTask(request: CreateAnalysisTaskRequest): Promise<AnalysisTask> {
  return unwrap(http.post('/analysis-tasks', request))
}

/**
 * 获取分析任务列表
 * 如果指定datasetId，则返回该数据集的任务；否则返回当前用户的所有任务
 */
export async function listAnalysisTasks(params: {
  page?: number
  size?: number
  datasetId?: number
  status?: AnalysisTaskStatus | string
}): Promise<PageResult<AnalysisTask>> {
  // 过滤掉空值参数
  const filteredParams: Record<string, any> = {}
  if (params.page) filteredParams.page = params.page
  if (params.size) filteredParams.size = params.size
  if (params.datasetId) filteredParams.datasetId = params.datasetId
  if (params.status) filteredParams.status = params.status
  
  return unwrap(http.get('/analysis-tasks', { params: filteredParams }))
}

/**
 * 获取分析任务详情
 */
export async function getAnalysisTask(id: number): Promise<AnalysisTask> {
  return unwrap(http.get(`/analysis-tasks/${id}`))
}

/**
 * 启动分析任务
 */
export async function startAnalysisTask(id: number): Promise<void> {
  await unwrap(http.post(`/analysis-tasks/${id}/start`))
}

/**
 * 暂停分析任务
 */
export async function pauseAnalysisTask(id: number): Promise<void> {
  await unwrap(http.post(`/analysis-tasks/${id}/pause`))
}

/**
 * 恢复分析任务
 */
export async function resumeAnalysisTask(id: number): Promise<void> {
  await unwrap(http.post(`/analysis-tasks/${id}/resume`))
}

/**
 * 取消分析任务
 */
export async function cancelAnalysisTask(id: number): Promise<void> {
  await unwrap(http.post(`/analysis-tasks/${id}/cancel`))
}

/**
 * 重试失败的行
 */
export async function retryFailedRows(id: number): Promise<void> {
  await unwrap(http.post(`/analysis-tasks/${id}/retry-failed`))
}

/**
 * 获取任务进度
 */
export async function getAnalysisTaskProgress(id: number): Promise<AnalysisTaskProgress> {
  return unwrap(http.get(`/analysis-tasks/${id}/progress`))
}

/**
 * 获取任务统计
 */
export async function getAnalysisTaskStatistics(id: number): Promise<TaskStatistics> {
  return unwrap(http.get(`/analysis-tasks/${id}/statistics`))
}

/**
 * 导出任务结果
 * @param id 任务ID
 * @param includeReasoning 是否包含判断依据（合并到结果单元格中）
 */
export async function exportAnalysisTask(id: number, includeReasoning: boolean = false): Promise<Blob> {
  const resp = await http.get(`/analysis-tasks/${id}/export`, {
    params: { includeReasoning },
    responseType: 'blob'
  })
  return resp.data as Blob
}

/**
 * 获取分析过程详情（用于实时展示分析进度和与大模型对话过程）
 */
export async function getAnalysisProcess(id: number): Promise<AnalysisProcess> {
  return unwrap(http.get(`/analysis-tasks/${id}/process`))
}

/**
 * 获取增量日志（用于实时更新）
 */
export async function getLogsSince(id: number, since: string, limit: number = 100): Promise<AnalysisLogEntry[]> {
  return unwrap(http.get(`/analysis-tasks/${id}/logs/since`, {
    params: { since, limit }
  }))
}