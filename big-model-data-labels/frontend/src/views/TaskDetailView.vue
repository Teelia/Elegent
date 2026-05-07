<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { Client } from '@stomp/stompjs'

import * as tasksApi from '../api/tasks'
import * as rowsApi from '../api/rows'
import * as analysisApi from '../api/analysis'
import * as syncApi from '../api/sync'
import * as labelsApi from '../api/labels'
import { createProgressClient } from '../api/ws'
import { statusLabels, statusTagTypes } from '../api/tasks'

import ResultTable from '../components/ResultTable.vue'
import FieldMappingDialog from '../components/FieldMappingDialog.vue'
import StatsPanel from '../components/StatsPanel.vue'
import KeywordPanel from '../components/KeywordPanel.vue'
import AnalysisConfigDialog from '../components/AnalysisConfigDialog.vue'
import LogViewer, { type LogEntry } from '../components/LogViewer.vue'

import type { Task, TaskProgress, TaskStatus } from '../api/tasks'
import type { DataRow } from '../api/rows'
import type { TaskStatistics, KeywordCount } from '../api/analysis'
import type { SyncConfig, TableSchema } from '../api/sync'
import type { Label } from '../api/labels'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const task = ref<Task | null>(null)
const progress = ref<TaskProgress | null>(null)

const rowsLoading = ref(false)
const rowPage = ref(1)
const rowSize = ref(50)
const rowTotal = ref(0)
const rows = ref<DataRow[]>([])

const pendingEdits = ref<Record<number, Record<string, unknown>>>({})
const savingEdits = ref(false)
const pendingCount = computed(() => Object.keys(pendingEdits.value).length)

// 临时标签相关
const tempLabels = ref<Label[]>([])
const newTempLabelName = ref('')
const newTempLabelDesc = ref('')
const newTempLabelColumns = ref<string[]>([])
const savingTempLabel = ref(false)
const showTempLabelForm = ref(false)
const showAnalysisConfig = ref(false)
const isRestartMode = ref(false) // 区分启动和重启
const analysisLogs = ref<LogEntry[]>([])
const logClient = ref<Client | null>(null)

// 显示判断依据
const showReasoning = ref(false)

const stats = ref<TaskStatistics | null>(null)
const keywords = ref<KeywordCount[]>([])
const keywordLabelKey = ref<string | null>(null)
const keywordColumns = ref<string[]>([])

const syncConfigs = ref<SyncConfig[]>([])
const selectedSyncConfigId = ref<number | null>(null)
const tableSchema = ref<TableSchema | null>(null)
const mappingVisible = ref(false)

// 标签配置相关
const activeLabels = ref<Label[]>([])
const selectedLabelIds = ref<number[]>([])
const labelsLoading = ref(false)

const client = ref<ReturnType<typeof createProgressClient> | null>(null)
let progressPollTimer: number | null = null

const labelKeys = computed(() => {
  const labels = task.value?.labels || []
  return labels.map((l) => `${l.name}_v${l.version}`)
})

// 获取标签类型映射（labelKey -> type）
const labelTypeMap = computed(() => {
  const labels = task.value?.labels || []
  const map: Record<string, string> = {}
  for (const l of labels) {
    const key = `${l.name}_v${l.version}`
    map[key] = l.type || 'classification'
  }
  return map
})

// 判断标签是否为提取类型
function isExtractionLabel(labelKey: string): boolean {
  return labelTypeMap.value[labelKey] === 'extraction'
}

// 获取提取结果的显示文本
function getExtractionResult(row: DataRow, labelKey: string): string {
  const result = row.labelResults?.[labelKey]
  if (!result) return ''
  // 自由提取模式返回的结构是 { result: "提取内容", success: true, confidence: 80 }
  if (typeof result === 'object' && result !== null) {
    const r = result as any
    return r.result || r.summary || ''
  }
  return String(result)
}

const fileColumns = computed(() => {
  return task.value?.columns || []
})

const isArchived = computed(() => task.value?.status === 'archived')

