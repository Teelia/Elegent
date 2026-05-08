<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Grid,
  List,
  Search,
  VideoPlay,
  VideoPause,
  Close,
  View,
  Finished,
  TrendCharts
} from '@element-plus/icons-vue'

import type { AnalysisTask, AnalysisTaskStatus } from '../api/analysisTasks'
import * as analysisTasksApi from '../api/analysisTasks'

const router = useRouter()

// 列表状态
const loading = ref(false)
const page = ref(1)
const size = ref(12)
const status = ref<string>('')
const searchKeyword = ref('')
const total = ref(0)
const items = ref<AnalysisTask[]>([])

// 视图模式
const viewMode = ref<'card' | 'table'>('table')

// 状态筛选选项
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待启动', value: 'pending' },
  { label: '进行中', value: 'processing' },
  { label: '已暂停', value: 'paused' },
  { label: '已完成', value: 'completed' },
  { label: '失败', value: 'failed' },
  { label: '已取消', value: 'cancelled' }
]

// 状态标签类型
const statusTagTypes: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
  pending: 'info',
  processing: 'warning',
  paused: 'warning',
  completed: 'success',
  failed: 'danger',
  cancelled: 'info'
}

// 状态显示文本
const statusLabels: Record<string, string> = {
  pending: '待启动',
  processing: '进行中',
  paused: '已暂停',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消'
}

// 过滤后的任务列表
const filteredItems = computed(() => {
  if (!searchKeyword.value) return items.value
  const keyword = searchKeyword.value.toLowerCase()
  return items.value.filter(task => 
    task.name?.toLowerCase().includes(keyword) ||
    task.datasetName?.toLowerCase().includes(keyword)
  )
})

// 格式化日期
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

// 获取任务列表
async function fetchList() {
  loading.value = true
  try {
    const resp = await analysisTasksApi.listAnalysisTasks({
      page: page.value,
      size: size.value,
      status: status.value || undefined
    })
    items.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 启动任务
async function handleStart(task: AnalysisTask) {
  try {
    await analysisTasksApi.startAnalysisTask(task.id)
    ElMessage.success('任务已启动')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '启动失败')
  }
}

// 暂停任务
async function handlePause(task: AnalysisTask) {
  try {
    await analysisTasksApi.pauseAnalysisTask(task.id)
    ElMessage.success('任务已暂停')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

// 恢复任务
async function handleResume(task: AnalysisTask) {
  try {
    await analysisTasksApi.resumeAnalysisTask(task.id)
    ElMessage.success('任务已继续')
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '继续失败')
  }
}

// 取消任务
async function handleCancel(task: AnalysisTask) {
  try {
    await ElMessageBox.confirm('确定要取消此任务吗？', '确认取消', { type: 'warning' })
    await analysisTasksApi.cancelAnalysisTask(task.id)
    ElMessage.success('任务已取消')
    await fetchList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '取消失败')
    }
  }
}

// 跳转到数据集详情（查看任务详情）
function gotoDataset(task: AnalysisTask) {
  router.push(`/datasets/${task.datasetId}`)
}

