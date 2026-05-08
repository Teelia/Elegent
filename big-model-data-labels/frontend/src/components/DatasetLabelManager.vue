<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, InfoFilled } from '@element-plus/icons-vue'
import type { CreateLabelRequest, ExtractorConfig, ExtractorType, Label, PreprocessingMode } from '../api/labels'
import * as labelsApi from '../api/labels'
import * as extractorsApi from '../api/extractors'
import type { ExtractorConfig as ExtractorConfigFull } from '../api/extractors'

type LabelTemplateId =
  | ''
  | 'id_invalid_length_exists'
  | 'id_invalid_length_extract'
  | 'id_invalid_strict_exists'
  | 'id_invalid_strict_extract'
  | 'id_invalid_digits_only_exists'
  | 'id_invalid_digits_only_extract'
type NumberIntentEntity = 'phone' | 'bank_card' | 'id_card'
type NumberIntentTask = 'exists' | 'extract' | 'invalid' | 'masked' | 'invalid_length_masked'

const props = defineProps<{
  datasetId: number
  columns: string[]
}>()

const emit = defineEmits<{
  (e: 'update'): void
}>()

// ========= 状态 =========
const globalLabels = ref<Label[]>([])
const datasetLabels = ref<Label[]>([])
const loading = ref(false)

const builtinExtractors = ref<ExtractorConfigFull[]>([])
const extractorsLoading = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTab = ref<'template' | 'basic' | 'advanced'>('template')
const advancedPanels = ref<string[]>(['preprocess'])
const editingLabel = ref<Label | null>(null)
const submitting = ref(false)
const labelFormRef = ref<any>(null)

const labelForm = ref<CreateLabelRequest & {
  templateId?: LabelTemplateId
  extractorType?: ExtractorType
  include18Digit?: boolean
  include15Digit?: boolean
  includeLoose?: boolean
  compositeExtractors?: string[]
  extractFieldsText?: string
  focusColumnsText?: string
  preprocessingMode?: PreprocessingMode
  includePreprocessorInPrompt?: boolean
  preprocessorExtractors?: string[]
  preprocessorOptions?: Record<string, any>
  numberIntentEnabled?: boolean
  numberIntentEntity?: NumberIntentEntity
  numberIntentTask?: NumberIntentTask
  numberIntentInclude?: Array<'valid' | 'invalid' | 'masked'>
  numberIntentRequireKeywordForInvalidBank?: boolean
  numberIntentDefaultMaskedOutput?: boolean
  numberIntentIdChecksumInvalidIsInvalid?: boolean
  numberIntentId18XIsInvalid?: boolean
  preprocessorConfigMode?: 'form' | 'json'
  preprocessorConfigText?: string
  enableEnhancement?: boolean
  enhancementTriggerConfidence?: number
  enhancementPromptId?: number | null
}>({
  name: '',
  type: 'classification',
  description: '',
  focusColumns: [],
  extractFields: [],
  scope: 'dataset',
  datasetId: props.datasetId,
  templateId: '',
  extractorType: 'id_card',
  include18Digit: true,
  include15Digit: true,
  includeLoose: true,
  compositeExtractors: ['id_card'],
  extractFieldsText: '',
  focusColumnsText: '',
  preprocessingMode: 'llm_only',
  includePreprocessorInPrompt: true,
  preprocessorExtractors: [],
  preprocessorOptions: {} as any,
  numberIntentEnabled: false,
  numberIntentEntity: 'phone',
  numberIntentTask: 'exists',
  numberIntentInclude: ['valid', 'invalid', 'masked'],
  numberIntentRequireKeywordForInvalidBank: true,
  numberIntentDefaultMaskedOutput: false,
  numberIntentIdChecksumInvalidIsInvalid: false,
  numberIntentId18XIsInvalid: false,
  preprocessorConfigMode: 'form',
  preprocessorConfigText: '',
  enableEnhancement: false,
  enhancementTriggerConfidence: 70,
  enhancementPromptId: null,
})

const formRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 50, message: '标签名称不能超过50个字符', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请输入标签规则描述', trigger: 'blur' },
    { max: 1000, message: '标签规则描述不能超过1000个字符', trigger: 'blur' },
  ],
}

