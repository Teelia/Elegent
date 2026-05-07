<script setup lang="ts">
import { computed } from 'vue'
import { Document, Clock, Check, Close, Loading, VideoPause, Finished } from '@element-plus/icons-vue'
import type { Task, TaskStatus } from '../api/tasks'
import { statusLabels, statusTagTypes } from '../api/tasks'

const props = defineProps<{
  task: Task
}>()

const emit = defineEmits<{
  (e: 'configure', task: Task): void
  (e: 'start', task: Task): void
  (e: 'resume', task: Task): void
  (e: 'restart', task: Task): void
  (e: 'pause', task: Task): void
  (e: 'cancel', task: Task): void
  (e: 'detail', task: Task): void
  (e: 'export', task: Task): void
}>()

// 计算属性
const isProcessing = computed(() => props.task.status === 'processing')
const isPending = computed(() => props.task.status === 'pending')
const isUploaded = computed(() => props.task.status === 'uploaded')
const isCompleted = computed(() => props.task.status === 'completed')
const isArchived = computed(() => props.task.status === 'archived')
const isPaused = computed(() => props.task.status === 'paused')
const isFailed = computed(() => props.task.status === 'failed')

const progress = computed(() => {
  if (!props.task.totalRows || props.task.totalRows === 0) return 0
  return Math.round((props.task.processedRows / props.task.totalRows) * 100)
})

const statusClass = computed(() => {
  return `status-${props.task.status}`
})

const statusIcon = computed(() => {
  switch (props.task.status) {
    case 'processing': return Loading
    case 'completed': return Check
    case 'failed': return Close
    case 'paused': return VideoPause
    case 'archived': return Finished
    default: return Clock
  }
})

const formatTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <el-card class="task-card" :class="statusClass" shadow="hover">
    <!-- 卡片头部 -->
    <div class="task-header">
      <div class="file-info">
        <el-icon class="file-icon"><Document /></el-icon>
        <span class="filename" :title="task.originalFilename">{{ task.originalFilename }}</span>
      </div>
      <el-tag :type="statusTagTypes[task.status as TaskStatus]" size="small">
        {{ statusLabels[task.status as TaskStatus] || task.status }}
      </el-tag>
    </div>

    <!-- 标签列表 -->
    <div class="task-labels" v-if="task.labels && task.labels.length > 0">
      <el-tag 
        v-for="label in task.labels.slice(0, 3)" 
        :key="label.id" 
        size="small" 
        type="info"
        class="label-tag"
      >
        🏷️ {{ label.name }}v{{ label.version }}
      </el-tag>
      <el-tag v-if="task.labels.length > 3" size="small" type="info">
        +{{ task.labels.length - 3 }}
      </el-tag>
    </div>
    <div class="task-labels empty" v-else-if="isUploaded || isPending">
      <span class="no-labels">未配置标签</span>
    </div>

    <!-- 进度条（处理中状态） -->
    <div class="task-progress" v-if="isProcessing">
      <el-progress 
        :percentage="progress" 
        :stroke-width="10"
        :status="isFailed ? 'exception' : undefined"
      />
      <div class="progress-detail">
        <span>{{ task.processedRows }} / {{ task.totalRows }}</span>
        <span v-if="task.failedRows > 0" class="failed-count">
          失败: {{ task.failedRows }}
        </span>
      </div>
    </div>

    <!-- 任务信息（非处理中状态） -->
    <div class="task-info" v-else>
      <div class="info-row">
        <span class="info-label">数据行:</span>
        <span class="info-value">{{ task.totalRows?.toLocaleString() || 0 }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">文件大小:</span>
        <span class="info-value">{{ formatFileSize(task.fileSize) }}</span>
      </div>
      <div class="info-row" v-if="isCompleted || isArchived">
        <span class="info-label">已处理:</span>
        <span class="info-value success">{{ task.processedRows }}</span>
        <span class="info-value danger" v-if="task.failedRows > 0">
          / 失败 {{ task.failedRows }}
        </span>
      </div>
    </div>

    <!-- 时间信息 -->
    <div class="task-time">
      <el-icon><Clock /></el-icon>
      <span>{{ formatTime(task.createdAt) }}</span>
    </div>

    <!-- 操作按钮 -->
    <div class="task-actions">
      <!-- 已上传状态 -->
      <template v-if="isUploaded">
        <el-button size="small" type="primary" @click="emit('configure', task)">
          配置标签
        </el-button>
      </template>

      <!-- 待启动状态 -->
      <template v-else-if="isPending">
        <el-button size="small" type="success" @click="emit('start', task)">
          ▶ 开始分析
        </el-button>
        <el-button size="small" @click="emit('configure', task)">
          配置
        </el-button>
      </template>

      <!-- 处理中状态 -->
      <template v-else-if="isProcessing">
        <el-button size="small" @click="emit('detail', task)">
          查看详情
        </el-button>
        <el-button size="small" type="warning" @click="emit('pause', task)">
          暂停
        </el-button>
      </template>

      <!-- 已暂停状态 -->
      <template v-else-if="isPaused">
        <el-button size="small" type="success" @click="emit('resume', task)">
          继续
        </el-button>
        <el-button size="small" @click="emit('detail', task)">
          详情
        </el-button>
        <el-button size="small" type="danger" @click="emit('cancel', task)">
          取消
        </el-button>
      </template>

      <!-- 已完成状态 -->
      <template v-else-if="isCompleted">
        <el-button size="small" type="primary" @click="emit('detail', task)">
          查看结果
        </el-button>
        <el-button size="small" @click="emit('export', task)">
          📥 导出
        </el-button>
      </template>

      <!-- 已归档状态 -->
      <template v-else-if="isArchived">
        <el-button size="small" @click="emit('detail', task)">
          查看
        </el-button>
        <el-button size="small" @click="emit('export', task)">
          📥 导出
        </el-button>
      </template>

      <!-- 失败状态 -->
      <template v-else-if="isFailed">
        <el-button size="small" @click="emit('detail', task)">
          查看详情
        </el-button>
        <el-button size="small" type="warning" @click="emit('restart', task)">
          重试
        </el-button>
      </template>
    </div>
  </el-card>
</template>

<style scoped>
.task-card {
  width: 100%;
  transition: all 0.3s ease;
}

.task-card:hover {
  transform: translateY(-2px);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.file-icon {
  font-size: 20px;
  color: #409eff;
  flex-shrink: 0;
}

.filename {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  min-height: 24px;
}

.task-labels.empty {
  color: #909399;
  font-size: 12px;
}

.label-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.no-labels {
  font-style: italic;
}

.task-progress {
  margin-bottom: 12px;
}

.progress-detail {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
  margin-top: 4px;
}

.failed-count {
  color: #f56c6c;
}

.task-info {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 4px;
}

.info-label {
  color: #909399;
  min-width: 60px;
}

.info-value {
  color: #303133;
}

.info-value.success {
  color: #67c23a;
}

.info-value.danger {
  color: #f56c6c;
}

.task-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.task-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 状态样式 */
.task-card.status-processing {
  border-left: 3px solid #e6a23c;
}

.task-card.status-completed {
  border-left: 3px solid #67c23a;
}

.task-card.status-failed {
  border-left: 3px solid #f56c6c;
}

.task-card.status-pending {
  border-left: 3px solid #409eff;
}

.task-card.status-paused {
  border-left: 3px solid #909399;
}

.task-card.status-archived {
  border-left: 3px solid #c0c4cc;
  opacity: 0.8;
}
</style>