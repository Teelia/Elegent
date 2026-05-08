import { http, unwrap } from './http'

/**
 * 提取器正则规则
 */
export interface ExtractorPattern {
  id?: number
  name: string
  pattern: string
  description?: string
  priority: number
  confidence: number
  validationType?: string
  validationConfig?: string
  isActive: boolean
  sortOrder: number
}

/**
 * 提取器选项配置
 */
export interface ExtractorOption {
  id?: number
  optionKey: string
  optionName: string
  optionType: 'boolean' | 'string' | 'number' | 'select'
  defaultValue?: string
  description?: string
  selectOptions?: string
  sortOrder: number
}

/**
 * 提取器配置
 */
export interface ExtractorConfig {
  id: number
  userId: number
  name: string
  code: string
  description?: string
  category: 'builtin' | 'custom'
  isActive: boolean
  isSystem: boolean
  createdAt: string
  updatedAt: string
  patterns: ExtractorPattern[]
  options: ExtractorOption[]
}

/**
 * 创建提取器请求
 */
export interface CreateExtractorRequest {
  name: string
  code: string
  description?: string
  patterns: Omit<ExtractorPattern, 'id'>[]
  options?: Omit<ExtractorOption, 'id'>[]
}

/**
 * 更新提取器请求
 */
export interface UpdateExtractorRequest {
  name?: string
  description?: string
  isActive?: boolean
  patterns?: Omit<ExtractorPattern, 'id'>[]
  options?: Omit<ExtractorOption, 'id'>[]
}

/**
 * 获取所有激活的提取器
 */
export async function listExtractors(): Promise<ExtractorConfig[]> {
  return unwrap(http.get('/extractors'))
}

/**
 * 获取所有内置提取器
 */
export async function listBuiltinExtractors(): Promise<ExtractorConfig[]> {
  return unwrap(http.get('/extractors/builtin'))
}

/**
 * 获取所有自定义提取器
 */
export async function listCustomExtractors(): Promise<ExtractorConfig[]> {
  return unwrap(http.get('/extractors/custom'))
}

/**
 * 根据ID获取提取器详情
 */
export async function getExtractor(id: number): Promise<ExtractorConfig> {
  return unwrap(http.get(`/extractors/${id}`))
}

/**
 * 根据代码获取提取器详情
 */
export async function getExtractorByCode(code: string): Promise<ExtractorConfig> {
  return unwrap(http.get(`/extractors/code/${code}`))
}

/**
 * 创建提取器
 */
export async function createExtractor(request: CreateExtractorRequest): Promise<ExtractorConfig> {
  return unwrap(http.post('/extractors', request))
}

/**
 * 更新提取器
 */
export async function updateExtractor(id: number, request: UpdateExtractorRequest): Promise<ExtractorConfig> {
  return unwrap(http.put(`/extractors/${id}`, request))
}

/**
 * 删除提取器
 */
export async function deleteExtractor(id: number): Promise<void> {
  await unwrap(http.delete(`/extractors/${id}`))
}

/**
 * AI生成提取器请求
 */
export interface AiGenerateExtractorRequest {
  mode: 'description' | 'samples'
  extractorName: string
  description?: string
  samples?: string
  needValidation?: boolean
}

/**
 * AI生成的正则规则建议
 */
export interface AiPatternSuggestion {
  name: string
  pattern: string
  description: string
  priority: number
  confidence: number
  validationType: string
  example: string
  negativeExample: string
}

/**
 * AI生成提取器响应
 */
export interface AiGenerateExtractorResponse {
  suggestedCode: string
  description: string
  patterns: AiPatternSuggestion[]
  explanation: string
}

/**
 * AI辅助生成提取器
 */
export async function aiGenerateExtractor(request: AiGenerateExtractorRequest): Promise<AiGenerateExtractorResponse> {
  return unwrap(http.post('/extractors/ai-generate', request))
}