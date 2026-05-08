<script setup lang="ts">
import { ref, computed, watch, onUnmounted, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  VideoPlay,
  VideoPause,
  Close,
  Refresh,
  Warning,
  CircleCheck,
  CircleClose,
  Loading,
  Connection,
  ChatDotRound,
  Document,
  Download,
  Plus,
  Delete,
  Setting,
  View
} from '@element-plus/icons-vue'

import type {
  AnalysisTask,
  AnalysisTaskProgress,
  AnalysisTaskStatus,
  AnalysisProcess,
  AnalyzingLabel,
  AnalysisLogEntry
} from '../api/analysisTasks'
import * as analysisTasksApi from '../api/analysisTasks'
import type { TaskExecutionLog } from '../api/executionLogs'
import * as executionLogsApi from '../api/executionLogs'
import type { LabelResult } from '../api/labelResults'
import * as labelResultsApi from '../api/labelResults'
import type { Label } from '../api/labels'
import * as labelsApi from '../api/labels'

const props = defineProps<{
  task: AnalysisTask | null
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'task-updated'): void
}>()

// 状态
const activeTab = ref('process')
const progress = ref<AnalysisTaskProgress | null>(null)
const logs = ref<TaskExecutionLog[]>([])
const logsLoading = ref(false)
const logsPage = ref(1)
const logsTotal = ref(0)

// 分析过程状态
const analysisProcess = ref<AnalysisProcess | null>(null)
const processLoading = ref(false)

// 数据表格状态
const dataTableLoading = ref(false)
const dataTableRows = ref<any[]>([])
const dataTablePage = ref(1)
const dataTableSize = ref(20)
const dataTableTotal = ref(0)
const exporting = ref(false)

// 标签选择状态
const availableLabels = ref<Label[]>([])
const globalLabels = ref<Label[]>([])
const datasetLabels = ref<Label[]>([])
const taskLabels = ref<Label[]>([])
const selectedLabelIds = ref<number[]>([])
const labelsLoading = ref(false)

// 显示选项
const showReasoning = ref(false) // 是否显示判定原因
const displayMode = ref<'result' | 'result_with_reason'>('result') // 显示模式

// 详情弹窗状态
const detailDialogVisible = ref(false)
const detailDialogTitle = ref('')
const detailDialogContent = ref('')
const detailDialogType = ref<'originalData' | 'aiReason'>('originalData')

// 轮询定时器
let pollTimer: number | null = null

// 计算属性
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const isRunning = computed(() => 
  props.task?.status === 'processing'
)

// 获取标签列（动态生成）
const labelColumns = computed(() => {
  if (!analysisProcess.value?.analyzingLabels) return []
  return analysisProcess.value.analyzingLabels.map(label => ({
    labelId: label.labelId,
    labelName: label.labelName,
    labelKey: `${label.labelName}_v${label.labelVersion}`
  }))
})

// 获取选中的标签
const selectedLabels = computed(() => {
  return availableLabels.value.filter(l => selectedLabelIds.value.includes(l.id))
})

// 按作用域分组的标签
const labelsByScope = computed(() => {
  return {
    global: globalLabels.value,
    dataset: datasetLabels.value,
    task: taskLabels.value
  }
})

const statusTagType = (s: AnalysisTaskStatus) => {
  switch (s) {
    case 'pending': return 'info'
    case 'processing': return 'warning'
    case 'paused': return 'warning'
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'cancelled': return 'info'
    default: return 'info'
  }
}

const statusLabel = (s: AnalysisTaskStatus) => {
  switch (s) {
    case 'pending': return '待启动'
    case 'processing': return '进行中'
    case 'paused': return '已暂停'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    case 'cancelled': return '已取消'
    default: return s
  }
}

const logLevelIcon = (level: string) => {
  switch (level) {
    case 'INFO': return CircleCheck
    case 'WARN': return Warning
    case 'ERROR': return CircleClose
    default: return CircleCheck
  }
}

const logLevelColor = (level: string) => {
  switch (level) {
    case 'INFO': return '#67c23a'
    case 'WARN': return '#e6a23c'
    case 'ERROR': return '#f56c6c'
    default: return '#909399'
  }
}

const formatTime = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit' 
  })
}

// 加载可用标签
async function loadAvailableLabels() {
  if (!props.task) return
  labelsLoading.value = true
  try {
    // 获取全局标签
    globalLabels.value = await labelsApi.getGlobalLabels()
    
    // 获取数据集专属标签
    if (props.task.datasetId) {
      datasetLabels.value = await labelsApi.getDatasetLabels(props.task.datasetId)
    }
    
    // 获取任务临时标签（scope=task）
    const taskLabelList = await labelsApi.activeLabels({ scope: 'task' })
    taskLabels.value = taskLabelList.filter(l => l.taskId === props.task?.id)
    
    // 合并所有可用标签
    availableLabels.value = [...globalLabels.value, ...datasetLabels.value, ...taskLabels.value]
    
    // 默认选中任务已关联的标签
    if (props.task.labels && props.task.labels.length > 0) {
      selectedLabelIds.value = props.task.labels.map(l => l.labelId)
    }
  } catch (e: any) {
    console.error('加载标签失败:', e)
  } finally {
    labelsLoading.value = false
  }
}

