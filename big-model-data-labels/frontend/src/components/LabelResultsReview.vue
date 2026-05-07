<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Edit,
  Check,
  Warning,
  Filter,
  Download,
  Setting,
  View
} from '@element-plus/icons-vue'

import type { LabelResult, LabelResultStatistics } from '../api/labelResults'
import * as labelResultsApi from '../api/labelResults'
import type { AnalysisTask, TaskLabel } from '../api/analysisTasks'

const props = defineProps<{
  task: AnalysisTask
}>()

// 状态
const loading = ref(false)
const results = ref<LabelResult[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

// 筛选条件
const filterLabelId = ref<number | ''>('')
const filterResult = ref<string | ''>('')
const filterNeedsReview = ref<boolean | ''>('')
const filterIsModified = ref<boolean | ''>('')

// 统计数据
const statistics = ref<LabelResultStatistics[]>([])

// 编辑状态
const editingId = ref<number | null>(null)
const editingResult = ref<string>('')
const editingThreshold = ref<number>(80)
const editingExtractedData = ref<Record<string, string>>({})
const editingLabelType = ref<string>('')

// 批量阈值设置
const batchThresholdDialogVisible = ref(false)
const batchThreshold = ref(80)
const batchLabelId = ref<number | ''>('')

// 详情弹窗状态
const detailDialogVisible = ref(false)
const detailDialogTitle = ref('')
const detailDialogContent = ref('')
const detailDialogType = ref<'originalData' | 'aiReason'>('originalData')

// 证据抽屉（规则证据/结构化输出）
const evidenceDrawerVisible = ref(false)
const evidenceDrawerTitle = ref('')
const evidenceRow = ref<LabelResult | null>(null)
const evidenceMaskOutput = ref(false)

// 计算属性
const availableLabels = computed(() => props.task.labels || [])

const resultOptions = [
  { label: '全部', value: '' },
  { label: '是', value: '是' },
  { label: '否', value: '否' },
]

const reviewOptions = [
  { label: '全部', value: '' },
  { label: '待审核', value: true },
  { label: '已通过', value: false },
]

const modifiedOptions = [
  { label: '全部', value: '' },
  { label: '已修改', value: true },
  { label: '未修改', value: false },
]

// 加载结果
async function loadResults() {
  loading.value = true
  try {
    const resp = await labelResultsApi.listLabelResults({
      analysisTaskId: props.task.id,
      labelId: filterLabelId.value || undefined,
      result: filterResult.value || undefined,
      needsReview: filterNeedsReview.value === '' ? undefined : filterNeedsReview.value,
      isModified: filterIsModified.value === '' ? undefined : filterIsModified.value,
      page: page.value,
      size: size.value
    })
    results.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载结果失败')
  } finally {
    loading.value = false
  }
}

// 加载统计
async function loadStatistics() {
  try {
    statistics.value = await labelResultsApi.getLabelResultStatistics(props.task.id)
  } catch (e: any) {
    console.error('加载统计失败:', e)
  }
}

// 开始编辑
function startEdit(result: LabelResult) {
  editingId.value = result.id
  editingResult.value = result.result || ''
  editingLabelType.value = result.labelType || 'classification'
  // 将小数形式（0-1）转换为百分比形式（0-100）用于编辑
  editingThreshold.value = Math.round((result.confidenceThreshold || 0) * 100)
  // 如果是提取类型，复制提取数据用于编辑
  if (result.labelType === 'extraction' && result.extractedData) {
    editingExtractedData.value = { ...result.extractedData } as Record<string, string>
  } else {
    editingExtractedData.value = {}
  }
}

// 取消编辑
function cancelEdit() {
  editingId.value = null
  editingResult.value = ''
  editingThreshold.value = 80
  editingExtractedData.value = {}
  editingLabelType.value = ''
}

// 保存编辑
async function saveEdit(result: LabelResult) {
  try {
    if (result.labelType === 'extraction') {
      // 提取类型：生成摘要并保存提取数据
      const summary = Object.entries(editingExtractedData.value)
        .filter(([, v]) => v)
        .map(([k, v]) => `${k}: ${v}`)
        .slice(0, 3)
        .join('; ') || '未提取到信息'

      await labelResultsApi.updateLabelResult(result.id, {
        result: summary,
        extractedData: editingExtractedData.value,
        confidenceThreshold: editingThreshold.value / 100
      })
    } else {
      // 分类类型：保存是/否结果
      await labelResultsApi.updateLabelResult(result.id, {
        result: editingResult.value,
        confidenceThreshold: editingThreshold.value / 100
      })
    }
    ElMessage.success('保存成功')
    cancelEdit()
    await loadResults()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// 快速修改结果
async function quickUpdateResult(result: LabelResult, newValue: string) {
  try {
    await labelResultsApi.updateLabelResult(result.id, {
      result: newValue
    })
    ElMessage.success('已更新')
    await loadResults()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

// 批量更新阈值
async function batchUpdateThreshold() {
  try {
    const count = await labelResultsApi.batchUpdateThreshold({
      analysisTaskId: props.task.id,
      labelId: batchLabelId.value || undefined,
      // 将百分比形式（0-100）转换为小数形式（0-1）发送给后端
      threshold: batchThreshold.value / 100
    })
    ElMessage.success(`已更新 ${count} 条记录的阈值`)
    batchThresholdDialogVisible.value = false
    await loadResults()
    await loadStatistics()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量更新失败')
  }
}

// 重置筛选
function resetFilters() {
  filterLabelId.value = ''
  filterResult.value = ''
  filterNeedsReview.value = ''
  filterIsModified.value = ''
  page.value = 1
  loadResults()
}

// 信心度颜色（confidence 是 0-1 的小数）
const confidenceColor = (confidence: number | null) => {
  if (confidence === null) return '#909399'
  if (confidence >= 0.8) return '#67c23a'
  if (confidence >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

// 显示原始数据详情
function showOriginalDataDetail(row: LabelResult) {
  detailDialogType.value = 'originalData'
  detailDialogTitle.value = `原始数据详情 - 行 #${row.rowIndex}`
  detailDialogContent.value = JSON.stringify(row.originalData, null, 2)
  detailDialogVisible.value = true
}

// 显示AI分析原因详情
function showAiReasonDetail(row: LabelResult) {
  detailDialogType.value = 'aiReason'
  detailDialogTitle.value = `AI分析原因 - ${row.labelName} (行 #${row.rowIndex})`
  detailDialogContent.value = row.aiReason || ''
  detailDialogVisible.value = true
}

function openEvidenceDrawer(row: LabelResult) {
  evidenceRow.value = row
  evidenceDrawerTitle.value = `证据 - ${row.labelName} (行 #${row.rowIndex ?? '-'})`
  evidenceMaskOutput.value = false
  evidenceDrawerVisible.value = true
}

function maskSensitiveText(text: string): string {
  if (!text) return ''
  if (text.includes('*')) return text

  const s = text.trim()
  const len = s.length
  if (len <= 4) return '*'.repeat(len)

  const head = len >= 12 ? 4 : 2
  const tail = 4
  if (len <= head + tail) {
    return '*'.repeat(Math.max(0, len - tail)) + s.slice(-tail)
  }
  return s.slice(0, head) + '*'.repeat(len - head - tail) + s.slice(-tail)
}

const evidenceData = computed(() => {
  return (evidenceRow.value?.extractedData || null) as any
})

const evidenceItems = computed(() => {
  const d = evidenceData.value
  if (!d || typeof d !== 'object') return []
  const items = (d as any).items
  return Array.isArray(items) ? items : []
})

const evidenceCounts = computed(() => {
  const d = evidenceData.value
  if (!d || typeof d !== 'object') return null
  const c = (d as any).counts
  return c && typeof c === 'object' ? c : null
})

const evidenceEntity = computed(() => {
  const d = evidenceData.value
  const v = d && typeof d === 'object' ? (d as any).entity : null
  return v == null ? '' : String(v)
})

const evidenceTask = computed(() => {
  const d = evidenceData.value
  const v = d && typeof d === 'object' ? (d as any).task : null
  return v == null ? '' : String(v)
})

const evidenceNeedsReview = computed(() => {
  const d = evidenceData.value
  const v = d && typeof d === 'object' ? (d as any).needs_review : null
  return v === true
})

function displayEvidenceValue(v: unknown): string {
  const text = v == null ? '' : String(v)
  return evidenceMaskOutput.value ? maskSensitiveText(text) : text
}

function buildEvidenceReasoningText(): string {
  const text = evidenceRow.value?.aiReason || ''
  if (!evidenceMaskOutput.value) return text

  // 仅基于“证据项”做精确替换，避免过宽正则误伤。
  let out = text
  const values = evidenceItems.value
    .map((it: any) => (it ? String(it.value ?? '') : ''))
    .filter((v: string) => v && !v.includes('*'))
  for (const v of values) {
    const masked = maskSensitiveText(v)
    if (masked !== v) {
      out = out.split(v).join(masked)
    }
  }
  return out
}

const evidenceReasoningText = computed(() => buildEvidenceReasoningText())

const evidenceDataJsonText = computed(() => {
  const d = evidenceData.value
  if (!d) return ''
  try {
    return JSON.stringify(d, null, 2)
  } catch {
    return String(d)
  }
})

// 复制内容到剪贴板
async function copyToClipboard() {
  try {
    await navigator.clipboard.writeText(detailDialogContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

// 监听筛选变化
watch([filterLabelId, filterResult, filterNeedsReview, filterIsModified], () => {
  page.value = 1
  loadResults()
})

// 监听任务ID变化，重新加载数据（修复数据串乱问题）
watch(() => props.task.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    // 重置筛选条件
    filterLabelId.value = ''
    filterResult.value = ''
    filterNeedsReview.value = ''
    filterIsModified.value = ''
    page.value = 1
    // 重新加载数据
    loadResults()
    loadStatistics()
  }
}, { immediate: false })

onMounted(() => {
  loadResults()
  loadStatistics()
})
</script>

<template>
  <div class="label-results-review">
    <!-- 统计卡片 -->
    <div class="statistics-cards" v-if="statistics.length > 0">
      <div 
        v-for="stat in statistics" 
        :key="stat.labelId" 
        class="stat-card"
      >
        <div class="stat-header">
          <span class="stat-label-name">{{ stat.labelName }}</span>
        </div>
        <div class="stat-body">
          <div class="stat-item">
            <span class="stat-value success">{{ stat.yesCount }}</span>
            <span class="stat-desc">是</span>
          </div>
          <div class="stat-item">
            <span class="stat-value danger">{{ stat.noCount }}</span>
            <span class="stat-desc">否</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ (stat.hitRate * 100).toFixed(1) }}%</span>
            <span class="stat-desc">命中率</span>
          </div>
          <div class="stat-item">
            <span class="stat-value warning">{{ stat.needsReviewCount }}</span>
            <span class="stat-desc">待审核</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="filterLabelId" placeholder="标签" style="width: 150px" clearable>
          <el-option 
            v-for="label in availableLabels" 
            :key="label.labelId" 
            :label="label.labelName" 
            :value="label.labelId" 
          />
        </el-select>
        <el-select v-model="filterResult" placeholder="结果" style="width: 100px" clearable>
          <el-option v-for="opt in resultOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="filterNeedsReview" placeholder="审核状态" style="width: 120px" clearable>
          <el-option v-for="opt in reviewOptions" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="filterIsModified" placeholder="修改状态" style="width: 120px" clearable>
          <el-option v-for="opt in modifiedOptions" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="filter-right">
        <el-button :icon="Setting" @click="batchThresholdDialogVisible = true">
          批量设置阈值
        </el-button>
      </div>
    </div>

    <!-- 结果表格 -->
    <el-table :data="results" v-loading="loading" style="width: 100%" border>
      <el-table-column type="index" label="#" width="60" fixed />
      <el-table-column prop="rowIndex" label="行号" width="80" />
      <el-table-column prop="labelName" label="标签" width="150" />
      <el-table-column label="结果" min-width="200">
        <template #default="{ row }">
          <!-- 提取类型标签 -->
          <template v-if="row.labelType === 'extraction'">
            <template v-if="editingId === row.id">
              <div class="extracted-edit-form">
                <div v-for="(value, key) in editingExtractedData" :key="key" class="extract-field-row">
                  <span class="field-label">{{ key }}:</span>
                  <el-input v-model="editingExtractedData[key]" size="small" style="width: 150px" />
                </div>
              </div>
            </template>
            <template v-else>
              <div class="extracted-fields" v-if="row.extractedData">
                <div v-for="(value, key) in row.extractedData" :key="key" class="extract-field-item">
                  <span class="field-name">{{ key }}:</span>
                  <span class="field-value" :class="{ 'no-value': !value }">{{ value || '-' }}</span>
                </div>
              </div>
              <span v-else class="no-value">{{ row.result || '提取失败' }}</span>
            </template>
          </template>
          <!-- 分类类型标签 -->
          <template v-else>
            <template v-if="editingId === row.id">
              <el-select v-model="editingResult" size="small" style="width: 80px">
                <el-option label="是" value="是" />
                <el-option label="否" value="否" />
              </el-select>
            </template>
            <template v-else>
              <el-tag
                :type="row.result === '是' ? 'success' : row.result === '否' ? 'danger' : 'info'"
                :class="{ 'modified-tag': row.isModified }"
              >
                {{ row.result || '-' }}
                <span v-if="row.isModified" class="modified-mark">*</span>
              </el-tag>
            </template>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="AI信心度" width="140">
        <template #default="{ row }">
          <div class="confidence-cell">
            <el-progress
              :percentage="Math.round((row.aiConfidence || 0) * 100)"
              :stroke-width="8"
              :color="confidenceColor(row.aiConfidence)"
              style="width: 80px"
            />
            <span class="confidence-value">{{ row.aiConfidence != null ? Math.round(row.aiConfidence * 100) : '-' }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="阈值" width="100">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-input-number
              v-model="editingThreshold"
              :min="0"
              :max="100"
              size="small"
              style="width: 80px"
            />
          </template>
          <template v-else>
            {{ Math.round((row.confidenceThreshold || 0) * 100) }}%
          </template>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.needsReview" type="warning" size="small">
            <el-icon><Warning /></el-icon>
            待审核
          </el-tag>
          <el-tag v-else type="success" size="small">
            <el-icon><Check /></el-icon>
            已通过
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原始数据" min-width="220">
        <template #default="{ row }">
          <div class="data-cell">
            <span class="original-data" :title="JSON.stringify(row.originalData || {})">
              {{ JSON.stringify(row.originalData || {}).substring(0, 80) }}{{ JSON.stringify(row.originalData || {}).length > 80 ? '...' : '' }}
            </span>
            <el-button
              v-if="row.originalData"
              type="primary"
              link
              size="small"
              class="view-detail-btn"
              @click.stop="showOriginalDataDetail(row)"
              title="查看完整内容"
            >
              <el-icon><View /></el-icon>
              <span class="btn-text">查看</span>
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="AI分析原因" min-width="220">
        <template #default="{ row }">
          <div class="data-cell" v-if="row.aiReason">
            <span class="ai-reason" :title="row.aiReason">
              {{ row.aiReason.substring(0, 60) }}{{ row.aiReason.length > 60 ? '...' : '' }}
            </span>
            <el-button
              type="primary"
              link
              size="small"
              class="view-detail-btn"
              @click.stop="showAiReasonDetail(row)"
              title="查看完整原因"
            >
              <el-icon><View /></el-icon>
              <span class="btn-text">查看</span>
            </el-button>
          </div>
          <span v-else class="no-reason">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-button size="small" type="success" @click="saveEdit(row)">保存</el-button>
            <el-button size="small" @click="cancelEdit">取消</el-button>
          </template>
          <template v-else>
            <el-button
              size="small"
              :icon="View"
              @click="openEvidenceDrawer(row)"
              :disabled="!row.extractedData && !row.aiReason"
            >
              证据
            </el-button>
            <el-button size="small" :icon="Edit" @click="startEdit(row)">编辑</el-button>
            <!-- 只有分类类型才显示快速修改 -->
            <el-dropdown v-if="row.labelType !== 'extraction'" trigger="click" @command="(cmd: string) => quickUpdateResult(row, cmd)">
              <el-button size="small">快速修改</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="是">设为"是"</el-dropdown-item>
                  <el-dropdown-item command="否">设为"否"</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadResults"
        @size-change="loadResults"
      />
    </div>

    <!-- 批量设置阈值弹窗 -->
    <el-dialog v-model="batchThresholdDialogVisible" title="批量设置信心度阈值" width="400px">
      <el-form label-width="100px">
        <el-form-item label="应用范围">
          <el-select v-model="batchLabelId" placeholder="全部标签" style="width: 100%" clearable>
            <el-option label="全部标签" :value="''" />
            <el-option 
              v-for="label in availableLabels" 
              :key="label.labelId" 
              :label="label.labelName" 
              :value="label.labelId" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新阈值">
          <el-slider v-model="batchThreshold" :min="0" :max="100" :step="5" show-input />
        </el-form-item>
        <el-form-item>
          <div class="batch-hint">
            <el-icon><Warning /></el-icon>
            修改阈值后，系统将重新计算所有结果的"待审核"状态
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchThresholdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="batchUpdateThreshold">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 详情查看弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="detailDialogTitle"
      width="600px"
      :close-on-click-modal="true"
    >
      <div class="detail-dialog-content">
        <template v-if="detailDialogType === 'originalData'">
          <pre class="json-content">{{ detailDialogContent }}</pre>
        </template>
        <template v-else>
          <div class="ai-reason-content">
            {{ detailDialogContent }}
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyToClipboard">
          复制内容
        </el-button>
      </template>
    </el-dialog>

    <!-- 证据抽屉（规则证据/结构化输出） -->
    <el-drawer
      v-model="evidenceDrawerVisible"
      :title="evidenceDrawerTitle"
      size="560px"
      :with-header="true"
    >
      <div class="evidence-drawer">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="默认展示明文，仅用于复核"
          description="如需脱敏显示，请开启“脱敏”开关；请避免在截图/导出/日志中传播敏感信息。"
        />

        <div class="evidence-toolbar">
          <el-switch
            v-model="evidenceMaskOutput"
            inline-prompt
            active-text="脱敏"
            inactive-text="明文"
          />
          <el-tag v-if="evidenceNeedsReview" type="warning">needs_review</el-tag>
        </div>

        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="标签">{{ evidenceRow?.labelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="行号">#{{ evidenceRow?.rowIndex ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ evidenceRow?.result || '-' }}</el-descriptions-item>
          <el-descriptions-item label="置信度">
            {{ evidenceRow?.aiConfidence == null ? '-' : `${Math.round(evidenceRow.aiConfidence * 100)}%` }}
          </el-descriptions-item>
          <el-descriptions-item label="entity">{{ evidenceEntity || '-' }}</el-descriptions-item>
          <el-descriptions-item label="task">{{ evidenceTask || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计数" :span="2">
            <span v-if="evidenceCounts">
              valid={{ (evidenceCounts as any).valid ?? 0 }}，
              invalid={{ (evidenceCounts as any).invalid ?? 0 }}，
              masked={{ (evidenceCounts as any).masked ?? 0 }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="evidence-section">
          <div class="evidence-section-title">规则推理</div>
          <div v-if="evidenceRow?.aiReason && evidenceRow.aiReason.trim().length > 0" class="ai-reason-content">
            {{ evidenceReasoningText }}
          </div>
          <div v-else class="evidence-empty">-</div>
        </div>

        <div class="evidence-section">
          <div class="evidence-section-title">结构化证据</div>

          <el-table
            v-if="evidenceItems.length > 0"
            :data="evidenceItems"
            size="small"
            style="width: 100%"
          >
            <el-table-column label="值" min-width="220">
              <template #default="{ row }">
                {{ displayEvidenceValue(row.value) }}
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="180" />
            <el-table-column label="关键词窗" min-width="120">
              <template #default="{ row }">
                {{ row.keywordHint || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="confidenceRule" label="规则置信" width="90" />
          </el-table>

          <el-empty v-else description="无证据项" />

          <el-collapse v-if="evidenceDataJsonText" class="evidence-raw">
            <el-collapse-item title="原始JSON（用于审计/排查）">
              <pre class="json-content">{{ evidenceDataJsonText }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.label-results-review {
  padding: 16px 0;
}

.statistics-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
}

.stat-header {
  margin-bottom: 8px;
}

.stat-label-name {
  font-weight: 600;
  font-size: 14px;
}

.stat-body {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.danger {
  color: #f56c6c;
}

.stat-value.warning {
  color: #e6a23c;
}

.stat-desc {
  font-size: 12px;
  color: #909399;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filter-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.confidence-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confidence-value {
  font-size: 12px;
  min-width: 35px;
}

.modified-tag {
  border-style: dashed;
}

.modified-mark {
  color: #e6a23c;
  margin-left: 2px;
}

.original-data {
  font-size: 12px;
  color: #666;
}

.ai-reason {
  font-size: 12px;
  color: #409eff;
}

.no-reason {
  font-size: 12px;
  color: #909399;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 证据抽屉 */
.evidence-drawer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.evidence-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.evidence-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.evidence-section-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.evidence-empty {
  color: #909399;
  font-size: 12px;
}

.evidence-raw {
  margin-top: 8px;
}

.batch-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

/* 提取字段样式 */
.extracted-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.extract-field-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.field-name {
  color: #909399;
  flex-shrink: 0;
}

.field-value {
  color: #303133;
  font-weight: 500;
}

.field-value.no-value {
  color: #c0c4cc;
  font-weight: normal;
}

.extracted-edit-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.extract-field-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-label {
  font-size: 12px;
  color: #606266;
  min-width: 60px;
}

.no-value {
  font-size: 12px;
  color: #c0c4cc;
}

/* 数据单元格样式 */
.data-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-cell .original-data,
.data-cell .ai-reason {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.view-detail-btn {
  flex-shrink: 0;
  padding: 4px 8px;
  opacity: 0.8;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 12px;
}

.view-detail-btn:hover {
  opacity: 1;
  background: #409eff;
  color: #fff;
}

.view-detail-btn .btn-text {
  font-size: 12px;
}

/* 详情弹窗样式 */
.detail-dialog-content {
  max-height: 400px;
  overflow-y: auto;
}

.json-content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  color: #303133;
}

.ai-reason-content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