const canConfigureLabels = computed(() => {
  const status = task.value?.status
  return status === 'uploaded' || status === 'pending'
})

const canStart = computed(() => {
  const status = task.value?.status
  return status === 'pending' && (task.value?.labels?.length || 0) > 0
})

const canPause = computed(() => task.value?.status === 'processing')

const canResume = computed(() => task.value?.status === 'paused')

const canRestart = computed(() => {
  const status = task.value?.status
  return status === 'completed' || status === 'failed' || status === 'cancelled'
})

const canCancel = computed(() => {
  const status = task.value?.status
  return status === 'pending' || status === 'processing' || status === 'paused'
})

// 是否显示已配置标签（任何状态都显示）
const hasConfiguredLabels = computed(() => (task.value?.labels?.length || 0) > 0)

async function loadTask() {
  task.value = await tasksApi.getTask(taskId)
  if (!keywordColumns.value.length) {
    keywordColumns.value = (task.value?.columns || []).slice(0, 2)
  }
  selectedLabelIds.value = task.value?.labels?.map(l => l.id) || []
  // 加载临时标签
  await loadTempLabels()
}

// 加载临时标签
async function loadTempLabels() {
  try {
    tempLabels.value = await tasksApi.getTempLabels(taskId)
  } catch (e: any) {
    console.error('加载临时标签失败', e)
    tempLabels.value = []
  }
}

