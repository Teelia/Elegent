<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document,
  DataAnalysis,
  List,
  Setting,
  TrendCharts,
  ArrowLeft,
  Plus,
  Refresh,
  VideoPlay,
  VideoPause,
  Close,
  Warning,
  View,
  Finished,
  Connection,
  PriceTag,
  Search,
  Edit,
  Check
} from '@element-plus/icons-vue'

import type { Dataset, DataRow } from '../api/datasets'
import * as datasetsApi from '../api/datasets'
import type { AnalysisTask, AnalysisTaskStatus, CreateAnalysisTaskRequest } from '../api/analysisTasks'
import * as analysisTasksApi from '../api/analysisTasks'
import type { Label } from '../api/labels'
import * as labelsApi from '../api/labels'
import type { ModelConfig } from '../api/modelConfig'
import * as modelConfigApi from '../api/modelConfig'
import type { IncrementUpdateMode } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'

// 组件
import AnalysisTaskDetail from '../components/AnalysisTaskDetail.vue'
import LabelResultsReview from '../components/LabelResultsReview.vue'
import TaskStatistics from '../components/TaskStatistics.vue'
import DatasetLabelManager from '../components/DatasetLabelManager.vue'

const route = useRoute()
const router = useRouter()

// 数据集ID（确保是有效的整数）
const datasetId = computed(() => {
  const id = Number(route.params.id)
  return Number.isNaN(id) ? 0 : id
})

// 加载状态
const loading = ref(false)
const dataset = ref<Dataset | null>(null)

// Tab相关
const activeTab = ref('overview')

// 数据预览
const dataRows = ref<DataRow[]>([])
const dataRowsLoading = ref(false)
const dataRowsPage = ref(1)
const dataRowsSize = ref(20)
const dataRowsTotal = ref(0)
const dataRowsKeyword = ref('')  // 搜索关键词

// 编辑模式
const editMode = ref(false)
const editedCells = ref<Map<string, any>>(new Map()) // 存储编辑的单元格 key: `${rowId}-${columnName}`
const saving = ref(false)

// 分析任务
const tasks = ref<AnalysisTask[]>([])
const tasksLoading = ref(false)

// 创建任务弹窗
const createTaskDialogVisible = ref(false)
const availableLabels = ref<Label[]>([])
const activeModelConfigs = ref<ModelConfig[]>([])

// 标签模板标识（由 labels.preprocessorConfig._meta.template 提供；不存在则视为普通标签）
const labelTemplateIdCache = new Map<number, string>()

function getLabelTemplateId(label: Label): string {
  if (!label?.preprocessorConfig) return ''
  const cached = labelTemplateIdCache.get(label.id)
  if (cached != null) return cached

  let tpl = ''
  try {
    const cfg = JSON.parse(label.preprocessorConfig)
    tpl = cfg?._meta?.template ? String(cfg._meta.template) : ''
  } catch {
    tpl = ''
  }
  labelTemplateIdCache.set(label.id, tpl)
  return tpl
}

function isTemplateLabel(label: Label): boolean {
  return getLabelTemplateId(label).length > 0
}
const newTaskForm = ref<CreateAnalysisTaskRequest>({
  datasetId: 0,
  name: '',
  labelIds: [],
  defaultConfidenceThreshold: 80,
  autoStart: false,
  modelConfigId: undefined,
  concurrency: 1
})

// 标签管理组件ref
const labelManagerRef = ref<InstanceType<typeof DatasetLabelManager> | null>(null)

// 模型配置加载状态
const modelConfigLoading = ref(false)

// 任务详情弹窗
const taskDetailVisible = ref(false)
const selectedTask = ref<AnalysisTask | null>(null)

// 结果审核弹窗
const resultsReviewVisible = ref(false)
const reviewTask = ref<AnalysisTask | null>(null)

// 增量更新弹窗
const incrementUpdateDialogVisible = ref(false)
const selectedUpdateMode = ref<IncrementUpdateMode>('append')
const incrementUpdateLoading = ref(false)

// 统计分析
const statisticsTaskId = ref<number | ''>('')
const statisticsTask = computed(() => {
  if (!statisticsTaskId.value) return null
  return tasks.value.find(t => t.id === statisticsTaskId.value) || null
})
const completedTasks = computed(() =>
  tasks.value.filter(t => t.status === 'completed' || t.processedRows > 0)
)

