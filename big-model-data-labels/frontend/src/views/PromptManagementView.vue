<template>
  <div class="prompt-management">
    <el-page-header @back="goBack" title="返回">
      <template #content>
        <span class="page-title">系统提示词管理</span>
      </template>
    </el-page-header>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="提示词类型">
          <el-select v-model="filterForm.promptType" placeholder="全部类型" clearable style="width: 150px" @change="loadPrompts">
            <el-option label="分类判断" value="classification" />
            <el-option label="LLM提取" value="extraction" />
            <el-option label="规则验证" value="validation" />
            <el-option label="二次强化" value="enhancement" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="openCreateDialog" :icon="Plus">新建提示词</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 提示词列表 -->
    <el-card v-for="type in promptTypes" :key="type" class="type-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="type-title">{{ PromptTypeNames[type] }}</span>
          <el-tag size="small" type="info">{{ getTypePromptCount(type) }}个</el-tag>
        </div>
      </template>

      <div class="prompt-list">
        <div
          v-for="prompt in getPromptsByType(type)"
          :key="prompt.id"
          class="prompt-item"
          :class="{ 'inactive': !prompt.isActive, 'default': prompt.isSystemDefault }"
        >
          <div class="prompt-header">
            <div class="prompt-info">
              <h4 class="prompt-name">
                {{ prompt.name }}
                <el-tag v-if="isPromptActive(prompt)" size="small" type="success" effect="dark" style="margin-left: 8px">
                  生效中
                </el-tag>
                <el-tag v-if="!isPromptActive(prompt) && prompt.isSystemDefault" size="small" type="info" style="margin-left: 8px">
                  默认
                </el-tag>
                <el-tag v-if="!prompt.isActive" size="small" type="danger" style="margin-left: 8px">已禁用</el-tag>
              </h4>
              <div class="prompt-meta">
                <span class="code">代码: {{ prompt.code }}</span>
                <span class="time">更新于 {{ formatTime(prompt.updatedAt) }}</span>
              </div>
            </div>
            <div class="prompt-actions">
              <el-button-group>
                <el-button size="small" @click="viewPrompt(prompt)">查看</el-button>
                <el-button size="small" type="primary" @click="editPrompt(prompt)">编辑</el-button>
                <el-button
                  size="small"
                  :type="prompt.isActive ? 'warning' : 'success'"
                  @click="togglePrompt(prompt)"
                >
                  {{ prompt.isActive ? '禁用' : '启用' }}
                </el-button>
                <!-- 设为生效按钮：独立显示，更显眼 -->
                <el-button
                  v-if="!isPromptActive(prompt)"
                  size="small"
                  type="success"
                  @click="handleMoreCommand('setDefault', prompt)"
                >
                  {{ prompt.isSystemDefault ? '重新激活' : '设为生效' }}
                </el-button>
                <el-dropdown @command="(cmd: string) => handleMoreCommand(cmd, prompt)">
                  <el-button size="small">
                    更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="duplicate">复制</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </el-button-group>
            </div>
          </div>

          <div class="prompt-preview">
            <pre class="template-preview">{{ truncateTemplate(prompt.template) }}</pre>
          </div>
        </div>

        <el-empty v-if="getPromptsByType(type).length === 0" description="暂无提示词" :image-size="80" />
      </div>
    </el-card>

    <!-- 查看/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'view' ? '查看提示词' : (dialogMode === 'create' ? '新建提示词' : '编辑提示词')"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form :model="promptForm" :rules="formRules" ref="promptFormRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="提示词名称" prop="name">
              <el-input v-model="promptForm.name" :disabled="dialogMode === 'view'" placeholder="请输入提示词名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提示词代码" prop="code">
              <el-input v-model="promptForm.code" :disabled="dialogMode === 'view'" placeholder="英文代码，如: custom_classification" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="提示词类型" prop="promptType">
          <el-select v-model="promptForm.promptType" :disabled="dialogMode === 'view'" style="width: 100%">
            <el-option label="分类判断" value="classification" />
            <el-option label="LLM提取" value="extraction" />
            <el-option label="规则验证" value="validation" />
            <el-option label="二次强化" value="enhancement" />
          </el-select>
        </el-form-item>

        <el-form-item label="模板内容" prop="template">
          <el-input
            type="textarea"
            v-model="promptForm.template"
            :disabled="dialogMode === 'view'"
            :rows="15"
            placeholder="请输入提示词模板，支持变量: {{variable_name}}"
          />
        </el-form-item>

        <el-form-item label="变量说明">
          <div class="variable-hints">
            <el-tag
              v-for="(desc, name) in VariableDescriptions"
              :key="name"
              size="small"
              style="margin: 4px"
              @click="insertVariable(name)"
              :class="{ 'clickable': dialogMode !== 'view' }"
            >
              <code>{&lbrace;&lbrace;{{ name }}&rbrace;&rbrace;}</code> - {{ desc }}
            </el-tag>
          </div>
          <div class="form-hint" v-if="dialogMode !== 'view'">点击变量可插入到模板中</div>
        </el-form-item>

        <el-form-item label="条件块">
          <div class="conditional-hint">
            <code>{&lbrace;&lbrace;#if variable&rbrace;&rbrace;}...{&lbrace;&lbrace;/if&rbrace;&rbrace;}</code>
            <span>当变量非空时显示内容</span>
          </div>
        </el-form-item>

        <el-form-item label="启用状态" v-if="dialogMode !== 'create'">
          <el-switch v-model="promptForm.isActive" :disabled="dialogMode === 'view'" />
        </el-form-item>
      </el-form>

      <template #footer v-if="dialogMode !== 'view'">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPrompt" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <p>确定要删除提示词「{{ deletingPrompt?.name }}」吗？此操作不可恢复。</p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete" :loading="deleting">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import {
  listSystemPrompts,
  createSystemPrompt,
  updateSystemPrompt,
  deleteSystemPrompt,
  toggleSystemPrompt,
  setAsDefaultPrompt,
  type SystemPrompt,
  type CreatePromptRequest,
  PromptTypeNames,
  VariableDescriptions,
  type PromptType
} from '@/api/system-prompts'