// 创建临时标签
async function createTempLabel() {
  const name = newTempLabelName.value.trim()
  if (!name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  
  savingTempLabel.value = true
  try {
    const label = await tasksApi.createTempLabel(taskId, {
      name,
      description: newTempLabelDesc.value.trim() || undefined,
      focusColumns: newTempLabelColumns.value.length > 0 ? newTempLabelColumns.value : undefined
    })
    ElMessage.success('临时标签创建成功')
    tempLabels.value.push(label)
    // 重置表单
    newTempLabelName.value = ''
    newTempLabelDesc.value = ''
    newTempLabelColumns.value = []
    showTempLabelForm.value = false
    // 刷新任务详情以获取更新的标签列表
    await loadTask()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    savingTempLabel.value = false
  }
}

// 将临时标签保存到全局标签库
async function promoteTempLabel(labelId: number) {
  try {
    await tasksApi.promoteTempLabel(taskId, labelId)
    ElMessage.success('已保存到全局标签库')
    await loadTempLabels()
    await loadActiveLabels()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function loadProgressOnce() {
  try {
    progress.value = await tasksApi.getProgress(taskId)
  } catch {
    // ignore
  }
}

function stopProgressPolling() {
  if (progressPollTimer != null) {
    window.clearInterval(progressPollTimer)
    progressPollTimer = null
  }
}

function startProgressPolling() {
  stopProgressPolling()
  let inFlight = false
  progressPollTimer = window.setInterval(async () => {
    if (inFlight) return
    const status = progress.value?.status || task.value?.status
    if (status !== 'processing') {
      return
    }
    inFlight = true
    try {
      await loadProgressOnce()
      const next = progress.value?.status
      if (next === 'completed' || next === 'failed') {
        stopProgressPolling()
        await loadTask()
        await loadRows()
        await loadStats()
      }
    } finally {
      inFlight = false
    }
  }, 2000)
}

async function loadRows() {
  rowsLoading.value = true
  try {
    const resp = await rowsApi.listRows(taskId, { page: rowPage.value, size: rowSize.value })
    rows.value = resp.items.map((r) => {
      const draft = pendingEdits.value[r.id]
      if (!draft) return r
      return { ...r, labelResults: draft, isModified: true }
    })
    rowTotal.value = resp.total
  } finally {
    rowsLoading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await analysisApi.getStatistics(taskId)
  } catch {
    stats.value = null
  }
}

async function loadSyncConfigs() {
  try {
    syncConfigs.value = await syncApi.listSyncConfigs()
  } catch {
    syncConfigs.value = []
  }
}

async function loadActiveLabels() {
  labelsLoading.value = true
  try {
    activeLabels.value = await labelsApi.activeLabels()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载标签失败')
  } finally {
    labelsLoading.value = false
  }
}

async function saveLabelsConfig() {
  if (!canConfigureLabels.value) {
    ElMessage.warning('当前状态不允许修改标签配置')
    return
  }
  try {
    await tasksApi.configureTaskLabels(taskId, selectedLabelIds.value)
    ElMessage.success('标签配置已保存')
    await loadTask()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function handleStart() {
  if (!canStart.value) {
    ElMessage.warning('请先配置标签')
    return
  }
  try {
    const labelIds = selectedLabelIds.value.length > 0 ? selectedLabelIds.value : (task.value?.labels?.map(l => l.id) || [])
    await tasksApi.startTask(taskId, labelIds)
    ElMessage.success('任务已启动')
    await loadTask()
    await loadProgressOnce()
    startProgressPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

async function handleStartClick() {
  if (!canStart.value) {
    ElMessage.warning('请先配置标签')
    return
  }
  isRestartMode.value = false
  showAnalysisConfig.value = true
}

async function handleStartConfirm(config: { modelConfigId: number; includeReasoning: boolean }) {
  showAnalysisConfig.value = false
  try {
    // 传递所有选中的标签ID（包括全局和临时）
    const labelIds = selectedLabelIds.value.length > 0 ? selectedLabelIds.value : (task.value?.labels?.map(l => l.id) || [])

    if (isRestartMode.value) {
      // 重新分析
      await tasksApi.restartTask(taskId, labelIds, config.modelConfigId, config.includeReasoning)
      ElMessage.success('任务已重新启动')
    } else {
      // 首次启动
      await tasksApi.startTask(taskId, labelIds, config.modelConfigId, config.includeReasoning)
      ElMessage.success('任务已启动')
    }

    analysisLogs.value = [] // 清空旧日志
    await loadTask()
    await loadProgressOnce()
    startProgressPolling()
    connectLogWebSocket()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

async function handlePause() {
  try {
    await tasksApi.pauseTask(taskId)
    ElMessage.success('任务已暂停')
    await loadTask()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

async function handleResume() {
  try {
    await tasksApi.resumeTask(taskId)
    ElMessage.success('任务已继续')
    analysisLogs.value = []
    await loadTask()
    await loadProgressOnce()
    startProgressPolling()
    connectLogWebSocket()
  } catch (e: any) {
    ElMessage.error(e?.message || '继续失败')
  }
}

async function handleRestartClick() {
  // 设置重启模式并显示分析配置对话框
  isRestartMode.value = true
  showAnalysisConfig.value = true
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消此任务吗？', '确认取消', { type: 'warning' })
    await tasksApi.cancelTask(taskId)
    ElMessage.success('任务已取消')
    await loadTask()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '取消失败')
    }
  }
}

function onChangeLabelValue(row: DataRow, labelKey: string, value: '是' | '否') {
  const next = { ...(row.labelResults || {}) }
  // 如果原本是对象结构（包含reasoning），则只更新result字段
  if (typeof next[labelKey] === 'object' && next[labelKey] !== null) {
    next[labelKey] = { ...next[labelKey], result: value }
  } else {
    next[labelKey] = value
  }
  row.labelResults = next
  row.isModified = true
  pendingEdits.value[row.id] = next
}

async function onSaveChanges() {
  if (isArchived.value) return
  const entries = Object.entries(pendingEdits.value)
  if (!entries.length) return

  savingEdits.value = true
  try {
    await rowsApi.batchUpdateRows(
      taskId,
      entries.map(([rowId, labelResults]) => ({ rowId: Number(rowId), labelResults }))
    )
    pendingEdits.value = {}
    ElMessage.success('保存成功')
    await loadRows()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingEdits.value = false
  }
}

async function onArchive() {
  try {
    if (pendingCount.value) {
      ElMessage.warning('存在未保存修改，请先点击"保存修改"')
      return
    }
    await tasksApi.archiveTask(taskId)
    ElMessage.success('已归档')
    await loadTask()
  } catch (e: any) {
    ElMessage.error(e?.message || '归档失败')
  }
}

async function onExport() {
  try {
    const blob = await tasksApi.exportTask(taskId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `task-${taskId}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function onOpenMapping() {
  if (!isArchived.value) {
    ElMessage.warning('请先归档后再同步')
    return
  }
  if (!selectedSyncConfigId.value) {
    ElMessage.warning('请选择同步配置')
    return
  }
  try {
    tableSchema.value = await syncApi.getTableSchema(selectedSyncConfigId.value)
    mappingVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '读取表结构失败')
  }
}

async function onSync(payload: { syncConfigId: number; fieldMappings: Record<string, string>; strategy: 'insert' }) {
  try {
    await syncApi.syncTask(taskId, payload)
    ElMessage.success('同步成功')
    mappingVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  }
}

function displayLabelKey(labelKey: string) {
  const m = /^(.*)_v\d+$/.exec(labelKey)
  return m ? m[1] : labelKey
}

async function analyzeKeywords() {
  try {
    if (!keywordColumns.value.length) {
      ElMessage.warning('请选择要分析的列')
      return
    }
    if (!fileColumns.value.length) {
      ElMessage.warning('无可分析列')
      return
    }
    const cols = keywordColumns.value.join(',')
    if (!keywordLabelKey.value) {
      ElMessage.warning('请选择标签')
      return
    }
    keywords.value = await analysisApi.getKeywords(taskId, { labelKey: keywordLabelKey.value, columns: cols, top: 30 })
  } catch (e: any) {
    ElMessage.error(e?.message || '关键词分析失败')
  }
}

async function analyzeKeywordsForLabel(labelKey: string) {
  keywordLabelKey.value = labelKey
  await analyzeKeywords()
}

function goBack() {
  router.push('/tasks')
}

function connectLogWebSocket() {
  if (logClient.value?.active) return

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws`

  const stompClient = new Client({
    brokerURL: wsUrl,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })

  stompClient.onConnect = () => {
    stompClient.subscribe(`/topic/task/${taskId}/log`, (message) => {
      try {
        const body = JSON.parse(message.body)
        analysisLogs.value.push({
          timestamp: body.timestamp,
          message: body.message,
          taskId: body.taskId
        })
      } catch (e) {
        console.error('Failed to parse log message', e)
      }
    })
  }

  stompClient.activate()
  logClient.value = stompClient
}

function disconnectLogWebSocket() {
  if (logClient.value) {
    logClient.value.deactivate()
    logClient.value = null
  }
}

onMounted(async () => {
  await loadTask()
  await loadActiveLabels()
  await loadSyncConfigs()
  await loadProgressOnce()
  
  if (task.value?.status === 'processing') {
    startProgressPolling()
    connectLogWebSocket()
  }
})

onBeforeUnmount(() => {
  stopProgressPolling()
  disconnectLogWebSocket()
})

onBeforeRouteLeave(async (_to, _from, next) => {
  if (!pendingCount.value) {
    next()
    return
  }

  try {
    await ElMessageBox.confirm(`当前有 ${pendingCount.value} 条未保存修改，确认离开？`, '提示', { type: 'warning' })
    next()
  } catch {
    next(false)
  }
})
</script>

<template>
  <div v-if="task">
    <!-- 页面头部 -->
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px">
      <div>
        <el-button text @click="goBack" style="margin-bottom: 8px">← 返回列表</el-button>
        <h2 style="margin:0 0 4px 0">任务详情</h2>
        <div style="color:#666;display:flex;align-items:center;gap:8px">
          <span>文件：{{ task.originalFilename }}</span>
          <el-tag :type="statusTagTypes[task.status as TaskStatus]">
            {{ statusLabels[task.status as TaskStatus] || task.status }}
          </el-tag>
        </div>
      </div>
      <div style="display:flex;gap:10px">
        <el-button v-if="canStart" type="success" @click="handleStartClick">启动分析</el-button>
        <el-button v-if="canResume" type="success" @click="handleResume">继续分析</el-button>
        <el-button v-if="canRestart" type="primary" @click="handleRestartClick">重新分析</el-button>
        <el-button v-if="canPause" type="warning" @click="handlePause">暂停</el-button>
        <el-button v-if="canCancel" type="danger" @click="handleCancel">取消</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button type="warning" :disabled="task.status !== 'completed'" @click="onArchive">归档</el-button>
      </div>
    </div>

    <AnalysisConfigDialog
      v-model:visible="showAnalysisConfig"
      :labels="[...activeLabels, ...tempLabels].filter(l => selectedLabelIds.includes(l.id))"
      :loading="false"
      @confirm="handleStartConfirm"
    />

    <!-- 临时标签卡片 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>🏷️ 任务临时标签</span>
          <el-button size="small" type="primary" @click="showTempLabelForm = !showTempLabelForm">
            {{ showTempLabelForm ? '收起' : '创建临时标签' }}
          </el-button>
        </div>
      </template>
      
      <!-- 创建临时标签表单 -->
      <div v-if="showTempLabelForm" style="margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 4px">
        <div style="margin-bottom: 12px">
          <label style="display: block; margin-bottom: 4px; font-weight: 500">标签名称 *</label>
          <el-input v-model="newTempLabelName" placeholder="输入标签名称" style="width: 300px" />
        </div>
        <div style="margin-bottom: 12px">
          <label style="display: block; margin-bottom: 4px; font-weight: 500">标签描述</label>
          <el-input
            v-model="newTempLabelDesc"
            type="textarea"
            :rows="2"
            placeholder="描述此标签的判断标准（可选）"
            style="width: 100%"
          />
        </div>
        <div style="margin-bottom: 12px">
          <label style="display: block; margin-bottom: 4px; font-weight: 500">关注列</label>
          <el-select
            v-model="newTempLabelColumns"
            multiple
            filterable
            clearable
            placeholder="选择要分析的列（可选）"
            style="width: 100%"
          >
            <el-option v-for="c in fileColumns" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button type="primary" @click="createTempLabel" :loading="savingTempLabel">创建</el-button>
          <el-button @click="showTempLabelForm = false">取消</el-button>
        </div>
      </div>
      
      <!-- 临时标签列表 -->
      <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center">
        <div v-for="label in tempLabels" :key="label.id" style="display: flex; align-items: center; gap: 4px">
          <el-tag type="warning">
            {{ label.name }} v{{ label.version }}
            <span v-if="label.scope === 'task'" style="font-size: 10px; color: #909399">（临时）</span>
          </el-tag>
          <el-tooltip content="保存到全局标签库" placement="top">
            <el-button
              v-if="label.scope === 'task'"
              size="small"
              type="success"
              circle
              @click="promoteTempLabel(label.id)"
            >
              <el-icon><Upload /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
      <div v-if="!tempLabels.length && !showTempLabelForm" style="color:#909399;font-size:12px;margin-top:8px">
        暂无临时标签。临时标签仅在当前任务中可见，可用于分析数据行，分析完成后可选择保存到全局标签库。
      </div>
    </el-card>

    <!-- 进度卡片 -->
    <el-card style="margin-bottom: 12px">
      <div style="display:flex;gap:16px;align-items:center">
        <div style="min-width: 240px">
          <div>进度：{{ progress?.percentage ?? 0 }}%</div>
          <div style="color:#666">
            <template v-if="(progress?.status || task.status) === 'processing'">
              正在处理第 {{ progress?.currentRow ?? progress?.processed ?? task.processedRows }}/{{ progress?.total ?? task.totalRows }} 条...
            </template>
            <template v-else>
              已处理：{{ progress?.processed ?? task.processedRows }}/{{ progress?.total ?? task.totalRows }}，失败：{{
                progress?.failed ?? task.failedRows
              }}
            </template>
          </div>
        </div>
        <el-progress :percentage="progress?.percentage ?? 0" :status="task.status === 'failed' ? 'exception' : undefined" style="flex:1" />
      </div>
      
      <!-- 实时日志 -->
      <div v-if="task.status === 'processing' || analysisLogs.length > 0" style="margin-top: 16px">
        <LogViewer :logs="analysisLogs" height="200px" />
      </div>
    </el-card>

    <!-- 标签配置卡片 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>🏷️ 分析标签配置</span>
          <el-button v-if="canConfigureLabels" size="small" type="primary" @click="saveLabelsConfig" :loading="labelsLoading">保存配置</el-button>
        </div>
      </template>

      <!-- 可编辑模式：选择标签 -->
      <div v-if="canConfigureLabels">
        <div style="margin-bottom: 12px">
          <span style="margin-right: 8px">选择要应用的标签：</span>
          <el-select v-model="selectedLabelIds" multiple filterable placeholder="请选择标签" style="width: 400px">
            <el-option-group label="全局标签">
              <el-option v-for="l in activeLabels" :key="l.id" :label="`${l.name} v${l.version}`" :value="l.id" />
            </el-option-group>
            <el-option-group v-if="tempLabels.length > 0" label="临时标签">
              <el-option v-for="l in tempLabels" :key="l.id" :label="`${l.name} v${l.version} (临时)`" :value="l.id" />
            </el-option-group>
          </el-select>
        </div>
      </div>

      <!-- 已配置标签展示（所有状态可见） -->
      <div v-if="task.labels && task.labels.length > 0">
        <div style="margin-bottom: 8px; color:#666; font-size: 13px">
          {{ canConfigureLabels ? '当前已配置标签：' : '任务使用的标签：' }}
        </div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px">
          <el-tooltip v-for="label in task.labels" :key="label.id" :content="label.description || '无描述'" placement="top" :disabled="!label.description">
            <el-tag :type="label.scope === 'task' ? 'warning' : 'primary'" style="cursor: help">
              {{ label.name }} v{{ label.version }}
              <span v-if="label.scope === 'task'" style="font-size: 10px; opacity: 0.8">（临时）</span>
            </el-tag>
          </el-tooltip>
        </div>
      </div>
      <div v-else style="color: #909399; font-size: 13px">
        {{ canConfigureLabels ? '请选择要分析的标签' : '未配置标签' }}
      </div>
    </el-card>

    <!-- 结果表卡片 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div style="display:flex;align-items:center;gap:12px">
            <span>结果表（可编辑标签列）</span>
            <el-checkbox v-model="showReasoning">显示判断依据</el-checkbox>
          </div>
          <div style="display:flex;gap:10px;align-items:center">
            <span v-if="pendingCount && !isArchived" style="color:#e6a23c">待保存：{{ pendingCount }}</span>
            <el-button
              v-if="!isArchived"
              size="small"
              type="primary"
              :disabled="!pendingCount"
              :loading="savingEdits"
              @click="onSaveChanges"
            >
              保存修改
            </el-button>
            <span style="color:#999" v-if="isArchived">已归档，只读</span>
          </div>
        </div>
      </template>

      <ResultTable :rows="rows" :original-columns="fileColumns" :label-keys="labelKeys">
        <template #labelCell="{ row, labelKey }">
          <div style="display:flex;flex-direction:column;gap:4px">
            <!-- 分类类型标签：下拉选择是/否 -->
            <template v-if="!isExtractionLabel(labelKey)">
              <el-select
                :model-value="(row.labelResults?.[labelKey]?.result || row.labelResults?.[labelKey]) || '否'"
                size="small"
                style="width: 90px"
                :disabled="isArchived"
                @update:model-value="(v:any)=>onChangeLabelValue(row, labelKey, v)"
              >
                <el-option label="是" value="是" />
                <el-option label="否" value="否" />
              </el-select>
              <div v-if="showReasoning && row.labelResults?.[labelKey]?.reasoning" style="font-size:12px;color:#666;max-width:200px;white-space:pre-wrap;background:#f9f9f9;padding:4px;border-radius:4px">
                {{ row.labelResults?.[labelKey]?.reasoning }}
              </div>
            </template>
            <!-- 提取类型标签：显示提取的文本内容 -->
            <template v-else>
              <el-tooltip
                :content="getExtractionResult(row, labelKey)"
                placement="top"
                :disabled="!getExtractionResult(row, labelKey) || getExtractionResult(row, labelKey).length < 50"
              >
                <div
                  class="extraction-result"
                  :class="{ 'extraction-empty': !getExtractionResult(row, labelKey) }"
                >
                  {{ getExtractionResult(row, labelKey) || '未提取到信息' }}
                </div>
              </el-tooltip>
            </template>
          </div>
        </template>
      </ResultTable>

      <div style="margin-top: 12px; display:flex; justify-content:flex-end">
        <el-pagination
          v-model:current-page="rowPage"
          v-model:page-size="rowSize"
          :total="rowTotal"
          layout="prev, pager, next, sizes, total"
          @current-change="loadRows"
          @size-change="loadRows"
        />
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>统计</span>
          <el-button size="small" @click="loadStats">刷新</el-button>
        </div>
      </template>
      <StatsPanel :stats="stats" />
    </el-card>

    <!-- 关键词分析卡片 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px">
          <span>关键词分析</span>
          <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;justify-content:flex-end">
            <el-select
              v-model="keywordColumns"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择要分析的列"
              style="width: 420px"
            >
              <el-option v-for="c in fileColumns" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
        </div>
      </template>

      <div v-if="labelKeys.length" style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <div v-for="k in labelKeys" :key="k" style="display:flex;gap:6px;align-items:center">
          <el-tag :type="k === keywordLabelKey ? 'success' : 'info'">{{ displayLabelKey(k) }}</el-tag>
          <el-button size="small" @click="analyzeKeywordsForLabel(k)">分析关键词</el-button>
        </div>
      </div>

      <KeywordPanel
        :items="keywords"
        :title="keywordLabelKey ? `关键词 Top 30（${displayLabelKey(keywordLabelKey)}）` : '关键词 Top 30'"
      />
    </el-card>

    <!-- 同步卡片 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>同步到外部数据库</span>
          <el-button size="small" :disabled="!isArchived" @click="onOpenMapping">字段映射并同步</el-button>
        </div>
      </template>
      <div style="display:flex;gap:12px;align-items:center">
        <el-select v-model="selectedSyncConfigId" placeholder="选择同步配置" style="width: 320px">
          <el-option v-for="c in syncConfigs" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <span style="color:#999" v-if="!isArchived">归档后才允许同步</span>
      </div>
    </el-card>

    <FieldMappingDialog
      v-model="mappingVisible"
      :file-columns="[...fileColumns, ...labelKeys]"
      :label-keys="labelKeys"
      :db-schema="tableSchema"
      :sync-config="syncConfigs.find((x) => x.id === selectedSyncConfigId) || null"
      :disabled="!isArchived"
      @submit="onSync"
    />
  </div>
</template>

<style scoped>
.el-card {
  border-radius: 8px;
  overflow: hidden;
}

.el-card__header {
  background: #f5f7fa;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ec;
  font-weight: 500;
  font-size: 14px;
  color: #333;
}

.el-card__body {
  padding: 16px;
  background: #fff;
}

.el-card__header,
.el-card__body {
  transition: background 0.3s;
}

.el-card:hover .el-card__header {
  background: #e6f7ff;
}

.el-card:hover .el-card__body {
  background: #fafafa;
}

.el-tag {
  border-radius: 12px;
  font-size: 12px;
}

.el-button {
  border-radius: 4px;
  font-size: 14px;
}

.el-select {
  width: 100%;
}

.el-input {
  width: 100%;
}

.el-checkbox {
  line-height: 1.5;
}

.el-pagination {
  padding: 12px 0;
}

/* 提取类型标签结果样式 */
.extraction-result {
  font-size: 13px;
  color: #303133;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  padding: 6px 10px;
  max-width: 300px;
  max-height: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  word-break: break-word;
  line-height: 1.4;
}

.extraction-result.extraction-empty {
  background: #fef0f0;
  border-color: #fde2e2;
  color: #909399;
  font-style: italic;
}
</style>
