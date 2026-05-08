<script setup lang="ts">
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, PriceTag, Edit, Delete, Clock, MoreFilled,
  Search, Calendar, DataAnalysis, InfoFilled
} from '@element-plus/icons-vue'
import type { Label, LabelType, PreprocessingMode, ExtractorConfig as LabelExtractorConfig, PreprocessorConfig } from '../api/labels'
import * as labelsApi from '../api/labels'
import type { ExtractorConfig, ExtractorOption } from '../api/extractors'
import * as extractorsApi from '../api/extractors'

const loading = ref(false)
const page = ref(1)
const size = ref(12)
const total = ref(0)
const items = ref<Label[]>([])
const searchKeyword = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<Label | null>(null)
const submitting = ref(false)
const form = reactive({
  name: '',
  type: 'classification' as LabelType,
  description: '',
  focusColumnsText: '',
  extractFieldsText: '',

  // 新增字段
  preprocessingMode: 'llm_only' as PreprocessingMode,
  includePreprocessorInPrompt: true,
  enableEnhancement: false,
  enhancementTriggerConfidence: 70,

  // 规则提取器配置
  enabledExtractors: [] as string[],
  extractorOptions: {} as Record<string, any>,

  // 结构化提取配置（原有）
  extractorType: 'id_card' as string,
  compositeExtractors: ['id_card'] as string[],
})

// 提取器列表
const extractorsList = ref<ExtractorConfig[]>([])
const extractorsLoading = ref(false)

// 当前选中的提取器配置
const currentExtractor = computed(() => {
  return extractorsList.value.find(e => e.code === form.extractorType)
})

const versionsVisible = ref(false)
const versions = ref<Label[]>([])

// 计算属性
const isExtraction = computed(() => form.type === 'extraction')
const isStructuredExtraction = computed(() => form.type === 'structured_extraction')
const isClassification = computed(() => form.type === 'classification')

// 是否显示规则提取器配置
const showExtractorConfig = computed(() => {
  return form.preprocessingMode === 'rule_only' ||
         form.preprocessingMode === 'rule_then_llm'
})

// 是否显示"传入LLM"选项
const showIncludePreprocessorOption = computed(() => {
  return form.preprocessingMode === 'rule_then_llm'
})

// 是否显示二次强化配置
const showEnhancementConfig = computed(() => {
  return form.preprocessingMode === 'llm_only' ||
         form.preprocessingMode === 'rule_then_llm'
})

// 可用的提取器列表（根据标签类型过滤）
const availableExtractors = computed(() => {
  if (form.type === 'structured_extraction') {
    // 结构化提取只显示专用提取器
    return extractorsList.value.filter(e =>
      ['id_card', 'bank_card', 'phone', 'email', 'date', 'money', 'ip_address', 'url', 'car_plate', 'company_name'].includes(e.code)
    )
  }
  // 分类和提取类型显示所有提取器
  return extractorsList.value
})

// 统计数据
const classificationCount = computed(() =>
  items.value.filter(l => l.type === 'classification' || !l.type).length
)
const extractionCount = computed(() =>
  items.value.filter(l => l.type === 'extraction').length
)
const structuredCount = computed(() =>
  items.value.filter(l => l.type === 'structured_extraction').length
)

// 获取标签类型显示文本
function getTypeLabel(type: string) {
  if (type === 'extraction') return 'LLM通用提取'
  if (type === 'structured_extraction') return '结构化提取'
  return '分类判断'
}

// 获取标签类型tag类型
function getTypeTagType(type: string) {
  if (type === 'extraction') return 'warning'
  if (type === 'structured_extraction') return 'success'
  return 'primary'
}

// 获取预处理模式标签
function getPreprocessingModeLabel(mode: string) {
  const labels: Record<string, string> = {
    'llm_only': '仅 LLM',
    'rule_only': '仅规则',
    'rule_then_llm': '规则辅助 LLM',
    // 兼容旧值
    'none': '仅 LLM',
    'llm': '仅 LLM',
    'rule': '仅规则',
    'hybrid': '规则辅助 LLM',
  }
  return labels[mode] || mode
}

