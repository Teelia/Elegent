<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { SyncConfig, TableSchema } from '../api/sync'

const props = defineProps<{
  modelValue: boolean
  fileColumns: string[]
  labelKeys?: string[]
  dbSchema: TableSchema | null
  syncConfig: SyncConfig | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submit', payload: { syncConfigId: number; fieldMappings: Record<string, string>; strategy: 'insert' }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const state = reactive({
  mapping: {} as Record<string, string>,
})

watch(
  () => props.modelValue,
  (v) => {
    if (v) state.mapping = {}
  },
)

function displayFileColumn(c: string) {
  if (props.labelKeys?.includes(c)) {
    const m = /^(.*)_v\d+$/.exec(c)
    return m ? m[1] : c
  }
  return c
}

function onSubmit() {
  if (!props.syncConfig) return
  const entries = Object.entries(state.mapping).filter(([k, v]) => k && v)
  if (entries.length === 0) {
    ElMessage.warning('请至少配置一个字段映射')
    return
  }
  emit('submit', { syncConfigId: props.syncConfig.id, fieldMappings: state.mapping, strategy: 'insert' })
}
</script>

<template>
  <el-dialog v-model="visible" title="字段映射" width="760px" :close-on-click-modal="false">
    <div style="margin-bottom: 12px; color: #666">
      仅归档后可同步；当前策略：insert
    </div>
    <el-table :data="fileColumns" height="420">
      <el-table-column label="文件列" min-width="240">
        <template #default="{ row }">{{ displayFileColumn(row) }}</template>
      </el-table-column>
      <el-table-column label="目标字段" min-width="340">
        <template #default="{ row }">
          <el-select v-model="state.mapping[row]" filterable clearable style="width: 100%" :disabled="disabled">
            <el-option
              v-for="c in dbSchema?.columns || []"
              :key="c.name"
              :label="`${c.name} (${c.type})`"
              :value="c.name"
            />
          </el-select>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="disabled" @click="onSubmit">同步</el-button>
    </template>
  </el-dialog>
</template>

