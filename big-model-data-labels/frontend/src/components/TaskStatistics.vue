<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'

import type { AnalysisTask } from '../api/analysisTasks'
import * as analysisTasksApi from '../api/analysisTasks'
import type { LabelResultStatistics } from '../api/labelResults'
import * as labelResultsApi from '../api/labelResults'

const props = defineProps<{
  task: AnalysisTask
}>()

// 状态
const loading = ref(false)
const statistics = ref<LabelResultStatistics[]>([])

// 计算属性
const totalProcessed = computed(() => props.task.processedRows || 0)
const totalSuccess = computed(() => props.task.successRows || 0)
const totalFailed = computed(() => props.task.failedRows || 0)
const successRate = computed(() => {
  if (totalProcessed.value === 0) return 0
  return Math.round((totalSuccess.value / totalProcessed.value) * 100)
})

// 加载统计数据
async function loadStatistics() {
  loading.value = true
  try {
    statistics.value = await labelResultsApi.getLabelResultStatistics(props.task.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载统计失败')
  } finally {
    loading.value = false
  }
}

// 导出统计报告
async function exportReport() {
  try {
    const blob = await analysisTasksApi.exportAnalysisTask(props.task.id)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${props.task.name}_统计报告.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

// 计算进度条颜色
const getProgressColor = (rate: number) => {
  if (rate >= 80) return '#67c23a'
  if (rate >= 60) return '#e6a23c'
  return '#f56c6c'
}

onMounted(() => {
  loadStatistics()
})

watch(() => props.task.id, () => {
  loadStatistics()
})
</script>

<template>
  <div class="task-statistics" v-loading="loading">
    <!-- 操作栏 -->
    <div class="stats-header">
      <el-button :icon="Refresh" @click="loadStatistics" :loading="loading">
        刷新统计
      </el-button>
      <el-button :icon="Download" type="primary" @click="exportReport">
        导出报告
      </el-button>
    </div>

    <!-- 总体统计 -->
    <el-card class="stats-card">
      <template #header>
        <span>总体统计</span>
      </template>
      <div class="overview-stats">
        <div class="stat-item">
          <div class="stat-value">{{ totalProcessed.toLocaleString() }}</div>
          <div class="stat-label">已处理</div>
        </div>
        <div class="stat-item success">
          <div class="stat-value">{{ totalSuccess.toLocaleString() }}</div>
          <div class="stat-label">成功</div>
        </div>
        <div class="stat-item danger">
          <div class="stat-value">{{ totalFailed.toLocaleString() }}</div>
          <div class="stat-label">失败</div>
        </div>
        <div class="stat-item primary">
          <div class="stat-value">{{ successRate }}%</div>
          <div class="stat-label">成功率</div>
        </div>
      </div>
    </el-card>

    <!-- 标签统计 -->
    <el-card class="stats-card" v-if="statistics.length > 0">
      <template #header>
        <span>标签命中统计</span>
      </template>
      <div class="label-stats-grid">
        <div 
          v-for="stat in statistics" 
          :key="stat.labelId" 
          class="label-stat-card"
        >
          <div class="label-stat-header">
            <span class="label-name">{{ stat.labelName }}</span>
          </div>
          
          <div class="label-stat-body">
            <!-- 命中率 -->
            <div class="hit-rate-section">
              <div class="hit-rate-label">命中率</div>
              <div class="hit-rate-value">
                <el-progress 
                  type="circle" 
                  :percentage="Math.round(stat.hitRate * 100)" 
                  :width="80"
                  :color="getProgressColor(stat.hitRate * 100)"
                />
              </div>
            </div>

            <!-- 详细数据 -->
            <div class="label-stat-details">
              <div class="detail-row">
                <span class="detail-label">是:</span>
                <span class="detail-value success">{{ stat.yesCount.toLocaleString() }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">否:</span>
                <span class="detail-value danger">{{ stat.noCount.toLocaleString() }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">平均信心度:</span>
                <span class="detail-value">{{ Math.round(stat.avgConfidence) }}%</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">待审核:</span>
                <span class="detail-value warning">{{ stat.needsReviewCount.toLocaleString() }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">已修改:</span>
                <span class="detail-value">{{ stat.modifiedCount.toLocaleString() }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 分布对比图 -->
    <el-card class="stats-card" v-if="statistics.length > 0">
      <template #header>
        <span>标签分布对比</span>
      </template>
      <div class="distribution-chart">
        <div 
          v-for="stat in statistics" 
          :key="stat.labelId" 
          class="distribution-row"
        >
          <div class="distribution-label">{{ stat.labelName }}</div>
          <div class="distribution-bar-container">
            <div class="distribution-bar">
              <div 
                class="bar-yes" 
                :style="{ width: `${stat.hitRate * 100}%` }"
                :title="`是: ${stat.yesCount}`"
              ></div>
              <div 
                class="bar-no" 
                :style="{ width: `${(1 - stat.hitRate) * 100}%` }"
                :title="`否: ${stat.noCount}`"
              ></div>
            </div>
          </div>
          <div class="distribution-value">{{ Math.round(stat.hitRate * 100) }}%</div>
        </div>
      </div>
      <div class="distribution-legend">
        <span class="legend-item"><span class="legend-color yes"></span>是</span>
        <span class="legend-item"><span class="legend-color no"></span>否</span>
      </div>
    </el-card>

    <!-- 空状态 -->
    <el-empty 
      v-if="statistics.length === 0 && !loading" 
      description="暂无统计数据，请先完成分析任务"
    />
  </div>
</template>

<style scoped>
.task-statistics {
  padding: 16px 0;
}

.stats-header {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.stats-card {
  margin-bottom: 16px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-item.success .stat-value {
  color: #67c23a;
}

.stat-item.danger .stat-value {
  color: #f56c6c;
}

.stat-item.primary .stat-value {
  color: #409eff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.label-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.label-stat-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
}

.label-stat-header {
  margin-bottom: 16px;
}

.label-name {
  font-weight: 600;
  font-size: 16px;
}

.label-stat-body {
  display: flex;
  gap: 24px;
}

.hit-rate-section {
  text-align: center;
}

.hit-rate-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.label-stat-details {
  flex: 1;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 14px;
}

.detail-label {
  color: #909399;
}

.detail-value {
  font-weight: 500;
}

.detail-value.success {
  color: #67c23a;
}

.detail-value.danger {
  color: #f56c6c;
}

.detail-value.warning {
  color: #e6a23c;
}

.distribution-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.distribution-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.distribution-label {
  min-width: 120px;
  font-size: 14px;
}

.distribution-bar-container {
  flex: 1;
}

.distribution-bar {
  display: flex;
  height: 24px;
  border-radius: 4px;
  overflow: hidden;
}

.bar-yes {
  background: #67c23a;
  transition: width 0.3s;
}

.bar-no {
  background: #f56c6c;
  transition: width 0.3s;
}

.distribution-value {
  min-width: 50px;
  text-align: right;
  font-weight: 600;
  color: #67c23a;
}

.distribution-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #666;
}

.legend-color {
  width: 16px;
  height: 16px;
  border-radius: 4px;
}

.legend-color.yes {
  background: #67c23a;
}

.legend-color.no {
  background: #f56c6c;
}
</style>