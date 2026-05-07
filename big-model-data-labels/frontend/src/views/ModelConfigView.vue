<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Star, StarFilled, Edit, Delete, Setting, Check, Close } from '@element-plus/icons-vue'

import type { ModelConfig, CreateModelConfigRequest, UpdateModelConfigRequest } from '../api/modelConfig'
import * as modelApi from '../api/modelConfig'
import { PROVIDER_OPTIONS } from '../api/modelConfig'

const loading = ref(false)
const saving = ref(false)

// 配置列表
const configList = ref<ModelConfig[]>([])

// 抽屉相关
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const editingConfig = ref<ModelConfig | null>(null)

// 表单数据
const form = reactive({
  name: '',
  provider: 'deepseek',
  baseUrl: '',
  model: '',
  apiKey: '',
  clearApiKey: false,
  timeout: 30000,
  temperature: 0.1,
  maxTokens: 500,
  retryTimes: 3,
  maxConcurrency: 10,
  isActive: true,
  isDefault: false,
  description: '',
})

// 根据provider获取默认值
const currentProviderOption = computed(() => {
  return PROVIDER_OPTIONS.find(p => p.value === form.provider)
})

// 是否为本地模型
const isLocalModel = computed(() => form.provider === 'local-deepseek')

// 抽屉标题
const drawerTitle = computed(() => {
  return drawerMode.value === 'create' ? '新建模型配置' : '编辑模型配置'
})

// 加载配置列表
async function loadList() {
  loading.value = true
  try {
    configList.value = await modelApi.listModelConfigs()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载配置列表失败')
  } finally {
    loading.value = false
  }
}

// 打开新建抽屉
function openCreateDrawer() {
  drawerMode.value = 'create'
  editingConfig.value = null
  const defaultProvider = PROVIDER_OPTIONS[0]
  Object.assign(form, {
    name: '',
    provider: defaultProvider.value,
    baseUrl: defaultProvider.defaultBaseUrl,
    model: defaultProvider.defaultModel,
    apiKey: '',
    clearApiKey: false,
    timeout: 30000,
    temperature: 0.1,
    maxTokens: 500,
    retryTimes: 3,
    maxConcurrency: 10,
    isActive: true,
    isDefault: false,
    description: '',
  })
  drawerVisible.value = true
}

// 打开编辑抽屉
function openEditDrawer(config: ModelConfig) {
  drawerMode.value = 'edit'
  editingConfig.value = config
  Object.assign(form, {
    name: config.name,
    provider: config.provider,
    baseUrl: config.baseUrl,
    model: config.model,
    apiKey: '',
    clearApiKey: false,
    timeout: config.timeout,
    temperature: config.temperature,
    maxTokens: config.maxTokens,
    retryTimes: config.retryTimes,
    maxConcurrency: config.maxConcurrency ?? 10,
    isActive: config.isActive,
    isDefault: config.isDefault ?? false,
    description: config.description ?? '',
  })
  drawerVisible.value = true
}

// 关闭抽屉
function closeDrawer() {
  drawerVisible.value = false
  form.apiKey = ''
  form.clearApiKey = false
}

// Provider变化时更新默认值
function onProviderChange(provider: string) {
  const option = PROVIDER_OPTIONS.find(p => p.value === provider)
  if (option && drawerMode.value === 'create') {
    form.baseUrl = option.defaultBaseUrl
    form.model = option.defaultModel
  }
}