// 加载进度（支持首次加载显示loading）
async function loadProgress(showLoading = false) {
  if (!props.task) return

  if (showLoading) {
    processLoading.value = true
  }

  try {
    const newProgress = await analysisTasksApi.getAnalysisTaskProgress(props.task.id)
    // 只在数据真正变化时才更新（避免不必要的重新渲染）
    if (JSON.stringify(newProgress) !== JSON.stringify(progress.value)) {
      progress.value = newProgress
    }
  } catch (e: any) {
    // 静默失败，不打印错误（避免控制台刷屏）
    console.debug('加载进度失败:', e)
  } finally {
    if (showLoading) {
      processLoading.value = false
    }
  }
}

// 加载分析过程（支持首次加载显示loading）
async function loadAnalysisProcess(showLoading = false) {
  if (!props.task) return

  if (showLoading) {
    processLoading.value = true
  }

  try {
    const newProcess = await analysisTasksApi.getAnalysisProcess(props.task.id)
    // 只在数据真正变化时才更新
    if (JSON.stringify(newProcess) !== JSON.stringify(analysisProcess.value)) {
      analysisProcess.value = newProcess
    }
  } catch (e: any) {
    // 静默失败
    console.debug('加载分析过程失败:', e)
  } finally {
    if (showLoading) {
      processLoading.value = false
    }
  }
}

// 加载日志
async function loadLogs() {
  if (!props.task) return
  logsLoading.value = true
  try {
    const resp = await executionLogsApi.listExecutionLogs({
      analysisTaskId: props.task.id,
      page: logsPage.value,
      size: 50
    })
    logs.value = resp.items
    logsTotal.value = resp.total
  } catch (e: any) {
    console.error('加载日志失败:', e)
  } finally {
    logsLoading.value = false
  }
}

// 加载数据表格
async function loadDataTable() {
  if (!props.task) return
  dataTableLoading.value = true
  try {
    // 使用新的按数据行分页接口
    const resp = await labelResultsApi.listLabelResultsByRow({
      analysisTaskId: props.task.id,
      page: dataTablePage.value,
      size: dataTableSize.value
    })

    // 直接使用返回的数据，已经按数据行分组
    dataTableRows.value = resp.items.map(item => ({
      rowId: item.rowId,
      rowIndex: item.rowIndex,
      originalData: item.originalData || {},
      labelResults: item.labelResults || {}
    }))
    dataTableTotal.value = resp.total
  } catch (e: any) {
    console.error('加载数据表格失败:', e)
  } finally {
    dataTableLoading.value = false
  }
}

// 切换显示模式（监听 showReasoning 变化自动更新 displayMode）
// 注意：v-model 已经处理了 showReasoning 的切换，不需要手动切换
watch(showReasoning, (val) => {
  displayMode.value = val ? 'result_with_reason' : 'result'
})

// 选择/取消选择标签
function toggleLabelSelection(labelId: number) {
  const index = selectedLabelIds.value.indexOf(labelId)
  if (index > -1) {
    selectedLabelIds.value.splice(index, 1)
  } else {
    selectedLabelIds.value.push(labelId)
  }
}

// 全选/取消全选某个作用域的标签
function toggleScopeLabels(scope: 'global' | 'dataset' | 'task', select: boolean) {
  const scopeLabels = labelsByScope.value[scope]
  if (select) {
    scopeLabels.forEach(l => {
      if (!selectedLabelIds.value.includes(l.id)) {
        selectedLabelIds.value.push(l.id)
      }
    })
  } else {
    scopeLabels.forEach(l => {
      const index = selectedLabelIds.value.indexOf(l.id)
      if (index > -1) {
        selectedLabelIds.value.splice(index, 1)
      }
    })
  }
}

// 获取标签作用域的显示名称
function getScopeLabel(scope: string): string {
  switch (scope) {
    case 'global': return '全局标签'
    case 'dataset': return '数据集标签'
    case 'task': return '临时标签'
    default: return scope
  }
}

// 获取标签作用域的标签类型
function getScopeTagType(scope: string): string {
  switch (scope) {
    case 'global': return 'primary'
    case 'dataset': return 'success'
    case 'task': return 'warning'
    default: return 'info'
  }
}

