<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { DatabaseExplorer, DatabaseItem, TableItem } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'

interface Props {
  sourceId: number
}

interface Emits {
  (e: 'select', database: string, table: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)
const explorer = ref<DatabaseExplorer | null>(null)
const expandedKeys = ref<string[]>([])
const currentKey = ref<string>('')

async function loadExplorer() {
  if (!props.sourceId) return

  loading.value = true
  try {
    explorer.value = await dataSourcesApi.exploreDatabases(props.sourceId)

    // 默认展开第一个数据库
    if (explorer.value.databases.length > 0) {
      expandedKeys.value = [explorer.value.databases[0].name]
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载数据库失败')
  } finally {
    loading.value = false
  }
}

function handleNodeClick(data: any) {
  // 如果点击的是表节点，触发选择事件
  if (data.type === 'TABLE' || data.type === 'VIEW') {
    // 从节点数据中提取数据库名和表名
    const database = data.database || data.parentDatabase
    const table = data.name
    emit('select', database, table)
  }
}

// 监听 sourceId 变化，重新加载数据
watch(() => props.sourceId, () => {
  loadExplorer()
})

onMounted(loadExplorer)

// 将树节点数据转换为 Element Plus Tree 需要的格式
function buildTreeData(explorer: DatabaseExplorer) {
  return explorer.databases.map((db: DatabaseItem) => ({
    name: db.name,
    type: 'database',
    children: db.tables.map((table: TableItem) => ({
      name: table.name,
      type: table.type,
      database: db.name,
      parentDatabase: db.name,
      rowCount: table.rowCount,
      label: `${table.name} (${table.type})`,
    })),
  }))
}

// 计算属性：树形数据
const treeData = computed(() => {
  if (!explorer.value) return []
  return buildTreeData(explorer.value)
})

// 默认插槽
const slots = defineSlots<{
  default?: (props: { node: any; data: any }) => any
}>()
</script>

<template>
  <div class="database-explorer">
    <el-tree
      v-loading="loading"
      :data="treeData"
      :props="{ children: 'children', label: 'name' }"
      node-key="name"
      :expand-on-click-node="false"
      :default-expanded-keys="expandedKeys"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <span class="custom-tree-node">
          <el-icon v-if="data.type === 'database'" class="node-icon">
            <Folder />
          </el-icon>
          <el-icon v-else class="node-icon">
            <Document />
          </el-icon>
          <span class="node-label">{{ node.label }}</span>
          <el-tag v-if="data.rowCount" size="small" type="info" class="row-count">
            {{ data.rowCount }} 行
          </el-tag>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<style scoped>
.database-explorer {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #f9fafb;
  max-height: 400px;
  overflow-y: auto;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.node-icon {
  color: #909399;
}

.node-label {
  flex: 1;
}

.row-count {
  font-size: 12px;
}
</style>
