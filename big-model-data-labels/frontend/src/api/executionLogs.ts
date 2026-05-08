import { http, unwrap } from './http'
import type { PageResult } from './types'

// ==================== 类型定义 ====================

/**
 * 日志级别
 */
export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'

/**
 * 任务执行日志
 */
export interface TaskExecutionLog {
  id: number
  analysisTaskId: number
  dataRowId?: number
  labelId?: number
  labelName?: string
  logLevel: LogLevel
  message: string
  aiConfidence?: number
  durationMs?: number
  createdAt: string
  // 关联数据
  rowIndex?: number
}

// ==================== API 函数 ====================

/**
 * 获取任务执行日志
 */
export async function listExecutionLogs(params: {
  analysisTaskId: number
  logLevel?: LogLevel
  dataRowId?: number
  page?: number
  size?: number
}): Promise<PageResult<TaskExecutionLog>> {
  return unwrap(http.get('/execution-logs', { params }))
}

/**
 * 获取最新的执行日志（用于实时显示）
 */
export async function getLatestLogs(analysisTaskId: number, limit: number = 50): Promise<TaskExecutionLog[]> {
  const page = await unwrap(http.get('/execution-logs', { 
    params: { 
      analysisTaskId,
      page: 1,
      size: limit 
    } 
  })) as PageResult<TaskExecutionLog>
  return page.items
}

/**
 * 获取错误日志
 */
export async function getErrorLogs(analysisTaskId: number, params?: {
  page?: number
  size?: number
}): Promise<PageResult<TaskExecutionLog>> {
  return unwrap(http.get('/execution-logs', { 
    params: { 
      analysisTaskId,
      logLevel: 'ERROR',
      ...params
    } 
  }))
}