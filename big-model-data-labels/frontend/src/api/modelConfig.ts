import { http, unwrap } from './http'

/**
 * 模型配置
 */
export interface ModelConfig {
  id?: number | null
  name: string
  provider: string
  providerDisplayName?: string
  baseUrl: string
  model: string
  timeout: number
  temperature: number
  maxTokens: number
  retryTimes: number
  maxConcurrency: number
  currentConcurrency?: number
  isActive: boolean
  isDefault?: boolean
  description?: string
  apiKeyConfigured: boolean
  fromDb: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

/**
 * 创建模型配置请求
 */
export interface CreateModelConfigRequest {
  name: string
  provider: string
  apiKey?: string
  baseUrl: string
  model: string
  timeout?: number
  temperature?: number
  maxTokens?: number
  retryTimes?: number
  maxConcurrency?: number
  isActive?: boolean
  isDefault?: boolean
  description?: string
}

/**
 * 更新模型配置请求
 */
export interface UpdateModelConfigRequest {
  name?: string
  apiKey?: string
  clearApiKey?: boolean
  baseUrl?: string
  model?: string
  timeout?: number
  temperature?: number
  maxTokens?: number
  retryTimes?: number
  maxConcurrency?: number
  isActive?: boolean
  isDefault?: boolean
  description?: string
}

/**
 * 提供商选项
 */
export const PROVIDER_OPTIONS = [
  { value: 'deepseek', label: 'DeepSeek', defaultBaseUrl: 'https://api.deepseek.com/v1', defaultModel: 'deepseek-chat' },
  { value: 'local-deepseek', label: '本地 DeepSeek (自部署)', defaultBaseUrl: 'http://localhost:8000/v1', defaultModel: '/model_70b' },
  { value: 'qwen', label: '通义千问 (Qwen)', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', defaultModel: 'qwen-turbo' },
  { value: 'openai', label: 'OpenAI', defaultBaseUrl: 'https://api.openai.com/v1', defaultModel: 'gpt-3.5-turbo' }
]

// ==================== API 函数 ====================

/**
 * 获取所有模型配置列表
 */
export async function listModelConfigs(): Promise<ModelConfig[]> {
  return unwrap(http.get('/admin/model-configs'))
}

/**
 * 获取激活的模型配置列表
 */
export async function listActiveModelConfigs(): Promise<ModelConfig[]> {
  return unwrap(http.get('/admin/model-configs/active'))
}

/**
 * 获取默认配置
 */
export async function getDefaultModelConfig(): Promise<ModelConfig> {
  return unwrap(http.get('/admin/model-configs/default'))
}

/**
 * 根据ID获取配置
 */
export async function getModelConfigById(id: number): Promise<ModelConfig> {
  return unwrap(http.get(`/admin/model-configs/${id}`))
}

/**
 * 创建模型配置
 */
export async function createModelConfig(payload: CreateModelConfigRequest): Promise<ModelConfig> {
  return unwrap(http.post('/admin/model-configs', payload))
}

/**
 * 更新模型配置
 */
export async function updateModelConfig(id: number, payload: UpdateModelConfigRequest): Promise<ModelConfig> {
  return unwrap(http.put(`/admin/model-configs/${id}`, payload))
}

/**
 * 删除模型配置
 */
export async function deleteModelConfig(id: number): Promise<void> {
  await unwrap(http.delete(`/admin/model-configs/${id}`))
}

/**
 * 设置默认配置
 */
export async function setDefaultModelConfig(id: number): Promise<ModelConfig> {
  return unwrap(http.post(`/admin/model-configs/${id}/set-default`))
}

// ==================== 兼容旧接口 ====================

/**
 * 获取DeepSeek配置（兼容旧接口）
 */
export async function getModelConfig(): Promise<ModelConfig> {
  return unwrap(http.get('/admin/model-configs/deepseek'))
}

/**
 * 更新DeepSeek配置（兼容旧接口）
 */
export async function updateDeepSeekConfig(payload: UpdateModelConfigRequest): Promise<ModelConfig> {
  return unwrap(http.put('/admin/model-configs/deepseek', payload))
}