// 格式化日期
function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

function parseFocusColumns(text: string): string[] {
  return text
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

// 加载提取器列表
async function fetchExtractors() {
  extractorsLoading.value = true
  try {
    extractorsList.value = await extractorsApi.listExtractors()
  } catch (e: any) {
    console.error('加载提取器列表失败', e)
  } finally {
    extractorsLoading.value = false
  }
}

// 构建提取器配置JSON
function buildExtractorConfig(): string {
  if (form.type !== 'structured_extraction') {
    return ''
  }

  const config: LabelExtractorConfig = {
    extractorType: form.extractorType as any,
  }

  // 添加选项配置
  if (Object.keys(form.extractorOptions).length > 0) {
    config.options = { ...form.extractorOptions }
  }

  // 组合提取器
  if (form.extractorType === 'composite') {
    config.extractors = form.compositeExtractors.map(extractorType => ({
      field: getExtractorField(extractorType),
      extractorType: extractorType as any,
    }))
  }

  return JSON.stringify(config)
}

// 获取提取器对应的字段名
function getExtractorField(extractorType: string): string {
  const extractor = extractorsList.value.find(e => e.code === extractorType)
  if (extractor) return extractor.name
  if (extractorType === 'id_card') return '身份证号'
  if (extractorType === 'bank_card') return '银行卡号'
  if (extractorType === 'phone') return '手机号'
  return extractorType
}

// 解析提取器配置
function parseExtractorConfig(configStr: string) {
  try {
    const config: LabelExtractorConfig = JSON.parse(configStr)
    form.extractorType = config.extractorType

    // 解析选项
    if (config.options) {
      form.extractorOptions = { ...config.options }
    } else {
      form.extractorOptions = {}
    }

    if (config.extractors) {
      form.compositeExtractors = config.extractors.map(e => e.extractorType)
    }
  } catch (e) {
    console.warn('解析提取器配置失败', e)
  }
}

// 初始化提取器选项默认值
function initExtractorOptions() {
  const extractor = currentExtractor.value
  if (!extractor || !extractor.options) {
    form.extractorOptions = {}
    return
  }

  const options: Record<string, any> = {}
  for (const opt of extractor.options) {
    if (opt.defaultValue !== undefined && opt.defaultValue !== null) {
      if (opt.optionType === 'boolean') {
        options[opt.optionKey] = opt.defaultValue === 'true'
      } else if (opt.optionType === 'number') {
        options[opt.optionKey] = parseFloat(opt.defaultValue)
      } else {
        options[opt.optionKey] = opt.defaultValue
      }
    }
  }
  form.extractorOptions = options
}

// 监听提取器类型变化，初始化选项
watch(() => form.extractorType, () => {
  if (dialogMode.value === 'create') {
    initExtractorOptions()
  }
})

// 监听标签类型变化，重置预处理模式
watch(() => form.type, (newType) => {
  if (dialogMode.value === 'create') {
    // 结构化提取不使用预处理模式
    if (newType === 'structured_extraction') {
      form.preprocessingMode = 'llm_only'
      form.enabledExtractors = []
    }
  }
})

// 监听预处理模式变化
watch(() => form.preprocessingMode, (newMode) => {
  if (dialogMode.value === 'create') {
    // 切换到不需要规则提取器的模式时，清空已选择的提取器
    if (newMode === 'llm_only') {
      form.enabledExtractors = []
    }
  }
})

// 构建预处理器配置
function buildPreprocessorConfig(): string | undefined {
  if (!showExtractorConfig.value || form.enabledExtractors.length === 0) {
    return undefined
  }

  const config: PreprocessorConfig = {
    extractors: form.enabledExtractors,
  }

  // 添加各提取器的选项
  if (Object.keys(form.extractorOptions).length > 0) {
    config.extractorOptions = {}
    for (const extractorCode of form.enabledExtractors) {
      const extractor = extractorsList.value.find(e => e.code === extractorCode)
      if (extractor && extractor.options) {
        const options: Record<string, any> = {}
        for (const opt of extractor.options) {
          const key = `${extractorCode}_${opt.optionKey}`
          if (form.extractorOptions[key] !== undefined) {
            options[opt.optionKey] = form.extractorOptions[key]
          }
        }
        if (Object.keys(options).length > 0) {
          config.extractorOptions[extractorCode] = options
        }
      }
    }
  }

  return JSON.stringify(config)
}

// 解析预处理器配置
function parsePreprocessorConfig(configStr: string | undefined) {
  form.enabledExtractors = []
  form.extractorOptions = {}

  if (!configStr) return

  try {
    const config: PreprocessorConfig = JSON.parse(configStr)
    form.enabledExtractors = config.extractors || []

    // 解析各提取器的选项
    if (config.extractorOptions) {
      for (const [extractorCode, options] of Object.entries(config.extractorOptions)) {
        for (const [key, value] of Object.entries(options as any)) {
          form.extractorOptions[`${extractorCode}_${key}`] = value
        }
      }
    }
  } catch (e) {
    console.warn('解析预处理器配置失败', e)
  }
}

// 构建强化分析配置
function buildEnhancementConfig(): string | undefined {
  if (!form.enableEnhancement) {
    return undefined
  }

  const config = {
    triggerConfidence: form.enhancementTriggerConfidence,
  }

  return JSON.stringify(config)
}

async function fetchList() {
  loading.value = true
  try {
    const resp = await labelsApi.listLabels({ page: page.value, size: size.value })
    items.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  form.name = ''
  form.type = 'classification'
  form.description = ''
  form.focusColumnsText = ''
  form.extractFieldsText = ''
  form.preprocessingMode = 'llm_only'
  form.includePreprocessorInPrompt = true
  form.enableEnhancement = false
  form.enhancementTriggerConfidence = 70
  form.enabledExtractors = []
  form.extractorOptions = {}
  form.extractorType = 'id_card'
  form.compositeExtractors = ['id_card']
  initExtractorOptions()
  dialogVisible.value = true
}

function openEdit(row: Label) {
  dialogMode.value = 'edit'
  editing.value = row
  form.name = row.name
  form.type = row.type || 'classification'
  form.description = row.description
  form.focusColumnsText = (row.focusColumns || []).join(',')
  form.extractFieldsText = (row.extractFields || []).join(',')

  // 解析预处理模式（兼容旧值）
  const modeMap: Record<string, PreprocessingMode> = {
    'none': 'llm_only',
    'llm': 'llm_only',
    'rule': 'rule_only',
    'hybrid': 'rule_then_llm',
  }
  form.preprocessingMode = (modeMap[row.preprocessingMode || ''] || row.preprocessingMode || 'llm_only') as PreprocessingMode
  form.includePreprocessorInPrompt = row.includePreprocessorInPrompt ?? true
  form.enableEnhancement = row.enableEnhancement ?? false

  // 解析预处理器配置
  parsePreprocessorConfig(row.preprocessorConfig)

  // 解析强化分析配置
  if (row.enhancementConfig) {
    try {
      const config = JSON.parse(row.enhancementConfig)
      form.enhancementTriggerConfidence = config.triggerConfidence || 70
    } catch (e) {
      form.enhancementTriggerConfidence = 70
    }
  }

  // 解析提取器配置
  if (row.extractorConfig) {
    parseExtractorConfig(row.extractorConfig)
  } else {
    form.extractorType = 'id_card'
    form.extractorOptions = {}
    form.compositeExtractors = ['id_card']
    initExtractorOptions()
  }

  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  if (!form.description.trim()) {
    ElMessage.warning('请输入标签规则描述')
    return
  }

  // 验证规则提取器配置
  if (showExtractorConfig.value && form.enabledExtractors.length === 0) {
    ElMessage.warning('请至少选择一个规则提取器')
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await labelsApi.createLabel({
        name: form.name.trim(),
        type: form.type,
        description: form.description.trim(),
        focusColumns: parseFocusColumns(form.focusColumnsText),
        extractFields: (form.type === 'extraction' || form.type === 'structured_extraction')
          ? parseFocusColumns(form.extractFieldsText)
          : undefined,
        extractorConfig: form.type === 'structured_extraction' ? buildExtractorConfig() : undefined,
        preprocessingMode: form.preprocessingMode,
        preprocessorConfig: buildPreprocessorConfig(),
        includePreprocessorInPrompt: form.includePreprocessorInPrompt,
        enableEnhancement: form.enableEnhancement,
        enhancementConfig: buildEnhancementConfig(),
        scope: 'global',
      })
      ElMessage.success('创建成功')
    } else if (editing.value) {
      await labelsApi.updateLabel(editing.value.id, {
        description: form.description.trim(),
        focusColumns: parseFocusColumns(form.focusColumnsText),
        extractFields: (editing.value.type === 'extraction' || editing.value.type === 'structured_extraction')
          ? parseFocusColumns(form.extractFieldsText)
          : undefined,
        extractorConfig: editing.value.type === 'structured_extraction' ? buildExtractorConfig() : undefined,
        preprocessingMode: form.preprocessingMode,
        preprocessorConfig: buildPreprocessorConfig(),
        includePreprocessorInPrompt: form.includePreprocessorInPrompt,
        enableEnhancement: form.enableEnhancement,
        enhancementConfig: buildEnhancementConfig(),
      })
      ElMessage.success('已创建新版本')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function openVersions(row: Label) {
  versionsVisible.value = true
  versions.value = []
  try {
    versions.value = await labelsApi.getVersions(row.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载版本失败')
  }
}

async function onDelete(row: Label) {
  try {
    await ElMessageBox.confirm(`确认删除标签「${row.name}」？（若被任务使用将禁止删除）`, '提示', { type: 'warning' })
    await labelsApi.deleteLabel(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  fetchList()
  fetchExtractors()
})
</script>

<template>
  <div class="labels-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>标签库</h2>
        <p class="page-desc">
          标签用于定义数据分析规则，创建后可在任何数据集的分析任务中使用
        </p>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索标签"
          :prefix-icon="Search"
          style="width: 300px; margin-right: 12px;"
          clearable
        />
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          创建标签
        </el-button>
      </div>
    </div>

    <!-- 标签统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">标签总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ classificationCount }}</div>
          <div class="stat-label">分类判断</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ extractionCount }}</div>
          <div class="stat-label">信息提取</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ structuredCount }}</div>
          <div class="stat-label">结构化提取</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 标签列表 -->
    <el-card class="labels-container">
      <div v-loading="loading" class="labels-grid">
        <el-card
          v-for="label in items"
          :key="label.id"
          class="label-card"
          shadow="hover"
        >
          <!-- 标签头部 -->
          <div class="label-header">
            <div class="label-name">
              <el-icon><PriceTag /></el-icon>
              <span>{{ label.name }}</span>
              <el-tag size="small" style="margin-left: 8px;">
                v{{ label.version }}
              </el-tag>
            </div>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEdit(label)">
                    <el-icon><Edit /></el-icon>
                    编辑（创建新版本）
                  </el-dropdown-item>
                  <el-dropdown-item @click="openVersions(label)">
                    <el-icon><Clock /></el-icon>
                    版本历史
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="onDelete(label)">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <!-- 标签类型 -->
          <div class="label-type">
            <el-tag :type="getTypeTagType(label.type)" size="small">
              {{ getTypeLabel(label.type) }}
            </el-tag>
            <el-tag v-if="label.preprocessingMode" type="info" size="small" style="margin-left: 4px;">
              {{ getPreprocessingModeLabel(label.preprocessingMode) }}
            </el-tag>
            <el-tag v-if="label.scope === 'dataset'" type="info" size="small" style="margin-left: 4px;">
              数据集专属
            </el-tag>
          </div>

          <!-- 标签描述 -->
          <div class="label-description">
            {{ label.description }}
          </div>

          <!-- 标签元信息 -->
          <div class="label-meta">
            <span>
              <el-icon><Calendar /></el-icon>
              {{ formatDate(label.createdAt) }}
            </span>
          </div>
        </el-card>

        <!-- 空状态 -->
        <el-empty
          v-if="items.length === 0 && !loading"
          description="暂无标签"
          style="grid-column: 1 / -1;"
        >
          <el-button type="primary" @click="openCreate">
            创建第一个标签
          </el-button>
        </el-empty>
      </div>

      <!-- 分页 -->
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 20px; justify-content: center;"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </el-card>

    <!-- 创建/编辑标签弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建标签' : '编辑标签（创建新版本）'"
      width="700px"
      destroy-on-close
    >
      <el-form :model="form" label-width="140px">
        <el-form-item label="标签名称" required>
          <el-input
            v-model="form.name"
            placeholder="例如：客户投诉识别"
            :disabled="dialogMode === 'edit'"
          />
          <div class="form-tip">
            {{ dialogMode === 'edit' ? '编辑时不可修改标签名称，将创建新版本' : '标签名称用于标识标签，创建后不可修改' }}
          </div>
        </el-form-item>

        <el-form-item label="标签类型" required>
          <el-radio-group v-model="form.type" :disabled="dialogMode === 'edit'">
            <el-radio value="classification">
              分类判断（是/否）
            </el-radio>
            <el-radio value="extraction">
              信息提取（LLM提取）
            </el-radio>
            <el-radio value="structured_extraction">
              结构化提取（号码提取）
            </el-radio>
          </el-radio-group>
          <div v-if="dialogMode === 'edit'" class="form-tip">
            类型创建后不可修改
          </div>
        </el-form-item>

        <el-form-item label="标签规则" required>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="请详细描述标签的判断规则，例如：&#10;判断文本是否包含客户投诉内容，包括但不限于：&#10;1. 对服务态度的不满&#10;2. 对产品质量的投诉&#10;3. 要求退款或赔偿的诉求"
          />
          <div class="form-tip">
            规则描述越详细，AI 分析越准确
          </div>
        </el-form-item>

        <el-form-item label="重点关注列">
          <el-input
            v-model="form.focusColumnsText"
            placeholder="例如：内容,备注（用逗号分隔，留空表示分析所有列）"
          />
          <div class="form-tip">
            指定需要重点分析的列名，可提高分析效率
          </div>
        </el-form-item>

        <!-- 处理模式配置（仅分类和提取类型显示） -->
        <template v-if="isClassification || isExtraction">
          <el-divider content-position="left">处理模式配置</el-divider>

          <el-form-item label="预处理模式" required>
            <el-radio-group v-model="form.preprocessingMode">
              <el-radio value="llm_only">
                仅 LLM 判断
                <div class="radio-desc">直接调用大模型进行分析，不使用规则提取器</div>
              </el-radio>
              <el-radio value="rule_only">
                仅规则提取
                <div class="radio-desc">只使用规则提取器，不调用大模型（速度快，不消耗 token）</div>
              </el-radio>
              <el-radio value="rule_then_llm">
                规则辅助 LLM
                <div class="radio-desc">先用规则提取器预处理，然后将结果传给大模型参考</div>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 是否将预处理结果传入 LLM -->
          <el-form-item
            v-if="showIncludePreprocessorOption"
            label="传入 LLM"
          >
            <el-switch
              v-model="form.includePreprocessorInPrompt"
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-tip" style="margin-left: 12px;">
              开启后，规则提取结果会作为上下文传给 LLM，可提高判断准确性，但会增加 token 消耗
            </div>
          </el-form-item>

          <!-- 规则提取器配置 -->
          <template v-if="showExtractorConfig">
            <el-form-item label="规则提取器" required>
              <div class="extractor-selector">
                <el-checkbox-group v-model="form.enabledExtractors">
                  <div v-for="ext in availableExtractors" :key="ext.code" class="extractor-checkbox-item">
                    <el-checkbox :value="ext.code">
                      {{ ext.name }}
                    </el-checkbox>
                    <el-tooltip v-if="ext.description" :content="ext.description" placement="top">
                      <el-icon class="info-icon"><InfoFilled /></el-icon>
                    </el-tooltip>

                    <!-- 提取器选项配置 -->
                    <div v-if="form.enabledExtractors.includes(ext.code) && ext.options && ext.options.length > 0"
                         class="extractor-sub-options">
                      <template v-for="opt in ext.options" :key="opt.optionKey">
                        <div class="sub-option-item">
                          <el-checkbox
                            v-if="opt.optionType === 'boolean'"
                            v-model="form.extractorOptions[`${ext.code}_${opt.optionKey}`]"
                          >
                            {{ opt.optionName }}
                          </el-checkbox>
                          <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
                        </div>
                      </template>
                    </div>
                  </div>
                </el-checkbox-group>
              </div>
              <div class="form-tip">
                至少选择一个提取器
              </div>
            </el-form-item>
          </template>

          <!-- 二次强化分析配置 -->
          <template v-if="showEnhancementConfig">
            <el-form-item label="二次强化分析">
              <el-switch
                v-model="form.enableEnhancement"
                active-text="开启"
                inactive-text="关闭"
              />
              <div class="form-tip" style="margin-left: 12px;">
                用 LLM 验证并调整初步结果的置信度
              </div>
            </el-form-item>

            <el-form-item
              v-if="form.enableEnhancement"
              label="触发条件"
            >
              <div class="enhancement-trigger">
                <span>置信度低于 </span>
                <el-input-number
                  v-model="form.enhancementTriggerConfidence"
                  :min="0"
                  :max="100"
                  :step="5"
                  style="width: 100px; margin: 0 8px;"
                />
                <span> % 时触发强化</span>
              </div>
              <div class="form-tip">
                建议设置为 60-80，过低会频繁触发，过高会失去强化作用
              </div>
            </el-form-item>
          </template>
        </template>

        <!-- 提取类型的额外字段 -->
        <el-form-item v-if="isExtraction" label="提取字段">
          <el-input
            v-model="form.extractFieldsText"
            placeholder="例如：姓名,手机号,地址（用逗号分隔）"
          />
          <div class="form-tip">
            指定需要提取的字段名称
          </div>
        </el-form-item>

        <!-- 结构化提取的配置 -->
        <template v-if="isStructuredExtraction">
          <el-divider content-position="left">结构化提取配置</el-divider>

          <el-form-item label="提取器类型">
            <el-select
              v-model="form.extractorType"
              :disabled="dialogMode === 'edit'"
              :loading="extractorsLoading"
              style="width: 100%"
              placeholder="选择提取器"
            >
              <el-option-group label="号码提取器">
                <el-option
                  v-for="ext in availableExtractors.filter(e =>
                    ['id_card', 'bank_card', 'phone'].includes(e.code)
                  )"
                  :key="ext.code"
                  :label="ext.name"
                  :value="ext.code"
                >
                  <div class="extractor-option">
                    <span>{{ ext.name }}</span>
                    <span class="extractor-desc">{{ ext.description }}</span>
                  </div>
                </el-option>
              </el-option-group>
              <el-option-group label="其他提取器">
                <el-option
                  v-for="ext in availableExtractors.filter(e =>
                    !['id_card', 'bank_card', 'phone'].includes(e.code)
                  )"
                  :key="ext.code"
                  :label="ext.name"
                  :value="ext.code"
                >
                  <div class="extractor-option">
                    <span>{{ ext.name }}</span>
                    <span class="extractor-desc">{{ ext.description }}</span>
                  </div>
                </el-option>
              </el-option-group>
            </el-select>
            <div class="form-tip" v-if="currentExtractor">
              {{ currentExtractor.description }}
            </div>
          </el-form-item>

          <!-- 动态选项配置 -->
          <el-form-item
            v-if="currentExtractor && currentExtractor.options && currentExtractor.options.length > 0"
            label="提取选项"
          >
            <div class="extractor-options">
              <template v-for="opt in currentExtractor.options" :key="opt.optionKey">
                <div class="option-item">
                  <el-checkbox
                    v-if="opt.optionType === 'boolean'"
                    v-model="form.extractorOptions[opt.optionKey]"
                  >
                    {{ opt.optionName }}
                  </el-checkbox>
                  <template v-else-if="opt.optionType === 'string'">
                    <span class="option-label">{{ opt.optionName }}:</span>
                    <el-input
                      v-model="form.extractorOptions[opt.optionKey]"
                      size="small"
                      style="width: 200px;"
                    />
                  </template>
                  <template v-else-if="opt.optionType === 'number'">
                    <span class="option-label">{{ opt.optionName }}:</span>
                    <el-input-number
                      v-model="form.extractorOptions[opt.optionKey]"
                      size="small"
                    />
                  </template>
                  <span class="option-desc" v-if="opt.description">{{ opt.description }}</span>
                </div>
              </template>
            </div>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">
          {{ dialogMode === 'create' ? '创建' : '保存为新版本' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 版本历史弹窗 -->
    <el-dialog
      v-model="versionsVisible"
      title="版本历史"
      width="800px"
    >
      <el-timeline>
        <el-timeline-item
          v-for="ver in versions"
          :key="ver.id"
          :timestamp="formatDate(ver.createdAt)"
          placement="top"
        >
          <el-card>
            <div class="version-header">
              <el-tag>v{{ ver.version }}</el-tag>
              <el-tag v-if="ver.isActive" type="success">当前版本</el-tag>
              <el-tag v-if="ver.preprocessingMode" type="info" size="small" style="margin-left: 4px;">
                {{ getPreprocessingModeLabel(ver.preprocessingMode) }}
              </el-tag>
            </div>
            <div class="version-content">
              {{ ver.description }}
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<style scoped>
.labels-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.header-left h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #303133;
}

.page-desc {
  margin: 0;
  font-size: 14px;
  color: #606266;
}

.header-right {
  display: flex;
  align-items: center;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #409EFF;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.labels-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  min-height: 400px;
}

.label-card {
  cursor: pointer;
  transition: all 0.3s;
}

.label-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.label-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.label-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.more-icon {
  cursor: pointer;
  font-size: 18px;
  color: #909399;
}

.more-icon:hover {
  color: #409EFF;
}

.label-type {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.label-description {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 44px;
}

.label-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  padding-top: 12px;
  border-top: 1px solid #EBEEF5;
}

.label-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.version-header {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.version-content {
  color: #606266;
  line-height: 1.6;
}

.extractor-option {
  display: flex;
  flex-direction: column;
}

.extractor-option .extractor-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.extractor-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.option-label {
  font-size: 14px;
  color: #606266;
  min-width: 80px;
}

.option-desc {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

/* 新增样式 */
.radio-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  margin-left: 24px;
}

.extractor-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.extractor-checkbox-item {
  padding: 8px;
  border: 1px solid #EBEEF5;
  border-radius: 4px;
  transition: all 0.2s;
}

.extractor-checkbox-item:hover {
  background-color: #F5F7FA;
}

.extractor-sub-options {
  margin-top: 8px;
  margin-left: 24px;
  padding: 8px;
  background-color: #FAFAFA;
  border-radius: 4px;
}

.sub-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.info-icon {
  margin-left: 4px;
  color: #909399;
  cursor: help;
}

.enhancement-trigger {
  display: flex;
  align-items: center;
}
</style>
