<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Delete, Setting, Search, Refresh,
  Document, Lock, Unlock, MagicStick, Check
} from '@element-plus/icons-vue'
import type { ExtractorConfig, ExtractorPattern, ExtractorOption, CreateExtractorRequest, UpdateExtractorRequest, AiGenerateExtractorRequest, AiGenerateExtractorResponse } from '../api/extractors'
import * as extractorsApi from '../api/extractors'

const loading = ref(false)
const extractors = ref<ExtractorConfig[]>([])
const searchKeyword = ref('')
const categoryFilter = ref<'all' | 'builtin' | 'custom'>('builtin')

// 对话框状态
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<ExtractorConfig | null>(null)
const submitting = ref(false)

// 表单数据
const form = reactive({
  name: '',
  code: '',
  description: '',
  patterns: [] as ExtractorPattern[],
  options: [] as ExtractorOption[],
})

// AI辅助状态
const aiGenerating = ref(false)
const aiMode = ref<'description' | 'samples'>('description')
const aiDescription = ref('')
const aiSamples = ref('')
const aiNeedValidation = ref(false)
const aiResult = ref<AiGenerateExtractorResponse | null>(null)
const aiCollapseVisible = ref(false)

// 计算属性
const builtinCount = computed(() => extractors.value.filter(e => e.isSystem).length)
const customCount = computed(() => extractors.value.filter(e => !e.isSystem).length)

const filteredExtractors = computed(() => {
  const list = extractors.value.filter(e => {
    if (categoryFilter.value === 'builtin') return e.isSystem
    if (categoryFilter.value === 'custom') return !e.isSystem
    return true
  })
  if (!searchKeyword.value) return list
  const keyword = searchKeyword.value.toLowerCase()
  return list.filter(e =>
    e.name.toLowerCase().includes(keyword) ||
    e.code.toLowerCase().includes(keyword) ||
    (e.description && e.description.toLowerCase().includes(keyword))
  )
})

// 格式化日期
function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取验证类型显示文本
function getValidationTypeLabel(type: string | undefined) {
  if (!type || type === 'none') return '无'
  if (type === 'checksum') return '校验位验证'
  if (type === 'luhn') return 'Luhn算法'
  return type
}

