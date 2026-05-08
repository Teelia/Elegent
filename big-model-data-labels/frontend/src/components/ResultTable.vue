<script setup lang="ts">
import { computed, ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { DataRow } from '../api/rows'

const props = defineProps<{
  rows: DataRow[]
  originalColumns: string[]
  labelKeys: string[]
  /** 是否显示判断依据 */
  showReasoning?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:showReasoning', value: boolean): void
}>()

// 内部状态：是否显示判断依据
const internalShowReasoning = ref(props.showReasoning ?? false)

const tableColumns = computed(() => {
  return [...props.originalColumns, ...props.labelKeys]
})

const labelHeaderMap = computed(() => {
  const map: Record<string, string> = {}
  for (const k of props.labelKeys) {
    const m = /^(.*)_v\d+$/.exec(k)
    map[k] = m ? m[1] : k
  }
  return map
})

// 获取某行某标签的判断依据
function getReasoning(row: DataRow, labelKey: string): string | undefined {
  return row.aiReasoning?.[labelKey]
}

// 获取某行某标签的信心度
function getConfidence(row: DataRow, labelKey: string): number | undefined {
  return row.aiConfidence?.[labelKey]
}

// 格式化信心度
function formatConfidence(confidence: number | undefined): string {
  if (confidence === undefined) return '-'
  return `${(confidence * 100).toFixed(0)}%`
}

// 切换显示判断依据
function toggleShowReasoning() {
  internalShowReasoning.value = !internalShowReasoning.value
  emit('update:showReasoning', internalShowReasoning.value)
}
</script>

<template>
  <div class="result-table-wrapper">
    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-checkbox v-model="internalShowReasoning" @change="toggleShowReasoning">
        显示判断依据
      </el-checkbox>
    </div>

    <el-table :data="rows" height="520" style="width: 100%">
      <el-table-column label="#" width="70" fixed="left">
        <template #default="{ row }">{{ row.rowIndex }}</template>
      </el-table-column>

      <!-- 原始数据列 -->
      <el-table-column
        v-for="c in originalColumns"
        :key="c"
        :label="c"
        min-width="160"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.originalData?.[c] }}
        </template>
      </el-table-column>

      <!-- 标签结果列 -->
      <el-table-column
        v-for="labelKey in labelKeys"
        :key="labelKey"
        :label="labelHeaderMap[labelKey] || labelKey"
        :min-width="internalShowReasoning ? 280 : 160"
      >
        <template #default="{ row }">
          <div class="label-cell">
            <!-- 标签值选择器 -->
            <div class="label-value">
              <slot name="labelCell" :row="row" :labelKey="labelKey" />
              <!-- 信心度指示 -->
              <el-tooltip
                v-if="getConfidence(row, labelKey) !== undefined"
                :content="`AI信心度: ${formatConfidence(getConfidence(row, labelKey))}`"
                placement="top"
              >
                <span
                  class="confidence-badge"
                  :class="{
                    'high': (getConfidence(row, labelKey) || 0) >= 0.8,
                    'medium': (getConfidence(row, labelKey) || 0) >= 0.5 && (getConfidence(row, labelKey) || 0) < 0.8,
                    'low': (getConfidence(row, labelKey) || 0) < 0.5
                  }"
                >
                  {{ formatConfidence(getConfidence(row, labelKey)) }}
                </span>
              </el-tooltip>
            </div>
            <!-- 判断依据 -->
            <div v-if="internalShowReasoning && getReasoning(row, labelKey)" class="reasoning-text">
              <el-icon class="reasoning-icon"><InfoFilled /></el-icon>
              <span>{{ getReasoning(row, labelKey) }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.result-table-wrapper {
  width: 100%;
}

.table-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.label-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label-value {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confidence-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 500;
}

.confidence-badge.high {
  background-color: #e1f3d8;
  color: #67c23a;
}

.confidence-badge.medium {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.confidence-badge.low {
  background-color: #fef0f0;
  color: #f56c6c;
}

.reasoning-text {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  padding: 4px 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
  max-width: 100%;
}

.reasoning-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: #909399;
}

.reasoning-text span {
  word-break: break-word;
}
</style>

