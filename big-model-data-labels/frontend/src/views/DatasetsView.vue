<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { Document, Grid, List, Refresh, Upload, Delete, View, FolderOpened, Loading, Filter, Search, MoreFilled, Box, Coin, DataLine, Folder } from '@element-plus/icons-vue'

import type { Dataset, DatasetStatus, DatasetUploadResponse, ColumnInfo } from '../api/datasets'
import * as datasetsApi from '../api/datasets'
import DataImportWizard from '../components/DataImportWizard.vue'

const router = useRouter()

// 数据导入向弹引用
const importWizardRef = ref<InstanceType<typeof DataImportWizard> | null>(null)

// 常量
const MAX_UPLOAD_SIZE = 50 * 1024 * 1024
const ALLOWED_EXTS = ['.xlsx', '.xls', '.csv']

// 状态
const loading = ref(false)
const page = ref(1)
const size = ref(12)
const status = ref<DatasetStatus | ''>('')
const keyword = ref('')
const total = ref(0)
const items = ref<Dataset[]>([])
const viewMode = ref<'card' | 'table'>('card')

// 上传相关
const uploading = ref(false)
const uploadDialogVisible = ref(false)
const uploadResp = ref<DatasetUploadResponse | null>(null)
const previewRows = ref<Array<Record<string, unknown>>>([])
const previewLoading = ref(false)

// 计算属性
const statusOptions = [
  { label: '全部', value: '' },
  { label: '已上传', value: 'uploaded' },
  { label: '已归档', value: 'archived' },
]

// 从列信息中提取列名
const columnNames = computed(() => {
  if (!uploadResp.value?.columns) return []
  return uploadResp.value.columns.map(col => getColumnName(col)).filter(Boolean) as string[]
})

// 辅助函数：从列信息中提取列名
function getColumnName(col: any): string {
  // 如果是字符串，尝试解析为 JSON
  if (typeof col === 'string') {
    try {
      const parsed = JSON.parse(col)
      return parsed.name || col
    } catch {
      return col
    }
  }
  // 如果是对象，返回 name 属性
  if (typeof col === 'object' && col !== null) {
    return col.name
  }
  return String(col || '')
}

// 预览数据
const previewData = computed(() => {
  // 优先使用 uploadResp.preview（如果后端返回了）
  if (uploadResp.value?.preview?.length) {
    return uploadResp.value.preview
  }
  // 否则使用从 API 获取的数据
  return previewRows.value
})

const statusTagType = (s: DatasetStatus) => {
  switch (s) {
    case 'uploaded': return 'success'
    case 'archived': return 'info'
    default: return 'info'
  }
}

