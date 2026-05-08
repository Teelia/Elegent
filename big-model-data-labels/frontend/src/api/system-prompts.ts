import { http, unwrap } from './http'

/**
 * 提示词类型
 */
export type PromptType = 'classification' | 'extraction' | 'validation' | 'enhancement'

/**
 * 系统提示词
 */
export interface SystemPrompt {
  id: number
  userId: number
  name: string
  code: string
  promptType: PromptType
  template: string
  variables?: string[]
  isSystemDefault: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 创建/更新提示词请求
 */
export interface CreatePromptRequest {
  name: string
  code: string
  promptType: PromptType
  template: string
  variables?: string[]
  isActive?: boolean
}

/**
 * 获取提示词列表
 */
export async function listSystemPrompts(params?: {
  promptType?: PromptType
}): Promise<SystemPrompt[]> {
  return unwrap(http.get('/system-prompts', { params }))
}

/**
 * 获取提示词详情
 */
export async function getSystemPrompt(id: number): Promise<SystemPrompt> {
  return unwrap(http.get(`/system-prompts/${id}`))
}

/**
 * 获取默认提示词
 */
export async function getDefaultPrompt(promptType: PromptType): Promise<SystemPrompt> {
  return unwrap(http.get(`/system-prompts/default/${promptType}`))
}

/**
 * 创建提示词
 */
export async function createSystemPrompt(payload: CreatePromptRequest): Promise<SystemPrompt> {
  return unwrap(http.post('/system-prompts', payload))
}

/**
 * 更新提示词
 */
export async function updateSystemPrompt(id: number, payload: CreatePromptRequest): Promise<SystemPrompt> {
  return unwrap(http.put(`/system-prompts/${id}`, payload))
}

/**
 * 删除提示词
 */
export async function deleteSystemPrompt(id: number): Promise<void> {
  await unwrap(http.delete(`/system-prompts/${id}`))
}

/**
 * 切换提示词状态
 */
export async function toggleSystemPrompt(id: number): Promise<SystemPrompt> {
  return unwrap(http.patch(`/system-prompts/${id}/toggle`))
}

/**
 * 设置为默认提示词
 */
export async function setAsDefaultPrompt(id: number): Promise<SystemPrompt> {
  return unwrap(http.post(`/system-prompts/${id}/set-default`))
}

/**
 * 提示词类型显示名称映射
 */
export const PromptTypeNames: Record<PromptType, string> = {
  classification: '分类判断',
  extraction: 'LLM提取',
  validation: '规则验证',
  enhancement: '二次强化'
}

/**
 * 常用变量说明
 */
export const VariableDescriptions: Record<string, string> = {
  label_name: '标签名称',
  label_description: '标签描述/规则',
  focus_columns: '关注列',
  extract_fields: '提取字段列表',
  row_data_json: '原始数据JSON',
  preprocessor_result: '预处理结果',
  extracted_numbers: '提取的号码列表',
  initial_result: '初步分析结果',
  initial_confidence: '初步置信度',
  initial_reasoning: '初步推理',
  validation_result: '规则验证结果'
}
