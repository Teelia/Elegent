<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as modelConfigApi from '../api/modelConfig'
import type { ModelConfig } from '../api/modelConfig'
import type { Label } from '../api/labels'

const props = defineProps<{
  visible: boolean
  labels: Label[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', config: { modelConfigId: number; includeReasoning: boolean }): void
}>()

const modelConfigs = ref<ModelConfig[]>([])
const selectedModelId = ref<number | null>(null)
const includeReasoning = ref(false)
const configsLoading = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const globalLabels = computed(() => props.labels.filter(l => l.scope !== 'task'))
const tempLabels = computed(() => props.labels.filter(l => l.scope === 'task'))

async function loadModelConfigs() {
  configsLoading.value = true
  try {
    const res = await modelConfigApi.listActiveModelConfigs()
    modelConfigs.value = res
    // 默认选中第一个
    if (res.length > 0 && !selectedModelId.value) {
      // 优先选默认的
      const def = res.find(c => c.isDefault)
      selectedModelId.value = (def && def.id) ? def.id : (res[0].id || null)
    }
  } catch (e) {
    ElMessage.error('加载模型配置失败')
  } finally {
    configsLoading.value = false
  }
}

function handleOpen() {
  loadModelConfigs()
}

function handleConfirm() {
  if (!selectedModelId.value) {
    ElMessage.warning('请选择一个模型配置')
    return
  }
  emit('confirm', {
    modelConfigId: selectedModelId.value,
    includeReasoning: includeReasoning.value
  })
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="启动分析配置"
    width="600px"
    @open="handleOpen"
  >
    <div class="config-form">
      <el-form label-position="top">
        <el-form-item label="选择大模型配置">
          <el-select 
            v-model="selectedModelId" 
            placeholder="请选择模型" 
            style="width: 100%"
            :loading="configsLoading"
          >
            <el-option
              v-for="item in modelConfigs"
              :key="item.id"
              :label="item.name + ' (' + item.providerDisplayName + ' - ' + item.model + ')'"
              :value="item.id"
            />
          </el-select>
          <div class="form-tip">选择用于执行本次分析任务的大语言模型配置。</div>
        </el-form-item>

        <el-form-item label="输出内容配置">
          <el-radio-group v-model="includeReasoning">
            <el-radio :label="false" border>仅结果 (Yes/No)</el-radio>
            <el-radio :label="true" border>结果 + 分析过程 (Reasoning)</el-radio>
          </el-radio-group>
          <div class="form-tip">
            开启"分析过程"会让大模型输出判断依据，有助于验证结果准确性，但会消耗更多Token且速度较慢。
          </div>
        </el-form-item>

        <el-divider content-position="left">本次分析包含的标签</el-divider>
        
        <div class="labels-preview">
          <div v-if="globalLabels.length > 0" class="label-group">
            <div class="group-title">全局/数据集标签</div>
            <div class="tags">
              <el-tag v-for="l in globalLabels" :key="l.id" type="primary" class="label-tag">
                {{ l.name }}
              </el-tag>
            </div>
          </div>
          
          <div v-if="tempLabels.length > 0" class="label-group">
            <div class="group-title">临时标签</div>
            <div class="tags">
              <el-tag v-for="l in tempLabels" :key="l.id" type="warning" class="label-tag">
                {{ l.name }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-form>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm" :loading="loading">
          开始分析
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
.labels-preview {
  max-height: 200px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
}
.label-group {
  margin-bottom: 12px;
}
.label-group:last-child {
  margin-bottom: 0;
}
.group-title {
  font-size: 12px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.label-tag {
  margin-bottom: 4px;
}
</style>
