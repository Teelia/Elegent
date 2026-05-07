<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import type { DataSource, TablePreview } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'
import DatabaseExplorer from './DatabaseExplorer.vue'

interface Emits {
  (e: 'import-completed', dataset: { datasetId: number }): void
}

const emit = defineEmits<Emits>()

const visible = ref(false)
const currentStep = ref(0)
const loading = ref(false)

const selectedSourceId = ref<number>()
const selectedDatabase = ref<string>('')
const selectedTableName = ref<string>('')

const preview = reactive<TablePreview>({
  tableName: '',
  columns: [],
  rows: [],
  totalRows: 0,
  sql: '',
})

const importQuery = ref('')
const datasetName = ref('')
const datasetDescription = ref('')

// 数据源列表
const dataSources = ref<DataSource[]>([])
const dataSourcesLoading = ref(false)

// 导入进度相关
const showProgress = ref(false)
const currentDatasetId = ref<number>()
const importProgress = ref({
  status: 'importing',
  totalRows: 0,
  message: ''
})
let progressTimer: ReturnType<typeof setInterval> | null = null

// 步骤定义
const steps = [
  { title: '选择数据源' },
  { title: '选择表' },
  { title: '配置导入' },
  { title: '确认导入' },
]

// 打开向导
async function open() {
  visible.value = true
  currentStep.value = 0
  selectedSourceId.value = undefined
  selectedDatabase.value = ''
  selectedTableName.value = ''
  importQuery.value = ''
  datasetName.value = ''
  datasetDescription.value = ''

  // 加载数据源列表
  await loadDataSources()
}

// 加载数据源列表
async function loadDataSources() {
  dataSourcesLoading.value = true
  try {
    dataSources.value = await dataSourcesApi.listDataSources()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载数据源失败')
  } finally {
    dataSourcesLoading.value = false
  }
}

// 选择数据源
function onSelectDataSource(sourceId: number) {
  selectedSourceId.value = sourceId
  selectedDatabase.value = ''
  selectedTableName.value = ''
  // 清空预览
  preview.columns = []
  preview.rows = []
  preview.totalRows = 0
}

// 选择表
async function onSelectTable(database: string, table: string) {
  selectedDatabase.value = database
  selectedTableName.value = table
  await loadPreview()
}

// 加载预览数据
async function loadPreview() {
  if (!selectedSourceId.value || !selectedTableName.value) return

  loading.value = true
  try {
    const result = await dataSourcesApi.previewTableData(
      selectedSourceId.value,
      selectedTableName.value,
      importQuery.value
    )
    Object.assign(preview, result)

    // 自动填充数据集名称
    if (!datasetName.value) {
      datasetName.value = `${selectedTableName.value}_${new Date().toISOString().slice(0, 10)}`
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载预览失败')
  } finally {
    loading.value = false
  }
}

// 刷新预览
async function onRefreshPreview() {
  await loadPreview()
}

// 下一步
function nextStep() {
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
  }
}

// 上一步
function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

// 确认导入
async function onConfirmImport() {
  if (!selectedSourceId.value || !selectedTableName.value || !datasetName.value) {
    ElMessage.warning('请完善导入配置')
    return
  }

  loading.value = true
  try {
    const result = await dataSourcesApi.createImportTask(
      selectedSourceId.value,
      {
        tableName: selectedTableName.value,
        datasetName: datasetName.value,
        importQuery: importQuery.value,
        description: datasetDescription.value,
      }
    )

    ElMessage.success('导入任务已创建，正在后台执行...')

    // 设置进度跟踪
    currentDatasetId.value = result.datasetId
    importProgress.value = {
      status: 'importing',
      totalRows: 0,
      message: '正在启动导入任务...'
    }
    showProgress.value = true

    // 启动轮询
    startProgressPolling(result.datasetId)

    emit('import-completed', { datasetId: result.datasetId })
  } catch (e: any) {
    ElMessage.error(e?.message || '创建导入任务失败')
  } finally {
    loading.value = false
  }
}

// 启动进度轮询
function startProgressPolling(datasetId: number) {
  // 清除旧的定时器
  stopProgressPolling()

  // 立即查询一次
  checkProgress(datasetId)

  // 每 2 秒查询一次进度
  progressTimer = setInterval(() => {
    checkProgress(datasetId)
  }, 2000)
}

// 停止进度轮询
function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

// 检查进度
async function checkProgress(datasetId: number) {
  try {
    const progress = await dataSourcesApi.getImportProgress(datasetId)
    importProgress.value = progress

    // 如果导入完成或失败，停止轮询
    if (progress.status === 'uploaded' || progress.status === 'failed') {
      stopProgressPolling()

      if (progress.status === 'uploaded') {
        ElMessage.success('数据导入完成！')
      } else {
        ElMessage.error('数据导入失败：' + progress.message)
      }

      // 3 秒后自动关闭进度显示
      setTimeout(() => {
        showProgress.value = false
        visible.value = false
      }, 3000)
    }
  } catch (e: any) {
    console.error('查询导入进度失败:', e)
    // 不停止轮询，可能是网络波动
  }
}

