<script setup lang="ts">
import { computed } from 'vue'
import type { Label } from '@/api/labels'

const props = defineProps<{
  modelValue: boolean
  label: Label | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})
</script>

<template>
  <el-dialog v-model="visible" title="内置标签详情" width="720px">
    <template v-if="label">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="名称">{{ label.name }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ label.version }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ label.builtinCategory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ label.preprocessingMode || 'llm_only' }}</el-descriptions-item>
        <el-descriptions-item label="启用" :span="2">{{ label.isActive ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">规则说明</div>
        <el-input :model-value="label.description" type="textarea" :rows="8" readonly />
      </div>

      <div style="margin-top: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">预处理配置（JSON）</div>
        <el-input :model-value="label.preprocessorConfig || ''" type="textarea" :rows="6" readonly />
      </div>

      <div style="margin-top: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">强化配置（JSON）</div>
        <el-input :model-value="label.enhancementConfig || ''" type="textarea" :rows="4" readonly />
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