// 定义各类型提示词的默认 code
const DEFAULT_PROMPT_CODES: Record<string, string[]> = {
  classification: [], // 分类判断使用 is_system_default 判断
  extraction: [],    // 提取使用 is_system_default 判断
  validation: [],    // 验证使用 is_system_default 判断
  enhancement: []    // 强化使用 is_system_default 判断
}

const router = useRouter()

// 数据
const prompts = ref<SystemPrompt[]>([])
const loading = ref(false)
const submitting = ref(false)
const deleting = ref(false)

// 筛选
const filterForm = reactive({
  promptType: undefined as PromptType | undefined
})

const promptTypes: PromptType[] = ['classification', 'extraction', 'validation', 'enhancement']

// 对话框
const dialogVisible = ref(false)
const dialogMode = ref<'view' | 'create' | 'edit'>('view')
const deleteDialogVisible = ref(false)
const deletingPrompt = ref<SystemPrompt | null>(null)

// 表单
const promptFormRef = ref<FormInstance>()
const promptForm = reactive<CreatePromptRequest & { id?: number; isActive?: boolean }>({
  name: '',
  code: '',
  promptType: 'classification',
  template: '',
  variables: [],
  isActive: true
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入提示词名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入提示词代码', trigger: 'blur' },
    { pattern: /^[a-z_][a-z0-9_]*$/, message: '代码只能包含小写字母、数字和下划线，且必须以字母或下划线开头', trigger: 'blur' }
  ],
  promptType: [{ required: true, message: '请选择提示词类型', trigger: 'change' }],
  template: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

// 计算属性
const getPromptsByType = (type: PromptType) => {
  return prompts.value.filter(p => p.promptType === type)
}

const getTypePromptCount = (type: PromptType) => {
  return getPromptsByType(type).length
}

/**
 * 判断提示词是否为当前生效的提示词
 * 判断逻辑：
 * - 提示词必须启用 (isActive = true)
 * - 提示词必须为默认提示词 (isSystemDefault = true)
 * - 每个类型只有一个生效的提示词
 */
const isPromptActive = (prompt: SystemPrompt) => {
  if (!prompt.isActive) return false

  // 所有类型统一使用 is_system_default 判断
  return prompt.isSystemDefault === true
}

// 方法
const goBack = () => {
  router.back()
}

const loadPrompts = async () => {
  loading.value = true
  try {
    prompts.value = await listSystemPrompts({
      promptType: filterForm.promptType
    })
  } catch (error: any) {
    ElMessage.error(error.message || '加载提示词失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}

const truncateTemplate = (template: string) => {
  if (template.length <= 200) return template
  return template.substring(0, 200) + '...'
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const viewPrompt = (prompt: SystemPrompt) => {
  dialogMode.value = 'view'
  Object.assign(promptForm, {
    id: prompt.id,
    name: prompt.name,
    code: prompt.code,
    promptType: prompt.promptType,
    template: prompt.template,
    variables: prompt.variables,
    isActive: prompt.isActive
  })
  dialogVisible.value = true
}

const editPrompt = (prompt: SystemPrompt) => {
  dialogMode.value = 'edit'
  Object.assign(promptForm, {
    id: prompt.id,
    name: prompt.name,
    code: prompt.code,
    promptType: prompt.promptType,
    template: prompt.template,
    variables: prompt.variables,
    isActive: prompt.isActive
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(promptForm, {
    name: '',
    code: '',
    promptType: 'classification',
    template: '',
    variables: [],
    isActive: true
  })
  promptFormRef.value?.clearValidate()
}

const insertVariable = (name: string) => {
  const textarea = document.querySelector('textarea[name="template"]') as HTMLTextAreaElement
  if (!textarea) return

  const variable = `{{${name}}}`
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const text = promptForm.template
  promptForm.template = text.substring(0, start) + variable + text.substring(end)

  // 设置光标位置
  setTimeout(() => {
    textarea.focus()
    textarea.setSelectionRange(start + variable.length, start + variable.length)
  }, 0)
}

const submitPrompt = async () => {
  if (!promptFormRef.value) return

  await promptFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const data = {
        name: promptForm.name,
        code: promptForm.code,
        promptType: promptForm.promptType,
        template: promptForm.template,
        variables: promptForm.variables,
        isActive: promptForm.isActive
      }

      if (dialogMode.value === 'create') {
        await createSystemPrompt(data)
        ElMessage.success('创建成功')
      } else if (promptForm.id) {
        await updateSystemPrompt(promptForm.id, data)
        ElMessage.success('更新成功')
      }

      dialogVisible.value = false
      await loadPrompts()
    } catch (error: any) {
      ElMessage.error(error.message || '保存失败')
    } finally {
      submitting.value = false
    }
  })
}

const togglePrompt = async (prompt: SystemPrompt) => {
  try {
    const action = prompt.isActive ? '禁用' : '启用'
    await ElMessageBox.confirm(`确定要${action}提示词「${prompt.name}」吗？`, '确认操作', {
      type: 'warning'
    })

    await toggleSystemPrompt(prompt.id)
    ElMessage.success(`${action}成功`)
    await loadPrompts()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleMoreCommand = async (command: string, prompt: SystemPrompt) => {
  switch (command) {
    case 'setDefault':
      await setAsDefault(prompt)
      break
    case 'duplicate':
      await duplicatePrompt(prompt)
      break
    case 'delete':
      await promptToDelete(prompt)
      break
  }
}

const setAsDefault = async (prompt: SystemPrompt) => {
  try {
    const isActive = prompt.isActive
    const actionText = isActive ? '设为生效' : '设为生效并启用'

    await ElMessageBox.confirm(
      `确定要将「${prompt.name}」${actionText}吗？\n\n此操作将使该提示词成为${PromptTypeNames[prompt.promptType]}类型的生效提示词，同类型的其他提示词将自动失效。`,
      '设为生效',
      { type: 'info' }
    )

    await setAsDefaultPrompt(prompt.id)
    ElMessage.success('设置成功，该提示词现已生效')
    await loadPrompts()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '设置失败')
    }
  }
}

const duplicatePrompt = async (prompt: SystemPrompt) => {
  dialogMode.value = 'create'
  Object.assign(promptForm, {
    name: `${prompt.name} (副本)`,
    code: `${prompt.code}_copy_${Date.now()}`,
    promptType: prompt.promptType,
    template: prompt.template,
    variables: prompt.variables,
    isActive: true
  })
  dialogVisible.value = true
}

const promptToDelete = async (prompt: SystemPrompt) => {
  deletingPrompt.value = prompt
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (!deletingPrompt.value) return

  deleting.value = true
  try {
    await deleteSystemPrompt(deletingPrompt.value.id)
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    deletingPrompt.value = null
    await loadPrompts()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

// 生命周期
onMounted(() => {
  loadPrompts()
})
</script>

<style scoped>
.prompt-management {
  padding: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.filter-card {
  margin: 20px 0;
}

.type-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.type-title {
  font-size: 16px;
  font-weight: 600;
}

.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.prompt-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s;
  background: #fff;
}

.prompt-item:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.prompt-item.inactive {
  opacity: 0.6;
  background: #f5f7fa;
}

.prompt-item.default {
  border-color: #67c23a;
}

.prompt-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.prompt-info {
  flex: 1;
}

.prompt-name {
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 500;
}

.prompt-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.prompt-actions {
  flex-shrink: 0;
}

.prompt-preview {
  margin-top: 12px;
}

.template-preview {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 100px;
  overflow: hidden;
  position: relative;
}

.template-preview::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: linear-gradient(transparent, #f5f7fa);
}

.variable-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.variable-hints .el-tag.clickable {
  cursor: pointer;
  user-select: none;
}

.variable-hints .el-tag.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.conditional-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.conditional-hint code {
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
}
</style>