// 保存配置
async function save() {
  saving.value = true
  try {
    if (!form.name.trim()) {
      ElMessage.warning('配置名称不能为空')
      return
    }
    if (!form.baseUrl.trim() || !form.model.trim()) {
      ElMessage.warning('Base URL 和 Model 不能为空')
      return
    }

    if (form.clearApiKey) {
      await ElMessageBox.confirm('确认清空 API Key？清空后将无法调用此模型。', '提示', { type: 'warning' })
    }

    if (drawerMode.value === 'create') {
      // 创建新配置
      const payload: CreateModelConfigRequest = {
        name: form.name.trim(),
        provider: form.provider,
        baseUrl: form.baseUrl.trim(),
        model: form.model.trim(),
        apiKey: form.apiKey.trim() || undefined,
        timeout: form.timeout,
        temperature: form.temperature,
        maxTokens: form.maxTokens,
        retryTimes: form.retryTimes,
        maxConcurrency: form.maxConcurrency,
        isActive: form.isActive,
        isDefault: form.isDefault,
        description: form.description.trim() || undefined,
      }
      await modelApi.createModelConfig(payload)
      ElMessage.success('创建成功')
    } else if (editingConfig.value?.id) {
      // 更新配置
      const payload: UpdateModelConfigRequest = {
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        model: form.model.trim(),
        apiKey: form.apiKey.trim() || undefined,
        clearApiKey: form.clearApiKey || undefined,
        timeout: form.timeout,
        temperature: form.temperature,
        maxTokens: form.maxTokens,
        retryTimes: form.retryTimes,
        maxConcurrency: form.maxConcurrency,
        isActive: form.isActive,
        isDefault: form.isDefault,
        description: form.description.trim() || undefined,
      }
      await modelApi.updateModelConfig(editingConfig.value.id, payload)
      ElMessage.success('保存成功')
    }

    closeDrawer()
    await loadList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 删除配置
async function deleteConfig(config: ModelConfig) {
  if (!config.id) return
  if (config.isDefault) {
    ElMessage.warning('不能删除默认配置')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除配置「${config.name}」吗？`, '确认删除', { type: 'warning' })
    await modelApi.deleteModelConfig(config.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

// 设为默认配置
async function setDefault(config: ModelConfig) {
  if (!config.id || config.isDefault) return
  try {
    await modelApi.setDefaultModelConfig(config.id)
    ElMessage.success('已设为默认配置')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '设置失败')
  }
}

// 切换启用状态
async function toggleActive(config: ModelConfig, next: boolean) {
  if (!config.id) return
  try {
    const payload: UpdateModelConfigRequest = {
      name: config.name,
      baseUrl: config.baseUrl,
      model: config.model,
      timeout: config.timeout,
      temperature: config.temperature,
      maxTokens: config.maxTokens,
      retryTimes: config.retryTimes,
      maxConcurrency: config.maxConcurrency,
      isActive: next,
      isDefault: config.isDefault,
    }
    await modelApi.updateModelConfig(config.id, payload)
    config.isActive = next
    ElMessage.success(next ? '已启用' : '已禁用')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
    await loadList()
  }
}

// 获取提供商图标
function getProviderIcon(provider: string) {
  const iconMap: Record<string, string> = {
    'deepseek': '🧠',
    'local-deepseek': '🏠',
    'qwen': '💬',
    'openai': '🤖',
  }
  return iconMap[provider] || '⚙️'
}

// 计算并发使用率
function getConcurrencyPercent(config: ModelConfig) {
  const current = config.currentConcurrency ?? 0
  const max = config.maxConcurrency ?? 10
  return Math.round((current / max) * 100)
}

onMounted(loadList)
</script>

<template>
  <div class="model-config-view">
    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">
          <el-icon class="title-icon"><Setting /></el-icon>
          模型配置
        </h2>
        <p class="page-subtitle">管理大模型配置，支持 DeepSeek、通义千问、OpenAI 等多种提供商</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Plus" type="primary" size="large" @click="openCreateDrawer">
          新建配置
        </el-button>
      </div>
    </div>

    <!-- 配置卡片网格 -->
    <div v-if="configList.length > 0" class="config-grid" v-loading="loading">
      <div
        v-for="config in configList"
        :key="config.id ?? config.name"
        class="config-card"
        :class="{
          'is-default': config.isDefault,
          'is-inactive': !config.isActive
        }"
      >
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="provider-icon">{{ getProviderIcon(config.provider) }}</div>
          <div class="card-title-row">
            <h3 class="card-title">{{ config.name }}</h3>
            <div class="card-badges">
              <el-tag v-if="config.isDefault" type="warning" size="small" effect="dark">
                <el-icon style="margin-right: 4px;"><StarFilled /></el-icon>
                默认
              </el-tag>
              <el-tag
                :type="config.isActive ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ config.isActive ? '已启用' : '已禁用' }}
              </el-tag>
            </div>
          </div>
          <p class="card-provider">{{ config.providerDisplayName || config.provider }}</p>
        </div>

        <!-- 分割线 -->
        <div class="card-divider"></div>

        <!-- 卡片信息 -->
        <div class="card-info">
          <div class="info-item">
            <span class="info-label">API</span>
            <span class="info-value">{{ config.baseUrl }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">模型</span>
            <span class="info-value">{{ config.model }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">并发</span>
            <div class="concurrency-info">
              <span class="info-value">{{ config.currentConcurrency ?? 0 }}/{{ config.maxConcurrency ?? 10 }}</span>
              <div class="concurrency-bar">
                <div
                  class="concurrency-fill"
                  :style="{ width: getConcurrencyPercent(config) + '%' }"
                ></div>
              </div>
            </div>
          </div>
          <div class="info-item" v-if="config.description">
            <span class="info-label">描述</span>
            <span class="info-value text-ellipsis">{{ config.description }}</span>
          </div>
        </div>

        <!-- 卡片操作 -->
        <div class="card-actions">
          <el-button
            v-if="!config.isDefault"
            text
            size="small"
            @click="setDefault(config)"
          >
            <el-icon><Star /></el-icon>
            设为默认
          </el-button>
          <el-button
            :type="config.isActive ? 'warning' : 'success'"
            text
            size="small"
            @click="toggleActive(config, !config.isActive)"
          >
            <el-icon><component :is="config.isActive ? Close : Check" /></el-icon>
            {{ config.isActive ? '禁用' : '启用' }}
          </el-button>
          <div class="action-divider"></div>
          <el-button text type="primary" size="small" @click="openEditDrawer(config)">
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
          <el-button
            text
            type="danger"
            size="small"
            @click="deleteConfig(config)"
            :disabled="config.isDefault"
          >
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </div>

      <!-- 新建卡片（快捷入口） -->
      <div class="config-card config-card-add" @click="openCreateDrawer">
        <div class="add-icon">
          <el-icon :size="48"><Plus /></el-icon>
        </div>
        <p class="add-text">新建配置</p>
        <p class="add-hint">点击添加新的模型配置</p>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!loading" class="empty-state">
      <div class="empty-icon">🧠</div>
      <h3 class="empty-title">暂无模型配置</h3>
      <p class="empty-desc">创建第一个模型配置，开始使用 AI 分析功能</p>
      <el-button type="primary" :icon="Plus" size="large" @click="openCreateDrawer">
        新建配置
      </el-button>
    </div>

    <!-- 编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="480px"
      :before-close="closeDrawer"
      class="config-drawer"
    >
      <div class="drawer-content">
        <el-form label-width="100px" label-position="left">
          <!-- 基本信息 -->
          <div class="form-section">
            <div class="form-section-title">
              <span class="title-text">基本信息</span>
              <div class="title-line"></div>
            </div>

            <el-form-item label="配置名称" required>
              <el-input
                v-model="form.name"
                placeholder="例如：DeepSeek 生产环境"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="提供商" required>
              <el-select
                v-model="form.provider"
                style="width: 100%"
                @change="onProviderChange"
                :disabled="drawerMode === 'edit'"
              >
                <el-option
                  v-for="opt in PROVIDER_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.label"
                />
              </el-select>
              <div v-if="drawerMode === 'edit'" class="form-tip">提供商创建后不可修改</div>
            </el-form-item>

            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="可选，配置用途说明"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </div>

          <!-- API 配置 -->
          <div class="form-section">
            <div class="form-section-title">
              <span class="title-text">API 配置</span>
              <div class="title-line"></div>
            </div>

            <el-form-item label="Base URL" required>
              <el-input
                v-model="form.baseUrl"
                :placeholder="currentProviderOption?.defaultBaseUrl || 'https://api.deepseek.com/v1'"
              />
              <div v-if="isLocalModel" class="form-tip">本地模型示例：http://10.42.101.29:8000/v1</div>
            </el-form-item>

            <el-form-item label="Model" required>
              <el-input
                v-model="form.model"
                :placeholder="currentProviderOption?.defaultModel || 'deepseek-chat'"
              />
              <div v-if="isLocalModel" class="form-tip">本地 DeepSeek 70B 模型示例：/model_70b</div>
            </el-form-item>

            <el-form-item label="API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="editingConfig?.apiKeyConfigured ? '已配置（留空不修改）' : '请输入 API Key'"
              />
              <div v-if="isLocalModel" class="form-tip">本地部署模型通常使用固定的 API Key，如 sk-123456</div>
            </el-form-item>

            <el-form-item
              v-if="editingConfig?.apiKeyConfigured && drawerMode === 'edit'"
              label="清空 API Key"
            >
              <el-switch v-model="form.clearApiKey" />
              <span class="form-tip-inline" style="margin-left: 10px">清空后将无法调用此模型</span>
            </el-form-item>
          </div>

          <!-- 模型参数 -->
          <div class="form-section">
            <div class="form-section-title">
              <span class="title-text">模型参数</span>
              <div class="title-line"></div>
            </div>

            <div class="form-row">
              <el-form-item label="超时时间">
                <el-input-number
                  v-model="form.timeout"
                  :min="1000"
                  :max="300000"
                  :step="1000"
                  style="width: 100%"
                />
                <span class="input-suffix">ms</span>
              </el-form-item>

              <el-form-item label="温度">
                <el-input-number
                  v-model="form.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  :precision="2"
                  style="width: 100%"
                />
              </el-form-item>
            </div>

            <div class="form-row">
              <el-form-item label="最大令牌">
                <el-input-number
                  v-model="form.maxTokens"
                  :min="1"
                  :max="8192"
                  style="width: 100%"
                />
              </el-form-item>

              <el-form-item label="重试次数">
                <el-input-number
                  v-model="form.retryTimes"
                  :min="0"
                  :max="10"
                  style="width: 100%"
                />
              </el-form-item>
            </div>

            <el-form-item label="最大并发">
              <el-input-number
                v-model="form.maxConcurrency"
                :min="1"
                :max="100"
                style="width: 100%"
              />
              <div class="form-tip">全局并发上限，超过将排队等待</div>
            </el-form-item>
          </div>

          <!-- 状态设置 -->
          <div class="form-section">
            <div class="form-section-title">
              <span class="title-text">状态设置</span>
              <div class="title-line"></div>
            </div>

            <el-form-item label="启用配置">
              <el-switch v-model="form.isActive" />
              <span class="form-tip-inline" style="margin-left: 10px">禁用后此配置不可用于分析任务</span>
            </el-form-item>

            <el-form-item label="设为默认">
              <el-switch v-model="form.isDefault" :disabled="editingConfig?.isDefault" />
              <span class="form-tip-inline" style="margin-left: 10px">分析任务默认使用的模型配置</span>
            </el-form-item>
          </div>

          <!-- 配置元信息 -->
          <div v-if="editingConfig && drawerMode === 'edit'" class="form-section">
            <div class="form-section-title">
              <span class="title-text">配置信息</span>
              <div class="title-line"></div>
            </div>

            <div class="metadata-grid">
              <div class="metadata-item">
                <span class="metadata-label">配置ID</span>
                <span class="metadata-value">{{ editingConfig.id }}</span>
              </div>
              <div class="metadata-item">
                <span class="metadata-label">来源</span>
                <span class="metadata-value">{{ editingConfig.fromDb ? '数据库' : 'application.yml' }}</span>
              </div>
              <div class="metadata-item">
                <span class="metadata-label">创建时间</span>
                <span class="metadata-value">{{ editingConfig.createdAt || '-' }}</span>
              </div>
              <div class="metadata-item">
                <span class="metadata-label">更新时间</span>
                <span class="metadata-value">{{ editingConfig.updatedAt || '-' }}</span>
              </div>
            </div>
          </div>
        </el-form>
      </div>

      <!-- 抽屉底部按钮 -->
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="closeDrawer">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">
            {{ drawerMode === 'create' ? '创建配置' : '保存修改' }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.model-config-view {
  animation: fadeInUp 0.4s ease-out;
}

/* 页面标题区 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
  animation: fadeInDown 0.5s ease-out;
}

.header-left {
  flex: 1;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #1a202c;
}

.title-icon {
  font-size: 28px;
  color: var(--accent-color, #00d4ff);
}

.page-subtitle {
  margin: 0;
  color: #718096;
  font-size: 14px;
  font-weight: 400;
}

.header-actions {
  display: flex;
  gap: 12px;
}

/* 卡片网格 */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  animation: fadeInUp 0.5s ease-out 0.1s both;
}

/* 卡片基础样式 */
.config-card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 2px solid transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.config-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--accent-color, #00d4ff), transparent);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease;
}

.config-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 212, 255, 0.15);
}

.config-card:hover::before {
  transform: scaleX(1);
}

.config-card.is-default {
  border-color: var(--accent-color, #00d4ff);
  background: linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%);
}

.config-card.is-inactive {
  opacity: 0.6;
}

/* 卡片头部 */
.card-header {
  margin-bottom: 16px;
}

.provider-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.15) 0%, rgba(0, 212, 255, 0.05) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 12px;
  border: 1px solid rgba(0, 212, 255, 0.2);
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 4px;
  gap: 8px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a202c;
  margin: 0;
  line-height: 1.4;
  flex: 1;
}

.card-badges {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.card-provider {
  font-size: 13px;
  color: #718096;
  margin: 0;
}

/* 分割线 */
.card-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #e2e8f0, transparent);
  margin-bottom: 16px;
}

/* 卡片信息 */
.card-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.info-label {
  font-size: 12px;
  color: #a0aec0;
  min-width: 36px;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: #4a5568;
  word-break: break-all;
}

.info-value.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.concurrency-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.concurrency-bar {
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
}

.concurrency-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-color, #00d4ff), var(--accent-dark, #0891b2));
  transition: width 0.3s ease;
}

/* 卡片操作 */
.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.action-divider {
  width: 1px;
  background: #e2e8f0;
  margin: 0 4px;
}

/* 新建卡片 */
.config-card-add {
  cursor: pointer;
  border: 2px dashed #cbd5e0;
  background: transparent;
  box-shadow: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}

.config-card-add:hover {
  border-color: var(--accent-color, #00d4ff);
  background: rgba(0, 212, 255, 0.03);
  transform: translateY(-2px);
}

.config-card-add::before {
  display: none;
}

.add-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.1) 0%, rgba(0, 212, 255, 0.05) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-color, #00d4ff);
  margin-bottom: 16px;
  transition: all 0.3s ease;
}

.config-card-add:hover .add-icon {
  background: var(--accent-color, #00d4ff);
  color: white;
  transform: scale(1.1);
}

.add-text {
  font-size: 16px;
  font-weight: 600;
  color: #1a202c;
  margin: 0 0 4px 0;
}

.add-hint {
  font-size: 13px;
  color: #a0aec0;
  margin: 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  animation: fadeInUp 0.5s ease-out;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a202c;
  margin: 0 0 8px 0;
}

.empty-desc {
  font-size: 14px;
  color: #718096;
  margin: 0 0 24px 0;
  text-align: center;
  max-width: 400px;
}

/* 抽屉样式 */
.drawer-content {
  padding: 0 8px;
  overflow-y: auto;
  max-height: calc(100vh - 120px);
}

.form-section {
  margin-bottom: 28px;
}

.form-section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 8px;
}

.title-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color, #1a365d);
  white-space: nowrap;
}

.title-line {
  flex: 1;
  height: 2px;
  background: linear-gradient(90deg, var(--accent-color, #00d4ff), transparent);
  border-radius: 1px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.el-form-item {
  margin-bottom: 16px;
}

.form-tip {
  color: #a0aec0;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.4;
}

.form-tip-inline {
  color: #718096;
  font-size: 13px;
}

/* 表单项后缀 */
.input-suffix {
  margin-left: 8px;
  color: #a0aec0;
  font-size: 13px;
}

/* 元信息网格 */
.metadata-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
}

.metadata-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metadata-label {
  font-size: 12px;
  color: #a0aec0;
}

.metadata-value {
  font-size: 13px;
  color: #4a5568;
  font-weight: 500;
}

/* 抽屉底部 */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
}

/* 响应式 */
@media (max-width: 1440px) {
  .config-grid {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .metadata-grid {
    grid-template-columns: 1fr;
  }
}

/* 动画 */
@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 抽屉全局样式覆盖 */
/* 遮罩层背景 */
:deep(.el-overlay) {
  background-color: rgba(0, 0, 0, 0.4) !important;
}

/* 抽屉容器 - 确保有白色背景 */
:deep(.config-drawer .el-drawer) {
  background: #ffffff !important;
}

/* 抽屉头部 */
:deep(.config-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  color: #1a202c !important;
}

:deep(.config-drawer .el-drawer__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1a365d !important;
}

:deep(.config-drawer .el-drawer__headerbtn) {
  color: #718096 !important;
}

:deep(.config-drawer .el-drawer__headerbtn:hover) {
  color: #1a202c !important;
}

/* 抽屉主体 - 确保白色背景 */
:deep(.config-drawer .el-drawer__body) {
  padding: 24px;
  background: #ffffff !important;
  color: #1a202c !important;
}

/* 抽屉底部 */
:deep(.config-drawer .el-drawer__footer) {
  padding: 0;
  background: #ffffff !important;
}

/* 表单样式覆盖 */
:deep(.config-drawer .el-form-item__label) {
  font-weight: 500;
  color: #374151 !important;
  font-size: 14px;
}

:deep(.config-drawer .el-input__inner) {
  color: #1a202c !important;
}

:deep(.config-drawer .el-input__wrapper) {
  border-radius: 8px;
  transition: all 0.3s ease;
  background-color: #ffffff !important;
}

:deep(.config-drawer .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent-color, #00d4ff) inset !important;
}

:deep(.config-drawer .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(0, 212, 255, 0.2) inset !important;
}

:deep(.config-drawer .el-textarea__inner) {
  color: #1a202c !important;
  background-color: #ffffff !important;
}

:deep(.config-drawer .el-select .el-input__inner) {
  color: #1a202c !important;
}

/* 开关颜色 */
:deep(.config-drawer .el-switch) {
  --el-switch-on-color: var(--accent-color, #00d4ff);
}

/* 分组标题颜色 */
:deep(.config-drawer .form-section-title .title-text) {
  color: #1a365d !important;
}

/* 元数据网格颜色 */
:deep(.config-drawer .metadata-label) {
  color: #a0aec0 !important;
}

:deep(.config-drawer .metadata-value) {
  color: #4a5568 !important;
}

/* 提示文字颜色 */
:deep(.config-drawer .form-tip) {
  color: #a0aec0 !important;
}

:deep(.config-drawer .form-tip-inline) {
  color: #718096 !important;
}

:deep(.config-drawer .input-suffix) {
  color: #a0aec0 !important;
}

/* 标签样式 */
:deep(.el-tag) {
  border-radius: 6px;
  font-weight: 500;
}
</style>
