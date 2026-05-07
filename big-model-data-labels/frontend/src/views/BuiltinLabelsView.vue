<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { Label } from '@/api/labels'
import type { BuiltinLabelCategory } from '@/api/builtin-labels'
import * as builtinLabelsApi from '@/api/builtin-labels'
import BuiltinLabelDetailDialog from '@/components/BuiltinLabelDetailDialog.vue'

const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.role === 'admin')

const loading = ref(false)
const labels = ref<Label[]>([])
const categories = ref<BuiltinLabelCategory[]>([])
const selectedCategory = ref('')

const page = ref(1)
const size = ref(10)
const total = ref(0)

const detailDialogVisible = ref(false)
const selectedLabel = ref<Label | null>(null)

onMounted(async () => {
  await Promise.all([fetchCategories(), fetchLabels()])
})

async function fetchCategories() {
  try {
    categories.value = await builtinLabelsApi.listCategories()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载分类失败')
  }
}

async function fetchLabels() {
  loading.value = true
  try {
    const resp = await builtinLabelsApi.listBuiltinLabels({
      category: selectedCategory.value || undefined,
      page: page.value,
      size: size.value,
    })
    labels.value = resp.items
    total.value = resp.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载内置标签失败')
  } finally {
    loading.value = false
  }
}

function handleCategoryChange() {
  page.value = 1
  fetchLabels()
}

function handleViewDetail(row: Label) {
  selectedLabel.value = row
  detailDialogVisible.value = true
}

async function handleToggleActive(row: Label) {
  if (!isAdmin.value) {
    return
  }
  try {
    await builtinLabelsApi.setBuiltinLabelActive(row.id, !!row.isActive)
    ElMessage.success('已更新状态')
  } catch (e: any) {
    ElMessage.error(e?.message || '更新状态失败')
    // 回滚 UI 状态
    row.isActive = !row.isActive
  }
}
</script>

<template>
  <div class="builtin-labels-view">
    <div class="page-header">
      <h2>内置全局标签库</h2>
      <p class="subtitle">系统预置的高质量标签，可直接用于数据标注</p>
    </div>

    <div class="category-filter">
      <el-radio-group v-model="selectedCategory" @change="handleCategoryChange">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button v-for="cat in categories" :key="cat.code" :label="cat.code">
          {{ cat.name }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="labels" v-loading="loading" stripe class="labels-table">
      <el-table-column prop="name" label="标签名称" min-width="220" />

      <el-table-column label="分类" width="180">
        <template #default="{ row }">
          <el-tag size="small">
            {{ row.builtinCategory || '-' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="description" label="说明" min-width="360" show-overflow-tooltip />

      <el-table-column label="模式" width="140">
        <template #default="{ row }">
          <span v-if="row.preprocessingMode === 'llm_only'">LLM</span>
          <span v-else-if="row.preprocessingMode === 'rule_only'">Rule</span>
          <span v-else-if="row.preprocessingMode === 'rule_then_llm'">Hybrid</span>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column label="版本" width="80" prop="version" />

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch v-model="row.isActive" :disabled="!isAdmin" @change="handleToggleActive(row)" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleViewDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, sizes, total"
        @current-change="fetchLabels"
        @size-change="fetchLabels"
      />
    </div>

    <BuiltinLabelDetailDialog v-model="detailDialogVisible" :label="selectedLabel" />
  </div>
</template>

<style scoped>
.builtin-labels-view {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.category-filter {
  margin-bottom: 24px;
}

.labels-table {
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
}
</style>

