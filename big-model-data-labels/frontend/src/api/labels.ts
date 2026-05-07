import { http, unwrap } from './http'
import type { PageResult } from './types'

/**
 * 标签作用域
 */
export type LabelScope = 'global' | 'dataset' | 'task'

/**
 * 标签类型
 */
export type LabelType = 'classification' | 'extraction' | 'structured_extraction'

/**
 * 预处理模式
 * llm_only: 仅使用 LLM，不使用规则提取器
 * rule_only: 仅使用规则提取器，不调用 LLM
 * rule_then_llm: 规则预处理 + LLM 判断
 */
export type PreprocessingMode = 'llm_only' | 'rule_only' | 'rule_then_llm'

/**
 * 兼容旧的预处理模式值（已弃用）
 * @deprecated
 */
export type LegacyPreprocessingMode = 'none' | 'rule' | 'llm' | 'hybrid'

/**
 * 提取器类型
 */
export type ExtractorType = 'id_card' | 'bank_card' | 'phone' | 'composite'

/**
 * 规则动作类型
 */
export type RuleAction = 'require' | 'exclude' | 'boost'

/**
 * 提取器配置
 */
export interface ExtractorConfig {
  /** 提取器类型 */
  extractorType: ExtractorType
  /** 提取器选项 */
  options?: {
    /** 身份证号选项 */
    include18Digit?: boolean
    include15Digit?: boolean
    includeLoose?: boolean
  }
  /** 复合提取的子提取器列表 */
  extractors?: {
    field: string
    extractorType: ExtractorType
  }[]
}

/**
 * 预处理器配置（当preprocessingMode为rule_only或rule_then_llm时有效）
 */
export interface PreprocessorConfig {
  /** 启用的提取器列表 */
  extractors: string[]
  /** 各提取器的选项配置 */
  extractorOptions?: Record<string, {
    [key: string]: boolean | string | number
  }>
}

/**
 * 强化分析配置
 */
export interface EnhancementConfig {
  /** 触发条件：置信度低于此值时触发 */
  triggerConfidence?: number
  /** 提示词ID（可选） */
  promptId?: number
}

/**
 * 标签
 */
export interface Label {
  id: number
  userId: number
  name: string
  version: number
  description: string
  focusColumns: string[]
  /** 标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取) */
  type: LabelType
  /** 提取字段列表（仅type=extraction或structured_extraction时有效） */
  extractFields?: string[]
  /** 提取器配置（仅type=structured_extraction时有效） */
  extractorConfig?: string
  /** 预处理模式（适用于 classification 和 extraction 类型） */
  preprocessingMode?: PreprocessingMode | LegacyPreprocessingMode
  /** 预处理器配置（当preprocessingMode为rule_only或rule_then_llm时有效） */
  preprocessorConfig?: string
  /** 是否将预处理结果传入 LLM（仅 rule_then_llm 模式有效） */
  includePreprocessorInPrompt?: boolean
  /** 是否启用二次强化分析 */
  enableEnhancement?: boolean
  /** 强化分析配置（仅enableEnhancement=true时有效） */
  enhancementConfig?: string
  /** 内置级别：system / custom */
  builtinLevel?: string
  /** 内置分类 */
  builtinCategory?: string
  isActive: boolean
  scope: LabelScope
  datasetId?: number
  /** 任务ID（仅task作用域使用） */
  taskId?: number
  createdAt: string
  updatedAt: string
}

/**
 * 创建标签请求
 */
export interface CreateLabelRequest {
  name: string
  description: string
  focusColumns?: string[]
  /** 标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取) */
  type?: LabelType
  /** 提取字段列表（仅type=extraction或structured_extraction时需要） */
  extractFields?: string[]
  /** 提取器配置（仅type=structured_extraction时需要） */
  extractorConfig?: string
  /** 预处理模式（适用于 classification 和 extraction 类型） */
  preprocessingMode?: PreprocessingMode
  /** 预处理器配置（当preprocessingMode为rule_only或rule_then_llm时需要） */
  preprocessorConfig?: string
  /** 是否将预处理结果传入 LLM（仅 rule_then_llm 模式有效） */
  includePreprocessorInPrompt?: boolean
  /** 是否启用二次强化分析 */
  enableEnhancement?: boolean
  /** 强化分析配置 */
  enhancementConfig?: string
  scope?: LabelScope
  datasetId?: number
}

/**
 * 获取标签列表
 */
export async function listLabels(params: {
  page?: number
  size?: number
  userId?: number
  scope?: LabelScope
  datasetId?: number
}): Promise<PageResult<Label>> {
  return unwrap(http.get('/labels', { params }))
}

/**
 * 获取活跃标签
 */
export async function activeLabels(params?: {
  userId?: number
  scope?: LabelScope
  datasetId?: number
}): Promise<Label[]> {
  return unwrap(http.get('/labels/active', { params }))
}

/**
 * 获取全局标签
 */
export async function getGlobalLabels(): Promise<Label[]> {
  return activeLabels({ scope: 'global' })
}

/**
 * 获取数据集专属标签
 */
export async function getDatasetLabels(datasetId: number): Promise<Label[]> {
  return activeLabels({ scope: 'dataset', datasetId })
}

/**
 * 获取数据集可用的所有标签（全局 + 专属）
 */
export async function getAvailableLabelsForDataset(datasetId: number): Promise<Label[]> {
  const [globalLabels, datasetLabels] = await Promise.all([
    getGlobalLabels(),
    getDatasetLabels(datasetId)
  ])
  return [...globalLabels, ...datasetLabels]
}

export async function getLabel(id: number): Promise<Label> {
  return unwrap(http.get(`/labels/${id}`))
}

export async function getVersions(id: number): Promise<Label[]> {
  return unwrap(http.get(`/labels/${id}/versions`))
}

export async function createLabel(payload: CreateLabelRequest): Promise<Label> {
  return unwrap(http.post('/labels', payload))
}

export async function updateLabel(id: number, payload: {
  description: string
  focusColumns?: string[]
  extractFields?: string[]
  extractorConfig?: string
  preprocessingMode?: PreprocessingMode
  preprocessorConfig?: string
  includePreprocessorInPrompt?: boolean
  enableEnhancement?: boolean
  enhancementConfig?: string
}): Promise<Label> {
  return unwrap(http.put(`/labels/${id}`, payload))
}

export async function deleteLabel(id: number): Promise<void> {
  await unwrap(http.delete(`/labels/${id}`))
}