// 组件卸载时清理定时器
onUnmounted(() => {
  stopProgressPolling()
})

// 监听对话框关闭，停止轮询并重置状态
watch(visible, (newVal) => {
  if (!newVal) {
    // 对话框关闭时，停止轮询
    stopProgressPolling()
    // 重置状态
    showProgress.value = false
  }
})

// 计算属性：是否可以继续
const canProceed = computed(() => {
  switch (currentStep.value) {
    case 0: // 选择数据源
      return !!selectedSourceId.value
    case 1: // 选择表
      return !!selectedTableName.value
    case 2: // 配置导入
      return !!datasetName.value
    default:
      return true
  }
})

// 计算属性：当前选中的数据源
const selectedDataSource = computed(() => {
  return dataSources.value.find(ds => ds.id === selectedSourceId.value)
})

// 暴露方法给父组件
defineExpose({
  open,
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="从数据库导入数据"
    width="900px"
    :close-on-click-modal="false"
  >
    <!-- 步骤指示器 -->
    <el-steps :active="currentStep" align-center style="margin-bottom: 24px">
      <el-step v-for="step in steps" :key="step.title" :title="step.title" />
    </el-steps>

    <!-- 步骤 1: 选择数据源 -->
    <div v-show="currentStep === 0" class="wizard-step">
      <h3>选择数据源</h3>
      <el-radio-group v-model="selectedSourceId" @change="onSelectDataSource" class="source-list">
        <el-radio
          v-for="ds in dataSources"
          :key="ds.id"
          :label="ds.id"
          class="source-item"
        >
          <div class="source-info">
            <div class="source-name">{{ ds.name }}</div>
            <div class="source-detail">
              <el-tag size="small" type="info">{{ ds.dbType.toUpperCase() }}</el-tag>
              <span class="source-host">{{ ds.host }}:{{ ds.port }}</span>
            </div>
          </div>
        </el-radio>
      </el-radio-group>
    </div>

    <!-- 步骤 2: 选择表 -->
    <div v-show="currentStep === 1" class="wizard-step">
      <h3>选择表</h3>
      <div v-if="selectedSourceId" class="explorer-container">
        <DatabaseExplorer
          :source-id="selectedSourceId"
          @select="onSelectTable"
        />
      </div>
      <el-empty v-else description="请先选择数据源" />
    </div>

    <!-- 步骤 3: 配置导入 -->
    <div v-show="currentStep === 2" class="wizard-step">
      <h3>配置导入</h3>

      <el-form label-width="120px">
        <el-form-item label="数据源">
          <el-tag v-if="selectedDataSource">
            {{ selectedDataSource.name }} ({{ selectedDataSource.dbType.toUpperCase() }})
          </el-tag>
        </el-form-item>

        <el-form-item label="数据库">
          <el-tag>{{ selectedDatabase || '-' }}</el-tag>
        </el-form-item>

        <el-form-item label="表名">
          <el-tag>{{ selectedTableName || '-' }}</el-tag>
        </el-form-item>

        <el-form-item label="数据集名称" required>
          <el-input v-model="datasetName" placeholder="输入数据集名称" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="datasetDescription"
            type="textarea"
            :rows="2"
            placeholder="数据集描述（可选）"
          />
        </el-form-item>

        <el-form-item label="查询条件">
          <el-input
            v-model="importQuery"
            type="textarea"
            :rows="3"
            placeholder="可选的 WHERE 条件，例如：created_at > '2024-01-01'"
            @blur="onRefreshPreview"
          />
        </el-form-item>
      </el-form>

      <div class="preview-section">
        <div class="preview-header">
          <h4>数据预览（前 20 行）</h4>
          <el-button size="small" @click="onRefreshPreview" :loading="loading">
            刷新预览
          </el-button>
        </div>
        <el-table
          :data="preview.rows"
          v-loading="loading"
          border
          max-height="300"
          size="small"
        >
          <el-table-column
            v-for="col in preview.columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
        <div class="preview-info">
          <span v-if="preview.totalRows !== undefined">
            总行数: {{ preview.totalRows }}
          </span>
          <span v-if="preview.sql" class="preview-sql">
            SQL: {{ preview.sql }}
          </span>
        </div>
      </div>
    </div>

    <!-- 步骤 4: 确认导入 -->
    <div v-show="currentStep === 3" class="wizard-step">
      <!-- 进度显示 -->
      <div v-if="showProgress" class="import-progress-section">
        <h3>导入进度</h3>
        <el-card shadow="never">
          <div class="progress-content">
            <el-progress
              :percentage="importProgress.status === 'uploaded' ? 100 :
                          importProgress.status === 'failed' ? 0 :
                          importProgress.totalRows > 0 ? Math.min(95, (importProgress.totalRows / (preview.totalRows || 1)) * 100) : 10"
              :status="importProgress.status === 'uploaded' ? 'success' :
                      importProgress.status === 'failed' ? 'exception' : undefined"
              :indeterminate="importProgress.status === 'importing' && importProgress.totalRows === 0"
            >
              <template #default="{ percentage }">
                <span class="progress-text">
                  {{ importProgress.message }}
                </span>
              </template>
            </el-progress>
            <div class="progress-stats">
              <div class="stat-item">
                <span class="stat-label">状态:</span>
                <el-tag v-if="importProgress.status === 'importing'" type="primary" size="small">
                  正在导入
                </el-tag>
                <el-tag v-else-if="importProgress.status === 'uploaded'" type="success" size="small">
                  导入完成
                </el-tag>
                <el-tag v-else-if="importProgress.status === 'failed'" type="danger" size="small">
                  导入失败
                </el-tag>
              </div>
              <div class="stat-item">
                <span class="stat-label">已导入行数:</span>
                <span class="stat-value">{{ importProgress.totalRows.toLocaleString() }}</span>
              </div>
              <div v-if="preview.totalRows" class="stat-item">
                <span class="stat-label">总行数:</span>
                <span class="stat-value">{{ preview.totalRows.toLocaleString() }}</span>
              </div>
            </div>
            <div class="progress-hint">
              <el-icon style="margin-right: 4px"><InfoFilled /></el-icon>
              导入任务在后台执行，您可以关闭此窗口。完成后可在数据集列表中查看结果。
            </div>
          </div>
        </el-card>
      </div>

      <!-- 确认表单 -->
      <template v-if="!showProgress">
        <h3>确认导入配置</h3>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="数据源">
            {{ selectedDataSource?.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="数据库类型">
            {{ selectedDataSource?.dbType.toUpperCase() || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="数据库">
            {{ selectedDatabase || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="表名">
            {{ selectedTableName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="数据集名称" :span="2">
            {{ datasetName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="查询条件" :span="2">
            {{ importQuery || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="预计导入行数" :span="2">
            {{ preview.totalRows !== undefined ? preview.totalRows.toLocaleString() : '未知' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          type="info"
          :closable="false"
          style="margin-top: 16px"
        >
          确认后将创建导入任务，数据将在后台异步导入。您可以关闭此窗口，导入将继续执行。
        </el-alert>
      </template>
    </div>

    <!-- 底部按钮 -->
    <template #footer>
      <el-button v-if="!showProgress" @click="visible = false">取消</el-button>
      <el-button v-if="currentStep > 0 && !showProgress" @click="prevStep">上一步</el-button>
      <el-button
        v-if="currentStep < 3 && !showProgress"
        type="primary"
        :disabled="!canProceed"
        @click="nextStep"
      >
        下一步
      </el-button>
      <el-button
        v-if="currentStep === 3 && !showProgress"
        type="primary"
        :loading="loading"
        @click="onConfirmImport"
      >
        确认导入
      </el-button>
      <el-button
        v-if="showProgress"
        type="primary"
        @click="visible = false"
      >
        在后台运行
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.wizard-step {
  min-height: 400px;
}

/* 导入进度部分 */
.import-progress-section {
  padding: 0;
}

.import-progress-section h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
}

.progress-content {
  padding: 0;
}

.progress-text {
  font-size: 14px;
  color: #606266;
}

.progress-stats {
  display: flex;
  gap: 24px;
  margin-top: 20px;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.progress-hint {
  display: flex;
  align-items: center;
  margin-top: 16px;
  padding: 12px;
  background-color: #ecf5ff;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}

.wizard-step h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
}

/* 步骤 1: 数据源列表 */
.source-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.source-item {
  width: 100%;
  margin: 0;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  transition: all 0.2s;
}

.source-item:hover {
  background-color: #f5f7fa;
}

.source-item.is-checked {
  background-color: #ecf5ff;
  border-color: #409eff;
}

.source-info {
  margin-left: 8px;
}

.source-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.source-detail {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #909399;
}

/* 步骤 2: 数据库浏览器 */
.explorer-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

/* 步骤 3: 预览区域 */
.preview-section {
  margin-top: 24px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.preview-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.preview-info {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
  display: flex;
  gap: 16px;
}

.preview-sql {
  color: #909399;
  font-size: 12px;
}
</style>