// 导出任务
async function handleExport(task: AnalysisTask) {
  try {
    const blob = await analysisTasksApi.exportAnalysisTask(task.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `analysis-task-${task.id}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

// 状态筛选变化
function onStatusChange() {
  page.value = 1
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="tasks-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2 class="page-title">
        <el-icon><TrendCharts /></el-icon>
        分析任务
      </h2>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="fetchList" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <!-- 状态筛选标签 -->
        <div class="status-tabs">
          <el-radio-group v-model="status" @change="onStatusChange" size="small">
            <el-radio-button 
              v-for="opt in statusOptions" 
              :key="opt.value" 
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="filter-right">
        <!-- 搜索框 -->
        <el-input
          v-model="searchKeyword"
          placeholder="搜索任务名或数据集..."
          :prefix-icon="Search"
          clearable
          style="width: 240px"
        />
        <!-- 视图切换 -->
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
          <el-radio-button value="table">
            <el-icon><List /></el-icon>
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 卡片视图 -->
    <div v-if="viewMode === 'card'" class="card-grid" v-loading="loading">
      <div 
        v-for="task in filteredItems" 
        :key="task.id" 
        class="task-card"
        @click="gotoDataset(task)"
      >
        <div class="card-header">
          <div class="card-title" :title="task.name">{{ task.name }}</div>
          <el-tag :type="statusTagTypes[task.status]" size="small">
            {{ statusLabels[task.status] || task.status }}
          </el-tag>
        </div>
        
        <div class="card-meta">
          <div class="meta-item">
            <span class="meta-label">数据集:</span>
            <span class="meta-value">{{ task.datasetName || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">进度:</span>
            <span class="meta-value">{{ task.processedRows }} / {{ task.totalRows }}</span>
          </div>
        </div>

        <div class="card-progress">
          <el-progress 
            :percentage="task.totalRows ? Math.round((task.processedRows / task.totalRows) * 100) : 0" 
            :stroke-width="8"
            :status="task.status === 'failed' ? 'exception' : task.status === 'completed' ? 'success' : undefined"
          />
        </div>

        <div class="card-footer">
          <span class="card-date">{{ formatDate(task.createdAt) }}</span>
        </div>

        <div class="card-actions" @click.stop>
          <el-button size="small" type="primary" text @click="gotoDataset(task)">
            <el-icon><View /></el-icon>
            查看
          </el-button>
          <el-button
            v-if="task.status === 'pending'"
            size="small"
            type="success"
            text
            @click="handleStart(task)"
          >
            <el-icon><VideoPlay /></el-icon>
            启动
          </el-button>
          <el-button
            v-if="task.status === 'processing'"
            size="small"
            type="warning"
            text
            @click="handlePause(task)"
          >
            <el-icon><VideoPause /></el-icon>
            暂停
          </el-button>
          <el-button
            v-if="task.status === 'paused'"
            size="small"
            type="success"
            text
            @click="handleResume(task)"
          >
            <el-icon><VideoPlay /></el-icon>
            继续
          </el-button>
          <el-button
            v-if="task.status === 'completed'"
            size="small"
            type="primary"
            text
            @click="handleExport(task)"
          >
            <el-icon><Finished /></el-icon>
            导出
          </el-button>
        </div>
      </div>
      <el-empty v-if="filteredItems.length === 0 && !loading" description="暂无任务">
        <template #description>
          <p>暂无分析任务</p>
          <p style="color: #909399; font-size: 12px;">请先在数据集详情页创建分析任务</p>
        </template>
      </el-empty>
    </div>

    <!-- 表格视图 -->
    <el-table v-else :data="filteredItems" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="任务名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="datasetName" label="数据集" min-width="150" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagTypes[row.status]" size="small">
            {{ statusLabels[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <div class="progress-cell">
            <el-progress 
              :percentage="row.totalRows ? Math.round((row.processedRows / row.totalRows) * 100) : 0" 
              :stroke-width="6"
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
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="gotoDataset(row)">详情</el-button>
          <el-button 
            v-if="row.status === 'pending'" 
            size="small" 
            type="success" 
            @click="handleStart(row)"
          >
            启动
          </el-button>
          <el-button 
            v-if="row.status === 'processing'" 
            size="small" 
            type="warning" 
            @click="handlePause(row)"
          >
            暂停
          </el-button>
          <el-button 
            v-if="row.status === 'paused'" 
            size="small" 
            type="success" 
            @click="handleResume(row)"
          >
            继续
          </el-button>
          <el-button 
            v-if="['pending', 'processing', 'paused'].includes(row.status)" 
            size="small" 
            type="danger" 
            @click="handleCancel(row)"
          >
            取消
          </el-button>
          <el-button 
            v-if="row.status === 'completed'" 
            size="small" 
            type="primary" 
            @click="handleExport(row)"
          >
            导出
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>
  </div>
</template>

<style scoped>
.tasks-view {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tabs :deep(.el-radio-button__inner) {
  padding: 8px 16px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.task-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.task-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #666;
}

.meta-item {
  display: flex;
  gap: 4px;
}

.meta-label {
  color: #999;
}

.card-progress {
  margin-bottom: 12px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.card-date {
  font-size: 12px;
  color: #999;
}

.card-actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
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
</style>