const isStructuredExtraction = computed(() => labelForm.value.type === 'structured_extraction')
const isExtraction = computed(() => labelForm.value.type === 'extraction')
const isIdInvalidTemplate = computed(() => !!labelForm.value.templateId && labelForm.value.templateId.startsWith('id_invalid_'))
const idInvalidTemplateHint = computed(() => {
  switch (labelForm.value.templateId) {
    case 'id_invalid_length_exists':
    case 'id_invalid_length_extract':
      return '模板固定：身份证-无效（仅长度错误），不包含校验位错误与遮挡号。'
    case 'id_invalid_strict_exists':
    case 'id_invalid_strict_extract':
      return '模板固定：身份证-无效（长度错误 + 校验位不通过），不包含遮挡号；18位末位X且校验正确视为有效。'
    case 'id_invalid_digits_only_exists':
    case 'id_invalid_digits_only_extract':
      return '模板固定：身份证-无效（长度错误 + 校验位不通过 + 末位X视为不合格），不包含遮挡号与15位。'
    default:
      return ''
  }
})
const showPreprocessingConfig = computed(() => {
  return labelForm.value.preprocessingMode === 'rule_only' ||
    labelForm.value.preprocessingMode === 'rule_then_llm'
})
const showIncludePreprocessorOption = computed(() => labelForm.value.preprocessingMode === 'rule_then_llm')
const showEnhancementConfig = computed(() => {
  return labelForm.value.preprocessingMode === 'llm_only' ||
    labelForm.value.preprocessingMode === 'rule_then_llm'
})

const templateOptions: Array<{
  id: Exclude<LabelTemplateId, ''>
  title: string
  desc: string
  type: 'classification' | 'extraction'
}> = [
  {
    id: 'id_invalid_length_exists',
    title: '错误身份证号（长度错）- 是否存在',
    desc: '仅基于规则证据判断是否出现“长度错误的身份证号”。不包含15位；不包含18位校验位不通过；不包含遮挡号。',
    type: 'classification',
  },
  {
    id: 'id_invalid_length_extract',
    title: '错误身份证号（长度错）- 提取',
    desc: '仅提取“长度错误的身份证号”（默认明文），并返回可审计证据用于复核。',
    type: 'extraction',
  },
  {
    id: 'id_invalid_strict_exists',
    title: '错误身份证号（长度/校验）- 是否存在',
    desc: '仅基于规则证据判断是否出现“身份证号不合格”。包含：长度错误、18位校验位不通过。不包含15位；不包含遮挡号；18位末位X且校验位正确视为有效。',
    type: 'classification',
  },
  {
    id: 'id_invalid_strict_extract',
    title: '错误身份证号（长度/校验）- 提取',
    desc: '仅提取“身份证号不合格”（默认明文）。包含：长度错误、18位校验位不通过；并返回可审计证据用于复核。',
    type: 'extraction',
  },
  {
    id: 'id_invalid_digits_only_exists',
    title: '错误身份证号（长度/校验/末位X）- 是否存在',
    desc: '仅基于规则证据判断是否出现“身份证号不合格（仅允许数字）”。包含：长度错误、18位校验位不通过、18位末位X（即便校验正确也视为不合格）。不包含15位；不包含遮挡号。',
    type: 'classification',
  },
  {
    id: 'id_invalid_digits_only_extract',
    title: '错误身份证号（长度/校验/末位X）- 提取',
    desc: '仅提取“身份证号不合格（仅允许数字）”（默认明文）。包含：长度错误、18位校验位不通过、18位末位X；并返回可审计证据用于复核。',
    type: 'extraction',
  },
]