// 导出结果
async function exportResults() {
  if (!props.task) return
  exporting.value = true
  try {
    // 传递 showReasoning 参数，如果开启则将判断依据和结果合并到同一单元格
    const blob = await analysisTasksApi.exportAnalysisTask(props.task.id, showReasoning.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const suffix = showReasoning.value ? '-with-reasoning' : ''
    a.download = `analysis-task-${props.task.id}-results${suffix}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(showReasoning.value ? '导出成功（包含判断依据）' : '导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// 获取标签结果的显示样式
function getLabelResultType(result: string | null): string {
  if (result === '是') return 'success'
  if (result === '否') return 'danger'
  return 'info'
}

// 显示原始数据详情
function showOriginalDataDetail(row: any) {
  detailDialogType.value = 'originalData'
  detailDialogTitle.value = `原始数据详情 - 行 #${row.rowIndex}`
  detailDialogContent.value = JSON.stringify(row.originalData, null, 2)
  detailDialogVisible.value = true
}

// 显示AI分析原因详情
function showAiReasonDetail(labelName: string, reason: string, rowIndex: number) {
  detailDialogType.value = 'aiReason'
  detailDialogTitle.value = `AI分析原因 - ${labelName} (行 #${rowIndex})`
  detailDialogContent.value = reason
  detailDialogVisible.value = true
}

// 复制内容到剪贴板
async function copyToClipboard() {
  try {
    await navigator.clipboard.writeText(detailDialogContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

// 启动轮询
function startPolling() {
  if (pollTimer) return

  // 立即执行一次
  if (isRunning.value) {
    loadProgress()
    loadAnalysisProcess()
  }

  // 设置定时器，间隔5秒（降低刷新频率）
  pollTimer = window.setInterval(() => {
    if (isRunning.value) {
      // 只更新进度和分析过程，不刷新数据表格（避免页面闪烁）
      loadProgress()
      loadAnalysisProcess()
    } else {
      // 如果任务已完成，停止轮询
      stopPolling()
    }
  }, 5000) // 从2秒改为5秒
}

// 停止轮询
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 启动任务
async function startTask() {
  if (!props.task) return
  try {
    await analysisTasksApi.startAnalysisTask(props.task.id)
    ElMessage.success('任务已启动')
    emit('task-updated')
    startPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

// 暂停任务
async function pauseTask() {
  if (!props.task) return
  try {
    await analysisTasksApi.pauseAnalysisTask(props.task.id)
    ElMessage.success('任务已暂停')
    emit('task-updated')
    stopPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

// 取消任务
async function cancelTask() {
  if (!props.task) return
  try {
    await analysisTasksApi.cancelAnalysisTask(props.task.id)
    ElMessage.success('任务已取消')
    emit('task-updated')
    stopPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

// 重试失败行
async function retryFailed() {
  if (!props.task) return
  try {
    await analysisTasksApi.retryFailedRows(props.task.id)
    ElMessage.success('已开始重试失败行')
    emit('task-updated')
    startPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '重试失败')
  }
}

// 监听Tab切换
watch(activeTab, (tab) => {
  if (tab === 'data') {
    // 只在数据为空或任务已完成时才自动加载
    // 避免任务进行中频繁切换Tab导致不必要的刷新
    if (dataTableRows.value.length === 0 || !isRunning.value) {
      loadDataTable()
    }
  }
})

// 监听弹窗显示
watch(() => props.visible, (visible) => {
  if (visible && props.task) {
    // 首次加载显示loading状态
    loadProgress(true)
    loadAnalysisProcess(true)
    loadLogs()
    loadAvailableLabels()
    if (isRunning.value) {
      startPolling()
    }
  } else {
    stopPolling()
  }
})

// 监听任务ID变化，重新加载数据（修复数据串乱问题）
watch(() => props.task?.id, (newId, oldId) => {
  if (props.visible && newId && newId !== oldId) {
    // 任务ID变化时重新加载所有数据
    loadProgress(true)
    loadAnalysisProcess(true)
    loadLogs()
    loadAvailableLabels()
    // 如果当前在数据Tab，也要重新加载
    if (activeTab.value === 'data') {
      loadDataTable()
    }
    // 根据新任务状态启动/停止轮询
    if (isRunning.value) {
      startPolling()
    } else {
      stopPolling()
    }
  }
})

// 监听任务状态变化
watch(() => props.task?.status, (status) => {
  if (status === 'processing') {
    startPolling()
  } else {
    stopPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <el-dialog 
    v-model="dialogVisible" 
    :title="task?.name || '任务详情'" 
    width="95%"
    :close-on-click-modal="false"
    top="3vh"
  >
    <template v-if="task">
      <!-- 任务状态栏 -->
      <div class="task-status-bar">
        <div class="status-info">
          <el-tag :type="statusTagType(task.status)" size="large">
            {{ statusLabel(task.status) }}
          </el-tag>
          <span class="task-time" v-if="task.startedAt">
            开始时间: {{ new Date(task.startedAt).toLocaleString('zh-CN') }}
          </span>
          <span class="processing-indicator" v-if="task.status === 'processing'">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在分析中...
          </span>
        </div>
        <div class="status-actions">
          <el-button 
            v-if="task.status === 'pending'" 
            type="success" 
            :icon="VideoPlay"
            @click="startTask"
          >
            启动
          </el-button>
          <el-button 
            v-if="task.status === 'processing'" 
            type="warning" 
            :icon="VideoPause"
            @click="pauseTask"
          >
            暂停
          </el-button>
          <el-button 
            v-if="task.status === 'paused'" 
            type="success" 
            :icon="VideoPlay"
            @click="startTask"
          >
            继续
          </el-button>
          <el-button 
            v-if="['pending', 'processing', 'paused'].includes(task.status)" 
            type="danger" 
            :icon="Close"
            @click="cancelTask"
          >
            取消
          </el-button>
          <el-button 
            v-if="task.failedRows > 0 && ['completed', 'paused'].includes(task.status)" 
            type="warning"
            @click="retryFailed"
          >
            重试失败行 ({{ task.failedRows }})
          </el-button>
          <el-button 
            type="primary"
            :icon="Download"
            @click="exportResults"
            :loading="exporting"
            :disabled="task.processedRows === 0"
          >
            导出结果
          </el-button>
        </div>
      </div>

      <!-- 标签选择区域 -->
      <div class="label-selection-section" v-loading="labelsLoading">
        <div class="section-header">
          <h4>
            <el-icon><Connection /></el-icon>
            选择分析标签
          </h4>
          <div class="display-options">
            <el-switch
              v-model="showReasoning"
              active-text="显示判定原因"
              inactive-text="仅显示结果"
            />
          </div>
        </div>
        
        <!-- 标签分组选择 -->
        <div class="label-groups">
          <!-- 全局标签 -->
          <div class="label-group" v-if="globalLabels.length > 0">
            <div class="group-header">
              <el-tag type="primary" size="small">全局标签</el-tag>
              <span class="group-count">({{ globalLabels.length }})</span>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('global', true)"
              >
                全选
              </el-button>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('global', false)"
              >
                取消
              </el-button>
            </div>
            <div class="label-chips">
              <el-check-tag
                v-for="label in globalLabels"
                :key="label.id"
                :checked="selectedLabelIds.includes(label.id)"
                @change="toggleLabelSelection(label.id)"
                class="label-chip"
              >
                {{ label.name }}
                <span class="label-version">v{{ label.version }}</span>
              </el-check-tag>
            </div>
          </div>
          
          <!-- 数据集标签 -->
          <div class="label-group" v-if="datasetLabels.length > 0">
            <div class="group-header">
              <el-tag type="success" size="small">数据集标签</el-tag>
              <span class="group-count">({{ datasetLabels.length }})</span>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('dataset', true)"
              >
                全选
              </el-button>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('dataset', false)"
              >
                取消
              </el-button>
            </div>
            <div class="label-chips">
              <el-check-tag
                v-for="label in datasetLabels"
                :key="label.id"
                :checked="selectedLabelIds.includes(label.id)"
                @change="toggleLabelSelection(label.id)"
                class="label-chip"
              >
                {{ label.name }}
                <span class="label-version">v{{ label.version }}</span>
              </el-check-tag>
            </div>
          </div>
          
          <!-- 临时标签 -->
          <div class="label-group" v-if="taskLabels.length > 0">
            <div class="group-header">
              <el-tag type="warning" size="small">临时标签</el-tag>
              <span class="group-count">({{ taskLabels.length }})</span>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('task', true)"
              >
                全选
              </el-button>
              <el-button
                link
                size="small"
                @click="toggleScopeLabels('task', false)"
              >
                取消
              </el-button>
            </div>
            <div class="label-chips">
              <el-check-tag
                v-for="label in taskLabels"
                :key="label.id"
                :checked="selectedLabelIds.includes(label.id)"
                @change="toggleLabelSelection(label.id)"
                class="label-chip"
              >
                {{ label.name }}
                <span class="label-version">v{{ label.version }}</span>
              </el-check-tag>
            </div>
          </div>
          
          <el-empty
            v-if="availableLabels.length === 0 && !labelsLoading"
            description="暂无可用标签"
            :image-size="40"
          />
        </div>
        
        <!-- 已选标签摘要 -->
        <div class="selected-labels-summary" v-if="selectedLabels.length > 0">
          <span class="summary-label">已选择 {{ selectedLabels.length }} 个标签:</span>
          <div class="selected-tags">
            <el-tag
              v-for="label in selectedLabels"
              :key="label.id"
              :type="getScopeTagType(label.scope)"
              size="small"
              closable
              @close="toggleLabelSelection(label.id)"
            >
              {{ label.name }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- Tab页 -->
      <el-tabs v-model="activeTab">
        <!-- 分析过程Tab -->
        <el-tab-pane name="process">
          <template #label>
            <span class="tab-label">
              <el-icon><ChatDotRound /></el-icon>
              分析过程
            </span>
          </template>
          
          <div class="process-content" v-loading="processLoading">
            <!-- 当前正在分析的标签列表 -->
            <div class="process-section">
              <h4>
                <el-icon><Connection /></el-icon>
                正在分析的标签
              </h4>
              <div class="analyzing-labels" v-if="analysisProcess?.analyzingLabels?.length">
                <div 
                  v-for="label in analysisProcess.analyzingLabels" 
                  :key="label.labelId"
                  class="analyzing-label-card"
                  :class="{ 'is-processing': label.isProcessing }"
                >
                  <div class="label-card-header">
                    <span class="label-name">{{ label.labelName }}</span>
                    <el-tag size="small" type="info">v{{ label.labelVersion }}</el-tag>
                    <el-tag 
                      v-if="label.isProcessing" 
                      size="small" 
                      type="warning"
                      effect="dark"
                    >
                      <el-icon class="is-loading"><Loading /></el-icon>
                      分析中
                    </el-tag>
                  </div>
                  <div class="label-card-desc" v-if="label.labelDescription">
                    {{ label.labelDescription }}
                  </div>
                  <div class="label-stats">
                    <span class="stat-item">
                      <span class="stat-label">命中数:</span>
                      <span class="stat-value hit">{{ label.hitCount }}</span>
                    </span>
                    <span class="stat-item">
                      <span class="stat-label">命中率:</span>
                      <span class="stat-value">{{ label.hitRate?.toFixed(1) }}%</span>
                    </span>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无标签" :image-size="60" />
            </div>

            <!-- 分析进度 -->
            <div class="process-section">
              <h4>分析进度</h4>
              <div class="progress-info">
                <el-progress 
                  :percentage="analysisProcess?.progressPercent || 0" 
                  :stroke-width="16"
                  :status="task.status === 'failed' ? 'exception' : task.status === 'completed' ? 'success' : undefined"
                />
                <div class="progress-details">
                  <span>已处理: {{ analysisProcess?.processedRows || 0 }} / {{ analysisProcess?.totalRows || 0 }}</span>
                  <span class="success-stat">成功: {{ analysisProcess?.successRows || 0 }}</span>
                  <span class="fail-stat">失败: {{ analysisProcess?.failedRows || 0 }}</span>
                  <span v-if="analysisProcess?.estimatedSecondsRemaining">
                    预计剩余: {{ Math.ceil((analysisProcess.estimatedSecondsRemaining || 0) / 60) }} 分钟
                  </span>
                </div>
              </div>
            </div>

            <!-- 与大模型对话过程 -->
            <div class="process-section">
              <h4>
                <el-icon><ChatDotRound /></el-icon>
                分析日志（与大模型对话过程）
                <el-button 
                  size="small" 
                  :icon="Refresh" 
                  @click="loadAnalysisProcess"
                  style="margin-left: 8px"
                >
                  刷新
                </el-button>
              </h4>
              <div class="ai-conversation-logs">
                <div 
                  v-for="log in analysisProcess?.recentLogs" 
                  :key="log.id"
                  class="conversation-log-item"
                  :class="log.logLevel.toLowerCase()"
                >
                  <div class="log-header">
                    <span class="log-time">{{ log.timeDisplay }}</span>
                    <el-icon :color="logLevelColor(log.logLevel)">
                      <component :is="logLevelIcon(log.logLevel)" />
                    </el-icon>
                    <span class="log-row" v-if="log.rowIndex">行 #{{ log.rowIndex }}</span>
                    <span class="log-label-tag" v-if="log.labelKey">{{ log.labelKey }}</span>
                  </div>
                  <div class="log-message">{{ log.message }}</div>
                  <div class="log-meta" v-if="log.confidence || log.durationMs">
                    <span v-if="log.confidence" class="meta-item confidence">
                      信心度: {{ Math.round((log.confidence || 0) * 100) }}%
                    </span>
                    <span v-if="log.durationMs" class="meta-item duration">
                      耗时: {{ log.durationMs }}ms
                    </span>
                  </div>
                </div>
                <el-empty 
                  v-if="!analysisProcess?.recentLogs?.length" 
                  description="暂无分析日志" 
                  :image-size="60"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 数据结果Tab（新增） -->
        <el-tab-pane name="data">
          <template #label>
            <span class="tab-label">
              <el-icon><Document /></el-icon>
              数据结果
              <el-badge 
                v-if="labelColumns.length > 0" 
                :value="labelColumns.length" 
                type="primary"
                style="margin-left: 4px"
              />
            </span>
          </template>
          
          <div class="data-table-content">
            <div class="data-table-header">
              <div class="header-info">
                <span>共 {{ dataTableTotal }} 条数据</span>
                <span v-if="labelColumns.length > 0" class="label-count-info">
                  | {{ labelColumns.length }} 个标签列
                </span>
                <el-tag
                  v-if="showReasoning"
                  type="info"
                  size="small"
                  style="margin-left: 8px"
                >
                  显示判定原因
                </el-tag>
              </div>
              <div class="header-actions">
                <el-switch
                  v-model="showReasoning"
                  active-text="显示原因"
                  inactive-text="仅结果"
                  size="small"
                  style="margin-right: 12px"
                />
                <el-button :icon="Refresh" size="small" @click="loadDataTable" :loading="dataTableLoading">
                  刷新
                </el-button>
                <el-button
                  type="primary"
                  :icon="Download"
                  size="small"
                  @click="exportResults"
                  :loading="exporting"
                  :disabled="task.processedRows === 0"
                >
                  导出Excel
                </el-button>
              </div>
            </div>

            <el-table
              :data="dataTableRows"
              v-loading="dataTableLoading"
              border
              style="width: 100%"
              max-height="500"
              stripe
            >
              <!-- 空状态插槽 -->
              <template #empty>
                <div class="table-empty-state">
                  <template v-if="isRunning">
                    <el-icon class="is-loading" :size="24" color="#409eff"><Loading /></el-icon>
                    <p>任务正在分析中，数据处理后将自动显示...</p>
                    <p class="empty-state-hint">
                      已处理: {{ analysisProcess?.processedRows || task?.processedRows || 0 }} / {{ analysisProcess?.totalRows || task?.totalRows || 0 }} 行
                    </p>
                  </template>
                  <template v-else-if="task?.status === 'pending'">
                    <el-icon :size="24" color="#909399"><Warning /></el-icon>
                    <p>任务尚未启动</p>
                    <p class="empty-state-hint">请点击"启动"按钮开始分析</p>
                  </template>
                  <template v-else>
                    <p>暂无数据</p>
                  </template>
                </div>
              </template>

              <el-table-column type="index" label="#" width="60" fixed />
              <el-table-column prop="rowIndex" label="行号" width="80" fixed />
              
              <!-- 原始数据列（取前3列展示） -->
              <el-table-column
                v-for="(_, key) in (dataTableRows[0]?.originalData || {})"
                :key="key"
                :label="String(key)"
                min-width="180"
              >
                <template #default="{ row }">
                  <div class="original-data-cell">
                    <span class="cell-text" :title="String(row.originalData?.[key] || '-')">
                      {{ row.originalData?.[key] || '-' }}
                    </span>
                    <el-button
                      v-if="row.originalData?.[key]"
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
              
              <!-- 动态标签结果列 -->
              <template v-for="col in labelColumns" :key="col.labelId">
                <!-- 结果列 -->
                <el-table-column
                  :label="col.labelName"
                  :width="showReasoning ? 100 : 140"
                  align="center"
                >
                  <template #header>
                    <div class="label-column-header">
                      <span>{{ col.labelName }}</span>
                      <el-tooltip content="标签分析结果" placement="top">
                        <el-icon style="margin-left: 4px; color: #909399"><Warning /></el-icon>
                      </el-tooltip>
                    </div>
                  </template>
                  <template #default="{ row }">
                    <div class="label-result-cell" v-if="row.labelResults?.[col.labelName]">
                      <el-tag
                        :type="getLabelResultType(row.labelResults[col.labelName].result)"
                        size="small"
                        :class="{ 'modified-tag': row.labelResults[col.labelName].isModified }"
                      >
                        {{ row.labelResults[col.labelName].result || '-' }}
                      </el-tag>
                      <div class="confidence-mini" v-if="row.labelResults[col.labelName].confidence">
                        {{ Math.round((row.labelResults[col.labelName].confidence || 0) * 100) }}%
                      </div>
                      <el-icon
                        v-if="row.labelResults[col.labelName].needsReview"
                        class="review-icon"
                        color="#e6a23c"
                      >
                        <Warning />
                      </el-icon>
                    </div>
                    <span v-else class="no-result">-</span>
                  </template>
                </el-table-column>
                
                <!-- 判定原因列（可选显示） -->
                <el-table-column
                  v-if="showReasoning"
                  :label="`${col.labelName}-原因`"
                  min-width="200"
                >
                  <template #header>
                    <div class="reason-column-header">
                      <span>判定原因</span>
                    </div>
                  </template>
                  <template #default="{ row }">
                    <div class="reason-cell" v-if="row.labelResults?.[col.labelName]?.aiReason">
                      <span class="reason-text" :title="row.labelResults[col.labelName].aiReason">
                        {{ row.labelResults[col.labelName].aiReason }}
                      </span>
                      <el-button
                        type="primary"
                        link
                        size="small"
                        class="view-detail-btn"
                        @click.stop="showAiReasonDetail(col.labelName, row.labelResults[col.labelName].aiReason, row.rowIndex)"
                        title="查看完整原因"
                      >
                        <el-icon><View /></el-icon>
                        <span class="btn-text">查看</span>
                      </el-button>
                    </div>
                    <span v-else class="no-result">-</span>
                  </template>
                </el-table-column>
              </template>
            </el-table>

            <div class="data-table-pagination">
              <el-pagination
                v-model:current-page="dataTablePage"
                v-model:page-size="dataTableSize"
                :total="dataTableTotal"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                @current-change="loadDataTable"
                @size-change="loadDataTable"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 进度Tab -->
        <el-tab-pane label="执行进度" name="progress">
          <div class="progress-content">
            <!-- 总体进度 -->
            <div class="progress-section">
              <h4>总体进度</h4>
              <el-progress 
                :percentage="progress?.percentage || 0" 
                :stroke-width="20"
                :status="task.status === 'failed' ? 'exception' : task.status === 'completed' ? 'success' : undefined"
              />
              <div class="progress-stats">
                <span>已处理: {{ task.processedRows }} / {{ task.totalRows }}</span>
                <span class="success-stat">成功: {{ task.successRows }}</span>
                <span class="fail-stat">失败: {{ task.failedRows }}</span>
                <span v-if="progress?.etaSeconds">预计剩余: {{ Math.ceil(progress.etaSeconds / 60) }} 分钟</span>
              </div>
            </div>

            <!-- 分标签进度 -->
            <div class="progress-section" v-if="progress?.labelProgress?.length">
              <h4>分标签进度</h4>
              <div class="label-progress-list">
                <div 
                  v-for="lp in progress.labelProgress" 
                  :key="lp.labelId" 
                  class="label-progress-item"
                >
                  <span class="label-name">{{ lp.labelName }}</span>
                  <el-progress 
                    :percentage="lp.percentage" 
                    :stroke-width="10"
                    style="flex: 1"
                  />
                  <span class="label-count">{{ lp.processed }} / {{ lp.total }}</span>
                </div>
              </div>
            </div>

            <!-- 执行统计 -->
            <div class="progress-section">
              <h4>执行统计</h4>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="默认信心阈值">
                  {{ Math.round((task.defaultConfidenceThreshold || 0) * 100) }}%
                </el-descriptions-item>
                <el-descriptions-item label="关联标签数">
                  {{ task.labels?.length || 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="创建时间">
                  {{ new Date(task.createdAt).toLocaleString('zh-CN') }}
                </el-descriptions-item>
                <el-descriptions-item label="完成时间" v-if="task.completedAt">
                  {{ new Date(task.completedAt).toLocaleString('zh-CN') }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-tab-pane>

        <!-- 日志Tab -->
        <el-tab-pane label="执行日志" name="logs">
          <div class="logs-content">
            <div class="logs-header">
              <el-button :icon="Refresh" size="small" @click="loadLogs" :loading="logsLoading">
                刷新
              </el-button>
              <span class="logs-count">共 {{ logsTotal }} 条日志</span>
            </div>
            
            <div class="logs-list" v-loading="logsLoading">
              <div 
                v-for="log in logs" 
                :key="log.id" 
                class="log-item"
                :class="log.logLevel.toLowerCase()"
              >
                <span class="log-time">{{ formatTime(log.createdAt) }}</span>
                <el-icon :color="logLevelColor(log.logLevel)">
                  <component :is="logLevelIcon(log.logLevel)" />
                </el-icon>
                <span class="log-message">{{ log.message }}</span>
                <span class="log-confidence" v-if="log.aiConfidence">
                  信心度: {{ Math.round((log.aiConfidence || 0) * 100) }}%
                </span>
                <span class="log-duration" v-if="log.durationMs">
                  耗时: {{ log.durationMs }}ms
                </span>
              </div>
              
              <el-empty v-if="logs.length === 0 && !logsLoading" description="暂无日志" />
            </div>

            <div class="logs-pagination" v-if="logsTotal > 50">
              <el-pagination
                v-model:current-page="logsPage"
                :total="logsTotal"
                :page-size="50"
                layout="prev, pager, next"
                @current-change="loadLogs"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 标签配置Tab -->
        <el-tab-pane label="标签配置" name="labels">
          <div class="labels-content">
            <div v-if="task.labels?.length" class="labels-list">
              <el-card 
                v-for="label in task.labels" 
                :key="label.id" 
                class="label-card"
                shadow="hover"
              >
                <div class="label-header">
                  <span class="label-name">{{ label.labelName }}</span>
                  <el-tag size="small">v{{ label.labelVersion }}</el-tag>
                </div>
                <div class="label-desc">{{ label.labelDescription }}</div>
              </el-card>
            </div>
            <el-empty v-else description="未配置标签" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 详情查看弹窗 -->
  <el-dialog
    v-model="detailDialogVisible"
    :title="detailDialogTitle"
    width="600px"
    :close-on-click-modal="true"
    append-to-body
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
</template>

<style scoped>
.task-status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.status-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.task-time {
  color: #666;
  font-size: 14px;
}

.processing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #e6a23c;
  font-size: 14px;
}

.status-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 分析过程样式 */
.process-content {
  padding: 16px 0;
}

.process-section {
  margin-bottom: 24px;
}

.process-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 正在分析的标签卡片 */
.analyzing-labels {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.analyzing-label-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 16px;
  transition: all 0.3s;
}

.analyzing-label-card.is-processing {
  border-color: #e6a23c;
  background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%);
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.15);
}

.analyzing-label-card .label-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.analyzing-label-card .label-name {
  font-weight: 600;
  font-size: 15px;
}

.analyzing-label-card .label-card-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  line-height: 1.4;
}

.analyzing-label-card .label-stats {
  display: flex;
  gap: 16px;
}

.analyzing-label-card .stat-item {
  font-size: 13px;
}

.analyzing-label-card .stat-label {
  color: #909399;
}

.analyzing-label-card .stat-value {
  font-weight: 500;
  margin-left: 4px;
}

.analyzing-label-card .stat-value.hit {
  color: #67c23a;
}

/* 进度信息 */
.progress-info {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
}

.progress-details {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  font-size: 14px;
  color: #666;
}

/* AI对话日志 */
.ai-conversation-logs {
  max-height: 350px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.conversation-log-item {
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  transition: background 0.2s;
}

.conversation-log-item:last-child {
  border-bottom: none;
}

.conversation-log-item:hover {
  background: #f5f7fa;
}

.conversation-log-item.error {
  background: #fef0f0;
}

.conversation-log-item.warn {
  background: #fdf6ec;
}

.conversation-log-item .log-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.conversation-log-item .log-time {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}

.conversation-log-item .log-row {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.conversation-log-item .log-label-tag {
  font-size: 12px;
  color: #67c23a;
  background: #f0f9eb;
  padding: 2px 6px;
  border-radius: 4px;
}

.conversation-log-item .log-message {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
}

.conversation-log-item .log-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
}

.conversation-log-item .meta-item {
  font-size: 12px;
}

.conversation-log-item .meta-item.confidence {
  color: #409eff;
}

.conversation-log-item .meta-item.duration {
  color: #909399;
}

/* 数据表格样式 */
.data-table-content {
  padding: 16px 0;
}

.data-table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-info {
  font-size: 14px;
  color: #666;
}

.label-count-info {
  color: #409eff;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.label-column-header {
  display: flex;
  align-items: center;
  justify-content: center;
}

.label-result-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.confidence-mini {
  font-size: 11px;
  color: #909399;
}

.review-icon {
  font-size: 14px;
}

.modified-tag {
  border-style: dashed;
}

.no-result {
  color: #c0c4cc;
}

.data-table-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.progress-content {
  padding: 16px 0;
}

.progress-section {
  margin-bottom: 24px;
}

.progress-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
}

.progress-stats {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  font-size: 14px;
  color: #666;
}

.success-stat {
  color: #67c23a;
}

.fail-stat {
  color: #f56c6c;
}

.label-progress-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.label-progress-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label-name {
  min-width: 120px;
  font-size: 14px;
}

.label-count {
  min-width: 80px;
  text-align: right;
  font-size: 12px;
  color: #666;
}

.logs-content {
  padding: 16px 0;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.logs-count {
  font-size: 14px;
  color: #666;
}

.logs-list {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
}

.log-item:last-child {
  border-bottom: none;
}

.log-item.error {
  background: #fef0f0;
}

.log-item.warn {
  background: #fdf6ec;
}

.log-time {
  color: #999;
  font-family: monospace;
  min-width: 70px;
}

.log-message {
  flex: 1;
}

.log-confidence {
  color: #409eff;
  font-size: 12px;
}

.log-duration {
  color: #909399;
  font-size: 12px;
}

.logs-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

.labels-content {
  padding: 16px 0;
}

.labels-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.label-card {
  cursor: default;
}

.label-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.label-name {
  font-weight: 600;
}

.label-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

/* 标签选择区域样式 */
.label-selection-section {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.label-selection-section .section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.label-selection-section .section-header h4 {
  margin: 0;
  font-size: 15px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.display-options {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.label-group {
  background: #f9fafc;
  border-radius: 6px;
  padding: 12px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.group-count {
  font-size: 12px;
  color: #909399;
}

.label-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.label-chip {
  cursor: pointer;
  transition: all 0.2s;
}

.label-chip .label-version {
  font-size: 11px;
  color: #909399;
  margin-left: 4px;
}

.label-chip.is-checked .label-version {
  color: #fff;
  opacity: 0.8;
}

.selected-labels-summary {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.summary-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  line-height: 24px;
}

.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 判定原因列样式 */
.reason-column-header {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
}

.reason-cell {
  max-width: 200px;
}

.reason-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  cursor: pointer;
}

.reason-text:hover {
  color: #409eff;
}

/* 原始数据单元格样式 */
.original-data-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.original-data-cell .cell-text {
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

/* 原因单元格样式优化 */
.reason-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.reason-cell .reason-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  -webkit-line-clamp: unset;
  -webkit-box-orient: unset;
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

/* 表格空状态样式 */
.table-empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #909399;
}

.table-empty-state p {
  margin: 8px 0;
}

.table-empty-state .empty-state-hint {
  font-size: 12px;
  color: #c0c4cc;
}
</style>