// 格式化函数
const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (dateStr: string) => {
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

// 加载数据集详情
async function loadDataset() {
  if (!datasetId.value) {
    ElMessage.error('无效的数据集ID')
    router.push('/datasets')
    return
  }
  loading.value = true
  try {
    dataset.value = await datasetsApi.getDataset(datasetId.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载数据集失败')
    router.push('/datasets')
  } finally {
    loading.value = false
  }
}

// 加载数据行
async function loadDataRows() {
  if (!datasetId.value) return
  dataRowsLoading.value = true
  try {
    const resp = await datasetsApi.getDatasetRows(datasetId.value, {
      page: dataRowsPage.value,
      size: dataRowsSize.value,
      keyword: dataRowsKeyword.value || undefined
    })
    dataRows.value = resp.items
    dataRowsTotal.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载数据失败')
  } finally {
    dataRowsLoading.value = false
  }
}

// 搜索数据行
function handleSearch() {
  dataRowsPage.value = 1  // 搜索时重置到第一页
  loadDataRows()
}

// 清除搜索
function clearSearch() {
  dataRowsKeyword.value = ''
  dataRowsPage.value = 1
  loadDataRows()
}

// 进入编辑模式
function enterEditMode() {
  editMode.value = true
  editedCells.value.clear()
  ElMessage.info('已进入编辑模式，可以直接点击单元格进行编辑')
}

// 退出编辑模式
function exitEditMode() {
  if (editedCells.value.size > 0) {
    ElMessageBox.confirm(
      `有 ${editedCells.value.size} 个单元格已修改但未保存，确定要退出吗？`,
      '提示',
      {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      editMode.value = false
      editedCells.value.clear()
      loadDataRows() // 重新加载数据，恢复原始值
    }).catch(() => {
      // 取消退出
    })
  } else {
    editMode.value = false
  }
}

// 更新单元格值
function updateCellValue(rowId: number, columnName: string, newValue: any) {
  const key = `${rowId}-${columnName}`
  editedCells.value.set(key, {
    rowId,
    columnName,
    newValue
  })
}

// 获取单元格值（优先返回编辑后的值）
function getCellValue(row: DataRow, columnName: string) {
  const key = `${row.id}-${columnName}`
  if (editedCells.value.has(key)) {
    return editedCells.value.get(key).newValue
  }
  return row.originalData?.[columnName]
}

// 保存所有修改
async function saveAllChanges() {
  if (editedCells.value.size === 0) {
    ElMessage.warning('没有需要保存的修改')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要保存 ${editedCells.value.size} 个单元格的修改吗？`,
      '确认保存',
      {
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  saving.value = true
  try {
    // 按行分组修改
    const rowUpdates = new Map<number, Record<string, any>>()

    editedCells.value.forEach((cell) => {
      if (!rowUpdates.has(cell.rowId)) {
        rowUpdates.set(cell.rowId, {})
      }
      rowUpdates.get(cell.rowId)![cell.columnName] = cell.newValue
    })

    // 批量更新
    const updates = Array.from(rowUpdates.entries()).map(([rowId, data]) => ({
      rowId,
      originalData: data
    }))

    await datasetsApi.batchUpdateDataRows(datasetId.value, updates)

    ElMessage.success(`成功保存 ${editedCells.value.size} 个单元格的修改`)
    editedCells.value.clear()
    editMode.value = false
    loadDataRows() // 重新加载数据
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 加载分析任务
async function loadTasks() {
  if (!datasetId.value) return
  tasksLoading.value = true
  try {
    const resp = await analysisTasksApi.listAnalysisTasks({
      datasetId: datasetId.value,
      page: 1,
      size: 100
    })
    tasks.value = resp.items
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务失败')
  } finally {
    tasksLoading.value = false
  }
}

// 加载模型配置列表
async function loadModelConfigs() {
  modelConfigLoading.value = true
  try {
    activeModelConfigs.value = await modelConfigApi.listActiveModelConfigs()
  } catch (e: any) {
    console.error('加载模型配置失败:', e)
  } finally {
    modelConfigLoading.value = false
  }
}

// 打开创建任务弹窗
async function openCreateTaskDialog() {
  if (!datasetId.value) {
    ElMessage.error('无效的数据集ID')
    return
  }
  try {
    // 并行加载标签和模型配置
    const [labels, configs] = await Promise.all([
      labelsApi.getAvailableLabelsForDataset(datasetId.value),
      modelConfigApi.listActiveModelConfigs()
    ])
    availableLabels.value = labels
    labelTemplateIdCache.clear()
    activeModelConfigs.value = configs

    // 找到默认模型配置
    const defaultConfig = configs.find(c => c.isDefault)

    newTaskForm.value = {
      datasetId: datasetId.value,
      name: `分析任务 - ${new Date().toLocaleString('zh-CN')}`,
      labelIds: [],
      defaultConfidenceThreshold: 80,
      autoStart: false,
      modelConfigId: defaultConfig?.id ?? undefined,
      concurrency: 1
    }
    createTaskDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载配置失败')
  }
}

// 创建分析任务
async function createTask() {
  if (newTaskForm.value.labelIds.length === 0) {
    ElMessage.warning('请选择至少一个标签')
    return
  }
  try {
    // 将百分比值（0-100）转换为小数（0-1）发送给后端，保留两位小数
    const threshold = newTaskForm.value.defaultConfidenceThreshold
    const requestData = {
      ...newTaskForm.value,
      defaultConfidenceThreshold: threshold
        ? Math.round(threshold) / 100
        : undefined
    }
    const task = await analysisTasksApi.createAnalysisTask(requestData)
    ElMessage.success('任务创建成功')
    createTaskDialogVisible.value = false
    await loadTasks()
    // 如果选择了自动启动，跳转到任务详情
    if (newTaskForm.value.autoStart) {
      // 可以跳转到任务详情页
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '创建任务失败')
  }
}

// 启动任务
async function startTask(task: AnalysisTask) {
  try {
    await analysisTasksApi.startAnalysisTask(task.id)
    ElMessage.success('任务已启动')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

// 暂停任务
async function pauseTask(task: AnalysisTask) {
  try {
    await analysisTasksApi.pauseAnalysisTask(task.id)
    ElMessage.success('任务已暂停')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

// 取消任务
async function cancelTask(task: AnalysisTask) {
  try {
    await ElMessageBox.confirm('确定要取消此任务吗？', '确认取消', { type: 'warning' })
    await analysisTasksApi.cancelAnalysisTask(task.id)
    ElMessage.success('任务已取消')
    await loadTasks()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '取消失败')
    }
  }
}

// 返回列表
function goBack() {
  router.push('/datasets')
}

// 打开任务详情
function openTaskDetail(task: AnalysisTask) {
  selectedTask.value = task
  taskDetailVisible.value = true
}

// 任务更新回调
async function onTaskUpdated() {
  await loadTasks()
}

// 打开结果审核
function openResultsReview(task: AnalysisTask) {
  reviewTask.value = task
  resultsReviewVisible.value = true
}

// 打开增量更新弹窗
function openIncrementUpdateDialog() {
  selectedUpdateMode.value = 'append'
  incrementUpdateDialogVisible.value = true
}

// 执行增量更新
async function executeIncrementUpdate() {
  if (!datasetId.value) return

  try {
    await ElMessageBox.confirm(
      `确定要执行${selectedUpdateMode.value === 'append' ? '追加' : '替换'}增量更新吗？`,
      '确认增量更新',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  incrementUpdateLoading.value = true
  try {
    await dataSourcesApi.incrementUpdateDataset(datasetId.value, {
      mode: selectedUpdateMode.value
    })
    ElMessage.success('增量更新任务已创建，正在后台执行')
    incrementUpdateDialogVisible.value = false
    await loadDataset()
  } catch (e: any) {
    ElMessage.error(e?.message || '增量更新失败')
  } finally {
    incrementUpdateLoading.value = false
  }
}

// 计算属性：是否是数据库导入的数据集
const isDatabaseDataset = computed(() => {
  return dataset.value?.sourceType === 'database'
})

// 监听Tab切换
watch(activeTab, (tab) => {
  if (tab === 'data' && dataRows.value.length === 0) {
    loadDataRows()
  }
  if (tab === 'tasks' && tasks.value.length === 0) {
    loadTasks()
  }
})

onMounted(async () => {
  await loadDataset()
  // 默认加载任务列表
  await loadTasks()
})
</script>

<template>
  <div class="dataset-detail-view" v-loading="loading">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="goBack">返回</el-button>
        <div class="dataset-info" v-if="dataset">
          <el-icon class="file-icon"><Document /></el-icon>
          <h2 class="dataset-name">{{ dataset.name }}</h2>
          <el-tag :type="dataset.status === 'uploaded' ? 'success' : 'info'" size="small">
            {{ dataset.status === 'uploaded' ? '已上传' : '已归档' }}
          </el-tag>
        </div>
      </div>
      <div class="header-right">
        <!-- 增量更新按钮 - 仅对数据库导入的数据集显示 -->
        <el-button
          v-if="isDatabaseDataset"
          type="success"
          :icon="Refresh"
          @click="openIncrementUpdateDialog"
        >
          增量更新
        </el-button>
        <el-button :icon="Refresh" @click="loadDataset">刷新</el-button>
      </div>
    </div>

    <!-- 数据集概览卡片 -->
    <div class="overview-cards" v-if="dataset">
      <div class="stat-card">
        <div class="stat-value">{{ dataset.totalRows?.toLocaleString() }}</div>
        <div class="stat-label">数据行数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dataset.columns?.length || 0 }}</div>
        <div class="stat-label">数据列数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatFileSize(dataset.fileSize) }}</div>
        <div class="stat-label">文件大小</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ tasks.length }}</div>
        <div class="stat-label">分析任务</div>
      </div>
    </div>

    <!-- Tab页 -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- 概览Tab -->
      <el-tab-pane label="概览" name="overview">
        <template #label>
          <span class="tab-label">
            <el-icon><DataAnalysis /></el-icon>
            概览
          </span>
        </template>
        
        <div class="overview-content" v-if="dataset">
          <!-- 基本信息 -->
          <el-card class="info-card">
            <template #header>
              <span>基本信息</span>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="文件名">{{ dataset.originalFilename }}</el-descriptions-item>
              <el-descriptions-item label="存储文件名">{{ dataset.storedFilename }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDate(dataset.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDate(dataset.updatedAt || '') }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 列信息 -->
          <el-card class="info-card">
            <template #header>
              <span>数据列 ({{ dataset.columns?.length || 0 }})</span>
            </template>
            <div class="columns-list">
              <el-tag
                v-for="(col, index) in dataset.columns"
                :key="index"
                style="margin: 4px"
              >
                {{ typeof col === 'string' ? col : col.name }}
              </el-tag>
            </div>
          </el-card>

          <!-- 分析任务概览 -->
          <el-card class="info-card">
            <template #header>
              <div class="card-header-with-action">
                <span>分析任务 ({{ tasks.length }})</span>
                <el-button 
                  type="primary" 
                  size="small" 
                  :icon="Plus" 
                  @click="openCreateTaskDialog"
                  :disabled="dataset.status === 'archived'"
                >
                  新建任务
                </el-button>
              </div>
            </template>
            <div v-if="tasks.length === 0" class="empty-tasks">
              <el-empty description="暂无分析任务">
                <el-button 
                  type="primary" 
                  @click="openCreateTaskDialog"
                  :disabled="dataset.status === 'archived'"
                >
                  创建第一个任务
                </el-button>
              </el-empty>
            </div>
            <div v-else class="tasks-mini-list">
              <div 
                v-for="task in tasks.slice(0, 5)" 
                :key="task.id" 
                class="task-mini-item"
              >
                <div class="task-mini-info">
                  <span class="task-mini-name">{{ task.name }}</span>
                  <el-tag :type="statusTagType(task.status)" size="small">
                    {{ statusLabel(task.status) }}
                  </el-tag>
                </div>
                <div class="task-mini-progress" v-if="task.status === 'processing'">
                  <el-progress 
                    :percentage="Math.round((task.processedRows / task.totalRows) * 100)" 
                    :stroke-width="6"
                    style="width: 100px"
                  />
                </div>
              </div>
              <el-button 
                v-if="tasks.length > 5" 
                text 
                type="primary" 
                @click="activeTab = 'tasks'"
              >
                查看全部 {{ tasks.length }} 个任务
              </el-button>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 数据预览Tab -->
      <el-tab-pane label="数据预览" name="data">
        <template #label>
          <span class="tab-label">
            <el-icon><List /></el-icon>
            数据预览
          </span>
        </template>
        
        <div class="data-preview-content">
          <!-- 搜索栏和编辑按钮 -->
          <div class="data-search-bar">
            <el-input
              v-model="dataRowsKeyword"
              placeholder="输入关键词搜索所有列..."
              clearable
              style="width: 400px"
              @keyup.enter="handleSearch"
              @clear="clearSearch"
              :disabled="editMode"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button
              type="primary"
              :icon="Search"
              @click="handleSearch"
              :loading="dataRowsLoading"
              :disabled="editMode"
            >
              搜索
            </el-button>
            <el-button
              :icon="Refresh"
              @click="clearSearch"
              :disabled="!dataRowsKeyword || editMode"
            >
              重置
            </el-button>
            <span v-if="dataRowsKeyword" class="search-result-hint">
              找到 {{ dataRowsTotal }} 条匹配结果
            </span>

            <!-- 编辑模式按钮 -->
            <div style="margin-left: auto; display: flex; gap: 12px; align-items: center;">
              <el-tag v-if="editMode && editedCells.size > 0" type="warning">
                已修改 {{ editedCells.size }} 个单元格
              </el-tag>
              <template v-if="!editMode">
                <el-button
                  type="primary"
                  :icon="Edit"
                  @click="enterEditMode"
                  :disabled="dataset?.status === 'archived'"
                >
                  进入编辑模式
                </el-button>
              </template>
              <template v-else>
                <el-button
                  type="success"
                  :icon="Check"
                  @click="saveAllChanges"
                  :loading="saving"
                  :disabled="editedCells.size === 0"
                >
                  保存修改
                </el-button>
                <el-button
                  @click="exitEditMode"
                  :disabled="saving"
                >
                  取消
                </el-button>
              </template>
            </div>
          </div>

          <el-table
            :data="dataRows"
            v-loading="dataRowsLoading"
            border
            style="width: 100%"
            max-height="500"
            :class="{ 'edit-mode-table': editMode }"
          >
            <el-table-column type="index" label="#" width="60" fixed />
            <template v-if="dataset?.columns">
              <el-table-column
                v-for="col in dataset.columns"
                :key="typeof col === 'string' ? col : col.name"
                :label="typeof col === 'string' ? col : col.name"
                min-width="150"
              >
                <template #default="{ row }">
                  <div v-if="!editMode" class="cell-content">
                    {{ getCellValue(row, typeof col === 'string' ? col : col.name) }}
                  </div>
                  <el-input
                    v-else
                    :model-value="getCellValue(row, typeof col === 'string' ? col : col.name)"
                    @input="(val: string) => updateCellValue(row.id, typeof col === 'string' ? col : col.name, val)"
                    size="small"
                    :class="{ 'edited-cell': editedCells.has(`${row.id}-${typeof col === 'string' ? col : col.name}`) }"
                  />
                </template>
              </el-table-column>
            </template>
          </el-table>
          
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="dataRowsPage"
              v-model:page-size="dataRowsSize"
              :total="dataRowsTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadDataRows"
              @size-change="loadDataRows"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 分析任务Tab -->
      <el-tab-pane label="分析任务" name="tasks">
        <template #label>
          <span class="tab-label">
            <el-icon><Setting /></el-icon>
            分析任务
          </span>
        </template>
        
        <div class="tasks-content">
          <div class="tasks-header">
            <el-button 
              type="primary" 
              :icon="Plus" 
              @click="openCreateTaskDialog"
              :disabled="dataset?.status === 'archived'"
            >
              新建分析任务
            </el-button>
            <el-button :icon="Refresh" @click="loadTasks" :loading="tasksLoading">
              刷新
            </el-button>
          </div>

          <el-table :data="tasks" v-loading="tasksLoading" style="width: 100%">
            <el-table-column prop="name" label="任务名称" min-width="200" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="180">
              <template #default="{ row }">
                <div class="progress-cell">
                  <el-progress 
                    :percentage="row.totalRows ? Math.round((row.processedRows / row.totalRows) * 100) : 0" 
                    :stroke-width="8"
                    :status="row.status === 'failed' ? 'exception' : row.status === 'completed' ? 'success' : undefined"
                  />
                  <span class="progress-text">
                    {{ row.processedRows }} / {{ row.totalRows }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="成功/失败" width="100">
              <template #default="{ row }">
                <span class="success-count">{{ row.successRows }}</span>
                /
                <span class="fail-count">{{ row.failedRows }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="defaultConfidenceThreshold" label="信心阈值" width="100">
              <template #default="{ row }">
                {{ Math.round((row.defaultConfidenceThreshold || 0) * 100) }}%
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="320" fixed="right">
              <template #default="{ row }">
                <div class="operation-buttons">
                  <el-button
                    size="small"
                    :icon="View"
                    @click="openTaskDetail(row)"
                  >
                    详情
                  </el-button>
                  <el-button
                    v-if="row.status === 'pending'"
                    size="small"
                    type="success"
                    :icon="VideoPlay"
                    @click="startTask(row)"
                  >
                    启动
                  </el-button>
                  <el-button
                    v-if="row.status === 'processing'"
                    size="small"
                    type="warning"
                    :icon="VideoPause"
                    @click="pauseTask(row)"
                  >
                    暂停
                  </el-button>
                  <el-button
                    v-if="row.status === 'paused'"
                    size="small"
                    type="success"
                    :icon="VideoPlay"
                    @click="startTask(row)"
                  >
                    继续
                  </el-button>
                  <el-button
                    v-if="['pending', 'processing', 'paused'].includes(row.status)"
                    size="small"
                    type="danger"
                    :icon="Close"
                    @click="cancelTask(row)"
                  >
                    取消
                  </el-button>
                  <el-button
                    v-if="row.status === 'completed' || row.processedRows > 0"
                    size="small"
                    type="primary"
                    :icon="Finished"
                    @click="openResultsReview(row)"
                  >
                    审核
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 统计分析Tab -->
      <el-tab-pane label="统计分析" name="statistics">
        <template #label>
          <span class="tab-label">
            <el-icon><TrendCharts /></el-icon>
            统计分析
          </span>
        </template>
        
        <div class="statistics-content">
          <!-- 任务选择器 -->
          <div class="statistics-task-selector" v-if="completedTasks.length > 0">
            <span class="selector-label">选择任务：</span>
            <el-select
              v-model="statisticsTaskId"
              placeholder="请选择要查看统计的任务"
              style="width: 300px"
            >
              <el-option
                v-for="task in completedTasks"
                :key="task.id"
                :label="task.name"
                :value="task.id"
              >
                <div class="task-option">
                  <span>{{ task.name }}</span>
                  <el-tag :type="statusTagType(task.status)" size="small">
                    {{ statusLabel(task.status) }}
                  </el-tag>
                </div>
              </el-option>
            </el-select>
          </div>

          <!-- 统计组件 -->
          <TaskStatistics v-if="statisticsTask" :task="statisticsTask" />
          
          <!-- 空状态 -->
          <el-empty
            v-else-if="completedTasks.length === 0"
            description="请先完成分析任务后查看统计"
          />
          <el-empty
            v-else
            description="请选择一个任务查看统计"
          />
        </div>
      </el-tab-pane>

      <!-- 标签管理Tab -->
      <el-tab-pane label="标签管理" name="labels">
        <template #label>
          <span class="tab-label">
            <el-icon><PriceTag /></el-icon>
            标签管理
          </span>
        </template>

        <div class="labels-content" v-if="dataset">
          <DatasetLabelManager
            ref="labelManagerRef"
            :dataset-id="datasetId"
            :columns="dataset.columns?.map((c: any) => typeof c === 'string' ? c : c.name) || []"
            @update="loadTasks"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建任务弹窗 -->
    <el-dialog 
      v-model="createTaskDialogVisible" 
      title="新建分析任务" 
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="newTaskForm" label-width="120px">
        <el-form-item label="任务名称" required>
          <el-input v-model="newTaskForm.name" placeholder="请输入任务名称" />
        </el-form-item>
        
        <el-form-item label="选择标签" required>
          <el-select 
            v-model="newTaskForm.labelIds" 
            multiple 
            filterable 
            placeholder="请选择要应用的标签"
            style="width: 100%"
          >
            <el-option-group 
              v-if="availableLabels.filter(l => l.scope === 'global').length > 0"
              label="全局标签"
            >
              <el-option 
                v-for="label in availableLabels.filter(l => l.scope === 'global')" 
                :key="label.id" 
                :label="`${label.name} v${label.version}`" 
                :value="label.id"
              >
                <div class="label-option">
                  <div class="label-option-main">
                    <span>{{ label.name }} v{{ label.version }}</span>
                    <el-tag v-if="isTemplateLabel(label)" size="small" type="success" effect="plain">内置</el-tag>
                  </div>
                  <span class="label-desc">{{ label.description?.substring(0, 30) }}...</span>
                </div>
              </el-option>
            </el-option-group>
            <el-option-group 
              v-if="availableLabels.filter(l => l.scope === 'dataset').length > 0"
              label="数据集专属标签"
            >
              <el-option 
                v-for="label in availableLabels.filter(l => l.scope === 'dataset')" 
                :key="label.id" 
                :label="`${label.name} v${label.version}`" 
                :value="label.id"
              >
                <div class="label-option">
                  <div class="label-option-main">
                    <span>{{ label.name }} v{{ label.version }}</span>
                    <el-tag v-if="isTemplateLabel(label)" size="small" type="success" effect="plain">内置</el-tag>
                  </div>
                  <span class="label-desc">{{ label.description?.substring(0, 30) }}...</span>
                </div>
              </el-option>
            </el-option-group>
          </el-select>
        </el-form-item>

        <el-form-item label="AI模型" required>
          <el-select
            v-model="newTaskForm.modelConfigId"
            placeholder="请选择分析使用的AI模型"
            style="width: 100%"
            :loading="modelConfigLoading"
          >
            <el-option
              v-for="config in activeModelConfigs"
              :key="config.id"
              :label="config.name"
              :value="config.id"
            >
              <div class="model-option">
                <div class="model-option-main">
                  <span class="model-name">{{ config.name }}</span>
                  <el-tag v-if="config.isDefault" type="success" size="small">默认</el-tag>
                </div>
                <div class="model-option-sub">
                  <span>{{ config.providerDisplayName || config.provider }}</span>
                  <span class="model-id">{{ config.model }}</span>
                </div>
              </div>
            </el-option>
          </el-select>
          <div class="form-hint">
            <el-icon><Connection /></el-icon>
            选择用于分析数据的大模型，不同模型可能产生不同的分析效果和费用
          </div>
        </el-form-item>

        <el-form-item label="默认信心阈值">
          <el-slider
            v-model="newTaskForm.defaultConfidenceThreshold"
            :min="0"
            :max="100"
            :step="5"
            show-input
            style="width: 100%"
          />
          <div class="form-hint">
            <el-icon><Warning /></el-icon>
            AI信心度低于此阈值的结果将标记为"待审核"
          </div>
        </el-form-item>

        <el-form-item label="并发数">
          <el-slider
            v-model="newTaskForm.concurrency"
            :min="1"
            :max="10"
            :step="1"
            :marks="{1: '1', 3: '3', 5: '5', 10: '10'}"
            show-input
            style="width: 100%"
          />
          <div class="form-hint">
            <el-icon><Warning /></el-icon>
            同时处理的数据行数，数值越大处理越快，但API调用费用也越高
          </div>
        </el-form-item>

        <el-form-item label="创建后启动">
          <el-switch v-model="newTaskForm.autoStart" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createTaskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建任务</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情弹窗 -->
    <AnalysisTaskDetail
      v-model:visible="taskDetailVisible"
      :task="selectedTask"
      @task-updated="onTaskUpdated"
    />

    <!-- 结果审核弹窗 -->
    <el-dialog
      v-model="resultsReviewVisible"
      :title="`结果审核 - ${reviewTask?.name || ''}`"
      width="95%"
      :close-on-click-modal="false"
      top="5vh"
    >
      <LabelResultsReview v-if="reviewTask" :task="reviewTask" />
      <template #footer>
        <el-button @click="resultsReviewVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 增量更新弹窗 -->
    <el-dialog
      v-model="incrementUpdateDialogVisible"
      title="增量更新数据集"
      width="500px"
      :close-on-click-modal="false"
    >
      <div class="increment-update-content">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        >
          增量更新将从外部数据源导入新增或更新的数据到当前数据集。
        </el-alert>

        <div v-if="dataset?.lastImportTime" class="last-import-info">
          <span class="info-label">上次导入时间：</span>
          <span class="info-value">{{ formatDate(dataset.lastImportTime) }}</span>
        </div>
        <div v-else class="last-import-info">
          <el-tag type="warning" size="small">首次导入</el-tag>
        </div>

        <el-form label-width="100px" style="margin-top: 20px">
          <el-form-item label="更新模式">
            <el-radio-group v-model="selectedUpdateMode">
              <el-radio value="append">
                <div class="radio-option">
                  <strong>追加模式</strong>
                  <div class="radio-desc">保留现有数据，仅添加新数据</div>
                </div>
              </el-radio>
              <el-radio value="replace">
                <div class="radio-option">
                  <strong>替换模式</strong>
                  <div class="radio-desc">清空现有数据后重新导入</div>
                </div>
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="selectedUpdateMode === 'replace'"
          type="warning"
          :closable="false"
          style="margin-top: 16px"
        >
          替换模式将清空当前数据集的所有数据，请谨慎操作！
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="incrementUpdateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="executeIncrementUpdate"
          :loading="incrementUpdateLoading"
        >
          开始更新
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dataset-detail-view {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dataset-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  font-size: 24px;
  color: #409eff;
}

.dataset-name {
  margin: 0;
  font-size: 18px;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-top: 8px;
}

.detail-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  margin-bottom: 0;
}

.columns-list {
  display: flex;
  flex-wrap: wrap;
}

.card-header-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-tasks {
  padding: 20px 0;
}

.tasks-mini-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-mini-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.task-mini-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-mini-name {
  font-weight: 500;
}

.data-preview-content {
  padding: 16px 0;
}

.data-search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.search-result-hint {
  font-size: 14px;
  color: #909399;
  margin-left: 8px;
}

/* 编辑模式样式 */
.edit-mode-table {
  border: 2px solid #409eff;
}

.cell-content {
  min-height: 24px;
  padding: 4px 0;
}

.edited-cell :deep(.el-input__wrapper) {
  background-color: #fff7e6;
  border-color: #ffa940;
}

.edited-cell :deep(.el-input__wrapper):hover {
  border-color: #ff7a00;
}

.edited-cell :deep(.el-input__wrapper.is-focus) {
  border-color: #ff7a00;
  box-shadow: 0 0 0 1px #ff7a00 inset;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.tasks-content {
  padding: 16px 0;
}

.tasks-header {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 12px;
  color: #666;
}

.success-count {
  color: #67c23a;
}

.fail-count {
  color: #f56c6c;
}

/* 操作按钮容器 - 确保不换行 */
.operation-buttons {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  white-space: nowrap;
}

.statistics-content {
  padding: 16px 0;
}

.labels-content {
  padding: 16px 0;
}

.statistics-task-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.selector-label {
  font-weight: 500;
}

.task-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.label-option {
  display: flex;
  flex-direction: column;
}

.label-option-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.label-desc {
  font-size: 12px;
  color: #999;
}

.form-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

/* 模型选择选项样式 */
.model-option {
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.model-option-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-name {
  font-weight: 500;
  color: #303133;
}

.model-option-sub {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.model-id {
  color: #409eff;
}

/* 标签配置卡片 */
.label-config-card {
  border-left: 3px solid #409eff;
}

/* 增量更新弹窗样式 */
.increment-update-content {
  padding: 8px 0;
}

.last-import-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 16px;
}

.info-label {
  font-weight: 500;
  color: #606266;
}

.info-value {
  color: #409eff;
  font-weight: 500;
}

.radio-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.radio-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