function parseCommaSeparated(text: string | undefined): string[] {
  if (!text) return []
  return text
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function getTypeLabel(type: string | undefined) {
  if (type === 'extraction') return 'LLM通用提取'
  if (type === 'structured_extraction') return '结构化号码提取'
  return '分类判断'
}

function getTypeTagType(type: string | undefined) {
  if (type === 'extraction') return 'warning'
  if (type === 'structured_extraction') return 'success'
  return 'info'
}

function applyTemplate(id: Exclude<LabelTemplateId, ''>) {
  const tpl = templateOptions.find(t => t.id === id)
  if (!tpl) return

  labelForm.value.templateId = id
  labelForm.value.type = tpl.type
  // 根据标签类型设置预处理模式：
  // - classification（判断是否存在）：使用 rule_only（纯规则，高效）
  // - extraction（提取具体内容）：使用 rule_then_llm（规则预处理 + LLM 提取）
  labelForm.value.preprocessingMode = tpl.type === 'extraction' ? 'rule_then_llm' : 'rule_only'
  labelForm.value.preprocessorConfigMode = 'form'
  labelForm.value.preprocessorExtractors = []
  labelForm.value.preprocessorOptions = {}

  labelForm.value.numberIntentEnabled = true
  labelForm.value.numberIntentEntity = 'id_card'
  // 存在/提取都使用 invalid 任务，后端按口径仅输出 ID_INVALID_LENGTH
  labelForm.value.numberIntentTask = 'invalid'
  labelForm.value.numberIntentInclude = ['invalid']
  labelForm.value.numberIntentDefaultMaskedOutput = false
  labelForm.value.numberIntentIdChecksumInvalidIsInvalid = false
  labelForm.value.numberIntentId18XIsInvalid = false

  // 模板差异：长度错 / 长度+校验 / 长度+校验+末位X（仅允许数字）
  if (id === 'id_invalid_strict_exists' || id === 'id_invalid_strict_extract') {
    labelForm.value.numberIntentIdChecksumInvalidIsInvalid = true
  }
  if (id === 'id_invalid_digits_only_exists' || id === 'id_invalid_digits_only_extract') {
    labelForm.value.numberIntentIdChecksumInvalidIsInvalid = true
    labelForm.value.numberIntentId18XIsInvalid = true
  }

  if (!labelForm.value.name || labelForm.value.name.trim().length === 0) {
    labelForm.value.name = tpl.title
  }
  if (!labelForm.value.description || labelForm.value.description.trim().length === 0) {
    labelForm.value.description = tpl.desc
  }

  dialogTab.value = 'basic'
}

function clearTemplate() {
  labelForm.value.templateId = ''
}

// ========== 结构化提取配置（labels.extractor_config） ==========
function parseExtractorConfig(configStr: string) {
  try {
    const config: ExtractorConfig = JSON.parse(configStr)
    labelForm.value.extractorType = config.extractorType

    if (config.options) {
      labelForm.value.include18Digit = config.options.include18Digit ?? true
      labelForm.value.include15Digit = config.options.include15Digit ?? true
      labelForm.value.includeLoose = config.options.includeLoose ?? true
    }

    if (config.extractors) {
      labelForm.value.compositeExtractors = config.extractors.map(e => e.extractorType)
    }
  } catch (e) {
    console.warn('解析提取器配置失败', e)
  }
}

function getExtractorField(extractorType: string): string {
  if (extractorType === 'id_card') return '身份证号'
  if (extractorType === 'bank_card') return '银行卡号'
  if (extractorType === 'phone') return '手机号'
  return '未知'
}

function buildExtractorConfig(): string {
  if (labelForm.value.type !== 'structured_extraction') {
    return ''
  }

  const config: ExtractorConfig = {
    extractorType: labelForm.value.extractorType!,
  }

  if (labelForm.value.extractorType === 'id_card') {
    config.options = {
      include18Digit: labelForm.value.include18Digit,
      include15Digit: labelForm.value.include15Digit,
      includeLoose: labelForm.value.includeLoose,
    }
  } else if (labelForm.value.extractorType === 'composite') {
    config.extractors = (labelForm.value.compositeExtractors || []).map(extractorType => ({
      field: getExtractorField(extractorType),
      extractorType: extractorType as ExtractorType,
    }))
  }

  return JSON.stringify(config)
}

// ========== 预处理器配置（labels.preprocessor_config） ==========
function parsePreprocessorConfig(configStr: string | null | undefined) {
  if (!configStr) return
  try {
    const config = JSON.parse(configStr)

    // JSON模式：保留原始内容，方便回退/比对
    labelForm.value.preprocessorConfigText = configStr

    // 识别模板来源（仅用于UI标识，不影响后端）
    if (config._meta?.template) {
      labelForm.value.templateId = config._meta.template as LabelTemplateId
    }

    labelForm.value.preprocessorExtractors = config.extractors || []

    if (config.number_intent) {
      labelForm.value.numberIntentEnabled = true
      labelForm.value.numberIntentEntity = (config.number_intent.entity || 'phone') as NumberIntentEntity
      labelForm.value.numberIntentTask = (config.number_intent.task || 'exists') as NumberIntentTask
      labelForm.value.numberIntentInclude = (config.number_intent.include || ['valid', 'invalid', 'masked']) as any
      const policy = config.number_intent.policy || {}
      labelForm.value.numberIntentRequireKeywordForInvalidBank = policy.require_keyword_for_invalid_bank ?? true
      labelForm.value.numberIntentDefaultMaskedOutput = policy.default_masked_output ?? false
      labelForm.value.numberIntentIdChecksumInvalidIsInvalid = policy.id_checksum_invalid_is_invalid ?? false
      labelForm.value.numberIntentId18XIsInvalid = policy.id18_x_is_invalid ?? false
    } else {
      labelForm.value.numberIntentEnabled = false
    }

    // 动态提取器选项
    if (config.extractorOptions) {
      labelForm.value.preprocessorOptions = {}
      for (const [extractorCode, options] of Object.entries(config.extractorOptions || {})) {
        for (const [key, value] of Object.entries(options as any)) {
          const fullKey = `${extractorCode}.${key}`
          ;(labelForm.value.preprocessorOptions as any)[fullKey] = value
        }
      }
    }
  } catch (e) {
    console.warn('解析预处理器配置失败', e)
  }
}

function buildPreprocessorConfigFromForm(): string {
  const hasExtractors = !!labelForm.value.preprocessorExtractors?.length
  const hasNumberIntent = !!labelForm.value.numberIntentEnabled
  if (!hasExtractors && !hasNumberIntent) return ''

  const config: any = {
    extractors: labelForm.value.preprocessorExtractors || [],
    extractorOptions: {},
  }

  if (labelForm.value.templateId) {
    config._meta = { template: labelForm.value.templateId }
  }

  if (labelForm.value.numberIntentEnabled) {
    config.number_intent = {
      entity: labelForm.value.numberIntentEntity || 'phone',
      task: labelForm.value.numberIntentTask || 'exists',
      include: labelForm.value.numberIntentInclude || ['valid', 'invalid', 'masked'],
      policy: {
        id15_is_valid: true,
        default_masked_output: labelForm.value.numberIntentDefaultMaskedOutput ?? false,
        require_keyword_for_invalid_bank: labelForm.value.numberIntentRequireKeywordForInvalidBank ?? true,
        id_checksum_invalid_is_invalid: labelForm.value.numberIntentIdChecksumInvalidIsInvalid ?? false,
        id18_x_is_invalid: labelForm.value.numberIntentId18XIsInvalid ?? false,
      },
    }
  }

  const dynamicOptions = labelForm.value.preprocessorOptions || {}
  for (const extractorCode of (labelForm.value.preprocessorExtractors || [])) {
    const extractor = builtinExtractors.value.find(e => e.code === extractorCode)
    if (extractor && extractor.options?.length > 0) {
      config.extractorOptions[extractorCode] = {}
      for (const option of extractor.options) {
        const key = `${extractorCode}.${option.optionKey}`
        const value = (dynamicOptions as any)[key]
        if (value !== undefined) {
          config.extractorOptions[extractorCode][option.optionKey] = value
        }
      }
    }
  }

  return JSON.stringify(config)
}

function buildPreprocessorConfig(): string {
  if (!labelForm.value.preprocessingMode || labelForm.value.preprocessingMode === 'llm_only') return ''
  if (labelForm.value.preprocessorConfigMode === 'json') {
    return (labelForm.value.preprocessorConfigText || '').trim()
  }
  return buildPreprocessorConfigFromForm()
}

function fillPreprocessorConfigTextFromForm() {
  labelForm.value.preprocessorConfigText = buildPreprocessorConfigFromForm()
}

// ========== 强化分析配置（labels.enhancement_config） ==========
function buildEnhancementConfig(): string {
  if (!labelForm.value.enableEnhancement) return ''
  const config: any = {
    triggerConfidence: labelForm.value.enhancementTriggerConfidence || 70,
  }
  if (labelForm.value.enhancementPromptId) {
    config.promptId = labelForm.value.enhancementPromptId
  }
  return JSON.stringify(config)
}

function parseEnhancementConfig(configStr: string | null | undefined) {
  if (!configStr) return
  try {
    const config = JSON.parse(configStr)
    labelForm.value.enableEnhancement = true
    labelForm.value.enhancementTriggerConfidence = config.triggerConfidence || 70
    labelForm.value.enhancementPromptId = config.promptId || null
  } catch (e) {
    console.warn('解析强化配置失败', e)
  }
}

// ========== 列表加载 ==========
async function loadLabels() {
  loading.value = true
  try {
    const [global, dataset] = await Promise.all([
      labelsApi.getGlobalLabels(),
      labelsApi.getDatasetLabels(props.datasetId),
    ])
    globalLabels.value = global
    datasetLabels.value = dataset
  } catch (e: any) {
    ElMessage.error(e?.message || '加载标签失败')
  } finally {
    loading.value = false
  }
}

async function loadExtractors() {
  extractorsLoading.value = true
  try {
    builtinExtractors.value = await extractorsApi.listBuiltinExtractors()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载提取器失败')
  } finally {
    extractorsLoading.value = false
  }
}

function resetFormForCreate() {
  labelForm.value = {
    name: '',
    type: 'classification',
    description: '',
    focusColumns: [],
    extractFields: [],
    scope: 'dataset',
    datasetId: props.datasetId,
    templateId: '',
    extractorType: 'id_card',
    include18Digit: true,
    include15Digit: true,
    includeLoose: true,
    compositeExtractors: ['id_card'],
    extractFieldsText: '',
    focusColumnsText: '',
    preprocessingMode: 'llm_only',
    includePreprocessorInPrompt: true,
    preprocessorExtractors: [],
    preprocessorOptions: {} as any,
    numberIntentEnabled: false,
    numberIntentEntity: 'phone',
    numberIntentTask: 'exists',
    numberIntentInclude: ['valid', 'invalid', 'masked'],
    numberIntentRequireKeywordForInvalidBank: true,
    numberIntentDefaultMaskedOutput: false,
    preprocessorConfigMode: 'form',
    preprocessorConfigText: '',
    enableEnhancement: false,
    enhancementTriggerConfidence: 70,
    enhancementPromptId: null,
  }
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingLabel.value = null
  resetFormForCreate()
  dialogTab.value = 'template'
  advancedPanels.value = ['preprocess']
  dialogVisible.value = true
}

function openEditDialog(label: Label) {
  dialogMode.value = 'edit'
  editingLabel.value = label
  resetFormForCreate()

  labelForm.value.name = label.name
  labelForm.value.type = label.type || 'classification'
  labelForm.value.description = label.description
  labelForm.value.extractFieldsText = (label.extractFields || []).join(',')
  labelForm.value.focusColumnsText = (label.focusColumns || []).join(',')

  labelForm.value.preprocessingMode = (label as any).preprocessingMode || 'llm_only'
  labelForm.value.includePreprocessorInPrompt = (label as any).includePreprocessorInPrompt ?? true
  labelForm.value.preprocessorConfigMode = 'form'
  labelForm.value.preprocessorConfigText = (label as any).preprocessorConfig || ''
  labelForm.value.enableEnhancement = (label as any).enableEnhancement || false

  if (label.extractorConfig) {
    parseExtractorConfig(label.extractorConfig)
  }
  if ((label as any).preprocessorConfig) {
    parsePreprocessorConfig((label as any).preprocessorConfig)
  }
  if ((label as any).enhancementConfig) {
    parseEnhancementConfig((label as any).enhancementConfig)
  }

  dialogTab.value = 'basic'
  advancedPanels.value = ['preprocess']
  dialogVisible.value = true
}

async function submitForm() {
  if (!labelFormRef.value) return

  try {
    await labelFormRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const focusColumns = parseCommaSeparated(labelForm.value.focusColumnsText)
    const extractFields = parseCommaSeparated(labelForm.value.extractFieldsText)
    const preprocessorConfig = buildPreprocessorConfig()
    const enhancementConfig = buildEnhancementConfig()

    if (dialogMode.value === 'create') {
      await labelsApi.createLabel({
        name: labelForm.value.name,
        type: labelForm.value.type,
        description: labelForm.value.description,
        focusColumns,
        extractFields: (labelForm.value.type === 'extraction' || labelForm.value.type === 'structured_extraction')
          ? extractFields
          : undefined,
        extractorConfig: labelForm.value.type === 'structured_extraction' ? buildExtractorConfig() : undefined,
        preprocessingMode: labelForm.value.preprocessingMode,
        preprocessorConfig: preprocessorConfig || undefined,
        includePreprocessorInPrompt: labelForm.value.includePreprocessorInPrompt,
        enableEnhancement: labelForm.value.enableEnhancement,
        enhancementConfig: enhancementConfig || undefined,
        scope: 'dataset',
        datasetId: props.datasetId,
      })
      ElMessage.success('标签创建成功')
    } else if (editingLabel.value) {
      await labelsApi.updateLabel(editingLabel.value.id, {
        description: labelForm.value.description,
        focusColumns,
        extractFields: (editingLabel.value.type === 'extraction' || editingLabel.value.type === 'structured_extraction')
          ? extractFields
          : undefined,
        extractorConfig: editingLabel.value.type === 'structured_extraction' ? buildExtractorConfig() : undefined,
        preprocessingMode: labelForm.value.preprocessingMode,
        preprocessorConfig: preprocessorConfig || undefined,
        includePreprocessorInPrompt: labelForm.value.includePreprocessorInPrompt,
        enableEnhancement: labelForm.value.enableEnhancement,
        enhancementConfig: enhancementConfig || undefined,
      })
      ElMessage.success('标签更新成功')
    }

    dialogVisible.value = false
    await loadLabels()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function deleteLabel(label: Label) {
  try {
    await ElMessageBox.confirm(
      `确定要删除标签 \"${label.name}\" 吗？删除后使用该标签的任务将无法正常显示。`,
      '确认删除',
      { type: 'warning' },
    )
    await labelsApi.deleteLabel(label.id)
    ElMessage.success('标签已删除')
    await loadLabels()
    emit('update')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

watch(() => props.datasetId, () => {
  if (props.datasetId) {
    loadLabels()
    loadExtractors()
  }
}, { immediate: true })
</script>

<template>
  <div class="dataset-label-manager">
    <div class="section-header">
      <div class="section-title">
        <span>标签配置</span>
        <el-tooltip content="数据集标签仅在当前数据集中使用；全局标签可跨数据集复用" placement="top">
          <el-icon class="info-icon"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建标签</el-button>
    </div>

    <div class="labels-section" v-loading="loading">
      <el-card class="group-card" shadow="never">
        <template #header>
          <div class="group-header">
            <span>数据集标签</span>
            <el-tag type="info" size="small">{{ datasetLabels.length }}</el-tag>
          </div>
        </template>

        <el-empty v-if="datasetLabels.length === 0" description="暂无专属标签，点击上方按钮创建" :image-size="60" />

        <div v-else class="labels-list">
          <div v-for="label in datasetLabels" :key="label.id" class="label-item">
            <div class="label-content">
              <div class="label-header">
                <span class="label-name">{{ label.name }}</span>
                <el-tag type="info" size="small">专属 v{{ label.version }}</el-tag>
                <el-tag :type="getTypeTagType(label.type)" size="small">
                  {{ getTypeLabel(label.type) }}
                </el-tag>
              </div>
              <div class="label-desc" :title="label.description">{{ label.description }}</div>
            </div>

            <div class="label-actions">
              <el-button size="small" :icon="Edit" @click="openEditDialog(label)">编辑</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="deleteLabel(label)">删除</el-button>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="group-card" shadow="never">
        <template #header>
          <div class="group-header">
            <span>全局标签</span>
            <el-tag type="success" size="small">{{ globalLabels.length }}</el-tag>
          </div>
        </template>

        <el-empty v-if="globalLabels.length === 0" description="暂无全局标签，请在标签库中创建" :image-size="60" />

        <div v-else class="labels-list">
          <div v-for="label in globalLabels" :key="label.id" class="label-item global">
            <div class="label-content">
              <div class="label-header">
                <span class="label-name">{{ label.name }}</span>
                <el-tag type="success" size="small">全局 v{{ label.version }}</el-tag>
                <el-tag :type="getTypeTagType(label.type)" size="small">
                  {{ getTypeLabel(label.type) }}
                </el-tag>
              </div>
              <div class="label-desc" :title="label.description">{{ label.description }}</div>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建数据集标签' : '编辑标签'"
      width="820px"
      :close-on-click-modal="false"
    >
      <el-form ref="labelFormRef" :model="labelForm" :rules="formRules" label-width="110px">
        <el-tabs v-model="dialogTab" type="card" class="label-dialog-tabs">
          <el-tab-pane v-if="dialogMode === 'create'" name="template" label="内置模板">
            <el-alert type="info" :closable="false" style="margin-bottom: 12px">
              选择模板后会自动填充“标签类型 + 预处理配置”，普通用户无需理解提取器/JSON 即可使用。
            </el-alert>

            <el-form-item label="选择模板">
              <el-radio-group v-model="labelForm.templateId" class="template-radio">
                <el-radio
                  v-for="tpl in templateOptions"
                  :key="tpl.id"
                  :value="tpl.id"
                  @change="applyTemplate(tpl.id)"
                >
                  {{ tpl.title }}
                </el-radio>
              </el-radio-group>

              <div class="form-hint">
                口径：仅“长度错误”算错；15位不算错；18位校验位不通过不算错；遮挡号不纳入判断与提取。
              </div>

              <el-button v-if="labelForm.templateId" size="small" @click="clearTemplate">
                清除模板选择
              </el-button>
            </el-form-item>
          </el-tab-pane>

          <el-tab-pane name="basic" label="基础信息">
            <el-form-item label="标签名称" prop="name">
              <el-input v-model="labelForm.name" placeholder="请输入标签名称" :disabled="dialogMode === 'edit'" />
              <div v-if="dialogMode === 'edit'" class="form-hint">
                标签名称不可修改，如需更改请创建新标签
              </div>
            </el-form-item>

            <el-form-item label="标签类型" v-if="dialogMode === 'create'">
              <template v-if="labelForm.templateId">
                <el-tag :type="getTypeTagType(labelForm.type)" size="small">
                  {{ getTypeLabel(labelForm.type) }}
                </el-tag>
                <span class="form-hint" style="margin-left: 8px">
                  由模板生成（如需自定义请先清除模板）
                </span>
              </template>
              <template v-else>
                <el-radio-group v-model="labelForm.type">
                  <el-radio value="classification">分类判断（是/否）</el-radio>
                  <el-radio value="extraction">LLM通用提取</el-radio>
                  <el-radio value="structured_extraction">结构化号码提取</el-radio>
                </el-radio-group>
              </template>
            </el-form-item>

            <el-form-item v-if="dialogMode === 'edit'" label="标签类型">
              <el-tag :type="getTypeTagType(editingLabel?.type)" size="small">
                {{ getTypeLabel(editingLabel?.type) }}
              </el-tag>
              <span style="margin-left: 8px; color: var(--text-tertiary); font-size: 12px">（类型创建后不可修改）</span>
            </el-form-item>

            <el-form-item label="标签规则" prop="description">
              <el-input
                v-model="labelForm.description"
                type="textarea"
                :rows="4"
                placeholder="请描述标签的判断/提取规则"
              />
            </el-form-item>

            <el-form-item v-if="isExtraction || isStructuredExtraction" label="提取字段">
              <el-input v-model="labelForm.extractFieldsText" placeholder="可选，逗号分隔，例如：姓名,手机号,地址" />
              <div class="form-hint">
                不填写则为“自由提取”。（模板标签通常无需填写）
              </div>
            </el-form-item>

            <el-form-item label="关注列">
              <el-input v-model="labelForm.focusColumnsText" placeholder="可选，逗号分隔，例如：用户评论,备注" />
              <div class="form-hint">选择AI重点关注的列，可提高分析准确度</div>
            </el-form-item>
          </el-tab-pane>

          <el-tab-pane name="advanced" label="高级配置">
            <el-collapse v-model="advancedPanels">
              <el-collapse-item name="preprocess" title="规则预处理（提取器 / number_intent / JSON）">
                <el-form-item label="预处理模式">
                  <el-radio-group v-model="labelForm.preprocessingMode">
                    <el-radio value="llm_only">仅 LLM</el-radio>
                    <el-radio value="rule_only">仅规则</el-radio>
                    <el-radio value="rule_then_llm">规则辅助 LLM</el-radio>
                  </el-radio-group>
                  <div class="form-hint">
                    仅规则：不调用大模型；规则辅助LLM：先规则提取证据，再交给大模型判断/解释。
                  </div>
                </el-form-item>

                <template v-if="showPreprocessingConfig">
                  <el-form-item label="配置方式">
                    <el-radio-group v-model="labelForm.preprocessorConfigMode">
                      <el-radio value="form">表单配置</el-radio>
                      <el-radio value="json">JSON编辑</el-radio>
                    </el-radio-group>
                    <div class="form-hint">
                      表单适合常规使用；JSON适合精确控制或迁移历史配置。
                    </div>
                  </el-form-item>

                  <template v-if="labelForm.preprocessorConfigMode === 'json'">
                    <el-form-item label="预处理器JSON">
                      <el-input
                        v-model="labelForm.preprocessorConfigText"
                        type="textarea"
                        :rows="8"
                        placeholder="请输入预处理器配置JSON（将原样保存）"
                      />
                      <div class="form-hint">
                        JSON模式下，将以此处内容为准；不使用下方表单配置。
                      </div>
                      <el-button size="small" @click="fillPreprocessorConfigTextFromForm">生成当前表单配置JSON</el-button>
                    </el-form-item>
                  </template>

                  <template v-else>
                    <el-form-item label="启用提取器" v-loading="extractorsLoading">
                      <el-checkbox-group v-model="labelForm.preprocessorExtractors">
                        <el-checkbox v-for="extractor in builtinExtractors" :key="extractor.code" :label="extractor.code">
                          {{ extractor.name }}
                        </el-checkbox>
                      </el-checkbox-group>
                      <div class="form-hint">
                        高级能力：用于复杂规则提取。模板标签通常不需要勾选提取器。
                      </div>
                    </el-form-item>

                    <el-divider content-position="left">号码类标签意图（number_intent）</el-divider>

                    <el-form-item label="启用意图">
                      <el-switch v-model="labelForm.numberIntentEnabled" />
                      <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                        开启后将以规则证据为准输出结果（建议用于号码类任务）
                      </span>
                    </el-form-item>

                    <template v-if="labelForm.numberIntentEnabled">
                      <el-form-item label="实体类型">
                        <el-select
                          v-model="labelForm.numberIntentEntity"
                          style="width: 100%"
                          :disabled="isIdInvalidTemplate"
                        >
                          <el-option label="手机号" value="phone" />
                          <el-option label="银行卡号" value="bank_card" />
                          <el-option label="身份证号" value="id_card" />
                        </el-select>
                      </el-form-item>

                      <el-form-item label="任务类型">
                        <el-select
                          v-model="labelForm.numberIntentTask"
                          style="width: 100%"
                          :disabled="isIdInvalidTemplate"
                        >
                          <el-option label="是否存在" value="exists" />
                          <el-option label="提取" value="extract" />
                          <el-option label="无效" value="invalid" />
                          <el-option label="遮挡" value="masked" />
                          <el-option v-if="labelForm.numberIntentEntity === 'id_card'" label="错误长度且遮挡" value="invalid_length_masked" />
                        </el-select>
                        <div v-if="isIdInvalidTemplate && idInvalidTemplateHint" class="form-hint">
                          {{ idInvalidTemplateHint }}
                        </div>
                      </el-form-item>

                      <el-form-item v-if="labelForm.numberIntentTask === 'extract'" label="提取范围">
                        <el-checkbox-group v-model="labelForm.numberIntentInclude">
                          <el-checkbox label="valid">有效</el-checkbox>
                          <el-checkbox label="invalid">无效</el-checkbox>
                          <el-checkbox label="masked">遮挡</el-checkbox>
                        </el-checkbox-group>
                      </el-form-item>

                      <el-form-item label="默认脱敏输出">
                        <el-switch v-model="labelForm.numberIntentDefaultMaskedOutput" />
                        <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                          关闭表示默认输出明文（可在结果页再切换脱敏）
                        </span>
                      </el-form-item>

                      <el-form-item v-if="labelForm.numberIntentEntity === 'bank_card'" label="无效银行卡策略">
                        <el-switch v-model="labelForm.numberIntentRequireKeywordForInvalidBank" />
                        <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                          开启后，无效银行卡需命中“银行卡/账号/转账”等关键词窗才会输出（降低误判）
                        </span>
                      </el-form-item>

                      <el-form-item v-if="labelForm.numberIntentEntity === 'id_card'" label="身份证策略">
                        <div style="display: flex; flex-direction: column; gap: 8px; width: 100%">
                          <div style="display: flex; align-items: center">
                            <el-switch
                              v-model="labelForm.numberIntentIdChecksumInvalidIsInvalid"
                              :disabled="isIdInvalidTemplate"
                            />
                            <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                              校验位不通过计入“无效”（默认关闭，保持历史口径）
                            </span>
                          </div>
                          <div style="display: flex; align-items: center">
                            <el-switch
                              v-model="labelForm.numberIntentId18XIsInvalid"
                              :disabled="isIdInvalidTemplate"
                            />
                            <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                              18位末位X计入“无效”（仅允许数字的业务口径）
                            </span>
                          </div>
                        </div>
                      </el-form-item>
                    </template>
                  </template>
                </template>

                <template v-if="showIncludePreprocessorOption">
                  <el-divider content-position="left">LLM 辅助配置</el-divider>
                  <el-form-item>
                    <el-checkbox v-model="labelForm.includePreprocessorInPrompt">
                      将规则预处理结果传入 LLM
                    </el-checkbox>
                    <div class="form-hint">
                      开启后，规则侧证据会进入大模型上下文，便于解释与一致性判断。
                    </div>
                  </el-form-item>
                </template>
              </el-collapse-item>

              <el-collapse-item name="enhancement" title="二次强化分析">
                <template v-if="showEnhancementConfig">
                  <el-form-item>
                    <el-switch v-model="labelForm.enableEnhancement" active-text="启用" inactive-text="不启用" />
                    <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                      启用后，LLM初步分析结果将进行二次验证和强化（更慢但更稳）
                    </span>
                  </el-form-item>

                  <el-form-item label="触发条件" v-if="labelForm.enableEnhancement">
                    <div class="enhancement-trigger">
                      <span>置信度低于 </span>
                      <el-input-number
                        v-model="labelForm.enhancementTriggerConfidence"
                        :min="0"
                        :max="100"
                        :step="5"
                        style="width: 100px; margin: 0 8px"
                      />
                      <span> % 时触发强化</span>
                    </div>
                    <div class="form-hint">建议 60-80；过高会频繁触发增加成本。</div>
                  </el-form-item>
                </template>

                <el-alert v-else type="info" :closable="false">
                  当前预处理模式不支持二次强化（仅 LLM / 规则辅助 LLM 支持）。
                </el-alert>
              </el-collapse-item>

              <el-collapse-item v-if="isStructuredExtraction" name="structured" title="结构化提取配置">
                <el-form-item label="提取器类型">
                  <el-select v-model="labelForm.extractorType" :disabled="dialogMode === 'edit'" style="width: 100%">
                    <el-option label="身份证号提取" value="id_card" />
                    <el-option label="银行卡号提取" value="bank_card" />
                    <el-option label="手机号提取" value="phone" />
                    <el-option label="复合提取（多种号码）" value="composite" />
                  </el-select>
                </el-form-item>

                <template v-if="labelForm.extractorType === 'id_card'">
                  <el-form-item label="包含18位标准">
                    <el-switch v-model="labelForm.include18Digit" />
                    <span style="margin-left: 8px; font-size: 12px; color: var(--text-tertiary)">
                      提取18位身份证号（含校验位验证）
                    </span>
                  </el-form-item>
                  <el-form-item label="包含15位旧版">
                    <el-switch v-model="labelForm.include15Digit" />
                  </el-form-item>
                  <el-form-item label="包含疑似号码">
                    <el-switch v-model="labelForm.includeLoose" />
                  </el-form-item>
                </template>

                <template v-if="labelForm.extractorType === 'composite'">
                  <el-form-item label="选择提取器">
                    <el-checkbox-group v-model="labelForm.compositeExtractors">
                      <el-checkbox label="id_card">身份证号</el-checkbox>
                      <el-checkbox label="bank_card">银行卡号</el-checkbox>
                      <el-checkbox label="phone">手机号</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </template>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dataset-label-manager {
  width: 100%;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.info-icon {
  color: var(--text-tertiary);
}

.labels-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.group-card :deep(.el-card__header) {
  padding: var(--space-3) var(--space-4);
}

.group-card :deep(.el-card__body) {
  padding: var(--space-3) var(--space-4);
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.labels-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.label-item {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
}

.label-item.global {
  background: #fbfffb;
}

.label-content {
  flex: 1;
  min-width: 0;
}

.label-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.label-name {
  font-weight: 600;
  color: var(--text-primary);
}

.label-desc {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.label-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.form-hint {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.template-radio {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: flex-start;
}

.enhancement-trigger {
  display: flex;
  align-items: center;
}

.label-dialog-tabs :deep(.el-tabs__content) {
  padding-top: var(--space-3);
}
</style>