const statusLabel = (s: DatasetStatus) => {
  switch (s) {
    case 'uploaded': return '已上传'
    case 'archived': return '已归档'
    default: return s
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

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

// 获取数据集列表
async function fetchList() {
  loading.value = true
  try {
    const resp = await datasetsApi.listDatasets({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
      keyword: keyword.value || undefined
    })
    items.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 上传处理
async function onUploadRequest(options: UploadRequestOptions) {
  const f = options.file as File
  const name = (f.name || '').toLowerCase()
  const ext = name.includes('.') ? name.substring(name.lastIndexOf('.')) : ''
  if (!ALLOWED_EXTS.includes(ext)) {
    const msg = `仅支持 ${ALLOWED_EXTS.join(', ')} 文件`
    ElMessage.error(msg)
    ;(options.onError as any)?.(new Error(msg))
    return
  }
  if (f.size > MAX_UPLOAD_SIZE) {
    const msg = '文件大小超过 50MB 限制'
    ElMessage.error(msg)
    ;(options.onError as any)?.(new Error(msg))
    return
  }
  uploading.value = true
  previewRows.value = [] // 重置预览数据
  try {
    const resp = await datasetsApi.uploadDataset(f)
    uploadResp.value = resp
    uploadDialogVisible.value = true

    // 获取数据集的预览数据（如果后端没有返回 preview）
    const datasetId = Number((resp as any).id || (resp as any).datasetId)
    if (datasetId && !Number.isNaN(datasetId) && (!resp.preview || resp.preview.length === 0)) {
      await fetchPreviewData(datasetId)
    }

    await fetchList()
    ;(options.onSuccess as any)?.(resp)
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
    ;(options.onError as any)?.(e)
  } finally {
    uploading.value = false
  }
}

// 获取预览数据
async function fetchPreviewData(datasetId: number) {
  previewLoading.value = true
  try {
    const result = await datasetsApi.getDatasetRows(datasetId, { page: 1, size: 20 })
    // 将 DataRow 转换为普通对象格式用于表格显示
    previewRows.value = result.items.map(row => row.originalData || {})
  } catch (e: any) {
    console.error('获取预览数据失败:', e)
    previewRows.value = []
  } finally {
    previewLoading.value = false
  }
}

// 跳转到详情页
function gotoDetail(dataset: Dataset) {
  router.push(`/datasets/${dataset.id}`)
}

// 归档数据集
async function handleArchive(dataset: Dataset) {
  try {
    await ElMessageBox.confirm(
      `确定要归档数据集 "${dataset.name}" 吗？归档后将不能创建新的分析任务。`,
      '确认归档',
      { type: 'warning' }
    )
    await datasetsApi.archiveDataset(dataset.id)
    ElMessage.success('归档成功')
    await fetchList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '归档失败')
    }
  }
}

// 删除数据集
async function handleDelete(dataset: Dataset) {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据集 "${dataset.name}" 吗？此操作不可恢复！`,
      '确认删除',
      { type: 'error' }
    )
    await datasetsApi.deleteDataset(dataset.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

// 关闭上传预览弹窗并跳转
function closeUploadAndGoto() {
  uploadDialogVisible.value = false
  if (uploadResp.value) {
    // 兼容不同的响应字段名，确保是有效的整数
    const datasetId = Number((uploadResp.value as any).id || (uploadResp.value as any).datasetId)
    if (datasetId && !Number.isNaN(datasetId)) {
      router.push(`/datasets/${datasetId}`)
    }
  }
}

// 打开数据导入向导
function openImportWizard() {
  importWizardRef.value?.open()
}

// 数据导入完成回调
function onImportCompleted(data: { datasetId: number }) {
  ElMessage.success('数据导入任务已创建')
  fetchList()
  // 可选：跳转到数据集详情页
  // router.push(`/datasets/${data.datasetId}`)
}

onMounted(fetchList)
</script>

<template>
  <div class="datasets-view">
    <!-- 页面标题和操作栏 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="title-section">
            <h2 class="page-title">
              <el-icon class="title-icon"><FolderOpened /></el-icon>
              我的数据集
            </h2>
            <p class="page-subtitle">管理和上传您的数据文件，支持 .xlsx, .xls, .csv 格式</p>
          </div>
        </div>
        <div class="header-actions">
          <el-button class="action-btn" :icon="Refresh" @click="fetchList" :loading="loading">
            刷新列表
          </el-button>
        </div>
      </div>
    </div>

    <!-- 上传区域 -->
    <el-card class="upload-section" shadow="never">
      <div class="upload-header">
        <div class="upload-title">
          <el-icon class="section-icon"><Upload /></el-icon>
          <span>上传数据文件</span>
        </div>
        <el-button class="import-btn" type="primary" :icon="DataLine" @click="openImportWizard">
          从数据库导入
        </el-button>
      </div>

      <el-upload
        drag
        :show-file-list="false"
        :http-request="onUploadRequest"
        :disabled="uploading"
        accept=".xlsx,.xls,.csv"
        class="upload-area"
      >
        <div class="upload-content" v-loading="uploading" element-loading-text="正在解析数据...">
          <template v-if="!uploading">
            <div class="upload-icon-wrapper">
              <el-icon class="upload-icon"><Upload /></el-icon>
            </div>
            <div class="upload-text">
              <div class="upload-title">点击或拖拽文件到此处上传</div>
              <div class="upload-hint">支持 .xlsx, .xls, .csv 格式，最大 50MB</div>
            </div>
          </template>
          <template v-else>
            <div class="uploading-content">
              <el-icon class="uploading-icon is-loading" :size="48"><Loading /></el-icon>
              <div class="upload-text">
                <div class="upload-title">正在上传并解析数据...</div>
                <div class="upload-hint">系统正在分析文件结构，请稍候</div>
              </div>
            </div>
          </template>
        </div>
      </el-upload>
    </el-card>

    <!-- 筛选和视图切换 -->
    <div class="filter-section">
      <div class="filter-left">
        <div class="filter-group">
          <el-select
            v-model="status"
            placeholder="状态筛选"
            class="filter-select"
            @change="fetchList"
          >
            <template #prefix><el-icon><Filter /></el-icon></template>
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="搜索文件名..."
            class="search-input"
            clearable
            @keyup.enter="fetchList"
            @clear="fetchList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="fetchList">搜索</el-button>
        </div>
      </div>
      <div class="filter-right">
        <el-radio-group v-model="viewMode" class="view-toggle">
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>
            <span>卡片</span>
          </el-radio-button>
          <el-radio-button value="table">
            <el-icon><List /></el-icon>
            <span>列表</span>
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 卡片视图 -->
    <div v-if="viewMode === 'card'" class="card-grid" v-loading="loading">
      <div
        v-for="dataset in items"
        :key="dataset.id"
        class="dataset-card"
        @click="gotoDetail(dataset)"
      >
        <div class="card-status-bar" :class="dataset.status"></div>
        <div class="card-content">
          <div class="card-header">
            <div class="file-icon-box">
              <el-icon class="file-icon"><Document /></el-icon>
            </div>
            <div class="card-title-section">
              <h3 class="card-title" :title="dataset.name">{{ dataset.name }}</h3>
              <p class="card-time">{{ formatDate(dataset.createdAt) }}</p>
            </div>
          </div>

          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-label">数据量</span>
              <span class="stat-value">{{ dataset.totalRows?.toLocaleString() }} 行</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-label">列数</span>
              <span class="stat-value">{{ dataset.columns?.length || 0 }} 列</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <el-tag :type="statusTagType(dataset.status)" size="small" effect="light">
                {{ statusLabel(dataset.status) }}
              </el-tag>
            </div>
          </div>

          <div class="card-actions" @click.stop>
            <el-button size="small" text @click="gotoDetail(dataset)">
              <el-icon><View /></el-icon>
              查看详情
            </el-button>
            <el-dropdown trigger="click">
              <el-button size="small" text>
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="dataset.status === 'uploaded'" @click="handleArchive(dataset)">
                    <el-icon><Box /></el-icon>
                    归档数据集
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(dataset)" class="danger-item">
                    <el-icon><Delete /></el-icon>
                    删除数据集
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="items.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无数据集">
          <template #image>
            <div class="empty-icon">
              <el-icon :size="80"><Folder /></el-icon>
            </div>
          </template>
          <template #description>
            <p class="empty-text">暂无数据集</p>
            <p class="empty-hint">请上传文件或从数据库导入数据</p>
          </template>
        </el-empty>
      </div>
    </div>

    <!-- 表格视图 -->
    <el-card v-else class="table-section" shadow="never">
      <el-table :data="items" v-loading="loading" class="dataset-table">
        <el-table-column prop="name" label="文件名" min-width="240">
          <template #default="{ row }">
            <div class="table-filename">
              <el-icon class="filename-icon"><Document /></el-icon>
              <span class="filename-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" label="数据行" width="120" align="right">
          <template #default="{ row }">
            <span class="table-number">{{ row.totalRows?.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="列数" width="100" align="right">
          <template #default="{ row }">
            <span class="table-number">{{ row.columns?.length || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            <span class="table-date">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="gotoDetail(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button
              v-if="row.status === 'uploaded'"
              size="small"
              type="warning"
              text
              @click="handleArchive(row)"
            >
              <el-icon><Box /></el-icon>
              归档
            </el-button>
            <el-button size="small" type="danger" text @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 分页 -->
    <div class="pagination-section">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>

    <!-- 上传预览弹窗 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传成功"
      width="85%"
      :close-on-click-modal="false"
      class="preview-dialog"
    >
      <template v-if="uploadResp">
        <div class="preview-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文件名">
              {{ uploadResp.name || uploadResp.originalFilename || uploadResp.filename || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="数据行数">
              {{ uploadResp.totalRows?.toLocaleString() }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="preview-columns">
          <h4 class="preview-section-title">
            <el-icon><DataLine /></el-icon>
            数据列 ({{ uploadResp.columns?.length || 0 }})
          </h4>
          <div class="column-tags">
            <el-tag v-for="(col, idx) in uploadResp.columns" :key="idx" size="small" effect="light">
              {{ getColumnName(col) }}
            </el-tag>
          </div>
        </div>

        <div class="preview-data">
          <h4 class="preview-section-title">
            <el-icon><Document /></el-icon>
            数据预览
          </h4>
          <el-table :data="previewData" height="320" border v-loading="previewLoading" stripe>
            <el-table-column
              v-for="c in columnNames"
              :key="c"
              :prop="c"
              :label="c"
              min-width="150"
              show-overflow-tooltip
            />
            <template #empty>
              <span v-if="previewLoading">加载中...</span>
              <span v-else>暂无数据</span>
            </template>
          </el-table>
        </div>
      </template>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="closeUploadAndGoto">
          <el-icon><View /></el-icon>
          进入数据集详情
        </el-button>
      </template>
    </el-dialog>

    <!-- 数据导入向导组件 -->
    <DataImportWizard ref="importWizardRef" @import-completed="onImportCompleted" />
  </div>
</template>

<style scoped>
/* ==================== Design System Variables ==================== */
.datasets-view {
  --primary-color: #3b82f6;
  --primary-hover: #2563eb;
  --success-color: #10b981;
  --warning-color: #f59e0b;
  --danger-color: #ef4444;
  --info-color: #6366f1;

  --bg-primary: #ffffff;
  --bg-secondary: #f8fafc;
  --bg-tertiary: #f1f5f9;
  --bg-hover: #e2e8f0;

  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-tertiary: #94a3b8;
  --text-inverse: #ffffff;

  --border-light: #e2e8f0;
  --border-medium: #cbd5e1;

  --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.07);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.08);
  --shadow-xl: 0 20px 25px -5px rgb(0 0 0 / 0.1);

  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;

  padding: 32px;
  max-width: 1680px;
  margin: 0 auto;
  background: var(--bg-secondary);
  min-height: calc(100vh - 64px);
}

/* ==================== Page Header ==================== */
.page-header {
  margin-bottom: 32px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.header-left {
  flex: 1;
}

.title-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.title-icon {
  font-size: 32px;
  color: var(--primary-color);
}

.page-subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: all 0.2s ease;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* ==================== Upload Section ==================== */
.upload-section {
  margin-bottom: 24px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-light);
  overflow: hidden;
}

.upload-section :deep(.el-card__body) {
  padding: 28px;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.upload-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.section-icon {
  font-size: 24px;
  color: var(--primary-color);
}

.import-btn {
  height: 38px;
  padding: 0 20px;
  border-radius: var(--radius-md);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.upload-area :deep(.el-upload-dragger) {
  padding: 48px 32px;
  border: 2px dashed var(--border-medium);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--bg-primary) 0%, var(--bg-secondary) 100%);
  transition: all 0.3s ease;
}

.upload-area:hover :deep(.el-upload-dragger) {
  border-color: var(--primary-color);
  background: var(--bg-primary);
  box-shadow: var(--shadow-lg);
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.upload-icon-wrapper {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
}

.upload-icon {
  font-size: 36px;
  color: var(--primary-color);
}

.upload-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.upload-hint {
  color: var(--text-tertiary);
  font-size: 13px;
  text-align: center;
}

.uploading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.uploading-icon {
  color: var(--primary-color);
}

/* ==================== Filter Section ==================== */
.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-left {
  flex: 1;
}

.filter-group {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-select {
  width: 140px;
}

.search-input {
  width: 280px;
}

.view-toggle {
  display: flex;
}

.view-toggle .el-radio-button {
  padding: 8px 16px;
}

.view-toggle .el-radio-button__inner {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

/* ==================== Card Grid ==================== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 24px;
  min-height: 300px;
}

.dataset-card {
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
  border: 1px solid var(--border-light);
}

.dataset-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-xl);
  border-color: var(--primary-color);
}

.card-status-bar {
  height: 4px;
  width: 100%;
}

.card-status-bar.uploaded {
  background: linear-gradient(90deg, var(--success-color), #34d399);
}

.card-status-bar.archived {
  background: linear-gradient(90deg, var(--text-tertiary), #cbd5e1);
}

.card-content {
  padding: 24px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.file-icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-icon {
  font-size: 24px;
  color: var(--success-color);
}

.card-title-section {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}

.card-title {
  margin: 0 0 6px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-time {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}

.card-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 16px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--border-light);
}

.card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.card-actions .el-button {
  font-size: 13px;
}

/* ==================== Empty State ==================== */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 480px;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  border: 1px dashed var(--border-medium);
}

.empty-icon {
  color: var(--text-tertiary);
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.empty-hint {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
}

/* ==================== Table Section ==================== */
.table-section {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
  overflow: hidden;
}

.table-section :deep(.el-card__body) {
  padding: 0;
}

.dataset-table {
  width: 100%;
}

.dataset-table :deep(.el-table__header) {
  background: var(--bg-secondary);
}

.dataset-table :deep(.el-table__header th) {
  background: var(--bg-secondary);
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-medium);
}

.dataset-table :deep(.el-table__body tr) {
  transition: background 0.2s ease;
}

.dataset-table :deep(.el-table__body tr:hover) {
  background: var(--bg-secondary) !important;
}

.table-filename {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
  color: var(--text-primary);
}

.filename-icon {
  font-size: 18px;
  color: var(--text-tertiary);
}

.filename-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-number {
  font-weight: 600;
  color: var(--text-primary);
  font-feature-settings: 'tnum';
}

.table-date {
  color: var(--text-secondary);
  font-size: 13px;
}

/* ==================== Pagination ==================== */
.pagination-section {
  margin-top: 32px;
  display: flex;
  justify-content: center;
  padding: 24px;
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

/* ==================== Preview Dialog ==================== */
.preview-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.preview-info,
.preview-columns,
.preview-data {
  margin-bottom: 28px;
}

.preview-section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  padding-bottom: 12px;
  border-bottom: 2px solid var(--border-light);
}

.column-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* ==================== Danger Item ==================== */
.danger-item {
  color: var(--danger-color);
}

.danger-item:hover {
  background: #fef2f2 !important;
  color: var(--danger-color);
}

/* ==================== Responsive ==================== */
@media (max-width: 768px) {
  .datasets-view {
    padding: 20px 16px;
  }

  .header-content {
    flex-direction: column;
  }

  .page-title {
    font-size: 24px;
  }

  .filter-section {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group {
    flex-direction: column;
  }

  .filter-select,
  .search-input {
    width: 100%;
  }

  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