// 加载提取器列表
async function fetchList() {
  loading.value = true
  try {
    extractors.value = await extractorsApi.listExtractors()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 打开创建对话框
function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  form.patterns = [{
    name: '默认规则',
    pattern: '',
    description: '',
    priority: 100,
    confidence: 0.9,
    validationType: 'none',
    isActive: true,
    sortOrder: 0
  }]
  form.options = []
  dialogVisible.value = true
}

// 打开编辑对话框
function openEdit(row: ExtractorConfig) {
  dialogMode.value = 'edit'
  editing.value = row
  form.name = row.name
  form.code = row.code
  form.description = row.description || ''
  form.patterns = row.patterns.map(p => ({ ...p }))
  form.options = row.options.map(o => ({ ...o }))
  dialogVisible.value = true
}

// 添加正则规则
function addPattern() {
  form.patterns.push({
    name: `规则${form.patterns.length + 1}`,
    pattern: '',
    description: '',
    priority: 0,
    confidence: 0.9,
    validationType: 'none',
    isActive: true,
    sortOrder: form.patterns.length
  })
}

// 删除正则规则
function removePattern(index: number) {
  if (form.patterns.length <= 1) {
    ElMessage.warning('至少需要保留一条规则')
    return
  }
  form.patterns.splice(index, 1)
}

// 添加选项
function addOption() {
  form.options.push({
    optionKey: '',
    optionName: '',
    optionType: 'boolean',
    defaultValue: 'true',
    description: '',
    sortOrder: form.options.length
  })
}

// 删除选项
function removeOption(index: number) {
  form.options.splice(index, 1)
}

// 提交表单
async function submit() {
  // 验证
  if (!form.name.trim()) {
    ElMessage.warning('请输入提取器名称')
    return
  }
  if (!form.code.trim()) {
    ElMessage.warning('请输入提取器代码')
    return
  }
  if (form.patterns.length === 0) {
    ElMessage.warning('请至少添加一条正则规则')
    return
  }
  for (const p of form.patterns) {
    if (!p.pattern.trim()) {
      ElMessage.warning('正则表达式不能为空')
      return
    }
    // 验证正则表达式语法
    try {
      new RegExp(p.pattern)
    } catch (e) {
      ElMessage.warning(`正则表达式语法错误: ${p.name}`)
      return
    }
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      const request: CreateExtractorRequest = {
        name: form.name.trim(),
        code: form.code.trim(),
        description: form.description.trim() || undefined,
        patterns: form.patterns.map(p => ({
          name: p.name,
          pattern: p.pattern,
          description: p.description || undefined,
          priority: p.priority,
          confidence: p.confidence,
          validationType: p.validationType || undefined,
          isActive: p.isActive,
          sortOrder: p.sortOrder
        })),
        options: form.options.length > 0 ? form.options.map(o => ({
          optionKey: o.optionKey,
          optionName: o.optionName,
          optionType: o.optionType,
          defaultValue: o.defaultValue || undefined,
          description: o.description || undefined,
          sortOrder: o.sortOrder
        })) : undefined
      }
      await extractorsApi.createExtractor(request)
      ElMessage.success('创建成功')
    } else if (editing.value) {
      const request: UpdateExtractorRequest = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        patterns: form.patterns.map(p => ({
          name: p.name,
          pattern: p.pattern,
          description: p.description || undefined,
          priority: p.priority,
          confidence: p.confidence,
          validationType: p.validationType || undefined,
          isActive: p.isActive,
          sortOrder: p.sortOrder
        })),
        options: form.options.map(o => ({
          optionKey: o.optionKey,
          optionName: o.optionName,
          optionType: o.optionType,
          defaultValue: o.defaultValue || undefined,
          description: o.description || undefined,
          sortOrder: o.sortOrder
        }))
      }
      await extractorsApi.updateExtractor(editing.value.id, request)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

// 删除提取器
async function onDelete(row: ExtractorConfig) {
  if (row.isSystem) {
    ElMessage.warning('系统内置提取器不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除提取器「${row.name}」？`, '提示', { type: 'warning' })
    await extractorsApi.deleteExtractor(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

// 测试正则表达式
const testDialogVisible = ref(false)
const testPattern = ref('')
const testText = ref('')
const testResults = ref<string[]>([])

function openTest(pattern: string) {
  testPattern.value = pattern
  testText.value = ''
  testResults.value = []
  testDialogVisible.value = true
}

function runTest() {
  testResults.value = []
  if (!testPattern.value || !testText.value) return

  try {
    const regex = new RegExp(testPattern.value, 'g')
    let match
    while ((match = regex.exec(testText.value)) !== null) {
      testResults.value.push(match[0])
    }
    if (testResults.value.length === 0) {
      ElMessage.info('未匹配到任何结果')
    }
  } catch (e: any) {
    ElMessage.error('正则表达式错误: ' + e.message)
  }
}

// ========== AI辅助功能 ==========

// AI生成提取器
async function generateWithAi() {
  if (!form.name.trim()) {
    ElMessage.warning('请先输入提取器名称')
    return
  }

  if (aiMode.value === 'description' && !aiDescription.value.trim()) {
    ElMessage.warning('请输入需求描述')
    return
  }

  if (aiMode.value === 'samples' && !aiSamples.value.trim()) {
    ElMessage.warning('请输入示例数据')
    return
  }

  aiGenerating.value = true
  try {
    const request: extractorsApi.AiGenerateExtractorRequest = {
      mode: aiMode.value,
      extractorName: form.name.trim(),
      description: aiMode.value === 'description' ? aiDescription.value.trim() : undefined,
      samples: aiMode.value === 'samples' ? aiSamples.value.trim() : undefined,
      needValidation: aiNeedValidation.value
    }

    const response = await extractorsApi.aiGenerateExtractor(request)
    aiResult.value = response

    // 自动填充到表单
    if (response.suggestedCode && !form.code) {
      form.code = response.suggestedCode
    }
    if (response.description) {
      form.description = response.description
    }

    // 将AI生成的规则应用到表单
    if (response.patterns && response.patterns.length > 0) {
      form.patterns = response.patterns.map((p, index) => ({
        name: p.name,
        pattern: p.pattern,
        description: p.description,
        priority: p.priority,
        confidence: p.confidence,
        validationType: p.validationType,
        isActive: true,
        sortOrder: index
      }))
    }

    ElMessage.success('AI生成成功！已自动填充到表单')
    aiCollapseVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || 'AI生成失败')
  } finally {
    aiGenerating.value = false
  }
}

// 清空AI结果
function clearAiResult() {
  aiResult.value = null
  aiDescription.value = ''
  aiSamples.value = ''
}

// 切换AI模式
function switchAiMode(mode: 'description' | 'samples') {
  aiMode.value = mode
  clearAiResult()
}

onMounted(fetchList)
</script>

<template>
  <div class="extractors-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>提取器配置</h2>
        <p class="page-desc">
          管理结构化提取的正则表达式规则，支持自定义提取器
        </p>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索提取器"
          :prefix-icon="Search"
          style="width: 250px; margin-right: 12px;"
          clearable
        />
        <el-select v-model="categoryFilter" style="width: 120px; margin-right: 12px;">
          <el-option label="内置" value="builtin" />
          <el-option label="自定义" value="custom" />
          <el-option label="全部" value="all" />
        </el-select>
        <el-button @click="fetchList" :icon="Refresh">刷新</el-button>
        <el-button type="primary" @click="openCreate" :icon="Plus">
          创建提取器
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-value">{{ extractors.length }}</div>
          <div class="stat-label">提取器总数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-value">{{ builtinCount }}</div>
          <div class="stat-label">内置提取器</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-value">{{ customCount }}</div>
          <div class="stat-label">自定义提取器</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 提取器列表 -->
    <el-card class="extractors-container">
      <el-table :data="filteredExtractors" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <div class="extractor-name">
              <el-icon v-if="row.isSystem" class="system-icon"><Lock /></el-icon>
              <el-icon v-else class="custom-icon"><Unlock /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isSystem ? 'warning' : 'success'" size="small">
              {{ row.isSystem ? '内置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则数" width="80" align="center">
          <template #default="{ row }">
            {{ row.patterns?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              @click="onDelete(row)"
              :disabled="row.isSystem"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建提取器' : '编辑提取器'"
      width="900px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <!-- AI辅助区域（仅创建模式显示） -->
        <el-form-item v-if="dialogMode === 'create'">
          <el-collapse v-model="aiCollapseVisible" style="width: 100%">
            <el-collapse-item name="ai">
              <template #title>
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-icon :color="aiCollapseVisible ? '#409EFF' : '#909399'"><MagicStick /></el-icon>
                  <span style="font-weight: 600;">AI 辅助生成</span>
                  <el-tag v-if="aiResult" type="success" size="small" effect="plain">已生成</el-tag>
                </div>
              </template>

              <div class="ai-assist-section">
                <!-- 模式选择 -->
                <el-radio-group v-model="aiMode" size="small" style="margin-bottom: 12px;">
                  <el-radio-button value="description" @click="switchAiMode('description')">
                    <el-icon style="margin-right: 4px;"><Document /></el-icon>
                    描述式生成
                  </el-radio-button>
                  <el-radio-button value="samples" @click="switchAiMode('samples')">
                    <el-icon style="margin-right: 4px;"><Search /></el-icon>
                    示例式学习
                  </el-radio-button>
                </el-radio-group>

                <!-- 描述式输入 -->
                <div v-if="aiMode === 'description'" class="ai-input-section">
                  <el-input
                    v-model="aiDescription"
                    type="textarea"
                    :rows="4"
                    placeholder="请用自然语言描述您要提取的数据，例如：&#10;提取中国18位居民身份证号码，格式为6位地区码+4位出生日期+3位顺序码+1位校验码，最后一位可以是X"
                  />
                </div>

                <!-- 示例式输入 -->
                <div v-if="aiMode === 'samples'" class="ai-input-section">
                  <el-input
                    v-model="aiSamples"
                    type="textarea"
                    :rows="4"
                    placeholder="请提供示例数据（每行一个），例如：&#10;11010519900307691X&#10;310104198501011234&#10;44030819920721234X"
                  />
                </div>

                <!-- 高级选项 -->
                <div class="ai-options">
                  <el-checkbox v-model="aiNeedValidation">
                    需要验证规则（如校验位、Luhn算法等）
                  </el-checkbox>
                </div>

                <!-- 操作按钮 -->
                <div class="ai-actions">
                  <el-button
                    type="primary"
                    :icon="MagicStick"
                    :loading="aiGenerating"
                    @click="generateWithAi"
                  >
                    {{ aiGenerating ? 'AI生成中...' : 'AI智能生成' }}
                  </el-button>
                  <el-button v-if="aiResult" @click="clearAiResult">
                    清除结果
                  </el-button>
                </div>

                <!-- AI生成结果展示 -->
                <div v-if="aiResult" class="ai-result-section">
                  <el-divider content-position="left">
                    <el-icon style="margin-right: 4px;"><Document /></el-icon>
                    AI生成结果
                  </el-divider>

                  <div class="ai-explanation">
                    <el-alert
                      :title="aiResult.explanation || 'AI已根据您的需求生成以下正则表达式规则'"
                      type="info"
                      :closable="false"
                      show-icon
                    />
                  </div>

                  <!-- 生成的规则预览 -->
                  <div v-for="(pattern, idx) in aiResult.patterns" :key="idx" class="ai-pattern-card">
                    <el-card shadow="hover">
                      <template #header>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                          <span style="font-weight: 600;">{{ pattern.name }}</span>
                          <el-tag size="small" type="success">
                            信心度: {{ Math.round(pattern.confidence * 100) }}%
                          </el-tag>
                        </div>
                      </template>
                      <div class="pattern-detail">
                        <div class="pattern-field">
                          <label>正则表达式:</label>
                          <code class="regex-code">{{ pattern.pattern }}</code>
                        </div>
                        <div class="pattern-field">
                          <label>说明:</label>
                          <span>{{ pattern.description }}</span>
                        </div>
                        <el-row :gutter="16">
                          <el-col :span="12">
                            <div class="pattern-field">
                              <label>匹配示例:</label>
                              <el-tag type="success" size="small">{{ pattern.example }}</el-tag>
                            </div>
                          </el-col>
                          <el-col :span="12">
                            <div class="pattern-field">
                              <label>不匹配示例:</label>
                              <el-tag type="danger" size="small">{{ pattern.negativeExample }}</el-tag>
                            </div>
                          </el-col>
                        </el-row>
                      </div>
                    </el-card>
                  </div>

                  <div class="ai-apply-tip">
                    <el-icon style="margin-right: 4px;"><Check /></el-icon>
                    以上规则已自动填充到表单，您可以继续编辑或直接创建
                  </div>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="提取器名称" required>
              <el-input
                v-model="form.name"
                placeholder="例如：车牌号提取"
                :disabled="dialogMode === 'edit' && editing?.isSystem"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提取器代码" required>
              <el-input
                v-model="form.code"
                placeholder="例如：license_plate"
                :disabled="dialogMode === 'edit'"
              />
              <div class="form-tip">唯一标识，创建后不可修改</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="描述提取器的用途"
          />
        </el-form-item>

        <!-- 正则规则 -->
        <el-divider content-position="left">正则规则</el-divider>
        
        <div class="patterns-section">
          <div
            v-for="(pattern, index) in form.patterns"
            :key="index"
            class="pattern-item"
          >
            <el-card shadow="never">
              <template #header>
                <div class="pattern-header">
                  <span>规则 {{ index + 1 }}: {{ pattern.name }}</span>
                  <div>
                    <el-button link type="primary" @click="openTest(pattern.pattern)">
                      测试
                    </el-button>
                    <el-button link type="danger" @click="removePattern(index)">
                      删除
                    </el-button>
                  </div>
                </div>
              </template>

              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="规则名称">
                    <el-input v-model="pattern.name" placeholder="规则名称" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="优先级">
                    <el-input-number v-model="pattern.priority" :min="0" :max="1000" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="正则表达式" required>
                <el-input
                  v-model="pattern.pattern"
                  type="textarea"
                  :rows="2"
                  placeholder="输入正则表达式，例如：\b[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9]{5,6}\b"
                  font-family="monospace"
                />
              </el-form-item>

              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="信心度">
                    <el-slider
                      v-model="pattern.confidence"
                      :min="0"
                      :max="1"
                      :step="0.05"
                      :format-tooltip="(val: number) => `${Math.round(val * 100)}%`"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="验证类型">
                    <el-select v-model="pattern.validationType" style="width: 100%">
                      <el-option label="无验证" value="none" />
                      <el-option label="校验位验证" value="checksum" />
                      <el-option label="Luhn算法" value="luhn" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="启用">
                    <el-switch v-model="pattern.isActive" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="规则描述">
                <el-input v-model="pattern.description" placeholder="描述该规则的匹配内容" />
              </el-form-item>
            </el-card>
          </div>

          <el-button type="dashed" style="width: 100%" @click="addPattern">
            <el-icon><Plus /></el-icon>
            添加规则
          </el-button>
        </div>

        <!-- 选项配置（折叠收纳） -->
        <el-collapse style="margin-top: 16px;">
          <el-collapse-item title="选项配置（可选）" name="options">
        <div class="options-section">
          <el-table :data="form.options" size="small" v-if="form.options.length > 0">
            <el-table-column label="选项键" width="150">
              <template #default="{ row }">
                <el-input v-model="row.optionKey" size="small" placeholder="optionKey" />
              </template>
            </el-table-column>
            <el-table-column label="选项名称" width="150">
              <template #default="{ row }">
                <el-input v-model="row.optionName" size="small" placeholder="显示名称" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <el-select v-model="row.optionType" size="small">
                  <el-option label="布尔" value="boolean" />
                  <el-option label="字符串" value="string" />
                  <el-option label="数字" value="number" />
                  <el-option label="下拉" value="select" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="默认值" width="120">
              <template #default="{ row }">
                <el-input v-model="row.defaultValue" size="small" placeholder="默认值" />
              </template>
            </el-table-column>
            <el-table-column label="描述">
              <template #default="{ row }">
                <el-input v-model="row.description" size="small" placeholder="选项描述" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeOption($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button type="dashed" style="width: 100%; margin-top: 12px;" @click="addOption">
            <el-icon><Plus /></el-icon>
            添加选项
          </el-button>
        </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="测试正则表达式" width="600px">
      <el-form label-width="100px">
        <el-form-item label="正则表达式">
          <el-input v-model="testPattern" type="textarea" :rows="2" readonly />
        </el-form-item>
        <el-form-item label="测试文本">
          <el-input
            v-model="testText"
            type="textarea"
            :rows="4"
            placeholder="输入要测试的文本"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runTest">执行测试</el-button>
        </el-form-item>
        <el-form-item label="匹配结果" v-if="testResults.length > 0">
          <div class="test-results">
            <el-tag
              v-for="(result, index) in testResults"
              :key="index"
              style="margin: 4px;"
            >
              {{ result }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped>
.extractors-page {
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

.extractors-container {
  margin-bottom: 20px;
}

.extractor-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-icon {
  color: #E6A23C;
}

.custom-icon {
  color: #67C23A;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.patterns-section {
  margin-bottom: 20px;
}

.pattern-item {
  margin-bottom: 16px;
}

.pattern-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.options-section {
  margin-bottom: 20px;
}

.test-results {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  min-height: 40px;
}

/* ========== AI辅助样式 ========== */
.ai-assist-section {
  padding: 8px 0;
}

.ai-input-section {
  margin-bottom: 12px;
}

.ai-options {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.ai-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.ai-result-section {
  margin-top: 16px;
}

.ai-explanation {
  margin-bottom: 16px;
}

.ai-pattern-card {
  margin-bottom: 12px;
}

.ai-pattern-card:last-child {
  margin-bottom: 0;
}

.pattern-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pattern-field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.pattern-field label {
  min-width: 80px;
  font-weight: 500;
  color: #606266;
  font-size: 13px;
  line-height: 1.8;
}

.regex-code {
  flex: 1;
  padding: 6px 12px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  color: #303133;
  word-break: break-all;
}

.ai-apply-tip {
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 13px;
}
</style>
